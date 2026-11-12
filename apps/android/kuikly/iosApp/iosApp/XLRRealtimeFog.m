//
//  XLRRealtimeFog.m
//  实时雾（雾档 = 二档）的 iOS 实现：MTKView + Metal 着色器。
//
//  与安卓 RealtimeFogView.kt 对齐的点：
//   · 30fps（安卓 postDelayed(tick, 33L)，这里 preferredFramesPerSecond = 30）
//   · 同一套调色板与 strength
//   · 静止（fogMoving=false）时停帧，并把 seed / phase 落到 NSUserDefaults
//     （安卓存在 SharedPreferences，切回动起来时相位是连着的）
//   · 由时间累积出相位，不是平移一张图
//
//  和安卓不同的地方：
//   · 安卓冻结时把最后一帧离屏渲染成 bitmap 再画（省 GPU）；这边直接把 MTKView 暂停，
//     屏幕上留着最后一张 drawable，效果一样但少一条离屏渲染路径。
//

#import "XLRRealtimeFog.h"
#import <MetalKit/MetalKit.h>
#import <simd/simd.h>
#import "KRComponentDefine.h"

/* ⚠ 字段全用 float4 装：simd_float3 是 16 字节对齐，如果混着 float 排，
   ObjC 和 MSL 两侧算出的偏移容易不一致，传过去就是一团错数据。 */
typedef struct {
    simd_float4 p0;   // resolution.xy, time, strength
    simd_float4 p1;   // relief, seed, 0, 0
    simd_float4 c1;   // color1.rgb
    simd_float4 c2;   // color2.rgb
    simd_float4 c3;   // color3.rgb
} XLFFogUniforms;

static NSString *const kXLFFogSeedKeyTemplate  = @"xlr-fog-%@-seed";
static NSString *const kXLFFogPhaseKeyTemplate = @"xlr-fog-%@-phase";

@interface XLRRealtimeFog () <MTKViewDelegate>
@property (nonatomic, strong, nullable) MTKView *mtkView;
@property (nonatomic, strong, nullable) id<MTLDevice> device;
@property (nonatomic, strong, nullable) id<MTLCommandQueue> commandQueue;
@property (nonatomic, strong, nullable) id<MTLRenderPipelineState> pipeline;
@property (nonatomic, copy)   NSString *skin;
@property (nonatomic, assign) BOOL moving;
@property (nonatomic, assign) double phaseSeconds;
@property (nonatomic, assign) float seed;
@property (nonatomic, assign) CFTimeInterval lastTick;
@property (nonatomic, assign) BOOL hasLastTick;
@property (nonatomic, assign) BOOL pipelineReady;
@property (nonatomic, assign) NSInteger frameCount;
@property (nonatomic, assign) NSInteger missCount;
@property (nonatomic, assign) NSInteger layoutCount;
@property (nonatomic, assign) NSInteger propCount;
@end

@implementation XLRRealtimeFog

@synthesize hr_rootView;

#pragma mark - 生命周期

- (instancetype)initWithFrame:(CGRect)frame {
    if (self = [super initWithFrame:frame]) {
        _skin = @"bw";
        _moving = YES;
        _seed = 1.0f;
        /* ⚠ 这里**不建** MTKView：Kuikly 创建组件时尺寸往往还是 0（alloc/init 之后再设 frame），
           那时候建出来的 MTKView 是 0×0，之后永远不渲染。推迟到 didMoveToWindow。 */
    }
    return self;
}

- (void)didMoveToWindow {
    [super didMoveToWindow];
    NSLog(@"[XLRFOG] didMoveToWindow window=%@ bounds=%.0fx%.0f mtk=%@",
          self.window ? @"有" : @"无",
          self.bounds.size.width, self.bounds.size.height,
          self.mtkView ? @"已建" : @"未建");
    if (self.window && !self.mtkView) {
        [self p_setupMetal];
        [self p_loadPhaseFromDefaults];
        [self p_applyMoving];
    }
}

- (void)dealloc {
    _mtkView.delegate = nil;
    [_mtkView setPaused:YES];
}

