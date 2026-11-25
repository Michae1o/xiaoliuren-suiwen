#include <metal_stdlib>
using namespace metal;

// Palette and two bilinearly sampled washes are composited before quantization.
// There is no filter on the chart, labels, or any previously rasterized layer.
static inline float xlrRecallMask(texture2d<half> halo, float2 point, float2 origin, float radius) {
    float2 uv = (point - origin) / (2.0f * max(radius, 1e-4f)) + float2(0.5f);
    if (any(uv < float2(0.0f)) || any(uv > float2(1.0f))) return 0.0f;
    constexpr sampler bilinear(coord::normalized, address::clamp_to_edge, filter::linear);
    return float(halo.sample(bilinear, uv).a);
}

static inline float xlrRecallThreshold(uint x, uint y) {
    uint h = (x * 0x8da6b343u) ^ (y * 0xd8163841u) ^ 0x421711u;
    h = (h ^ (h >> 16)) * 0x7feb352du;
    h = (h ^ (h >> 15)) * 0x846ca68bu;
    h ^= h >> 16;
    return (float(h >> 16) + 0.5f) * (1.0f / 65536.0f);
}

// SwiftUI supplies user-space points and colors resolved in the destination color space.
// The noise is addressed only by viewport pixel, never camera coordinates or time.
[[ stitchable ]] half4 xlrRecallAmbient(float2 point, texture2d<half> halo,
                                       half4 night, half4 glow, half4 second,
                                       float2 firstCenter, float2 secondCenter,
                                       float firstRadius, float secondRadius, float displayScale) {
    float3 rgb = mix(float3(night.rgb), float3(glow.rgb),
                     0.33f * xlrRecallMask(halo, point, firstCenter, firstRadius));
    rgb = mix(rgb, float3(second.rgb),
              0.14f * xlrRecallMask(halo, point, secondCenter, secondRadius));

    uint2 pixel = uint2(floor(max(point * displayScale, float2(0.0f))));
    float threshold = xlrRecallThreshold(pixel.x, pixel.y);
    // One shared threshold per RGB triplet, at most one 8-bit code from normal rounding.
    float3 dithered = floor(clamp(rgb, float3(0.0f), float3(1.0f)) * 255.0f + threshold) / 255.0f;
    return half4(half3(dithered), half(1.0f));
}
