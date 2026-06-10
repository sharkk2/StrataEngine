#version 430 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUV;
layout(location = 2) in vec3 aNormal;

layout(std140, binding = 0) uniform Camera {
    mat4 uProjection;
    mat4 uView;
};

uniform mat4 uModel;

out vec2 vUV;
out vec3 vWorldPos;
out vec3 vNormal;

void main() {

    vec4 worldPos = uModel * vec4(aPos, 1.0);
    //vWorldPos = worldPos.xyz;
    vUV = aUV;
    gl_Position = uProjection * uView * worldPos;
    vNormal = normalize(mat3(transpose(inverse(uModel))) * aNormal);
    vWorldPos = vec3(uModel * vec4(aPos, 1.0));
}