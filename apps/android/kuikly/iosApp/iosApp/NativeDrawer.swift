import SwiftUI
import UIKit

/// 记录抽屉（原生）。数值与结构照 Kuikly 那版：
/// 面板宽 0.72 屏、右侧圆角 22、搜索框 38 高（顶部 56）、页签 12.5 + 18×2 下划线、
/// 行 56 高（日期 10pt + 问句 12.5 + ⋮ 34）、左滑露出 76 宽的删除、按天分组。
final class DrawerModel: ObservableObject {
    @Published var open: Bool = false
    /// 0 = 全收起，1 = 全打开（跟手用）
    @Published var prog: CGFloat = 0
    @Published var recs: [RecItem] = []
    @Published var rules: [RuleItem] = []
    /// 回收站（已删、可还原）
    @Published var dead: [DeadItem] = []
    /// 整个抽屉切成回收站页（点底部胶囊升起 / 再点收起）
    @Published var trashPage = false
    @Published var tab: String = "cases"
    @Published var noModel = false
    @Published var modelReady = true
    /// 流式文本（复盘 / 追问共用，Kotlin 每来一段就覆盖成累计全文）
    @Published var live: String = ""
    @Published var q: String = ""
    @Published var detailId: String = ""
    @Published var menuId: String = ""
    @Published var swipeId: String = ""
    @Published var swipeDx: CGFloat = 0
    /// 刚弹出菜单时短暂忽略空白点击（否则长按松手那一下就把菜单关了）
    var suppressUntil: Date = .distantPast

    struct RecItem: Identifiable, Equatable {
        let id: String, q: String, bg: String, gua: String, cat: String
        let t: String, duanci: String, fb: String, note: String, ans: String
        let rv: String, rvErr: String
        let thread: [[String]]
    }
    struct RuleItem: Identifiable, Equatable {
        let id: String, at: String, gua: String, rule: String, why: String
        /* 来源那一卦：q＝当时所问，from＝那一卦的 id（点进去能看全） */
        let q: String, from: String
    }
    /// 回收站里的一条（Kotlin 侧 RecStore.loadDeleted() 推过来，只有 id / 所问 / 时间）
    struct DeadItem: Identifiable, Equatable {
        let id: String, q: String, t: String
    }

    var filtered: [RecItem] {
        let s = q.trimmingCharacters(in: .whitespaces)
        guard !s.isEmpty else { return recs }
        return recs.filter { ($0.q + $0.duanci).lowercased().contains(s.lowercased()) }
    }

    var detail: RecItem? { filtered.first { $0.id == detailId } ?? recs.first { $0.id == detailId } }

    /// 准则详情页（和卦例一样：列表只出关键句，点进去看依据与来源）
    @Published var ruleId: String = ""
    var rule: RuleItem? { rules.first { $0.id == ruleId } }

    var accentColorRed: Color { Color(.sRGB, red: 0.70, green: 0.26, blue: 0.22) }

    struct DayGroup: Identifiable {
        let id: String
        let rows: [RecItem]
    }

    func grouped() -> [DayGroup] {
        var order: [String] = []
        var map: [String: [RecItem]] = [:]
        for r in filtered {
            let day = r.t.count >= 10 ? String(r.t.prefix(10)) : r.t
            if map[day] == nil { order.append(day); map[day] = [] }
            map[day]?.append(r)
        }
        return order.map { DayGroup(id: $0, rows: map[$0] ?? []) }
    }

    func load(_ json: String) {
        guard let d = json.data(using: .utf8),
              let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else { return }
        noModel = ((o["noModel"] as? String) == "1")
        modelReady = !noModel
        var rs: [RecItem] = []
        for r in (o["recs"] as? [[String: Any]]) ?? [] {
            func s(_ k: String) -> String { (r[k] as? String) ?? "" }
            rs.append(RecItem(id: s("id"), q: s("q"), bg: s("bg"), gua: s("gua"), cat: s("cat"),
                              t: s("t"), duanci: s("duanci"), fb: s("fb"), note: s("note"),
                              ans: s("ans"), rv: s("rv"), rvErr: s("rvErr"),
                              thread: (r["thread"] as? [[String]]) ?? []))
        }
        var ls: [RuleItem] = []
        for r in (o["rules"] as? [[String: Any]]) ?? [] {
            func s(_ k: String) -> String { (r[k] as? String) ?? "" }
            ls.append(RuleItem(id: s("id"), at: s("at"), gua: s("gua"), rule: s("rule"), why: s("why"),
                               q: s("q"), from: s("from")))
        }
        recs = rs
        rules = ls
        var ds: [DeadItem] = []
        for r in (o["dead"] as? [[String: Any]]) ?? [] {
            func s(_ k: String) -> String { (r[k] as? String) ?? "" }
            ds.append(DeadItem(id: s("id"), q: s("q"), t: s("t")))
        }
        dead = ds
        if detailId != "" && !recs.contains(where: { $0.id == detailId }) { detailId = "" }
    }

    func act(_ k: String, _ v: String) {
        NotificationCenter.default.post(name: Notification.Name("xlrDrawerAct"), object: nil,
                                        userInfo: ["k": k, "v": v])
    }
    func sync() { act("sync", "") }
}

struct NativeDrawer: View {
    @ObservedObject var m: AskCardModel          // 只借它的皮肤色
    @ObservedObject var s: DrawerModel

    @State private var closing = false

    private var W: CGFloat { UIScreen.main.bounds.width }
    private var panelW: CGFloat { W * 0.72 }

    var body: some View {
        /* ⚠ 这里**不读** s.prog：它每帧都在变，一读就等于「开合动画的每一帧都重建整块面板」
           （搜索框 + 页签 + 整个列表 + 回收站页）。
           位移和遮暗都交给下面两个薄壳，它们各自观察 prog ⇒ 只有那一层重组。 */
        ZStack(alignment: .leading) {
            DrawerBackdrop(s: s) { close() }
            panel
                .frame(width: panelW)
                .modifier(DrawerSlide(s: s, width: panelW))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .onChange(of: s.open) { on in
            withAnimation(.interactiveSpring(response: 0.34, dampingFraction: 0.86)) {
                s.prog = on ? 1 : 0
            }
            if on {
                s.sync()
            } else {
                s.detailId = ""
                s.menuId = ""
                s.swipeId = ""
                s.swipeDx = 0
                /* 抽屉收起 ⇒ 回收站页也退回去，下次打开还是记录页 */
                s.trashPage = false
            }
        }
    }

    private func close() {
        s.open = false
        s.act("close", "")
    }

    // MARK: - 面板

    private var panel: some View {
        ZStack(alignment: .topLeading) {
            /* 面板材质：
               iOS 26 走系统液态玻璃（真折射、真透明）；低版本退回实底 + 竖向渐变
               （低版本没有 glassEffect，只能实底，不然整块透出来没法看）。 */
            if #available(iOS 26.0, *) {
                Color.clear.glassEffect(.regular, in: RightRoundedRect(radius: 22))
            } else {
                RightRoundedRect(radius: 22).fill(m.surface)
                RightRoundedRect(radius: 22)
                    .fill(LinearGradient(colors: [m.ink.opacity(0.025), .clear, m.ink.opacity(0.055)],
                                         startPoint: .leading, endPoint: .trailing))
            }
            RightRoundedRect(radius: 22)
                .stroke(m.edge.opacity(m.dark ? 0.18 : 0.50), lineWidth: 1)
            content
        }
        .frame(width: panelW)
        .frame(maxHeight: .infinity)
        /* ⛔ 面板上**不再挂任何横滑手势**」）。
           收回方式只剩：点面板左边的遮罩、点「设置」行、系统返回键。
           这样记录行的左滑（露删除键）跟抽屉完全解耦，不会互相抢。 */
        .simultaneousGesture(TapGesture().onEnded { s.menuId = ""; s.swipeId = "" })
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: 0) {
            /* 记录页 ⇄ 回收站页：整块切换。 */
            ZStack(alignment: .top) {
                if s.trashPage {
                    trashPage
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                } else {
                    casesPage
                        .transition(.opacity)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .animation(.spring(response: 0.34, dampingFraction: 0.88), value: s.trashPage)

            /* 底部这条胶囊两个页都在：记录页点它升起回收站，回收站页点它收回去。
               详情页里不占地方（Kuikly 就是 detail == null 才画）。 */
            if s.detail == nil { trashPill }
            Rectangle().fill(m.line).frame(height: 1)
            settingsRow
        }
        .frame(width: panelW)
        .clipShape(RightRoundedRect(radius: 22))
    }

    /* 记录页（搜索 + 页签 + 列表） */
    private var casesPage: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
            tabs
            Rectangle().fill(m.line).frame(height: 1)
            body_list
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }

