export const models = {
  squid:['00100','01110','11111','10101','01010'],
  crab:['0100010','0010100','0111110','1101011','1111111','1000001','0100010'],
  octopus:['0011100','0111110','1111111','1101011','1111111','0010100','0101010'],
  fighter:['0001000','0011100','0011100','0111110','1111111','1001001','0001000'],
  ship:['0001000','0011100','0011100','1111111','1111111'],
};
// Bounds include the front, top and right faces drawn by the cube renderer.
export function cubeBounds(size,x=0,y=0){return {left:x,right:x+size*1.3,top:y-size*.3,bottom:y+size};}
export const modelBounds=Object.fromEntries(Object.entries(models).map(([name,rows])=>{
 const cells=[];rows.forEach((row,r)=>[...row].forEach((v,c)=>{if(v==='1')cells.push(cubeBounds(4,(c-row.length/2)*4,(r-rows.length/2)*4));}));
 return [name,{left:Math.min(...cells.map(b=>b.left)),right:Math.max(...cells.map(b=>b.right)),top:Math.min(...cells.map(b=>b.top)),bottom:Math.max(...cells.map(b=>b.bottom))}];
}));
export function visualBounds(shape,x,y,scale={x:1,y:1}){
 return {left:x+shape.left*scale.x,right:x+shape.right*scale.x,top:y+shape.top*scale.y,bottom:y+shape.bottom*scale.y};
}
function overlaps(a,b){return a.left<b.right&&a.right>b.left&&a.top<b.bottom&&a.bottom>b.top;}
export class Game {
  constructor(random=Math.random){this.random=random;this.renderScale={x:1,y:1};this.restart();this.state='title';}
  get perspective(){return this.wave%2===0?'cockpit':'classic';}
  get flight(){return this.wave>=2;}
  restart(){this.scene=null;this.sceneTime=0;this.score=0;this.lives=3;this.wave=1;this.player=240;this.playerY=520;this.velocity={x:0,y:0};this.shots=[];this.enemyShots=[];this.invulnerable=0;this.cooldown=0;this.state='playing';this.newWave();}
  newWave(){
    this.aliens=[];this.shields=[];
    if(this.flight){
      this.aliens=Array.from({length:12+Math.min(this.wave,8)*2},(_,i)=>{
        const phase=i*1.71,baseX=45+(i%6)*76,baseY=65+Math.floor(i/6)*65;
        const cycle=phase%7;
        return {x:Math.max(22,Math.min(458,baseX+Math.sin(phase)*55+Math.sin(cycle*2)*30)),
          y:baseY+Math.sin(phase)*22+(cycle>3&&cycle<6?Math.sin((cycle-3)/3*Math.PI)*(330-baseY):0),
          type:'fighter',points:40,phase,age:0,baseX,baseY};
      });
      this.playerY=520;this.velocity={x:0,y:0};
    }else{
      for(let r=0;r<5;r++)for(let c=0;c<9;c++)this.aliens.push({x:55+c*42,y:75+r*34,type:r===0?'squid':r<3?'crab':'octopus',points:r===0?30:r<3?20:10});
      for(let b=0;b<4;b++)for(let r=0;r<5;r++)for(let c=0;c<9;c++)if(!(r>2&&c>2&&c<6)&&!(r===0&&(c===0||c===8)))this.shields.push({x:62+b*105+c*5,y:440+r*5});
    }
    this.direction=1;this.enemyClock=1;this.saucer=null;this.saucerClock=12;
  }
  pause(){if(this.state==='playing')this.state='paused';else if(this.state==='paused')this.state='playing';}
  update(dt,input={}){
    if(this.state!=='playing')return;
    // Bound elapsed time: resuming a suspended mobile tab must not fast-forward combat.
    dt=Math.max(0,Math.min(dt,.05));
    if(this.scene){
      this.sceneTime+=dt;
      const duration={crash:2,blackout:1.2,waking:3}[this.scene];
      if(this.sceneTime>=duration){
        this.sceneTime=0;
        if(this.scene==='crash')this.scene='blackout';
        else if(this.scene==='blackout'){this.wave=3;this.scene='waking';}
        else {this.scene=null;this.newWave();this.invulnerable=2;}
      }
      return;
    }
    this.cooldown-=dt;this.invulnerable-=dt;
    if(this.flight){
      const ax=Math.max(-1,Math.min(1,input.axis||0)),ay=Math.max(-1,Math.min(1,input.vertical||0));
      const norm=Math.max(1,Math.hypot(ax,ay));
      const blend=1-Math.exp(-8*dt);
      this.velocity.x+=(ax/norm*280-this.velocity.x)*blend;
      this.velocity.y+=(ay/norm*280-this.velocity.y)*blend;
      this.player=Math.max(20,Math.min(460,this.player+this.velocity.x*dt));
      this.playerY=Math.max(280,Math.min(530,this.playerY+this.velocity.y*dt));
      if(this.player===20||this.player===460)this.velocity.x=0;
      if(this.playerY===280||this.playerY===530)this.velocity.y=0;
    }else this.player=Math.max(20,Math.min(460,this.player+(input.axis||0)*230*dt));
    if(input.fire&&this.cooldown<=0){this.shots.push({x:this.player,y:this.playerY-10});this.cooldown=this.flight?.18:.28;}
    if(this.flight){
      for(const a of this.aliens){
        a.age=(a.age||0)+dt;a.phase??=0;a.baseX??=a.x;a.baseY??=a.y;
        const cycle=(a.age+a.phase)%7;
        a.x=Math.max(22,Math.min(458,a.baseX+Math.sin(a.age*1.6+a.phase)*55+Math.sin(cycle*2)*30));
        a.y=a.baseY+Math.sin(a.age*2+a.phase)*22+(cycle>3&&cycle<6?Math.sin((cycle-3)/3*Math.PI)*(330-a.baseY):0);
      }
    }else{
      const speed=18+this.wave*4+(45-this.aliens.length)*1.2;
      let edge=false;for(const a of this.aliens){a.x+=this.direction*speed*dt;if(a.x<20||a.x>460)edge=true;}
      if(edge){this.direction*=-1;for(const a of this.aliens){a.x=Math.max(20,Math.min(460,a.x));a.y+=15;}}
      if(this.aliens.some(a=>a.y>=490)){this.state='over';return;}
    }
    const bounds=(shape,x,y)=>visualBounds(shape,x,y,this.renderScale);
    for(const a of this.aliens)this.shields=this.shields.filter(s=>!overlaps(bounds(modelBounds[a.type||'crab'],a.x,a.y),bounds(cubeBounds(5),s.x,s.y)));
    this.enemyClock-=dt;if(this.enemyClock<=0&&this.aliens.length){
      const bottom=this.flight?this.aliens:this.aliens.filter(a=>!this.aliens.some(b=>Math.abs(b.x-a.x)<2&&b.y>a.y));
      const a=bottom[Math.floor(this.random()*bottom.length)];const shot={x:a.x,y:a.y+12};if(this.flight){const dx=this.player-a.x,dy=this.playerY-shot.y,length=Math.max(1,Math.hypot(dx,dy));shot.vx=dx/length*210;shot.vy=dy/length*210;}this.enemyShots.push(shot);this.enemyClock=Math.max(.22,1.1-this.wave*.08-this.score/10000);
    }
    if(!this.flight)this.saucerClock-=dt;if(this.saucerClock<=0&&!this.saucer){this.saucer={x:-25,y:38};this.saucerClock=18;}
    if(this.saucer){this.saucer.x+=75*dt;if(this.saucer.x>510)this.saucer=null;}
    for(const s of this.shots){s.previousX=s.x;s.previousY=s.y;s.y-=400*dt;}for(const s of this.enemyShots){s.previousX=s.x;s.previousY=s.y;s.x+=(s.vx||0)*dt;s.y+=(s.vy??(150+this.wave*10))*dt;}
    const hits=(shot,shape,x,y,enemy=false)=>{
      const projectile=bounds(cubeBounds(enemy?4:3,-2),shot.x,shot.y);
      const previous=bounds(cubeBounds(enemy?4:3,-2),shot.previousX??shot.x,shot.previousY??shot.y);
      projectile.left=Math.min(projectile.left,previous.left);projectile.right=Math.max(projectile.right,previous.right);
      projectile.top=Math.min(projectile.top,previous.top);projectile.bottom=Math.max(projectile.bottom,previous.bottom);
      return overlaps(projectile,bounds(shape,x,y));
    };
    const shieldHit=(s,enemy=false)=>{const i=this.shields.findIndex(b=>hits(s,cubeBounds(5),b.x,b.y,enemy));if(i<0)return false;const b=this.shields[i];this.shields=this.shields.filter(v=>Math.hypot(v.x-b.x,v.y-b.y)>8);return true;};
    this.shots=this.shots.filter(s=>{
      if(shieldHit(s))return false;
      if(this.saucer&&hits(s,modelBounds.octopus,this.saucer.x,this.saucer.y)){this.score+=100;this.saucer=null;return false;}
      const i=this.aliens.findIndex(a=>hits(s,modelBounds[a.type||'crab'],a.x,a.y));if(i>=0){this.score+=this.aliens[i].points;this.aliens.splice(i,1);return false;}return s.y>0;
    });
    let hit=false;this.enemyShots=this.enemyShots.filter(s=>{if(shieldHit(s,true))return false;if(hits(s,modelBounds.ship,this.player,this.playerY,true)){if(this.invulnerable<=0)hit=true;return false;}return s.y>-20&&s.y<570&&s.x>-20&&s.x<500;});
    if(this.flight&&this.invulnerable<=0&&this.aliens.some(a=>overlaps(bounds(modelBounds[a.type||'crab'],a.x,a.y),bounds(modelBounds.ship,this.player,this.playerY))))hit=true;
    if(hit){this.lives--;this.invulnerable=2;this.enemyShots=[];if(this.lives<=0)this.state='over';}
    if(!this.aliens.length&&this.state==='playing'){if(this.wave===2){this.scene='crash';this.sceneTime=0;this.shots=[];this.enemyShots=[];this.saucer=null;this.velocity={x:0,y:0};return;}this.wave++;this.shots=[];this.enemyShots=[];this.newWave();this.invulnerable=2;}
  }
}
