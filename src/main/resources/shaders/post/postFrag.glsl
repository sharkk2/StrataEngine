#version 430 core
out vec4 FragColor;
in vec2 TexCoord;

uniform sampler2D screenTexture;
uniform float exposure;
uniform float saturation;
uniform float gamma;
uniform int useHDR;
uniform float time;

vec3 ACESFilm(vec3 x) {
    float a = 2.51f;
    float b = 0.03f;
    float c = 2.43f;
    float d = 0.59f;
    float e = 0.14f;
    return clamp((x*(a*x+b))/(x*(c*x+d)+e), 0.0, 1.0);
}

void main() {
    vec3 color = texture(screenTexture, TexCoord).rgb;

    if (useHDR == 1) {
        color = color * exposure;
        color = ACESFilm(color);
        float luminance = dot(color, vec3(0.299, 0.587, 0.114));
        color = mix(vec3(luminance), color, saturation);
    }

    color = pow(color, vec3(1.0 / gamma));

    float dist = distance(TexCoord, vec2(0.5, 0.5));
    float vignette = smoothstep(0.9, 0.45f, dist);

    FragColor = vec4(color * vignette, 1.0);
}