    /* 底部那条胶囊（记录页 / 回收站页都在）：显示条数，点一下升起回收站，再点一下收回去。 */
    private var trashPill: some View {
        HStack(spacing: 0) {
            Text("回收站 · \(s.dead.count) 则")
                .font(.system(size: 11.5))
                .foregroundColor(m.dim2)
            Spacer(minLength: 0)
            Text(s.trashPage ? "˄" : "˅")
                .font(.system(size: 11))
                .foregroundColor(m.dim2)
        }
        .padding(.horizontal, 12)
        .frame(height: 36)
        .background(Capsule().fill(m.ctl))
        .overlay(Capsule().strokeBorder(m.edge.opacity(0.5), lineWidth: 0.6))
        .contentShape(Capsule())
        .onTapGesture {
            haptic(.light)
            s.trashPage.toggle()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
    }

    /* 回收站页（整块升起来的那一层）：标题 + 全部已删记录 + 一键还原 / 一键清理。
       ⚠ 以前是「胶囊下面挂 10 条小行、每条两个小按钮」，判「用起来别扭」
         ⇒ 改成整页：列表只读，底部两颗一键按钮。 */
    private var trashPage: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("回收站")
                    .font(.system(size: 20, weight: .medium, design: .serif))
                    .foregroundColor(m.ink)
                Spacer(minLength: 0)
                Text("\(s.dead.count) 则")
                    .font(.system(size: 11.5))
                    .foregroundColor(m.dim2)
            }
            .padding(.horizontal, 14)
            .padding(.top, 56)
            .padding(.bottom, 10)

            Rectangle().fill(m.line).frame(height: 1)

