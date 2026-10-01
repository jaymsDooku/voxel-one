#version 330 core

in vec3 vNormal;
in vec3 vColor;
uniform int uVertexColor;
in vec3 vWorldPosition;

out vec4 fragColor;

uniform vec3 uLightDirection;
uniform vec3 uColor;
uniform int uDistantTerrain;
uniform vec3 uDetailOrigin;
uniform vec3 uTerrainMinimum;
uniform vec3 uTerrainMaximum;
uniform int uDetailRows[16];
uniform int uFog;
uniform vec3 uCameraPosition;

void main() {
    if (uDistantTerrain == 1) {
        if (any(lessThan(vWorldPosition.xz, uTerrainMinimum.xz))
                || any(greaterThan(vWorldPosition.xz, uTerrainMaximum.xz))) discard;
        ivec2 column = ivec2(floor((vWorldPosition.xz - vNormal.xz * 0.125) / 16.0)) - ivec2(uDetailOrigin.xz);
        if (column.x >= 0 && column.x < 16 && column.y >= 0 && column.y < 16
                && (uDetailRows[column.y] & (1 << column.x)) != 0) discard;
    }
    vec3 normal = normalize(vNormal);

    float diffuse = max(
        dot(normal, normalize(-uLightDirection)),
        0.0
    );

    float ambient = 0.35;

    vec3 blockColor = uVertexColor==1?vColor:uColor;

    vec3 finalColor =
        blockColor * (ambient + diffuse * 0.65);

    if (uFog == 1) {
        float distance = length(vWorldPosition.xz - uCameraPosition.xz);
        float haze = smoothstep(640.0, 1920.0, distance);
        finalColor = mix(finalColor, vec3(0.48, 0.72, 0.92), haze);
    }
    fragColor = vec4(finalColor, 1.0);
}