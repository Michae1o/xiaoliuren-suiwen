import SwiftUI
import ImageIO

/// Full-bleed decorative underlay. The single original star chart and trace
/// are painted afterwards on both Canvas and Metal-backed ambient paths.
enum RecallSeasonLayer {
    private static func rgb(_ bits: UInt32) -> Color {
        Color(red: Double((bits >> 16) & 255) / 255,
              green: Double((bits >> 8) & 255) / 255, blue: Double(bits & 255) / 255)
    }

    static func texture(_ name: String) -> CGImage? {
        guard let url = Bundle.main.url(forResource: name, withExtension: "webp",
                                         subdirectory: "Assets/common/recall/season"),
              let source = CGImageSourceCreateWithURL(url as CFURL, nil)
        else { return nil }
        let options = [kCGImageSourceShouldCacheImmediately: true] as CFDictionary
        return CGImageSourceCreateImageAtIndex(source, 0, options)
    }

    private static let frostTextures: [CGImage?] = ["frost-side", "frost-sill", "frost-corner"].map { RecallSeasonLayer.texture($0) }

    static func draw(_ context: GraphicsContext, size: CGSize, art: RecallSeasonArt, elapsed: Double,
                     reduced: Bool, position: Double) {
        let w = Double(size.width), h = Double(size.height)
        guard w > 1, h > 1 else { return }
        switch art {
        case .rain: return // The sky renders rain together with its decorative atlas.
        case .autumnEquinox:
            equinox(context, w: w, h: h, elapsed: elapsed, reduced: reduced)
        case .frost: frost(context, w: w, h: h, elapsed: elapsed, reduced: reduced, position: position)
        default:
            RecallSeasonSupplement.draw(context, size: size, art: art, elapsed: elapsed,
                                        reduced: reduced, page: position)
        }
    }

    // Same continuous fields/stacking as the desktop and Android. No
    // masks, divider, plate, bar, or screen-space clipping boundary.
    private struct DuskField {
        let tint: UInt32
        let x, y, rx, ry, alpha: Double

        init(_ tint: UInt32, _ x: Double, _ y: Double, _ rx: Double, _ ry: Double, _ alpha: Double) {
            self.tint = tint; self.x = x; self.y = y
            self.rx = rx; self.ry = ry; self.alpha = alpha
        }
    }

    static let duskBase: UInt32 = 0x101b2a
    private static let duskFields: [DuskField] = [
        .init(0xc28253, 0.22, 0.69, 0.65, 0.55, 0.46),
        .init(0x7198b7, 0.76, 0.27, 0.65, 0.55, 0.53),
        .init(0xe7ad77, 0.22, 0.70, 0.32, 0.32, 0.17),
        .init(0xa9c9d8, 0.77, 0.27, 0.30, 0.29, 0.16),
    ]
    private static let duskFalloff: [(Double, Double)] = [
        (0.0, 1.0), (0.18, 0.90), (0.46, 0.59), (0.74, 0.22), (1.0, 0.0)
    ]

    private static func equinox(_ context: GraphicsContext, w: Double, h: Double, elapsed: Double,
                                reduced: Bool) {
        let form = Path(CGRect(x: 0, y: 0, width: w, height: h))
        context.fill(form, with: .color(rgb(duskBase)))
        let arrival = RecallSeasonArt.duskArrival(elapsed, reduced: reduced)
        guard arrival > 0 else { return }
        for wash in duskFields {
            var local = context
            local.translateBy(x: w * wash.x, y: h * wash.y)
            local.scaleBy(x: w * wash.rx, y: h * wash.ry)
            let shade = rgb(wash.tint)
            let gradient = Gradient(stops: duskFalloff.map {
                .init(color: shade.opacity(wash.alpha * arrival * $0.1), location: $0.0)
            })
            local.fill(Path(CGRect(x: -1, y: -1, width: 2, height: 2)),
                       with: .radialGradient(gradient, center: .zero, startRadius: 0, endRadius: 1))
        }
    }

