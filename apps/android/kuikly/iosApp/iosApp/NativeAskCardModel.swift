import SwiftUI

/// 原生起卦卡的状态 + 桥。
///
/// 约定：
///   · q / bg 由 Swift 自己持有，用 onChange 推给 Kotlin；
///     Kotlin 回推同一个值时不覆盖，避免光标跳。
///   · 其余状态（取数方式 / 三个数 / 年月日 / 时辰 / 事类 / 详情开合）以 Kotlin 为准：
///     Swift 点一下 → 推 `xlrAskEdit` → Kotlin 改 State → 回推 `xlrAskState`。
final class AskCardModel: ObservableObject {

    // ---- 内容（双向） ----
    @Published var q: String = "" { didSet { if oldValue != q { autoRow() } } }
    @Published var bg: String = ""

    // ---- 其余状态（Kotlin 为准） ----
    @Published var mode: Int = 0
    @Published var nums: [String] = ["", "", ""]
    @Published var year: Int = 2026
    @Published var month: Int = 1
    @Published var day: Int = 1
    @Published var zi: Int = 1
    @Published var ziName: String = "子"
    @Published var catName: String = "事类·自动"
    @Published var moreOpen: Bool = false
    @Published var bgOpen: Bool = false
    @Published var castLabel: String = ""
    @Published var hint: String = ""
    @Published var greet1: String = ""
    @Published var greet2: String = ""
    @Published var err: String = ""
    /// 开屏在不在场：只有开屏那颗球要卡片矩形（Kotlin 的 Session.cardRect 只有 SplashPage 读）。
    /// 开屏前默认 true（要先上报一次落点），开屏结束后 Kotlin 推 0，之后不再逐帧上报。
    @Published var splashOn = true
    /// Kotlin 那边装了桥（推过状态）才显示这张原生卡；没推过就什么都不画，等于回退到 Kuikly 自绘
    @Published var ready = false

    // ---- 皮肤（Kotlin 推过来的色值） ----
    @Published var ink = Color(white: 0.05)
    @Published var dim = Color(white: 0.25)
    @Published var dim2 = Color(white: 0.42)
    @Published var line = Color(white: 0, opacity: 0.13)
    @Published var face = Color(white: 1)
    @Published var edge = Color(white: 0, opacity: 0.16)
    @Published var ctl = Color(white: 1, opacity: 0.6)
    @Published var track = Color(white: 0, opacity: 0.05)
    @Published var pill = Color(white: 1, opacity: 0.9)
    @Published var primary = Color(white: 0.05)
    @Published var onPrimary = Color(white: 1)
    @Published var accent = Color(.sRGB, red: 0.72, green: 0.20, blue: 0.16)
    /// 判定色（Kotlin 的 KY_JYC）：断辞里判词那一档用的就是它，跟 accent（警示红）不是一个色
    @Published var jyc = Color(.sRGB, red: 0.70, green: 0.26, blue: 0.22)
    @Published var surface = Color(white: 0.98)
    @Published var rCard: CGFloat = 24
    @Published var dark = false

    let modeTitles = ["时间取数", "月日时", "报数"]

    /// 输入框高度：30 + (行数-1) × 27.2，最多 4 行（同 Kotlin fieldRows）
    @Published private(set) var fieldHeight: CGFloat = 60
    private var rows = 1

    private var typoSink: DispatchWorkItem?

    // MARK: - 状态进来

