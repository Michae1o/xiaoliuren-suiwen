import SwiftUI
import UIKit

/// 收输入法（全 App 共用一个口子）
func xlrEndEditing() {
    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder),
                                    to: nil, from: nil, for: nil)
}

/// 根视图。除了把 Kuikly 页面挂进 SwiftUI，还负责两件事：
/// **状态栏文字颜色跟着皮肤走**、**键盘让位**。
///
/// 状态栏：`Info.plist` 里开了 `UIViewControllerBasedStatusBarAppearance`，但工程里没人设过样式，
/// 于是永远黑字；黑夜 / 莫高窟皮肤下几乎看不见。Kotlin 侧皮肤一变就发 `xlrThemeChanged`，
/// 这里收下切 `preferredColorScheme`，状态栏反过来变白，键盘外观也跟着对。
///
/// 键盘让位：高度、时长、曲线全部由 Kotlin 侧量好后经 `xlrIme` 发过来（那份高度是实测校准过的），
/// 这里只负责把值转成「往上平移多少」，动画本身在 KuiklyRenderViewPage 里用 CALayer transform 做。
struct ContentView: View {

    /// 初始值直接读本地皮肤键，避免「刚启动那一下」用错颜色：
    /// Kotlin 侧 NSUserDefaults 的键统一带 `xlr-k-llm.` 前缀。
    @State private var isDarkTheme: Bool = ContentView.readInitialDark()

    /* 原生选择器（时辰/事类/年/月/日）：Kotlin 发 xlrPickerShow，这里弹系统滚轮 sheet */
    @ObservedObject private var pickerCenter = PickerCenter.shared
    /* 原生起卦卡：Kuikly 那份卡在 iOS 上不再画，改成这块 SwiftUI（高度动画走 Core Animation） */
    @ObservedObject private var ask = AskCardModel()
    /* 原生记录抽屉的状态（数据由 Kotlin 推 JSON 过来） */
    @StateObject private var drawer = DrawerModel()
    /// 三页轨道的横向位移**单独**存：逐帧变化只让这层薄壳重算，
    /// 不牵动起卦卡（卡里有 UITextView + 液态玻璃，跟着重算就是掉帧来源）。
    @StateObject private var shift = PageShiftModel()
    /* 原生卡只在「壹页 + 没有整屏遮罩」时露出来（Kotlin 那边 xlrAskVis 推过来） */
    @State private var askShown = false
    /* 点详情那一刻的键盘位移快照：详情一开，卡片底边只走一次（从键盘上方落回 71），
       而且和卡片长高同一条 0.26s 曲线 ⇒ 「详情把键盘顶下去」。
       不做这个快照的话，键盘收起是系统另一条动画，看起来就是先合再开。 */
    @State private var kbSnap: CGFloat = 0

    private var askLift: CGFloat {
        if drawer.open { return 0 }        /* 抽屉开着时，别让壹页的卡跟着键盘抬 */
        return ask.moreOpen ? max(0, kbShift - kbSnap) : kbShift
    }

    private static func readInitialDark() -> Bool {
        let skin = UserDefaults.standard.string(forKey: "xlr-k-llm.K_SKIN") ?? "xuan"
        return skin == "hei" || skin == "mg"
    }

    /// 键盘让位：整页上移的量（pt）。
    /// 换算：上移 = 键盘高 − 59（59 = 卡片底部留白 71 → 12 的差），
    /// 这样卡片底边正好停在键盘上方 12pt，而页面底边还压在键盘下面 59pt，全程不会露底色。
    @State private var kbShift: CGFloat = 0
    /// 现在允许键盘（Kotlin 推的 xlrImeAllow：设置页填 Key / 引导 / 皮肤菜单）。
    /// 「不在壹页就把多余键盘收掉」那道闸要看它让路（）。
    @State private var imeAllowed = false
    /// 键盘自己的动画时长/曲线（跟键盘同一套，手感才对）
    @State private var kbDur: Double = 0.25
    @State private var kbCurve: UIView.AnimationOptions = .curveEaseOut