- (void)layoutSubviews {
    [super layoutSubviews];
    /* 尺寸三级兜底：自己 → 父容器 → 窗口。
       ⚠ 实测 didMoveToWindow 时 bounds 还是 0×0（Kuikly 先挂上来、后设 frame），
         如果它压根不设尺寸，MTKView 就永远是 0×0、永远拿不到 drawable。
         这里让 MTKView 至少铺满父容器 —— view 的 clipsToBounds 默认 NO，超界也能画。 */
    CGRect r = self.bounds;
    if (r.size.width <= 0 || r.size.height <= 0) {
        r = self.superview ? self.superview.bounds : CGRectZero;
    }
    if ((r.size.width <= 0 || r.size.height <= 0) && self.window) {
        r = self.window.bounds;
    }
    if (r.size.width > 0 && r.size.height > 0) {
        self.mtkView.frame = r;
        /* 半分辨率渲染（见 p_setupMetal 里的说明）。drawableSize 改了就要同步，
           否则 shader 拿到的 resolution 与实际像素对不上，噪点会被缩放。 */
        CGSize want = CGSizeMake(floor(r.size.width * 0.5), floor(r.size.height * 0.5));
        if (!CGSizeEqualToSize(self.mtkView.drawableSize, want)) {
            self.mtkView.drawableSize = want;
        }
    }
    if (self.layoutCount < 3) {
        NSLog(@"[XLRFOG] layoutSubviews#%ld self=%.0fx%.0f 用=%.0fx%.0f window=%@",
              (long)self.layoutCount, self.bounds.size.width, self.bounds.size.height,
              r.size.width, r.size.height, self.window ? @"有" : @"无");
    }
    self.layoutCount += 1;
    if (self.mtkView && r.size.width > 0 && r.size.height > 0) {
        if (self.mtkView.isPaused && self.moving && self.pipelineReady) {
            NSLog(@"[XLRFOG] layoutSubviews: 尺寸已就绪但仍暂停 ⇒ 恢复");
            [self.mtkView setPaused:NO];
        }
        [self.mtkView draw];
    }
}

#pragma mark - Metal

- (void)p_setupMetal {
    id<MTLDevice> device = MTLCreateSystemDefaultDevice();
    if (!device) {
        NSLog(@"[XLRFOG] 这台设备没有 Metal device，实时雾不可用");
        return;
    }
    self.device = device;
    self.commandQueue = [device newCommandQueue];

    MTKView *mv = [[MTKView alloc] initWithFrame:self.bounds device:device];
    mv.delegate = self;
    mv.opaque = NO;
    mv.backgroundColor = UIColor.clearColor;
    mv.clearColor = MTLClearColorMake(0, 0, 0, 0);
    /* 跟安卓一致：30fps。60fps 对这么慢的流体毫无意义，只会让 GPU 一直满转。 */
    mv.preferredFramesPerSecond = 30;
    /* ⚠ 雾是**极低频的柔和纹理**，渲染分辨率减半肉眼看不出来（着色器里每像素要跑 4 组噪声 × 3 层，
       像素数直接等于计算量）。实机上全分辨率 1206×2622 @30fps 跑起来会明显发烫/掉帧，
       减半后计算量降到 1/4，MTKView 会自动把 drawable 拉伸回全屏，雾这种材质看不出区别。 */
    mv.autoResizeDrawable = NO;
    mv.enableSetNeedsDisplay = NO;
    mv.userInteractionEnabled = NO;
    mv.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    [self addSubview:mv];
    self.mtkView = mv;

    NSError *err = nil;
    id<MTLLibrary> lib = [device newDefaultLibrary];
    if (!lib) {
        NSLog(@"[XLRFOG] 取不到 default.metallib（FogShader.metal 没进 target？）");
        return;
    }
    id<MTLFunction> vs = [lib newFunctionWithName:@"fog_vertex"];
    id<MTLFunction> fs = [lib newFunctionWithName:@"fog_fragment"];
    if (!vs || !fs) {
        NSLog(@"[XLRFOG] 找不到 fog_vertex / fog_fragment");
        return;
    }
    MTLRenderPipelineDescriptor *desc = [MTLRenderPipelineDescriptor new];
    desc.vertexFunction = vs;
    desc.fragmentFunction = fs;
    desc.colorAttachments[0].pixelFormat = mv.colorPixelFormat;
    desc.colorAttachments[0].blendingEnabled = YES;
    /* ⚠ 必须用**预乘 alpha** 的混合：着色器返回的是 half4(color*alpha, alpha)，
       和安卓 AGSL 的约定一致。如果这里配成 SourceAlpha/OneMinusSourceAlpha，
       alpha 会被乘两次 —— hei 的 strength 0.18 实际只剩 0.03，画面看着就是「没有雾」。 */
    desc.colorAttachments[0].sourceRGBBlendFactor = MTLBlendFactorOne;
    desc.colorAttachments[0].destinationRGBBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
    desc.colorAttachments[0].sourceAlphaBlendFactor = MTLBlendFactorOne;
    desc.colorAttachments[0].destinationAlphaBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
    self.pipeline = [device newRenderPipelineStateWithDescriptor:desc error:&err];
    if (!self.pipeline) {
        NSLog(@"[XLRFOG] 管线创建失败: %@", err);
        return;
    }
    self.pipelineReady = YES;
    NSLog(@"[XLRFOG] Metal 实时雾就绪 30fps");
}

