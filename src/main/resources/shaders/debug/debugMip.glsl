#version 430 core

uniform sampler2D srcTexture;
uniform int mip;

in vec2 TexCoord;
out vec4 fragColor;

void main() {
    vec3 color = textureLod(srcTexture, TexCoord, float(mip)).rgb;
    fragColor = vec4(color, 1.0);
}