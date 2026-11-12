import SwiftUI
import UIKit

/// 原生「起卦卡」（壹页底部那张输入卡 + 详情）。
///
/// 详情按钮 36 / 起卦圆 42 命中 48 / 事类 52 / 详情面板 204 / 输入 16·27.2），
/// 玻璃材质照 `Skin.glassMaterial` 那三层（面色 + 竖向 rim 渐变 + 横向边光 + 1px 描边）复刻。
/// 颜色不写死：Kotlin 换肤时把当前皮肤那组色值推过来（xlrAskSkin）。
struct NativeAskCard: View {

    @ObservedObject var m: AskCardModel
    /// 卡当前在不在屏幕上（Kuikly 的 xlrAskVis）。不在就不许有任何输入框留着焦点。
    var active: Bool = true

    @State private var fieldH: CGFloat = 60
    @State private var detailH: CGFloat = 0
    @State private var cellFrames: [Int: CGRect] = [:]
    @State private var displayMode: Int = 0
    @State private var dragRect: CGRect? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            greeting
            card
            if !m.err.isEmpty {
                Text(m.err)
                    .font(.system(size: 11.5))
                    .lineSpacing(6)
                    .foregroundColor(m.accent)
                    .padding(.top, 10)
                    .padding(.horizontal, 4)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .background(GeometryReader { g in
            Color.clear.preference(key: AskHeightKey.self, value: g.size.height)
        })
        .onPreferenceChange(AskHeightKey.self) { h in m.report(height: h) }
        .onAppear { displayMode = m.mode }
        .onChange(of: m.mode) { v in
            withAnimation(.spring(response: 0.34, dampingFraction: 0.86)) { displayMode = v }
        }
    }

