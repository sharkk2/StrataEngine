#version 430 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUV;
layout(location = 3) in ivec4 aBoneIds;
layout(location = 4) in vec4 aBoneWeights;

layout(std430, binding = 3) readonly buffer BoneMatrices {
    mat4 boneMatrices[];
};

uniform mat4 spaceMatrix;
uniform mat4 model;
out vec2 vUV;
uniform int uSkinned;


void main() {
    vec4 localPos = vec4(aPos, 1.0);

    if (uSkinned == 1) {
        mat4 skinMatrix = boneMatrices[aBoneIds.x] * aBoneWeights.x + boneMatrices[aBoneIds.y] * aBoneWeights.y + boneMatrices[aBoneIds.z] * aBoneWeights.z + boneMatrices[aBoneIds.w] * aBoneWeights.w;
        localPos = skinMatrix * localPos;
    }

    gl_Position = spaceMatrix * model * localPos;
    vUV = aUV;
}