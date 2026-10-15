const norm=v=>{const n=Math.hypot(...v);return v.map(x=>x/n);};
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
export const ENTRY_DURATION=12;
export function cameraAt(t,profile){
 if(!Number.isFinite(t))throw RangeError('Invalid entry time');
 const p=Math.min(1,Math.max(0,t/ENTRY_DURATION)),ease=p*p*(3-2*p);
 const keys=[12000000,120000,6000,1500,12],segment=Math.min(3,Math.floor(p*4)),fraction=p*4-segment,s=fraction*fraction*(3-2*fraction);
 const altitude=Math.exp(Math.log(keys[segment])*(1-s)+Math.log(keys[segment+1])*s);
 const a=-.30*Math.pow(Math.max(0,(altitude-12)/(12000000-12)),.7),r=profile.radius+altitude+profile.landingHeight;
 const eye=[0,r*Math.cos(a),r*Math.sin(a)-250*(ease**8)];
 const orbital=Math.min(1,Math.max(0,t/3)),focus=orbital*orbital*(3-2*orbital);
 // Look along the curved surface while crossing the shell, then bring the
 // same landing anchor into the low approach. A nadir aim hid the horizon.
 const lookKeys=[3300,1200000,100000,3300,3300],lookDistance=Math.exp(Math.log(lookKeys[segment])*(1-s)+Math.log(lookKeys[segment+1])*s);
 const surfaceY=Math.sqrt(Math.max(0,profile.radius*profile.radius-lookDistance*lookDistance));
 const target=[0,surfaceY+profile.landingHeight-profile.radius*.65*(1-focus),lookDistance];
 const forward=norm(target.map((x,i)=>x-eye[i])),right=norm(cross(forward,[0,1,0])),up=cross(right,forward);
 const altitudeVelocity=p>=1?0:altitude*(Math.log(keys[segment+1])-Math.log(keys[segment]))*6*fraction*(1-fraction)*4/ENTRY_DURATION;
 return {eye,forward,right,up,altitude,altitudeVelocity,progress:p};
}

export class EntrySequence {
 constructor(profile){this.profile=profile;this.duration=ENTRY_DURATION;this.epoch=0;this.cancelled=false;}
 sample(time){if(this.cancelled)return null;return cameraAt(time,this.profile);}
 cancel(){this.cancelled=true;this.epoch++;}
}

// External fighter camera: same anchor and FOV, raised after waking completes.
export function combatCameraAt(elapsed,profile){
 const landing=cameraAt(ENTRY_DURATION,profile),p=Math.max(0,Math.min(1,elapsed/.8)),blend=p*p*(3-2*p);
 const destination=[0,profile.radius+profile.landingHeight+1800,-3500],eye=landing.eye.map((v,i)=>v+(destination[i]-v)*blend),target=[0,profile.radius+profile.landingHeight,3300];
 const forward=norm(target.map((v,i)=>v-eye[i])),right=norm(cross(forward,[0,1,0])),up=cross(right,forward);
 return {...landing,eye,forward,right,up,altitude:12+(1800-12)*blend,combat:true,handoff:blend};
}
