//
//  FogShader.metal
//  实时雾（三档）—— 由安卓 RealtimeFogView.kt 里的 AGSL 逐行翻译而来。
//
//  翻译要点：
//   · AGSL 的 `half4 main(float2 coord)` 在这里是 fragment，coord 取 [[position]]（像素坐标），
//     Metal 的 fragment position 原点也在左上，与 AGSL 一致，不用翻转。
//   · AGSL 的 uniform 全局变量在这里是 constant buffer，字段全部用 float4 装，
//     免得 simd_float3 的 16 字节对齐在 ObjC 和 MSL 两侧算出不同偏移。
//   · 算法一个数都没改（hash / noise3 / density / wander / smoke 全照抄），
//     改的只是语法外壳，所以画面应该和安卓一致。
//

#include <metal_stdlib>
using namespace metal;

struct FogUniforms {
    float4 p0;   // resolution.xy, time, strength
    float4 p1;   // relief, seed, 0, 0
    float4 c1;   // color1.rgb
    float4 c2;   // color2.rgb
    float4 c3;   // color3.rgb
};

static inline float xlf_hash(float3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

static inline float xlf_noise3(float3 p) {
    float3 i = floor(p);
    float3 f = fract(p);
    f = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    return mix(
        mix(mix(xlf_hash(i),                xlf_hash(i + float3(1, 0, 0)), f.x),
            mix(xlf_hash(i + float3(0, 1, 0)), xlf_hash(i + float3(1, 1, 0)), f.x), f.y),
        mix(mix(xlf_hash(i + float3(0, 0, 1)), xlf_hash(i + float3(1, 0, 1)), f.x),
            mix(xlf_hash(i + float3(0, 1, 1)), xlf_hash(i + float3(1, 1, 1)), f.x), f.y), f.z);
}

static inline float xlf_density(float3 p) {
    return 0.76 * xlf_noise3(p)
         + 0.19 * xlf_noise3(p * 1.93 + 5.7)
         + 0.05 * xlf_noise3(p * 3.71 + 13.1);
}

/* 各自独立的随机目的地、平滑衔接：没有轨道式循环，也不会逐帧随机闪。 */
static inline float2 xlf_wander(float seconds, float period, float id, float seed) {
    float segment = floor(seconds / period);
    float f = fract(seconds / period);
    f = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float2 start  = float2(xlf_hash(float3(segment,        id,        seed)),
                           xlf_hash(float3(segment,        id + 9.7,  seed)));
    float2 finish = float2(xlf_hash(float3(segment + 1.0,  id,        seed)),
                           xlf_hash(float3(segment + 1.0,  id + 9.7,  seed)));
    return (mix(start, finish, f) - 0.5) * 2.4;
}

static inline float xlf_smoke(float3 p, float relief) {
    if (relief < 0.5) return xlf_density(p);
    /* 旋转坐标 + 各自扭曲的内部，把平滑的圆岛打散成分叉、消散的浓团。 */
    p.xy = float2(p.x * 0.86 - p.y * 0.51, p.x * 0.51 + p.y * 0.86);
    float2 curl = float2(xlf_noise3(p * 1.1 + 3.7), xlf_noise3(p * 1.1 + 9.2)) - 0.5;
    p.xy += curl * 1.1;
    float broad = 0.57 * xlf_noise3(p)
                + 0.28 * xlf_noise3(p * 1.87 + 5.7)
                + 0.11 * xlf_noise3(p * 3.4 + 13.1)
                + 0.04 * xlf_noise3(p * 5.8 + 21.3);
    float breaks = xlf_noise3(float3(p.xy * 2.1, p.z * 0.7 + 17.4));
    return broad - 0.13 * smoothstep(0.45, 0.8, breaks);
}

fragment half4 fog_fragment(float4 pos [[position]],
                            constant FogUniforms &u [[buffer(0)]]) {
    float2 resolution = u.p0.xy;
    float  time       = u.p0.z;
    float  strength   = u.p0.w;
    float  relief     = u.p1.x;
    float  seed       = u.p1.y;
    float3 color1     = u.c1.xyz;
    float3 color2     = u.c2.xyz;
    float3 color3     = u.c3.xyz;

    float2 coord = pos.xy;
    float2 uv = coord / resolution.y;
    float t = time * 0.045;
    float2 p = uv * mix(3.2, 4.6, relief);

    float2 bend = float2(
        xlf_noise3(float3(p * 0.7 + 2.1, t * 0.6)),
        xlf_noise3(float3(p * 0.7 + 8.3, t * 0.6 + 4.2))) - 0.5;

    /* 一股宽的、缓慢演化的水流，没有高频褶皱。 */
    p += bend * (0.65 + 0.55 * relief);
    p += relief * 0.16 * float2(sin(p.y * 1.6 + t * 0.9), sin(p.x * 1.4 - t * 0.7));

    float lo = mix(0.22, 0.26, relief);
    float hi = mix(0.86, 0.72, relief);

    float2 travelScale = float2(resolution.x / resolution.y, 1.0) * 4.6;
    float2 wa = relief * xlf_wander(time,        26.0, 1.3,  seed) * travelScale;
    float2 wb = relief * xlf_wander(time + 7.0,  31.0, 8.9,  seed) * travelScale;
    float2 wc = relief * xlf_wander(time + 15.0, 29.0, 17.2, seed) * travelScale;

    float a = smoothstep(lo, hi, xlf_smoke(float3(p - wa + float2(t * 0.55, -t * 0.35), t), relief));
    float b = smoothstep(lo, hi, xlf_smoke(float3(p * 0.87 - wb + float2(5.4 - t * 0.40, 2.6 + t * 0.28), t + 7.3), relief));
    float c = smoothstep(lo + 0.04, hi + 0.02, xlf_smoke(float3(p * 0.72 - wc + float2(9.2 + t * 0.32, 6.1 - t * 0.20), t * 0.8 + 15.7), relief));

    /* 大而柔的浓团 + 明确的空隙：避免糊成一片均匀的粉彩。 */
    a *= a;
    b *= b;
    c *= c;
    c *= mix(1.0, 0.72, relief);   // 偏暖的赭色只作较弱的辅助流。

    float total = a + b + c + 0.001;
    float alpha = strength * (1.0 - exp(-1.15 * total));
    float3 color = (color1 * a + color2 * b + color3 * c) / total;

    /* 浓雾内部同色系压暗，⛔ 不是描边也不是投影。 */
    float core  = smoothstep(0.30, 1.25, total);
    float depth = xlf_noise3(float3(p * 0.8 + 4.7, t * 0.85 + 3.1));
    color *= 1.0 - relief * core * (0.08 + 0.12 * depth);

    return half4(half3(color * alpha), half(alpha));
}

/* 全屏三角形：不需要顶点缓冲，用 vertex_id 直接算三个角。 */
vertex float4 fog_vertex(uint vid [[vertex_id]]) {
    float2 p = float2(float((vid << 1) & 2), float(vid & 2));
    return float4(p * 2.0 - 1.0, 0.0, 1.0);
}