    func apply(_ info: [AnyHashable: Any]?) {
        guard let info else { return }
        func s(_ k: String) -> String? { info[k] as? String }
        func i(_ k: String) -> Int? { Int(s(k) ?? "") }

        if let v = s("q"), v != q { q = v }
        if let v = s("bg"), v != bg { bg = v }
        if let v = i("mode") { mode = v }
        if let v = s("n0") { nums[0] = v }
        if let v = s("n1") { nums[1] = v }
        if let v = s("n2") { nums[2] = v }
        if let v = i("year") { year = v }
        if let v = i("month") { month = v }
        if let v = i("day") { day = v }
        if let v = i("zi") { zi = v }
        if let v = s("ziName") { ziName = v }
        if let v = s("catName") { catName = v }
        if let v = s("moreOpen") { moreOpen = (v == "1") }
        if let v = s("bgOpen") { bgOpen = (v == "1") }
        if let v = s("castLabel") { castLabel = v }
        if let v = s("hint") { hint = v }
        if let v = s("greet1") { greet1 = v }
        if let v = s("greet2") { greet2 = v }
        if let v = s("err") { err = v }
        if let v = s("splash") { splashOn = (v == "1") }
        ready = true
        autoRow()
    }

    // MARK: - 皮肤进来

    func applySkin(_ info: [AnyHashable: Any]?) {
        guard let info else { return }
        func c(_ k: String) -> Color? {
            guard let hex = info[k] as? String else { return nil }
            return Color(hex: hex)
        }
        if let v = c("ink") { ink = v }
        if let v = c("dim") { dim = v }
        if let v = c("dim2") { dim2 = v }
        if let v = c("line") { line = v }
        if let v = c("face") { face = v }
        if let v = c("edge") { edge = v }
        if let v = c("ctl") { ctl = v }
        if let v = c("track") { track = v }
        if let v = c("pill") { pill = v }
        if let v = c("primary") { primary = v }
        if let v = c("onPrimary") { onPrimary = v }
        if let v = c("accent") { accent = v }
        if let v = c("jyc") { jyc = v }
        if let v = c("surface") { surface = v }
        if let s = info["rCard"] as? String, let v = Double(s) { rCard = CGFloat(v) }
        if let s = info["dark"] as? String { dark = (s == "1") }
    }

    // MARK: - 编辑出去

    func push(_ k: String, _ v: String) {
        NotificationCenter.default.post(
            name: Notification.Name("xlrAskEdit"),
            object: nil, userInfo: ["k": k, "v": v]
        )
    }

    func stepNum(_ i: Int, _ d: Int) {
        let cur = Int(nums[i]) ?? 0
        nums[i] = "\(max(0, min(99, cur + d)))"
        push("num\(i)", nums[i])
    }

    func setNum(_ i: Int, _ raw: String) {
        let digits = String(raw.filter { $0.isNumber }.prefix(2))
        guard digits != nums[i] else { return }
        nums[i] = digits
        push("num\(i)", digits)
    }

    func toggleDetail() {
        let next = !moreOpen
        withAnimation(.easeInOut(duration: 0.26)) {
            moreOpen = next
        }
        push("moreOpen", next ? "1" : "0")
    }

    /// 高度回传（Kuikly 用它在卡片上方摆问候语）
    func report(height: CGFloat) {
        NotificationCenter.default.post(
            name: Notification.Name("xlrAskHeight"),
            object: nil, userInfo: ["h": Double(height)]
        )
    }

    /// 卡片在屏幕上的矩形（点）：开屏那颗球要落在它上面
    private var lastRect: CGRect = .zero
    func reportRect(_ r: CGRect) {
        /* ⛔ 开屏结束之后没人读这个矩形了（Kotlin 侧只有 SplashPage 读 Session.cardRect）。
           继续上报的话，翻页位移、键盘让位时卡片的全局 frame 每帧都在变
           ⇒ 每帧一条 `xlrAskEdit`，Kotlin 那边还要 split + toFloat ×4 解析。 */
        guard splashOn else { return }
        guard abs(r.minX - lastRect.minX) > 0.5 || abs(r.minY - lastRect.minY) > 0.5
            || abs(r.width - lastRect.width) > 0.5 || abs(r.height - lastRect.height) > 0.5
        else { return }
        lastRect = r
        /* Kuikly 的 cardRect 是**物理像素**（它自己的布局坐标系），Swift 拿到的是点
           ⇒ 乘屏幕倍率再报，否则小球会飞到左下角。 */
        let s = UIScreen.main.scale
        NotificationCenter.default.post(
            name: Notification.Name("xlrAskEdit"),
            object: nil,
            userInfo: ["k": "rect",
                       "v": "\(r.minX * s),\(r.minY * s),\(r.width * s),\(r.height * s)"]
        )
    }

