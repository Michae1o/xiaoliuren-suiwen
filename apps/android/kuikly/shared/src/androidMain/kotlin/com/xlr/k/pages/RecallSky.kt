package com.xlr.k.pages

import androidx.compose.runtime.Composable
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.extension.MakeKuiklyComposeNode
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.coil3.rememberAsyncImagePainter
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.Image
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.ui.graphics.ColorFilter
import com.tencent.kuikly.compose.ui.graphics.painter.Painter
import com.tencent.kuikly.compose.ui.layout.ContentScale
import com.tencent.kuikly.compose.ui.layout.onGloballyPositioned
import com.tencent.kuikly.compose.ui.unit.IntSize
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.StrokeCap
import com.tencent.kuikly.compose.ui.graphics.Path
import com.tencent.kuikly.compose.ui.graphics.drawscope.Stroke
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.platform.LocalDensity
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.core.base.Attr
import com.tencent.kuikly.core.base.DeclarativeBaseView
import com.tencent.kuikly.core.base.event.Event
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay

internal data class RecallInk(val background: Color, val accent: Color, val second: Color, val glow: Color)
private data class Camera(val x: Float, val y: Float, val scale: Float, val rotation: Float)
private val lenses = listOf(
    Camera(.69f, .47f, .59f, -.11f),
    Camera(.18f, .27f, .70f, .27f),
    Camera(.80f, .57f, .81f, .58f),
)
private val clusterAnchors = listOf(
    Offset(.18f, .46f), Offset(.40f, .48f), Offset(.13f, .58f),
    Offset(.32f, .61f), Offset(.12f, .70f), Offset(.40f, .72f),
    Offset(.22f, .78f), Offset(.44f, .81f), Offset(.30f, .88f),
)
private val statusAnchors = listOf(Offset(.31f, .40f), Offset(.69f, .52f))
private fun lerpRecall(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun lerpRecall(a: Offset, b: Offset, t: Float) = Offset(lerpRecall(a.x, b.x, t), lerpRecall(a.y, b.y, t))
private fun polarRecall(center: Offset, radial: Float, angle: Double) =
    Offset(center.x + radial * cos(angle).toFloat(), center.y + radial * sin(angle).toFloat())

private data class AtlasEdge(val a: RecallAtlasStar, val b: RecallAtlasStar, val rank: Int)
private val atlasEdges by lazy {
    (recallAtlasMansions + recallAtlasEnclosures).flatMapIndexed { i, group ->
        val rank = when {
            group.name == "北斗" -> 2
            i >= recallAtlasMansions.size -> 1
            else -> 0
        }
        group.lines.flatMap { line ->
            line.asList().zipWithNext().mapNotNull { (a, b) ->
                val first = recallAtlasStars[a]
                val second = recallAtlasStars[b]
                if (first == null || second == null) null else AtlasEdge(first, second, rank)
            }
        }
    }
}

private data class RecallLens(val center: Offset, val radius: Float, val cosine: Float, val sine: Float) {
    fun at(star: RecallAtlasStar) = Offset(
        center.x + (star.x * cosine - star.y * sine) * radius,
        center.y + (star.x * sine + star.y * cosine) * radius,
    )
}

private fun atlasLens(position: Float, width: Float, height: Float): RecallLens {
    val index = position.toInt().coerceIn(0, 1)
    val t = (position - index).coerceIn(0f, 1f)
    val a = lenses[index]
    val b = lenses[index + 1]
    val angle = lerpRecall(a.rotation, b.rotation, t)
    return RecallLens(
        Offset(lerpRecall(a.x, b.x, t) * width, lerpRecall(a.y, b.y, t) * height),
        lerpRecall(a.scale, b.scale, t) * min(width, height),
        cos(angle), sin(angle),
    )
}

/** Three independently read layers: atmospheric light, one coherent historic atlas, and local data. */
@Composable
internal fun RecallSky(
    position: Float,
    summary: RecallSummary,
    trace: RecallTrace,
    ink: RecallInk,
    ambientRunning: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier,
    seasonTerm: String? = null,
) {
    // Keep local time through drag/settle. No jump to a new phase when a gesture ends.
    var elapsed by remember { mutableStateOf(0f) }
    LaunchedEffect(ambientRunning, reduceMotion) {
        if (!ambientRunning || reduceMotion) return@LaunchedEffect
        var last = SystemClock.uptimeMillis()
        while (true) {
            delay(40)
            val now = SystemClock.uptimeMillis()
            elapsed += (now - last).coerceIn(0L, 80L) / 1000f
            last = now
        }
    }
    val sceneClock = recallSceneClock(elapsed, reduceMotion)
    val density = LocalDensity.current.density
    val driftX = 7f * density * recallWave(sceneClock, 23f)
    val driftY = 4.5f * density * recallWave(sceneClock, 29f, .12f)
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    val rainModel = remember(seasonTerm) { if (seasonTerm == "雨水") RainModel() else null }
    var rainFrame by remember(seasonTerm) { mutableStateOf(RecallRainFrame()) }
    LaunchedEffect(rainModel, ambientRunning, reduceMotion, bounds, density) {
        val model = rainModel ?: return@LaunchedEffect
        val w = bounds.width / density
        val h = bounds.height / density
        if (w <= 1f || h <= 1f) return@LaunchedEffect
        if (reduceMotion) {
            rainFrame = recallRainFrame(model, w, h, density, true)
            return@LaunchedEffect
        }
        if (!ambientRunning) return@LaunchedEffect
        var last = SystemClock.uptimeMillis()
        while (true) {
            delay(33)
            val now = SystemClock.uptimeMillis()
            model.resize(w, h)
            model.step((now-last).coerceIn(0L, 55L) / 1000f, w, h)
            last = now
            rainFrame = recallRainFrame(model, w, h, density, false)
        }
    }
    val haloTexture = rememberAsyncImagePainter("assets://common/recall/smooth-halo.png")
    Box(modifier.onGloballyPositioned { bounds = it.size }) {
        val w = bounds.width.toFloat()
        val h = bounds.height.toFloat()
        val lens = atlasLens(position, w, h)
        if (lens.radius > 0f && seasonTerm != "秋分") {
            RecallAtmosphere(lens, sceneClock, ink, haloTexture, w, h)
        }
        val aboveAtlas = seasonTerm == "白露" || seasonTerm == "芒种" || seasonTerm == "大雪"
        if (seasonTerm != null && seasonTerm != "雨水" && !aboveAtlas) {
            RecallSeasonalUnderlay(seasonTerm, position, reduceMotion, ambientRunning)
        }
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = driftX; translationY = driftY }) {
            if (lens.radius > 0f) {
                RecallConstellationHalos(lens, sceneClock, ink, haloTexture, w, h, density)
            }
            RecallHistoricAtlas(position, ink, Modifier.fillMaxSize(), rainFrame.panes, Offset(driftX, driftY))
            if (lens.radius > 0f) {
                RecallGlints(lens, sceneClock, ink, haloTexture, w, h, density)
            }
        }
        if (seasonTerm != null && aboveAtlas) {
            RecallSeasonalUnderlay(seasonTerm, position, reduceMotion, ambientRunning)
        }
        if (rainModel != null) {
            Canvas(Modifier.fillMaxSize()) { drawRecallRain(rainFrame, position) }
        }
        RecallRecordMarks(position, summary, trace, ink, Modifier.fillMaxSize())
    }
}

