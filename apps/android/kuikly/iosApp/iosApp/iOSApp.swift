import SwiftUI
import shared

@main
struct iOSApp: App {

    init() {
        // 把宿主的「存储 / 网络 / 触感 / 剪贴板 / 导出」接到共享层的 Platform 上。
        // 对应安卓 MainActivity 里那一段赋值；不接的话共享层会静默降级（能显示，但存不了、连不上模型）。
        PlatformIos.shared.install()
        PlatformIos.shared.setHourCycle { Self.detectHourCycle() }
    }

    private static func detectHourCycle() -> String {
        let pat = DateFormatter.dateFormat(fromTemplate: "j", options: 0, locale: .current) ?? ""
        var inQuote = false
        for ch in pat {
            if ch == "'" {
                inQuote.toggle()
                continue
            }
            if inQuote { continue }
            switch ch {
            case "k": return "h24"
            case "H": return "h23"
            case "h": return "h12"
            case "K": return "h11"
            default: break
            }
        }
        return "h23"
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
            /* ⛔ 这里不要再 .ignoresSafeArea()：它默认忽略**所有**安全区，包括键盘，
               结果是键盘弹出来时容器一点不变矮、输入卡原地不动（安卓是 adjustResize，
               窗口真变矮所以会顶起来）。让 ContentView 自己按需忽略 container 区。 */
        }
    }
}
