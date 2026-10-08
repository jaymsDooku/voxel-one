import {Game,models} from './game.mjs';
export const game=new Game();
const canvas=document.querySelector('canvas'),ctx=canvas.getContext('2d');
const hud=document.querySelector('#hud'),overlay=document.querySelector('#overlay'),title=document.querySelector('#title'),message=document.querySelector('#message'),start=document.querySelector('#start'),pause=document.querySelector('#pause'),status=document.querySelector('#status');
const keys=new Set(),pointers=new Map();let last=0,oldState='';
function clear(){keys.clear();pointers.clear();document.querySelectorAll('.active').forEach(b=>b.classList.remove('active'));}
function toggle(){clear();game.pause();}
start.onclick=()=>{clear();if(game.state==='paused')game.pause();else game.restart();};pause.onclick=toggle;
addEventListener('keydown',e=>{if(['ArrowLeft','ArrowRight','Space','KeyA','KeyD','KeyP','Escape'].includes(e.code)){e.preventDefault();if(!e.repeat&&(e.code==='KeyP'||e.code==='Escape'))toggle();keys.add(e.code);}});
addEventListener('keyup',e=>keys.delete(e.code));
for(const b of document.querySelectorAll('[data-control]')){
 b.addEventListener('pointerdown',e=>{e.preventDefault();if(e.isTrusted)b.setPointerCapture(e.pointerId);pointers.set(e.pointerId,b.dataset.control);b.classList.add('active');});
 const release=e=>{pointers.delete(e.pointerId);if(![...pointers.values()].includes(b.dataset.control))b.classList.remove('active');};
 b.addEventListener('pointerup',release);b.addEventListener('pointercancel',release);b.addEventListener('lostpointercapture',release);
}
addEventListener('blur',()=>{clear();if(game.state==='playing')game.pause();});document.addEventListener('visibilitychange',()=>{if(document.hidden){clear();if(game.state==='playing')game.pause();}});
function cube(x,y,size,color){ctx.fillStyle=color;ctx.fillRect(x,y,size,size);ctx.fillStyle='#ffffff50';ctx.beginPath();ctx.moveTo(x,y);ctx.lineTo(x+size*.3,y-size*.3);ctx.lineTo(x+size*1.3,y-size*.3);ctx.lineTo(x+size,y);ctx.fill();ctx.fillStyle='#00000050';ctx.beginPath();ctx.moveTo(x+size,y);ctx.lineTo(x+size*1.3,y-size*.3);ctx.lineTo(x+size*1.3,y+size*.7);ctx.lineTo(x+size,y+size);ctx.fill();}
function model(name,x,y,color){const rows=models[name],s=4;rows.forEach((row,r)=>[...row].forEach((v,c)=>{if(v==='1')cube(x+(c-row.length/2)*s,y+(r-rows.length/2)*s,s,color);}));}
// Keep the combat rules in their original coordinates. Project their positions
// across the viewport, but keep each voxel square at every aspect ratio.
let width=0,height=0,unit=1,top=0,playHeight=0,backdrop;
function resize(){
 width=canvas.clientWidth;height=canvas.clientHeight;
 const dpr=Math.min(devicePixelRatio||1,2);
 canvas.width=Math.round(width*dpr);canvas.height=Math.round(height*dpr);
 ctx.setTransform(dpr,0,0,dpr,0,0);
 top=height<500?62:100;const bottom=height<500?72:112;
 playHeight=Math.max(100,height-top-bottom);
 unit=Math.min(width/480,playHeight/570);
 game.renderScale={x:unit/(width/480),y:unit/(playHeight/570)};
 backdrop=document.createElement('canvas');backdrop.width=canvas.width;backdrop.height=canvas.height;
 const sky=backdrop.getContext('2d');sky.scale(dpr,dpr);
 sky.fillStyle='#030817';sky.fillRect(0,0,width,height);
 for(const [x,y,r,color] of [[.22,.3,.65,'#7137ab'],[.8,.48,.55,'#125d83'],[.56,.12,.4,'#a13b73']]){
  const glow=sky.createRadialGradient(width*x,height*y,0,width*x,height*y,Math.max(width,height)*r);
  glow.addColorStop(0,color+'85');glow.addColorStop(.45,color+'30');glow.addColorStop(1,color+'00');sky.fillStyle=glow;sky.fillRect(0,0,width,height);
 }
 for(let i=0;i<340;i++){
  const x=((i*137.508)%997)/997*width,y=((i*79.731)%991)/991*height;
  const size=i%19===0?2:1;sky.fillStyle=i%3?'#b2c9ee90':'#ffffff';sky.fillRect(x,y,size,size);
  if(i%47===0){sky.fillStyle='#a5dfff55';sky.fillRect(x-3,y,7,1);sky.fillRect(x,y-3,1,7);}
 }
 // Distant block worlds establish the voxel setting without hiding combat.
 for(const [x,y,r] of [[.08,.36,22],[.92,.7,34]]){
  sky.fillStyle='#172941';sky.fillRect(width*x-r,height*y-r,r*2,r*2);
  sky.fillStyle='#315472';sky.fillRect(width*x-r,height*y-r,r*2,6);
  sky.fillStyle='#0b1428';sky.fillRect(width*x+r-7,height*y-r,7,r*2);
  sky.fillStyle='#417284';sky.fillRect(width*x-r+5,height*y-r+10,8,8);
 }
}
new ResizeObserver(resize).observe(canvas);
export function project(x,y){
 if(game.perspective==='classic')return [x/480*width,top+y/570*playHeight];
 // Camera sits inside the ship. Near objects spread out as they approach.
 const depth=.38+.62*Math.max(0,y)/570;
 return [width/2+(x-game.player)/480*width*depth,top+playHeight*(.12+.78*(y/570)**1.35)];
}
function worldModel(name,x,y,color){if(game.perspective==='cockpit'){const rows=models[name];rows.forEach((row,r)=>[...row].forEach((v,c)=>{if(v==='1')worldCube(x,y,4,color,(c-row.length/2)*4,(r-rows.length/2)*4);}));return;}const [px,py]=project(x,y);ctx.save();ctx.translate(px,py);ctx.scale(unit,unit);model(name,0,0,color);ctx.restore();}
function worldCube(x,y,size,color,offsetX=0,offsetY=0){
 if(game.perspective==='cockpit'){
  const polygon=(points,fill)=>{ctx.fillStyle=fill;ctx.beginPath();points.forEach(([dx,dy],i)=>{const [px,py]=project(x+(offsetX+dx)*game.renderScale.x,y+(offsetY+dy)*game.renderScale.y);if(i)ctx.lineTo(px,py);else ctx.moveTo(px,py);});ctx.closePath();ctx.fill();};
  polygon([[0,0],[size,0],[size,size],[0,size]],color);
  polygon([[0,0],[size*.3,-size*.3],[size*1.3,-size*.3],[size,0]],'#ffffff50');
  polygon([[size,0],[size*1.3,-size*.3],[size*1.3,size*.7],[size,size]],'#00000050');return;
 }
 const [px,py]=project(x,y);ctx.save();ctx.translate(px,py);ctx.scale(unit,unit);cube(offsetX,0,size,color);ctx.restore();}