/** Kuikly has no radial brush. Use a single dithered, continuous-alpha mask, never concentric fills. */
@Composable
private fun RecallGlow(
    painter: Painter, center: Offset, radius: Float, ink: Color, opacity: Float,
    width: Float, height: Float, sprite: Dp? = null, density: Float = 1f,
) {
    val base = sprite?.value?.times(density)
    val imageW = base?.coerceAtMost(width) ?: width
    val imageH = base?.coerceAtMost(height) ?: height
    val geometry = if (sprite == null) Modifier.fillMaxSize() else Modifier.size(sprite)
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        alpha = opacity,
        colorFilter = ColorFilter.tint(ink),
        modifier = geometry.graphicsLayer {
            translationX = center.x - imageW * .5f
            translationY = center.y - imageH * .5f
            scaleX = 2f * radius / imageW
            scaleY = 2f * radius / imageH
        },
    )
}

@Composable
private fun RecallAtmosphere(
    lens: RecallLens, clock: Float, ink: RecallInk, painter: Painter, width: Float, height: Float,
) {
    val density = LocalDensity.current.density
    val washA = Offset(lens.center.x + 17f * density * recallWave(clock, 37f, .30f),
        lens.center.y + 13f * density * recallWave(clock, 41f, .61f))
    val washB = Offset(lens.center.x - .45f * lens.radius + 12f * density * recallWave(clock, 31f, .22f),
        lens.center.y + .60f * lens.radius + 14f * density * recallWave(clock, 43f, .35f))
    val radiusA = lens.radius * 1.69f
    val radiusB = lens.radius * 1.38f
    if (Build.VERSION.SDK_INT >= 33) {
        val geometry = listOf(width, height, washA.x, washA.y, radiusA, washB.x, washB.y, radiusB)
        val packet = geometry.joinToString(",") +
            ",${ink.background.recallRgb()},${ink.glow.recallRgb()},${ink.second.recallRgb()}"
        MakeKuiklyComposeNode(
            factory = { RecallAtmosphereNode() },
            modifier = Modifier.fillMaxSize(),
            viewUpdate = { node -> node.attr { frame(packet) } },
        )
    } else {
        // Preserve the existing asset renderer on older Android versions.
        RecallGlow(painter, washA, radiusA, ink.glow, .20f, width, height)
        RecallGlow(painter, washB, radiusB, ink.second, .095f, width, height)
    }
}

