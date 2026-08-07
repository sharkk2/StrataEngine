#version 430 core

out float FragColor;
in vec2 TexCoords;

uniform sampler2D gPosition;
uniform sampler2D gNormal;
uniform sampler2D texNoise;

#define KERNEL_SIZE 64
uniform int kernelSize;

uniform vec3 samples[KERNEL_SIZE];

layout(std140, binding = 0) uniform Camera {
    mat4 projection;
    mat4 view;
    mat4 inverseView;
};

uniform float noiseScaleX;
uniform float noiseScaleY;
uniform float radius;
uniform float bias;

void main() {
    vec3 fragViewPos = texture(gPosition, TexCoords).xyz;
    if (fragViewPos == vec3(0.0)) {
        FragColor = 1.0;
        return;
    }

    // if gNormal is world-space, keep mat3(view)*; if it's already view-space, remove it
    vec3 normalView = normalize(mat3(view) * texture(gNormal, TexCoords).xyz);

    vec2 noiseScale = vec2(noiseScaleX, noiseScaleY);
    vec3 randomVec = texture(texNoise, TexCoords * noiseScale).rgb;

    vec3 tangent = normalize(randomVec - normalView * dot(randomVec, normalView));
    vec3 bitangent = cross(normalView, tangent);
    mat3 TBN = mat3(tangent, bitangent, normalView);

    float occlusion = 0.0;
    float maxViewOffset = radius;
    float radiusScreenClamp = 0.03;
    for (int i = 0; i < kernelSize; i++) {
        vec3 rawOffset = TBN * samples[i] * radius;

        float viewDepth = abs(fragViewPos.z);
        float maxOffsetLen = radiusScreenClamp * viewDepth;
        float offsetLen = length(rawOffset);
        if (offsetLen > maxOffsetLen) {
            rawOffset = rawOffset * (maxOffsetLen / offsetLen);
        }

        vec3 sampleViewPos = fragViewPos + rawOffset;

        vec4 offset = projection * vec4(sampleViewPos, 1.0);
        offset.xyz /= offset.w;
        offset.xyz = offset.xyz * 0.5 + 0.5;

        if (offset.x < 0.0 || offset.x > 1.0 || offset.y < 0.0 || offset.y > 1.0) continue;
        float geomViewZ = texture(gPosition, offset.xy).z;
        if (geomViewZ == 0.0) continue;

        float rangeCheck = smoothstep(0.0, 1.0, radius / abs(fragViewPos.z - geomViewZ));
        vec3 viewDir = normalize(-fragViewPos);
        float slopeBias = max(bias, bias * (1.0 - dot(normalView, viewDir)));
        occlusion += (geomViewZ >= sampleViewPos.z + slopeBias ? 1.0 : 0.0) * rangeCheck;
    }

    FragColor = 1.0 - (occlusion / float(kernelSize));
}