package com.xlr.k.recall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors
import kotlin.math.sin
import kotlin.math.roundToInt

/** Draw one current term. Gallery work never leaves the background canvas or updates during invisibility. */
internal class RecallSeasonalGallery(context: Context) {
    private val app = context.applicationContext
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val matrix = Matrix()
    private val colorMatrix = ColorMatrix()
    private val brightnessMatrix = ColorMatrix()
    private val rect = RectF()
    private val imageRect = RectF()
    private val mesh = FloatArray(10 * 9 * 2)
    private val main = Handler(Looper.getMainLooper())
    private var wanted = ""
    private var names = emptyList<String>()
    private val images = mutableMapOf<String, Bitmap>()

    fun prepare(term: String, onReady: () -> Unit) {
        if (wanted == term) return
        wanted = term
        names = galleryFrame(term, 390f, 844f, 9f, 0f, true).filterIsInstance<SeasonalPhoto>().map { it.name }.distinct()
        images.clear()
        if (names.isEmpty()) return
        val missing = names.filter { name ->
            val cached = bitmapCache.get(name)
            if (cached != null) images[name] = cached
            cached == null
        }
        if (missing.isEmpty()) return
        decodeQueue.execute {
            // Allocation/path checks happen only for this term and only when there is no cached bitmap.
            val loaded = missing.mapNotNull { name ->
                val bitmap = bitmapCache.get(name) ?: runCatching {
                    app.assets.open("common/recall/season/$name.webp").use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                            // Small botanical details use original pixels; bound only wide diffuse snow.
                            if (name == "snow") inSampleSize = 2
                        })
                    }
                }.getOrNull()?.also { bitmapCache.put(name, it) }
                bitmap?.let { name to it }
            }
            main.post {
                if (wanted == term) {
                    images.putAll(loaded)
                    onReady()
                }
            }
        }
    }

    fun draw(canvas: Canvas, term: String, w: Float, h: Float, age: Float, camera: Float, reduced: Boolean) {
        val items = galleryFrame(term, w, h, age, camera, reduced)
        if (items.isEmpty()) return
        // Preserve chart and trace. Only decorative surfaces carrying the v16 envelope go through this pass.
        val feather = term in galleryEdgeEnvelope
        val outer = if (feather) canvas.saveLayer(0f, 0f, w, h, null) else -1
        for (piece in items) when (piece) {
            is SeasonalLight -> drawLight(canvas, piece)
            is SeasonalPhoto -> drawPhoto(canvas, piece)
        }
        if (feather) {
            maskPaint.shader = LinearGradient(0f, 0f, 0f, h, edgeMask, edgePositions, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, maskPaint)
            canvas.restoreToCount(outer)
        }
    }

    private fun drawLight(canvas: Canvas, l: SeasonalLight) {
        if (l.alpha <= .002f || l.rx <= 0f || l.ry <= 0f) return
        paint.alpha = 255
        paint.colorFilter = null
        paint.shader = radial(l.x, l.y, l.rx, l.ry,
            intArrayOf(color(l.rgb, l.alpha), color(l.rgb, l.alpha * l.middleAlpha), l.rgb),
            floatArrayOf(0f, l.middle, 1f))
        canvas.drawRect(l.x-l.rx, l.y-l.ry, l.x+l.rx, l.y+l.ry, paint)
        paint.shader = null
    }

    private fun drawPhoto(canvas: Canvas, p: SeasonalPhoto) {
        val image = images[p.name] ?: return
        if (p.alpha <= .002f || p.width <= 0f || p.height <= 0f || p.scaleX in -.008f.. .008f) return
        val left=p.x;val top=p.y;val w=p.width;val h=p.height
        val pushed=canvas.save()
        val cx=left+w*p.pivotX;val cy=top+h*p.pivotY
        canvas.rotate(p.rotate,cx,cy)
        canvas.scale(p.scaleX,p.scaleY,cx,cy)
        rect.set(left,top,left+w,top+h)
        val separate = p.mask != null || p.reveal != null
        val photoLayer=if (separate) canvas.saveLayer(rect, null) else -1
        paint.alpha=(unit(p.alpha)*255f).roundToInt()
        paint.colorFilter = if (p.saturation == 1f && p.brightness == 1f) null else {
            colorMatrix.setSaturation(p.saturation)
            brightnessMatrix.setScale(p.brightness,p.brightness,p.brightness,1f)
            colorMatrix.postConcat(brightnessMatrix)
            ColorMatrixColorFilter(colorMatrix)
        }
        if (p.name=="snow") {
            // CSS object-fit: contain; bottom-aligned within the decorative frame.
            val scale=minOf(w/image.width,h/image.height)
            val drawW=image.width*scale;val drawH=image.height*scale
            imageRect.set(left+(w-drawW)*.5f,top+h-drawH,left+(w+drawW)*.5f,top+h)
        } else {
            imageRect.set(rect)
        }
        if (p.refraction > .01f && canvas.isHardwareAccelerated) {
            // Displace only this original heat-air photograph; base art, text and stars are never sampled.
            for (y in 0..8) for (x in 0..9) {
                val u=x/9f;val v=y/8f
                val keep=4f*u*(1f-u)*4f*v*(1f-v)
                val i=(y*10+x)*2
                mesh[i]=imageRect.left+u*imageRect.width()+keep*p.refraction*.56f*sin(28f*v+17f*u+p.phase*13f)
                mesh[i+1]=imageRect.top+v*imageRect.height()+keep*p.refraction*.24f*sin(76f*v-19f*u+p.phase*11f)
            }
            canvas.drawBitmapMesh(image,9,8,mesh,0,null,0,paint)
        } else canvas.drawBitmap(image,null,imageRect,paint)
        paint.alpha=255
        paint.colorFilter=null
        p.mask?.let { m ->
            maskPaint.shader=radial(left+w*m.x,top+h*m.y,w*m.rx,h*m.ry,maskColors,
                floatArrayOf(0f,m.plateau,(m.plateau+.32f).coerceAtMost(.89f),1f))
            canvas.drawRect(rect,maskPaint)
        }
        p.reveal?.let { r ->
            val dir=if(r.fromBottom)-1f else 1f
            val root=if(r.fromBottom)top+h else top
            maskPaint.shader=LinearGradient(left,root+dir*h*r.stop,left,root+dir*h*(r.stop+r.softness),
                0xff000000.toInt(),0x00000000,Shader.TileMode.CLAMP)
            canvas.drawRect(rect,maskPaint)
        }
        if (separate) canvas.restoreToCount(photoLayer)
        canvas.restoreToCount(pushed)
    }

    private fun radial(x: Float,y: Float,rx: Float,ry: Float, colors: IntArray, stops: FloatArray): RadialGradient {
        val gradient=RadialGradient(0f,0f,1f,colors,stops,Shader.TileMode.CLAMP)
        matrix.reset();matrix.setScale(rx,ry);matrix.postTranslate(x,y)
        gradient.setLocalMatrix(matrix)
        return gradient
    }

    private companion object {
        private val decodeQueue = Executors.newSingleThreadExecutor { r -> Thread(r,"Recall seasonal images").apply { isDaemon=true } }
        private val bitmapCache = object : LruCache<String, Bitmap>(12*1024*1024) {
            override fun sizeOf(key: String,value: Bitmap) = value.allocationByteCount
        }
        private val maskColors=intArrayOf(0xff000000.toInt(),0xff000000.toInt(),0xa8000000.toInt(),0)
        private val edgePositions=floatArrayOf(0f,.002f,.017f,.036f,.059f,.088f,.912f,.941f,.964f,.983f,.998f,1f)
        private val edgeMask=floatArrayOf(0f,0f,.11f,.39f,.78f,1f,1f,.78f,.39f,.11f,0f,0f).map{color(0,it)}.toIntArray()
        private fun color(rgb: Int, a: Float) = ((unit(a)*255f).roundToInt() shl 24) or rgb
    }
}
