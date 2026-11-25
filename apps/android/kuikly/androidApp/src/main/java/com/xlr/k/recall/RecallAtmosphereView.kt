package com.xlr.k.recall

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.tencent.kuikly.core.render.android.expand.component.KRView

/** Decorative background only; receive the existing scene state, never run a second animation clock. */
class RecallAtmosphereView(context: Context) : KRView(context) {
    private val atmosphere = if (Build.VERSION.SDK_INT >= 33) AtmosphereSurface(context) else View(context)

    init {
        addView(atmosphere, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false

    override fun setProp(propKey: String, propValue: Any): Boolean {
        if (propKey != "recallAtmosphere") return super.setProp(propKey, propValue)
        if (Build.VERSION.SDK_INT >= 33) (atmosphere as AtmosphereSurface).update(propValue.toString())
        return true
    }
}

@TargetApi(33)
private class AtmosphereSurface(context: Context) : View(context) {
    private val program = try {
        RuntimeShader(ATMOSPHERE_SHADER)
    } catch (error: IllegalArgumentException) {
        Log.w("XLRRecallSky", "Atmospheric shader unavailable; using the original wash asset", error)
        null
    }
    private val drawPaint = Paint().apply { shader = program }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fallbackBounds = RectF()
    private val fallbackMask: Bitmap? by lazy {
        runCatching { context.assets.open("common/recall/smooth-halo.png").use { BitmapFactory.decodeStream(it) } }
            .getOrNull()
    }
    private var frame: AtmosphereFrame? = null

    fun update(packet: String) {
        val fields = packet.split(',')
        if (fields.size != 11) return
        val v = FloatArray(8) { fields[it].toFloatOrNull() ?: return }
        if (v.any { !it.isFinite() } || v[0] <= 0f || v[1] <= 0f || v[4] <= 0f || v[7] <= 0f) return
        val next = AtmosphereFrame(v, fields[8].toIntOrNull() ?: return, fields[9].toIntOrNull() ?: return,
            fields[10].toIntOrNull() ?: return)
        frame = next
        if (program != null) {
            program.setFloatUniform("sourceSize", v[0], v[1])
            program.setFloatUniform("washA", v[2], v[3], v[4])
            program.setFloatUniform("washB", v[5], v[6], v[7])
            setRgb(program, "groundRgb", next.background)
            setRgb(program, "firstRgb", next.first)
            setRgb(program, "secondRgb", next.second)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val current = frame ?: return
        if (width <= 0 || height <= 0) return
        if (program != null && canvas.isHardwareAccelerated) {
            program.setFloatUniform("viewSize", width.toFloat(), height.toFloat())
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), drawPaint)
        } else {
            // GPU-disabled or shader compilation failure: keep the existing (un-dithered) visual.
            val mask = fallbackMask ?: return
            val v = current.geometry
            canvas.save()
            canvas.scale(width / v[0], height / v[1])
            drawWash(canvas, mask, v[2], v[3], v[4], current.first, 51)
            drawWash(canvas, mask, v[5], v[6], v[7], current.second, 24)
            canvas.restore()
        }
    }

    private fun drawWash(canvas: Canvas, mask: Bitmap, x: Float, y: Float, r: Float, ink: Int, alpha: Int) {
        fallbackBounds.set(x - r, y - r, x + r, y + r)
        bitmapPaint.alpha = alpha
        bitmapPaint.colorFilter = PorterDuffColorFilter(ink or 0xFF000000.toInt(), PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(mask, null, fallbackBounds, bitmapPaint)
    }

    private fun setRgb(shader: RuntimeShader, uniform: String, rgb: Int) {
        // Inputs are sRGB codes like the original skin colors; Android's standard window is sRGB.
        shader.setFloatUniform(uniform, ((rgb ushr 16) and 255) / 255f, ((rgb ushr 8) and 255) / 255f,
            (rgb and 255) / 255f)
    }
}

private data class AtmosphereFrame(val geometry: FloatArray, val background: Int, val first: Int, val second: Int)

private const val ATMOSPHERE_SHADER = """
    uniform float2 sourceSize;
    uniform float2 viewSize;
    uniform float3 washA;
    uniform float3 washB;
    uniform float3 groundRgb;
    uniform float3 firstRgb;
    uniform float3 secondRgb;

    float halo(float2 point, float3 wash) {
        float2 unit = (point - wash.xy) / wash.z;
        // Continuous source curve of smooth-halo.png, before its 8-bit alpha sampling.
        return pow(max(0.0, 1.0 - dot(unit, unit)), 2.8);
    }

    half4 main(float2 physicalPixel) {
        float2 point = physicalPixel * (sourceSize / viewSize);
        float a = 0.20 * halo(point, washA);
        float b = 0.095 * halo(point, washB);
        if (a + b == 0.0) return half4(half3(groundRgb), 1.0);
        float3 color = mix(mix(groundRgb, firstRgb, a), secondRgb, b);
        // Jimenez's interleaved gradient noise: anchored to the viewport's physical pixels,
        // not to a wash center or to time. Use one threshold for RGB, on the opaque result.
        float2 pixel = floor(physicalPixel);
        float threshold = fract(52.9829189 * fract(dot(pixel, float2(0.06711056, 0.00583715))));
        float3 code = floor(clamp(color, 0.0, 1.0) * 255.0 + threshold);
        return half4(half3(code / 255.0), 1.0);
    }
"""
