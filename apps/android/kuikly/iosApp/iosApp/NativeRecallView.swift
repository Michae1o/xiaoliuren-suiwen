import SwiftUI
import QuartzCore

/// Model-space drag and release state. A new finger samples the visible easing curve,
/// instead of jumping to the previous animation's destination.
private struct RecallPageMotion {
    var target = 0
    var resting = 0.0
    var glide: Glide?
    var dragOrigin: Double?
    var vertical = false
    var ambientPause: Double?
    var ambientOffset = 0.0

    struct Glide {
        let from, to, started, duration: Double
        var end: Double { started + duration }
        func at(_ now: Double) -> Double {
            let t = min(max((now - started) / duration, 0), 1)
            let remainder = 1 - t
            return from + (to - from) * (1 - remainder * remainder * remainder)
        }
    }

    func position(_ now: Double) -> Double { glide?.at(now) ?? resting }

    mutating func drag(dx: Double, dy: Double, width: Double, now: Double) {
        guard width > 0, !vertical else { return }
        if dragOrigin == nil {
            guard abs(dx) > abs(dy) * 1.14 else { vertical = true; return }
            dragOrigin = position(now)
            glide = nil
            if ambientPause == nil { ambientPause = now }
        }
        guard let origin = dragOrigin else { return }
        resting = max(0, min(2, origin - dx / width))
    }

    mutating func end(dx: Double, predicted: Double, width: Double, now: Double, reduce: Bool) {
        defer { vertical = false }
        guard let origin = dragOrigin, width > 0 else { return }
        let distance = resting - origin
        let startPage = Int(origin.rounded())
        let sameDirection = (predicted - dx) * dx > 0
        let fling = abs(dx) > 14 && abs(predicted - dx) > 65 && sameDirection
        let selected: Int
        if abs(distance) >= 0.55 { selected = Int(resting.rounded()) }
        else if abs(distance) > 0.22 || fling { selected = startPage + (dx < 0 ? 1 : -1) }
        else { selected = startPage }
        settle(selected, now: now, reduce: reduce)
    }

    mutating func settle(_ page: Int, now: Double, reduce: Bool) {
        target = min(2, max(0, page))
        let previous = position(now)
        let destination = Double(target)
        dragOrigin = nil
        vertical = false
        resting = destination
        if reduce || abs(previous - destination) < 0.001 {
            glide = nil
            resumeAmbient(at: now)
        } else {
            if ambientPause == nil { ambientPause = now }
            glide = Glide(from: previous, to: destination, started: now,
                          duration: 0.17 + 0.095 * min(1.6, abs(previous - destination)))
        }
    }

    mutating func finish(_ deadline: Double) {
        guard let glide = glide, glide.end == deadline else { return }
        self.glide = nil
        resumeAmbient(at: deadline)
    }

    private mutating func resumeAmbient(at now: Double) {
        if let pause = ambientPause {
            ambientOffset += max(0, now - pause)
            ambientPause = nil
        }
    }

    func clock(at now: Double) -> Double { (ambientPause ?? now) - ambientOffset }
}

/// A native presentation drawn over (not instead of) the measured root overlay.
/// Removal is delayed until the exit lands, including on iOS 15/16 without a SwiftUI completion API.
struct NativeRecallPresentation: View {
    let report: RecallReport
    let exiting: Bool
    let onClose: () -> Void
    let onExitFinished: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var revealed = false

    var body: some View {
        NativeRecallView(report: report, onClose: onClose)
            .opacity(revealed ? 1 : 0)
            .offset(x: reduceMotion || revealed ? 0 : 16)
            .allowsHitTesting(revealed && !exiting)
            .accessibilityHidden(!revealed || exiting)
            .task(id: exiting) { @MainActor in
                let duration = reduceMotion ? 0.14 : (exiting ? 0.24 : 0.32)
                if exiting {
                    withAnimation(.timingCurve(0.36, 0.0, 0.62, 1.0, duration: duration)) {
                        revealed = false
                    }
                    do {
                        // Allow the final eased frame to land before releasing the underlying view.
                        try await Task<Never, Never>.sleep(nanoseconds: UInt64((duration + 0.02) * 1_000_000_000))
                        if !Task.isCancelled { onExitFinished() }
                    } catch { /* Dismissal was superseded or the host unmounted. */ }
                } else {
                    do {
                        // Commit the initial state so a fast bridge update cannot skip the reveal.
                        try await Task<Never, Never>.sleep(nanoseconds: 20_000_000)
                        guard !Task.isCancelled else { return }
                        withAnimation(.timingCurve(0.19, 0.72, 0.26, 1.0, duration: duration)) {
                            revealed = true
                        }
                    } catch { /* Replaced by an exit or a different report. */ }
                }
            }
    }
}

