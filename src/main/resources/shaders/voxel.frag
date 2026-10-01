#version 330 core

in vec3 vNormal;
in vec3 vColor;
uniform int uVertexColor;
in vec3 vWorldPosition;

out vec4 fragColor;

uniform vec3 uLightDirection;
uniform vec3 uColor;

void main() {
    vec3 normal = normalize(vNormal);

    float diffuse = max(
        dot(normal, normalize(-uLightDirection)),
        0.0
    );

    float ambient = 0.35;

    vec3 blockColor = uVertexColor==1?vColor:uColor;

    vec3 finalColor =
        blockColor * (ambient + diffuse * 0.65);

    fragColor = vec4(finalColor, 1.0);
}