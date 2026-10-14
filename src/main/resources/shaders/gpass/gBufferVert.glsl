#version 430 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aTexCoords;
layout(location = 2) in vec3 aNormal;
layout(location = 3) in ivec4 aBoneIds;
layout(location = 4) in vec4 aBoneWeights;
layout(location = 5) in mat4 iModel;  // takes locations 5, 6, 7, 8
layout(location = 9) in vec3 aAlbedo;
layout(location = 10) in vec3 aEmissive;
layout(location = 11) in vec4 aERMO_Material;
layout(location = 13) in int aBoneOffset;

flat out vec3 iAlbedo;
flat out vec3 iEmissive;
flat out vec4 iERMO_Material;

out vec3 FragPos;
out vec2 TexCoords;
out vec3 WorldNormal;

layout(std140, binding = 0) uniform Camera {
    mat4 projection;
    mat4 view;
    mat4 inverseView;
    vec3 cameraPos;
};

layout(std430, binding = 3) readonly buffer BoneMatrices {
    mat4 bones[];
};

uniform mat4 uModel;
uniform int uInstanced;
uniform int uSkinned;
uniform int uBoneOffset;

void main() {
    iAlbedo = aAlbedo; iEmissive = aEmissive; iERMO_Material = aERMO_Material;
    int bOffset = (uInstanced == 1) ? aBoneOffset : uBoneOffset;

    mat4 model = (uInstanced == 1) ? iModel : uModel;
    vec4 localPos = vec4(aPos, 1.0);
    vec3 localNormal = aNormal;

    if (uSkinned == 1) {
        mat4 skinMatrix = bones[bOffset + int(aBoneIds.x)] * aBoneWeights.x
        + bones[bOffset + int(aBoneIds.y)] * aBoneWeights.y
        + bones[bOffset + int(aBoneIds.z)] * aBoneWeights.z
        + bones[bOffset + int(aBoneIds.w)] * aBoneWeights.w;
        localPos = skinMatrix * localPos;
        localNormal = mat3(skinMatrix) * localNormal;
    }

    vec4 worldPos = model * localPos;
    FragPos = worldPos.xyz;
    TexCoords = aTexCoords;
    WorldNormal = normalize(mat3(transpose(inverse(model))) * localNormal);
    gl_Position = projection * view * worldPos;
}