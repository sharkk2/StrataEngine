#version 430 core

in vec3 TexDir;
out vec4 FragColor;

uniform int useTexture;
uniform samplerCube skybox;
uniform vec3 sunDir;

int useDayNightCycle = 1;

void main() {
    if (useTexture == 1) {
        FragColor = texture(skybox, TexDir);
        return;
    }

    vec3 dir = normalize(TexDir);
    vec3 sDir = normalize(-sunDir);

    float sunHeight = sDir.y;

    vec3 zenithColor, midColor, horizonColor, hazeColor;

    if (useDayNightCycle == 1) {
        float dayBlend = clamp(sunHeight * 2.0, 0.0, 1.0);
        float sunsetBlend = clamp(1.0 - abs(sunHeight) * 2.0, 0.0, 1.0);

        vec3 dayZenith = vec3(0.08, 0.15, 0.45);
        vec3 dayMid = vec3(0.25, 0.52, 0.85);
        vec3 dayHorizon = vec3(0.72, 0.84, 0.98);
        vec3 dayHaze = vec3(0.9, 0.75, 0.6);

        vec3 sunsetZenith = vec3(0.05, 0.05, 0.25);
        vec3 sunsetMid = vec3(0.6, 0.25, 0.1);
        vec3 sunsetHorizon = vec3(1.0, 0.45, 0.1);
        vec3 sunsetHaze = vec3(1.0, 0.4, 0.15);

        vec3 nightZenith = vec3(0.01, 0.01, 0.05);
        vec3 nightMid = vec3(0.02, 0.02, 0.08);
        vec3 nightHorizon = vec3(0.04, 0.04, 0.12);
        vec3 nightHaze = vec3(0.03, 0.03, 0.1);

        zenithColor = mix(mix(nightZenith, sunsetZenith, dayBlend), dayZenith, dayBlend);
        midColor = mix(mix(nightMid, sunsetMid, dayBlend), dayMid, dayBlend);
        horizonColor = mix(mix(nightHorizon, sunsetHorizon, dayBlend), dayHorizon, dayBlend);
        hazeColor = mix(mix(nightHaze, sunsetHaze, dayBlend), dayHaze, dayBlend);

        zenithColor = mix(zenithColor, sunsetZenith, sunsetBlend * 0.5);
        horizonColor = mix(horizonColor, sunsetHorizon, sunsetBlend * 0.8);
        hazeColor = mix(hazeColor, sunsetHaze, sunsetBlend * 0.9);

    } else {
        zenithColor = vec3(0.08, 0.15, 0.45);
        midColor = vec3(0.25, 0.52, 0.85);
        horizonColor = vec3(0.72, 0.84, 0.98);
        hazeColor = vec3(0.9, 0.75, 0.6);
    }

    float t = clamp(dir.y, 0.0, 1.0);

    vec3 skyColor = mix(horizonColor, midColor, smoothstep(0.0, 0.25, t));
    skyColor = mix(skyColor, zenithColor, smoothstep(0.15, 1.0, t));

    float hazeBand = exp(-t * 20.0) * clamp(1.0 - abs(dir.y) * 6.0, 0.0, 1.0);
    skyColor = mix(skyColor, hazeColor, hazeBand * 0.5);

    float sunDot = dot(dir, sDir);
    float sunAngle = acos(clamp(sunDot, -1.0, 1.0));

    float sunVisibility = useDayNightCycle == 1 ? clamp(sunHeight * 10.0, 0.0, 1.0) : 1.0;

    float discRadius = 0.035;
    float discEdge = smoothstep(discRadius, discRadius * 0.7, sunAngle);

    vec3 sunDiscColor = useDayNightCycle == 1
    ? mix(vec3(1.0, 0.4, 0.1), vec3(1.0, 0.97, 0.88), clamp(sunHeight * 3.0, 0.0, 1.0))
    : vec3(1.0, 0.97, 0.88);

    float innerCorona = exp(-sunAngle * 80.0) * 0.9;
    float outerGlow = exp(-sunAngle * 12.0) * 0.4;
    float mie = pow(max(sunDot, 0.0), 6.0) * 0.6;

    vec3 sunContrib = vec3(0.0);
    vec3 innerCoronaColor = vec3(8,7,5.5);
    vec3 glowColor = vec3(1.0, 0.85, 0.55);
    vec3 mieColor = vec3(1.0, 0.75, 0.45);

    sunContrib += mieColor * mie;
    sunContrib += glowColor * outerGlow;
    sunContrib += innerCoronaColor * innerCorona;
    sunContrib += sunDiscColor * discEdge * 20.0;
    sunContrib *= sunVisibility;

    vec3 starDir = normalize(TexDir);
    vec3 absDir = abs(starDir);
    vec2 faceUV;
    float faceID;

    if (absDir.x >= absDir.y && absDir.x >= absDir.z) {
        faceUV = starDir.yz / absDir.x;
        faceID = starDir.x > 0.0 ? 0.0 : 1.0;
    } else if (absDir.y >= absDir.x && absDir.y >= absDir.z) {
        faceUV = starDir.xz / absDir.y;
        faceID = starDir.y > 0.0 ? 2.0 : 3.0;
    } else {
        faceUV = starDir.xy / absDir.z;
        faceID = starDir.z > 0.0 ? 4.0 : 5.0;
    }

    vec2 cell2D = floor(faceUV * 40.0);
    vec3 cellSeed = vec3(cell2D, faceID);

    float rand  = fract(sin(dot(cellSeed, vec3(127.1, 311.7, 74.7))) * 43758.5453);
    float rand2 = fract(sin(dot(cellSeed, vec3(269.5, 183.3, 246.1))) * 43758.5453);
    float rand3 = fract(sin(dot(cellSeed, vec3(113.5, 271.9, 124.6))) * 43758.5453);

    float starBrightness = 0.0;

    if (rand > 0.95) {
        vec2 cellUV = fract(faceUV * 40.0) - 0.5;
        cellUV -= (vec2(rand2, rand3) - 0.5) * 0.6;

        float dist = length(cellUV);
        float size = mix(0.04, 0.12, rand2);

        starBrightness = smoothstep(size, size * 0.3, dist);
        starBrightness *= mix(0.4, 1.0, rand3);
    }

    float twinkle = sin(rand * 63.7 + rand2 * 91.3) * 0.5 + 0.5;
    starBrightness *= mix(0.7, 1.0, twinkle);

    float starVisibility = useDayNightCycle == 1 ? clamp(-sunHeight * 4.0, 0.0, 1.0) : 0.0;
    starVisibility *= clamp(dir.y * 10.0, 0.0, 1.0);

    vec3 starColor = mix(vec3(0.8, 0.9, 1.0), vec3(1.0, 0.85, 0.7), rand2);
    vec3 starContrib = starColor * starBrightness * 2.5 * starVisibility;
    vec3 clearColor = skyColor + sunContrib + starContrib;
    float dayFactor = clamp(sunHeight * 0.5 + 0.5, 0.0, 1.0);
    vec3 finalColor = clearColor;

    FragColor = vec4(finalColor, 1.0);
}