            if s.dead.isEmpty {
                Text("回收站是空的。")
                    .font(.system(size: 11.5))
                    .foregroundColor(m.dim2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
                Spacer(minLength: 0)
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        ForEach(s.dead) { d in
                            HStack(spacing: 8) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(d.q.isEmpty ? "（没写所问）" : d.q)
                                        .font(.system(size: 13))
                                        .foregroundColor(m.ink)
                                        .lineLimit(2)
                                    Text(monthDay(d.t) + (d.t.count >= 16 ? "　" + String(d.t.suffix(5)) : ""))
                                        .font(.system(size: 10))
                                        .foregroundColor(m.dim2)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                /* 单条自己决定：还原回卦例本 / 彻底删 */
                                miniCapsule("还原", danger: false) { s.act("restore", d.id) }
                                miniCapsule("彻底删", danger: true) { s.act("purge", d.id) }
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            Rectangle().fill(m.line.opacity(0.7)).frame(height: 0.6)
                        }
                        Spacer(minLength: 8)
                    }
                }
            }

            /* 一键两颗（禁用的那颗自动降透明度：没东西可还原/清理时不许点） */
            HStack(spacing: 10) {
                bigCapsule("一键还原", danger: false, enabled: !s.dead.isEmpty) {
                    s.act("restoreAll", "")
                }
                bigCapsule("清理", danger: true, enabled: !s.dead.isEmpty) {
                    s.act("purgeAll", "")
                }
            }
            .padding(.horizontal, 14)
            .padding(.top, 10)
            .padding(.bottom, 4)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }

    private func bigCapsule(_ t: String, danger: Bool, enabled: Bool,
                            tap: @escaping () -> Void) -> some View {
        Button(action: { if enabled { haptic(.light); tap() } }) {
            Text(t)
                .font(.system(size: 12.5, weight: .medium))
                .foregroundColor(enabled ? (danger ? Color(.sRGB, red: 0.72, green: 0.20, blue: 0.16) : m.ink)
                                         : m.dim2)
                .frame(maxWidth: .infinity)
                .frame(height: 38)
                .background(Capsule().fill(m.ctl))
                .overlay(Capsule().strokeBorder(m.edge.opacity(0.5), lineWidth: 0.6))
        }
        .buttonStyle(.plain)
        .opacity(enabled ? 1 : 0.45)
    }

    private func miniCapsule(_ t: String, danger: Bool, tap: @escaping () -> Void) -> some View {
        Button(action: { haptic(.light); tap() }) {
            Text(t)
                .font(.system(size: 11))
                .foregroundColor(danger ? Color(.sRGB, red: 0.72, green: 0.20, blue: 0.16) : m.ink)
                .padding(.horizontal, 10)
                .frame(height: 26)
                .background(Capsule().fill(m.ctl))
                .overlay(Capsule().strokeBorder(m.edge.opacity(0.5), lineWidth: 0.6))
        }
        .buttonStyle(.plain)
    }

    /* 设置入口在抽屉**底部**（Kuikly 那版就是这样：48 高一行，齿轮 + 设置 + 缺 Key 小圆点） */
    private var settingsRow: some View {
        HStack(spacing: 5) {
            Spacer(minLength: 0)
            GearGlyph(color: m.dim).frame(width: 11, height: 11)
            Text("设置").font(.system(size: 12.5)).foregroundColor(m.dim)
            if !s.modelReady {
                Circle().fill(m.accent).frame(width: 5, height: 5)
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 48)
        .contentShape(Rectangle())
        .onTapGesture {
            haptic(.light)
            s.open = false
            s.act("close", "")
            s.act("settings", "")
        }
    }

    private var header: some View {
        HStack(spacing: 8) {
            HStack(spacing: 8) {
                SearchGlyph(color: m.dim2).frame(width: 14, height: 14)
                ZStack(alignment: .leading) {
                    if s.q.isEmpty {
                        Text("搜索卦例内容…")
                            .font(.system(size: 13))
                            .foregroundColor(m.dim2)
                    }
                    TextField("", text: Binding(get: { s.q }, set: { s.q = $0 }))
                        .font(.system(size: 13))
                        .foregroundColor(m.ink)
                        .accentColor(m.ink)
                }
            }
            .padding(.horizontal, 13)
            .frame(height: 44)
            /* 搜索栏也用系统液态玻璃 */
            .background { searchBg }
            .overlay(Capsule().strokeBorder(m.edge.opacity(0.45), lineWidth: 0.6))
        }
        /* 左右边距收一点：搜索框占宽再大些 */
        .padding(.leading, 12)
        .padding(.trailing, 10)
        .padding(.top, 56)
        .padding(.bottom, 10)
    }

    @ViewBuilder
    private var searchBg: some View {
        if #available(iOS 26.0, *) {
            Color.clear.glassEffect(.regular.interactive(), in: Capsule())
        } else {
            Capsule().fill(m.ctl)
        }
    }

    private var tabs: some View {
        HStack(spacing: 16) {
            tabItem("卦例", on: s.tab == "cases") { s.tab = "cases"; s.detailId = ""; s.menuId = ""; s.ruleId = "" }
            tabItem("准则 \(s.rules.count)", on: s.tab == "rules") { s.tab = "rules"; s.detailId = ""; s.menuId = ""; s.ruleId = "" }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
    }

    private func tabItem(_ label: String, on: Bool, tap: @escaping () -> Void) -> some View {
        VStack(spacing: 0) {
            Text(label)
                .font(.system(size: 12.5, weight: on ? .medium : .regular))
                .foregroundColor(on ? m.ink : m.dim2)
                .padding(.vertical, 8)
            Rectangle()
                .fill(on ? m.accent : Color.clear)
                .frame(width: on ? 18 : 0, height: 2)
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: tap)
    }

    @ViewBuilder
    private var body_list: some View {
        if let d = s.detail {
            RecDetailView(m: m, s: s, r: d)
        } else if s.tab == "rules" {
            if let rl = s.rule {
                RuleDetailView(m: m, s: s, r: rl)
            } else {
                rulesList
            }
        } else if s.filtered.isEmpty {
            Text(s.q.isEmpty
                 ? "还没有记录。\n每起一卦都会自动记进这里。事后回来标一下准不准——错的卦才值钱。"
                 : "没有匹配「\(s.q)」的记录。")
                .font(.system(size: 11.5))
                .lineSpacing(4)
                .foregroundColor(m.dim2)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
            Spacer(minLength: 0)
        } else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    ForEach(s.grouped()) { g in
                        Text(dayLabel(g.id))
                            .font(.system(size: 11.5))
                            .foregroundColor(m.dim2)
                            .padding(.leading, 14)
                            .padding(.top, 14)
                            .padding(.bottom, 6)
                        ForEach(g.rows) { r in
                            RecRowView(m: m, s: s, r: r)
                        }
                    }
                    Spacer(minLength: 20)
                }
            }
            /* 点空白处：菜单收起、左滑露出的删除键也收回去
               。
               刚弹出来的 0.35s 内不响应菜单，免得松手就收。 */
            .simultaneousGesture(TapGesture().onEnded {
                if s.menuId != "" && Date() > s.suppressUntil { s.menuId = "" }
                if s.swipeId != "" { s.swipeId = "" }
            })
        }
    }

    private var rulesList: some View {
        Group {
            if s.rules.isEmpty {
                Text("还没有准则。\n准则只从【你确认过的复盘】里来——不自动写、不偷偷学。")
                    .font(.system(size: 11.5))
                    .lineSpacing(5)
                    .foregroundColor(m.dim2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
                Spacer(minLength: 0)
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        /* 列表只出「关键句」（那条准则本身）——依据、来源都收进详情，
                           照记录条那套。 */
                        ForEach(Array(s.rules.enumerated()), id: \.element.id) { i, r in
                            /* ⚠ 用 onTapGesture 而不是 Button：Button 套在 LazyVStack 里
                               会把纵向拖拽也吃掉（抽屉里就滑不动了）。卦例那几行也是这么做的。 */
                            VStack(alignment: .leading, spacing: 4) {
                                Text(r.rule)
                                    .font(.system(size: 13.5, weight: .medium))
                                    .lineSpacing(4)
                                    .lineLimit(2)
                                    .multilineTextAlignment(.leading)
                                    .foregroundColor(m.ink)
                                Text("第 \(i + 1) 条 · \(r.at)\(r.gua.isEmpty ? "" : " · " + r.gua)")
                                    .font(.system(size: 10.5))
                                    .foregroundColor(m.dim2)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 11)
                            .contentShape(Rectangle())
                            .onTapGesture { s.ruleId = r.id }
                            Rectangle().fill(m.line.opacity(0.7)).frame(height: 1)
                                .padding(.leading, 14)
                        }
                    }
                }
            }
        }
    }

    private func groupedOld() -> [(String, [DrawerModel.RecItem])] {
        var order: [String] = []
        var map: [String: [DrawerModel.RecItem]] = [:]
        for r in s.filtered {
            let day = r.t.count >= 10 ? String(r.t.prefix(10)) : r.t
            if map[day] == nil { order.append(day); map[day] = [] }
            map[day]?.append(r)
        }
        return order.map { ($0, map[$0] ?? []) }
    }
}

// MARK: - 一行

private struct RecordSwipe: ViewModifier {
    let changed: (CGFloat) -> Void
    let ended: (CGFloat, CGFloat) -> Void
    let cancelled: () -> Void

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 18.0, *) {
            content.gesture(HorizontalRecordPan(changed: changed, ended: ended, cancelled: cancelled))
        } else {
            content.simultaneousGesture(
                DragGesture(minimumDistance: 8)
                    .onChanged { value in
                        guard abs(value.translation.width) > abs(value.translation.height) else { return }
                        changed(value.translation.width)
                    }
                    .onEnded { value in
                        ended(value.translation.width,
                              value.predictedEndTranslation.width - value.translation.width)
                    }
            )
        }
    }
}

@available(iOS 18.0, *)
private struct HorizontalRecordPan: UIGestureRecognizerRepresentable {
    let changed: (CGFloat) -> Void
    let ended: (CGFloat, CGFloat) -> Void
    let cancelled: () -> Void

    func makeCoordinator(converter: CoordinateSpaceConverter) -> Coordinator {
        Coordinator()
    }

    func makeUIGestureRecognizer(context: Context) -> UIPanGestureRecognizer {
        let pan = UIPanGestureRecognizer()
        pan.delegate = context.coordinator
        return pan
    }

    func handleUIGestureRecognizerAction(_ recognizer: UIPanGestureRecognizer, context: Context) {
        // Use window coordinates so moving the row never changes the measured translation.
        let translation = recognizer.translation(in: nil).x
        switch recognizer.state {
        case .began, .changed:
            changed(translation)
        case .ended:
            let deceleration = CGFloat(UIScrollView.DecelerationRate.normal.rawValue)
            let projectedDelta = recognizer.velocity(in: nil).x / 1000 * deceleration / (1 - deceleration)
            ended(translation, projectedDelta)
        case .cancelled, .failed:
            cancelled()
        default:
            break
        }
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            guard let pan = gestureRecognizer as? UIPanGestureRecognizer else { return false }
            let velocity = pan.velocity(in: nil)
            return abs(velocity.x) > abs(velocity.y)
        }
    }
}

