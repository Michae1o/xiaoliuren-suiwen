import Foundation
import SwiftUI
import UIKit
import UserNotifications

/// Kotlin owns every local date, count and reminder. These types are display-only.
struct RecallLensPayload: Decodable, Identifiable {
    struct Category: Decodable {
        let label: String
        let count: Int
    }
    struct Point: Decodable {
        let day, dayIndex, cluster, clusterIndex, clusterCount, status, statusIndex, statusCount: Int
    }
    struct Season: Decodable {
        let term, tagline: String
        let ink, accent, second, glow: UInt32
    }

    let key, label, start, end: String
    let dayCount, total, noted: Int
    let categories: [Category]
    let trace: [Point]
    let season: Season?
    var id: String { key }
}

struct RecallEnvelope: Decodable {
    enum Kind: String, Decodable { case open, close, reminder, clearReminder }
    let kind: Kind
    let active, key, title, message, source, face: String?
    let lenses: [RecallLensPayload]?
    let offerMs, hintMs: UInt64?

    init?(_ notification: Notification) {
        guard let json = notification.userInfo?["payload"] as? String,
              let decoded = try? JSONDecoder().decode(Self.self, from: Data(json.utf8)) else { return nil }
        self = decoded
    }
}

struct RecallReport: Identifiable {
    let id = UUID()
    let lenses: [RecallLensPayload]
    let selected: String
    let fromAbout: Bool

    init?(_ envelope: RecallEnvelope) {
        guard let lenses = envelope.lenses, !lenses.isEmpty else { return nil }
        self.lenses = lenses
        self.fromAbout = envelope.source == "about"
        self.selected = lenses.first(where: { $0.key == envelope.active })?.key ?? lenses[0].key
    }
}

struct RecallReminderNotice: Identifiable {
    let id, title, message: String
    let face: String?
    let offerNanos, hintNanos: UInt64
    init?(_ envelope: RecallEnvelope) {
        guard let key = envelope.key, let title = envelope.title, let message = envelope.message else { return nil }
        self.id = key
        self.title = title
        self.message = message
        self.face = envelope.face
        self.offerNanos = min(envelope.offerMs ?? 8_000, 60_000) * 1_000_000
        self.hintNanos = min(envelope.hintMs ?? 5_200, 60_000) * 1_000_000
    }
}

enum NativeRecallBridge {
    static let receive = Notification.Name("xlrRecall")
    static let command = Notification.Name("xlrRecallCommand")

    static func send(_ action: String, key: String? = nil, active: Bool? = nil) {
        var data: [String: Any] = ["action": action]
        if let key = key { data["key"] = key }
        if let active = active { data["active"] = NSNumber(value: active) }
        NotificationCenter.default.post(name: command, object: nil, userInfo: data)
    }
}

