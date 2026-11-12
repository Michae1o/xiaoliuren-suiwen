import SwiftUI
import shared

@main
struct iOSApp: App {

    init() {
        // 把宿主的「存储 / 网络 / 触感 / 剪贴板 / 导出」接到共享层的 Platform 上。
        // 对应安卓 MainActivity 里那一段赋值；不接的话共享层会静默降级（能显示，但存不了、连不上模型）。
        PlatformIos.shared.install()
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
