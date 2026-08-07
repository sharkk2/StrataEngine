#version 430 core

out float FragColor;
in vec2 TexCoords;
uniform sampler2D ssaoInput;

uniform sampler2D gPosition;

void main() {
    vec2 texelSize = 1.0 / vec2(textureSize(ssaoInput, 0));
    float centerZ = texture(gPosition, TexCoords).z;
    float result = 0.0, totalWeight = 0.0;
    float depthThreshold = max(abs(centerZ) * 0.02, 0.03);

    for (int x = -2; x < 2; x++) {
        for (int y = -2; y < 2; y++) {
            vec2 uv = TexCoords + vec2(x, y) * texelSize;
            float z = texture(gPosition, uv).z;
            float w = (abs(z - centerZ) < depthThreshold) ? 1.0 : 0.0;
            result += texture(ssaoInput, uv).r * w;
            totalWeight += w;
        }
    }
    FragColor = totalWeight > 0.0 ? result / totalWeight : texture(ssaoInput, TexCoords).r;
}