private struct RecRowView: View {
    @ObservedObject var m: AskCardModel
    @ObservedObject var s: DrawerModel
    let r: DrawerModel.RecItem

    /* 本行的横向位移放在**本地**：逐帧拖动只重绘这一行。
       以前挂在共享 model 上，每帧 publish 一次 → 整个列表重算，所以拖起来一顿一顿的。 */
    @State private var off: CGFloat = 0
    @State private var opened = false
    /* 这次拖动的起点与「已经白吃掉的那 8pt」：起步不跳、来回拖也不累加 */
    @State private var dragging = false
    @State private var startOff: CGFloat = 0
    @State private var firstTx: CGFloat = 0
    private var dx: CGFloat { off }

    /// 高亮底色（长按浮起那张预览用）：浅色皮肤压一点墨，深色皮肤提一点亮
    private var hl: Color { m.ink.opacity(m.dark ? 0.16 : 0.075) }

    /* 菜单项：系统 contextMenu / Menu 共用（长按可从条目一直拖到某项再松手） */
    @ViewBuilder private var menuItems: some View {
        Button { s.detailId = r.id } label: {
            Label("看这一卦", systemImage: "doc.text.magnifyingglass")
        }
        Button { s.act("fb", "\(r.id)|对") } label: {
            Label("应验", systemImage: "checkmark.circle")
        }
        Button { s.act("fb", "\(r.id)|错"); s.detailId = r.id } label: {
            Label("没应验", systemImage: "xmark.circle")
        }
        Button { s.detailId = r.id } label: {
            Label("接着问", systemImage: "bubble.left.and.bubble.right")
        }
        Button { UIPasteboard.general.string = r.duanci } label: {
            Label("复制断辞", systemImage: "doc.on.doc")
        }
        Divider()
        Button(role: .destructive) { s.act("del", r.id) } label: {
            Label("删除", systemImage: "trash")
        }
    }

    var body: some View {
        ZStack(alignment: .trailing) {
            /* 删除键**常驻布局**（不再按 dx 插入/移除）：以前一滑才插进来，SwiftUI 那一帧
               先按 76 宽重排一次，看着就是「先往右闪一下再往左滑」。
               位置常驻 + 平时 opacity 0（见下）⇒ 平时一点红都看不见，滑开多少露多少。 */
            Button {
                    haptic(.medium)
                    s.act("del", r.id)
                    s.swipeId = ""
                    withAnimation(.spring(response: 0.24, dampingFraction: 0.9)) { off = 0; opened = false }
                } label: {
                    VStack(spacing: 3) {
                        Image(systemName: "trash")
                            .font(.system(size: 15, weight: .medium))
                        Text("删除").font(.system(size: 11.5))
                    }
                    .foregroundColor(.white)
                    /* 拉开时图标「弹」一下：0.86 → 1（只做视觉，不占布局） */
                    .scaleEffect(min(1.0, max(0.86, -off / 76)))
                    /* 宽度跟着露出来的量走：拖过头（橡皮筋）时红块一起被拉长，
                       否则行和红块之间会裂出一条面板色。 */
                    .frame(width: max(76, -off), height: 58)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(Color(.sRGB, red: 1.0, green: 0.23, blue: 0.19)))
                }
                .buttonStyle(.plain)
                .contentShape(Rectangle())
                /* 没滑开就点不到它（它常驻，但被行盖着） */
                .allowsHitTesting(off < -1)
                /* ⚠ 还要「看不见」：长按时系统把行抬成预览卡，底下这块红就从卡片边上漏出来
                   。只动 opacity 不动结构 ⇒ 不重排，也不会有「先往右闪一下」。 */
                .opacity(off < -0.5 ? 1 : 0)
            HStack(spacing: 8) {
                Text(monthDay(r.t))
                    .font(.system(size: 10.5))
                    .foregroundColor(m.dim2)
                Text(r.q)
                    .font(.system(size: 13.5, weight: .medium))
                    .foregroundColor(m.ink)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        if dx < 0 { off = 0; opened = false; s.swipeId = "" } else { s.detailId = r.id }
                    }
                Menu { menuItems } label: {
                    Text("⋮").font(.system(size: 16)).foregroundColor(m.dim2)
                        .frame(width: 34, height: 34)
                }
                .menuStyle(BorderlessButtonMenuStyle())
            }
            .padding(.leading, 10)
            .padding(.trailing, 4)
            .frame(height: 58)
            .background(
                /* 行底恒为面板色（和抽屉底同色）：不能随 dx 切换 ——
                   切换那一下的观感就是「刚滑就闪一下」。
                   按住高亮那层已删：系统 contextMenu 大约 0.5s 就把这一行换成预览卡，
                   原来要等 0.9s 的那道高亮根本亮不出来（预览卡自己带高亮底，见 previewRow），
                   而它挂的 DragGesture(minimumDistance: 0) 会在手指一碰下就成立，
                   把 ScrollView 的 pan 按死 ⇒ 整个卦例列表滑不动。 */
                RoundedRectangle(cornerRadius: 14).fill(m.surface)
            )
            .offset(x: dx)
            .modifier(RecordSwipe(
                changed: { translation in
                    if !dragging {
                        dragging = true
                        startOff = off
                        firstTx = translation
                    }
                    off = Self.rubber(startOff + translation - firstTx)
                },
                ended: { translation, projectedDelta in
                    guard dragging else { return }
                    dragging = false
                    let predicted = startOff + translation + projectedDelta - firstTx
                    let open = predicted < -76 * (startOff < -30 ? 0.60 : 0.52)
                    let velocity = max(-1.8, min(1.8, projectedDelta / 900))
                    if open {
                        withAnimation(.interpolatingSpring(mass: 0.85, stiffness: 250,
                                                          damping: 20, initialVelocity: velocity)) {
                            off = -76
                            opened = true
                        }
                    } else {
                        withAnimation(.spring(response: 0.42, dampingFraction: 0.95)) {
                            off = 0
                            opened = false
                        }
                    }
                    if open && s.swipeId != r.id { haptic(.light) }
                    s.swipeId = open ? r.id : ""
                },
                cancelled: {
                    guard dragging else { return }
                    dragging = false
                    withAnimation(.spring(response: 0.42, dampingFraction: 0.95)) {
                        off = opened ? -76 : 0
                    }
                }
            ))
            /* 别的行被划开：本行自己收回去（不再经过共享 model 的逐帧值） */
            .onChange(of: s.swipeId) { v in
                if v != r.id && opened {
                    /* 别的行被划开 ⇒ 本行按微信那支的「关」曲线收回（不回摆、长尾收干净） */
                    withAnimation(.spring(response: 0.42, dampingFraction: 0.95)) { off = 0; opened = false }
                }
            }
            /* 长按：仍是系统 contextMenu（条目能一直拖到某一项上再松手），
               但预览换成**我们自己画的、带高亮的这一条** —— 。
               系统把原行换成它自己的快照，高亮画不进快照 ⇒ 只能自己给预览。 */
            .modifier(RowMenu(items: menuItems, preview: previewRow))
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
    }

    /// 橡皮筋：拖过 76 之后不是硬停 —— 继续跟手，但越拖越沉（有上限），
    private static func rubber(_ x: CGFloat) -> CGFloat {
        let limit: CGFloat = 76
        if x >= 0 { return 0 }                                  /* 闭合状态不能往右拖 */
        if x >= -limit { return x }                             /* 正常区间：1:1 */
        let over = -x - limit
        return -(limit + over * 0.34 / (1 + over / 70))          /* 超出部分按阻尼折，最多再走 ~28pt */
    }

    /* 浮起那一张（系统 contextMenu 的预览）：同一条记录 + 高亮底。
       宽度照抽屉面板：0.72 屏 − 行左右各 10。 */
    private var previewRow: some View {
        HStack(spacing: 8) {
            Text(monthDay(r.t))
                .font(.system(size: 10.5))
                .foregroundColor(m.dim2)
            Text(r.q)
                .font(.system(size: 13.5, weight: .medium))
                .foregroundColor(m.ink)
                .lineLimit(1)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 10)
        .frame(width: UIScreen.main.bounds.width * 0.72 - 20, height: 58, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(m.surface))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(hl))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

