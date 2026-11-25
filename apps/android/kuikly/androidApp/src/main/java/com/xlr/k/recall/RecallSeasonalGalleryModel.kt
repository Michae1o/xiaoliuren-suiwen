package com.xlr.k.recall

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Normalized/dp staging only. Photo bytes and all photo proportions are from the reviewed desktop material set. */
internal sealed interface SeasonalLayer
internal data class SeasonalMask(val x: Float, val y: Float, val rx: Float, val ry: Float, val plateau: Float = .28f)
internal data class SeasonalReveal(val fromBottom: Boolean, val stop: Float, val softness: Float)
internal data class SeasonalPhoto(
    val name: String, val x: Float, val y: Float, val width: Float, val height: Float, val alpha: Float,
    val rotate: Float = 0f, val scaleX: Float = 1f, val scaleY: Float = 1f,
    val pivotX: Float = .5f, val pivotY: Float = .5f, val brightness: Float = 1f, val saturation: Float = 1f,
    val mask: SeasonalMask? = null, val reveal: SeasonalReveal? = null, val refraction: Float = 0f,
    val phase: Float = 0f,
) : SeasonalLayer
internal data class SeasonalLight(
    val x: Float, val y: Float, val rx: Float, val ry: Float,
    val rgb: Int, val alpha: Float, val middleAlpha: Float = .32f, val middle: Float = .52f,
) : SeasonalLayer

internal val gallerySeconds = mapOf(
    "立春" to 1.53f, "惊蛰" to 3.45f, "春分" to 4.15f, "清明" to 2.8f,
    "谷雨" to 3.2f, "立夏" to 2.85f, "小满" to 3f, "芒种" to 1.69f,
    "夏至" to 4.05f, "小暑" to 4.25f, "大暑" to 4.65f, "立秋" to 4.9f,
    "处暑" to 4.6f, "白露" to 0f, "寒露" to 3.55f, "立冬" to 4.15f,
    "小雪" to 5.25f, "大雪" to 2.71f, "冬至" to 4.15f, "小寒" to 3.6f, "大寒" to 4.30f,
)
internal val galleryAboveAtlas = setOf("芒种", "白露", "大雪")
internal val galleryEdgeEnvelope = setOf("白露", "芒种", "大雪") // same v16 decorative-only bridge scopes

private val photoRatio = mapOf(
    "fibres" to 250f / 185f, "thunder-main" to 1460f / 459f, "swallows" to 1064f / 580f,
    "blossoms" to 786f / 686f, "ripples-main" to 850f / 220f, "ripples-ring" to 1057f / 272f,
    "canopy-main" to 885f / 690f, "canopy-far" to 404f / 347f, "grainfill-main" to 323f / 840f,
    "grainfill-far" to 260f / 714f, "awns" to 1050f / 656f, "lotus-cool" to 779f / 761f,
    "heat-haze" to 1440f / 208f, "turning-leaf" to 1192f / 929f, "cloudbreak" to 1200f / 367f,
    "grass-dew" to 1376f / 638f, "first-ice" to 1440f / 267f, "icicles" to 342f / 407f,
    "deep-ice" to 1480f / 265f,
)

private fun key(a: Float, vararg values: Float): Float {
    if (a <= 0f) return values[1]
    for (i in 2 until values.size step 2) if (a <= values[i]) {
        return mix(values[i-1], values[i+1], (a - values[i-2]) / (values[i] - values[i-2]))
    }
    return values.last()
}

