#version 430 core

in vec3 TexDir;
out vec4 FragColor;

uniform int useCubeTex;
uniform samplerCube cubeTex;

uniform int dayTime;
uniform int dayLength;
uniform int dayTimeEffective;

uniform int showSun;
uniform int showMoon;
uniform int showStars;

uniform int useMoonTex;
uniform sampler2D moonTex;

uniform int weather;

uniform vec3 sunDir;
uniform vec3 moonDir;

const float PI = 3.14159265359;
const float SUN_RADIUS = 0.035;
const float MOON_RADIUS = 0.035;

struct SkyPalette {
    vec3 zenith;
    vec3 mid;
    vec3 horizon;
    vec3 haze;
};

float hash21(vec2 p) {
    p = fract(p * vec2(127.1, 311.7));
    p += dot(p, p + 34.45);
    return fract(p.x * p.y);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);

    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));

    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        sum += valueNoise(p) * amp;
        p *= 2.02;
        amp *= 0.5;
    }
    return sum;
}

SkyPalette computeSkyPalette(float sunHeight) {
    SkyPalette pal;

    if (dayTimeEffective == 1) {
        float sunUp = clamp(sunHeight * 5.0, 0.0, 1.0);
        float sunsetBlend = 1.0 - smoothstep(0.05, 0.18, abs(sunHeight));

        vec3 dayZenith = vec3(0.08, 0.15, 0.45);
        vec3 dayMid = vec3(0.25, 0.52, 0.85);
        vec3 dayHorizon = vec3(0.72, 0.84, 0.98);
        vec3 dayHaze = vec3(0.90, 0.75, 0.60);

        vec3 nightZenith = vec3(0.002, 0.003, 0.010);
        vec3 nightMid = vec3(0.004, 0.006, 0.016);
        vec3 nightHorizon = vec3(0.008, 0.010, 0.022);
        vec3 nightHaze = vec3(0.004, 0.005, 0.012);

        vec3 sunsetZenith = vec3(0.05, 0.05, 0.18);
        vec3 sunsetMid = vec3(0.45, 0.20, 0.20);
        vec3 sunsetHorizon = vec3(0.95, 0.45, 0.30);
        vec3 sunsetHaze = vec3(0.90, 0.40, 0.30);

        pal.zenith = mix(nightZenith, dayZenith, sunUp);
        pal.mid = mix(nightMid, dayMid, sunUp);
        pal.horizon = mix(nightHorizon, dayHorizon, sunUp);
        pal.haze = mix(nightHaze, dayHaze, sunUp);

        pal.zenith = mix(pal.zenith, sunsetZenith, sunsetBlend * 0.05);
        pal.mid = mix(pal.mid, sunsetMid, sunsetBlend * 0.10);
        pal.horizon = mix(pal.horizon, sunsetHorizon, sunsetBlend * 0.15);
        pal.haze = mix(pal.haze, sunsetHaze, sunsetBlend * 0.20);
    } else {
        pal.zenith = vec3(0.08, 0.15, 0.45);
        pal.mid = vec3(0.25, 0.52, 0.85);
        pal.horizon = vec3(0.72, 0.84, 0.98);
        pal.haze = vec3(0.90, 0.75, 0.60);
    }

    return pal;
}

vec3 sampleGradient(SkyPalette pal, vec3 dir) {
    float t = clamp(dir.y, 0.0, 1.0);
    vec3 color = mix(pal.horizon, pal.mid, smoothstep(0.0, 0.25, t));
    color = mix(color, pal.zenith, smoothstep(0.15, 1.0, t));

    float hazeBand = exp(-t * 20.0) * clamp(1.0 - abs(dir.y) * 6.0, 0.0, 1.0);
    return mix(color, pal.haze, hazeBand * 0.5);
}

vec3 renderSun(vec3 dir, vec3 sDir, float sunHeight, float clearness) {
    if (showSun == 0) return vec3(0.0);

    float sunDot = dot(dir, sDir);
    float sunAngle = acos(clamp(sunDot, -1.0, 1.0));

    float visibility = (dayTimeEffective == 1) ? clamp(sunHeight * 10.0, 0.0, 1.0) : 1.0;
    visibility *= clearness;

    float discEdge = smoothstep(SUN_RADIUS, SUN_RADIUS * 0.7, sunAngle);
    vec3 discColor = mix(vec3(1.0, 0.4, 0.1), vec3(1.0, 0.97, 0.88), clamp(sunHeight * 3.0, 0.0, 1.0));

    float innerCorona = exp(-sunAngle * 80.0) * 0.9;
    float outerGlow = exp(-sunAngle * 12.0) * 0.4;
    float mie = pow(max(sunDot, 0.0), 6.0) * 0.6;

    vec3 contrib = vec3(1.0, 0.85, 0.65) * mie
    + vec3(1.0, 0.90, 0.75) * outerGlow
    + vec3(3.0, 4.0, 3.5) * innerCorona
    + discColor * discEdge * 20.0;

    return contrib * visibility;
}

