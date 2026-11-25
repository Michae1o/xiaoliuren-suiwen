import SwiftUI
import UIKit

struct RecallColors {
    let night, accent, second, glow: Color
    static let paper = Color(red: 244.0/255, green: 241.0/255, blue: 232.0/255)

    init(_ season: RecallLensPayload.Season?) {
        let autumn = season?.term == "秋分"
        // The pale one-surface palette stays legible on both broad fields.
        night = Self.rgb(autumn ? 0xFF101B2A : (season?.ink ?? 0xFF101923))
        accent = Self.rgb(autumn ? 0xFFF8ECD6 : (season?.accent ?? 0xFFF1D7A8))
        second = Self.rgb(autumn ? 0xFFEDF2F2 : (season?.second ?? 0xFF9DB6C9))
        glow = Self.rgb(season?.glow ?? 0xFF3D617D)
    }

    private static func rgb(_ argb: UInt32) -> Color {
        Color(red: Double((argb >> 16) & 255) / 255,
              green: Double((argb >> 8) & 255) / 255,
              blue: Double(argb & 255) / 255, opacity: Double(argb >> 24) / 255)
    }
}

/// Reads the same unchanged data and CC BY-SA 4.0 attribution shipped with Android by XcodeGen's
/// Assets folder reference: huiguan-atlas/README.md + song-1052.json. No network and no live-sky claim.
private struct RecallAtlas: Decodable {
    struct Star: Decodable { let x, y, mag: Double }
    struct Group: Decodable { let name: String; let lines: [[Int]] }
    let stars: [String: Star]
    let mansions, enclosures: [Group]

    struct Chart {
        let points: [Int: Star]
        let edges: [[(Int, Int)]]
        let driftOwner: [Int: Int]
        let glints: [Int]

        static let bundled: Chart = {
            guard let url = Bundle.main.url(forResource: "song-1052", withExtension: "json",
                                             subdirectory: "Assets/huiguan-atlas"),
                  let data = try? Data(contentsOf: url),
                  let atlas = try? JSONDecoder().decode(RecallAtlas.self, from: data)
            else { return Chart(points: [:], edges: [[], [], []], driftOwner: [:], glints: []) }

            let points = Dictionary(uniqueKeysWithValues: atlas.stars.compactMap { item in
                Int(item.key).map { ($0, item.value) }
            })
            var edges: [[(Int, Int)]] = [[], [], []]
            var owner: [Int: Int] = [:]
            for (index, group) in (atlas.mansions + atlas.enclosures).enumerated() {
                let rank = group.name == "北斗" ? 2 : (index < atlas.mansions.count ? 0 : 1)
                let seed = group.lines.first?.first
                for line in group.lines {
                    for (a, b) in zip(line, line.dropFirst()) {
                        guard points[a] != nil, points[b] != nil else { continue }
                        edges[rank].append((a, b))
                        if let seed = seed {
                            // A shared star always uses the same position at every endpoint.
                            if owner[a] == nil { owner[a] = seed }
                            if owner[b] == nil { owner[b] = seed }
                        }
                    }
                }
            }
            let glints = Array(points.sorted {
                $0.value.mag == $1.value.mag ? $0.key < $1.key : $0.value.mag < $1.value.mag
            }.prefix(22).map { $0.key })
            return Chart(points: points, edges: edges, driftOwner: owner, glints: glints)
        }()
    }
}

struct NativeRecallSky: View {
    let position: Double
    let seconds: Double
    let lens: RecallLensPayload
    let colors: RecallColors
    let season: RecallSeasonArt?
    let seasonElapsed: Double
    let reduced: Bool
    @Environment(\.displayScale) private var displayScale

