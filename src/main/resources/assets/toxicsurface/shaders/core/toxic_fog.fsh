#version 150

uniform sampler2D DepthSampler;   // main scene depth
uniform sampler2D HeightSampler;  // per-column terrain top, encoded 16-bit in R(hi)+G(lo)
uniform sampler2D VolumeSampler;  // near-field exposure volume, Y-slice atlas (R=255 -> fog-able cell)

uniform mat4 InvViewProj;         // inverse(projection * camera-relative modelView)
uniform vec3 CameraPos;           // world-space camera position
uniform float CeilingY;           // toxic ceiling; no gas above this Y
uniform vec2 HeightOrigin;        // world (x,z) of the height texture's (0,0) corner
uniform float HeightWorldSize;    // world blocks the height texture spans (square)
uniform vec3 VolOrigin;           // world min corner of the exposure volume
uniform int VolReady;             // 0 until the first volume build lands; fall back to the roof test
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

// Near-field exposure volume layout; must match the VOL_* constants in ToxicGasFogRenderer.
const int VOL_SX = 96;
const int VOL_SY = 48;
const int VOL_SZ = 96;
const int VOL_ATLAS_COLS = 8;
const float VOL_BLEND = 8.0;      // blocks inside the volume edge over which near blends into far

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

// Whether the atmosphere reaches world point p (0 = no gas can be here, 1 = exposed air). Near the
// camera this reads the 3D exposure volume — true air connectivity classified with the damage scanner's
// own rules, so a breached room floods, caves and overhangs fog, and sealed bases stay clear. Outside
// the volume (or before its first build) it falls back to the per-column roof test, and the two are
// blended over the volume's outer VOL_BLEND blocks so the handover never shows a seam.
float exposureGate(vec3 p) {
    float farGate = (p.y >= terrainTop(p.xz)) ? 1.0 : 0.0;
    if (VolReady == 0) {
        return farGate;
    }
    vec3 lo = p - VolOrigin;
    vec3 hi = vec3(float(VOL_SX), float(VOL_SY), float(VOL_SZ)) - lo;
    float inset = min(min(min(lo.x, hi.x), min(lo.y, hi.y)), min(lo.z, hi.z));
    if (inset <= 0.0) {
        return farGate;
    }
    ivec3 c = clamp(ivec3(floor(lo)), ivec3(0), ivec3(VOL_SX - 1, VOL_SY - 1, VOL_SZ - 1));
    int px = (c.y % VOL_ATLAS_COLS) * VOL_SX + c.x;
    int py = (c.y / VOL_ATLAS_COLS) * VOL_SZ + c.z;
    float nearGate = (texelFetch(VolumeSampler, ivec2(px, py), 0).r > 0.5) ? 1.0 : 0.0;
    return mix(farGate, nearGate, clamp(inset / VOL_BLEND, 0.0, 1.0));
}

// Volumetric toxic haze. Rather than tinting the surface pixel, we march the view ray from the
// camera to the surface and accumulate how much genuinely-exposed toxic *air* it crosses — air that
// is at/below the ceiling and that the atmosphere can actually reach (exposureGate). This reads as
// real depth (distant ground fades into haze, trees/hills fog uniformly), keeps sealed rooms clear,
// and floods fog anywhere the gas would genuinely hurt: under overhangs, into caves, and through a
// breached wall.
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
        float gate = exposureGate(p);

        // Ambient world gas: at/below the ceiling, wherever the atmosphere reaches. Density in [0,1].
        float density = (p.y <= CeilingY) ? gate : 0.0;

        // A running generator's smog makes a sphere toxic even outside the ambient layer (e.g. above
        // the ceiling or in an as-yet-clean area) — but only in air the atmosphere reaches, so a wall
        // still keeps a neighbouring generator's smog out of a sealed room.
        for (int s = 0; s < MAX_VOLUMES; s++) {
            if (s >= SmogCount) break;
            density = max(density, sphereWeight(p, SmogData, s) * gate);
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
