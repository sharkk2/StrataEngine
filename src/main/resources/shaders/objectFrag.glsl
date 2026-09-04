#version 430 core
#extension GL_ARB_bindless_texture : enable

in vec2 vUV;
in vec3 vWorldPos;
in vec3 vNormal;

out vec4 FragColor;


uniform vec3 dlDirection;
uniform vec3 dlColor;
uniform vec3 ambient;
uniform int dlEnabled;
uniform float dlIntensity;

uniform int globalShadowEnabled;
uniform sampler2DShadow globalShadowTex;
uniform mat4 globalLightSpaceMatrix;

uniform sampler2D albedoTex;
uniform vec3 albedo;

uniform sampler2D metalnessTex;
uniform float metalness;

uniform sampler2D roughnessTex;
uniform float roughness;

uniform sampler2D normalTex;

uniform sampler2D emissiveTex;
uniform vec3 emissive;
uniform float emissiveStrength;

uniform sampler2D aoTex;
uniform sampler2D opacityTex;
uniform float opacity;
uniform int isPackedORM;

uniform sampler2D alphaMaskTex;
uniform float alphaMaskThreshold;
uniform int alphaCutout;

uniform vec3 cameraPos;
uniform vec3 skyColor;

uniform int useAlbedoTex;
uniform int useMetalnessTex;
uniform int useRoughnessTex;
uniform int useNormalTex;
uniform int useEmissiveTex;
uniform int useAoTex;
uniform int useOpacityTex;
uniform int useAlphaMaskTex;


uniform int lightEnabled;
#define MAX_COOKIE_LIGHTS 64
#define MAX_SHADOW_MAPS 64

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
    int shadowSlot;
    mat4 lightSpaceMatrix;
};

uniform int lightCount;

layout(std430, binding = 2) readonly buffer LightBuffer {
    Light lights[];
};

uniform sampler2D cookieTextures[MAX_COOKIE_LIGHTS];
uniform sampler2DShadow shadowTextures[MAX_SHADOW_MAPS];
uniform samplerCubeShadow shadowCubeTextures[MAX_SHADOW_MAPS];

layout(std140, binding = 1) uniform Fog {
    vec4 fogColor; // rgb used, fuck alpha
    float fogDensity;
    float fogStart;
    float fogEnd;
    int fogEnabled;
    int fogMode;
    int blendSkyColor;
};


uniform int renderingMode;
const float PI = 3.14159265359;

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
    if (posLightSpace.w <= 0.0) return 1.0;

    vec3 projCoords = posLightSpace.xyz / posLightSpace.w;
    projCoords = projCoords * 0.5 + 0.5;
    if (projCoords.z > 1.0) return 1.0;

    float currentDot = max(dot(norm, lightDir), 0.0);
    float tanAcos = sqrt(1.0 - currentDot * currentDot) / currentDot;
    float bias = 0.0005;
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

