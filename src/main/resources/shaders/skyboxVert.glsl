#version 430 core

layout (location = 0) in vec3 aPos;
out vec3 TexDir;

layout(std140, binding = 0) uniform Camera {
    mat4 projection;
    mat4 view;
    mat4 inverseView;
};

void main() {
    mat4 rotView = mat4(mat3(view));
    vec4 pos = projection * rotView * vec4(aPos, 1.0);
    TexDir = aPos;

    gl_Position = pos.xyww;
}
