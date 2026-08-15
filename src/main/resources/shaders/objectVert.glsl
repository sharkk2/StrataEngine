#version 430 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUV;
layout(location = 2) in vec3 aNormal;
layout(location = 3) in ivec4 aBoneIds;
layout(location = 4) in vec4 aBoneWeights;

layout(std140, binding = 0) uniform Camera {
    mat4 uProjection;
    mat4 uView;
    mat4 uInverseView;
};

layout(std430, binding = 3) readonly buffer BoneMatrices {
    mat4 boneMatrices[];
};


uniform mat4 uModel;
uniform int uSkinned;

out vec2 vUV;
out vec3 vWorldPos;
out vec3 vNormal;

void main() {
    vec4 localPos = vec4(aPos, 1.0);
    vec3 localNormal = aNormal;

    if (uSkinned == 1) {
        mat4 skinMatrix = boneMatrices[aBoneIds.x] * aBoneWeights.x + boneMatrices[aBoneIds.y] * aBoneWeights.y + boneMatrices[aBoneIds.z] * aBoneWeights.z + boneMatrices[aBoneIds.w] * aBoneWeights.w;

        localPos = skinMatrix * localPos;
        localNormal = mat3(skinMatrix) * localNormal;
    }

    vec4 worldPos = uModel * localPos;
    vUV = aUV;
    gl_Position = uProjection * uView * worldPos;
    vNormal = normalize(mat3(transpose(inverse(uModel))) * aNormal);
    vWorldPos = vec3(uModel * vec4(aPos, 1.0));
}