function draw(t){
 if(!backdrop)return;
 ctx.drawImage(backdrop,0,0,width,height);
 // A receding voxel deck beneath the ship, integrated into the space scene.
 ctx.strokeStyle='#53cbd324';ctx.lineWidth=1;
 const horizon=top+playHeight*.84;
 for(let i=-8;i<=8;i++){ctx.beginPath();ctx.moveTo(width/2+i*width/20,horizon);ctx.lineTo(width/2+i*width/6,height);ctx.stroke();}
 for(let i=1;i<7;i++){const y=horizon+(height-horizon)*(i/6)**2;ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(width,y);ctx.stroke();}
 for(const a of game.aliens)worldModel(a.type,a.x,a.y,a.type==='squid'?'#cf88ff':a.type==='crab'?'#70f3da':'#ffcb72');
 if(game.saucer)worldModel('octopus',game.saucer.x,game.saucer.y,'#ff6a93');
 for(const b of game.shields)worldCube(b.x,b.y,5,'#459daa');
 if(game.perspective==='classic'&&(game.invulnerable<=0||Math.floor(t/100)%2))worldModel('ship',game.player,520,'#8dcaff');
 for(const s of game.shots)worldCube(s.x,s.y,3,'#fff5af',-2);
 for(const s of game.enemyShots)worldCube(s.x,s.y,4,'#ff6386',-2);
 if(game.perspective==='cockpit'){
  // Windshield rim and instruments leave the combat area unobstructed.
  const bottom=top+playHeight*.94;
  ctx.fillStyle='#0b1729';ctx.fillRect(0,bottom,width,height-bottom);
  ctx.strokeStyle=game.invulnerable>0?'#ffb66d':'#70f3da';ctx.lineWidth=3;
  ctx.beginPath();ctx.moveTo(0,height);ctx.lineTo(width*.08,bottom);ctx.lineTo(width*.92,bottom);ctx.lineTo(width,height);ctx.stroke();
  const [cx,cy]=project(game.player,200);ctx.lineWidth=1.5;ctx.beginPath();ctx.moveTo(cx-12,cy);ctx.lineTo(cx-4,cy);ctx.moveTo(cx+4,cy);ctx.lineTo(cx+12,cy);ctx.moveTo(cx,cy-12);ctx.lineTo(cx,cy-4);ctx.moveTo(cx,cy+4);ctx.lineTo(cx,cy+12);ctx.stroke();
  ctx.fillStyle='#70f3da';ctx.font='12px system-ui';ctx.textAlign='center';ctx.fillText('COCKPIT · STRAFE TO AIM',width/2,bottom+18);ctx.textAlign='start';
 }
 canvas.dataset.perspective=game.perspective;
}
function frame(t){const touch=[...pointers.values()];game.update((t-last)/1000,{axis:Number(keys.has('ArrowRight')||keys.has('KeyD')||touch.includes('right'))-Number(keys.has('ArrowLeft')||keys.has('KeyA')||touch.includes('left')),fire:keys.has('Space')||touch.includes('fire')});last=t;draw(t);hud.textContent=`Score ${game.score} · Lives ${game.lives} · Wave ${game.wave}`;canvas.setAttribute('aria-label',`Level ${game.wave}: ${game.perspective==='cockpit'?'First-person spaceship cockpit':'Classic Space Invaders'}`);if(oldState!==game.state){oldState=game.state;overlay.hidden=game.state==='playing';pause.disabled=!['playing','paused'].includes(game.state);pause.textContent=game.state==='paused'?'Resume':'Pause';if(game.state==='paused'){title.textContent='Paused';message.textContent='Your game is safe. Resume when ready.';start.textContent='Resume';}if(game.state==='over'){title.textContent='Game over';message.textContent=`Final score: ${game.score} · Wave ${game.wave}`;start.textContent='Play again';}status.textContent=game.state==='playing'?'Game started':title.textContent;}requestAnimationFrame(frame);}
requestAnimationFrame(frame);