float computeCubeShadow(samplerCubeShadow map, vec3 fragToLight, float farPlane, vec3 norm, vec3 lightDir) {
    float localZ = max(abs(fragToLight.x), max(abs(fragToLight.y), abs(fragToLight.z)));
    if (localZ > farPlane) return 1.0;

    float near = 0.1;
    float ndcDepth = (farPlane + near) / (farPlane - near) - (2.0 * farPlane * near) / ((farPlane - near) * localZ);
    float refDepth = ndcDepth * 0.5 + 0.5;

    float bias = 0.0008;
    return texture(map, vec4(fragToLight, refDepth - bias));
}




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
    vec4 mat_albedo = (useAlbedoTex == 1) ? texture(albedoTex, vUV): vec4(albedo, opacity);
    mat_albedo.rgb = pow(mat_albedo.rgb, vec3(2.2));
    float mat_metalness = metalness;
    float mat_roughness = roughness;
    float mat_ao = (useAoTex == 1) ? texture(aoTex, vUV).r : 1.0;

    if (isPackedORM == 1) {
        vec4 packedORM = texture(roughnessTex, vUV);
        mat_ao = packedORM.r;
        mat_roughness = packedORM.g;
        mat_metalness = packedORM.b;
    } else {
        if (useMetalnessTex == 1) mat_metalness = texture(metalnessTex, vUV).r;
        if (useRoughnessTex == 1) mat_roughness = texture(roughnessTex, vUV).r;
    }

    mat_roughness = clamp(mat_roughness, 0.04, 1.0);
    vec3 mat_emissive = (useEmissiveTex == 1)? pow(texture(emissiveTex, vUV).rgb, vec3(2.2)): emissive;

    if (useOpacityTex == 1) {
        mat_albedo.a = texture(opacityTex, vUV).r;
    } else if (opacity != 1) {
        mat_albedo.a = opacity;
    }


    switch (renderingMode) {
        case 1: FragColor = mat_albedo; break;
        case 2: FragColor = vec4(vec3(mat_metalness), 1); break;
        case 3: FragColor = vec4(vec3(mat_roughness), 1); break;
        case 4: {
            if (useNormalTex == 1) {FragColor = vec4(texture(normalTex, vUV).rgb * 2.0 - 1.0, 1);} break;
        }
        case 5: FragColor = vec4(mat_emissive, 1); break;
        case 6: FragColor = vec4(vec3(mat_ao), 1); break;
        case 7: FragColor = vec4(vec3(mat_albedo.a), 1); break;
    }
    if (renderingMode != 0) return;
    if (useAlphaMaskTex == 1) {
        if (texture(alphaMaskTex, vUV).r < alphaMaskThreshold) discard;
    } else if (alphaCutout == 1) {
        if (mat_albedo.a < alphaMaskThreshold) discard;
    } else {
        if (mat_albedo.a < 0.1f) discard;
    }

    if (lightEnabled == 0) {
        FragColor = mat_albedo;
        return;
    }

    vec3 norm;
    if (useNormalTex == 1) {
        vec3 sampledNormal = texture(normalTex, vUV).rgb * 2.0 - 1.0;
        if (renderingMode == 4) {
            FragColor = vec4(sampledNormal, 1);
            return;
        }

        vec3 N = normalize(vNormal);
        vec3 dp1 = dFdx(vWorldPos);
        vec3 dp2 = dFdy(vWorldPos);
        vec2 duv1 = dFdx(vUV);
        vec2 duv2 = dFdy(vUV);
        float det = duv1.x * duv2.y - duv2.x * duv1.y;
        float invDet = (abs(det) > 0.00001) ? 1.0 / det : 1.0;
        vec3 T = normalize((dp1 * duv2.y - dp2 * duv1.y) * invDet);
        T = normalize(T - dot(T, N) * N);
        vec3 B = cross(N, T);
        mat3 TBN = mat3(T, B, N);
        norm = normalize(TBN * sampledNormal);
    } else {
        norm = normalize(vNormal);
    }

    vec3 V = normalize(cameraPos - vWorldPos);
    float NdotV = max(dot(norm, V), 0.0);

    vec3 F0 = mix(vec3(0.04), mat_albedo.rgb, mat_metalness);
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
        vec3 diffuse = kD * mat_albedo.rgb / PI;

        float shadowFactor = 1.0;
        if (globalShadowEnabled == 1) {
            vec4 posLightSpace = globalLightSpaceMatrix * vec4(vWorldPos + norm * 0.02, 1.0);
            shadowFactor = computeShadow(globalShadowTex, posLightSpace, norm, L);
        }

        Lo += (diffuse + specular) * dlColor * NdotL * dlIntensity * shadowFactor;
    }

    // Local Lights Calculation (Points/Spots)
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
        vec3 diffuse = kD * mat_albedo.rgb / PI;

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

        float localShadowFactor = 1.0;
        if (light.shadowSlot >= 0) {
            if (light.type == 1) {
                vec4 posLightSpace = light.lightSpaceMatrix * vec4(vWorldPos + norm * 0.02, 1.0);
                localShadowFactor = computeShadow(shadowTextures[light.shadowSlot], posLightSpace, norm, L);
            } else {
                vec3 fragToLight = vWorldPos - light.position;
                localShadowFactor = computeCubeShadow(shadowCubeTextures[light.shadowSlot], fragToLight, light.range, norm, L);
            }
        }

        Lo += (diffuse + specular) * light.color * light.intensity * NdotL * attenuation * rangeFalloff * spotFactor * cookieColor * localShadowFactor;
    }

    vec3 F_ambient = fresnelSchlickRoughness(NdotV, F0, mat_roughness);
    vec3 kD_ambient = (vec3(1.0) - F_ambient) * (1.0 - mat_metalness);
    float ambientSpecularStrength = 1.0 / (mat_roughness * mat_roughness * 4.0 + 1.0);
    vec3 ambientColor = (kD_ambient * mat_albedo.rgb + F_ambient * ambientSpecularStrength) * ambient * mat_ao;
    vec3 emissiveColor = mat_emissive * emissiveStrength;

    vec3 finalColor = Lo + ambientColor + emissiveColor;

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

    FragColor = vec4(finalColor, mat_albedo.a);
}