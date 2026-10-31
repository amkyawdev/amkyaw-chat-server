package com.amkyawdev.chatserver.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.amkyawdev.chatserver.R
import kotlin.random.Random

/**
 * Falling katakana "matrix rain" background.
 *
 * Columns of glyphs scroll downward with a bright head and a dimming tail.
 * Drawing is skipped while the view is detached so the effect costs nothing
 * in the background.
 */
class MatrixRainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val glyphs = "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789<>/\\|=+*#$%&"
    private val tailLength = 14

    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
    }

    private var rainColor = Color.parseColor("#00FF41")
    private var headColor = Color.parseColor("#6BFF9E")
    private var textSizePx = 42f
    private var density = 1.0f
    private var speed = 1.0f
    private var flicker = true

    private var columns = 0
    private var cellW = 0f
    private var cellH = 0f
    private var drops = FloatArray(0)
    private var active = BooleanArray(0)
    private val random = Random(System.currentTimeMillis())

    init {
        attrs?.let { a ->
            val ta = context.obtainStyledAttributes(a, R.styleable.MatrixRainView)
            rainColor = ta.getColor(R.styleable.MatrixRainView_rainColor, rainColor)
            headColor = ta.getColor(R.styleable.MatrixRainView_rainHeadColor, headColor)
            textSizePx = ta.getDimension(R.styleable.MatrixRainView_rainTextSize, textSizePx)
            density = ta.getFloat(R.styleable.MatrixRainView_rainDensity, density)
            speed = ta.getFloat(R.styleable.MatrixRainView_rainSpeed, speed)
            flicker = ta.getBoolean(R.styleable.MatrixRainView_rainFlicker, flicker)
            ta.recycle()
        }
        headPaint.textSize = textSizePx
        bodyPaint.textSize = textSizePx
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellW = (textSizePx * 0.85f).coerceAtLeast(1f)
        cellH = (textSizePx * 1.05f).coerceAtLeast(1f)
        columns = ((w / cellW).toInt() + 1).coerceAtLeast(1)
        drops = FloatArray(columns) { random.nextFloat() * -h.toFloat() / cellH }
        active = BooleanArray(columns) { random.nextFloat() < density.coerceIn(0.05f, 1f) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (drops.isEmpty()) return

        val lineGap = 2
        for (c in 0 until columns) {
            if (!active[c]) continue
            val head = drops[c].toInt()
            val x = c * cellW
            for (t in 0 until tailLength) {
                val row = head - t
                if (row < 0) continue
                val y = row * cellH
                if (y > height) continue

                val g = glyphs[random.nextInt(glyphs.length)]
                if (t == 0) {
                    headPaint.color = headColor
                    headPaint.alpha = 255
                    canvas.drawText(g.toString(), x, y, headPaint)
                } else {
                    val alpha = (255 - (t * 255 / tailLength)).coerceIn(0, 255)
                    bodyPaint.color = rainColor
                    bodyPaint.alpha = if (flicker && t > tailLength / 2) {
                        (alpha * if (random.nextInt(10) == 0) 0.4f else 1f).toInt()
                    } else {
                        alpha
                    }
                    canvas.drawText(g.toString(), x, y, bodyPaint)
                }
            }

            // Advance the column; recycle to the top once it clears the view.
            drops[c] += (0.45f + random.nextFloat() * 0.55f) * speed * lineGap * 0.5f
            if (drops[c] * cellH > height + tailLength * cellH) {
                drops[c] = -random.nextInt(10).toFloat()
            }
        }
        postInvalidateOnAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) postInvalidateOnAnimation()
    }

    fun setRainDensity(value: Float) {
        density = value.coerceIn(0.05f, 1f)
        active = BooleanArray(columns) { random.nextFloat() < density }
        invalidate()
    }
}