private fun phase(a: Float, from: Float, to: Float = 1f) = unit((a - from) / (to - from))
private fun rad(deg: Float) = deg * (PI.toFloat() / 180f)
private val snowFlakes = arrayOf(
    floatArrayOf(7f,10f,1.5f,31f,3.8f,.02f), floatArrayOf(15f,29f,2.2f,28f,-2.8f,.18f),
    floatArrayOf(23f,3f,3f,39f,4.6f,.06f), floatArrayOf(31f,41f,1.8f,33f,4.9f,.32f),
    floatArrayOf(36f,20f,2.6f,30f,-2.5f,.19f), floatArrayOf(41f,8f,1.2f,46f,4.4f,.22f),
    floatArrayOf(48f,32f,2.1f,23f,3f,.11f), floatArrayOf(53f,12f,3f,43f,-4.7f,.29f),
    floatArrayOf(59f,27f,1.7f,25f,2.1f,.03f), floatArrayOf(65f,15f,2f,42f,3.9f,.26f),
    floatArrayOf(69f,41f,2f,31f,4.1f,.21f), floatArrayOf(75f,4f,3.3f,50f,-2.4f,.17f),
    floatArrayOf(82f,34f,1.4f,32f,4f,.31f), floatArrayOf(87f,14f,2.6f,48f,3f,.20f),
    floatArrayOf(94f,20f,1.4f,32f,-4.1f,.11f),
)

