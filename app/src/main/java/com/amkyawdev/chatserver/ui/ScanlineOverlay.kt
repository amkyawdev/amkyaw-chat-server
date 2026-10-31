package com.amkyawdev.chatserver.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.amkyawdev.chatserver.R

/**
 * CRT scanline overlay: faint horizontal rules drawn over the content.
 *
 * Purely decorative and non-interactive, so it never consumes touches.
 */
class ScanlineOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply { isAntiAlias = false }
    private var spacing = 6f
    private var baseAlpha = 0.06f
    private var enabled = true

    init {
        attrs?.let { a ->
            val ta = context.obtainStyledAttributes(a, R.styleable.ScanlineOverlay)
            paint.color = ta.getColor(R.styleable.ScanlineOverlay_scanlineColor, Color.parseColor("#00FF41"))
            spacing = ta.getDimension(R.styleable.ScanlineOverlay_scanlineSpacing, 6f)
            baseAlpha = ta.getFloat(R.styleable.ScanlineOverlay_scanlineAlpha, baseAlpha)
            enabled = ta.getBoolean(R.styleable.ScanlineOverlay_scanlineEnabled, true)
            ta.recycle()
        }
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!enabled) return
        paint.alpha = (baseAlpha * 255).toInt().coerceIn(0, 255)
        var y = 0f
        while (y < height) {
            canvas.drawRect(0f, y, width.toFloat(), y + 1f, paint)
            y += spacing
        }
    }
}
