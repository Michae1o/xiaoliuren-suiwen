package com.xlr.k.pages

import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.Path
import com.tencent.kuikly.compose.ui.graphics.StrokeCap
import com.tencent.kuikly.compose.ui.graphics.drawscope.DrawScope
import com.tencent.kuikly.compose.ui.graphics.drawscope.Stroke
import com.tencent.kuikly.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

internal data class RecallRainPane(val at: Offset, val rx: Float, val ry: Float, val opacity: Float, val seed: Float, val impact: Float)
internal data class RecallAirLine(val a: Offset, val b: Offset, val width: Float, val opacity: Float)
internal data class RecallWetFilm(val a: Offset, val b: Offset, val width: Float, val opacity: Float)
internal data class RecallRainFrame(val panes: List<RecallRainPane> = emptyList(),
    val fall: List<RecallAirLine> = emptyList(), val film: List<RecallWetFilm> = emptyList())

internal fun recallRainFrame(model: RainModel, w: Float, h: Float, density: Float, still: Boolean): RecallRainFrame {
    fun px(x: Float, y: Float) = Offset(x*density, y*density)
    val panes = (if (still) model.still(w, h) else model.beads).mapNotNull { b ->
        val pose = model.pose(b)
        if (pose.alpha <= .001f) return@mapNotNull null
        val e = sin(PI.toFloat()*pose.drag)
        val impact = sin(PI.toFloat()*rainUnit(b.age/.24f))*exp(-b.age*3.4f)
        RecallRainPane(px(pose.x, pose.y), b.size*density*(1f+.39f*impact-.23f*e),
            b.size*density*(.86f-.27f*impact+.54f*e),pose.alpha*(if (b.quiet) .66f else 1f),b.shape,impact)
    }
    if (still) return RecallRainFrame(panes)
    return RecallRainFrame(panes, model.falls.filter { it.y in 0f..h+1f }.map { d ->
        RecallAirLine(px(d.x-d.gx*.033f, d.y-rainMix(9f,25f,d.seed)),px(d.x,d.y+1f),
            d.r*.82f*density,if(d.quiet) .40f else .58f)
    }, model.traces.mapNotNull { t ->
        if (t.dy-t.y<1.6f) null else RecallWetFilm(px(t.x,t.y),px(t.dx,t.dy),t.thickness*density,
            rainUnit(1f-t.age/t.life).pow(1.7f)*(if(t.quiet) .65f else 1f))
    })
}

// Raster-free local refraction of decorative geometry; the original data
// marks are drawn after the weather layer and do not enter this mapping.
internal fun recallWetPoint(p: Offset, panes: List<RecallRainPane>): Offset {
    var d = Offset.Zero
    for (q in panes) {
        val v = p-q.at
        val x = v.x/q.rx
        val y = v.y/q.ry
        val r2 = x*x+y*y
        if (r2 >= 1f) continue
        val g = (1f-r2)
        val gain = q.opacity*g*g
        d += Offset((v.x*1.12f + q.rx*.21f*y)*gain, (v.y*.94f-q.ry*.13f*x)*gain)
    }
    return p+d
}
internal fun recallRainTouches(a: Offset, b: Offset, panes: List<RecallRainPane>): Boolean {
    val dx=b.x-a.x; val dy=b.y-a.y
    val norm=dx*dx+dy*dy
    return panes.any { q ->
        val t=if(norm>0f) rainUnit(((q.at.x-a.x)*dx+(q.at.y-a.y)*dy)/norm) else 0f
        abs(a.x+dx*t-q.at.x)<max(q.rx,q.ry)*1.12f && abs(a.y+dy*t-q.at.y)<max(q.rx,q.ry)*1.12f
    }
}
internal inline fun recallWetLine(a: Offset, b: Offset, drift: Offset, panes: List<RecallRainPane>,
    density: Float, plot: (Offset,Boolean)->Unit) {
    val dx=b.x-a.x; val dy=b.y-a.y
    val n=ceil(hypot(dx,dy)/(.9f*density)).toInt().coerceIn(1,384)
    for (i in 0..n) {
        val t=i.toFloat()/n
        val p=Offset(a.x+dx*t,a.y+dy*t)
        plot(recallWetPoint(p+drift,panes)-drift, i==0)
    }
}
private fun waterShape(p: RecallRainPane): Path {
    val points = List(20) { i ->
        val a = i * PI / 10
        val k = (1 + .073*sin(3*a+p.seed*6) + .049*cos(5*a+p.seed*3) + p.impact*.072*cos(2*a+1.9))
        val v = (sin(a)*k).toFloat()
        val u = (cos(a)*k).toFloat()*(1+max(v,0f)*.15f)
        Offset(p.at.x+p.rx*u,p.at.y+p.ry*v)
    }
    return Path().apply {
        val start = (points.last()+points.first())*.5f
        moveTo(start.x,start.y)
        for ((i,q) in points.withIndex()) {
            val next=(q+points[(i+1)%points.size])*.5f
            quadraticBezierTo(q.x,q.y,next.x,next.y)
        }
        close()
    }
}
internal fun DrawScope.drawRecallRain(frame: RecallRainFrame, position: Float) {
    val dark = abs(position - 1f)<.56f
    val ice = Color(if(dark) 0xFFC1D9E6 else 0xFF507789)
    val shine=Color(0xFFC1E5E9)
    for (d in frame.fall) {
        drawLine(Brush.linearGradient(listOf(ice.copy(alpha=0f),ice.copy(alpha=d.opacity)),d.a,d.b),
            d.a,d.b,strokeWidth=d.width,cap=StrokeCap.Round)
    }
    for (t in frame.film) {
        val d=t.b-t.a
        val film=Path().apply { moveTo(t.a.x,t.a.y); quadraticBezierTo(t.a.x-.6f.dp.toPx(),t.a.y+d.y*.54f,t.b.x,t.b.y) }
        drawPath(film,brush=Brush.linearGradient(listOf(ice.copy(alpha=0f),ice.copy(alpha=t.opacity*.30f),
            shine.copy(alpha=t.opacity*.06f)),t.a,t.b),style=Stroke(t.width,cap=StrokeCap.Round))
    }
    for (p in frame.panes) {
        val a=p.at
        val br=Brush.linearGradient(listOf(shine.copy(alpha=p.opacity*.24f),shine.copy(alpha=p.opacity*.025f),
             Color(0xFF091719).copy(alpha=p.opacity*.16f)),
            a-Offset(p.rx*.47f,p.ry*.81f),a+Offset(p.rx*.4f,p.ry*.86f))
        drawPath(waterShape(p),brush=br)
    }
}
