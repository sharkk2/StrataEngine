#version 430 core

in vec2 vUV;
flat in float iOpacity;

uniform float uOpacity;
uniform int uInstanced;
uniform int useAlbedoTex;
uniform int useOpacityTex;
uniform sampler2D albedoTex;
uniform sampler2D opacityTex;

void main() {
    float opacity = (uInstanced == 1) ? iOpacity : uOpacity;
    float alpha = opacity;

    if (useAlbedoTex == 1) {
        alpha = texture(albedoTex, vUV).a;
    }

    if (useOpacityTex == 1) {
        alpha = texture(opacityTex, vUV).r;
    } else if (opacity != 1.0) {
        alpha = opacity;
    }

    if (alpha < 0.05) discard;
}