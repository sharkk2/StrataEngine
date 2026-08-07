#version 430 core

uniform sampler2D srcTexture;
uniform float filterRadius;

in vec2 TexCoord;
out vec4 upsample;

void main() {
    float x = filterRadius;
    float y = filterRadius;

    vec3 a = texture(srcTexture, vec2(TexCoord.x - x, TexCoord.y + y)).rgb;
    vec3 b = texture(srcTexture, vec2(TexCoord.x, TexCoord.y + y)).rgb;
    vec3 c = texture(srcTexture, vec2(TexCoord.x + x, TexCoord.y + y)).rgb;
    vec3 d = texture(srcTexture, vec2(TexCoord.x - x, TexCoord.y)).rgb;
    vec3 e = texture(srcTexture, vec2(TexCoord.x, TexCoord.y)).rgb;
    vec3 f = texture(srcTexture, vec2(TexCoord.x + x, TexCoord.y)).rgb;
    vec3 g = texture(srcTexture, vec2(TexCoord.x - x, TexCoord.y - y)).rgb;
    vec3 h = texture(srcTexture, vec2(TexCoord.x, TexCoord.y - y)).rgb;
    vec3 i = texture(srcTexture, vec2(TexCoord.x + x, TexCoord.y - y)).rgb;

    vec3 us = e * 4.0;
    us += (b + d + f + h) * 2.0;
    us += (a + c + g + i);
    us *= 1.0 / 16.0;
    upsample = vec4(us, 1);
}