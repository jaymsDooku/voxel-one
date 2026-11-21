#version 330 core
#include "shaders/atmosphere-common.glsl"
#include "shaders/irradiance.glsl"
uniform sampler2DArray uShadow;
uniform int uShadowEnabled,uHeld,uShadowCascades;
uniform mat4 uShadowMatrix[3];
#include "shaders/sun-shadow.glsl"
#include "shaders/preview-wall-sky.glsl"
uniform vec3 uTestPosition,uTestNormal;
uniform int uTestLedge;
uniform vec3 uTestSun;
out vec4 fragColor;
void main(){
    float sky=irradiance(uTestPosition).a;
    if(uTestLedge==1){
        bool covered;
        float shadow=shadowAt(uTestPosition-vec3(0,.001,0),vec3(0,1,0),uTestSun,covered);
        fragColor=vec4(sky,previewLedgeVisibility(uTestPosition,sky,shadow,covered),covered?1.:0.);
        return;
    }
    float side=previewWallSky(uTestPosition,uTestNormal,sky);
    fragColor=vec4(sky,side,previewSideExposure(uTestPosition,uTestNormal),
            previewCourtyardSky(uTestPosition,uTestNormal));
}
