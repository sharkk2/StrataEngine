#version 430 core
layout (triangles) in;
layout (triangle_strip, max_vertices = 18) out;

layout(location = 0) in vec2 vUVIn[];

uniform mat4 shadowMatrices[6];

out vec2 vUV;

void main() {
    for (int face = 0; face < 6; face++) {
        gl_Layer = face;
        for (int i = 0; i < 3; i++) {
            gl_Position = shadowMatrices[face] * gl_in[i].gl_Position;
            vUV = vUVIn[i];
            EmitVertex();
        }
        EndPrimitive();
    }
}