internal fun galleryFrame(term: String, w: Float, h: Float, age: Float, camera: Float, reduced: Boolean): List<SeasonalLayer> {
    val seconds = gallerySeconds[term] ?: return emptyList()
    if (w <= 0f || h <= 0f) return emptyList()
    val compact = w <= 690f || w <= h
    val a = if (reduced || seconds <= 0f) 1f else when (term) {
        "立春" -> cubicProgress(age / seconds, .18f, .78f, .31f, 1f)
        "芒种" -> cubicProgress(age / seconds, .27f, .09f, .39f, 1f)
        "大雪" -> cubicProgress(age / seconds, .19f, .68f, .23f, 1f)
        else -> cubicProgress(age / seconds, .30f, .12f, .35f, 1f)
    }
    val n = night(camera)
    val travel = unit(camera / 2f)
    fun extent(small: Float, lo: Float, hi: Float, large: Float, bigLo: Float, bigHi: Float) =
        if (compact) (small * w).coerceIn(lo, hi) else (large * w).coerceIn(bigLo, bigHi)
    fun p(name: String, width: Float, x: Float, y: Float, alpha: Float, right: Boolean = false,
          bottom: Boolean = false, rotating: Float = 0f, sx: Float = 1f, sy: Float = 1f,
          px: Float = .5f, py: Float = .5f, brightness: Float = 1f, saturation: Float = 1f,
          m: SeasonalMask? = null, reveal: SeasonalReveal? = null, refract: Float = 0f): SeasonalPhoto {
        val height = width / photoRatio.getValue(name)
        return SeasonalPhoto(name, if (right) w - x - width else x, if (bottom) h - y - height else y,
            width, height, unit(alpha), rotating, sx, sy, px, py, brightness, saturation, m, reveal, refract, a)
    }
    fun l(x: Float, y: Float, rx: Float, ry: Float, rgb: Int, alpha: Float, rest: Float = .32f, middle: Float = .52f) =
        SeasonalLight(x * w, y * h, rx * w, ry * h, rgb, unit(alpha), rest, middle)
    return when (term) {
        "立春" -> listOf(p("fibres", extent(.17f,65f,93f,.10f,74f,112f), (if (compact) -14f else -21f) + mix(-9f,0f,a),
            .37f*h + mix(3f,0f,a) - 2.3f * phase(camera,1f,2f), a*(.56f-.12f*n), brightness=1f-.18f*n,
            saturation=.8f, m=SeasonalMask(.04f,.50f,.68f,.49f,.12f)))
        "惊蛰" -> listOf(
            p("thunder-main", extent(.65f,183f,391f,.54f,252f,754f), w * -.042f + mix(13f,0f,a) - travel*7f,
                h * if (compact) -.01f else -.03f, mix(.34f,.59f,a)-.05f*n, brightness=mix(.74f,1.03f,n),
                saturation=.68f, m=SeasonalMask(.17f,.07f,.96f,.92f,.24f)),
            l(.14f,.045f,.30f,.23f,0xF9DCC1, key(a,0f,.07f,.2f,.11f,.38f,.34f,.55f,.21f,.76f,.08f,1f,.07f)))
        "春分" -> {
            val width=extent(.20f,73f,127f,.14f,81f,219f)
            val travelX=key(a,0f,-.39f,.18f,-.31f,.75f,-.09f,1f,.015f)*w
            val travelY=key(a,0f,.02f,.24f,-.008f,.58f,-.024f,1f,-.029f)*h
            val x=w*(if (compact) .57f else .55f)+travelX
            val y=h*(if (compact) .155f else .14f)+travelY
            val ratio=key(a,0f,.91f,.3f,.96f,1f,1f)
            listOf(l(.50f,.20f,.66f,.35f,0xF5D6A2,.09f+.027f*a),
                p("swallows",width,x,y,(if (reduced) .81f else .20f+.63f*a)*(1f-.05f*n),
                    sx=ratio,sy=ratio,brightness=1f-.04f*n,saturation=.64f))
        }
        "清明" -> listOf(
            p("blossoms", extent(.47f,153f,367f,.34f,210f,489f), w * if (compact) -.05f else -.06f,
                h*(if (compact) -.055f else -.13f)+mix(6f,0f,a), .53f+.22f*a-.11f*n,
                sx=-1f, brightness=.86f+.18f*n,saturation=.9f,m=SeasonalMask(.59f,.11f,.94f,.96f,.31f)),
            l(.14f+.077f*a,.115f,.20f,.105f,0xFFF1EA,key(a,0f,0f,.15f,0f,.37f,.24f,.53f,.30f,.72f,.09f,1f,0f)))
        "谷雨" -> {
            val spread=key(a,0f,.59f,.17f,.64f,.67f,1f,1f,1.12f)
            listOf(
                p("ripples-main",extent(.40f,107f,215f,.33f,145f,475f),w*(if(compact) .46f else .35f),
                    h*(if(compact) .02f else .011f),mix(.34f,.58f,a)-.11f*n,bottom=true,sx=mix(.85f,1f,travel),
                    px=.33f,py=.8f,brightness=mix(.76f,1.04f,n),saturation=.76f,m=SeasonalMask(.50f,.53f,.49f,.49f)),
                p("ripples-ring",extent(.58f,152f,298f,.42f,192f,599f),w*(if (compact) .01f else .03f),
                    h*.051f - 17f*n,key(a,0f,0f,.15f,.22f,.36f,.69f,.68f,.56f,1f,.40f)*(1f-.25f*n),bottom=true,sx=spread,sy=spread,
                    brightness=mix(.76f,1.04f,n),saturation=.76f,m=SeasonalMask(.50f,.5f,.48f,.47f)),
                l(.255f,.906f+17f*n/h+key(a,0f,-25f,.09f,-21f,.21f,0f,.3f,3f,1f,3f)/h,
                    4f/w,8.5f/h,0xECF4EE,key(a,0f,0f,.08f,0f,.13f,.34f,.22f,.52f,.34f,.02f,1f,0f)))
        }
        "立夏" -> listOf(
            p("canopy-far",extent(.35f,106f,231f,.24f,101f,362f),w*-.10f-mix(-18f,0f,a)-travel*9f,
                h*(if(compact) -.05f else -.11f),mix(.23f,.49f,a)-.09f*n,right=true,brightness=mix(.79f,1.04f,n),saturation=.78f,
                m=SeasonalMask(.82f,0f,.86f,.91f,.14f)),
            p("canopy-main",extent(.53f,156f,330f,.33f,258f,582f),w*-.04f+mix(13f,0f,a)-travel*12f,
                h*(if (compact) -.04f else -.09f)+sin(a*PI).toFloat()*2.7f,mix(.32f,.65f,a)-.14f*n,
                brightness=mix(.79f,1.04f,n),saturation=.78f,m=SeasonalMask(.24f,0f,.74f,.90f,.23f)))
        "小满" -> {
            val near=phase(a,.08f);val far=phase(a,.25f)
            val fw=(if(compact) (h*.24f).coerceIn(128f,197f) else (h*.3f).coerceIn(144f,258f))*photoRatio.getValue("grainfill-far")
            val nw=(if(compact) (h*.31f).coerceIn(174f,279f) else (h*.4f).coerceIn(185f,339f))*photoRatio.getValue("grainfill-main")
            listOf(
                p("grainfill-far",fw,w*(if(compact) .17f else .099f),h*(if(compact) -.032f else -.04f),(.49f-.12f*n)*far,bottom=true,
                    rotating=mix(4.2f,0f,far),px=.23f,py=.89f,brightness=mix(.75f,1.03f,n),saturation=.87f,m=SeasonalMask(.34f,.79f,.61f,.63f,.14f)),
                p("grainfill-main",nw,w*.012f+mix(-12f,0f,near)+travel*3f,h*(if(compact) -.034f else -.048f),(.71f-.16f*n)*near,
                    bottom=true,rotating=mix(-2.9f,.5f,near)+sin(near*PI).toFloat()*2.2f,px=.16f,py=.85f,
                    brightness=mix(.75f,1.03f,n),saturation=.87f,m=SeasonalMask(.22f,.76f,.77f,.75f,.17f)))
        }
        "芒种" -> listOf(p("awns",extent(.39f,125f,204f,.31f,170f,380f),w*(if(compact) -.018f else -.02f)-mix(11f,0f,a)
            + phase(camera,0f)*3.4f,h*(if(compact) .009f else 0f)-mix(5f,0f,a)-phase(camera,1f,2f)*2.5f,
            a*(.52f-.08f*n)+sin(a*PI).toFloat()*.08f,right=true,bottom=true,rotating=mix(2.1f,0f,a)-sin(a*PI).toFloat()*1.7f,
            px=.81f,py=.86f,m=SeasonalMask(.83f,.65f,.72f,.73f,.21f)))
        "夏至" -> {
            val reach=key(a,0f,.28f,.3f,.49f,.69f,.84f,1f,1.05f)
            listOf(l(.13f,.93f,reach,.90f,0xFFF5BF,.28f+.51f*a-.24f*n,.48f,.23f),
                l(.19f,.87f,reach,.19f,0xFFE2A9,.18f+.51f*a-.23f*n,.45f,.53f))
        }
        "小暑" -> listOf(p("lotus-cool",extent(.44f,160f,335f,.29f,208f,415f),w*(if(compact) -.04f else -.01f)-mix(10f,0f,a),
            h*(if(compact) .18f else .13f),.51f+.27f*a-.10f*n,right=true,bottom=true,
            rotating=key(a,0f,-5.5f,.19f,1.7f,.40f,-1.55f,.67f,.85f,1f,0f),px=.82f,py=.91f,
            brightness=.89f+.25f*n,saturation=.87f,m=SeasonalMask(.65f,.48f,.93f,.95f,.32f)))
        "大暑" -> {
            val width=extent(1.12f,320f,694f,.81f,388f,1610f)
            val right=w*(if(compact) -.12f else -.05f);val lower=h*(if(compact) .20f else .13f)
            listOf(
                p("heat-haze",width,right,lower,.39f+.34f*a-.07f*n,right=true,bottom=true,
                    brightness=.87f+.21f*n,saturation=.79f,m=SeasonalMask(.67f,.64f,.86f,.81f,.16f)),
                p("heat-haze",width,right-key(a,0f,0f,.17f,3.8f,.38f,-2.7f,.57f,2.8f,.82f,-1.2f,1f,0f),
                    lower-key(a,0f,0f,.17f,-1.9f,.38f,-.4f,.57f,-1.4f,.82f,-.4f,1f,0f),
                    key(a,0f,.015f,.19f,.49f,.42f,.26f,.60f,.39f,.8f,.15f,1f,.08f),right=true,bottom=true,
                    sy=key(a,0f,1f,.23f,1.027f,.43f,.99f,.60f,1.022f,.82f,1.005f,1f,1f),m=SeasonalMask(.57f,.39f,.79f,.62f,.15f),
                    refract=if(reduced) 0f else key(a,0f,0f,.16f,8.7f,.38f,3.4f,.59f,6.8f,.8f,1.8f,1f,0f)))
        }
        "立秋" -> {
            val fold=key(a,0f,-22f,.17f,4f,.32f,76f,.54f,150f,.78f,201f,1f,209f)
            val tilt=key(a,0f,15f,.30f,41f,.53f,11f,.75f,-23f,1f,-8f)
            listOf(p("turning-leaf",extent(.19f,55f,126f,.13f,79f,234f),
                w*((if(compact) .84f else .81f)+key(a,0f,.055f,.14f,-.03f,.39f,-.09f,.63f,-.065f,.84f,-.12f,1f,-.14f)),
                h*((if(compact) .115f else .12f)+key(a,0f,-.15f,.13f,-.07f,.34f,.05f,.60f,.16f,.84f,.29f,1f,.32f)),
                key(a,0f,.27f,.13f,.93f,.72f,.86f,1f,.82f), rotating=key(a,0f,-31f,.18f,-9f,.39f,22f,.59f,-17f,.8f,28f,1f,16f),
                sx=cos(rad(fold)),sy=cos(rad(tilt)), brightness=.90f+.18f*n,saturation=.82f-.12f*n))
        }
        "处暑" -> {
            val width=extent(.95f,310f,620f,.86f,420f,1850f)
            val left=w*(if(compact) -.05f else -.04f); val top=if (compact) .01f*h else 0f
            listOf(
                p("cloudbreak",width,left+key(a,0f,.06f,.32f,0f,.72f,-.03f,1f,-.05f)*width,top,
                    .47f+.23f*a+.03f*n,brightness=.84f+.23f*n,saturation=.92f-.12f*n,
                    m=SeasonalMask(.32f,.01f,key(a,0f,.35f,.48f,.60f,1f,.97f),.81f,.31f)),
                p("cloudbreak",width,left+mix(-.02f,.12f,a)*width,top,
                    key(a,0f,.61f,.23f,.51f,.67f,.16f,1f,.07f),brightness=1.07f,saturation=1.1f,
                    m=SeasonalMask(.57f,.62f,.73f,.49f,.27f)),
                l(.16f,.045f,.52f,.27f,0xFFD18F,key(a,0f,.44f,.42f,.28f,.78f,.12f,1f,.04f)))
        }
        "白露" -> {
            val dusk=key(camera,0f,0f,.15f,0f,.89f,1f,1.1f,1f,1.87f,0f,2f,0f)
            listOf(SeasonalPhoto("dew-mist-day",0f,0f,w,h,1f-dusk), SeasonalPhoto("dew-mist-night",0f,0f,w,h,dusk))
        }
        "寒露" -> {
            val width=extent(.57f,168f,354f,.38f,215f,680f);val right=w*(if(compact) -.09f else -.07f)
            val lower=h*(if(compact) .19f else .18f)
            listOf(
                p("grass-dew",width,right,lower,.51f+.24f*a+.04f*n,right=true,bottom=true,
                    brightness=.90f+.41f*n,saturation=.86f-.16f*n,m=SeasonalMask(.61f,.47f,.91f,.88f,.39f)),
                p("grass-dew",width,right,lower,key(a,0f,.06f,.12f,.11f,.39f,.38f,.63f,.27f,.86f,.18f,1f,.12f),right=true,bottom=true,
                    brightness=1.35f,saturation=.65f,m=SeasonalMask(mix(.20f,.82f,a),.38f,.25f,.58f,.08f)))
        }
        "立冬" -> listOf(p("first-ice",extent(.89f,290f,628f,.72f,340f,1350f),w*(if(compact) -.09f else -.05f),
            h*.14f,.35f+.40f*a+.02f*n,bottom=true,brightness=.93f+.24f*n,saturation=.67f,
            m=SeasonalMask(.10f,.67f,key(a,0f,.14f,.25f,.37f,1f,.96f),.81f,.15f)))
        "小雪" -> snowFlakes.mapIndexed { i, f ->
            val (x0,y0,r,descent,wind)=f
            val start=f[5];val step=phase(a,start)
            val fade=if(i%3==0) (.96f-(step-.38f)*.47f).coerceAtLeast(0f) else (1f-(step-.63f)*1.85f).coerceAtLeast(.28f)
            l((x0+step*wind+sin(step*PI*1.5).toFloat()*.82f)/100f,(y0+step*descent)/100f,
                r*1.01f/w,r*1.12f/h,if(n>.47f) 0xE9F5FF else 0x687D98,
                .92f*(.38f+r*.077f)*phase(step,0f,.23f)*fade*(1f+.12f*n),.81f,.27f)
        }
        "大雪" -> {
            val near=if (reduced) 1f else cubicProgress(age/2.32f,.19f,.68f,.23f,1f)
            val drift=phase(camera,0f)
            fun snow(p:Float, close:Boolean):SeasonalPhoto {
                val cw=w*(if(close) if(compact) 1.94f else 1.31f else if(compact) 1.69f else 1.08f)
                val ch=h*(if(close) if(compact) .61f else .68f else if(compact) .49f else .64f)
                val x = w - cw + w*(if(close) if(compact) .52f else .22f else if(compact) .48f else .15f)
                val y = h - ch - h*(if(close) if(compact) -.01f else -.04f else if(compact) .09f else .07f)
                return SeasonalPhoto("snow", x + cw*(mix(if(close) 21f else 11f,if(close) -3f else 0f,p)-drift*(if(close) 7f else 3f)+phase(camera,1f,2f)*2f)/100f,
                    y + ch*(mix(if(close)-6f else -3f,if(close)4f else 2f,p)+phase(camera,1f,2f)*(if(close)7f else 3f))/100f,
                    cw,ch,p*(if(close).74f else .43f)*(1f+.16f*n), mask=if(close)SeasonalMask(.47f,.85f,.83f,.66f,.25f)
                    else SeasonalMask(.54f,.93f,.76f,.63f,.24f))
            }
            listOf(l(1f,.51f,.41f,.70f,0xABBCD1,.21f+.07f*n),snow(a,false),snow(near,true))
        }
        "冬至" -> {
            val rise=key(a,0f,17f,.18f,14f,.50f,7f,.84f,0f,1f,-1f)/h
            val open=key(a,0f,.25f,.18f,.33f,.48f,.62f,.85f,.91f,1f,1f)
            listOf(l(.79f,.93f,.36f,.27f,0x61637C,.14f+.16f*a),
                l(.82f,.87f+rise,.51f*open,.13f,0xFFDD97,.12f+.47f*a+.06f*n),
                l(.78f,.89f+rise,.50f*open,.018f,0xFFE5A7,.19f+.58f*a-.06f*n,.62f,.28f))
        }
        "小寒" -> listOf(p("icicles",extent(.28f,96f,162f,.155f,105f,213f),w*(if(compact) -.039f else -.021f),
            h*(if(compact) -.019f else -.029f),.34f+.42f*a-.05f*n,right=true,
            brightness=.75f+.45f*n,saturation=.62f,m=SeasonalMask(.8f,0f,.81f,.89f,.20f),
            reveal=SeasonalReveal(false,key(a,0f,.31f,.2f,.44f,.58f,.73f,1f,1.07f),.21f)))
        "大寒" -> listOf(
            p("deep-ice",extent(.84f,276f,546f,.68f,380f,1358f),w*(if(compact) -.07f else -.04f),
                h*(if(compact) -.015f else -.04f),.39f+.32f*a-.06f*n,right=true,bottom=true,
                brightness=.86f+.32f*n,saturation=.75f,m=SeasonalMask(.76f,.99f,.74f,.86f,.24f),
                reveal=SeasonalReveal(true,key(a,0f,.26f,.16f,.40f,.56f,.79f,1f,1.08f),.29f)),
            l(.84f,.95f,.53f,.22f,0x83A6C4,(.37f+.32f*a-.14f*n)*.25f))
        else -> emptyList()
    }
}