/// 长按菜单：能带预览就用我们自己的预览（iOS 16+），否则退回系统快照。
private struct RowMenu<M: View, P: View>: ViewModifier {
    let items: M
    let preview: P

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 16.0, *) {
            content.contextMenu(menuItems: { items }, preview: { preview })
        } else {
            content.contextMenu { items }
        }
    }
}

private struct RecMenu: View {
    @ObservedObject var m: AskCardModel
    var hasModel: Bool
    var onOpen: () -> Void
    var onYes: () -> Void
    var onNo: () -> Void
    var onFollow: () -> Void
    var onCopy: () -> Void
    var onDelete: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            item("看这一卦", action: onOpen)
            sep
            item("应验", action: onYes)
            sep
            item("没应验", action: onNo)
            sep
            item("接着问", action: onFollow, disabled: !hasModel)
            sep
            item("复制断辞", action: onCopy)
            sep
            item("删除", action: onDelete, danger: true)
        }
        .frame(width: 148)
        /* 原生菜单的质感：毛玻璃底 + 细描边 + 投影，行高压缩到 36（Kuikly 那版就是这样） */
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 13, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 13, style: .continuous)
            .strokeBorder(Color.white.opacity(m.dark ? 0.12 : 0.35), lineWidth: 0.6))
        .shadow(color: .black.opacity(0.22), radius: 16, y: 8)
    }

    private var sep: some View {
        Rectangle().fill(m.line.opacity(0.7)).frame(width: 148, height: 0.5)
    }

    private func item(_ t: String, action: @escaping () -> Void, danger: Bool = false,
                      disabled: Bool = false) -> some View {
        Button(action: {
            if disabled { return }
            haptic(.light)
            action()
        }) {
            HStack(spacing: 0) {
                Text(t)
                    .font(.system(size: 13))
                    .foregroundColor(danger ? Color(.sRGB, red: 0.78, green: 0.22, blue: 0.18)
                                     : (disabled ? m.dim2 : m.ink))
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .frame(width: 148, height: 36)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// 轻触反馈（跟 Kuikly 那版的 Platform.haptic 对齐）
private func haptic(_ style: UIImpactFeedbackGenerator.FeedbackStyle = .light) {
    let g = UIImpactFeedbackGenerator(style: style)
    g.prepare()
    g.impactOccurred()
}

// MARK: - 准则详情

private struct RuleDetailView: View {
    @ObservedObject var m: AskCardModel
    @ObservedObject var s: DrawerModel
    let r: DrawerModel.RuleItem

    private func secLabel(_ t: String) -> some View {
        Text(t)
            .font(.system(size: 11.5, weight: .semibold))
            .kerning(1.8)
            .foregroundColor(m.ink.opacity(0.72))
            .padding(.bottom, 7)
    }

    private func hr() -> some View {
        VStack(spacing: 0) {
            Spacer().frame(height: 15)
            Rectangle().fill(m.line.opacity(0.55)).frame(height: 1)
            Spacer().frame(height: 15)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button { s.ruleId = "" } label: {
                    Text("‹ 返回准则").font(.system(size: 13)).foregroundColor(m.dim)
                        .frame(height: 30)
                }
                .buttonStyle(.plain)
                Spacer()
                Text("准则").font(.system(size: 10.5)).foregroundColor(m.dim2)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            Rectangle().fill(m.line).frame(height: 1)

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    secLabel("结论")
                    Text(r.rule).font(.system(size: 14.5)).lineSpacing(6)
                    hr()
                    secLabel("依据")
                    Text(r.why.isEmpty ? "这条没留下依据。" : r.why)
                        .font(.system(size: 12.5)).lineSpacing(5)
                        .foregroundColor(r.why.isEmpty ? m.dim2 : m.dim)
                    hr()
                    secLabel("来源")
                    if !r.gua.isEmpty {
                        Text("这一卦：\(r.gua)").font(.system(size: 12.5)).foregroundColor(m.dim)
                    }
                    if !r.q.isEmpty {
                        Text("所问：\(r.q)").font(.system(size: 12.5)).foregroundColor(m.dim)
                            .padding(.top, 4)
                    }
                    Text("记于 \(r.at)").font(.system(size: 10.5)).foregroundColor(m.dim2)
                        .padding(.top, 6)
                    hr()
                    Text("删掉这条准则")
                        .font(.system(size: 13))
                        .foregroundColor(Color(.sRGB, red: 0.72, green: 0.20, blue: 0.16))
                        .frame(height: 34)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            s.act("ruleDel", r.id)
                            s.ruleId = ""
                        }
                    Spacer().frame(height: 24)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
            }
        }
    }
}

// MARK: - 详情

/// 详情视图底边在全局坐标里的位置（量键盘压上来多少用）
private struct DetailBottomKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = max(value, nextValue()) }
}

/// 「点抽屉任意地方收键盘」：挂一个 window 级的 tap。
/// 为什么不用 SwiftUI 的 onTapGesture：这层是自绘界面，输入控件（UITextView）与面板上的
/// 落在输入框 / 按钮上的那一下直接放过 —— 否则点一下输入框自己就把键盘收了。
final class TapOutsideToDismiss: NSObject, UIGestureRecognizerDelegate {
    static let shared = TapOutsideToDismiss()
    private var rec: UITapGestureRecognizer?