    /* 记录行长按菜单：系统 action sheet（原生材质 + 红字删除），条目由 Kotlin 推过来 */
    @State private var sheetItems: [String] = []
    @State private var sheetDanger: Int = -1
    @State private var sheetShown = false

    private func postSheetPick(_ idx: Int) {
        NotificationCenter.default.post(
            name: Notification.Name("xlrActionSheetPick"),
            object: nil,
            userInfo: ["idx": idx]
        )
    }

    var body: some View {
        ZStack(alignment: .top) {
            KuiklyRenderViewPage(
                pageName: "Yi",
                data: ["appId": 1],
                shiftY: kbShift,
                shiftDuration: kbDur,
                shiftCurve: kbCurve
            )
            /* ★ 顶栏是原生层，挂在页面之外：页面被键盘顶上去时它不动（钉住）。
               Kotlin 侧 TopBar 在 iOS 上只让出同样的高度（46 + 40）。 */
            NativeTopBar()
            /* 原生起卦卡：贴底、跟随键盘（kbShift 与 Kuikly 页面用的是同一个位移）。
               ⚠ 「不显示」不能从视图树里摘掉：开屏那颗球要靠它量出落点（xlrAskRect），
               摘了就没落点、整段开屏动画错乱。所以常驻 + 透明。 */
            if ask.ready {
                AskCardHost(ask: ask, shift: shift, active: askShown)
                    .padding(.horizontal, 22)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .padding(.bottom, 71)
                    .offset(y: -askLift)
                    .opacity(askShown ? 1 : 0)
                    /* 抽屉盖上来时才让位：淡出而不是瞬消。
                       曲线用 spring（跟手系），⛔ 不用线性 tween。 */
                    .animation(.interactiveSpring(response: 0.30, dampingFraction: 0.90), value: askShown)
                    .allowsHitTesting(askShown)
            }
            /* 原生记录抽屉（iOS）：整块 SwiftUI，数据由 Kotlin 推 JSON */
            NativeDrawer(m: ask, s: drawer)
        }
            /* ⚠ 必须忽略**所有**区域（container + keyboard）：
               只写 .container 时 SwiftUI 仍会按键盘把容器压矮（实机实测 874 → 546），
               Kuikly 按 546 重排、再叠平移 ⇒ 底部露出一大片底色。整屏尺寸恒定，平移多少就是多少。 */
            .ignoresSafeArea()
            /* 键盘让位的数据源：Kotlin 侧 installKeyboard() 量好高度后 post 的 `xlrIme`。
               h = 键盘可见高度（pt），0 表示收起。 */
            .onReceive(NotificationCenter.default.publisher(for: Notification.Name("xlrIme"))) { note in
                let info = note.userInfo
                let h = (info?["h"] as? NSNumber)?.doubleValue ?? 0
                kbDur = (info?["dur"] as? NSNumber)?.doubleValue ?? 0.25
                if let c = (info?["curve"] as? NSNumber)?.uintValue {
                    kbCurve = UIView.AnimationOptions(rawValue: c << 16)
                } else {
                    kbCurve = .curveEaseOut
                }
                xlrLastKbHeight = CGFloat(h)
                /* 跟键盘同一条曲线、同一节奏：系统键盘大约是 (0.25,0.1,0.25,1)，
                   时长取键盘的但封顶 0.22s —— 之前用满 0.25s 的 easeInOut，
                   起手慢、看着比键盘「慢半拍」。 */
                withAnimation(.timingCurve(0.25, 0.1, 0.25, 1,
                                           duration: min(max(0.12, kbDur), 0.22))) {
                    kbShift = max(0, CGFloat(h) - 59)
                }
                xlrLastShift = kbShift
                /* ★ 直接调 host：r50 里 ap 一直是 0 —— updateUIViewController 这条路没把位移送下去，
                   这里绕开它，值一算完就落到容器的 transform 上。 */
                xlrHostRef?.applyShift(kbShift, duration: kbDur, curve: kbCurve)
                /* ★ 兜底闸：卡不在壹页、也没开抽屉/遮罩时，键盘就不该存在 ——
                   无论它是谁叫起来的，当场收掉。
                   ⚠ 必须放过 `barHidden`（设置页/引导/皮肤菜单/依据浮层）：设置里要填 Key，
                     一收就变成「点输入框没反应」。
                   ⚠ 也必须放过**叁页**：那里有「接着问」输入框，收掉＝点了没反应（2026-11-11 报的）。 */
                if h > 0 && !askShown && !drawer.open && !imeAllowed && xlrCurrentStep != "叁" {
                    xlrEndEditing()
                }
            }
            .preferredColorScheme(isDarkTheme ? .dark : .light)
            /* 记录行菜单：系统 action sheet。Kotlin 侧推 `xlrActionSheet`（items + 哪个是红字），
               选完回 `xlrActionSheetPick`（idx，取消给 -1）。 */
            .confirmationDialog("", isPresented: $sheetShown, titleVisibility: .hidden) {
                ForEach(Array(sheetItems.enumerated()), id: \.offset) { idx, label in
                    Button(label, role: idx == sheetDanger ? .destructive : nil) {
                        postSheetPick(idx)
                    }
                }
                Button("取消", role: .cancel) { postSheetPick(-1) }
            }
            .onReceive(NotificationCenter.default.publisher(for: Notification.Name("xlrActionSheet"))) { note in
                sheetItems = (note.userInfo?["items"] as? [String]) ?? []
                sheetDanger = (note.userInfo?["danger"] as? NSNumber)?.intValue ?? -1
                sheetShown = !sheetItems.isEmpty
            }
            /* 原生选择器：Kotlin 侧点「时辰 / 事类 / 年 / 月 / 日」时推 xlrPickerShow，
               这里弹系统的滚轮 sheet；选完回 xlrPickerPick（取消回 xlrPickerCancel）。 */
            .sheet(isPresented: $pickerCenter.show) {
                NativePickerSheet(c: pickerCenter)
            }
            .onReceive(NotificationCenter.default.publisher(for: Notification.Name("xlrPickerShow"))) { note in
                pickerCenter.present(note.userInfo)
            }
            /* 原生起卦卡：Kotlin 推状态 / 皮肤；这里改完再回推 xlrAskEdit */
            .onReceive(NotificationCenter.default.publisher(for: Notification.Name("xlrAskState"))) { note in
                ask.apply(note.userInfo)
            }
            .onReceive(NotificationCenter.default.publisher(for: Notification.Name("xlrAskSkin"))) { note in
                ask.applySkin(note.userInfo)
            }
            .onReceive(NotificationCenter.default.publisher(for: Notification.Name("xlrAskVis"))) { note in
                askShown = ((note.userInfo?["v"] as? String) == "1")
            }
            .onReceive(NotificationCenter.default.publisher(for: Notification.Name("xlrDrawerData"))) { note in
                if let j = note.userInfo?["json"] as? String { drawer.load(j) }
            }
            .onReceive(NotificationCenter.default.publisher(for: Notification.Name("xlrDrawerOpen"))) { _ in
                drawer.open = true
            }
            .onReceive(NotificationCenter.default.publisher(for: Notification.Name("xlrDrawerDelta"))) { note in
                drawer.live = (note.userInfo?["t"] as? String) ?? ""
            }
            .onReceive(NotificationCenter.default.publisher(for: Notification.Name("xlrPageShift"))) { note in
                if let n = note.userInfo?["x"] as? NSNumber {
                    let px = n.doubleValue
                    if abs(px - shift.px) >= 0.5 { shift.px = px }
                }
            }
            /* 允许键盘的场合（设置页 / 引导 / 皮肤菜单）：Kotlin 直接推，别绕 barHidden ——
               那条要过 TopBarFeed 才到这儿，中间几帧正好把第一次点击的键盘收掉。 */
            .onReceive(NotificationCenter.default.publisher(for: Notification.Name("xlrImeAllow"))) { note in
                imeAllowed = ((note.userInfo?["v"] as? String) == "1")
            }
            /* 抽屉跟手：Kotlin 在手指拖动时逐帧推进度，原生这层照这个值摆 —— 滑多少出多少。
               推 0 = 没到阈值，带弹簧回原位；推 1 = 手势把它带满了。 */
            .onReceive(NotificationCenter.default.publisher(for: Notification.Name("xlrDrawerProg"))) { note in
                let v = (note.userInfo?["v"] as? NSNumber)?.doubleValue ?? 0
                if v <= 0.0001 {
                    withAnimation(.interactiveSpring(response: 0.34, dampingFraction: 0.86)) { drawer.prog = 0 }
                    if drawer.open { drawer.open = false }
                } else if v >= 0.9999 {
                    withAnimation(.interactiveSpring(response: 0.34, dampingFraction: 0.86)) { drawer.prog = 1 }
                } else {
                    drawer.prog = CGFloat(v)      /* 逐帧跟手：不加动画 */
                }
            }
            .onChange(of: askShown) { on in
                /* 离开壹页 / 被遮罩盖住：把输入法收掉（否则回到壹页时它自己又弹出来）。
                   回到壹页时也补一次：起卦完从贰/叁回来，Kuikly 那边会把焦点还给它自己的输入框。 */
                if !on {
                    xlrEndEditing()
                } else {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.30) { xlrEndEditing() }
                }
            }
            /* 详情开合的那一刻记下键盘位移：卡片底边与长高用同一条曲线，键盘收起不再另起一段 */
            .onChange(of: ask.moreOpen) { on in
                withAnimation(.easeInOut(duration: 0.26)) { kbSnap = on ? kbShift : 0 }
            }
            .onReceive(NotificationCenter.default.publisher(for: Notification.Name("xlrThemeChanged"))) { note in
                // Kotlin 侧传的是 0/1，桥过来是 NSNumber
                if let n = note.userInfo?["dark"] as? NSNumber {
                    let dark = n.boolValue
                    if dark != isDarkTheme { isDarkTheme = dark }
                }
            }
            /* 收键盘（Kotlin 侧 Platform.hideIme → 这个通知）。
               ⚠ 为什么放在 Swift 而不是 Kotlin：K/N 的 UIKit 绑定里拿不到干净的
                 resignFirstResponder / endEditing（keyWindow 可能是 nil，windows 遍历会报 unresolved）。
               先走响应链让 first responder（Kuikly 的原生输入框）自己 resign，
               再兜底把每个窗口的编辑都 end 掉 —— 只发通知不 resign 的话键盘不会动。 */
            .onReceive(NotificationCenter.default.publisher(for: Notification.Name("xlrHideIme"))) { _ in
                _ = UIApplication.shared.sendAction(
                    #selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil
                )
                for scene in UIApplication.shared.connectedScenes {
                    guard let ws = scene as? UIWindowScene else { continue }
                    for w in ws.windows { _ = w.endEditing(true) }
                }
            }
    }
}

/// 三页轨道的横向位移（px）。单独一个 model：逐帧只刷新薄壳，不牵动卡片内部。
final class PageShiftModel: ObservableObject {
    @Published var px: Double = 0
}

/// 原生卡的薄壳：卡片内部状态不变时不会重算。
private struct AskCardHost: View {
    @ObservedObject var ask: AskCardModel
    let shift: PageShiftModel
    /// 卡在不在屏幕上：不在就把它里面的输入框锁掉（否则键盘可能挂在看不见的卡上）
    var active: Bool = true

    var body: some View {
        NativeAskCard(m: ask, active: active)
            .modifier(PageShiftOffset(shift: shift))
    }
}

/// 逐帧位移只让这一层重组：content 是**已经建好的卡**，不会每帧重跑卡片的 body。
private struct PageShiftOffset: ViewModifier {
    @ObservedObject var shift: PageShiftModel
    func body(content: Content) -> some View {
        content.offset(x: CGFloat(shift.px) / UIScreen.main.scale)
    }
}
