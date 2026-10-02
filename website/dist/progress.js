/* Read-only progress view. Refreshing never launches an agent or runs tests. */
const source='https://raw.githubusercontent.com/jaymsDooku/voxel-one/development-progress/progress.json';
const labels={complete:'Complete',in_progress:'In progress',queued:'Queued',blocked:'Blocked'};
let loadedAt=0;
function el(tag,text,className){const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(className)n.className=className;return n;}
function safeLink(url){try{const u=new URL(url,location.href);return u.protocol==='https:'||u.origin===location.origin?u.href:null;}catch{return null;}}
function render(data){
  if(data.schemaVersion!==1||!Array.isArray(data.items))throw new Error('Unsupported progress data');
  const complete=data.items.filter(i=>i.status==='complete'),active=data.items.filter(i=>i.status==='in_progress'||i.status==='blocked'),queued=data.items.filter(i=>i.status==='queued');
  document.getElementById('completed-count').textContent=complete.length;
  document.getElementById('active-count').textContent=active.length;
  document.getElementById('queued-count').textContent=queued.length;
  document.getElementById('tests-count').textContent=data.verification?.tests??'—';
  document.getElementById('test-label').textContent='Tests · '+(data.verification?.label??'Recorded build');
  document.getElementById('updated-at').textContent='Last milestone: '+new Date(data.updatedAt).toLocaleString();
  const root=document.getElementById('work');
  const opened=new Set(Array.from(root.querySelectorAll('details[open]')).map(n=>n.dataset.item));root.replaceChildren();
  for(const [title,items] of [['Current work',active],['Next up',queued],['Completed work',complete.slice().reverse()]]){
    if(!items.length)continue;
    const section=el('section',undefined,'work-group');section.append(el('h2',title));
    for(const item of items){
      const details=el('details',undefined,'work-item');details.dataset.item=item.id;details.open=opened.has(item.id)||item.status==='in_progress';
      const summary=el('summary'),heading=el('span',undefined,'work-heading');heading.append(el('strong',item.title),el('small',item.description));summary.append(heading,el('span',labels[item.status]||item.status,'badge '+item.status));details.append(summary);
      const evidence=el('div',undefined,'evidence');evidence.append(el('p',item.note||''));
      if(item.updatedAt)evidence.append(el('p','Updated '+new Date(item.updatedAt).toLocaleString(),'timestamp'));
      const links=el('div',undefined,'evidence-links');
      for(const entry of item.evidence||[]){const href=safeLink(entry.url);if(!href)continue;
        const a=el('a',entry.label);a.href=href;a.target='_blank';a.rel='noopener noreferrer';
        if(entry.kind==='image'){const f=el('figure'),img=el('img');img.src=href;img.alt=entry.label+' — '+item.title;img.loading='lazy';img.width=1280;img.height=720;a.textContent='';a.append(img);f.append(a,el('figcaption',entry.label+'. Recorded evidence, not a live view.'));evidence.append(f);}else if(entry.kind==='video'){const f=el('figure'),video=el('video');video.controls=true;video.playsInline=true;video.preload='none';video.setAttribute('aria-label',entry.label+' — '+item.title);const u=new URL(href);const prefix='/jaymsDooku/voxel-one/development-progress/evidence/';video.src=u.hostname==='raw.githubusercontent.com'&&u.pathname.startsWith(prefix)?'/api/evidence-video/'+encodeURIComponent(u.pathname.slice(prefix.length)):href;video.append(el('p','Use the download link to watch this recording.'));a.textContent='Download recording';f.append(video,el('figcaption',entry.label+'. Recorded game footage.'),a);evidence.append(f);}else links.append(a);
      }
      if(links.children.length)evidence.prepend(links);else if(!(item.evidence||[]).length)evidence.append(el('p','Testing evidence will be added as this work is verified.','timestamp'));
      details.append(evidence);section.append(details);
    }root.append(section);
  }
}
async function load(){
  const button=document.getElementById('refresh');button.disabled=true;
  const state=document.getElementById('load-state');state.textContent='Checking latest milestone…';
  const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),6000);
  try{const response=await fetch(source+'?t='+Date.now(),{cache:'no-store',signal:controller.signal});if(!response.ok)throw new Error('Progress unavailable');render(await response.json());state.textContent='Latest published progress. Updates are posted at meaningful milestones.';loadedAt=Date.now();}
  catch{if(!loadedAt){try{const r=await fetch('progress.json');if(!r.ok)throw new Error('No snapshot');render(await r.json());}catch{state.textContent='Progress could not be loaded. Try Refresh when your connection returns.';button.disabled=false;return;}}
    state.textContent='Showing the saved snapshot. Latest progress could not be reached; try Refresh.';
  }finally{clearTimeout(timeout);button.disabled=false;}
}
document.getElementById('refresh').addEventListener('click',load);
document.addEventListener('visibilitychange',()=>{if(!document.hidden&&Date.now()-loadedAt>300000)load();});
load();
