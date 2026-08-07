#version 430 core

in vec2 vUV;

out vec4 FragColor;
uniform sampler2D gPosition;
uniform sampler2D gNormal;
uniform sampler2D gAlbedo;
uniform sampler2D gMaterial;
uniform sampler2D gEmissive;  // RGB16F emissive color, already multiplied by strength in the geometry pass

uniform vec3 dlDirection;
uniform vec3 dlColor;
uniform vec3 ambient;
uniform int dlEnabled;
uniform float dlIntensity;


uniform int globalShadowEnabled;
uniform sampler2DShadow globalShadowTex;
uniform mat4 globalLightSpaceMatrix;

uniform vec3 cameraPos;
uniform vec3 skyColor;

uniform sampler2D ssaoTex;
uniform int ssaoEnabled;

#define MAX_COOKIE_LIGHTS 8

struct Light {
    vec3 position;
    float range;
    vec3 color;
    float intensity;
    vec3 direction;
    float constant;
    float linear;
    float quadratic;
    float innerCutOff;
    float outerCutOff;
    int type;
    int hasCookie;
    int cookieSlot;
    int _pad0; // could be used for a castShadow??? maybe
};

uniform int lightCount;

layout(std430, binding = 2) readonly buffer LightBuffer {
    Light lights[];
};

uniform sampler2D cookieTextures[MAX_COOKIE_LIGHTS];

const vec2 poissonDisk[16] = vec2[](
vec2(-0.94201624, -0.39906216), vec2( 0.94558609, -0.76890725),
vec2(-0.094184101, -0.92938870), vec2( 0.34495938, 0.29387760),
vec2(-0.91588581, 0.45771432), vec2(-0.81544232, -0.87912464),
vec2(-0.38277543, 0.27676845), vec2( 0.97484398, 0.75648379),
vec2( 0.44323325, -0.97511554), vec2( 0.53742981, -0.47373420),
vec2(-0.26496911, -0.41893023), vec2( 0.79197514, 0.19090188),
vec2(-0.24188840, 0.99706507), vec2(-0.81409955, 0.91437590),
vec2( 0.19984126, 0.78641367), vec2( 0.14383161, -0.14100790)
);

float rand(vec4 seed) {
    float dot_product = dot(seed, vec4(12.9898, 78.233, 45.164, 94.673));
    return fract(sin(dot_product) * 43758.5453);
}

float computeShadow(sampler2DShadow map, vec4 posLightSpace, vec3 norm, vec3 lightDir) {
    vec3 projCoords = posLightSpace.xyz / posLightSpace.w;
    projCoords = projCoords * 0.5 + 0.5;
    if (projCoords.z > 1.0) return 1.0;

    float currentDot = max(dot(norm, lightDir), 0.0);
    float tanAcos = sqrt(1.0 - currentDot * currentDot) / currentDot;
    float bias = clamp(0.0005 * tanAcos, 0.0005, 0.015);
    float angle = rand(vec4(gl_FragCoord.xyy, 1.0)) * 6.28318;
    float s = sin(angle);
    float c = cos(angle);
    mat2 rotation = mat2(c, s, -s, c);
    vec2 texelSize = 1.0 / textureSize(map, 0);
    float spread = 2.0;
    float shadow = 0.0;
    for (int i = 0; i < 16; i++) {
        vec2 offset = rotation * poissonDisk[i] * texelSize * spread;
        shadow += texture(map, vec3(projCoords.xy + offset, projCoords.z - bias));
    }
    return shadow / 16.0;
}

layout(std140, binding = 1) uniform Fog {
    vec4 fogColor; // rgb used, fuck alpha
    float fogDensity;
    float fogStart;
    float fogEnd;
    int fogEnabled;
    int fogMode;
    int blendSkyColor;
};

layout(std140, binding = 0) uniform Camera {
    mat4 projection;
    mat4 view;
    mat4 inverseView;
};

uniform int renderingMode;
const float PI = 3.14159265359;

float distributionGGX(vec3 N, vec3 H, float rough) {
    float a = rough * rough;
    float a2 = a * a;
    float NdotH = max(dot(N, H), 0.0);
    float denom = (NdotH * NdotH * (a2 - 1.0) + 1.0);
    return a2 / (PI * denom * denom + 0.0000001);
}

float geometrySchlickGGX(float NdotV, float rough) {
    float r = rough + 1.0;
    float k = (r * r) / 8.0;
    return NdotV / (NdotV * (1.0 - k) + k);
}

float geometrySmith(float NdotV, float NdotL, float rough) {
    return geometrySchlickGGX(NdotV, rough) * geometrySchlickGGX(NdotL, rough);
}

vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

