#version 330 core
in vec2 vUV;out vec4 fragColor;
uniform mat4 uInverseViewProjection;
uniform vec3 uCameraPosition,uSunDirection;
uniform int uIsometric,uClouds;
uniform float uDaylight,uAmbient,uTime,uCloudCoverage;
const float PI=3.14159265;
float hash(vec3 p){return fract(sin(dot(p,vec3(127.1,311.7,74.7)))*43758.5453);}
float noise(vec3 p){vec3 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);}
float fbm(vec3 p){return noise(p)*.57+noise(p*2.03)*.28+noise(p*4.11)*.15;}
float cloud(vec3 p){float h=(p.y-180.)/60.;float shape=smoothstep(0.,.15,h)*(1.-smoothstep(.6,1.,h));return max(0.,fbm(p*.008+vec3(uTime*.002,0,0))-(1.-uCloudCoverage))*shape;}
void main(){
    vec4 farPoint=uInverseViewProjection*vec4(vUV*2.-1.,1,1);vec3 ray=normalize(farPoint.xyz/farPoint.w-uCameraPosition);
    float mu=dot(ray,uSunDirection),g=.76;
    float air=1./sqrt(max(.0025,pow(max(0.,ray.y),2.)));
    float sunAir=1./sqrt(max(.0025,pow(max(0.,uSunDirection.y),2.)));
    vec3 beta=vec3(.0058,.0135,.0331),optical=beta*8.+vec3(.004*1.2);
    float phaseR=3.*(1.+mu*mu)/(16.*PI),phaseM=(1.-g*g)/(4.*PI*pow(1.+g*g-2.*g*mu,1.5));
    vec3 color=(1.-exp(-optical*air))*(beta*8.*phaseR+vec3(.004*1.2*phaseM))/optical*exp(-optical*sunAir)*18.*uDaylight+vec3(.006)*uAmbient;
    float disc=smoothstep(.9997,.99985,mu)*8.;
    color+=vec3(1.,.9,.7)*disc*uDaylight;
    if(uClouds==1&&ray.y>.001&&uCameraPosition.y<240.){
        float near=max(0.,(180.-uCameraPosition.y)/ray.y),far=(240.-uCameraPosition.y)/ray.y;
        float stepSize=(far-near)/16.,transmittance=1.;vec3 scattering=vec3(0);
        for(int i=0;i<16;i++){
            vec3 p=uCameraPosition+ray*(near+(float(i)+.5)*stepSize);float density=cloud(p);
            float attenuation=exp(-density*stepSize*.04);
            float lightDensity=0.;for(int j=1;j<=4;j++)lightDensity+=cloud(p+uSunDirection*float(j)*12.);
            vec3 illumination=vec3(.18,.25,.36)*uAmbient+vec3(1.,.89,.68)*exp(-lightDensity*1.5)*uDaylight;
            scattering+=transmittance*(1.-attenuation)*illumination;transmittance*=attenuation;
        }
        color=color*transmittance+scattering;
    }
    float moon=smoothstep(.9992,.9996,dot(ray,-uSunDirection))*.6*(1.-uDaylight);
    vec3 cell=floor(ray*600.);float stars=step(.997,hash(cell))*(1.-uAmbient)*step(.05,ray.y);
    if(uIsometric==1)color=vec3(.18,.28,.43)*uAmbient;
    fragColor=vec4(color+vec3(.45,.58,.9)*(moon+stars),1);
}