    /* 问候语：跟卡片同一个 VStack ⇒ 键盘抬起、详情展开都由同一条动画带着走。
       字号/行高/字距照 Kuikly：30 / 42.6 / 2.4，衬线；副句 13.5 / 字距 1.89 / 距上 9。 */
    private var greeting: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text(m.greet1)
                .font(.system(size: 30, weight: .medium, design: .serif))
                .kerning(2.4)
                .lineSpacing(max(0, 42.6 - UIFont.systemFont(ofSize: 30).lineHeight))
                .foregroundColor(m.ink)
                .fixedSize(horizontal: false, vertical: true)
            Text(m.greet2)
                .font(.system(size: 13.5))
                .kerning(1.89)
                .foregroundColor(m.dim2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.bottom, 36)
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: 0) {
            field
            Spacer().frame(height: 6)
            mainRow
            if m.mode == 2 && !m.moreOpen { numCompact }
            /* 用条件插入 + 外层 .animation：插入那一帧整张卡就跟着长，和键盘收起是同一时刻起步
               （之前用固定高度容器 + 事后量高度，量到之前高度一直是 0 ⇒ 键盘先收完、卡才长，
               看着就是「先合起来再展开」）。裁切在外层，收起时内容不会露到卡外。 */
            if m.moreOpen { detail }
        }
        .padding(.horizontal, 14)
        .padding(.top, 10)
        .padding(.bottom, 8)
        /* 回落时里面的字要跟着收起：内容按卡片圆角裁掉，玻璃壳画在裁切之外。 */
        .clipShape(RoundedRectangle(cornerRadius: m.rCard, style: .continuous))
        .animation(.easeInOut(duration: 0.26), value: m.moreOpen)
        .background(cardGlass)
        /* 把卡片的屏幕矩形报给 Kotlin：开屏那颗球要落在它上面（以前是 Kuikly 自己量，
           卡改成原生之后没人报了 ⇒ 小球没有落点，整段动画错乱）。 */
        .background(GeometryReader { g in
            Color.clear.preference(key: AskRectKey.self, value: g.frame(in: .global))
        })
        .onPreferenceChange(AskRectKey.self) { r in m.reportRect(r) }
    }

    /* 详情：高度做缓动 + 内容裁切（照 Kuikly 的 expH/clipToBounds）。
       直接 if moreOpen { detail } 的话，收起那 260ms 里内容还是原高度，
       先在卡片下沿露出来，才被卡片裁掉 —— 用户看到的就是「字残留」。 */
    private var detailBlock: some View {
        detail
            .background(GeometryReader { g in
                Color.clear.preference(key: DetailHKey.self, value: g.size.height)
            })
            .frame(height: m.moreOpen ? detailH : 0, alignment: .top)
            .clipped()
            .animation(.easeInOut(duration: 0.26), value: m.moreOpen)
            .onPreferenceChange(DetailHKey.self) { h in if h > 1 { detailH = h } }
    }

    // MARK: - 卡面玻璃（照 Skin.glassMaterial 三层）

    @ViewBuilder
    private var cardGlass: some View {
        let shape = RoundedRectangle(cornerRadius: m.rCard, style: .continuous)
        let rim = m.dark ? m.ink : Color.white
        if #available(iOS 26.0, *) {
            /* 系统液态玻璃：真折射 + 真透明。
               ⛔ 不再「翻页时换成毛玻璃」——那套降级会让卡面颜色在手指一动就跳一下
               。
               逐帧重建已经在 ContentView 那边消掉了，这里保持一种材质。 */
            Color.clear.glassEffect(.regular, in: shape)
        } else {
            ZStack {
            shape.fill(m.face)
            shape.fill(
                LinearGradient(
                    stops: [
                        .init(color: rim.opacity(0.48), location: 0),
                        .init(color: rim.opacity(0.06), location: 0.012),
                        .init(color: .clear, location: 0.045),
                        .init(color: .clear, location: 0.94),
                        .init(color: m.ink.opacity(0.02), location: 0.99),
                        .init(color: m.ink.opacity(0.055), location: 1),
                    ],
                    startPoint: .top, endPoint: .bottom
                )
            )
            shape.fill(
                LinearGradient(
                    stops: [
                        .init(color: rim.opacity(0.15), location: 0),
                        .init(color: .clear, location: 0.025),
                        .init(color: .clear, location: 0.975),
                        .init(color: m.ink.opacity(0.045), location: 1),
                    ],
                    startPoint: .leading, endPoint: .trailing
                )
            )
            shape.strokeBorder(m.edge, lineWidth: 1)
            }
        }
    }

    // MARK: - 输入区

    private var field: some View {
        ZStack(alignment: .topLeading) {
            if m.q.isEmpty {
                Text("问具体的事。例如「我这次面试能过吗」")
                    .font(.system(size: 16))
                    .foregroundColor(m.dim2)
                    .lineLimit(1)
                    .allowsHitTesting(false)
            }
            GrowingTextView(
                text: $m.q,
                fontSize: 16, lineHeight: 27.2,
                color: UIColor(m.ink), maxLines: 4,
                onHeight: { h in
                    /* 照 Kuikly 的账：文字高 = 30 + (行数-1)×27.2，字段框再垫 30
                       ⇒ 单行 60、两行 87.2……（之前只算到 30，卡片整体矮了 30dp，
                       ） */
                    let rows = min(4, max(1, Int((h / 27.2).rounded())))
                    let box = 30 + CGFloat(rows - 1) * 27.2 + 30
                    if abs(box - fieldH) > 0.5 {
                        withAnimation(.easeOut(duration: 0.14)) { fieldH = box }
                    }
                },
                onSubmit: { castNow() },
                editable: active
            )
            .frame(height: fieldH - 30)
        }
        .frame(height: fieldH, alignment: .topLeading)
        .onChange(of: m.q) { _ in m.push("q", m.q) }
    }

    // MARK: - 取数方式（轨道 + 可拖的药丸）+ 两个圆按钮

    private var mainRow: some View {
        HStack(spacing: 0) {
            modeTrack
            Spacer(minLength: 0)
            detailButton
            Spacer().frame(width: 8)
            goButton
        }
        .frame(height: 36)
    }

    /* 轨道：36 高 = 2 + 32 + 2（和两个圆钮同径、同中线）；**宽度只包住三格**，
       ⛔ 里面不能放 GeometryReader 之类会撑满的兄弟节点，否则轨道会一路长到 + 按钮。
       材质：iOS 26 系统液态玻璃；低版本退回半透明填充 + 细描边。 */
    private var modeTrack: some View {
        HStack(spacing: 2) {
            ForEach(0..<3, id: \.self) { i in
                Text(m.modeTitles[i])
                    .font(.system(size: 13.5, weight: m.mode == i ? .semibold : .regular))
                    .foregroundColor(m.mode == i ? m.ink : m.dim)
                    .padding(.horizontal, 10)
                    .frame(height: 32)
                    .background(GeometryReader { g in
                        Color.clear.preference(
                            key: CellFrameKey.self,
                            value: [i: g.frame(in: .named("track"))]
                        )
                    })
                    .contentShape(Rectangle())
                    .onTapGesture {
                        withAnimation(.spring(response: 0.34, dampingFraction: 0.86)) {
                            m.mode = i
                        }
                        m.push("mode", "\(i)")
                    }
            }
        }
        .padding(2)
        /* 药丸画在 **background** 里、且在玻璃**之前**应用：
           background 越晚应用越靠底 ⇒ 先铺药丸、再铺玻璃，药丸才压在玻璃上、文字压在药丸上。
           （之前塞 overlay ⇒ 不透明的玻璃把「月日时」整个盖住；塞 ZStack ⇒ GeometryReader 撑满，
             轨道一路长到 + 按钮。两张截图点名。） */
        .background(alignment: .topLeading) {
            GeometryReader { _ in
                let r = dragRect ?? cellFrames[displayMode] ?? .zero
                Capsule()
                    .fill(m.pill.opacity(0.5))
                    .frame(width: max(0, r.width), height: 32)
                    .modifier(GlassCapsule(fallback: m.pill))
                    .offset(x: r.minX, y: 2)
                    .scaleEffect(x: 1, y: dragRect == nil ? 1 : 0.9)
            }
            .allowsHitTesting(false)
        }
        .modifier(GlassCapsule(fallback: m.track, interactive: true))
        .coordinateSpace(name: "track")
        .onPreferenceChange(CellFrameKey.self) { v in cellFrames = v }
        .highPriorityGesture(
            DragGesture(minimumDistance: 6)
                .onChanged { g in
                    guard !cellFrames.isEmpty else { return }
                    let cur = cellFrames[m.mode] ?? .zero
                    let d = g.translation.width
                    /* 前缘跟手、后缘拖半拍 ⇒ 被拉长一截，同 Kotlin 的橡皮筋。
                       0.42/0.62 那组拉得太长，收到 0.26/0.34。 */
                    let stretch = min(abs(d) * 0.26, cur.width * 0.34)
                    let w = cur.width + stretch
                    let rawL = d >= 0 ? (cur.minX + cur.width + d) - w : cur.minX + d
                    let total = (cellFrames.values.map { $0.maxX }.max() ?? cur.maxX)
                    let l = min(max(0, rawL), max(0, total - w))
                    dragRect = CGRect(x: l, y: 0, width: w, height: 32)
                }
                .onEnded { g in
                    defer { withAnimation(.easeOut(duration: 0.18)) { dragRect = nil } }
                    guard !cellFrames.isEmpty else { return }
                    let probe = (dragRect?.midX) ?? 0
                    var best = m.mode, bestD = CGFloat.greatestFiniteMagnitude
                    for (i, f) in cellFrames {
                        let dd = abs(f.midX - probe)
                        if dd < bestD { bestD = dd; best = i }
                    }
                    if best != m.mode {
                        m.mode = best
                        m.push("mode", "\(best)")
                    }
                }
        )
    }

    private var detailButton: some View {
        Pressable(action: { m.toggleDetail() }) {
            PlusGlyph(color: m.ink, open: m.moreOpen)
                .frame(width: 14, height: 14)
                .frame(width: 36, height: 36)
                .modifier(GlassCircle(fallback: m.ctl, interactive: true))
        }
    }

    private func castNow() {
        guard !m.q.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        /* 先把问句同步给 Kotlin，再触发起卦（否则它可能还拿着上一个字） */
        m.push("q", m.q)
        m.push("cast", "1")
    }

    private var goButton: some View {
        Pressable(action: { castNow() }) {
            ZStack {
                Circle().fill(m.primary)
                ArrowGlyph(color: m.onPrimary).frame(width: 19, height: 19)
            }
            .frame(width: 36, height: 36)
            .opacity(m.q.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.3 : 1)
        }
    }

    // MARK: - 报数便捷栏

    private var numCompact: some View {
        HStack(spacing: 0) {
            Spacer(minLength: 0)
            HStack(spacing: 0) {
                numCell(0); sep; numCell(1); sep; numCell(2)
            }
            .padding(.horizontal, 3)
            .padding(.vertical, 2)
            .background(Capsule().fill(m.ctl))
            .overlay(Capsule().strokeBorder(m.edge.opacity(0.5), lineWidth: 0.6))
            Spacer(minLength: 0)
        }
        .padding(.top, 8)
        .padding(.bottom, 6)
    }

    private var sep: some View {
        Rectangle().fill(m.line).frame(width: 1, height: 12)
    }

    private func numCell(_ i: Int) -> some View {
        TextField("", text: Binding(
            get: { m.nums[i] },
            set: { v in m.setNum(i, v) }
        ))
        .keyboardType(.numberPad)
        .multilineTextAlignment(.center)
        .font(.system(size: 14))
        .foregroundColor(m.ink)
        .frame(width: 40, height: 26)
    }

    // MARK: - 详情

    private var detail: some View {
        VStack(alignment: .leading, spacing: 0) {
            Spacer().frame(height: 10)
            bgFold
            Spacer().frame(height: 10)
            detailPanel
            Spacer().frame(height: 12)
            catCapsule
            Spacer().frame(height: 4)
        }
    }

    private var bgFold: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 7) {
                Text("▸")
                    .font(.system(size: 11))
                    .foregroundColor(m.dim2)
                    .rotationEffect(.degrees(m.bgOpen ? 90 : 0))
                Text("补充背景（选填，越具体越准）")
                    .font(.system(size: 14))
                    .foregroundColor(m.dim2)
                Spacer(minLength: 0)
            }
            .frame(height: 24)
            .contentShape(Rectangle())
            .onTapGesture {
                withAnimation(.easeOut(duration: 0.22)) { m.bgOpen.toggle() }
                m.push("bgOpen", m.bgOpen ? "1" : "0")
            }

            if m.bgOpen {
                ZStack(alignment: .topLeading) {
                    if m.bg.isEmpty {
                        Text("相关的实际情况。例如：已婚；对方是同事；面试是下周三；猫上次在阳台丢的。")
                            .font(.system(size: 13))
                            .foregroundColor(m.dim2)
                            .allowsHitTesting(false)
                    }
                    GrowingTextView(
                        text: $m.bg,
                        fontSize: 13, lineHeight: 20,
                        color: UIColor(m.ink), maxLines: 4,
                        onHeight: { _ in m.push("bg", m.bg) },
                        editable: active
                    )
                    .frame(height: 84)
                }
                .padding(.top, 8)
                .frame(height: 92, alignment: .topLeading)
                .onChange(of: m.bg) { _ in m.push("bg", m.bg) }
            }
        }
    }

    private var detailPanel: some View {
        VStack(alignment: .leading, spacing: 0) {
            Spacer().frame(height: 10)
            Rectangle().fill(m.line).frame(height: 1)
            Spacer().frame(height: 12)
            Text(m.castLabel)
                .font(.system(size: 11.5))
                .lineSpacing(max(0, 17 - UIFont.systemFont(ofSize: 11.5).lineHeight))
                .foregroundColor(m.dim2)
            Spacer().frame(height: 10)
            if m.mode == 1 {
                HStack(spacing: 6) {
                    pill("\(m.year) 年", 86) { m.openPicker("year") }
                    pill("\(m.month) 月", 70) { m.openPicker("month") }
                    pill("\(m.day) 日", 70) { m.openPicker("day") }
                }
            } else {
                HStack(spacing: 6) { stepper(0); stepper(1); stepper(2) }
            }
            Spacer().frame(height: 10)
            HStack(spacing: 8) {
                Text("起卦时辰").font(.system(size: 12)).foregroundColor(m.dim2)
                pill("\(m.ziName)时", 78) { m.openPicker("zi") }
            }
            Spacer().frame(height: 10)
            Text(m.hint)
                .font(.system(size: 11.5))
                .lineSpacing(max(0, 18 - UIFont.systemFont(ofSize: 11.5).lineHeight))
                .foregroundColor(m.dim2)
                .lineLimit(3)
                .fixedSize(horizontal: false, vertical: true)
        }
        /* 不再写死 204：写死就必然在末尾堆出一块空白。现在高度跟着内容走。 */
    }

    private func pill(_ label: String, _ w: CGFloat, tap: @escaping () -> Void) -> some View {
        Pressable(action: tap) {
            HStack(spacing: 5) {
                Text(label).font(.system(size: 13.5)).foregroundColor(m.ink)
                CaretGlyph(color: m.dim).frame(width: 8, height: 8)
            }
            .frame(width: w, height: 40)
            /* 时间/年/月/日/时辰这几颗也要系统液态玻璃 */
            .modifier(GlassCapsule(fallback: m.ctl, interactive: true))
        }
    }

    private func stepper(_ i: Int) -> some View {
        HStack(spacing: 0) {
            tapBox("−") { m.stepNum(i, -1) }
            TextField("", text: Binding(
                get: { m.nums[i] },
                set: { v in m.setNum(i, v) }
            ))
            .keyboardType(.numberPad)
            .multilineTextAlignment(.center)
            .font(.system(size: 15))
            .foregroundColor(m.ink)
            .frame(width: 46, height: 34)
            tapBox("+") { m.stepNum(i, 1) }
        }
        .frame(height: 34)
        .modifier(GlassCapsule(fallback: m.ctl, interactive: true))
    }

    private func tapBox(_ s: String, tap: @escaping () -> Void) -> some View {
        Pressable(action: tap) {
            Text(s)
                .font(.system(size: 15))
                .foregroundColor(m.dim)
                .frame(width: 30, height: 34)
        }
    }

    private var catCapsule: some View {
        Pressable(action: { m.openPicker("cat") }) {
            HStack(spacing: 6) {
                Text(m.catName).font(.system(size: 13.5)).foregroundColor(m.dim)
                CaretGlyph(color: m.dim).frame(width: 8, height: 8)
            }
            .padding(.horizontal, 13)
            /* 52 ⇒ 40：跟时辰那几颗一样高就行。
               高度少了 12，整张卡的详情段也随之收短。 */
            .frame(height: 40)
            .modifier(GlassCapsule(fallback: m.ctl, interactive: true))
        }
    }
}

