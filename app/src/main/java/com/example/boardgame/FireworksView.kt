package com.example.boardgame

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.compose.ui.geometry.CornerRadius
import androidx.core.view.updatePadding
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withRotation


data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var alpha: Int = 255,
    var color: Int,
    val radius: Float = 4f,
    val trail: MutableList<PointF> = mutableListOf(),
    val rotationOffset: Float = Random.nextFloat() * 360f,
    val isSpark: Boolean = Random.nextFloat() < 0.5f
    )

fun createBurst(centerX: Float, centerY : Float, color: Int, particleCount : Int = 40) : List<Particle>{
    val particles = mutableListOf<Particle>()
    for(i in 0 until particleCount) {
        val angle = (2 * Math.PI * i / particleCount) + Random.nextDouble(-0.08, 0.08)
        val speed = Random.nextFloat() * 9 + 5
        particles.add(Particle(centerX, centerY, vx= (cos(angle) * speed).toFloat() , vy= (sin(angle) * speed).toFloat(), color = color ))
    }
    return particles
}

class FireworksView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet?= null,
    defyStyleAttr: Int = 0
) : View(context, attrs, defyStyleAttr){
    private val particles = mutableListOf<Particle>()
    private val sparkPaint : Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint : Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint : Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val g : Float = 0.18f
    private val drag = 0.985f
    private val colors = listOf<Int>(
        "#FF1744".toColorInt(),
        "#FFEA00".toColorInt(),
        "#00E5FF".toColorInt(),
        "#FF4081".toColorInt(),
        "#76FF03".toColorInt(),
        "#FFFFFF".toColorInt(),
        "#FF9100".toColorInt()
    )

    init {
//        glowPaint.maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply{
        duration = 16
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { updatePrt() }
    }
    fun start() {
        particles.clear()
        animator.start()
        bursts()
    }
    fun stop() {
        if(animator.isRunning){
            animator.cancel()
            particles.clear()
            invalidate()
        }
    }
    fun isRunning() : Boolean {
        return animator.isRunning
    }

    private fun bursts() {
        postDelayed({
    if(animator.isRunning){
        val nBurst = if (Random.nextFloat() < 0.25f) 4 else 6
        repeat(nBurst) {
            val x = Random.nextFloat() * width
            val y = Random.nextFloat() * height * 0.45f + height * 0.05f
            particles.addAll(createBurst(x, y, colors.random(), (50..90).random()))
        }
        bursts()
    }
        }, Random.nextLong(400, 900))
    }
    private fun updatePrt() {
        val iterator = particles.iterator()
        while(iterator.hasNext()) {
            val p = iterator.next()
            p.trail.add(PointF(p.x, p.y))
            if (p.trail.size > 8)
                p.trail.removeAt((0))

            p.vx *= drag
            p.vy *= drag
            p.x += p.vx
            p.y += p.vy
            p.vy += g
            p.alpha -= 4
            if (p.alpha <= 0)
                iterator.remove()
        }
        invalidate()
    }
    private fun drawFakeGlow(canvas: Canvas, p: Particle) {
        glowPaint.color = p.color
        glowPaint.alpha = (p.alpha * 0.25f).toInt().coerceIn(0, 255)
        canvas.drawCircle(p.x, p.y, p.radius * 3f, glowPaint)
        glowPaint.alpha = (p.alpha * 0.4f).toInt()
        canvas.drawCircle(p.x, p.y, p.radius * 1.8f, glowPaint)
    }

    private fun starPath(cx: Float, cy: Float, outerR: Float) : Path{
        val path = Path()
        val innerR = outerR * 0.4f
        val points = 4
        for(i in 0 until points * 2) {
            val angle = Math.PI * i / points
            val r = if( i % 2 == 0) outerR else innerR
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if(i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }
    private fun drawTrail(canvas : Canvas, p : Particle){
        for((i, point) in p.trail.withIndex()) {
            val fade = (i + 1f) / p.trail.size
            trailPaint.color = p.color
            trailPaint.alpha = (p.alpha * fade * 0.6f).toInt().coerceIn(0, 255)
            canvas.drawCircle(point.x, point.y, p.radius * fade *  0.7f, trailPaint)
        }
    }

    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas)
        val trailCap = if(particles.size > 200) 3 else 6
        val useStars = particles.size < 250
        for(p in particles) {
            while (p.trail.size > trailCap)
                p.trail.removeAt(0)
            drawTrail(canvas, p)
            drawFakeGlow(canvas, p)
            glowPaint.color = p.color
            glowPaint.alpha = (p.alpha * 0.5f).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, p.radius, glowPaint)
            sparkPaint.color = p.color
            sparkPaint.alpha = p.alpha.coerceIn(0, 255)
            if(p.isSpark && useStars) {
                canvas.withRotation(p.rotationOffset, p.x, p.y) {
                    drawPath(starPath(p.x, p.y, p.radius * 2f), sparkPaint)
                }
            }
            else{
                canvas.drawCircle(p.x, p.y, p.radius, sparkPaint)
            }

        }
    }

}