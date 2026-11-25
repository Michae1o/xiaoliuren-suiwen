import Foundation

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
    let active, key, title, message, source: String?
    let lenses: [RecallLensPayload]?

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
    init?(_ envelope: RecallEnvelope) {
        guard let key = envelope.key, let title = envelope.title, let message = envelope.message else { return nil }
        self.id = key
        self.title = title
        self.message = message
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