    /// 打开系统滚轮选择器（选完回推给 Kotlin）
    func openPicker(_ kind: String) {
        let (title, values, labels, selected): (String, [String], [String], String)
        switch kind {
        case "year":
            let ys = (2020...2040).map { $0 }
            (title, values, labels, selected) = ("年", ys.map { "\($0)" }, ys.map { "\($0) 年" }, "\(year)")
        case "month":
            (title, values, labels, selected) = ("月", (1...12).map { "\($0)" }, (1...12).map { "\($0) 月" }, "\(month)")
        case "day":
            let n = daysIn(year, month)
            (title, values, labels, selected) = ("日", (1...n).map { "\($0)" }, (1...n).map { "\($0) 日" }, "\(day)")
        case "zi":
            let names = ["子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"]
            (title, values, labels, selected) = ("起卦时辰", (1...12).map { "\($0)" }, names.map { $0 + "时" }, "\(zi)")
        default:
            let cats: [(String, String)] = [
                ("auto", "事类·自动"), ("love", "感情"), ("work", "事业"), ("money", "财运"),
                ("lost", "寻物"), ("health", "健康"), ("travel", "出行"), ("study", "考试"),
                ("deal", "合作"), ("lawsuit", "官司"),
            ]
            let sel = cats.first(where: { $0.1 == catName })?.0 ?? "auto"
            (title, values, labels, selected) = ("事类", cats.map { $0.0 }, cats.map { $0.1 }, sel)
        }
        PickerCenter.shared.present(
            ["title": title, "values": values.joined(separator: "\u{1}"),
             "labels": labels.joined(separator: "\u{1}"), "selected": selected]
        ) { v in
            guard let v = v else { return }
            NotificationCenter.default.post(
                name: Notification.Name("xlrAskEdit"),
                object: nil, userInfo: ["k": "pick_" + kind, "v": v]
            )
        }
    }

    private func daysIn(_ y: Int, _ m: Int) -> Int {
        switch m {
        case 1, 3, 5, 7, 8, 10, 12: return 31
        case 4, 6, 9, 11: return 30
        default: return ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0) ? 29 : 28
        }
    }

    // MARK: - 输入框行数（照 Kotlin fieldRows 的口径估：每行约 27.2pt，一行 16pt 字 ≈ 22 个汉字宽）

    private func autoRow() {
        typoSink?.cancel()
        let text = q
        let work = DispatchWorkItem { [weak self] in
            guard let self else { return }
            let perLine = 21.0                     /* 344pt 卡宽 - 28 内边距 ≈ 316pt / 15.5pt 每字 ≈ 20 字 */
            let lines = max(1, min(4, Int(ceil(Double(text.count) / perLine))))
            self.rows = lines
            self.fieldHeight = 30 + CGFloat(lines - 1) * 27.2
        }
        typoSink = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05, execute: work)
    }
}

extension Color {
    /// "#RRGGBB" 或 "#RRGGBBAA"
    init(hex: String) {
        var s = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.hasPrefix("#") { s.removeFirst() }
        var v: UInt64 = 0
        Scanner(string: s).scanHexInt64(&v)
        let r, g, b, a: Double
        if s.count == 8 {
            r = Double((v >> 24) & 0xFF) / 255
            g = Double((v >> 16) & 0xFF) / 255
            b = Double((v >> 8) & 0xFF) / 255
            a = Double(v & 0xFF) / 255
        } else {
            r = Double((v >> 16) & 0xFF) / 255
            g = Double((v >> 8) & 0xFF) / 255
            b = Double(v & 0xFF) / 255
            a = 1
        }
        self = Color(.sRGB, red: r, green: g, blue: b, opacity: a)
    }
}