vec3 renderStars(vec3 dir, float sunHeight, float clearness) {
    if (showStars == 0 || dayTimeEffective == 0) return vec3(0.0);

    vec3 absDir = abs(dir);
    vec2 faceUV;
    float faceID;

    if (absDir.x >= absDir.y && absDir.x >= absDir.z) {
        faceUV = dir.yz / absDir.x;
        faceID = dir.x > 0.0 ? 0.0 : 1.0;
    } else if (absDir.y >= absDir.x && absDir.y >= absDir.z) {
        faceUV = dir.xz / absDir.y;
        faceID = dir.y > 0.0 ? 2.0 : 3.0;
    } else {
        faceUV = dir.xy / absDir.z;
        faceID = dir.z > 0.0 ? 4.0 : 5.0;
    }

    vec2 cell = floor(faceUV * 40.0);
    vec3 seed = vec3(cell, faceID);

    float rand = fract(sin(dot(seed, vec3(127.1, 311.7, 74.7))) * 43758.5453);
    float rand2 = fract(sin(dot(seed, vec3(269.5, 183.3, 246.1))) * 43758.5453);
    float rand3 = fract(sin(dot(seed, vec3(113.5, 271.9, 124.6))) * 43758.5453);

    float brightness = 0.0;
    if (rand > 0.95) {
        vec2 cellUV = fract(faceUV * 40.0) - 0.5;
        cellUV -= (vec2(rand2, rand3) - 0.5) * 0.6;

        float dist = length(cellUV);
        float size = mix(0.04, 0.12, rand2);

        brightness = smoothstep(size, size * 0.3, dist);
        brightness *= mix(0.3, 0.85, rand3);
    }

    float twinkle = sin(rand * 63.7 + rand2 * 91.3) * 0.5 + 0.5;
    brightness *= mix(0.7, 1.0, twinkle);

    float visibility = clamp(-sunHeight * 4.0, 0.0, 1.0) * smoothstep(0.0, 0.5, dir.y) * clearness;

    vec3 color = mix(vec3(0.8, 0.9, 1.0), vec3(1.0, 0.85, 0.7), rand2);
    return min(color * brightness * visibility, vec3(1.0));
}

vec3 renderMoon(vec3 dir, vec3 mDir, vec3 baseColor, float clearness) {
    if (showMoon == 0) return baseColor;

    float moonDot = dot(dir, mDir);
    float moonAngle = acos(clamp(moonDot, -1.0, 1.0));

    float visibility = clamp(mDir.y * 10.0 + 1.0, 0.0, 1.0) * clearness;

    float glow = exp(-moonAngle * 16.0) * 0.14;
    vec3 color = baseColor + vec3(0.55, 0.65, 0.85) * glow * visibility;

    if (moonAngle >= MOON_RADIUS * 1.5) return color;

    float edge = smoothstep(MOON_RADIUS * 1.05, MOON_RADIUS * 0.95, moonAngle);

    if (useMoonTex == 1) {
        vec3 up = (abs(mDir.y) > 0.999) ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
        vec3 right = normalize(cross(up, mDir));
        vec3 mUp = cross(mDir, right);

        vec2 moonUV = vec2(dot(dir, right), dot(dir, mUp)) / MOON_RADIUS * 0.5 + 0.5;

        if (moonUV.x >= 0.0 && moonUV.x <= 1.0 && moonUV.y >= 0.0 && moonUV.y <= 1.0) {
            vec4 tex = texture(moonTex, moonUV);
            float alpha = tex.a * edge * visibility;
            color = mix(color, tex.rgb, alpha);
        }
    } else {
        float alpha = edge * visibility;
        color = mix(color, vec3(0.80, 0.80, 0.78), alpha);
    }

    return color;
}

float cloudCoverageFromWeather() {
    if (weather <= 0) return 0.0;
    if (weather == 1) return 0.15;
    if (weather == 2) return 0.45;
    return 0.9;
}

vec3 renderClouds(vec3 dir, vec3 skyColor, float sunHeight, float coverage) {
    if (coverage <= 0.0 || dir.y < 0.02) return skyColor;

    float scroll = float(dayTime) * 0.00003;
    vec2 uv = dir.xz / (dir.y + 0.15) * 0.5 + vec2(scroll, scroll * 0.6);

    float density = fbm(uv * 2.0);
    float shading = fbm(uv * 4.0 + 17.0);

    float lowThresh = mix(0.68, 0.15, coverage);
    float highThresh = mix(0.82, 0.42, coverage);
    density = smoothstep(lowThresh, highThresh, density);

    float horizonFade = smoothstep(0.02, 0.25, dir.y);
    density *= horizonFade * coverage;
    density = clamp(density, 0.0, 0.92);

    float cloudLight = smoothstep(-0.10, 0.40, sunHeight);

    vec3 cloudLit = vec3(0.90, 0.88, 0.86);
    vec3 cloudShadow = vec3(0.32, 0.34, 0.40);
    vec3 cloudNight = vec3(0.015, 0.018, 0.030);

    vec3 cloudDay = mix(cloudShadow, cloudLit, shading);
    vec3 cloudColor = mix(cloudNight, cloudDay, cloudLight);

    return mix(skyColor, cloudColor, density);
}

void main() {
    if (useCubeTex == 1) {
        FragColor = texture(cubeTex, TexDir);
        return;
    }

    vec3 dir = normalize(TexDir);
    vec3 sDir = normalize(-sunDir);
    vec3 mDir = normalize(-moonDir);

    float sunHeight = sDir.y;
    float cloudCoverage = cloudCoverageFromWeather();
    float clearness = 1.0 - cloudCoverage * 0.7;

    SkyPalette pal = computeSkyPalette(sunHeight);
    vec3 color = sampleGradient(pal, dir);

    color += renderSun(dir, sDir, sunHeight, clearness);
    color += renderStars(dir, sunHeight, clearness);
    color = renderMoon(dir, mDir, color, clearness);
    color = renderClouds(dir, color, sunHeight, cloudCoverage);

    FragColor = vec4(color, 1.0);
}