    // From the Android atlas: perspective shifts/rotates between three reads, not three different star fields.
    private static let cameras: [(x: Double, y: Double, scale: Double, angle: Double)] = [
        (0.69, 0.47, 0.59, -0.11), (0.18, 0.27, 0.70, 0.27), (0.80, 0.57, 0.81, 0.58)
    ]
    private static let haloSprite: UIImage? = {
        guard let url = Bundle.main.url(forResource: "smooth-halo", withExtension: "png",
                                         subdirectory: "Assets/common/recall") else { return nil }
        return UIImage(contentsOfFile: url.path)
    }()
    private static let clusters: [(Double, Double)] = [
        (0.18, 0.46), (0.40, 0.48), (0.13, 0.58), (0.32, 0.61), (0.12, 0.70),
        (0.40, 0.72), (0.22, 0.78), (0.44, 0.81), (0.30, 0.88)
    ]

    private struct SkyFrame {
        let index: Int
        let fraction, x, y, scale, angle: Double
        var origin: CGPoint { CGPoint(x: x, y: y) }
    }

    var body: some View {
        Group {
            if season == .autumnEquinox {
                // The soft, full-bleed autumn fields replace only this season's
                // ambient wash. The original atlas is rendered just once.
                skyCanvas(includeAmbient: false)
            } else if #available(iOS 17.0, *), let sprite = Self.haloSprite {
                GeometryReader { geometry in
                    ZStack {
                        shadedAmbient(in: geometry.size, sprite: sprite)
                        skyCanvas(includeAmbient: false)
                    }
                }
            } else {
                // Preserve the previous Canvas on iOS 15–16 or if the optional mask is absent.
                skyCanvas(includeAmbient: true)
            }
        }
        .accessibilityHidden(true)
        .allowsHitTesting(false)
    }

    private func skyFrame(in size: CGSize) -> SkyFrame {
        let w = Double(size.width), h = Double(size.height), span = min(w, h)
        let index = min(1, max(0, Int(position)))
        let fraction = min(1, max(0, position - Double(index)))
        let a = Self.cameras[index], b = Self.cameras[index + 1]
        return SkyFrame(index: index, fraction: fraction,
                        x: mix(a.x, b.x, fraction) * w, y: mix(a.y, b.y, fraction) * h,
                        scale: mix(a.scale, b.scale, fraction) * span, angle: mix(a.angle, b.angle, fraction))
    }

    private func firstWash(_ frame: SkyFrame) -> CGPoint {
        CGPoint(x: frame.x + 15 * wave(37, 0.3), y: frame.y + 12 * wave(41, 0.61))
    }

    private func secondWash(_ frame: SkyFrame) -> CGPoint {
        CGPoint(x: frame.x - frame.scale * 0.45, y: frame.y + frame.scale * 0.6)
    }

    @available(iOS 17.0, *)
    private func shadedAmbient(in size: CGSize, sprite: UIImage) -> some View {
        let frame = skyFrame(in: size)
        var shader = ShaderLibrary.default.xlrRecallAmbient(
            .image(Image(uiImage: sprite)),
            .color(colors.night), .color(colors.glow), .color(colors.second),
            .float2(firstWash(frame)), .float2(secondWash(frame)),
            .float(frame.scale * 1.7), .float(frame.scale * 1.35),
            .float(displayScale)
        )
        // The shader already performs one bounded, stationary stochastic rounding pass.
        shader.dithersColor = false
        return Rectangle().fill(shader)
    }

    private func skyCanvas(includeAmbient: Bool) -> some View {
        Canvas(rendersAsynchronously: false) { context, size in
            let w = Double(size.width), h = Double(size.height)
            if min(w, h) <= 1 { return }
            let frame = skyFrame(in: size)
            if includeAmbient {
                context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(colors.night))
                glow(context, at: firstWash(frame), radius: frame.scale * 1.7,
                     color: colors.glow, opacity: 0.33, atmospheric: true)
                glow(context, at: secondWash(frame), radius: frame.scale * 1.35,
                     color: colors.second, opacity: 0.14, atmospheric: true)
            }
            let rain = season == .rain ? RecallSeasonLayer.rainFrame(
                w: w, h: h, elapsed: seasonElapsed, reduced: reduced) : RecallSeasonLayer.RainFrame()
            if let season, season != .rain {
                RecallSeasonLayer.draw(context, size: size, art: season, elapsed: seasonElapsed,
                                       reduced: reduced, position: position)
            }
            drawChart(context, origin: frame.origin, scale: frame.scale, angle: frame.angle, width: w, height: h, rain: rain)
            if season == .rain { RecallSeasonLayer.drawRain(context, frame: rain, position: position) }
            drawRecords(context, index: frame.index, fraction: frame.fraction, width: w, height: h)
        }
    }

    private func drawChart(_ context: GraphicsContext, origin: CGPoint, scale: Double, angle: Double,
                           width: Double, height: Double, rain: RecallSeasonLayer.RainFrame) {
        let chart = RecallAtlas.Chart.bundled
        let c = cos(angle), s = sin(angle)
        let dx = 5.5 * wave(23), dy = 3.5 * wave(29, 0.12)
        var mapped: [Int: CGPoint] = [:]
        mapped.reserveCapacity(chart.points.count)
        var drifts: [Int: (Double, Double)] = [:]
        var stars = Array(repeating: Path(), count: 6)
        for (hip, star) in chart.points {
            let local: (Double, Double)
            if let seed = chart.driftOwner[hip] {
                if let offset = drifts[seed] {
                    local = offset
                } else {
                    let offset = (4.6 * pulse(seed, 17, 11), 3.6 * pulse(seed + 103, 18, 13))
                    drifts[seed] = offset
                    local = offset
                }
            } else {
                local = (0, 0)
            }
            let p = CGPoint(x: Double(origin.x) + (star.x * c - star.y * s) * scale + dx + local.0,
                            y: Double(origin.y) + (star.x * s + star.y * c) * scale + dy + local.1)
            mapped[hip] = p
            if p.x < -8 || Double(p.x) > width + 8 || p.y < -8 || Double(p.y) > height + 8 { continue }
            let luminosity = max(0.1, min(1, (5.7 - star.mag) / 6.2))
            let bucket = min(5, Int(luminosity * 6))
            stars[bucket].addEllipse(in: disc(rain.refract(p), radius: 0.48 + luminosity * 0.89))
        }
        let opacities: [Double] = [0.25, 0.34, 0.70]
        for (rank, edges) in chart.edges.enumerated() {
            var path = Path()
            for (a, b) in edges {
                guard let first = mapped[a], let second = mapped[b] else { continue }
                rain.appendLine(to: &path, a: first, b: second)
            }
            context.stroke(path, with: .color((rank == 0 ? colors.second : colors.accent).opacity(opacities[rank])),
                           style: StrokeStyle(lineWidth: rank == 2 ? 1 : 0.62, lineCap: .round))
        }
        for (index, path) in stars.enumerated() {
            context.fill(path, with: .color(colors.second.opacity(0.26 + Double(index) * 0.09)))
        }
        for id in chart.glints {
            guard let p = mapped[id], p.x >= -10, Double(p.x) <= width + 10, p.y >= -10, Double(p.y) <= height + 10 else { continue }
            let w = 0.5 + 0.5 * pulse(id, 5.4, 5.9)
            let flare = w * w * w
            glow(context, at: rain.refract(p), radius: 3.4 + flare * 4.3, color: colors.accent, opacity: 0.18 + flare * 0.62)
        }
    }

    private func drawRecords(_ context: GraphicsContext, index: Int, fraction: Double, width w: Double, height h: Double) {
        let dayCenter = CGPoint(x: w * 0.49, y: h * 0.71)
        let dayRadius = min(w * 0.30, h * 0.19)
        let span = min(w, h)
        let final = max(0, position - 1)
        var bright = Path(), pending = Path(), rims = Path(), halos = Path(), pendingHalos = Path()
        for dot in lens.trace {
            guard Self.clusters.indices.contains(dot.cluster) else { continue }
            let group = Self.clusters[dot.cluster]
            let cluster = packed(x: group.0 * w, y: group.1 * h, rank: dot.clusterIndex,
                                 count: dot.clusterCount, extent: span * 0.045)
            let status = packed(x: (dot.status == 1 ? 0.69 : 0.31) * w,
                                y: (dot.status == 1 ? 0.52 : 0.40) * h,
                                rank: dot.statusIndex, count: dot.statusCount, extent: span * 0.093)
            let phase = -Double.pi / 2 + Double(dot.day) * 2 * .pi / Double(max(1, lens.dayCount))
                + Double(dot.dayIndex / 6) * 0.08
            let day = polar(dayCenter, radius: dayRadius * (1 + Double(dot.dayIndex % 6) * 0.105), angle: phase)
            let a = index == 0 ? day : cluster
            let b = index == 0 ? cluster : status
            let p = CGPoint(x: mix(Double(a.x), Double(b.x), fraction),
                            y: mix(Double(a.y), Double(b.y), fraction))
            if dot.status == 1 {
                pending.addEllipse(in: disc(p, radius: 1.8))
                pendingHalos.addEllipse(in: disc(p, radius: 4.2))
                rims.addEllipse(in: disc(p, radius: 2.5))
            } else {
                bright.addEllipse(in: disc(p, radius: 1.8))
                halos.addEllipse(in: disc(p, radius: 4.2))
            }
        }
        context.fill(halos, with: .color(colors.accent.opacity(0.14)))
        context.fill(bright, with: .color(colors.accent.opacity(0.9)))
        context.fill(pendingHalos, with: .color(colors.accent.opacity(0.14 - final * 0.125)))
        context.fill(pending, with: .color(colors.accent.opacity(0.90 - final * 0.83)))
        if final > 0 { context.stroke(rims, with: .color(colors.second.opacity(final * 0.8)), lineWidth: 0.8) }
    }

    private func glow(_ context: GraphicsContext, at point: CGPoint, radius: Double,
                      color: Color, opacity: Double, atmospheric: Bool = false) {
        if atmospheric, let sprite = Self.haloSprite {
            // Same dithered continuous-alpha mask used by Android; no layered circles/bands.
            var resolved = context.resolve(Image(uiImage: sprite).renderingMode(.template))
            resolved.shading = .color(color.opacity(opacity))
            context.draw(resolved, in: disc(point, radius: radius))
            return
        }
        let gradient = Gradient(stops: [
            .init(color: color.opacity(opacity), location: 0),
            .init(color: color.opacity(opacity * 0.54), location: 0.36),
            .init(color: color.opacity(opacity * 0.12), location: 0.70),
            .init(color: color.opacity(0), location: 1)
        ])
        context.fill(Path(ellipseIn: disc(point, radius: radius)),
                     with: .radialGradient(gradient, center: point, startRadius: 0, endRadius: CGFloat(radius)))
    }

    private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    private func wave(_ period: Double, _ phase: Double = 0) -> Double {
        sin((seconds / period + phase) * 2 * .pi)
    }
    private func pulse(_ seed: Int, _ minimum: Double, _ span: Double) -> Double {
        let hash = (UInt32(truncatingIfNeeded: seed) &* 1_103_515_245 &+ 12_345) >> 9
        return wave(minimum + span * Double(hash % 591) / 590, Double((hash / 591) % 1009) / 1009)
    }
    private func disc(_ point: CGPoint, radius: Double) -> CGRect {
        CGRect(x: Double(point.x) - radius, y: Double(point.y) - radius, width: radius * 2, height: radius * 2)
    }
    private func polar(_ point: CGPoint, radius: Double, angle: Double) -> CGPoint {
        CGPoint(x: Double(point.x) + cos(angle) * radius, y: Double(point.y) + sin(angle) * radius)
    }
    private func packed(x: Double, y: Double, rank: Int, count: Int, extent: Double) -> CGPoint {
        polar(CGPoint(x: x, y: y), radius: count < 2 ? 0 : extent * sqrt(Double(rank) / Double(count - 1)),
              angle: Double(rank) * 2.399963)
    }
}
