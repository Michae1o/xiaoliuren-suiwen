import SwiftUI
import Combine

/// 当前在第几页（「壹 / 贰 / 叁」）：顶栏状态一推就更新。
/// 键盘闸（ContentView 里那道「不在壹页就把多余键盘收掉」）靠它放过叁页 ——
var xlrCurrentStep: String = "壹"

/// 原生顶栏（菜单 / 步进徽标 / 皮肤 / 日夜）。
///
/// 为什么要有它：Kuikly 的整页会被键盘当成一个图层整体上移，顶栏画在页面里就一定跟着跑。
/// 只有把顶栏搬到页面**之外**的原生层，才能做到「页面动、顶栏钉住不动」。
///
/// 状态由 Kotlin 推（`xlrTopBar`：step / label / dark / hidden），点击回传 `xlrTopAction`。
/// 玻璃用**系统液态玻璃**（iOS 26+ 的 `.glassEffect`），低版本退回 `.ultraThinMaterial`。
///
/// ⚠ 本工程 deployment target = iOS 15：不能用 iOS 16+ 的 UnevenRoundedRectangle。
struct NativeTopBar: View {

    @State private var step = "壹"
    @State private var label = "起卦"
    @State private var dark = false
    /// 默认**隐藏**：开屏那几帧还没收到 Kotlin 的推送，先不显示，避免开屏页上挂着顶栏
    @State private var hidden = true

    @State private var labelOpen = false
    @State private var labelW: CGFloat = 0
    @State private var token = 0

    private let statusBarH: CGFloat = 46
    private let rowH: CGFloat = 40
    private let sidePad: CGFloat = 16
    private let hairline: CGFloat = 0.6

    var body: some View {
        VStack(spacing: 0) {
            Color.clear.frame(height: statusBarH)
            ZStack {
                badge
                HStack(spacing: 0) {
                    menuButton
                    Spacer(minLength: 0)
                    rightCapsule
                }
                .padding(.horizontal, sidePad)
            }
            .frame(height: rowH)
        }
        .opacity(hidden ? 0 : 1)
        .allowsHitTesting(!hidden)
        .animation(.easeOut(duration: 0.18), value: hidden)
        .onReceive(NotificationCenter.default.publisher(for: Notification.Name("xlrTopBar"))) { note in
            let info = note.userInfo
            let nextStep = (info?["step"] as? String) ?? step
            let stepChanged = nextStep != step
            step = nextStep
            xlrCurrentStep = nextStep   /* 给键盘闸用：叁页有「接着问」输入框，不许收键盘 */
            label = (info?["label"] as? String) ?? label
            dark = (info?["dark"] as? NSNumber)?.boolValue ?? dark
            hidden = (info?["hidden"] as? NSNumber)?.boolValue ?? hidden
            if stepChanged { openLabel(3.0) }
        }
    }

    // MARK: - 徽标

    private var badge: some View {
        HStack(spacing: 0) {
            /* 字号不做动画：SwiftUI 对 `.font(size:)` 的插值是**交叉淡入淡出**，
               所以以前无论怎么调，看着都是「溶解」。改成固定 21 号 + scaleEffect 缩到 17
               （缩放走 GPU，逐帧平滑）。标签也不再动宽度裁切，改成整体画满、由左往右揭开。 */
            Text(step)
                .font(.system(size: 21, design: .serif))
                .foregroundStyle(.primary)
                .fixedSize()
                .scaleEffect(labelOpen ? 17.0 / 21.0 : 1)
                .frame(width: labelOpen ? 17 : 21, height: rowH)
                .clipped()
                .animation(.timingCurve(0.34, 1.3, 0.5, 1, duration: 0.46), value: labelOpen)

            Text("· " + label)
                .font(.system(size: 12))
                .foregroundStyle(.secondary)
                .fixedSize()
                .offset(x: labelOpen ? 0 : -5)
                .background(GeometryReader { g in
                    Color.clear.onAppear { if labelW < 1 { labelW = g.size.width } }
                })
                .frame(width: labelOpen ? (labelW < 1 ? 50 : labelW) : 0, alignment: .leading)
                .clipped()
                .padding(.leading, labelOpen ? 7 : 0)
                .animation(.timingCurve(0.34, 1.3, 0.5, 1, duration: 0.5), value: labelOpen)
        }
        .frame(height: rowH)
        .contentShape(Rectangle())
        .onTapGesture { openLabel(2.0) }
    }

