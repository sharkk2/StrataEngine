#version 430 core

out vec4 FragColor;
in vec2 vUV;

uniform sampler2D albedo;
uniform int hasAlbedo;

void main() {
    if (hasAlbedo == 1) {
        if (texture(albedo, vUV).a < 0.05) discard;
    }
    FragColor = vec4(1.0, 0, 0, 1.0);
}