private fun Color.recallRgb(): Int =
    ((red * 255f).roundToInt() shl 16) or ((green * 255f).roundToInt() shl 8) or (blue * 255f).roundToInt()

private class RecallAtmosphereAttr : Attr() {
    fun frame(packet: String) {
        "recallAtmosphere" with packet
        touchEnable(false)
    }
}

private class RecallAtmosphereNode : DeclarativeBaseView<RecallAtmosphereAttr, Event>() {
    override fun createAttr() = RecallAtmosphereAttr()
    override fun createEvent() = Event()
    override fun viewName() = "XLRRecallAtmosphere"
}

/** A mounted term owns only a pointer-transparent full-screen effect behind the existing chart. */
@Composable
private fun RecallSeasonalUnderlay(term: String, position: Float, reduced: Boolean, moving: Boolean) {
    val packet = "$term,$position,$reduced,$moving"
    MakeKuiklyComposeNode(
        factory = { RecallSeasonalNode() },
        modifier = Modifier.fillMaxSize(),
        viewUpdate = { node -> node.attr { frame(packet) } },
    )
}

private class RecallSeasonalAttr : Attr() {
    fun frame(packet: String) {
        "recallSeasonalFrame" with packet
        touchEnable(false)
    }
}
private class RecallSeasonalNode : DeclarativeBaseView<RecallSeasonalAttr, Event>() {
    override fun createAttr() = RecallSeasonalAttr()
    override fun createEvent() = Event()
    override fun viewName() = "XLRRecallSeasonal"
}

@Composable
private fun RecallHistoricAtlas(position: Float, ink: RecallInk, modifier: Modifier, panes: List<RecallRainPane>, drift: Offset) {
    Canvas(modifier) {
        val lens = atlasLens(position, size.width, size.height)
        if (lens.radius <= 0f) return@Canvas
        // No radial graticule or rings: keep only actual catalog points and constellation edges.
        for (edge in atlasEdges) {
            val color = if (edge.rank > 0) ink.accent else ink.second
            val tint = color.copy(alpha = if (edge.rank == 2) .70f else if (edge.rank == 1) .34f else .25f)
            val width = (if (edge.rank == 2) 1f else .62f).dp.toPx()
            val a = lens.at(edge.a)
            val b = lens.at(edge.b)
            if (panes.isNotEmpty() && recallRainTouches(a+drift, b+drift, panes)) {
                val path = Path()
                recallWetLine(a, b, drift, panes, density) { p, first ->
                    if (first) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(path, color = tint, style = Stroke(width = width, cap = StrokeCap.Round))
            } else drawLine(tint, a, b, strokeWidth = width, cap = StrokeCap.Round)
        }
        for (star in recallAtlasStars.values) {
            val point = lens.at(star)
            val p = if (panes.isEmpty()) point else recallWetPoint(point + drift, panes) - drift
            if (p.x < 0f || p.x > size.width || p.y < 0f || p.y > size.height) continue
            val brightness = ((5.7f - star.magnitude) / 6.2f).coerceIn(.1f, 1f)
            drawCircle(ink.second.copy(alpha = .21f + brightness * .52f),
                radius = (.48f + brightness * .89f).dp.toPx(), center = p)
        }
        // A single faint pole marker is not another archive star.
        drawCircle(ink.second.copy(alpha = .32f), 1.6.dp.toPx(), lens.center, style = Stroke(.5.dp.toPx()))
    }
}

@Composable
private fun RecallRecordMarks(
    position: Float,
    summary: RecallSummary,
    trace: RecallTrace,
    ink: RecallInk,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val index = position.toInt().coerceIn(0, 1)
        val t = (position - index).coerceIn(0f, 1f)
        val w = size.width
        val h = size.height
        fun screen(p: Offset) = Offset(p.x * w, p.y * h)
        val dayC = Offset(w * .49f, h * .71f)
        val dayR = min(w * .30f, h * .19f)
        fun phase(day: Int) = -PI / 2 + day * 2 * PI / max(1, summary.dayCount)
        // Keep the same calendar positions, but no clock/tick wheel on top of the records.
        fun pack(center: Offset, rank: Int, count: Int, extent: Float) = polarRecall(
            center, if (count < 2) 0f else extent * sqrt(rank.toFloat() / (count - 1)), rank * 2.399963,
        )
        for (dot in trace.points) {
            val day = polarRecall(dayC, dayR * (1f + (dot.dayIndex % 6) * .105f),
                phase(dot.day) + (dot.dayIndex / 6) * .08)
            val cluster = pack(screen(clusterAnchors[dot.cluster]), dot.clusterIndex,
                dot.clusterCount, min(w, h) * .045f)
            val status = pack(screen(statusAnchors[dot.status]), dot.statusIndex,
                dot.statusCount, min(w, h) * .093f)
            val p = if (index == 0) lerpRecall(day, cluster, t) else lerpRecall(cluster, status, t)
            val pending = dot.status == 1
            val final = (position - 1f).coerceIn(0f, 1f)
            val fill = if (pending) .90f - final * .83f else .90f
            drawCircle(ink.accent.copy(alpha = fill * .15f), 4.2.dp.toPx(), p)
            drawCircle(ink.accent.copy(alpha = fill), 1.8.dp.toPx(), p)
            if (pending && final > 0f)
                drawCircle(ink.second.copy(alpha = final * .8f), 2.5.dp.toPx(), p, style = Stroke(.8.dp.toPx()))
        }
    }
}