// MARK: - 系统液态玻璃（iOS 26+；低版本退回填充 + 细描边）

private struct GlassCapsule: ViewModifier {
    var fallback: Color
    var interactive: Bool = false

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            if interactive {
                /* .interactive()：按下去玻璃自己软一下（系统同款动画） */
                content.glassEffect(.regular.interactive(), in: Capsule())
            } else {
                content.glassEffect(.regular, in: Capsule())
            }
        } else {
            content
                .background(Capsule().fill(fallback))
                .overlay(Capsule().strokeBorder(Color.primary.opacity(0.10), lineWidth: 0.6))
        }
    }
}

private struct GlassCircle: ViewModifier {
    var fallback: Color
    var interactive: Bool = false

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            if interactive {
                content.glassEffect(.regular.interactive(), in: Circle())
            } else {
                content.glassEffect(.regular, in: Circle())
            }
        } else {
            content
                .background(Circle().fill(fallback))
                .overlay(Circle().strokeBorder(Color.primary.opacity(0.10), lineWidth: 0.6))
        }
    }
}

// MARK: - 按压反馈（按下缩一点 + 提亮，松手执行）

private struct Pressable<Content: View>: View {
    var action: () -> Void
    var active: Bool = true
    @ViewBuilder var content: () -> Content
    @GestureState private var down = false

