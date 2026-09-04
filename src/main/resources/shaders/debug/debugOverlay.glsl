#version 430 core
in vec2 vUV;
out vec4 FragColor;

uniform sampler2D uOverlayTex;

void main() {
    FragColor = texture(uOverlayTex, vUV);
}