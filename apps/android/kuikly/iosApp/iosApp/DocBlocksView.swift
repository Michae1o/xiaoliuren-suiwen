import SwiftUI
import UIKit

/// 断辞正文排版 —— 逐条照 Android `DocView.kt` 的 `docBlocks` / `DocBlockView`

///
/// 规则：
///   · 空行           ⇒ 段距 7
///   · 【xxx】余下     ⇒ 小标题（13/21 · 500 · 墨色，上 14 下 4）
///   · 首块正文：前面没有小标题且 bigFirst ⇒ kind 1（本机速断：判词 30/42 衬线判定色）
///               否则                    ⇒ kind 2（模型结论：判词同样 30；
///                                          整句不是判词时 ≤18 字用 30/37、更长用 22/33）
///   · 其余           ⇒ 普通正文 13.5/26
///   判词写法 `**判词**（括注）余下` ⇒ 判词判定色、括注 17/28 降灰、余下照常。
///
/// ⚠ 之前这里是 `Text(r.duanci)` 直接贴原串，所以 `**暴雨**` 这种 markdown 标记原样露出来
struct DocBlocksView: View {
    @ObservedObject var m: AskCardModel
    let raw: String
    /// 首块要不要立成大字：本机速断 true、模型断辞 false
    var bigFirst: Bool = true

    private enum Blk {
        case h(String)
        case p(String, Int)     // kind: 0 普通 / 1 速断大字 / 2 模型结论块
        case gap
    }

    private static let reHead = try! NSRegularExpression(pattern: "^【(.+?)】\\s*(.*)$")
    private static let reVerdict = try! NSRegularExpression(
        pattern: "^(\\*\\*)?([^（）()]{1,16})（([^（）]{0,44})）(\\*\\*)?(.*)$"
    )

    private static func stripBold(_ s: String) -> String {
        s.replacingOccurrences(of: "\\*\\*(.+?)\\*\\*", with: "$1", options: .regularExpression)
    }

    /// 按 Android 那三条正则切块
    private static func blocks(_ text: String, bigFirst: Bool) -> [Blk] {
        var out: [Blk] = []
        var headSeen = false
        var bodySeen = false
        for line in text.components(separatedBy: "\n") {
            let s = line.trimmingCharacters(in: .whitespaces)
            if s.isEmpty { out.append(.gap); continue }
            let ns = s as NSString
            if let mm = Self.reHead.firstMatch(in: s, range: NSRange(location: 0, length: ns.length)) {
                let head = ns.substring(with: mm.range(at: 1))
                out.append(.h("【" + head + "】"))
                headSeen = true
                let rest = ns.substring(with: mm.range(at: 2)).trimmingCharacters(in: .whitespaces)
                if !rest.isEmpty {
                    out.append(.p(rest, bodySeen ? 0 : 2))
                    bodySeen = true
                }
                continue
            }
            let kind: Int
            if bodySeen { kind = 0 }
            else if headSeen { kind = 2 }
            else if bigFirst { kind = 1 }
            else { kind = 2 }
            out.append(.p(s, kind))
            bodySeen = true
        }
        return out
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(Self.blocks(raw, bigFirst: bigFirst).enumerated()), id: \.offset) { _, b in
                blockView(b)
            }
        }
    }

    @ViewBuilder
    private func blockView(_ b: Blk) -> some View {
        switch b {
        case .gap:
            Spacer().frame(height: 7)

        case .h(let t):
            Text(Self.stripBold(t))
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(m.ink)
                .padding(.top, 14)
                .padding(.bottom, 4)

        case .p(let t, let kind):
            let s = Self.stripBold(t)
            if kind == 1 || kind == 2, let v = Self.verdict(s) {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(alignment: .bottom, spacing: 2) {
                        Text(v.lead)
                            .font(.system(size: 30, weight: .medium, design: .serif))
                            .kerning(1.0)
                            .foregroundColor(m.jyc)
                        Text("（" + v.note + "）")
                            .font(.system(size: 17))
                            .foregroundColor(m.dim2)
                            .padding(.bottom, 6)
                    }
                    if !v.tail.isEmpty {
                        rich(v.tail, size: 13.5, lineHeight: 26, color: m.dim)
                            .padding(.top, 2)
                    }
                }
                .fixedSize(horizontal: false, vertical: true)

            } else if kind == 1 {
                Text(s)
                    .font(.system(size: 30, weight: .medium, design: .serif))
                    .kerning(1.0)
                    .foregroundColor(m.jyc)
                    .fixedSize(horizontal: false, vertical: true)

            } else if kind == 2 {
                let big = s.count <= 18
                Text(s)
                    .font(.system(size: big ? 30 : 22, weight: .medium, design: .serif))
                    .kerning(1.0)
                    .foregroundColor(m.jyc)
                    .fixedSize(horizontal: false, vertical: true)

            } else {
                rich(s, size: 13.5, lineHeight: 26, color: m.dim)
            }
        }
    }

    /// 正文：把 `**…**` 收成真粗体（不再把星号露出来）
    /// ⚠ 返回 `some View` 不是 `Text`：`.lineSpacing` / `.foregroundColor` 是 View 修饰符，
    ///   链完就不是 Text 了（CI 上就是这样报的：cannot convert 'some View' to 'Text'）。
    private func rich(_ s: String, size: CGFloat, lineHeight: CGFloat, color: Color) -> some View {
        var attr: AttributedString
        if let a = try? AttributedString(
            markdown: s,
            options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)
        ) {
            attr = a
        } else {
            attr = AttributedString(s)
        }
        return Text(attr)
            .font(.system(size: size))
            .lineSpacing(max(0, lineHeight - UIFont.systemFont(ofSize: size).lineHeight))
            .foregroundColor(color)
    }

    private struct Verdict {
        let lead: String, note: String, tail: String
    }

    private static func verdict(_ s: String) -> Verdict? {
        let ns = s as NSString
        guard let mm = Self.reVerdict.firstMatch(in: s, range: NSRange(location: 0, length: ns.length)) else {
            return nil
        }
        return Verdict(lead: Self.stripBold(ns.substring(with: mm.range(at: 2))),
                       note: ns.substring(with: mm.range(at: 3)),
                       tail: Self.stripBold(ns.substring(with: mm.range(at: 5))))
    }
}