    var body: some View {
        content()
            .scaleEffect(down && active ? 0.95 : 1)
            .brightness(down && active ? 0.06 : 0)
            .animation(.easeOut(duration: 0.12), value: down)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .updating($down) { _, s, _ in s = true }
                    .onEnded { _ in action() }
            )
    }
}

// MARK: - 几何回收

private struct AskHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = max(value, nextValue()) }
}

private struct CellFrameKey: PreferenceKey {
    static var defaultValue: [Int: CGRect] = [:]
    static func reduce(value: inout [Int: CGRect], nextValue: () -> [Int: CGRect]) {
        value.merge(nextValue()) { _, new in new }
    }
}

private struct DetailHKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = max(value, nextValue()) }
}

private struct AskRectKey: PreferenceKey {
    static var defaultValue: CGRect = .zero
    static func reduce(value: inout CGRect, nextValue: () -> CGRect) {
        let n = nextValue()
        if n.width > 1 { value = n }
    }
}

// MARK: - 字符画（同 Kotlin：⛔ 不用 ＋/→ 字符，笔画跟圆对不齐）

private struct PlusGlyph: View {
    var color: Color
    var open: Bool
    var body: some View {
        GeometryReader { g in
            let k = g.size.width / 14.0
            Path { p in
                p.move(to: CGPoint(x: 1.4 * k, y: 7 * k)); p.addLine(to: CGPoint(x: 12.6 * k, y: 7 * k))
                p.move(to: CGPoint(x: 7 * k, y: 1.4 * k)); p.addLine(to: CGPoint(x: 7 * k, y: 12.6 * k))
            }
            .stroke(color, style: StrokeStyle(lineWidth: 1.5 * k, lineCap: .round))
            .rotationEffect(.degrees(open ? 45 : 0))
        }
    }
}

