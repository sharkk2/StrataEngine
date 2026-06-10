#version 430 core

in vec2 vUV;
in vec3 vWorldPos;
in vec3 vNormal;

out vec4 FragColor;


uniform vec3 direction;
uniform vec3 color;
uniform vec3 ambient;
uniform int enabled;

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

uniform vec3 cameraPos;

uniform int useAlbedoTex;
uniform int useMetalnessTex;
uniform int useRoughnessTex;
uniform int useNormalTex;
uniform int useEmissiveTex;
uniform int useAoTex;
uniform int useOpacityTex;


#define MAX_LIGHTS 16

struct Light {
    int type;
    vec3 position;
    vec3 color;
    vec3 direction;
    float range;
    float intensity;
    float constant;
    float linear;
    float quadratic;
    float innerCutOff;
    float outerCutOff;
    int hasCookie;
};

uniform int lightCount;
uniform Light lights[MAX_LIGHTS];
uniform sampler2D cookieTextures[MAX_LIGHTS];


layout(std140, binding = 1) uniform Fog {
    vec3 fogColor;
    float fogDensity;
    float fogStart;
    float fogEnd;
    int fogEnabled;
    int fogMode;
};

uniform int renderingMode;
// 0: normal, 1: albedo only, 2: metalness, 3: roughness, 4: normal, 5: emissive, 6: ao, 7: opacity

const float PI = 3.14159265359;

// GGX / Trowbridge-Reitz normal distribution function
float distributionGGX(vec3 N, vec3 H, float rough) {
    float a = rough * rough;
    float a2 = a * a;
    float NdotH = max(dot(N, H), 0.0);
    float denom = (NdotH * NdotH * (a2 - 1.0) + 1.0);
    return a2 / (PI * denom * denom + 0.0000001);
}

// Schlick-GGX geometry term for a single direction
float geometrySchlickGGX(float NdotV, float rough) {
    float r = rough + 1.0;
    float k = (r * r) / 8.0;
    return NdotV / (NdotV * (1.0 - k) + k);
}

// Smith's combined shadowing-masking term
float geometrySmith(float NdotV, float NdotL, float rough) {
    return geometrySchlickGGX(NdotV, rough) * geometrySchlickGGX(NdotL, rough);
}

// Fresnel-Schlick approximation
vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}




void main() {
    vec4 mat_albedo = (useAlbedoTex == 1) ? texture(albedoTex, vUV): vec4(albedo, opacity);
    mat_albedo.rgb = pow(mat_albedo.rgb, vec3(2.2));
    float mat_metalness = metalness;
    float mat_roughness = roughness;
    float mat_ao = (useAoTex == 1) ? texture(aoTex, vUV).r : 1.0;
    if (isPackedORM == 1) {
        vec4 packedORM = texture(roughnessTex, vUV);

        mat_ao = packedORM.r; // R = Ambient Occlusion
        mat_roughness = packedORM.g; // G = Roughness
        mat_metalness = packedORM.b; // B = Metalness
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
    if (mat_albedo.a < 0.1f) discard;

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

    if (enabled == 1) {
        vec3 L = normalize(-direction);
        vec3 H = normalize(V + L);
        float NdotL = max(dot(norm, L), 0.0);

        float D = distributionGGX(norm, H, mat_roughness);
        float G = geometrySmith(NdotV, NdotL, mat_roughness);
        vec3 F = fresnelSchlick(max(dot(H, V), 0.0), F0);

        vec3 specular = (D * G * F) / (4.0 * NdotV * NdotL + 0.0001);
        vec3 kD = (vec3(1.0) - F) * (1.0 - mat_metalness);
        vec3 diffuse = kD * mat_albedo.rgb / PI;

        Lo += (diffuse + specular) * color * NdotL * 4;
    }

    for (int i = 0; i < lightCount; i++) {
        Light light = lights[i];

        vec3 L = normalize(light.position - vWorldPos);
        vec3 H = normalize(V + L);
        float NdotL = max(dot(norm, L), 0.0);

        float dist = length(light.position - vWorldPos);
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

            vec2 cookieUV = vec2(dot(toFrag, right), -dot(toFrag, up)) / scale * 0.5 + 0.5;
            cookieColor = texture(cookieTextures[i], cookieUV).rgb;
        }

        Lo += (diffuse + specular) * light.color * light.intensity * NdotL * attenuation * rangeFalloff * spotFactor * cookieColor;
    }

    // note: ambient is leaking as rim lighting here

    vec3 F_ambient = fresnelSchlick(NdotV, F0);
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
            fogFactor = exp(-d * d);
        }

        vec3 fogLinear = pow(fogColor, vec3(2.2));
        finalColor = mix(fogLinear, finalColor, fogFactor);
    }

    finalColor = finalColor / (finalColor + vec3(1.0));
    FragColor = vec4(finalColor, mat_albedo.a);
}
