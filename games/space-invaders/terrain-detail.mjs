// Immutable, spatially complete parent tiles with bounded optional child detail.
export const DETAIL_LIMITS=Object.freeze({Low:{coarse:8,fine:16,faces:640},Medium:{coarse:5,fine:10,faces:2048},High:{coarse:3,fine:8,faces:4096}});
export function terrainTiles(scene,stride){
 if(!scene)return [];const tiles=[];
 for(let row=0;row<scene.view.rows;row+=stride)for(let col=0;col<scene.view.cols;col+=stride){
  const centerRow=Math.min(scene.view.rows-1,row+Math.floor(stride/2)),centerCol=Math.min(scene.view.cols-1,col+Math.floor(stride/2)),sample=scene.columns[centerRow*scene.view.cols+centerCol];if(!sample)continue;
  let landmark=null;for(let y=row;y<Math.min(row+stride,scene.view.rows);y++)for(let x=col;x<Math.min(col+stride,scene.view.cols);x++){const c=scene.columns[y*scene.view.cols+x],span=c?.spans?.filter(s=>s.top>c.height).sort((a,b)=>b.top-a.top)[0];if(span&&(!landmark||span.top>landmark.spans[0].top))landmark={...c,spans:[span]};}
  const next=scene.columns[Math.min(scene.view.rows-1,centerRow+stride)*scene.view.cols+centerCol];
  tiles.push({...sample,spans:[],source:scene,stride,landmark,nextHeight:next?.height??sample.height,col:centerCol,row:centerRow});
 }
 return tiles.sort((a,b)=>b.z-a.z);
}
export function terrainPlan(coarse,fine,quality){const limits=DETAIL_LIMITS[quality]||DETAIL_LIMITS.Low;return {coarse:terrainTiles(coarse,limits.coarse),fine:terrainTiles(fine,limits.fine).map(col=>({...col,detail:true})),faceLimit:limits.faces};}

// World-space overlap fades child faces before their rectangular boundary.
export function terrainFeather(col){const v=col.source.view,edge=Math.max(Math.abs(col.x-v.centerX)/(v.cols*v.step/2),Math.abs(col.z-v.centerZ)/(v.rows*v.step/2)),f=Math.max(0,Math.min(1,(1-edge)/.35));return f*f*(3-2*f);}
