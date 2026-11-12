import SwiftUI
import UIKit

/// 原生输入框（UITextView 包一层）。
///
/// 为什么不用 SwiftUI 的 `TextEditor`：
///   ① 它没有「无背景」的开关（`.scrollContentBackground` 是 iOS 16 才有的，本工程最低 15）；
///   ② 排版参数（行高/内边距）压不干净，占位字和正文对不齐 —— 而这张卡要跟 Kotlin 那版逐像素对。
///   UITextView 能直接给 `textContainerInset = .zero`、`lineFragmentPadding = 0`，
///   行高用 NSParagraphStyle 固定，跟 Kotlin 的 `lineHeight` 完全一致。
struct GrowingTextView: UIViewRepresentable {

    @Binding var text: String
    var fontSize: CGFloat
    var lineHeight: CGFloat
    var color: UIColor
    var maxLines: Int
    /// 文本高度变了（pt）——父级用它撑卡
    var onHeight: (CGFloat) -> Void
    /// 回车键（键盘右下角「发送」）按下时回调：起卦用它
    var onSubmit: (() -> Void)? = nil
    /// 键盘上方加一条「完成」工具条（抽屉里的输入框用：点了能收键盘）
    var showsDoneBar: Bool = false
    /// 开始/结束编辑（记录详情用它决定：写「实际情况」的时候要把底下那根接着问栏收起来）
    var onFocus: ((Bool) -> Void)? = nil
    /// 能不能输入。卡不在壹页时置 false ⇒ 顺手 resign，键盘不可能再挂在这张卡上
    var editable: Bool = true

    func makeUIView(context: Context) -> UITextView {
        let tv = UITextView()
        tv.backgroundColor = .clear
        tv.textContainerInset = .zero
        tv.textContainer.lineFragmentPadding = 0
        tv.isScrollEnabled = true
        tv.showsVerticalScrollIndicator = false
        tv.autocorrectionType = .no
        tv.spellCheckingType = .no
        tv.returnKeyType = .send
        tv.delegate = context.coordinator
        tv.setContentHuggingPriority(.defaultLow, for: .vertical)
        if showsDoneBar {
            let bar = UIToolbar(frame: CGRect(x: 0, y: 0, width: 0, height: 44))
            bar.items = [
                UIBarButtonItem(barButtonSystemItem: .flexibleSpace, target: nil, action: nil),
                UIBarButtonItem(title: "完成", style: .done,
                                target: context.coordinator, action: #selector(Coordinator.done)),
            ]
            bar.sizeToFit()
            tv.inputAccessoryView = bar
        }
        return tv
    }

    func updateUIView(_ tv: UITextView, context: Context) {
        context.coordinator.parent = self
        /* 卡不在壹页：先把焦点交出去再禁编辑 —— 顺序反过来的话 resign 会被 isEditable 挡掉，
           键盘就会挂在一个看不见的输入框上。 */
        if !editable {
            if tv.isFirstResponder { tv.resignFirstResponder() }
            if tv.isEditable { tv.isEditable = false }
        } else if !tv.isEditable {
            tv.isEditable = true
        }
        if tv.text != text { tv.text = text }
        let f = UIFont.systemFont(ofSize: fontSize)
        if tv.font != f { tv.font = f }
        if tv.textColor != color { tv.textColor = color }
        tv.typingAttributes = attrs()
        /* 高度：按宽度量一次，最多 maxLines 行 */
        let w = tv.bounds.width
        if w > 1 {
            let h = Self.measure(text.isEmpty ? " " : text, width: w, size: fontSize,
                                 lineHeight: lineHeight, maxLines: maxLines)
            DispatchQueue.main.async { self.onHeight(h) }
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    private func attrs() -> [NSAttributedString.Key: Any] {
        let ps = NSMutableParagraphStyle()
        ps.minimumLineHeight = lineHeight
        ps.maximumLineHeight = lineHeight
        return [
            .font: UIFont.systemFont(ofSize: fontSize),
            .foregroundColor: color,
            .paragraphStyle: ps,
        ]
    }

    static func measure(_ s: String, width: CGFloat, size: CGFloat, lineHeight: CGFloat, maxLines: Int) -> CGFloat {
        let f = UIFont.systemFont(ofSize: size)
        let r = (s as NSString).boundingRect(
            with: CGSize(width: width, height: .greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            attributes: [.font: f],
            context: nil
        )
        let lines = min(CGFloat(maxLines), max(1, ceil(r.height / lineHeight)))
        return lines * lineHeight
    }

    final class Coordinator: NSObject, UITextViewDelegate {
        var parent: GrowingTextView
        init(_ p: GrowingTextView) { parent = p }
        func textView(_ tv: UITextView, shouldChangeTextIn range: NSRange,
                      replacementText text: String) -> Bool {
            if text == "\n" {
                parent.onSubmit?()
                return false
            }
            return true
        }
        @objc func done() {
            UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder),
                                            to: nil, from: nil, for: nil)
        }
        func textViewDidBeginEditing(_ tv: UITextView) {
            parent.onFocus?(true)
        }

        func textViewDidEndEditing(_ tv: UITextView) {
            parent.onFocus?(false)
        }

        func textViewDidChange(_ tv: UITextView) {
            parent.text = tv.text
            let w = tv.bounds.width
            if w > 1 {
                parent.onHeight(Self.parentMeasure(parent, tv.text, w))
            }
        }
        private static func parentMeasure(_ p: GrowingTextView, _ t: String, _ w: CGFloat) -> CGFloat {
            GrowingTextView.measure(t.isEmpty ? " " : t, width: w, size: p.fontSize,
                                    lineHeight: p.lineHeight, maxLines: p.maxLines)
        }
    }
}
