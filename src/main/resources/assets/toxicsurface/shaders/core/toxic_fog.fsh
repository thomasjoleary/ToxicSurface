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
uniform float MaxDist;            // ray march cap (~the height-map coverage radius); follows render distance
uniform int Steps;                // active march samples this frame; scales with MaxDist to hold step density

// Nearby Cleanser bubbles (carve gas OUT) and generator smog clouds (add gas IN), packed as
// {x, y, z, r} quads. See ToxicGasFogRenderer / ClientFogVolumes / FogVolumesPayload.
const int MAX_VOLUMES = 16;
uniform int CleanserCount;
uniform float CleanserData[64];   // MAX_VOLUMES * 4
uniform int SmogCount;
uniform float SmogData[64];       // MAX_VOLUMES * 4

in vec2 texCoord;

out vec4 fragColor;

const int MAX_STEPS = 64;         // loop bound; the active count is the Steps uniform (must match renderer)
const float SPHERE_EDGE = 3.0;    // blocks over which a volume's boundary fades, to hide step banding

// Soft membership of world point p in sphere index i of a packed {x,y,z,r} array: 1 well inside,
// ramping smoothly to 0 across the outer SPHERE_EDGE blocks. A hard in/out test makes the sphere
// silhouette flip a whole ray-step of exposure at once (visible tearing); the ramp spreads that flip
// over a few blocks so it reads as a soft edge instead.
float sphereWeight(vec3 p, float data[64], int i) {
    vec3 c = vec3(data[i * 4], data[i * 4 + 1], data[i * 4 + 2]);
    float r = data[i * 4 + 3];
    return 1.0 - smoothstep(r - SPHERE_EDGE, r, length(p - c));
}

// Interleaved-gradient noise in [0,1) from the pixel coord — a cheap, even dither used to offset the
// ray's start within one step so the march's step banding dissolves into fine grain instead of hard
// bands (there is no temporal accumulation here, so a stable screen-space dither is the best we can do).
float ign(vec2 pix) {
    return fract(52.9829189 * fract(dot(pix, vec2(0.06711056, 0.00583715))));
}

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

    // Reconstruct the camera-relative direction/endpoint of this pixel's view ray.
    vec4 clip = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 rel4 = InvViewProj * clip;
    vec3 rel = rel4.xyz / rel4.w;
    float surfaceDist = length(rel);
    vec3 dir = rel / max(surfaceDist, 1e-4);

    // Stop at the surface; for open sky (no surface) march a fixed cap so horizon air still hazes.
    float marchDist = (depth > 0.9999) ? MaxDist : min(surfaceDist, MaxDist);
    if (marchDist <= 0.1) {
        discard;
    }

    float stepLen = marchDist / float(Steps);
    float jitter = ign(gl_FragCoord.xy);  // offset the samples within their cell to dither the banding
    float exposed = 0.0;
    for (int i = 0; i < MAX_STEPS; i++) {
        if (i >= Steps) break;
        vec3 p = CameraPos + dir * ((float(i) + jitter) * stepLen);
        float ground = terrainTop(p.xz);

        // Ambient world gas: at/below the ceiling and above this column's terrain. Density in [0,1].
        float density = (p.y <= CeilingY && p.y >= ground) ? 1.0 : 0.0;

        // A running generator's smog makes a sphere toxic even outside the ambient layer (e.g. above
        // the ceiling or in an as-yet-clean area) — but never below the ground of its column.
        for (int s = 0; s < MAX_VOLUMES; s++) {
            if (s >= SmogCount) break;
            if (p.y >= ground) {
                density = max(density, sphereWeight(p, SmogData, s));
            }
        }

        // A Cleanser bubble wins over everything: it fades the exposed gas back out inside its sphere.
        for (int c = 0; c < MAX_VOLUMES; c++) {
            if (c >= CleanserCount) break;
            density *= (1.0 - sphereWeight(p, CleanserData, c));
        }

        exposed += stepLen * density;
    }

    if (exposed <= 0.0) {
        discard;
    }
    float a = FogMaxAlpha * (1.0 - exp(-FogDensity * exposed));
    fragColor = vec4(FogColor, a);
}
