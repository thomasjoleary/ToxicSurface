#version 150

uniform sampler2D DepthSampler;

uniform mat4 InvViewProj;
uniform float CameraY;
uniform float CeilingY;
uniform vec3 FogColor;
uniform float FogDensity;
uniform float FogMaxAlpha;

in vec2 texCoord;

out vec4 fragColor;

// Reconstructs world-space Y (relative to the camera at the origin) from this pixel's depth,
// so the haze is blended in wherever the visible surface actually sits inside the gas layer —
// regardless of whether the camera itself is sealed, cleansed, or above the toxic ceiling
// (DESIGN.md §3 gas visibility). Discards near the far plane so empty sky doesn't get tinted.
void main() {
    float depth = texture(DepthSampler, texCoord).r;
    if (depth > 0.9999) {
        discard;
    }

    vec4 clipPos = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = InvViewProj * clipPos;
    vec3 relPos = viewPos.xyz / viewPos.w;
    float worldY = relPos.y + CameraY;

    if (worldY > CeilingY) {
        discard;
    }

    float dist = length(relPos);
    float alpha = FogMaxAlpha * (1.0 - exp(-FogDensity * dist));
    fragColor = vec4(FogColor, alpha);
}
