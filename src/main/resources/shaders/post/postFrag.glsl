#version 430 core

out vec4 FragColor;
in vec2 TexCoord;

uniform sampler2D screenTexture;
uniform float exposure;
uniform float saturation;
uniform float gamma;
uniform int useHDR;

uniform usampler2D stencilView;
uniform sampler2D depthTexture;

float rand(vec2 seed) {
    float dot_product = dot(seed, vec2(12.9898, 78.233));
    return fract(sin(dot_product) * 43758.5453);
}


void main() {
    vec3 color = texture(screenTexture, TexCoord).rgb;
    if (useHDR == 1) {
        color = vec3(1.0) - exp(-color * exposure);
        float luminance = dot(color, vec3(0.299, 0.587, 0.114));
        color = mix(vec3(luminance), color, saturation);
    }
    color = pow(color, vec3(1.0 / gamma));

    float dist = distance(TexCoord, vec2(0.5, 0.5));
    float vignette = smoothstep(0.9, 0.45f, dist);
    FragColor = vec4(color * vignette, 1.0);
}