private enum RecallAboutBackSwipe {
    static let edgeWidth: CGFloat = 28

    static func shouldReturn(dx: Double, dy: Double, projectedX: Double, width: Double) -> Bool {
        let traveled = dx
        guard traveled >= 42, traveled >= abs(dy) * 1.35, projectedX >= dx - 18 else { return false }
        let threshold = min(88, max(64, width * 0.20))
        return traveled >= threshold || projectedX >= max(115, width * 0.30)
    }
}

struct NativeRecallView: View {
    let report: RecallReport
    let onClose: () -> Void

    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @GestureState private var isTouching = false
    @State private var lensKey: String
    @State private var motion = RecallPageMotion()
    @State private var seasonClock = RecallSeasonClock()
    private static let names = ["卦数", "事类", "实际情况"]

    init(report: RecallReport, onClose: @escaping () -> Void) {
        self.report = report
        self.onClose = onClose
        _lensKey = State(initialValue: report.selected)
    }

    private var lens: RecallLensPayload { report.lenses.first { $0.key == lensKey } ?? report.lenses[0] }

    private var seasonArt: RecallSeasonArt? { RecallSeasonArt(lens.season?.term) }

    private func paperOpacity(_ usual: Double) -> Double {
        seasonArt == .autumnEquinox ? 1 : usual
    }

    var body: some View {
        let colors = RecallColors(lens.season)
        let deadline = motion.glide?.end
        GeometryReader { geometry in
            // TimelineView can publish its content's ideal size. Pin both the timeline and
            // every rendered scene to the real overlay viewport, not the date/footer's size.
            timeline(in: geometry.size, colors: colors, deadline: deadline)
        }
        .onAppear { seasonClock.restart(at: CACurrentMediaTime(), active: scenePhase == .active) }
        .onChange(of: seasonArt) { _ in
            seasonClock.restart(at: CACurrentMediaTime(), active: scenePhase == .active)
        }
        .onChange(of: scenePhase) { phase in seasonClock.setActive(phase == .active, at: CACurrentMediaTime()) }
        .task(id: deadline) {
            guard let deadline = deadline else { return }
            let wait = max(0, deadline - CACurrentMediaTime())
            do {
                try await Task<Never, Never>.sleep(nanoseconds: UInt64(wait * 1_000_000_000))
                if !Task.isCancelled { motion.finish(deadline) }
            } catch { /* A new gesture or dismissal invalidated this settle. */ }
        }
        .onChange(of: isTouching) { touching in
            if !touching, motion.dragOrigin != nil {
                motion.settle(motion.target, now: CACurrentMediaTime(), reduce: reduceMotion)
            }
            if !touching { motion.vertical = false }
        }
        .onChange(of: reduceMotion) { reduce in
            if reduce { motion.settle(motion.target, now: CACurrentMediaTime(), reduce: true) }
        }
        .accessibilityAction(.escape, onClose)
    }