private data class ConstellationGlow(
    val center: RecallAtlasStar,
    val seed: Int,
    val extent: Float,
)
private val constellationGlows by lazy {
    val names = listOf("北斗", "紫微垣东蕃", "太微垣西蕃", "心宿", "昴宿", "轸宿")
    val groups = (recallAtlasMansions + recallAtlasEnclosures).associateBy { it.name }
    names.mapIndexedNotNull { z, name ->
        val stars = groups[name]?.lines?.flatMap { it.asList() }?.distinct()
            ?.mapNotNull(recallAtlasStars::get).orEmpty()
        if (stars.isEmpty()) return@mapIndexedNotNull null
        val x = stars.map { it.x }.average().toFloat()
        val y = stars.map { it.y }.average().toFloat()
        ConstellationGlow(RecallAtlasStar(0, x, y, 0f), stars.first().hip,
            if (z == 0) .091f else .066f + .008f * (z % 3))
    }
}

/** Individually drifting, diffuse optical accents, tethered within a few dp of each historical group. */
@Composable
private fun RecallConstellationHalos(
    lens: RecallLens, clock: Float, ink: RecallInk, painter: Painter, width: Float, height: Float, density: Float,
) {
    constellationGlows.forEachIndexed { i, glow ->
        val anchor = lens.at(glow.center)
        val (x, y) = recallLocalDrift(glow.seed, clock)
        val p = Offset(anchor.x + x * density, anchor.y + y * density)
        val color = if (i % 2 == 0) ink.accent else ink.second
        RecallGlow(painter, p, lens.radius * glow.extent * 2.15f, color,
            if (i == 0) .19f else .13f, width, height, sprite = 64.dp, density = density)
    }
}

private data class RecallFlare(val star: RecallAtlasStar, val pulse: RecallPulse)
private val recallFlares by lazy {
    recallAtlasStars.values.sortedBy { it.magnitude }.take(22).map { star ->
        // Stable, distinct phases/periods give the appearance of chance without frame-to-frame noise.
        RecallFlare(star, recallPulse(star.hip))
    }
}

@Composable
private fun RecallGlints(
    lens: RecallLens, clock: Float, ink: RecallInk, painter: Painter, width: Float, height: Float, density: Float,
) {
    for (flare in recallFlares) {
        val p = lens.at(flare.star)
        if (p.x !in -10f..width + 10f || p.y !in -10f..height + 10f) continue
        val glow = recallFlareEnvelope(flare.pulse, clock)
        RecallGlow(painter, p, (3.0f + glow * 3.7f) * density, ink.accent,
            .14f + glow * .63f, width, height, sprite = 18.dp, density = density)
    }
}