/// A rolling queue merges Sundays and month ends, with month taking precedence on the same day.
actor RecallSystemSchedule {
    static let shared = RecallSystemSchedule()
    private let center = UNUserNotificationCenter.current()
    private let prefix = "xlr.recall.system."
    private var generation = 0

    func setEnabled(_ enabled: Bool) async {
        generation += 1
        let revision = generation
        let pending = await center.pendingNotificationRequests()
        guard revision == generation else { return }
        let ours = pending.filter { $0.identifier.hasPrefix(prefix) }
        guard enabled else {
            center.removePendingNotificationRequests(withIdentifiers: ours.map(\.identifier))
            return
        }
        let auth = await center.notificationSettings().authorizationStatus
        guard revision == generation, auth == .authorized || auth == .provisional || auth == .ephemeral else { return }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = .current
        let now = Date()
        let otherSlots = pending.count - ours.count
        let capacity = max(0, min(56, 64 - otherSlots))
        var day = cal.startOfDay(for: now)
        var desired: [UNNotificationRequest] = []
        while desired.count < capacity {
            guard let fire = cal.date(bySettingHour: 21, minute: 0, second: 0, of: day),
                  let nextDay = cal.date(byAdding: .day, value: 1, to: day) else { break }
            let monthly = cal.component(.day, from: nextDay) == 1
            if fire > now && (monthly || cal.component(.weekday, from: day) == 1) {
                let parts = cal.dateComponents([.year, .month, .day, .hour, .minute], from: fire)
                let id = "\(prefix)\(parts.year ?? 0)-\(parts.month ?? 0)-\(parts.day ?? 0)"
                let content = UNMutableNotificationContent()
                content.title = monthly ? "本月回观" : "本周回观"
                content.body = "打开遂问，回顾这段时间的卦例。"
                content.sound = .default
                let trigger = UNCalendarNotificationTrigger(dateMatching: parts, repeats: false)
                desired.append(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
            }
            day = nextDay
        }
        let existing = Set(ours.map(\.identifier))
        let wanted = Set(desired.map(\.identifier))
        center.removePendingNotificationRequests(withIdentifiers: Array(existing.subtracting(wanted)))
        // Refresh only missing dates, so resuming at 21:00 doesn't reset a queued notification.
        for request in desired where !existing.contains(request.identifier) {
            guard revision == generation else { return }
            do { try await center.add(request) }
            catch { NSLog("Recall notification scheduling failed: %@", error.localizedDescription) }
            if revision != generation && !UserDefaults.standard.bool(forKey: "xlr.recallSystemNotifications") {
                center.removePendingNotificationRequests(withIdentifiers: [request.identifier])
                return
            }
        }
    }
}

struct RecallSystemControls: ViewModifier {
    static let key = "xlr.recallSystemNotifications"
    /// 「这个功能曾经真的开过」——只有它为真时，启动期才允许去碰 UNUserNotificationCenter。
    /// 侧载（自签）包里那个接口有一条会直接抛异常的老路（bundleProxyForCurrentProcess is nil），
    /// 而默认值是关的，没必要每次冷启动都去踩它。
    static let armedKey = "xlr.recall.system.armed"
    @Binding var enabled: Bool
    @State private var requesting = false
    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content
            .task { await sync() }
            .onChange(of: scenePhase) { phase in
                if phase == .active { Task { await sync() } }
            }
            .onReceive(NotificationCenter.default.publisher(for: Notification.Name("xlrRecallSystemQuery"))
                .receive(on: RunLoop.main)) { _ in
                Task { await sync() }
            }
            .onReceive(NotificationCenter.default.publisher(for: Notification.Name("xlrRecallSystemToggle"))
                .receive(on: RunLoop.main)) { note in
                guard let value = note.userInfo?["enabled"] as? NSNumber else { return }
                Task { await change(value.boolValue) }
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.significantTimeChangeNotification)) { _ in
                if enabled { Task { await sync() } }
            }
    }

    @MainActor
    private func sync() async {
        let armed = UserDefaults.standard.bool(forKey: Self.armedKey)
        guard enabled || armed else {
            // 从没开过 ⇒ 冷启动一次都不碰通知中心
            NativeRecallBridge.send("systemNotificationsState", active: false)
            return
        }
        let status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        let active = enabled && (status == .authorized || status == .provisional || status == .ephemeral)
        NativeRecallBridge.send("systemNotificationsState", active: active)
        await RecallSystemSchedule.shared.setEnabled(active)
        if active {
            UserDefaults.standard.set(true, forKey: Self.armedKey)
        } else if !enabled {
            // 收尾清完再落旗，下次冷启动就能彻底不碰
            UserDefaults.standard.set(false, forKey: Self.armedKey)
        }
    }

    @MainActor
    private func change(_ value: Bool) async {
        guard !requesting else { return }
        if !value {
            enabled = false
            UserDefaults.standard.set(false, forKey: Self.key)
            await sync()
            return
        }
        requesting = true
        defer { requesting = false }
        enabled = true
        UserDefaults.standard.set(true, forKey: Self.key)
        let center = UNUserNotificationCenter.current()
        let status = await center.notificationSettings().authorizationStatus
        if status == .notDetermined {
            _ = try? await center.requestAuthorization(options: [.alert, .sound])
        } else if status == .denied, let url = URL(string: UIApplication.openSettingsURLString) {
            await UIApplication.shared.open(url)
        }
        await sync()
    }
}
