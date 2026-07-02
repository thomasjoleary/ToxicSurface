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

const int STEPS = 24;
const float MAX_DIST = 180.0;     // ray march cap (also ~the height-map coverage limit)

// Terrain top (highest solid) at a world column, decoded from the two high/low bytes in R and G.
// Returns a huge value for columns outside the mapped region so those samples never count as air.
float terrainTop(vec2 worldXZ) {
    vec2 uv = (worldXZ - HeightOrigin) / HeightWorldSize;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return 1.0e6;
    }
    vec4 t = texture(HeightSampler, uv);
    float hi = floor(t.r * 255.0 + 0.5);
    float lo = floor(t.g * 255.0 + 0.5);
    return (hi * 256.0 + lo) - 64.0;
}

// Volumetric toxic haze. Rather than tinting the surface pixel, we march the view ray from the
// camera to the surface and accumulate how much genuinely-exposed toxic *air* it crosses — air that
// is at/below the ceiling and above the terrain in its own column. This reads as real depth (distant
// ground fades into haze, trees/hills fog uniformly) and keeps sealed rooms clear for free: a ray
// inside a room stays below its roof the whole way, so it accumulates zero exposed air. The height
// data is a small per-column texture rebuilt around the camera, so there is no cell blockiness and no
// flashing.
void main() {
    float depth = texture(DepthSampler, texCoord).r;
    if (depth > 0.9999) {
        // Open sky: the gas is ground-hugging, so it must not tint the sky. (Marching a fixed
        // distance here made near-horizontal sky rays skim the exposed layer and haze the sky — a
        // green band along the top of the view. Distant *terrain* at the horizon still fogs below,
        // since those pixels have a real surface depth.)
        discard;
    }

    // Reconstruct the camera-relative direction/endpoint of this pixel's view ray.
    vec4 clip = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 rel4 = InvViewProj * clip;
    vec3 rel = rel4.xyz / rel4.w;
    float surfaceDist = length(rel);
    vec3 dir = rel / max(surfaceDist, 1e-4);

    // The surface (whatever this pixel shows) above the ceiling is above the gas layer — a cloud, a
    // mountain peak poking out of the gas, a tall build — and must be seen clear, not tinted. (Clouds
    // are geometry, not far-plane sky, so the sky test above doesn't catch them; without this they
    // pick up the gas the ray crosses on the way up and read green.)
    if (CameraPos.y + rel.y > CeilingY) {
        discard;
    }

    float marchDist = min(surfaceDist, MAX_DIST);
    if (marchDist <= 0.1) {
        discard;
    }

    float stepLen = marchDist / float(STEPS);
    float exposed = 0.0;
    for (int i = 0; i < STEPS; i++) {
        vec3 p = CameraPos + dir * ((float(i) + 0.5) * stepLen);
        if (p.y <= CeilingY && p.y >= terrainTop(p.xz)) {
            exposed += stepLen;
        }
    }

    if (exposed <= 0.0) {
        discard;
    }
    float a = FogMaxAlpha * (1.0 - exp(-FogDensity * exposed));
    fragColor = vec4(FogColor, a);
}
