#version 330 core
in vec2 vUV;out vec4 fragColor;
uniform mat4 uInverseViewProjection;
uniform mat4 uInverseProjection,uInverseView;
uniform vec3 uCameraPosition,uSunDirection;
uniform int uIsometric,uClouds;
uniform float uDaylight,uAmbient,uTime,uCloudCoverage;
#include "shaders/atmosphere-common.glsl"
uniform sampler2D uSkyView;
float hash(vec3 p){return fract(sin(dot(p,vec3(127.1,311.7,74.7)))*43758.5453);}
float noise(vec3 p){vec3 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);}
float fbm(vec3 p){return noise(p)*.57+noise(p*2.03)*.28+noise(p*4.11)*.15;}
float cloud(vec3 p){float h=(p.y-180.)/60.;float shape=smoothstep(0.,.15,h)*(1.-smoothstep(.6,1.,h));return max(0.,fbm(p*.008+vec3(uTime*.002,0,0))-(1.-uCloudCoverage))*shape;}
void main(){
    vec4 nearPoint=uInverseViewProjection*vec4(vUV*2.-1.,-1,1),farPoint=uInverseViewProjection*vec4(vUV*2.-1.,1,1);
    vec3 origin=uIsometric==1?nearPoint.xyz/nearPoint.w:uCameraPosition;
    vec4 projectedRay=uInverseProjection*vec4(vUV*2.-1.,1,1);
    vec3 ray=uIsometric==1?normalize(mat3(uInverseView)*vec3(0,0,-1)):normalize(mat3(uInverseView)*projectedRay.xyz),planetRay=normalize(uWorldToPlanet*ray);
    vec3 planetOrigin=uPlanetCamera+uWorldToPlanet*(origin-uCameraPosition)*uBlockKm;
    float mu=dot(planetRay,uAtmosphereSun);
    vec2 skyUV=vec2(fract(atan(planetRay.x,planetRay.z)/(2.*ATM_PI)),asin(clamp(planetRay.y,-1.,1.))/ATM_PI+.5);
    vec3 color=texture(uSkyView,skyUV).rgb;
    if(uIsometric==1||length(planetOrigin)>uPlanetRadius+uAtmosphereHeight){vec3 t;atmosphereIntegrate(planetOrigin,planetRay,uAtmosphereSun,2.*(uPlanetRadius+uAtmosphereHeight),length(planetOrigin)>uPlanetRadius+uAtmosphereHeight?256:uAtmosphereSamples,t,color);}
    vec3 solarT=atmosphereSunlight(planetOrigin,uAtmosphereSun);
    float disc=smoothstep(cos(uSolarRadius*1.1),cos(uSolarRadius*.9),mu);
    color+=uSolar*solarT*disc;
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

    fragColor=vec4(color+vec3(.45,.58,.9)*(moon+stars),1);
}