#pragma mark - KuiklyRenderViewExportProtocol

- (void)hrv_setPropWithKey:(NSString *)propKey propValue:(id)propValue {
    /* 头几次把收到的属性名都打出来：确认 Kuikly 有没有传尺寸/位置类属性，
       以及 fogSkin/fogMoving 是否真的到了。只看一次日志就能定位。 */
    if (self.propCount < 12) {
        NSLog(@"[XLRFOG] setProp %@ = %@", propKey, propValue);
    }
    self.propCount += 1;
    if ([propKey isEqualToString:@"fogSkin"]) {
        [self p_setSkin:[propValue description]];
        return;
    }
    if ([propKey isEqualToString:@"fogMoving"]) {
        /* ⚠ 实测 Kuikly 传过来的是 **NSNumber 1/0**（日志：setProp fogMoving = 1），
           不是字符串 "true"/"false"。这里两种都吃，否则布尔判断永远为 NO，
           moving=false ⇒ MTKView 一直暂停 ⇒ 一帧都不画（曾卡在这一条上）。 */
        BOOL on;
        if ([propValue isKindOfClass:[NSNumber class]]) {
            on = [(NSNumber *)propValue boolValue];
        } else {
            NSString *text = [[propValue description] lowercaseString];
            on = [text isEqualToString:@"true"] || [text isEqualToString:@"1"];
        }
        [self p_setMoving:on];
        return;
    }
    KUIKLY_SET_CSS_COMMON_PROP;
}

#pragma mark - 皮肤 / 运动

- (void)p_setSkin:(NSString *)skin {
    if (!skin.length || [self.skin isEqualToString:skin]) return;
    self.skin = skin;
    [self p_loadPhaseFromDefaults];
    self.hasLastTick = NO;
    [self p_applyMoving];
}

- (void)p_setMoving:(BOOL)moving {
    if (self.moving == moving) return;
    self.moving = moving;
    self.hasLastTick = NO;
    if (!moving) [self p_savePhaseToDefaults];
    [self p_applyMoving];
}

- (void)p_applyMoving {
    BOOL run = self.moving && self.pipelineReady;
    NSLog(@"[XLRFOG] p_applyMoving moving=%d ready=%d ⇒ paused=%d",
          self.moving, self.pipelineReady, !run);
    [self.mtkView setPaused:!run];
    if (!run) [self.mtkView draw];   // 停之前补一帧，避免留下空画布
}

#pragma mark - 相位持久化（对齐安卓的 SharedPreferences）

- (void)p_loadPhaseFromDefaults {
    NSUserDefaults *d = NSUserDefaults.standardUserDefaults;
    NSString *seedKey  = [NSString stringWithFormat:kXLFFogSeedKeyTemplate,  self.skin];
    NSString *phaseKey = [NSString stringWithFormat:kXLFFogPhaseKeyTemplate, self.skin];
    if ([d objectForKey:seedKey] == nil) {
        self.seed = (float)((double)arc4random() / (double)UINT32_MAX) * 100.0f;
        [d setFloat:self.seed forKey:seedKey];
    } else {
        self.seed = [d floatForKey:seedKey];
    }
    self.phaseSeconds = (double)[d floatForKey:phaseKey];
}

- (void)p_savePhaseToDefaults {
    NSUserDefaults *d = NSUserDefaults.standardUserDefaults;
    [d setFloat:(float)self.phaseSeconds
         forKey:[NSString stringWithFormat:kXLFFogPhaseKeyTemplate, self.skin]];
    [d setFloat:self.seed
         forKey:[NSString stringWithFormat:kXLFFogSeedKeyTemplate, self.skin]];
}

#pragma mark - 调色板（与安卓 applyPalette 一一对应）