    private func timeline(in viewport: CGSize, colors: RecallColors, deadline: Double?) -> some View {
        TimelineView(.animation(minimumInterval: deadline != nil ? 1.0 / 60 : 1.0 / 24,
                                paused: reduceMotion || scenePhase != .active)) { _ in
            let now = CACurrentMediaTime()
            let position = motion.position(now)
            let selected = min(2, max(0, Int(position.rounded())))
            ZStack {
                // The foreground remains safe-area aware; only the uninterrupted sky
                // extends beneath the real status bar, home indicator, header and footer.
                NativeRecallSky(position: reduceMotion ? Double(selected) : position,
                                seconds: reduceMotion ? 0 : motion.clock(at: now), lens: lens, colors: colors,
                                season: seasonArt, seasonElapsed: seasonClock.elapsed(at: now), reduced: reduceMotion)
                    .ignoresSafeArea()
                VStack(spacing: 0) {
                    header(colors)
                    Text(lens.start + "  –  " + lens.end)
                        .font(.caption2.monospacedDigit())
                        .tracking(0.4)
                        .foregroundStyle(RecallColors.paper.opacity(paperOpacity(0.70)))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 24)
                        .padding(.top, 10)
                        .padding(.bottom, 3)
                    pager(position: position, selected: selected, colors: colors)
                    footer(selected: selected, colors: colors)
                }
                .padding(.bottom, 6)
            }
            .frame(width: viewport.width, height: viewport.height, alignment: .topLeading)
            .background(colors.night.ignoresSafeArea())
            .contentShape(Rectangle())
        }
        .frame(width: viewport.width, height: viewport.height, alignment: .topLeading)
    }

    private func header(_ colors: RecallColors) -> some View {
        HStack(spacing: 5) {
            Button(action: onClose) {
                Image(systemName: "arrow.left")
                    .font(.system(size: 17, weight: .regular))
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("返回")
            Text("回观")
                .font(.system(.title3, design: .serif).weight(.medium))
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 6)
            if report.lenses.count > 1 {
                HStack(spacing: 0) {
                    ForEach(report.lenses) { item in
                        let selected = item.key == lensKey
                        Button { lensKey = item.key } label: {
                            Text(item.label)
                                .font(.caption.weight(.medium))
                                .foregroundStyle(selected ? colors.accent : RecallColors.paper.opacity(paperOpacity(0.76)))
                                .padding(.horizontal, 12)
                                .frame(minWidth: 44, minHeight: 44)
                                .background(Capsule().fill(selected ? colors.accent.opacity(0.16) : Color.clear))
                                .contentShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(selected ? .isSelected : [])
                    }
                }
                .background(Capsule().fill(RecallColors.paper.opacity(0.055)))
                .overlay(Capsule().strokeBorder(RecallColors.paper.opacity(0.13), lineWidth: 0.7)
                    .allowsHitTesting(false))
            }
        }
        .foregroundStyle(RecallColors.paper)
        .padding(.leading, 9)
        .padding(.trailing, 21)
    }

    private func pager(position: Double, selected: Int, colors: RecallColors) -> some View {
        GeometryReader { geo in
            let width = geo.size.width
            ZStack {
                ForEach(Self.names.indices, id: \.self) { index in
                    frame(index, colors: colors, short: geo.size.height < 435)
                        .frame(width: width, height: geo.size.height)
                        .offset(x: CGFloat(Double(index) - position) * width)
                        .accessibilityHidden(index != selected)
                        .allowsHitTesting(index == selected)
                }
            }
            .frame(width: width, height: geo.size.height)
            .clipped()
            .contentShape(Rectangle())
            .overlay {
                if report.fromAbout && motion.target == 0 && motion.glide == nil &&
                    motion.resting == 0 && width > RecallAboutBackSwipe.edgeWidth {
                    // This hit region is present only while settled at page one. A right drag
                    // is clamped at zero by the simultaneous pager; left drags can still page.
                    Color.clear
                        .frame(width: RecallAboutBackSwipe.edgeWidth, height: geo.size.height)
                        .contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance: 10).onEnded { value in
                            if RecallAboutBackSwipe.shouldReturn(
                                dx: Double(value.translation.width), dy: Double(value.translation.height),
                                projectedX: Double(value.predictedEndTranslation.width), width: Double(width)) {
                                onClose()
                            }
                        })
                        // Physical left, even if the system language lays views out right-to-left.
                        .offset(x: (RecallAboutBackSwipe.edgeWidth - width) / 2)
                        .accessibilityHidden(true)
                }
            }
            // A vertical category scroll stays vertical; only directional horizontal movement moves the sky.
            .simultaneousGesture(DragGesture(minimumDistance: 9, coordinateSpace: .local)
                .updating($isTouching) { _, state, _ in state = true }
                .onChanged { value in
                    motion.drag(dx: Double(value.translation.width), dy: Double(value.translation.height),
                                width: Double(width), now: CACurrentMediaTime())
                }
                .onEnded { value in
                    motion.end(dx: Double(value.translation.width), predicted: Double(value.predictedEndTranslation.width),
                               width: Double(width), now: CACurrentMediaTime(), reduce: reduceMotion)
                })
        }
    }

    @ViewBuilder private func frame(_ index: Int, colors: RecallColors, short: Bool) -> some View {
        switch index {
        case 0: countPage(colors, short: short)
        case 1: categoryPage(colors, short: short)
        default: notesPage(colors, short: short)
        }
    }

    private func countPage(_ colors: RecallColors, short: Bool) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            marker("壹  ·  卦数", colors)
            title(lens.season?.term ?? lens.label)
            if let season = lens.season {
                Text(season.tagline).font(.footnote).foregroundStyle(colors.second)
            }
            HStack(alignment: .lastTextBaseline, spacing: 7) {
                Text(String(lens.total))
                    .font(.system(size: short ? 57 : 71, design: .serif))
                    .foregroundStyle(colors.accent)
                Text("则").font(.subheadline).foregroundStyle(RecallColors.paper)
            }
            .accessibilityElement(children: .combine)
            Spacer(minLength: 8)
            footnote(lens.total == 0 ? "这段时间还没有卦例。" : "按起卦日呈现 · 亮点为卦例")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .padding(.horizontal, 25)
        .padding(.top, short ? 15 : 37)
        .padding(.bottom, short ? 14 : 26)
    }

    private func categoryPage(_ colors: RecallColors, short: Bool) -> some View {
        GeometryReader { geo in
            VStack(alignment: .trailing, spacing: 11) {
                VStack(alignment: .leading, spacing: 4) {
                    marker("贰  ·  所问", colors)
                    title("事类")
                    if lens.categories.isEmpty {
                        footnote("这段时间没有事类记录。")
                        Spacer(minLength: 0)
                    } else {
                        ScrollView(.vertical, showsIndicators: false) {
                            LazyVStack(spacing: 0) {
                                ForEach(Array(lens.categories.enumerated()), id: \.offset) { row in
                                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                                        Text(row.element.label)
                                            .font(.footnote)
                                            .foregroundStyle(RecallColors.paper)
                                            .lineLimit(2)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                        Text(String(row.element.count))
                                            .font(.system(.body, design: .serif))
                                            .foregroundStyle(colors.accent)
                                    }
                                    .padding(.vertical, 8)
                                    .accessibilityElement(children: .combine)
                                }
                            }
                        }
                    }
                }
                .frame(width: max(0, geo.size.width * 0.55 - 20),
                       height: max(0, geo.size.height * (short ? 0.77 : 0.66)),
                       alignment: .topLeading)
                Spacer(minLength: 0)
                footnote(lens.categories.count > 9 ? "低频事类仅在点图合并" : "同类卦例，聚为一簇")
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 25)
            .padding(.top, geo.size.height * (short ? 0.07 : 0.13))
            .padding(.bottom, short ? 12 : 25)
        }
    }

    private func notesPage(_ colors: RecallColors, short: Bool) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            marker("叁  ·  后来", colors)
                .frame(maxWidth: .infinity, alignment: .trailing)
            Spacer(minLength: 0)
            title("实际情况")
            HStack(spacing: 12) {
                ledger(lens.noted, label: "已记", color: colors.accent)
                ledger(max(0, lens.total - lens.noted), label: "未记", color: colors.second)
            }
            footnote(lens.total == 0 ? "这段时间还没有卦例。" : "只统计是否填写，不判断是否应验。")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, 25)
        .padding(.top, short ? 16 : 35)
        .padding(.bottom, short ? 14 : 24)
    }

    private func ledger(_ count: Int, label: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(String(count)).font(.system(size: 42, design: .serif)).foregroundStyle(color)
            Text(label).font(.footnote).foregroundStyle(RecallColors.paper)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }

    private func marker(_ text: String, _ colors: RecallColors) -> some View {
        Text(text).font(.caption2.weight(.medium)).tracking(2).foregroundStyle(colors.second)
    }

    private func title(_ text: String) -> some View {
        Text(text).font(.system(.largeTitle, design: .serif).weight(.medium))
            .foregroundStyle(RecallColors.paper)
            .accessibilityAddTraits(.isHeader)
    }

    private func footnote(_ text: String) -> some View {
        Text(text).font(.caption).foregroundStyle(RecallColors.paper.opacity(paperOpacity(0.76)))
    }

    private func footer(selected: Int, colors: RecallColors) -> some View {
        HStack(spacing: 0) {
            ForEach(Self.names.indices, id: \.self) { page in
                Button {
                    motion.settle(page, now: CACurrentMediaTime(), reduce: reduceMotion)
                } label: {
                    VStack(spacing: 6) {
                        Capsule()
                            .fill(selected == page ? colors.accent : colors.second.opacity(0.47))
                            .frame(width: selected == page ? 23 : 6, height: 2)
                            .accessibilityHidden(true)
                        Text(Self.names[page]).font(.caption2.weight(.medium))
                            .foregroundStyle(RecallColors.paper.opacity(paperOpacity(selected == page ? 1 : 0.67)))
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected == page ? .isSelected : [])
            }
        }
        .padding(.horizontal, 20)
    }
}

struct NativeRecallReminder: View {
    let item: RecallReminderNotice
    let acknowledge: () -> Void
    let open: () -> Void
    let dismiss: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Button(action: open) {
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.title).font(.caption.weight(.semibold)).lineLimit(1)
                        if !item.message.isEmpty {
                            Text(item.message).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                        }
                    }
                    Spacer(minLength: 2)
                    Image(systemName: "chevron.forward")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
                .padding(.leading, 12)
                .padding(.trailing, 3)
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint("查看回观")
            Button(action: dismiss) {
                Image(systemName: "xmark")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.secondary)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("关闭回观提醒")
        }
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
        .task(id: item.id) {
            // If the home surface becomes obstructed, SwiftUI cancels this task before writing "shown".
            do {
                try await Task<Never, Never>.sleep(nanoseconds: 150_000_000)
                if !Task.isCancelled { acknowledge() }
            } catch { /* It never stayed visible. */ }
        }
    }
}
