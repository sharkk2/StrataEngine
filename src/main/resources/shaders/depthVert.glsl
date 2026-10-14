#version 430 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aTexCoords;
layout(location = 3) in ivec4 aBoneIds;
layout(location = 4) in vec4 aBoneWeights;
layout(location = 5) in mat4 iModel;
layout(location = 9) in vec3 aAlbedo;
layout(location = 11) in vec4 aERMO_Material;
layout(location = 12) in vec2 aAlphaMaterial;
layout(location = 13) in int aBoneOffset;

out vec2 TexCoords;

flat out vec3 iAlbedo;
flat out float iOpacity;
flat out vec2 iAlphaMaterial;

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
    iAlbedo = aAlbedo; iOpacity = aERMO_Material.w; iAlphaMaterial = aAlphaMaterial;
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
    TexCoords = aTexCoords;
    gl_Position = projection * view * worldPos;
}