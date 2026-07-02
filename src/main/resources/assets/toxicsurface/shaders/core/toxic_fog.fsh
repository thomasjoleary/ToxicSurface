#version 150

uniform sampler2D DepthSampler;   // main scene depth
uniform sampler2D HeightSampler;  // per-column terrain top, encoded 16-bit in R(hi)+G(lo)

uniform mat4 InvViewProj;         // inverse(projection * camera-relative modelView)
uniform vec3 CameraPos;           // world-space camera position
uniform float CeilingY;           // toxic ceiling; no gas above this Y
uniform vec2 HeightOrigin;        // world (x,z) of the height texture's (0,0) corner
uniform float HeightWorldSize;    // world blocks the height texture spans (square)
uniform vec3 FogColor;
uniform float FogDensity;
uniform float FogMaxAlpha;

in vec2 texCoord;

out vec4 fragColor;

// Decodes a column's terrain-top Y from the two high/low bytes packed into R and G.
float decodeTop(vec2 uv) {
    vec4 t = texture(HeightSampler, uv);
    float hi = floor(t.r * 255.0 + 0.5);
    float lo = floor(t.g * 255.0 + 0.5);
    return (hi * 256.0 + lo) - 64.0;
}

// Per-pixel toxic haze. For each rendered surface pixel we reconstruct its world position from the
// depth buffer and add green haze scaled by distance from the camera — but only where that surface
// is genuinely exposed toxic air: at or below the ceiling, and at the top of its own column (a pixel
// well below its column's highest solid is under a roof, i.e. sealed, so it is skipped). Because the
// test is per pixel and the height data updates smoothly, there are no cell seams and no flashing;
// the depth reconstruction naturally hazes distant ground more than near ground, and — since it is
// evaluated along the true view ray — reads dense looking down from above yet soft near the ceiling.
void main() {
    float depth = texture(DepthSampler, texCoord).r;
    if (depth > 0.9999) {
        discard; // open sky: nothing to tint
    }

    vec4 clip = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 rel4 = InvViewProj * clip;
    vec3 rel = rel4.xyz / rel4.w;   // camera-relative world offset
    vec3 world = rel + CameraPos;

    if (world.y > CeilingY) {
        discard; // surface sits above the gas layer
    }

    vec2 uv = (world.xz - HeightOrigin) / HeightWorldSize;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        discard; // outside the mapped region around the player
    }

    float top = decodeTop(uv);
    if (world.y < top - 1.5) {
        discard; // under cover (a roof) in this column: sealed, no gas
    }

    float dist = length(rel);
    float a = FogMaxAlpha * (1.0 - exp(-FogDensity * dist));
    fragColor = vec4(FogColor, a);
}
