package com.xlr.k.recall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.tencent.kuikly.core.render.android.expand.component.KRView
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Decorative, touch-transparent underlay; no astronomy or record payload crosses this bridge. */
class RecallSeasonalView(context: Context) : KRView(context) {
    private val layer = SeasonalSurface(context)
    init {
        addView(layer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
    override fun setProp(propKey: String, propValue: Any): Boolean {
        if (propKey != "recallSeasonalFrame") return super.setProp(propKey, propValue)
        layer.update(propValue.toString())
        return true
    }
}

private class SeasonalSurface(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val rect = RectF()
    private val path = Path()
    private val shaderMatrix = Matrix()
    private var term = ""
    private var camera = 0f
    private var reduced = false
    private var moving = false
    private var age = 0f
    private var last = 0L
    private val gallery = RecallSeasonalGallery(context)
    private val frost by lazy { frostCuts.associate { it.name to weatherBitmap(context, it.name)?.let { image -> cutFrost(image, it) } } }

    fun update(packet: String) {
        val fields = packet.split(',')
        if (fields.size != 4) return
        val p = fields[1].toFloatOrNull()?.takeIf { it.isFinite() } ?: return
        if (term != fields[0]) {
            term = fields[0]
            age = 0f
            last = 0L
            gallery.prepare(term) { invalidate() }
        }
        camera = p
        reduced = fields[2] == "true"
        val shouldMove = fields[3] == "true"
        if (shouldMove != moving || reduced) last = 0L
        moving = shouldMove
        invalidate()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); last = 0L; invalidate() }
    override fun onDetachedFromWindow() { last = 0L; super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        last = 0L
        if (visibility == VISIBLE) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0 || !isShown) { last = 0L; return }
        val w = width / density
        val h = height / density
        val next = SystemClock.elapsedRealtimeNanos()
        val dt = if (moving && !reduced && last > 0L) ((next - last) / 1e9).toFloat().coerceIn(0f, .055f) else 0f
        last = if (moving && !reduced) next else 0L
        age += dt
        canvas.save()
        canvas.scale(density, density)
        when (term) {
            "秋分" -> drawEquinox(canvas, w, h)
            "霜降" -> drawFrost(canvas, w, h)
            else -> gallery.draw(canvas, term, w, h, age, camera, reduced)
        }
        canvas.restore()
        val animate = term == "秋分" && age < EQUINOX_SECONDS || term == "霜降" && age < 3.14f ||
            age < (gallerySeconds[term] ?: 0f)
        if (animate && moving && !reduced && windowVisibility == VISIBLE && isAttachedToWindow) postInvalidateOnAnimation()
    }

    private fun drawEquinox(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(EQUINOX_BASE or 0xFF000000.toInt())
        val progress = equinoxProgress(age, reduced)
        if (progress <= 0f) return
        val wasDither = paint.isDither
        paint.isDither = true
        // All four washes share viewport geometry and continuous radial falloff. No separate
        // foreground or contrast overlays: the scene uses the same pale UI over both sides.
        for (wash in equinoxWashes) {
            val colors = IntArray(equinoxFalloff.size) { level ->
                tinted(wash.rgb, wash.alpha * progress * equinoxFalloff[level])
            }
            paint.shader = RadialGradient(0f, 0f, 1f, colors, equinoxStops, Shader.TileMode.CLAMP).also {
                shaderMatrix.reset()
                shaderMatrix.setScale(wash.rx * w, wash.ry * h)
                shaderMatrix.postTranslate(wash.x * w, wash.y * h)
                it.setLocalMatrix(shaderMatrix)
            }
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        paint.shader = null
        paint.isDither = wasDither
    }

    private fun drawFrost(canvas: Canvas, w: Float, h: Float) {
        val brightness = mix(.77f, 1.03f, night(camera))
        val matrix = ColorMatrix().apply { setScale(brightness, brightness, brightness, 1f) }
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        for (cut in frostCuts) {
            val bitmap = frost[cut.name] ?: continue
            val place = frostPose(cut, w, h, age, camera, reduced)
            if (place.grow + cut.softness <= 0f) continue
            val horizontal = cut.name == "frost-side"
            val x = place.x; val y = place.y; val pw = place.w; val ph = place.h
            rect.set(x, y, x + pw, y + ph)
            val masked = place.grow < 1f
            val layer = if (masked) canvas.saveLayer(rect, null) else -1
            paint.alpha = (place.photoAlpha * 255f).roundToInt()
            canvas.drawBitmap(bitmap, null, rect, paint)
            if (masked) {
                val end = place.grow + cut.softness
                maskPaint.shader = if (horizontal)
                    LinearGradient(x + pw * place.grow, y, x + pw * end, y, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                else LinearGradient(x, y + ph * (1f - place.grow), x, y + ph * (1f - end), Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                canvas.drawRect(rect, maskPaint)
                canvas.restoreToCount(layer)
            }
        }
        paint.alpha = 255
        paint.colorFilter = null
    }


}

private fun weatherBitmap(context: Context, name: String): Bitmap? =
    runCatching { context.assets.open("common/recall/season/$name.webp").use { BitmapFactory.decodeStream(it) } }.getOrNull()
private fun tinted(rgb: Int, opacity: Float) = ((unit(opacity) * 255f).roundToInt() shl 24) or rgb

private fun fadeAt(x: Float, values: FloatArray): Float {
    if (x <= values[0]) return values[1]
    for (i in 2 until values.size step 2) {
        if (x <= values[i]) return mix(values[i - 1], values[i + 1], (x - values[i - 2]) / (values[i] - values[i - 2]))
    }
    return values.last()
}

/** Bake only the stationary eroded/muted parts of the v16 CSS masks; keep per-frame growth as a small local layer. */
private fun cutFrost(source: Bitmap, cut: FrostCut): Bitmap {
    val w = source.width; val h = source.height
    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            val u = (x + .5f) / w; val v = (y + .5f) / h
            val color = pixels[y * w + x]
            val alpha = unit(Color.alpha(color) / 255f * 1.07f - .042f)
            val radius = hypot((u - cut.maskX) / cut.maskRx, (v - cut.maskY) / cut.maskRy)
            val mask = fadeAt(radius, cut.radialStops) * (cut.sideStops?.let { fadeAt(u, it) } ?: 1f)
            val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
            val l = .213f * r + .715f * g + .072f * b
            pixels[y * w + x] = Color.argb((alpha * mask * 255f).roundToInt(),
                mix(l, r.toFloat(), .42f).roundToInt(), mix(l, g.toFloat(), .42f).roundToInt(),
                mix(l, b.toFloat(), .42f).roundToInt())
        }
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}
