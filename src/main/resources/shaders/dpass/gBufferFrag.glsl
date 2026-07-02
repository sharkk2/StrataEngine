#version 430 core

layout (location = 0) out vec3 gPosition;
layout (location = 1) out vec3 gNormal;
layout (location = 2) out vec4 gAlbedo;
layout (location = 3) out vec4 gMaterial;
layout (location = 4) out vec3 gEmissive;

in vec3 FragPos;
in vec2 TexCoords;
in vec3 WorldNormal;

uniform sampler2D albedoTex;
uniform vec3 albedo;

uniform sampler2D metalnessTex;
uniform float metalness;

uniform sampler2D roughnessTex;
uniform float roughness;

uniform sampler2D normalTex;

uniform sampler2D emissiveTex;
uniform vec3 emissive;
uniform float emissiveStrength;

uniform sampler2D aoTex;
uniform sampler2D opacityTex;
uniform float opacity;
uniform int isPackedORM;

uniform sampler2D alphaMaskTex;
uniform float alphaMaskThreshold;
uniform int alphaCutout;

uniform int useAlbedoTex;
uniform int useMetalnessTex;
uniform int useRoughnessTex;
uniform int useNormalTex;
uniform int useEmissiveTex;
uniform int useAoTex;
uniform int useOpacityTex;
uniform int useAlphaMaskTex;

layout(std140, binding = 0) uniform Camera {
    mat4 projection;
    mat4 view;
    mat4 inverseView;
};

void main() {
    vec4 mat_albedo = (useAlbedoTex == 1) ? texture(albedoTex, TexCoords) : vec4(albedo, opacity);
    mat_albedo.rgb = pow(mat_albedo.rgb, vec3(2.2));

    if (useOpacityTex == 1) {
        mat_albedo.a = texture(opacityTex, TexCoords).r;
    } else if (opacity != 1) {
        mat_albedo.a = opacity;
    }

    if (useAlphaMaskTex == 1) {
        if (texture(alphaMaskTex, TexCoords).r < alphaMaskThreshold) discard;
    } else if (alphaCutout == 1) {
        if (mat_albedo.a < alphaMaskThreshold) discard;
    } else {
        if (mat_albedo.a < 0.1) discard;
    }

    float mat_metalness = metalness;
    float mat_roughness = roughness;
    float mat_ao = (useAoTex == 1) ? texture(aoTex, TexCoords).r : 1.0;

    if (isPackedORM == 1) {
        vec3 packedORM = texture(roughnessTex, TexCoords).rgb;
        mat_ao = packedORM.r;
        mat_roughness = packedORM.g;
        mat_metalness = packedORM.b;
    } else {
        if (useMetalnessTex == 1) mat_metalness = texture(metalnessTex, TexCoords).r;
        if (useRoughnessTex == 1) mat_roughness = texture(roughnessTex, TexCoords).r;
    }
    mat_roughness = clamp(mat_roughness, 0.04, 1.0);

    vec3 norm;

    if (useNormalTex == 1) {
        vec3 N = normalize(WorldNormal);
        vec3 dp1 = dFdx(FragPos);
        vec3 dp2 = dFdy(FragPos);
        vec2 duv1 = dFdx(TexCoords);
        vec2 duv2 = dFdy(TexCoords);
        float det = duv1.x * duv2.y - duv2.x * duv1.y;
        float invDet = (abs(det) > 0.00001) ? 1.0 / det : 1.0;
        vec3 T = normalize((dp1 * duv2.y - dp2 * duv1.y) * invDet);
        T = normalize(T - dot(T, N) * N);
        vec3 B = cross(N, T);
        mat3 TBN = mat3(T, B, N);
        vec3 sampledNormal = texture(normalTex, TexCoords).rgb * 2.0 - 1.0;
        norm = normalize(TBN * sampledNormal);
    } else {
        norm = normalize(WorldNormal);
    }


    vec3 mat_emissive = (useEmissiveTex == 1) ? pow(texture(emissiveTex, TexCoords).rgb, vec3(2.2)) : emissive;

    gPosition = (view * vec4(FragPos, 1.0)).xyz;
    gNormal = norm;
    gAlbedo = mat_albedo;
    gMaterial = vec4(mat_metalness, mat_roughness, mat_ao, emissiveStrength);
    gEmissive = mat_emissive * emissiveStrength;
}