- (void)p_fillPaletteInto:(XLFFogUniforms *)u {
    unsigned int c1 = 0x242424, c2 = 0xFFFFFF, c3 = 0x888888;
    float strength = 0.42f;
    NSString *s = self.skin;

    if ([s isEqualToString:@"glass"])      { c1 = 0xDF9EC4; c2 = 0x94B5EB; c3 = 0xB497DB; strength = 0.33f; }
    else if ([s isEqualToString:@"hei"])   { c1 = 0xACACAC; c2 = 0x686868; c3 = 0x959595; strength = 0.18f; }
    else if ([s isEqualToString:@"xuan"])  { c1 = 0xC0A587; c2 = 0xB8AA97; c3 = 0xC8B89F; strength = 0.10f; }
    else if ([s isEqualToString:@"qing"])  { c1 = 0x639F94; c2 = 0x819FB2; c3 = 0x729C9B; strength = 0.33f; }
    else if ([s isEqualToString:@"dou"])   { c1 = 0xA8746A; c2 = 0xB8969D; c3 = 0xBD9388; strength = 0.33f; }
    else if ([s isEqualToString:@"dh"])    { c1 = 0xA96F53; c2 = 0x698D7D; c3 = 0x647F99; strength = 0.30f; }
    else if ([s isEqualToString:@"mg"])    { c1 = 0xC69A59; c2 = 0x577392; c3 = 0xAD7955; strength = 0.28f; }
    else if ([s isEqualToString:@"bw"])    { strength = 0.16f; }

    u->c1 = simd_make_float4(((c1 >> 16) & 255) / 255.0f, ((c1 >> 8) & 255) / 255.0f, (c1 & 255) / 255.0f, 1.0f);
    u->c2 = simd_make_float4(((c2 >> 16) & 255) / 255.0f, ((c2 >> 8) & 255) / 255.0f, (c2 & 255) / 255.0f, 1.0f);
    u->c3 = simd_make_float4(((c3 >> 16) & 255) / 255.0f, ((c3 >> 8) & 255) / 255.0f, (c3 & 255) / 255.0f, 1.0f);
    u->p0.w = strength;
    /* 和安卓一致：所有选中的皮肤都开不规则烟团 + 游走 */
    u->p1.x = 1.0f;
    u->p1.y = self.seed;
}

#pragma mark - MTKViewDelegate

- (void)mtkView:(MTKView *)view drawableSizeWillChange:(CGSize)size {
    /* 尺寸变化不用重建管线：分辨率是 uniform，每帧都传 */
}

- (void)drawInMTKView:(MTKView *)view {
    if (!self.pipelineReady || !self.moving) return;
    if (view.bounds.size.width <= 0 || view.bounds.size.height <= 0) return;

    /* 相位按时钟累积（安卓也是 elapsedRealtimeNanos 累积，不是按帧数） */
    CFTimeInterval now = CACurrentMediaTime();
    if (self.hasLastTick) {
        self.phaseSeconds += (now - self.lastTick);
    }
    self.lastTick = now;
    self.hasLastTick = YES;

    MTLRenderPassDescriptor *pass = view.currentRenderPassDescriptor;
    id<CAMetalDrawable> drawable = view.currentDrawable;
    if (!pass || !drawable) {
        /* 这两样拿不到就不会画任何东西，但外面看不出来 ⇒ 计数打日志 */
        self.missCount += 1;
        if (self.missCount % 60 == 1) {
            NSLog(@"[XLRFOG] 取不到 renderPass/drawable，累计 %ld 次", (long)self.missCount);
        }
        return;
    }

    CGSize drawableSize = view.drawableSize;
    /* 前几帧打尺寸：drawableSize 若是 0，着色器里 coord/resolution.y 会算出 NaN，整层全透明 */
    if (self.frameCount < 3) {
        NSLog(@"[XLRFOG] 第 %ld 帧 drawableSize=%.0fx%.0f bounds=%.0fx%.0f phase=%.1f",
              (long)self.frameCount, drawableSize.width, drawableSize.height,
              view.bounds.size.width, view.bounds.size.height, self.phaseSeconds);
    }
    if (self.frameCount % 300 == 0 && self.frameCount > 0) {
        NSLog(@"[XLRFOG] frames=%ld phase=%.1f skin=%@", (long)self.frameCount, self.phaseSeconds, self.skin);
    }
    self.frameCount += 1;

    XLFFogUniforms u;
    memset(&u, 0, sizeof(u));
    u.p0 = simd_make_float4((float)drawableSize.width, (float)drawableSize.height,
                            (float)self.phaseSeconds, 0.0f);
    [self p_fillPaletteInto:&u];

    id<MTLCommandBuffer> cb = [self.commandQueue commandBuffer];
    id<MTLRenderCommandEncoder> enc = [cb renderCommandEncoderWithDescriptor:pass];
    [enc setRenderPipelineState:self.pipeline];
    [enc setFragmentBytes:&u length:sizeof(u) atIndex:0];
    [enc drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:0 vertexCount:3];
    [enc endEncoding];
    [cb presentDrawable:drawable];
    [cb commit];
}

@end
