package com.xlr.k.pages

import kotlin.random.Random

internal fun rainUnit(x: Float) = x.coerceIn(0f, 1f)
internal fun rainMix(a: Float, b: Float, x: Float) = a + (b - a) * x

internal class RainModel(private val rng: Random = Random.Default) {
    internal data class Fall(var x: Float, var y: Float, var speed: Float, val gx: Float, val r: Float, val quiet: Boolean, val door: Float, val seed: Float)
    internal data class Bead(
        val x: Float, val y: Float, val size: Float, val linger: Float, val drop: Float,
        val pause: Float, val quiet: Boolean, val tilt: Float, val shape: Float,
        var age: Float = 0f, var trace: Trace? = null,
    )
    internal data class Trace(val x: Float, val y: Float, var dx: Float, var dy: Float, val life: Float, val quiet: Boolean,
        val thickness: Float, var age: Float = 0f, var released: Boolean = false)
    internal data class BeadPose(val x: Float, val y: Float, val alpha: Float, val drag: Float)
    val falls = ArrayList<Fall>(19)
    val beads = ArrayList<Bead>(6)
    val traces = ArrayList<Trace>(10)
    private var wait = .06f
    private var width = 0f
    private var height = 0f
    fun resize(w: Float, h: Float) {
        if (width > 0f && height > 0f && (width != w || height != h)) {
            // Reset the decorative simulation on rotation; never stretch individual drops.
            falls.clear(); beads.clear(); traces.clear(); wait = .06f
        }
        width = w; height = h
    }
    private fun random(a: Float, b: Float) = rainMix(a, b, rng.nextFloat())
    fun step(delta: Float, w: Float, h: Float) {
        val dt = delta.coerceIn(0f, .055f)
        traces.removeAll { if (it.released) it.age += dt; it.age >= it.life }
        wait -= dt
        while (wait <= 0f) {
            if (falls.size < 19) {
                val zone = random(.075f, .97f)
                val quiet = zone > .31f && zone < .73f
                val hit = rng.nextFloat() < if (quiet) .20f else .28f
                falls.add(Fall(w * zone, -12f, random(295f, 560f), random(-17f, 6f), random(.85f, 1.92f),
                    quiet, if (hit) random(.22f, .74f) else 1.12f, rng.nextFloat()))
            }
            wait += random(.088f, .215f)
        }
        val fall = falls.iterator()
        while (fall.hasNext()) {
            val d = fall.next()
            d.speed += 78f * dt
            d.y += d.speed * dt
            d.x += d.gx * dt
            if (d.y < d.door * h) continue
            if (d.door < 1f && beads.size < 6) {
                val size = (d.r * 2.85f + random(.95f, 1.3f)).coerceIn(3.7f, 6.8f)
                val linger = random(1.7f, 4.05f)
                val slips = rng.nextFloat() < .48f
                beads.add(Bead(d.x.coerceIn(w * .063f, w * .978f), h * d.door, size, linger,
                    if (slips) random(21f, 43f) else 0f, random(.33f, 1.1f), d.quiet, random(-.34f, .30f), rng.nextFloat()))
            }
            fall.remove()
        }
        val living = beads.iterator()
        while (living.hasNext()) {
            val b = living.next()
            b.age += dt
            if (b.drop > 0f && b.age > b.pause + .055f && b.age < b.linger) {
                val p = pose(b)
                if (b.trace == null && p.y - b.y > 1.7f && traces.size < 10) {
                    b.trace = Trace(b.x, b.y, p.x, p.y, random(1.25f, 2.05f), b.quiet, random(.90f, 1.48f)).also { traces.add(it) }
                }
                b.trace?.let { if (p.y > it.dy + .025f) { it.dx = p.x; it.dy = p.y; it.age = 0f } }
            }
            if (b.age > b.linger) {
                b.trace?.released = true
                living.remove()
            }
        }
    }
    fun pose(b: Bead): BeadPose {
        val t = rainUnit((b.age - b.pause) / 1.03f)
        val ease = t * t * (3f - 2f * t)
        return BeadPose(b.x + b.tilt * ease * 2f, b.y + ease * b.drop,
            rainUnit((b.linger - b.age) / .66f) * rainUnit(b.age / .095f), ease)
    }
    fun still(w: Float, h: Float): List<Bead> {
        val data = arrayOf(floatArrayOf(.184f,.395f,4.0f,.32f), floatArrayOf(.438f,.567f,4.4f,.56f),
            floatArrayOf(.674f,.251f,3.9f,.74f), floatArrayOf(.859f,.716f,5.5f,.21f), floatArrayOf(.958f,.435f,4.7f,.28f))
        return data.map { (x, y, size, shape) -> Bead(x * w, y * h, size, 4f, 0f, .7f, x > .31f && x < .73f,
            shape - .5f, shape, age = 1f) }
    }
}
