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
function draw(t){ctx.clearRect(0,0,480,570);for(let i=0;i<70;i++){ctx.fillStyle=i%3?'#395174':'#9ec9eb';ctx.fillRect((i*137)%480,(i*79)%570,1,1);}for(const a of game.aliens)model(a.type,a.x,a.y,a.type==='squid'?'#cf88ff':a.type==='crab'?'#70f3da':'#ffcb72');if(game.saucer)model('octopus',game.saucer.x,game.saucer.y,'#ff6a93');for(const b of game.shields)cube(b.x,b.y,5,'#459daa');if(game.invulnerable<=0||Math.floor(t/100)%2)model('ship',game.player,520,'#8dcaff');for(const s of game.shots)cube(s.x-2,s.y,3,'#fff5af');for(const s of game.enemyShots)cube(s.x-2,s.y,4,'#ff6386');ctx.strokeStyle='#254867';ctx.beginPath();ctx.moveTo(0,544);ctx.lineTo(480,544);ctx.stroke();}
function frame(t){const touch=[...pointers.values()];game.update((t-last)/1000,{axis:Number(keys.has('ArrowRight')||keys.has('KeyD')||touch.includes('right'))-Number(keys.has('ArrowLeft')||keys.has('KeyA')||touch.includes('left')),fire:keys.has('Space')||touch.includes('fire')});last=t;draw(t);hud.textContent=`Score ${game.score} · Lives ${game.lives} · Wave ${game.wave}`;if(oldState!==game.state){oldState=game.state;overlay.hidden=game.state==='playing';pause.disabled=!['playing','paused'].includes(game.state);pause.textContent=game.state==='paused'?'Resume':'Pause';if(game.state==='paused'){title.textContent='Paused';message.textContent='Your game is safe. Resume when ready.';start.textContent='Resume';}if(game.state==='over'){title.textContent='Game over';message.textContent=`Final score: ${game.score} · Wave ${game.wave}`;start.textContent='Play again';}status.textContent=game.state==='playing'?'Game started':title.textContent;}requestAnimationFrame(frame);}
requestAnimationFrame(frame);
