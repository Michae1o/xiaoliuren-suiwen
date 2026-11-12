import SwiftUI

/// SwiftUI ←→ Kuikly 容器的桥。
///
/// ★ 结构要点：SwiftUI 只管一层**空容器**（`XLRHostController`），Kuikly 的 nav VC 是它的子 VC，
///   键盘让位的 transform 只打在**子 VC 的 view** 上。
///   为什么必须这样：上一版直接变换 SwiftUI 管的那个 view，SwiftUI 每次布局都会给它赋 frame，
///   而给带 transform 的 view 赋 frame 会把 transform 的效果「烤」进位置再清掉
///   ⇒ 实机实测表现为「先瞬移 ~350px，再用 430ms 滑回原位，净位移 0」。
///   现在被变换的这层 SwiftUI 永远碰不到，只由我们用 bounds + center 摆位（这两个量与 transform 无关）。
struct KuiklyRenderViewPage: UIViewControllerRepresentable {
    var pageName: String
    var data: Dictionary<String, Any>
    /// 要往上平移多少（pt，正数 = 上移）
    var shiftY: CGFloat = 0
    /// 键盘自己的动画时长 / 曲线（Kotlin 侧从键盘通知里抄过来的）
    var shiftDuration: Double = 0.25
    var shiftCurve: UIView.AnimationOptions = .curveEaseOut

    func makeUIViewController(context: Context) -> XLRHostController {
        let host = XLRHostController()
        xlrHostRef = host
        host.install(XLRRenderViewController(pageName: pageName, pageData: data))
        return host
    }

    func updateUIViewController(_ host: XLRHostController, context: Context) {
        host.applyShift(shiftY, duration: shiftDuration, curve: shiftCurve)
    }
}

/// 只做两件事：把 Kuikly 的 VC 装成子 VC；按需要平移子 VC 的那一层。
final class XLRHostController: UIViewController {

    private var child: UIViewController?
    private var appliedShift: CGFloat = 0
    /// 目标位移（pt）。即使 applyShift 早于 install 到达，也会在 layoutChild 里补上。
    private var wantShift: CGFloat = 0

    func install(_ page: UIViewController) {
        addChild(page)
        view.addSubview(page.view)
        page.didMove(toParent: self)
        child = page
        layoutChild()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        layoutChild()
    }

    /// 只用 bounds + center 摆位 —— 带 transform 的 view 不能碰 frame。
    /// transform 在这里**每次布局都校准一次**：即使某次 applyShift 因为 child 还没装好而被丢掉，
    /// 下一次布局也会把位移补上（r50 的 ap0 就是这么来的：值到了，但那一刻没东西可移动）。
    private func layoutChild() {
        guard let v = child?.view else { return }
        let size = view.bounds.size
        guard size.width > 1, size.height > 1 else { return }
        v.bounds = CGRect(origin: .zero, size: size)
        v.center = CGPoint(x: size.width / 2, y: size.height / 2)
    }

    /// 整页上移：CALayer 属性，一次提交后由渲染服务器插值，主线程忙也不掉帧。
    func applyShift(_ shift: CGFloat, duration: Double, curve: UIView.AnimationOptions) {
        /* ⛔ 位移改在 Kotlin 侧做（RootPage 的 translationY）：
           r52 实测读数 ap270、transform 也确实设到了这个 view 上，但 Kuikly 的渲染层不跟它走，像素没动。
           这里只保留读数，不再变换（否则一旦两边都生效就成双倍位移）。 */
        wantShift = shift
        if true {
            appliedShift = shift
            xlrLastApplied = shift
            return
        }
        guard let v = child?.view else {
            appliedShift = shift
            xlrLastApplied = shift
            return
        }
        guard abs(shift - appliedShift) > 0.5 else { return }
        appliedShift = shift
        xlrLastApplied = shift
        UIView.animate(
            withDuration: max(duration, 0.05),
            delay: 0,
            options: [curve, .beginFromCurrentState, .allowUserInteraction]
        ) {
            v.transform = CGAffineTransform(translationX: 0, y: -shift)
        }
    }
}

/* ---- 临时排查用（这一版排查键盘让位，下一版删） ---- */
var xlrLastKbHeight: CGFloat = 0
var xlrLastShift: CGFloat = 0
var xlrLastApplied: CGFloat = 0
/// ContentView 收到 xlrIme 时直接打这个引用 —— 不依赖 SwiftUI 是否触发 updateUIViewController。
/// （r50 实测读数 kb329 sh270 **ap0**：高度和位移都算对了，但 transform 一次都没落地。）
var xlrHostRef: XLRHostController?
