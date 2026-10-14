#version 430 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUV;
layout(location = 3) in ivec4 aBoneIds;
layout(location = 4) in vec4 aBoneWeights;
layout(location = 5) in mat4 iModel;
layout(location = 9) in float aOpacity;
layout(location = 13) in int aBoneOffset;


layout(std430, binding = 3) readonly buffer BoneMatrices {
    mat4 bones[];
};

uniform mat4 spaceMatrix;
uniform mat4 uModel;
uniform int uSkinned;
uniform int uBoneOffset;
uniform int uInstanced;
uniform int uCubePass;

layout(location = 0) out vec2 vUV;
layout(location = 1) flat out float iOpacity;

void main() {
    iOpacity = aOpacity;
    int bOffset = (uInstanced == 1) ? aBoneOffset : uBoneOffset;
    mat4 model = (uInstanced == 1) ? iModel : uModel;
    vec4 localPos = vec4(aPos, 1.0);

    if (uSkinned == 1) {
        mat4 skinMatrix = bones[bOffset + int(aBoneIds.x)] * aBoneWeights.x
        + bones[bOffset + int(aBoneIds.y)] * aBoneWeights.y
        + bones[bOffset + int(aBoneIds.z)] * aBoneWeights.z
        + bones[bOffset + int(aBoneIds.w)] * aBoneWeights.w;
        localPos = skinMatrix * localPos;
    }

    vec4 worldPos = model * localPos;
    gl_Position = uCubePass == 1 ? worldPos : spaceMatrix * worldPos;
    vUV = aUV;
}