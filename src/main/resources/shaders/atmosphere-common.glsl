// Original bounded RGB model. GPU lengths in kilometres, coefficients per kilometre.
uniform int uAtmosphereEnabled,uAtmosphereSamples,uPlanetLighting;
uniform float uPlanetRadius,uAtmosphereHeight,uRayleighScale,uMieScale,uOzoneCentre,uOzoneWidth,uMieG,uSolarRadius,uBlockKm;
uniform vec3 uRayleigh,uMieScattering,uMieExtinction,uOzone,uSolar,uPlanetCamera,uAtmosphereSun;
uniform mat3 uWorldToPlanet;
uniform vec3 uAtmosphereWorldCamera,uAtmosphereViewDirection,uAtmosphereRayOrigin;
uniform int uAtmosphereOrtho,uAtmosphereReflection;
uniform float uAtmosphereReflectionPlane,uOverviewHaze;
uniform vec3 uAtmosphereReflectedEye;
uniform sampler2D uTransmittance;
uniform vec3 uAtmosphereLutSize,uGroundAlbedo;
uniform int uMultipleScatteringEnabled;
#ifdef ATM_SCENE
vec2 atmosphereVisibility(vec3 worldPoint);
vec3 atmosphereWorldStart;
#endif
const float ATM_PI=3.14159265359;
vec2 atmosphereSphere(vec3 p,vec3 d,float r){float b=dot(p,d),c=(length(p)-r)*(length(p)+r),v=b*b-c;
    if(v<0.)return vec2(1.,-1.);float h=sqrt(max(0.,v));float q=-b-(b>=0.?h:-h);float a=q,broot=abs(q)<1e-12?-b:c/q;return vec2(min(a,broot),max(a,broot));}
vec3 atmosphereDensity(float h){if(h<0.||h>uAtmosphereHeight)return vec3(0);return vec3(exp(-h/uRayleighScale),exp(-h/uMieScale),max(0.,1.-abs(h-uOzoneCentre)/uOzoneWidth));}
vec3 atmosphereExtinction(vec3 density){return uRayleigh*density.x+uMieExtinction*density.y+uOzone*density.z;}
vec2 atmospherePath(vec3 p,vec3 d,float distance){if(length(p)<uPlanetRadius-.001)return vec2(1.,0.);vec2 shell=atmosphereSphere(p,d,uPlanetRadius+uAtmosphereHeight);
    float a=max(0.,shell.x),b=min(distance,shell.y);vec2 ground=atmosphereSphere(p,d,uPlanetRadius);
    if(ground.y>0.&&ground.x>=-.001)b=min(b,max(0.,ground.x));return vec2(a,b);}
vec3 atmosphereSunlight(vec3 p,vec3 sun){if(length(p)<uPlanetRadius-.001)return vec3(0);vec2 ground=atmosphereSphere(p,sun,uPlanetRadius);
    if(ground.y>0.&&ground.x>=-.001)return vec3(0);if(uAtmosphereEnabled==0)return vec3(1);float r=length(p);
    if(r>=uPlanetRadius+uAtmosphereHeight)return vec3(1);
    float h=clamp((r-uPlanetRadius)/uAtmosphereHeight,0.,1.);float mu=dot(p/r,sun);
    float mapped=sign(mu)*sqrt(abs(mu));
    vec2 uv=vec2(((mapped*.5+.5)*(uAtmosphereLutSize.x-1.)+.5)/uAtmosphereLutSize.x,(sqrt(h)*(uAtmosphereLutSize.y-1.)+.5)/uAtmosphereLutSize.z);
    return texture(uTransmittance,uv).rgb;}
