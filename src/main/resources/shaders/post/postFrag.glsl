#version 430 core
out vec4 FragColor;
in vec2 TexCoord;

uniform sampler2D depthTexture;
uniform sampler2D screenTexture;
uniform sampler2D bloomTexture;
uniform sampler3D lutTexture;
uniform float bloomStrength;

uniform float exposure;
uniform float saturation;
uniform float gamma;
uniform int useHDR;
uniform int applyACES;
uniform int gammaCorrect;
uniform int useColorGrading;
uniform int useBloom;
uniform float time;
uniform int AAMode;

uniform vec2 sunScreenPos;
uniform vec3 sunColor;
uniform float godrayExposure;
uniform float godrayDecay;
uniform float godrayDensity;
uniform float godrayWeight;
uniform float godrayMaxBrightness;
uniform int godraysEnabled;

const int GODRAY_SAMPLES = 64;



float luminance(vec3 color) {return dot(color, vec3(0.299, 0.587, 0.114));}

vec3 FXAA(sampler2D tex, vec2 uv) {
    ivec2 size = textureSize(screenTexture, 0);
    vec2 texel = 1.0 / vec2(size);

    vec3 c = texture(tex, uv).rgb;
    float l = luminance(c);

    float n = luminance(texture(screenTexture, TexCoord + vec2(0.0, texel.y)).rgb);
    float s = luminance(texture(screenTexture, TexCoord - vec2(0.0, texel.y)).rgb);
    float e = luminance(texture(screenTexture, TexCoord + vec2(texel.x, 0.0)).rgb);
    float w = luminance(texture(screenTexture, TexCoord - vec2(texel.x, 0.0)).rgb);

    float horizontal = abs(n - s);
    float vertical = abs(e - w);
    if (max(horizontal, vertical) < 0.1) return c;
    if (horizontal > vertical) {
        vec3 a = texture(tex, uv + vec2(0, texel.y)).rgb;
        vec3 b = texture(tex, uv - vec2(0, texel.y)).rgb;
        return (a + b + c) / 3.0;
    } else {
        vec3 a = texture(tex, uv + vec2(texel.x, 0)).rgb;
        vec3 b = texture(tex, uv - vec2(texel.x, 0)).rgb;
        return (a + b + c) / 3.0;
    }
}

vec3 ACESFilm(vec3 x) {
    float a = 2.51f;
    float b = 0.03f;
    float c = 2.43f;
    float d = 0.59f;
    float e = 0.14f;
    return clamp((x*(a*x+b))/(x*(c*x+d)+e), 0.0, 1.0);
}

float dither(vec2 uv) {
    return fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 computeGodRays(vec2 uv) {
    vec2 deltaTexCoord = (uv - sunScreenPos) * (godrayDensity / float(GODRAY_SAMPLES));
    float jitter = dither(uv);
    vec2 coord = uv - deltaTexCoord * jitter;

    float illuminationDecay = 1.0;
    float accum = 0.0;

    for (int i = 0; i < GODRAY_SAMPLES; i++) {
        coord -= deltaTexCoord;
        float depth = texture(depthTexture, coord).r;
        float unoccluded = depth >= 1.0 ? 1.0 : 0.0;
        accum += unoccluded * illuminationDecay * godrayWeight;
        illuminationDecay *= godrayDecay;
    }

    float distFromSun = distance(uv, sunScreenPos);
    float radialFalloff = 1.0 - smoothstep(0.0, 2.0, distFromSun);
    vec3 result = sunColor * accum * godrayExposure * radialFalloff;
    return min(result, vec3(godrayMaxBrightness));
}

void main() {
    vec3 color;
    switch (AAMode) {
        case 0: color = texture(screenTexture, TexCoord).rgb; break;
        case 1: color = FXAA(screenTexture, TexCoord); break;
        default: color = texture(screenTexture, TexCoord).rgb; break;
    }

    if (godraysEnabled == 1) {
        color += computeGodRays(TexCoord);
    }

    if (useBloom == 1) {
        vec3 bloomColor = texture(bloomTexture, TexCoord).rgb;
        color += bloomColor * bloomStrength;
    }

    if (useHDR == 1) {
        color = color * exposure;
        if (applyACES == 1) color = ACESFilm(color);
        else { color = color / (1.0 + color); }
        color = mix(vec3(luminance(color)), color, saturation);
    } else {
        color = clamp(color, 0.0, 1.0);
    }


    if (gammaCorrect == 1) color = pow(color, vec3(1.0 / gamma));
    if (useColorGrading == 1) {
        float lutSize = float(textureSize(lutTexture, 0).x);
        vec3 lutCoord = color * (lutSize - 1.0) / lutSize + 0.5 / lutSize;
        color = texture(lutTexture, lutCoord).rgb;
    }


    float dist = distance(TexCoord, vec2(0.5, 0.5));
    float vignette = smoothstep(0.9, 0.45f, dist);

    FragColor = vec4(color * vignette, 1.0);
}