    func install() {
        guard rec == nil else { return }
        guard let win = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .first(where: { $0.isKeyWindow }) ?? UIApplication.shared.windows.first
        else { return }
        let r = UITapGestureRecognizer(target: self, action: #selector(fire))
        r.cancelsTouchesInView = false      /* 底下那一行/那颗按钮照旧收到这一下 */
        r.delegate = self
        win.addGestureRecognizer(r)
        rec = r
    }

    func remove() {
        let r = rec
        rec = nil
        /* 下一拍再摘：可能正是在它自己的 action 里被叫到的 */
        DispatchQueue.main.async { if let r = r { r.view?.removeGestureRecognizer(r) } }
    }

    @objc private func fire() { xlrEndEditing() }

    func gestureRecognizer(_ g: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        var v = touch.view
        while let cur = v {
            if cur is UITextView || cur is UITextField || cur is UIControl { return false }
            v = cur.superview
        }
        return true
    }
}

private struct RecDetailView: View {
    @ObservedObject var m: AskCardModel
    @ObservedObject var s: DrawerModel
    let r: DrawerModel.RecItem
    @State private var note: String = ""
    @State private var followQ: String = ""
    @State private var followBusy = false
    /* 上一段流式答案：数据落库回来的那一两拍先顶住，别让它「闪一下没了」 */
    @State private var lastAns = ""
    /* 键盘高度：底部输入栏要跟着顶上去（这块是自绘层，系统不会替我们避让） */
    @State private var kbH: CGFloat = 0
    /* 键盘顶边（屏幕坐标）与本详情视图底边（全局坐标）：两者相减 = 键盘实际盖住本视图多少。
       不拿「屏高 - 键盘」硬算，也不猜安全区 —— 面板是不是铺到屏幕底、有没有下巴，都自动对上。 */
    @State private var kbTop: CGFloat = UIScreen.main.bounds.height
    @State private var detailBottom: CGFloat = 0
    /* 面板底部已经在安全区里了：只用「键盘高 - 安全区」补差，别多顶一截 */
    private var safeBottom: CGFloat {
        (UIApplication.shared.windows.first?.safeAreaInsets.bottom) ?? 0
    }
    /* 「实际情况」那个多行框有没有在编辑 */
    @State private var noteFocused = false
    /* 键盘盖住本视图的量（正数才是真的挡住了） */
    private var kbOverlap: CGFloat { max(0, detailBottom - kbTop) }
    @State private var reviewAsked = false
    @State private var askReview = false
    /* 刚发出去、还没回写成 thread 的那一问（先显示出来，别让界面像没反应） */
    @State private var pendingQ = ""

    /* 把「实际情况」那个框滚到可视区底部（等键盘那套动画走完再滚，早了会滚到半截布局上） */
    private func scrollNote(_ sp: ScrollViewProxy, after: Double) {
        DispatchQueue.main.asyncAfter(deadline: .now() + after) {
            withAnimation(.easeOut(duration: 0.24)) { sp.scrollTo("noteField", anchor: .bottom) }
        }
    }

    /* 发一句追问：输入框回车（.onSubmit）和「发送」按钮都走这里 */
    private func sendFollow() {
        let q = followQ.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty, !followBusy else { return }
        followBusy = true
        pendingQ = q
        s.act("follow", "\(r.id)|\(q)")
        followQ = ""
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button { s.detailId = "" } label: {
                    Text("‹ 返回列表").font(.system(size: 13)).foregroundColor(m.dim)
                        .frame(height: 30)
                }
                .buttonStyle(.plain)
                Spacer()
                Text("复盘").font(.system(size: 10.5)).foregroundColor(m.dim2)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            Rectangle().fill(m.line).frame(height: 1)

            ScrollViewReader { sp in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    secLabel("所问")
                    Text(r.q).font(.system(size: 14.5)).lineSpacing(7)
                    if !r.bg.isEmpty {
                        Spacer().frame(height: 10)
                        Text("背景").font(.system(size: 10.5, weight: .semibold))
                            .kerning(1.2).foregroundColor(m.dim2)
                        Text(r.bg).font(.system(size: 13)).lineSpacing(6).foregroundColor(m.dim)
                            .padding(.top, 3)
                    }
                    hr()
                    secLabel("卦体")
                    Text(r.gua).font(.system(size: 14.5, design: .serif)).lineSpacing(7)
                    Text("事类：\(r.cat.isEmpty ? "通用" : r.cat) · 记于 \(r.t)")
                        .font(.system(size: 11)).foregroundColor(m.dim2)
                    hr()
                    secLabel("当时的断辞")
                    if r.duanci.isEmpty {
                        Text("这一卦没出过断辞。").font(.system(size: 11)).foregroundColor(m.dim2)
                    } else {
                        /* 按 Android `DocView` 那一套排版渲染
                           （小标题 / 判词大字 / 正文真粗体）。以前直接贴原串 ⇒ `**暴雨**`
                           的星号原样露出来。
                           bigFirst：本机速断首块立大字，模型断辞不放大。 */
                        DocBlocksView(m: m, raw: r.duanci, bigFirst: r.ans != "model")
                            .padding(.top, 2)
                    }
                    /* ---- 追问：有才显示 ---- */
                    if !r.thread.isEmpty || followBusy {
                        hr()
                        secLabel(r.thread.isEmpty ? "追问" : "追问（\(r.thread.count + (followBusy ? 1 : 0))）")
                        ForEach(Array(r.thread.enumerated()), id: \.offset) { _, p in
                            HStack(alignment: .top, spacing: 0) {
                                Text("问 · ").font(.system(size: 12.5)).foregroundColor(m.dim2)
                                Text(p.count > 0 ? p[0] : "")
                                    .font(.system(size: 12.5, weight: .medium)).lineSpacing(4)
                            }
                            if p.count > 1 {
                                Text(p[1]).font(.system(size: 12))
                                    .lineSpacing(4).foregroundColor(m.dim)
                                    .padding(.top, 3)
                            }
                            Spacer().frame(height: 9)
                        }
                        /* 刚发出去、还没落进 thread 的那一问：先把问句和流式答顶在这儿，
                           不然按下去之后整块「没反应」 */
                        if followBusy {
                            HStack(alignment: .top, spacing: 0) {
                                Text("问 · ").font(.system(size: 12.5)).foregroundColor(m.dim2)
                                Text(pendingQ).font(.system(size: 12.5, weight: .medium)).lineSpacing(4)
                            }
                            if s.live.isEmpty {
                                ThreeDots(color: m.dim2).padding(.top, 6)
                            } else {
                                Text(s.live).font(.system(size: 12))
                                    .lineSpacing(4).foregroundColor(m.dim)
                                    .padding(.top, 3)
                            }
                            Spacer().frame(height: 9)
                        }
                    }
                    hr()
                    secLabel("是否应验")
                    /* 口径：应验管的是这一卦所有的断辞 —— 当时的断辞 + 追问，一起算 */
                    Text("这一卦的断辞（连追问一起）算不算应验")
                        .font(.system(size: 10.5)).foregroundColor(m.dim2)
                        .padding(.bottom, 8)
                    HStack(spacing: 8) {
                        setBtn(r.fb == "对" ? "应验 ✓" : "应验", primary: r.fb == "对") {
                            s.act("fb", "\(r.id)|对")
                        }
                        setBtn(r.fb == "错" ? "没应验 ✓" : "没应验", primary: r.fb == "错") {
                            s.act("fb", "\(r.id)|错")
                        }
                        Spacer()
                        setBtn("删掉这则", primary: false, danger: true) {
                            s.act("del", r.id)
                            s.detailId = ""
                        }
                    }
                    Spacer().frame(height: 12)
                    Text("实际情况").font(.system(size: 10.5, weight: .semibold))
                        .kerning(1.2).foregroundColor(m.dim2)
                        .padding(.bottom, 6)
                    ZStack(alignment: .topLeading) {
                        RoundedRectangle(cornerRadius: 12)
                            .fill(m.ink.opacity(m.dark ? 0.10 : 0.035))
                        RoundedRectangle(cornerRadius: 12)
                            .strokeBorder(m.edge.opacity(0.55), lineWidth: 1)
                        if note.isEmpty {
                            Text("点这里，写下实际结果…")
                                .font(.system(size: 12))
                                .foregroundColor(m.dim2)
                                .padding(.horizontal, 12)
                                .padding(.top, 12)
                                .allowsHitTesting(false)
                        }
                        GrowingTextView(text: $note, fontSize: 13, lineHeight: 21,
                                        color: UIColor(m.ink), maxLines: 5,
                                        onHeight: { _ in }, showsDoneBar: true,
                                        onFocus: { f in noteFocused = f })
                            .frame(height: 84)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 8)
                    }
                    .frame(height: 100)
                    /* 让 ScrollViewReader 能把它滚到键盘上方（点输入框时） */
                    .id("noteField")
                    .onChange(of: note) { _ in s.act("note", "\(r.id)|\(note)") }

                    /* ---- 复盘（标了「没应验」才有，属于「是否应验」这一块） ---- */
                    if r.fb == "错" {
                        Spacer().frame(height: 14)
                        Text("复盘").font(.system(size: 10.5, weight: .semibold))
                            .kerning(1.2).foregroundColor(m.dim2)
                            .padding(.bottom, 6)
                        if !r.rv.isEmpty {
                            Text(r.rv).font(.system(size: 12.5)).lineSpacing(6)
                            Spacer().frame(height: 8)
                            HStack(spacing: 8) {
                                setBtn("写入准则", primary: true) { s.act("ruleAdd", r.id); s.tab = "rules" }
                                setBtn("算了，不记", primary: false) { s.act("ruleDrop", r.id) }
                            }
                            Spacer().frame(height: 6)
                            Text("写入后，以后每次解卦都会一并交给模型")
                                .font(.system(size: 10.5)).foregroundColor(m.dim2)
                        } else if s.noModel {
                            Text("要复盘得先接一个模型——\n复盘会把这一卦和实际结果一起交给模型，让它指出错在哪一步。\n（不接模型的话，这一条就只留在记录里，不生成复盘。）")
                                .font(.system(size: 10.5)).lineSpacing(5).foregroundColor(m.dim2)
                        } else if !r.rvErr.isEmpty {
                            Text(r.rvErr).font(.system(size: 10.5)).foregroundColor(s.accentColorRed)
                            Spacer().frame(height: 6)
                            setBtn("重新复盘", primary: false) { s.act("review", r.id) }
                        } else if !r.note.isEmpty {
                            Group {
                                if s.live.isEmpty {
                                    Text("复盘进行中…").font(.system(size: 10.5)).foregroundColor(m.dim2)
                                } else {
                                    Text(s.live).font(.system(size: 12.5)).lineSpacing(6)
                                }
                            }
                            .onAppear {
                                if !reviewAsked { reviewAsked = true; s.act("review", r.id) }
                            }
                        } else {
                            /* 没填「实际情况」就复盘 = 让模型凭空猜，先问一句 */
                            Text(s.live.isEmpty
                                 ? "还没反馈实际情况。复盘要把这一卦和实际结果一起交给模型，它才看得出错在哪一步。"
                                 : s.live)
                                .font(.system(size: s.live.isEmpty ? 10.5 : 12.5))
                                .lineSpacing(s.live.isEmpty ? 5 : 6)
                                .foregroundColor(s.live.isEmpty ? m.dim2 : m.ink)
                            if s.live.isEmpty {
                                Spacer().frame(height: 6)
                                setBtn("复盘这一卦", primary: false) { askReview = true }
                            } else if !s.live.isEmpty {
                                /* 流式跑完 → 数据会重新推一次，rv 落库后这一块自然换成结果 */
                                EmptyView()
                            }
                        }
                    }

                    /* 不是模型解的：底部没有输入栏，在这里说明一句 */
                    if r.ans != "model" {
                        Spacer().frame(height: 16)
                        secLabel("接着问")
                        Text("用模型解过这一卦之后，这里就能接着问。")
                            .font(.system(size: 10.5)).foregroundColor(m.dim2)
                    }

                    Spacer().frame(height: 28)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
                /* 点详情里的空白处：收输入法（否则键盘挡住了就没法关） */
                .background(
                    Color.clear
                        .contentShape(Rectangle())
                        .onTapGesture { xlrEndEditing() }
                )
            }
            /* 键盘起来时把「可视区」底边裁到键盘之上（这块是自绘层，系统不会替我们避让），
               再把「实际情况」那个框滚到可视区底部 ⇒ 输入框跟页面一起抬上来。
               ：键盘弹出来了，输入框却压在键盘底下、页面一动不动。 */
            .padding(.bottom, noteFocused ? kbOverlap : 0)
            .onChange(of: noteFocused) { f in
                if f { scrollNote(sp, after: 0.28) }
            }
            .onChange(of: kbOverlap) { _ in
                if noteFocused { scrollNote(sp, after: 0.28) }
            }
            }

