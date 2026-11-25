import SwiftUI
import Foundation

/// Native portrait adaptation of the approved desktop optics. Photography is
/// the same bundled WebP, not a new render; the sky still draws its single atlas
/// and records afterwards. Time is the existing active-only Recall clock.
enum RecallSeasonSupplement {
    private final class Bitmap: NSObject {
        let image: CGImage
        init(_ image: CGImage) { self.image = image }
    }

    private static let cache: NSCache<NSString, Bitmap> = {
        let cache = NSCache<NSString, Bitmap>()
        cache.countLimit = 8
        cache.totalCostLimit = 12 * 1024 * 1024
        return cache
    }()
    private static func photo(_ name: String) -> CGImage? {
        let key = name as NSString
        if let found = cache.object(forKey: key) { return found.image }
        guard let decoded = RecallSeasonLayer.texture(name) else { return nil }
        cache.setObject(Bitmap(decoded), forKey: key, cost: decoded.bytesPerRow * decoded.height)
        return decoded
    }

    private static let edgeStops: [(Double, Double)] = [
        (0,0), (0.002,0), (0.017,0.11), (0.036,0.39), (0.059,0.78), (0.088,1),
        (0.912,1), (0.941,0.78), (0.964,0.39), (0.983,0.11), (0.998,0), (1,0)
    ]

    private struct Optics {
        let context: GraphicsContext
        let w, h, elapsed, page: Double
        let reduced: Bool
        var night: Double { RecallSeasonArt.clamp(1 - abs(page - 1)) }
        var pageRect: CGRect { CGRect(x: 0, y: 0, width: w, height: h) }
        func p(_ seconds: Double) -> Double { reduced ? 1 : RecallSeasonArt.ease(elapsed / seconds) }
        func pulse(_ progress: Double, center: Double, span: Double) -> Double {
            reduced ? 0 : RecallSeasonArt.ease(1 - abs(progress - center) / span)
        }
        func color(_ hex: UInt32) -> Color {
            Color(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
                  blue: Double(hex & 255) / 255)
        }

        func crop(_ name: String, x: Double, y: Double, width: Double, alpha: Double,
                  rotation: Double = 0, fold: Double = 1, feather: Bool = false) {
            guard alpha > 0, let image = RecallSeasonSupplement.photo(name) else { return }
            let ww = min(w * 1.95, max(1, width))
            let hh = ww * Double(image.height) / Double(image.width)
            let frame = CGRect(x: x * w, y: y * h, width: ww, height: hh)
            var ink = context
            if feather {
                ink.clip(to: Path(frame))
                ink.clipToLayer { mask in
                    mask.fill(Path(frame), with: .linearGradient(Gradient(stops: [
                        .init(color: .clear, location: 0), .init(color: .white, location: 0.16),
                        .init(color: .white, location: 0.83), .init(color: .clear, location: 1)
                    ]), startPoint: CGPoint(x: frame.midX, y: frame.minY),
                       endPoint: CGPoint(x: frame.midX, y: frame.maxY)))
                }
            }
            ink.opacity = RecallSeasonArt.clamp(alpha)
            if rotation != 0 || fold != 1 {
                ink.translateBy(x: frame.midX, y: frame.midY)
                ink.rotate(by: .degrees(rotation))
                ink.scaleBy(x: fold, y: 1)
                ink.draw(Image(decorative: image, scale: 1),
                         in: CGRect(x: -ww / 2, y: -hh / 2, width: ww, height: hh))
            } else {
                ink.draw(Image(decorative: image, scale: 1), in: frame)
            }
        }

        func light(x: Double, y: Double, rx: Double, ry: Double, hue: UInt32, alpha: Double) {
            guard rx > 0, ry > 0, alpha > 0 else { return }
            var field = context
            field.translateBy(x: x * w, y: y * h)
            field.scaleBy(x: rx, y: ry)
            let tone = color(hue)
            field.fill(Path(CGRect(x: -1, y: -1, width: 2, height: 2)),
                       with: .radialGradient(Gradient(stops: [
                .init(color: tone.opacity(RecallSeasonArt.clamp(alpha)), location: 0),
                .init(color: tone.opacity(RecallSeasonArt.clamp(alpha) * 0.42), location: 0.38),
                .init(color: tone.opacity(RecallSeasonArt.clamp(alpha) * 0.10), location: 0.70),
                .init(color: .clear, location: 1)
            ]), center: .zero, startRadius: 0, endRadius: 1))
        }