vec3 atmosphereMultiple(vec3 p,vec3 sun){
    if(uMultipleScatteringEnabled==0||uAtmosphereEnabled==0)return vec3(0);
    float r=length(p),size=uAtmosphereLutSize.z-uAtmosphereLutSize.y;
    if(r>=uPlanetRadius+uAtmosphereHeight)return vec3(0);
    float mu=clamp(dot(p/r,sun)*.5+.5,0.,1.),h=sqrt(clamp((r-uPlanetRadius)/uAtmosphereHeight,0.,1.));
    return texture(uTransmittance,vec2((mu*(size-1.)+.5)/uAtmosphereLutSize.x,(uAtmosphereLutSize.y+h*(size-1.)+.5)/uAtmosphereLutSize.z)).rgb;
}
void atmosphereIntegrate(vec3 p,vec3 d,vec3 sun,float distance,int count,out vec3 transmittance,out vec3 light){
    transmittance=vec3(1);light=vec3(0);if(uAtmosphereEnabled==0)return;
    vec2 span=atmospherePath(p,d,distance);if(span.y<=span.x)return;
    float stepSize=(span.y-span.x)/float(count),mu=clamp(dot(d,sun),-1.,1.),g=uMieG;
    float phaseR=3.*(1.+mu*mu)/(16.*ATM_PI),phaseM=(1.-g*g)/(4.*ATM_PI*pow(1.+g*g-2.*g*mu,1.5));
    for(int i=0;i<256;i++){if(i>=count)break;vec3 q=p+d*(span.x+(float(i)+.5)*stepSize);vec3 rho=atmosphereDensity(length(q)-uPlanetRadius);
        vec3 sigma=atmosphereExtinction(rho),stepT=exp(-sigma*stepSize);
        vec3 source=(uRayleigh*rho.x*phaseR+uMieScattering*rho.y*phaseM)*uSolar*atmosphereSunlight(q,sun);
        vec3 multiple=(uRayleigh*rho.x+uMieScattering*rho.y)*atmosphereMultiple(q,sun);
#ifdef ATM_SCENE
        vec3 worldSample=atmosphereWorldStart+transpose(uWorldToPlanet)*d*((span.x+(float(i)+.5)*stepSize)/uBlockKm);
        vec2 visible=atmosphereVisibility(worldSample);source*=visible.x;multiple*=visible.y;
#endif
        source+=multiple;
        vec3 integral=vec3(sigma.x>1e-8?(1.-stepT.x)/sigma.x:stepSize,sigma.y>1e-8?(1.-stepT.y)/sigma.y:stepSize,sigma.z>1e-8?(1.-stepT.z)/sigma.z:stepSize);
        light+=transmittance*source*integral;transmittance*=stepT;
    }
}

// Incoming solar irradiance, before the material BRDF; no second clock attenuation.
vec3 atmosphereDirect(vec3 cameraDelta){
    vec3 p=uPlanetCamera+uWorldToPlanet*cameraDelta*uBlockKm;
    // Offset only the virtual surface roundoff; never move a real exterior sample.
    if(length(p)<uPlanetRadius+.001)p=normalize(p)*(uPlanetRadius+.001);
    return uSolar*atmosphereSunlight(p,uAtmosphereSun);
}

// Orthographic samples start on a separate camera-plane ray for each surface point.
void atmosphereAerial(vec3 worldPoint,int count,out vec3 t,out vec3 light){
    vec3 offset=worldPoint-uAtmosphereRayOrigin,origin=uPlanetCamera+uWorldToPlanet*(uAtmosphereRayOrigin-uAtmosphereWorldCamera)*uBlockKm,delta;
    if(uAtmosphereOrtho==1){
        float distance=max(0.,dot(offset,uAtmosphereViewDirection));
        origin+=uWorldToPlanet*(offset-uAtmosphereViewDirection*distance)*uBlockKm;
        delta=uWorldToPlanet*uAtmosphereViewDirection*(distance*uBlockKm);
    }else delta=uWorldToPlanet*offset*uBlockKm;
#ifdef ATM_SCENE
    atmosphereWorldStart=uAtmosphereRayOrigin;
    if(uAtmosphereOrtho==1)atmosphereWorldStart+=offset-uAtmosphereViewDirection*max(0.,dot(offset,uAtmosphereViewDirection));
#endif
    if(uAtmosphereReflection==1){
        vec3 direction=uAtmosphereOrtho==1?reflect(uAtmosphereViewDirection,vec3(0,1,0)):normalize(worldPoint-uAtmosphereReflectedEye);
        if(abs(direction.y)>1e-6){
            vec3 waterPoint=worldPoint-direction*((worldPoint.y-uAtmosphereReflectionPlane)/direction.y);
            origin=uPlanetCamera+uWorldToPlanet*(waterPoint-uAtmosphereWorldCamera)*uBlockKm;
            delta=uWorldToPlanet*(worldPoint-waterPoint)*uBlockKm;
#ifdef ATM_SCENE
            atmosphereWorldStart=waterPoint;
#endif
        }
    }
    float distance=length(delta);t=vec3(1);light=vec3(0);
    if(distance>1e-6)atmosphereIntegrate(origin,delta/distance,uAtmosphereSun,distance,count,t,light);
    if(uAtmosphereOrtho==1){t=mix(vec3(1),t,uOverviewHaze);light*=uOverviewHaze;}
}

// Diagnostic virtual planetary surface closes the background below the local patch.
vec3 atmosphereGround(vec3 p,vec3 d){
    if(length(p)<uPlanetRadius-.001){vec3 n=normalize(p);return uGroundAlbedo*uSolar*atmosphereSunlight(n*(uPlanetRadius+.002),uAtmosphereSun)*max(0.,dot(n,uAtmosphereSun))/ATM_PI;}
    vec2 ground=atmosphereSphere(p,d,uPlanetRadius);
    if(ground.x<0.||ground.y<=0.)return vec3(0);
    vec3 q=p+d*ground.x,n=normalize(q);
    return uGroundAlbedo*uSolar*atmosphereSunlight(q+n*.002,uAtmosphereSun)*max(0.,dot(n,uAtmosphereSun))/ATM_PI;
}