            /* ---- 接着问：输入栏钉在详情底部；键盘一弹就跟着顶到键盘之上 ----
               ⚠ 正在写「实际情况」时不显示它 —— 那块键盘是给反馈用的，
                 两根输入栏一起被顶起来看着很怪。 */
            if r.ans == "model" && !noteFocused {
                VStack(spacing: 0) {
                    Rectangle().fill(m.line).frame(height: 1)
                    HStack(spacing: 8) {
                        ZStack(alignment: .leading) {
                            if followQ.isEmpty {
                                Text("接着问一句…").font(.system(size: 12)).foregroundColor(m.dim2)
                            }
                            TextField("", text: $followQ)
                                .font(.system(size: 12)).foregroundColor(m.ink)
                                .submitLabel(.send)
                                .onSubmit { sendFollow() }
                        }
                        .padding(.horizontal, 12)
                        .frame(height: 36)
                        .background(Capsule().fill(m.ctl))
                        .overlay(Capsule().strokeBorder(m.edge.opacity(0.5), lineWidth: 0.6))
                        /* 问着时只转三点，不写「问着呢」 */
                        if followBusy {
                            ThreeDots(color: m.accent)
                                .frame(width: 44)
                        } else {
                            setBtn("发送", primary: true) { sendFollow() }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 9)
                    .padding(.bottom, 9)
                }
                .background(m.surface)
                .padding(.bottom, max(0, kbH - safeBottom))
            }
        }
        .onAppear { note = r.note }
        /* 写「实际情况」时点抽屉任意地方都能收键盘（UIKit 层挂 window tap，见 TapOutsideToDismiss）。
           ⚠ 只在写实际情况时挂：底部「接着问」栏在的时候，点「发送」不该把键盘收掉。 */
        .onChange(of: noteFocused) { f in
            if f { TapOutsideToDismiss.shared.install() } else { TapOutsideToDismiss.shared.remove() }
        }
        .onDisappear { TapOutsideToDismiss.shared.remove() }
        /* 键盘让位：底部输入栏跟着键盘顶上去（自绘层没有系统的自动避让） */
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillChangeFrameNotification)) { n in
            guard let f = (n.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue)?.cgRectValue else { return }
            withAnimation(.easeOut(duration: 0.22)) {
                kbH = max(0, UIScreen.main.bounds.height - f.origin.y)
                kbTop = f.origin.y
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
            withAnimation(.easeOut(duration: 0.22)) {
                kbH = 0
                kbTop = UIScreen.main.bounds.height
            }
        }
        /* 本详情视图的底边（全局坐标）：跟键盘顶边相减就是「键盘真的盖住我多少」 */
        .background(GeometryReader { g in
            Color.clear.preference(key: DetailBottomKey.self, value: g.frame(in: .global).maxY)
        })
        .onPreferenceChange(DetailBottomKey.self) { v in
            if abs(v - detailBottom) > 0.5 { detailBottom = v }
        }
        .onChange(of: r.id) { _ in
            note = r.note
            reviewAsked = false
            followBusy = false
            followQ = ""
            pendingQ = ""
        }
        .onChange(of: r.thread.count) { _ in followBusy = false; pendingQ = ""; lastAns = "" }
        .onChange(of: s.live) { v in
            if v.isEmpty { followBusy = false } else { lastAns = v }
        }
        .alert("还未反馈实际情况", isPresented: $askReview) {
            Button("确定复盘", role: .destructive) {
                s.live = ""
                s.act("review", r.id)
            }
            Button("先去填实际情况", role: .cancel) {}
        } message: {
            Text("复盘是把这一卦和实际结果一起交给模型，让它指出错在哪一步。你现在还没填「实际情况」，模型只能凭空推测，复盘会不准。确定要复盘吗？")
        }
    }

    /* 板块标题：比正文更「像标题」——半粗、字距拉开、墨色略收
        */
    private func secLabel(_ t: String) -> some View {
        Text(t)
            .font(.system(size: 11.5, weight: .semibold))
            .kerning(1.8)
            .foregroundColor(m.ink.opacity(0.72))
            .padding(.bottom, 7)
    }

    /* 板块之间的淡分隔线 */
    private func hr() -> some View {
        VStack(spacing: 0) {
            Spacer().frame(height: 15)
            Rectangle().fill(m.line.opacity(0.55)).frame(height: 1)
            Spacer().frame(height: 15)
        }
    }

    private func setBtn(_ t: String, primary: Bool, danger: Bool = false, tap: @escaping () -> Void) -> some View {
        Button(action: tap) {
            Text(t)
                .font(.system(size: 13))
                .foregroundColor(danger ? Color(.sRGB, red: 0.72, green: 0.20, blue: 0.16) : m.ink)
                .padding(.horizontal, 12)
                .frame(height: 34)
                .background(Capsule().fill(primary ? m.pill : m.ctl))
                .overlay(Capsule().strokeBorder(m.edge.opacity(0.5), lineWidth: 0.6))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - 形状 / 图标 / 小工具

/// 抽屉面板的位移 + 命中开关：只观察 prog，**不牵动面板内容**重建。
private struct DrawerSlide: ViewModifier {
    @ObservedObject var s: DrawerModel
    let width: CGFloat
    func body(content: Content) -> some View {
        content
            .offset(x: -width * (1 - s.prog))
            .allowsHitTesting(s.prog > 0.001)
    }
}

/// 主页那条遮暗：进度越大越暗（Kuikly 那版是 0.10 的黑），单独成层同理。
private struct DrawerBackdrop: View {
    @ObservedObject var s: DrawerModel
    var onTap: () -> Void

    var body: some View {
        Color(.sRGB, red: 0.07, green: 0.06, blue: 0.05)
            .opacity(0.10 * Double(s.prog))
            .ignoresSafeArea()
            .allowsHitTesting(s.prog > 0.01)
            .onTapGesture { onTap() }
    }
}

/// 加载态：三点跳动（记录详情与叁页同一套）
struct ThreeDots: View {
    var color: Color
    @State private var on = false
    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<3) { i in
                Circle().fill(color).frame(width: 5, height: 5)
                    .opacity(on ? 1 : 0.32)
                    .scaleEffect(on ? 1.18 : 0.8)
                    .animation(.easeInOut(duration: 0.42).repeatForever(autoreverses: true)
                        .delay(Double(i) * 0.14), value: on)
            }
        }
        .onAppear { on = true }
    }
}

