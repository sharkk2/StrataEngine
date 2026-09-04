#version 430 core
out vec4 FragColor;
in vec2 TexCoord;

uniform sampler2D depthTexture;
uniform sampler2D screenTexture;
uniform sampler2D bloomTexture;
uniform sampler3D lutTexture;
uniform sampler2D lensDirtTexture;
uniform float bloomStrength;

uniform float exposure;
uniform float saturation;
uniform float gamma;
uniform int useHDR;
uniform int applyACES;
uniform int gammaCorrect;
uniform int useColorGrading;
uniform int useBloom;
uniform int useLensDirt;
uniform float time;
uniform int AAMode;
uniform float lensDirtIntensity;

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
    vec2 texel = 1.0 / vec2(textureSize(tex, 0));

    vec3 rgbNW = texture(tex, uv + vec2(-1.0, -1.0) * texel).rgb;
    vec3 rgbNE = texture(tex, uv + vec2(1.0, -1.0) * texel).rgb;
    vec3 rgbSW = texture(tex, uv + vec2(-1.0, 1.0) * texel).rgb;
    vec3 rgbSE = texture(tex, uv + vec2(1.0, 1.0) * texel).rgb;
    vec3 rgbM  = texture(tex, uv).rgb;

    float lumaNW = luminance(rgbNW);
    float lumaNE = luminance(rgbNE);
    float lumaSW = luminance(rgbSW);
    float lumaSE = luminance(rgbSE);
    float lumaM  = luminance(rgbM);

    float lumaMin = min(lumaM, min(min(lumaNW, lumaNE), min(lumaSW, lumaSE)));
    float lumaMax = max(lumaM, max(max(lumaNW, lumaNE), max(lumaSW, lumaSE)));

    if (lumaMax - lumaMin < max(0.0312, lumaMax * 0.125)) return rgbM;

    vec2 dir;
    dir.x = -((lumaNW + lumaNE) - (lumaSW + lumaSE));
    dir.y =  ((lumaNW + lumaSW) - (lumaNE + lumaSE));

    const float reduceMin = 1.0 / 128.0;
    const float reduceMul = 1.0 / 8.0;
    const float spanMax = 8.0;

    float dirReduce = max((lumaNW + lumaNE + lumaSW + lumaSE) * 0.25 * reduceMul, reduceMin);
    float rcpDirMin = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);
    dir = clamp(dir * rcpDirMin, -spanMax, spanMax) * texel;

    vec3 rgbA = 0.5 * (
    texture(tex, uv + dir * (1.0 / 3.0 - 0.5)).rgb +
    texture(tex, uv + dir * (2.0 / 3.0 - 0.5)).rgb);
    vec3 rgbB = rgbA * 0.5 + 0.25 * (
    texture(tex, uv + dir * -0.5).rgb +
    texture(tex, uv + dir *  0.5).rgb);

    float lumaB = luminance(rgbB);
    return (lumaB < lumaMin || lumaB > lumaMax) ? rgbA : rgbB;
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

vec3 bloomMix(vec3 clr) {
    vec3 blm = texture(bloomTexture, TexCoord).rgb;
    vec3 drt = texture(lensDirtTexture, vec2(TexCoord.x, 1.0f - TexCoord.y)).rgb * lensDirtIntensity;
    vec3 result = mix(clr, blm + blm*drt, vec3(bloomStrength));
    return result;
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
        if (useLensDirt == 1) color = bloomMix(color);
        else {
            vec3 bloomColor = texture(bloomTexture, TexCoord).rgb;
            color += bloomColor * bloomStrength;
        }
    }

    if (useHDR == 1) {
        color = color * exposure;
        if (applyACES == 1) color = ACESFilm(color * 0.6);
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