    private func openLabel(_ seconds: Double) {
        labelOpen = true
        token += 1
        let mine = token
        DispatchQueue.main.asyncAfter(deadline: .now() + seconds) {
            if token == mine { labelOpen = false }
        }
    }

    // MARK: - 玻璃

    private var menuButton: some View {
        Image(systemName: "line.3.horizontal")
            .font(.system(size: 15, weight: .regular))
            .foregroundStyle(.primary.opacity(0.85))
            .frame(width: 40, height: rowH)
            .modifier(GlassPlate(shape: Capsule()))
            .contentShape(Capsule())
            .onTapGesture { send("menu") }
    }

    private var rightCapsule: some View {
        HStack(spacing: 0) {
            /* 皮肤：照 Kuikly 那颗 SkinGlyph 重画。 */
            SkinGlyph(color: .primary.opacity(0.85))
                .frame(width: 43, height: rowH)
                .contentShape(Rectangle())
                .onTapGesture { send("skin") }
            Rectangle()
                .fill(Color.primary.opacity(0.10))
                .frame(width: hairline, height: 22)
            Image(systemName: dark ? "sun.max" : "moon")
                .font(.system(size: 15, weight: .regular))
                .foregroundStyle(.primary.opacity(0.85))
                .frame(width: 43, height: rowH)
                .contentShape(Rectangle())
                .onTapGesture { send("daynight") }
        }
        .frame(height: rowH)
        .modifier(GlassPlate(shape: Capsule()))
    }

    // MARK: - 动作回传

    private func send(_ act: String) {
        NotificationCenter.default.post(
            name: Notification.Name("xlrTopAction"),
            object: nil,
            userInfo: ["act": act]
        )
    }
}

/// 皮肤图标（16 格坐标，逐点照 Kuikly `YiParts.kt` 的 `SkinGlyph()`）：
/// 外圈 r6.2 描边 1.07 + 三颗 r1.1 实心点 (5.1,7.6) (6.9,4.9) (10.1,5.2)。
/// 为什么自己画：SF Symbol 里没有这颗，`paintpalette` 是系统那套彩色调色盘，
private struct SkinGlyph: View {
    var color: Color

    var body: some View {
        Canvas { ctx, size in
            let k = min(size.width, size.height) / 16
            let cx = 8 * k, cy = 8 * k, r = 6.2 * k
            var ring = Path()
            ring.addEllipse(in: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2))
            ctx.stroke(ring, with: .color(color), lineWidth: 1.07 * k)

            let dots: [(CGFloat, CGFloat)] = [(5.1, 7.6), (6.9, 4.9), (10.1, 5.2)]
            for (dx, dy) in dots {
                let dr = 1.1 * k
                let rect = CGRect(x: dx * k - dr, y: dy * k - dr, width: dr * 2, height: dr * 2)
                ctx.fill(Path(ellipseIn: rect), with: .color(color))
            }
        }
        .frame(width: 16, height: 16)
    }
}

/// iOS 26+ 用系统液态玻璃；低版本退回系统材质 + 细描边（保证有底）。
private struct GlassPlate<S: InsettableShape>: ViewModifier {
    let shape: S

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            /* .interactive()：按下去玻璃自己会「软一下」（系统液态玻璃的同款动画）；
               ⛔ 无参的 glassEffect() 是静态玻璃，按下去毫无反应 —— 点名「不是同款动画」 */
            content.glassEffect(.regular.interactive())
        } else {
            content
                .background(.ultraThinMaterial, in: shape)
                .overlay(shape.strokeBorder(Color.primary.opacity(0.10), lineWidth: 0.6))
        }
    }
}
