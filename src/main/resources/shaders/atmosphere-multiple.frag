#version 330 core
in vec2 vUV;out vec4 fragColor;
#include "shaders/atmosphere-common.glsl"
// Original isotropic repeat-scattering closure. Directional single scatter is integrated
// over 128 equal-solid-angle Fibonacci directions. Feedback includes diffuse ground bounce.
// It is a compact approximation, not a full higher-order transport solver.
uniform int uMultipleSamples;
void main(){
    if(uAtmosphereEnabled==0){fragColor=vec4(0,0,0,1);return;}
    vec3 p=vec3(0,uPlanetRadius+max(.001,vUV.y*vUV.y*uAtmosphereHeight),0);
    float mu=vUV.x*2.-1.;vec3 sun=vec3(sqrt(max(0.,1.-mu*mu)),mu,0);
    vec3 meanLight=vec3(0),meanFeedback=vec3(0);
    for(int j=0;j<128;j++){
        float y=1.-2.*(float(j)+.5)/128.,phi=float(j)*2.39996323;
        vec3 d=vec3(sqrt(1.-y*y)*cos(phi),y,sqrt(1.-y*y)*sin(phi));
        vec3 t,l;atmosphereIntegrate(p,d,sun,2.*(uPlanetRadius+uAtmosphereHeight),uMultipleSamples,t,l);
        vec2 path=atmospherePath(p,d,2.*(uPlanetRadius+uAtmosphereHeight));vec3 feedback=vec3(0),viewT=vec3(1);
        if(path.y>path.x){float stepSize=(path.y-path.x)/float(uMultipleSamples);for(int i=0;i<128;i++){if(i>=uMultipleSamples)break;
            vec3 q=p+d*(path.x+(float(i)+.5)*stepSize),rho=atmosphereDensity(length(q)-uPlanetRadius);
            vec3 extinction=atmosphereExtinction(rho),scatter=uRayleigh*rho.x+uMieScattering*rho.y,stepT=exp(-extinction*stepSize);
            feedback+=viewT*(1.-stepT)*scatter/max(extinction,vec3(1e-8));viewT*=stepT;
        }}
        vec2 ground=atmosphereSphere(p,d,uPlanetRadius);
        if(ground.x>0.&&ground.x<=path.y+.001){vec3 q=p+d*ground.x,n=normalize(q);
            l+=t*uGroundAlbedo*uSolar*atmosphereSunlight(q+n*.002,sun)*max(0.,dot(n,sun))/ATM_PI;
            feedback+=viewT*uGroundAlbedo;
        }
        meanLight+=l/128.;meanFeedback+=feedback/128.;
    }
    fragColor=vec4(max(vec3(0),meanLight)/(1.-clamp(meanFeedback,vec3(0),vec3(.95))),1);
}
