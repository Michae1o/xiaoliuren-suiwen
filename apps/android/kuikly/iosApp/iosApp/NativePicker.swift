import SwiftUI

/// 原生选择器（起卦时辰 / 事类 / 年 / 月 / 日）。
///
///   ① 自绘弹层是**一次性把所有选项铺开**的，不是可滚动的系统控件；
///   ② 观感不是 iOS 原生选择器；
///   ③ 它是画在页面里的一层，点它的空白处会漏到后面那颗按钮上。
/// 这里用系统 `Picker(...).pickerStyle(.wheel)`（底下就是 UIPickerView）+ sheet：
/// 模态（吃掉全部点击）、天然可滚动、动效跟系统一致。
///
/// 桥：
///   Kotlin → 这里：`xlrPickerShow`（kind / title / values / labels / selected，数组用 \u{1} 拼串）
///   这里 → Kotlin：`xlrPickerPick`（value，点了「完成」）／`xlrPickerCancel`（取消或下滑关掉）
struct NativePickerSheet: View {
    @ObservedObject var c: PickerCenter

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button("取消") { c.finish(cancelled: true) }
                Spacer()
                Text(c.title).font(.headline)
                Spacer()
                Button("完成") { c.finish(cancelled: false) }
                /* ⛔ 别写 .fontWeight(...)：它要 iOS 16+，本工程 deployment target 是 15（r58 就是被它编译失败的）。
                   想加粗用 Text 包一层 .bold()。 */
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)

            Divider()

            Picker("", selection: $c.index) {
                ForEach(Array(c.labels.enumerated()), id: \.offset) { i, s in
                    Text(s).tag(i)
                }
            }
            .pickerStyle(.wheel)
            .labelsHidden()
            .frame(maxWidth: .infinity)
            .frame(height: 216)

            Spacer(minLength: 8)
        }
        .modifier(SheetChrome())
        .onDisappear {
            /* 下滑关掉也要告诉 Kotlin：不然 Session.picker 一直挂着，再点同一颗就没反应 */
            c.finish(cancelled: true)
        }
    }
}

/// iOS 16+ 才有 detents / 拖动指示；deployment target 是 15，按版本套。
private struct SheetChrome: ViewModifier {
    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 16.0, *) {
            content
                .presentationDetents([.height(300)])
                .presentationDragIndicator(.visible)
        } else {
            content
        }
    }
}

final class PickerCenter: ObservableObject {
    static let shared = PickerCenter()

    @Published var show = false
    @Published var index = 0
    @Published var title = ""
    @Published var labels: [String] = []

    private var values: [String] = []
    /// 已经「结束」过就不再重复回传（下滑关掉时 onDisappear 会再走一遍 finish）
    private var pending = false
    /// 原生卡直接用回调拿结果；不设时才走「发通知给 Kotlin」那条老路（Kotlin 自绘弹层的路径）
    private var onPick: ((String?) -> Void)?

    func present(_ info: [AnyHashable: Any]?, onPick: ((String?) -> Void)? = nil) {
        guard let info else { return }
        self.onPick = onPick
        title = (info["title"] as? String) ?? ""
        labels = split(info["labels"])
        values = split(info["values"])
        let sel = (info["selected"] as? String) ?? ""
        index = values.firstIndex(of: sel) ?? 0
        pending = true
        show = true
    }

    func finish(cancelled: Bool) {
        guard pending else { return }
        pending = false
        show = false
        let cb = onPick
        onPick = nil
        if cancelled {
            if let cb = cb { cb(nil) }
            else { NotificationCenter.default.post(name: Notification.Name("xlrPickerCancel"), object: nil) }
        } else {
            let v = (index >= 0 && index < values.count) ? values[index] : ""
            if let cb = cb {
                cb(v)
            } else {
                NotificationCenter.default.post(
                    name: Notification.Name("xlrPickerPick"),
                    object: nil,
                    userInfo: ["value": v]
                )
            }
        }
    }

    private func split(_ any: Any?) -> [String] {
        guard let s = any as? String, !s.isEmpty else { return [] }
        return s.components(separatedBy: "\u{1}")
    }
}