    private static func frost(_ context: GraphicsContext, w: Double, h: Double, elapsed: Double,
                              reduced: Bool, position: Double) {
        let p = reduced ? 1 : RecallSeasonArt.ease(elapsed / 3.14)
        let night = 0.72 + 0.28 * RecallSeasonArt.clamp(1 - abs(position - 1))
        let widths = [max(50, min(79, 0.16 * w)), max(148, min(258, 0.59 * w)), max(111, min(199, 0.47 * w))]
        let lags = [0.0, 0.12, 0.24], weights = [0.85, 0.88, 0.78]
        for (index, photo) in frostTextures.enumerated() {
            guard let photo else { continue }
            let width = widths[index], height = width * Double(photo.height) / Double(photo.width)
            let x = index == 0 ? -0.029 * w : (index == 1 ? -0.01 * w : 1.04 * w - width)
            let y = index == 0 ? 0.34 * h : ((index == 1 ? 1.017 : 1.009) * h - height)
            let rect = CGRect(x: x, y: y, width: width, height: height), form = Path(rect)
            let a = RecallSeasonArt.clamp((p - lags[index]) / (1 - lags[index]))
            var frostPatch = context
            frostPatch.clip(to: form)
            // Soft photographic envelope grown inwards from the true glass rim.
            frostPatch.clipToLayer { mask in
                let c = index == 0 ? CGPoint(x: x, y: y + height * 0.52)
                                   : CGPoint(x: x + width * (index == 1 ? 0.33 : 0.64), y: y + height)
                var feather = mask
                feather.translateBy(x: c.x, y: c.y)
                let rx = width * (index == 0 ? 0.99 : 0.80)
                let ry = height * (index == 0 ? 0.55 : 1.08)
                feather.scaleBy(x: rx, y: ry)
                feather.fill(Path(CGRect(x: -2, y: -2, width: 4, height: 4)),
                             with: .radialGradient(Gradient(stops: [
                    .init(color: .white, location: 0.18), .init(color: .white.opacity(0.7), location: 0.53),
                    .init(color: .white.opacity(0.28), location: 0.77), .init(color: .clear, location: 1)
                ]), center: .zero, startRadius: 0, endRadius: 1))
            }
            if a < 0.976 {
                frostPatch.clipToLayer { mask in
                    let head = -0.18 + 1.21 * a
                    let start = index == 0 ? CGPoint(x: x, y: y) : CGPoint(x: x, y: y + height)
                    let end = index == 0 ? CGPoint(x: x + width, y: y) : CGPoint(x: x, y: y)
                    mask.fill(form, with: .linearGradient(Gradient(stops: [
                        .init(color: .white, location: RecallSeasonArt.clamp(head)),
                        .init(color: .clear, location: RecallSeasonArt.clamp(head + 0.20))
                    ]), startPoint: start, endPoint: end))
                }
            }
            frostPatch.opacity = (0.03 + (0.44 - 0.15 * night) * a) * weights[index]
            frostPatch.draw(Image(decorative: photo, scale: 1), in: rect)
        }
    }

    struct Pane {
        let x, y, rx, ry, opacity, seed, impact: Double

        func displace(_ p: CGPoint) -> CGSize {
            let vx = Double(p.x) - x, vy = Double(p.y) - y
            let nx = vx / rx, ny = vy / ry
            let r2 = nx * nx + ny * ny
            guard r2 < 1 else { return .zero }
            let g = 1 - r2, strain = opacity * g * g
            return CGSize(width: (vx * 1.12 + rx * 0.21 * ny) * strain,
                          height: (vy * 0.94 - ry * 0.13 * nx) * strain)
        }

