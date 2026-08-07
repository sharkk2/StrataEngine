#version 430 core

uniform sampler2D srcTexture;
uniform float srcResolutionX;
uniform float srcResolutionY;
uniform int mipLevel;

in vec2 TexCoord;
out vec4 downsample;

uniform float threshold;
uniform float knee;

vec3 prefilter(vec3 c) {
    float brightness = max(c.r, max(c.g, c.b));
    float soft = clamp(brightness - threshold + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee + 1e-4);
    float contribution = max(soft, brightness - threshold) / max(brightness, 1e-4);
    return c * contribution;
}

vec3 sampleAt(vec2 offset, vec2 texelSize) {
    vec3 c = texture(srcTexture, TexCoord + offset * texelSize).rgb;
    if (mipLevel == 0) c = prefilter(c);
    return c;
}


void main() {
    vec2 texelSize = vec2(1.0 / srcResolutionX, 1.0 / srcResolutionY);

    vec3 a = sampleAt(vec2(-2.0, 2.0), texelSize);
    vec3 b = sampleAt(vec2(0.0, 2.0), texelSize);
    vec3 c = sampleAt(vec2(2.0, 2.0), texelSize);
    vec3 d = sampleAt(vec2(-2.0, 0.0), texelSize);
    vec3 e = sampleAt(vec2(0.0, 0.0), texelSize);
    vec3 f = sampleAt(vec2(2.0, 0.0), texelSize);
    vec3 g = sampleAt(vec2(-2.0, -2.0), texelSize);
    vec3 h = sampleAt(vec2(0.0, -2.0), texelSize);
    vec3 i = sampleAt(vec2(2.0, -2.0), texelSize);
    vec3 j = sampleAt(vec2(-1.0, 1.0), texelSize);
    vec3 k = sampleAt(vec2(1.0, 1.0), texelSize);
    vec3 l = sampleAt(vec2(-1.0, -1.0), texelSize);
    vec3 m = sampleAt(vec2(1.0, -1.0), texelSize);

    if (mipLevel == 0) {
        vec3 group0 = (a + b + d + e) * 0.03125;
        vec3 group1 = (b + c + e + f) * 0.03125;
        vec3 group2 = (d + e + g + h) * 0.03125;
        vec3 group3 = (e + f + h + i) * 0.03125;
        vec3 group4 = (j + k + l + m) * 0.125;
        downsample = vec4(max(group0 + group1 + group2 + group3 + group4, 0.0001), 1);
    } else {
        vec3 ds = e * 0.125;
        ds += (a + c + g + i) * 0.03125;
        ds += (b + d + f + h) * 0.0625;
        ds += (j + k + l + m) * 0.125;
        downsample = vec4(ds, 1);
    }
}