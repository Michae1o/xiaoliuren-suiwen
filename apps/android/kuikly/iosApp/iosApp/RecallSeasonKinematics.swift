import Foundation

/// Decorative mobile Recall layers; names match the native Kotlin bridge payload.
enum RecallSeasonArt: String, CaseIterable {
    case springStarts = "立春", rain = "雨水", thunder = "惊蛰"
    case springEquinox = "春分", clearing = "清明", grainRain = "谷雨"
    case summerStarts = "立夏", grainFull = "小满", grainAwn = "芒种"
    case summerSolstice = "夏至", lotus = "小暑", heat = "大暑"
    case autumnStarts = "立秋", cloudbreak = "处暑", whiteDew = "白露"
    case autumnEquinox = "秋分", coldDew = "寒露", frost = "霜降"
    case winterStarts = "立冬", firstSnow = "小雪", deepSnow = "大雪"
    case winterSolstice = "冬至", icicles = "小寒", deepIce = "大寒"

    init?(_ term: String?) {
        guard let term else { return nil }
        self.init(rawValue: term)
    }

    /// The native opening runs on the accessibility-aware presentation clock.
    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    static func ease(_ value: Double) -> Double {
        let t = clamp(value)
        return t * t * (3 - 2 * t)
    }

    static func duskArrival(_ elapsed: Double, reduced: Bool) -> Double {
        reduced ? 1 : ease(elapsed / 3.05)
    }
}

/// Wall-clock-free, bounded number of decorative water tracks. Carries no
/// user data. Every track is evaluated analytically in screen coordinates, so
/// viewport resizing does not relaunch the effect or allocate a simulation
/// buffer. Fall-through and adhering drops are separate trajectories.
struct RecallRainTrack {
    let x, door, velocity, wind, radius, wait, period, linger, slip, delay, traceLife: Double
    let hasImpact: Bool
    var quiet: Double { x > 0.31 && x < 0.73 ? 0.66 : 1 }

    private static func pick(_ seed: Int, _ channel: Int) -> Double {
        // All operations below are exact within 32 bits on every supported iPhone.
        var v = UInt32(seed &* 7919 &+ channel &* 104729 &+ 2089)
        v ^= v >> 16; v = v &* 0x7feb352d
        v ^= v >> 15; v = v &* 0x846ca68b
        v ^= v >> 16
        return Double(v) / Double(UInt32.max)
    }

    static let pane: [RecallRainTrack] = (0..<18).map { index in
        func r(_ channel: Int) -> Double { RecallRainTrack.pick(index, channel) }
        // Jittered bins make a drizzle across the entire pane, not a fixed
        // right-hand lane. There are at most five simultaneous lens-bead tracks.
        let x = index == 0 ? 0.083 : (0.075 + 0.895 * (Double(index) + r(1)) / 18)
        let impact = [1, 5, 8, 12, 16].contains(index)
        let slips = impact && (index == 5 || index == 12 || index == 16)
        return RecallRainTrack(x: x, door: impact ? 0.22 + r(2) * 0.52 : 1.08,
                               velocity: 295 + 265 * r(3), wind: -17 + 23 * r(4),
                               radius: 0.85 + 1.07 * r(5), wait: 0.07 + Double((index * 7) % 18) * 0.16,
                               period: 6.7 + 1.9 * r(6), linger: 1.7 + 2.35 * r(7),
                               slip: slips ? 21 + 22 * r(8) : 0, delay: 0.33 + 0.77 * r(9),
                               traceLife: 1.25 + 0.8 * r(10), hasImpact: impact)
    }

    func pose(elapsed: Double, width: Double, height: Double) -> RecallRainPose? {
        guard width > 0, height > 0, elapsed.isFinite, elapsed >= wait else { return nil }
        let t = (elapsed - wait).truncatingRemainder(dividingBy: period)
        let travel = door * height + 12
        let hit = (sqrt(velocity * velocity + 156 * travel) - velocity) / 78
        if t < hit {
            let y = -12 + velocity * t + 39 * t * t
            return y < 0 ? nil : .fall(x * width + wind * t, y, radius, wind, quiet)
        }
        let age = t - hit
        guard hasImpact, age <= linger + traceLife else { return nil }
        let anchorX = min(width * 0.978, max(width * 0.063, x * width + wind * hit))
        let anchorY = travel - 12
        let drag = RecallSeasonArt.ease((min(age, linger) - delay) / 1.03)
        let dy = drag * slip
        let dx = drag * (wind / 10)
        let appear = RecallSeasonArt.clamp(age / 0.095)
        let fade = RecallSeasonArt.clamp((linger - age) / 0.66)
        let mark = dy > 1.7 ? RecallSeasonArt.clamp(1 - max(0, age - linger) / traceLife) : 0
        return .lens(anchorX, anchorY, dx, dy, min(6.8, max(3.7, radius * 2.85 + 1.15)),
                     quiet * appear * fade, quiet * mark * mark, age)
    }
}

enum RecallRainPose {
    case fall(Double, Double, Double, Double, Double)
    // Actual impact point, changing displacement, bead size, bead alpha,
    // separate trailing-water alpha, elapsed since impact.
    case lens(Double, Double, Double, Double, Double, Double, Double, Double)
}

struct RecallSeasonClock {
    private var accumulated = 0.0
    private var activeStart: Double?

    mutating func restart(at now: Double, active: Bool) {
        accumulated = 0
        activeStart = active ? now : nil
    }

    mutating func setActive(_ active: Bool, at now: Double) {
        if active {
            if activeStart == nil { activeStart = now }
        } else if let start = activeStart {
            accumulated += max(0, now - start)
            activeStart = nil
        }
    }

    func elapsed(at now: Double) -> Double { accumulated + (activeStart.map { max(0, now - $0) } ?? 0) }
}
