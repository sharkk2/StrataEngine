#version 430 core

in vec2 TexCoords;

uniform sampler2D albedoTex;
uniform vec3 uAlbedo;
uniform float uOpacity;

uniform sampler2D opacityTex;
uniform sampler2D alphaMaskTex;
uniform float uAlphaMaskThreshold;

flat in vec3 iAlbedo;
flat in float iOpacity;
flat in vec2 iAlphaMaterial;

uniform int useAlbedoTex;
uniform int useOpacityTex;
uniform int useAlphaMaskTex;
uniform int uAlphaCutout;
uniform int uInstanced;

void main() {
    vec3 albedo = uInstanced == 1 ? iAlbedo : uAlbedo;
    float opacity = uInstanced == 1 ? iOpacity : uOpacity;
    float alphaCutout = uInstanced == 1 ? iAlphaMaterial.x : uAlphaCutout;
    float alphaMaskThreshold = uInstanced == 1 ? iAlphaMaterial.y : uAlphaMaskThreshold;

    vec4 mat_albedo = (useAlbedoTex == 1) ? texture(albedoTex, TexCoords) : vec4(albedo, opacity);

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
}