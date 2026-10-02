#version 330 core
in vec3 vColor,vNormal,vWorldPosition;
out vec4 fragColor;
uniform float uDaylight,uAmbient;
uniform vec3 uLightDirection,uCameraPosition,uDetailOrigin,uTerrainMinimum,uTerrainMaximum;
uniform int uDetailRows[16],uFog;
uniform samplerCube uEnvironment;
void main(){
    if(any(lessThan(vWorldPosition.xz,uTerrainMinimum.xz))||any(greaterThan(vWorldPosition.xz,uTerrainMaximum.xz)))discard;
    ivec2 c=ivec2(floor((vWorldPosition.xz-vNormal.xz*.125)/16.))-ivec2(uDetailOrigin.xz);
    if(c.x>=0&&c.x<16&&c.y>=0&&c.y<16&&(uDetailRows[c.y]&(1<<c.x))!=0)discard;
    vec3 N=normalize(vNormal),V=normalize(uCameraPosition-vWorldPosition);
    vec3 color=pow(max(vColor,vec3(0)),vec3(2.2))*(vec3(.24,.32,.45)*uAmbient+vec3(1.5,1.38,1.15)*uDaylight*max(dot(N,normalize(-uLightDirection)),0.));
    color+=textureLod(uEnvironment,reflect(-V,N),6.).rgb*.018;
    if(uFog==1){float haze=smoothstep(640.,1920.,length(vWorldPosition.xz-uCameraPosition.xz));color=mix(color,vec3(.32,.53,.8)*uAmbient,haze);}
    fragColor=vec4(color,1);
}
