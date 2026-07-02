#version 430 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aTexCoords;
layout(location = 2) in vec3 aNormal;

out vec3 FragPos;
out vec2 TexCoords;
out vec3 WorldNormal;

layout(std140, binding = 0) uniform Camera {
    mat4 projection;
    mat4 view;
    mat4 inverseView;
};

uniform mat4 uModel;

void main() {
    vec4 worldPos = uModel * vec4(aPos, 1.0);
    FragPos = worldPos.xyz;
    TexCoords = aTexCoords;
    WorldNormal = normalize(mat3(transpose(inverse(uModel))) * aNormal);
    gl_Position = projection * view * worldPos;
}