        func ellipse(x: Double, y: Double, width: Double, height: Double, hue: UInt32, alpha: Double) {
            guard alpha > 0 else { return }
            let rect = CGRect(x: w * x - width / 2, y: h * y - height / 2, width: width, height: height)
            context.stroke(Path(ellipseIn: rect), with: .color(color(hue).opacity(alpha)), lineWidth: 0.7)
        }
        func insideEdges() -> Optics {
            var layer = context
            layer.clipToLayer { mask in
                mask.fill(Path(pageRect), with: .linearGradient(Gradient(stops: RecallSeasonSupplement.edgeStops.map {
                    .init(color: .white.opacity($0.1), location: $0.0)
                }), startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))
            }
            return Optics(context: layer, w: w, h: h, elapsed: elapsed, page: page, reduced: reduced)
        }
        func textureFill(_ name: String, alpha: Double) {
            guard alpha > 0.003, let image = RecallSeasonSupplement.photo(name) else { return }
            var ink = context
            ink.opacity = RecallSeasonArt.clamp(alpha)
            ink.draw(Image(decorative: image, scale: 1), in: pageRect)
        }
    }

    static func draw(_ context: GraphicsContext, size: CGSize, art: RecallSeasonArt,
                     elapsed: Double, reduced: Bool, page: Double) {
        let o = Optics(context: context, w: Double(size.width), h: Double(size.height),
                       elapsed: elapsed, page: page, reduced: reduced)
        guard min(o.w, o.h) > 1 else { return }
        let w = o.w, h = o.h
        switch art {
        case .springStarts:
            let p = o.p(2.32)
            o.crop("fibres", x: -0.115 + p * 0.034, y: 0.36, width: w * 0.25, alpha: p * 0.66, feather: true)
        case .thunder:
            let p = o.p(3.45)
            o.crop("thunder-main", x: -0.045 + 0.038 * (1 - p), y: -0.003, width: w * 0.74,
                   alpha: 0.30 + 0.34 * p, feather: true)
            o.light(x: 0.19, y: 0.072, rx: w * 0.36, ry: h * 0.14,
                    hue: 0xf3d7ae, alpha: 0.24 * o.pulse(p, center: 0.39, span: 0.27))
        case .springEquinox:
            let p = o.p(4.15)
            for x in [0.24, 0.76] {
                o.light(x: x, y: 0.26, rx: w * 0.29, ry: h * 0.28,
                        hue: x < 0.5 ? 0xd8ca92 : 0x9db3a5, alpha: 0.09 + p * 0.09)
            }
            o.crop("swallows", x: 0.18 + p * 0.41, y: 0.182 - 0.035 * p,
                   width: w * 0.26, alpha: 0.29 + 0.65 * p)
        case .clearing:
            let p = o.p(2.80)
            o.crop("blossoms", x: -0.037, y: -0.073 + 0.008 * (1 - p), width: w * 0.54,
                   alpha: 0.48 + 0.22 * p, fold: -1)
            // Native water sheen and a short rippled edge; not the desktop's
            // second clipped rendering of the star paths.
            o.light(x: 0.70, y: 0.74, rx: w * 0.44, ry: h * 0.068, hue: 0x9bc8c9, alpha: 0.21)
            o.ellipse(x: 0.72, y: 0.739, width: w * (0.12 + p * 0.28), height: 7 + p * 12,
                      hue: 0xb5d7db, alpha: 0.18 + 0.17 * o.pulse(p, center: 0.55, span: 0.32))
            let prism = o.pulse(p, center: 0.46, span: 0.33)
            o.light(x: 0.13 + p * 0.07, y: 0.11, rx: w * 0.23, ry: h * 0.12, hue: 0xe7bfe0, alpha: 0.13 * prism)
        case .grainRain:
            let p = o.p(3.2)
            o.crop("ripples-main", x: 0.50, y: 0.85, width: w * 0.43, alpha: 0.32 + 0.26 * p, feather: true)
            o.crop("ripples-ring", x: 0.12 - p * 0.08, y: 0.77, width: w * (0.24 + 0.34 * p),
                   alpha: 0.38 * p + 0.32 * o.pulse(p, center: 0.42, span: 0.29), feather: true)
        case .summerStarts:
            let p = o.p(2.85)
            o.crop("canopy-far", x: 0.70 + 0.036 * (1 - p), y: -0.075, width: w * 0.36,
                   alpha: 0.24 + 0.21 * p)
            o.crop("canopy-main", x: -0.08 + 0.033 * (1 - p), y: -0.064, width: w * 0.64,
                   alpha: 0.34 + 0.27 * p)
        case .grainFull:
            let p = o.p(3.0)
            o.crop("grainfill-far", x: 0.17, y: 0.81 - 0.060 * p, width: w * 0.22,
                   alpha: 0.43 * p, rotation: 4.2 * (1 - p), feather: true)
            o.crop("grainfill-main", x: 0.018, y: 0.76 - 0.067 * p, width: w * 0.26,
                   alpha: 0.64 * p, rotation: -2.9 * (1 - p), feather: true)
        case .grainAwn:
            let p = o.p(2.1)
            o.crop("awns", x: 0.65 + 0.046 * (1 - p), y: 0.72, width: w * 0.37,
                   alpha: 0.48 * p, rotation: -6 * sin(p * .pi), feather: true)
        case .summerSolstice:
            let p = o.p(4.05)
            o.light(x: 0.17, y: 0.91, rx: w * (0.31 + 0.95 * p), ry: h * 0.61, hue: 0xffefbf,
                    alpha: (0.16 + 0.29 * p) * (1 - 0.26 * o.night))
            o.light(x: 0.26, y: 0.85, rx: w * (0.31 + 0.92 * p), ry: h * 0.18, hue: 0xf5c58b, alpha: 0.19 * p)
        case .lotus:
            let p = o.p(4.25)
            o.crop("lotus-cool", x: 0.59 + 0.035 * (1 - p), y: 0.58, width: w * 0.46,
                   alpha: 0.52 + 0.23 * p, rotation: 5.6 * (1 - p) * sin(p * 11 - 1.7), feather: true)
        case .heat:
            let p = o.p(4.65)
            o.crop("heat-haze", x: 0.046, y: 0.78, width: w * 0.96, alpha: 0.40 + 0.27 * p, feather: true)
            let pulse = o.pulse(p, center: 0.49, span: 0.45)
            if pulse > 0.003 {
                // Native short differential shear over the same approved thermal
                // photograph, not a live filter sampled from app/record content.
                for row in 0..<3 {
                    let band = 0.769 + Double(row) * 0.018
                    var strip = context
                    strip.clip(to: Path(CGRect(x: 0, y: h * band, width: w, height: h * 0.018)))
                    let refraction = Optics(context: strip, w: w, h: h, elapsed: elapsed, page: page, reduced: reduced)
                    refraction.crop("heat-haze", x: 0.046 + sin(p * 18 + Double(row) * 1.4) * pulse * 0.013,
                                    y: 0.762 - 0.009 * pulse, width: w * 0.96, alpha: 0.43 * pulse)
                }
            }
        case .autumnStarts:
            let p = o.p(4.9)
            let twist = -0.38 + 4.03 * p, fold = cos(twist)
            o.crop("turning-leaf", x: 0.78 - 0.16 * p + 0.055 * sin(p * 8), y: -0.042 + 0.49 * p,
                   width: w * 0.24, alpha: 0.28 + 0.55 * p,
                   rotation: -25 + 41 * p + 18 * sin(p * 10),
                   fold: abs(fold) < 0.11 ? (fold < 0 ? -0.11 : 0.11) : fold)
        case .cloudbreak:
            let p = o.p(4.6)
            o.crop("cloudbreak", x: -0.11 + 0.045 * (1 - p), y: 0.026, width: w * 1.13,
                   alpha: 0.39 + 0.29 * p, feather: true)
            o.light(x: 0.30, y: 0.16, rx: w * 0.57, ry: h * 0.16, hue: 0xeab77e, alpha: 0.35 - 0.31 * p)
            o.light(x: 0.47, y: 0.19, rx: w * 0.52, ry: h * 0.18, hue: 0xa7c6db, alpha: 0.08 + 0.18 * p)
        case .whiteDew:
            let masked = o.insideEdges()
            let p = o.p(2.2)
            masked.textureFill("dew-mist-day", alpha: (0.44 - 0.29 * o.night) * p)
            masked.textureFill("dew-mist-night", alpha: (0.61 + 0.31 * o.night) * p)
        case .coldDew:
            let p = o.p(3.55)
            o.crop("grass-dew", x: 0.52, y: 0.67, width: w * 0.61, alpha: 0.52 + 0.26 * p)
            o.light(x: 0.75, y: 0.73, rx: w * 0.28, ry: h * 0.068, hue: 0xb7d2cc,
                    alpha: 0.11 * o.pulse(p, center: 0.43, span: 0.35))
        case .winterStarts:
            let p = o.p(4.15)
            o.crop("first-ice", x: -0.07 - 0.045 * (1 - p), y: 0.80, width: w * 0.88,
                   alpha: 0.28 + 0.44 * p, feather: true)
        case .firstSnow:
            let p = o.p(5.25)
            // Same deliberately sparse, soft-focus cue, without a snowfall loop.
            for index in 0..<15 {
                let seed = Double(index)
                let delay = Double((index * 7) % 10) * 0.028
                let a = RecallSeasonArt.clamp((p - delay) / (1 - delay))
                let x = 0.065 + seed * 0.061 + 0.019 * sin(seed * 2.09 + a * 4.64)
                let y = 0.035 + Double((index * 13) % 34) * 0.011 + a * (0.23 + Double(index % 5) * 0.047)
                let r = 1.2 + Double((index * 3) % 7) * 0.23
                o.light(x: x, y: y, rx: r, ry: r * 1.21, hue: 0xdbeafa,
                        alpha: min(1, a / 0.23) * (0.34 + Double(index % 4) * 0.053))
            }
        case .deepSnow:
            let p = o.p(3.1)
            let faded = o.insideEdges()
            faded.crop("snow", x: 0.36 + 0.17 * (1 - p), y: 0.49, width: w * 1.25, alpha: 0.43 * p)
            faded.crop("snow", x: 0.17 + 0.24 * (1 - p), y: 0.57 + 0.05 * p,
                        width: w * 1.48, alpha: 0.63 * p)
        case .winterSolstice:
            let p = o.p(4.15)
            o.light(x: 0.52, y: 0.91 - 0.045 * p, rx: w * (0.12 + 0.38 * p), ry: h * 0.13,
                    hue: 0xe7af6c, alpha: 0.09 + 0.35 * p)
            o.light(x: 0.54, y: 0.91 - 0.043 * p, rx: w * (0.10 + 0.32 * p), ry: h * 0.023,
                    hue: 0xffdca5, alpha: 0.10 + 0.30 * p)
        case .icicles:
            let p = o.p(3.6)
            o.crop("icicles", x: 0.73, y: -0.048 + p * 0.013, width: w * 0.31,
                   alpha: 0.30 + 0.44 * p)
        case .deepIce:
            let p = o.p(4.30)
            o.crop("deep-ice", x: -0.06 + 0.05 * (1 - p), y: 0.84, width: w * 1.10,
                   alpha: 0.32 + 0.36 * p, feather: true)
            o.light(x: 0.57, y: 0.88, rx: w * (0.35 + 0.21 * p), ry: h * 0.15,
                    hue: 0x83abc5, alpha: 0.09 + 0.19 * p)
        case .rain, .autumnEquinox, .frost:
            break // The independently staged pilot already handles these exact branches.
        }
    }
}