struct RightRoundedRect: Shape {
    var radius: CGFloat
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let r = radius
        p.move(to: CGPoint(x: rect.minX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX - r, y: rect.minY))
        p.addArc(center: CGPoint(x: rect.maxX - r, y: rect.minY + r), radius: r,
                 startAngle: .degrees(-90), endAngle: .degrees(0), clockwise: false)
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - r))
        p.addArc(center: CGPoint(x: rect.maxX - r, y: rect.maxY - r), radius: r,
                 startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false)
        p.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        p.closeSubpath()
        return p
    }
}

private struct SearchGlyph: View {
    var color: Color
    var body: some View {
        GeometryReader { g in
            let k = g.size.width / 13.0
            ZStack(alignment: .topLeading) {
                Circle().strokeBorder(color, lineWidth: 1.2 * k)
                    .frame(width: 9 * k, height: 9 * k)
                Rectangle().fill(color)
                    .frame(width: 1.2 * k, height: 5 * k)
                    .rotationEffect(.degrees(-45))
                    .offset(x: 9.4 * k, y: 7.6 * k)
            }
        }
    }
}

private struct GearGlyph: View {
    var color: Color
    var body: some View {
        GeometryReader { g in
            let r = g.size.width / 2
            Path { p in
                for i in 0..<8 {
                    let a = Double(i) * 45.0 * .pi / 180.0
                    let ca = CGFloat(cos(a)), sa = CGFloat(sin(a))
                    p.move(to: CGPoint(x: r + ca * r * 0.52, y: r + sa * r * 0.52))
                    p.addLine(to: CGPoint(x: r + ca * r * 0.96, y: r + sa * r * 0.96))
                }
                p.addEllipse(in: CGRect(x: r * 0.38, y: r * 0.38, width: r * 1.24, height: r * 1.24))
            }
            .stroke(color, style: StrokeStyle(lineWidth: r * 0.22, lineCap: .butt))
        }
    }
}

private func monthDay(_ t: String) -> String {
    if t.count < 10 { return t }
    let mm = String(t.dropFirst(5).prefix(2)).replacingOccurrences(of: "^0", with: "", options: .regularExpression)
    let dd = String(t.dropFirst(8).prefix(2)).replacingOccurrences(of: "^0", with: "", options: .regularExpression)
    return mm + "·" + dd
}

private func dayLabel(_ day: String) -> String {
    let f = DateFormatter()
    f.dateFormat = "yyyy-MM-dd"
    let today = f.string(from: Date())
    let yest = f.string(from: Calendar.current.date(byAdding: .day, value: -1, to: Date()) ?? Date())
    if day == today { return "今天" }
    if day == yest { return "昨天" }
    if day.count >= 10 {
        let mm = String(day.dropFirst(5).prefix(2)).replacingOccurrences(of: "^0", with: "", options: .regularExpression)
        let dd = String(day.dropFirst(8).prefix(2)).replacingOccurrences(of: "^0", with: "", options: .regularExpression)
        if day.prefix(4) == today.prefix(4) { return "\(mm) 月 \(dd) 日" }
    }
    return day
}