vec3 fresnelSchlickRoughness(float cosTheta, vec3 F0, float rough) {
    return F0 + (max(vec3(1.0 - rough), F0) - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

void main() {

    vec3 viewPos = texture(gPosition, vUV).rgb;
    vec3 vWorldPos = (inverseView * vec4(viewPos, 1.0)).xyz;
    vec3 vNormal = texture(gNormal, vUV).rgb;

    vec4 albedoSample = texture(gAlbedo, vUV);
    vec3 mat_albedo = albedoSample.rgb;
    float mat_opacity = albedoSample.a;

    vec4 materialSample = texture(gMaterial, vUV);
    float mat_metalness = materialSample.r;
    float mat_roughness = clamp(materialSample.g, 0.04, 1.0);
    float mat_ao = materialSample.b;
    float ssao = ssaoEnabled == 1 ? texture(ssaoTex, vUV).r : 1.0;

    float combinedAO = mat_ao * ssao;

    vec3 mat_emissive = texture(gEmissive, vUV).rgb;

    switch (renderingMode) {
        case 1: FragColor = vec4(mat_albedo, mat_opacity); return;
        case 2: FragColor = vec4(vec3(mat_metalness), 1); return;
        case 3: FragColor = vec4(vec3(mat_roughness), 1); return;
        case 4: FragColor = vec4(normalize(vNormal) * 0.5 + 0.5, 1); return;
        case 5: FragColor = vec4(mat_emissive, 1); return;
        case 6: FragColor = vec4(vec3(combinedAO), 1); return;
        case 7: FragColor = vec4(vec3(mat_opacity), 1); return;
    }

    vec3 norm = normalize(vNormal);
    vec3 V = normalize(cameraPos - vWorldPos);
    float NdotV = max(dot(norm, V), 0.0);

    vec3 F0 = mix(vec3(0.04), mat_albedo, mat_metalness);
    vec3 Lo = vec3(0.0);

    if (dlEnabled == 1) {
        vec3 L = normalize(-dlDirection);
        vec3 H = normalize(V + L);
        float NdotL = max(dot(norm, L), 0.0);

        float D = distributionGGX(norm, H, mat_roughness);
        float G = geometrySmith(NdotV, NdotL, mat_roughness);
        vec3 F = fresnelSchlick(max(dot(H, V), 0.0), F0);

        vec3 specular = (D * G * F) / (4.0 * NdotV * NdotL + 0.0001);
        vec3 kD = (vec3(1.0) - F) * (1.0 - mat_metalness);
        vec3 diffuse = kD * mat_albedo / PI;

        float shadowFactor = 1.0;
        if (globalShadowEnabled == 1) {
            vec4 posLightSpace = globalLightSpaceMatrix * vec4(vWorldPos, 1.0);
            shadowFactor = computeShadow(globalShadowTex, posLightSpace, norm, L);
        }

        Lo += (diffuse + specular) * dlColor * NdotL * dlIntensity * shadowFactor;
    }

    for (int i = 0; i < lightCount; i++) {
        Light light = lights[i];
        float dist = length(light.position - vWorldPos);
        if (dist > light.range) continue;

        vec3 L = normalize(light.position - vWorldPos);
        vec3 H = normalize(V + L);
        float NdotL = max(dot(norm, L), 0.0);
        float attenuation = 1.0 / (light.constant + light.linear * dist + light.quadratic * dist * dist);
        float rangeFalloff = clamp(1.0 - pow(dist / light.range, 4.0), 0.0, 1.0);
        rangeFalloff *= rangeFalloff;

        float spotFactor = 1.0;
        if (light.type == 1) {
            float theta = dot(L, normalize(-light.direction));
            float epsilon = light.innerCutOff - light.outerCutOff;
            spotFactor = clamp((theta - light.outerCutOff) / epsilon, 0.0, 1.0);
        }

        float D = distributionGGX(norm, H, mat_roughness);
        float G = geometrySmith(NdotV, NdotL, mat_roughness);
        vec3 F = fresnelSchlick(max(dot(H, V), 0.0), F0);

        vec3 specular = (D * G * F) / (4.0 * NdotV * NdotL + 0.0001);
        vec3 kD = (vec3(1.0) - F) * (1.0 - mat_metalness);
        vec3 diffuse = kD * mat_albedo / PI;

        vec3 cookieColor = vec3(1.0);
        if (light.type == 1 && light.hasCookie == 1) {
            vec3 lightFwd = normalize(light.direction);
            vec3 worldUp = abs(lightFwd.y) < 0.999 ? vec3(0, 1, 0) : vec3(1, 0, 0);
            vec3 right = normalize(cross(worldUp, lightFwd));
            vec3 up = cross(lightFwd, right);
            vec3 toFrag = vWorldPos - light.position;
            float fwdDist = max(dot(toFrag, lightFwd), 0.0001);
            float tanOuter = sqrt(1.0 - light.outerCutOff * light.outerCutOff) / light.outerCutOff;
            float scale = fwdDist * tanOuter;

            vec2 cookieUV = vec2(dot(toFrag, right), dot(toFrag, up)) / scale * 0.5 + 0.5;
            cookieColor = texture(cookieTextures[light.cookieSlot], cookieUV).rgb;
        }

        Lo += (diffuse + specular) * light.color * light.intensity * NdotL * attenuation * rangeFalloff * spotFactor * cookieColor;
    }

    vec3 F_ambient = fresnelSchlickRoughness(NdotV, F0, mat_roughness);
    vec3 kD_ambient = (vec3(1.0) - F_ambient) * (1.0 - mat_metalness);
    float ambientSpecularStrength = 1.0 / (mat_roughness * mat_roughness * 4.0 + 1.0);
    vec3 ambientColor = (kD_ambient * mat_albedo + F_ambient * ambientSpecularStrength) * ambient * combinedAO;

    vec3 finalColor = Lo + ambientColor + mat_emissive;

    if (fogEnabled == 1) {
        float fragDist = length(cameraPos - vWorldPos);

        float fogFactor;
        if (fogMode == 0) {
            fogFactor = clamp((fogEnd - fragDist) / (fogEnd - fogStart), 0.0, 1.0);
        } else {
            float d = fogDensity * fragDist;
            fogFactor = exp(-(d * d));
        }

        vec3 baseFogColor = fogColor.rgb;
        if (blendSkyColor == 1) {
            baseFogColor = pow(skyColor, vec3(2.2)) + dlColor * 0.25;
        }

        finalColor = mix(baseFogColor, finalColor, fogFactor);
    }

    FragColor = vec4(finalColor, mat_opacity);
}
