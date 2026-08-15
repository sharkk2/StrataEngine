#version 430 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aTexCoords;
layout(location = 2) in vec3 aNormal;
layout(location = 3) in ivec4 aBoneIds;
layout(location = 4) in vec4 aBoneWeights;

out vec3 FragPos;
out vec2 TexCoords;
out vec3 WorldNormal;

layout(std140, binding = 0) uniform Camera {
    mat4 projection;
    mat4 view;
    mat4 inverseView;
};

layout(std430, binding = 3) readonly buffer BoneMatrices {
    mat4 boneMatrices[];
};

uniform mat4 uModel;
uniform int uSkinned;

void main() {
    vec4 localPos = vec4(aPos, 1.0);
    vec3 localNormal = aNormal;

    if (uSkinned == 1) {
        mat4 skinMatrix = boneMatrices[aBoneIds.x] * aBoneWeights.x + boneMatrices[aBoneIds.y] * aBoneWeights.y + boneMatrices[aBoneIds.z] * aBoneWeights.z + boneMatrices[aBoneIds.w] * aBoneWeights.w;
        localPos = skinMatrix * localPos;
        localNormal = mat3(skinMatrix) * localNormal;
    }

    vec4 worldPos = uModel * localPos;
    FragPos = worldPos.xyz;
    TexCoords = aTexCoords;
    WorldNormal = normalize(mat3(transpose(inverse(uModel))) * localNormal);
    gl_Position = projection * view * worldPos;
}