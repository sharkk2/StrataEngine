#version 430 core
#define MAX_CASCADES 4   // keep equal to MAX_LAYERS in CascadedShadowMap and MAX_CASCADES in the lighting shader

layout (triangles, invocations = MAX_CASCADES) in;
layout (triangle_strip, max_vertices = 3) out;

layout(location = 0) in vec2 vUVIn[];
layout(location = 1) in float vOpacityIn[];

uniform mat4 cascadeMatrices[MAX_CASCADES];
uniform int cascadeCount;

out vec2 vUV;
flat out float iOpacity;

void main() {
    if (gl_InvocationID >= cascadeCount) return;

    mat4 m = cascadeMatrices[gl_InvocationID];
    vec4 p[3];
    for (int i = 0; i < 3; i++) p[i] = m * gl_in[i].gl_Position;

    // skip triangles fully outside this cascade's left/right/top/bottom
    if (p[0].x >  p[0].w && p[1].x >  p[1].w && p[2].x >  p[2].w) return;
    if (p[0].x < -p[0].w && p[1].x < -p[1].w && p[2].x < -p[2].w) return;
    if (p[0].y >  p[0].w && p[1].y >  p[1].w && p[2].y >  p[2].w) return;
    if (p[0].y < -p[0].w && p[1].y < -p[1].w && p[2].y < -p[2].w) return;

    for (int i = 0; i < 3; i++) {
        gl_Layer = gl_InvocationID;
        gl_Position = p[i];
        vUV = vUVIn[i];
        iOpacity = vOpacityIn[i];
        EmitVertex();
    }
    EndPrimitive();
}