        func outline() -> Path {
            let points: [CGPoint] = (0..<20).map { i in
                let a = Double(i) * .pi / 10
                let scale = 1 + 0.073 * sin(3 * a + seed * 6) + 0.049 * cos(5 * a + seed * 3)
                    + impact * 0.072 * cos(2 * a + 1.9)
                let v = sin(a) * scale, u = cos(a) * scale * (1 + 0.15 * max(0, v))
                return CGPoint(x: x + rx * u, y: y + ry * v)
            }
            func mid(_ a: CGPoint, _ b: CGPoint) -> CGPoint {
                CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2)
            }
            var p = Path(); p.move(to: mid(points[19], points[0]))
            for (i, c) in points.enumerated() {
                p.addQuadCurve(to: mid(c, points[(i + 1) % 20]), control: c)
            }
            p.closeSubpath()
            return p
        }
    }

    struct RainFrame {
        var panes: [Pane] = []
        var air: [RecallRainPose] = []
        var film: [RecallRainPose] = []

        func refract(_ p: CGPoint) -> CGPoint {
            var painted = p
            for q in panes {
                let d = q.displace(p)
                painted.x += d.width; painted.y += d.height
            }
            return painted
        }

        func appendLine(to path: inout Path, a: CGPoint, b: CGPoint) {
            let dx = Double(b.x - a.x), dy = Double(b.y - a.y)
            let norm = dx * dx + dy * dy
            let hit = panes.contains { q in
                let t = norm > 0 ? RecallSeasonArt.clamp(((q.x - Double(a.x)) * dx + (q.y - Double(a.y)) * dy) / norm) : 0
                let r = 1.12 * max(q.rx, q.ry)
                return abs(Double(a.x) + t * dx - q.x) < r && abs(Double(a.y) + t * dy - q.y) < r
            }
            if !hit { path.move(to: a); path.addLine(to: b); return }
            let steps = min(384, max(1, Int(ceil(hypot(dx, dy) / 0.9))))
            for i in 0...steps {
                let t = Double(i) / Double(steps)
                let p = refract(CGPoint(x: Double(a.x) + dx * t, y: Double(a.y) + dy * t))
                if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
            }
        }
    }

    static func rainFrame(w: Double, h: Double, elapsed: Double, reduced: Bool) -> RainFrame {
        if reduced {
            let points = [(0.184, 0.395, 4.0), (0.438, 0.567, 4.4), (0.674, 0.251, 3.9),
                          (0.859, 0.716, 5.5), (0.958, 0.435, 4.7)]
            return RainFrame(panes: points.map { item in
                let (x, y, r) = item
                return Pane(x: x * w, y: y * h, rx: r, ry: r * 0.86,
                            opacity: x > 0.31 && x < 0.73 ? 0.66 : 1, seed: x, impact: 0)
            })
        }
        var frame = RainFrame()
        for (i, track) in RecallRainTrack.pane.enumerated() {
            guard let pose = track.pose(elapsed: elapsed, width: w, height: h) else { continue }
            switch pose {
            case .fall: frame.air.append(pose)
            case .lens(let x, let y, let dx, let dy, let r, let alpha, let film, let age):
                if dy > 1.7 && film > 0 { frame.film.append(pose) }
                if alpha <= 0 { continue }
                let slide = track.slip > 0 ? RecallSeasonArt.clamp(dy / track.slip) : 0
                let stretch = sin(.pi * slide)
                let impact = sin(.pi * RecallSeasonArt.clamp(age / 0.24)) * exp(-age * 3.4)
                frame.panes.append(Pane(x: x + dx, y: y + dy,
                    rx: r * (1 + 0.39 * impact - 0.23 * stretch),
                    ry: r * (0.86 - 0.27 * impact + 0.54 * stretch),
                    opacity: alpha, seed: Double(i) * 0.381, impact: impact))
            }
        }
        return frame
    }

    static func drawRain(_ context: GraphicsContext, frame: RainFrame, position: Double) {
        let dark = abs(position - 1) < 0.56
        let ice = rgb(dark ? 0xc1d9e6 : 0x507789), shine = rgb(0xc1e5e9)
        for pose in frame.air {
            guard case .fall(let x, let y, let r, let wind, let quiet) = pose else { continue }
            let head = CGPoint(x: x, y: y + 1), tail = CGPoint(x: x - wind * 0.033, y: y - 9 - (r - 0.85) * 14.95)
            var line = Path(); line.move(to: tail); line.addLine(to: head)
            context.stroke(line, with: .linearGradient(Gradient(colors: [.clear, ice.opacity(0.58 * quiet)]),
                startPoint: tail, endPoint: head), style: StrokeStyle(lineWidth: r * 0.82, lineCap: .round))
        }
        for pose in frame.film {
            guard case .lens(let x, let y, let dx, let dy, _, _, let alpha, _) = pose else { continue }
            let start = CGPoint(x: x, y: y), end = CGPoint(x: x + dx, y: y + dy)
            var line = Path(); line.move(to: start)
            line.addQuadCurve(to: end, control: CGPoint(x: x - 0.6, y: y + dy * 0.54))
            context.stroke(line, with: .linearGradient(Gradient(stops: [
                .init(color: ice.opacity(0.02 * alpha), location: 0),
                .init(color: ice.opacity(0.30 * alpha), location: 0.54),
                .init(color: shine.opacity(0.06 * alpha), location: 1)
            ]), startPoint: start, endPoint: end), style: StrokeStyle(lineWidth: 1.18, lineCap: .round))
        }
        // No rendered outline or sprite. The chart already supplies the content
        // of the transparent belly; this adds only two soft reflection tones.
        for drop in frame.panes {
            let start = CGPoint(x: drop.x - drop.rx * 0.47, y: drop.y - drop.ry * 0.81)
            let end = CGPoint(x: drop.x + drop.rx * 0.4, y: drop.y + drop.ry * 0.86)
            context.fill(drop.outline(), with: .linearGradient(Gradient(colors: [
                shine.opacity(0.24 * drop.opacity), shine.opacity(0.025 * drop.opacity),
                rgb(0x091719).opacity(0.16 * drop.opacity)]), startPoint: start, endPoint: end))
        }
    }
}
