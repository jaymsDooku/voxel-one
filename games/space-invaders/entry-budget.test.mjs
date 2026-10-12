import test from 'node:test';
import assert from 'node:assert/strict';
import {EntryRenderer} from './entry-renderer.mjs';
test('preparation face budget survives animation-frame counter resets',()=>{
 const documentBefore=globalThis.document;globalThis.document={createElement:()=>({addEventListener(){}})};
 try{
  const r=new EntryRenderer(),c={eye:[0,0,0],forward:[0,0,1],right:[1,0,0],up:[0,1,0]},points=[[-1,-1,10],[1,-1,10],[0,1,10]];let fills=0;
  const ctx={beginPath(){},moveTo(){},lineTo(){},closePath(){},fill(){fills++;}};
  r.moons=()=>{};r.faceWork=0;r.faceLimit=640;
  r.localTerrainSteps=function*(){for(let i=0;i<1000;i++){yield;r.polygon(ctx,points,'#fff',c,320,240);}};
  const job=r.composeFrame(ctx,320,240,c,'airless');while(!job.next().done){r.faceWork=0;r.faceLimit=640;}
  assert.equal(fills,640);assert.equal(r.preparationFacePeak,640);assert.equal(r.faceWork,0);
 }finally{globalThis.document=documentBefore;}
});
