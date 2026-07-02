#version 430 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUV;


uniform mat4 spaceMatrix;
uniform mat4 model;
out vec2 vUV;

void main() {
    gl_Position = spaceMatrix * model * vec4(aPos, 1);
    vUV = aUV;
}