private struct ArrowGlyph: View {
    var color: Color
    var body: some View {
        GeometryReader { g in
            let k = g.size.width / 19.0
            Path { p in
                p.move(to: CGPoint(x: 3.9 * k, y: 9.5 * k)); p.addLine(to: CGPoint(x: 14.2 * k, y: 9.5 * k))
                p.move(to: CGPoint(x: 9.8 * k, y: 4.9 * k)); p.addLine(to: CGPoint(x: 14.4 * k, y: 9.5 * k))
                p.move(to: CGPoint(x: 9.8 * k, y: 14.1 * k)); p.addLine(to: CGPoint(x: 14.4 * k, y: 9.5 * k))
            }
            .stroke(color, style: StrokeStyle(lineWidth: 1.5 * k, lineCap: .round, lineJoin: .round))
        }
    }
}

private struct CaretGlyph: View {
    var color: Color
    var body: some View {
        GeometryReader { g in
            let k = g.size.width / 8.0
            Path { p in
                p.move(to: CGPoint(x: 1 * k, y: 2.5 * k))
                p.addLine(to: CGPoint(x: 4 * k, y: 5.5 * k))
                p.addLine(to: CGPoint(x: 7 * k, y: 2.5 * k))
            }
            .stroke(color, style: StrokeStyle(lineWidth: 1 * k, lineCap: .round, lineJoin: .round))
        }
    }
}
