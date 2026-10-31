package com.amkyawdev.chatserver.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.amkyawdev.chatserver.R

/**
 * TextView that can render RGB-split "glitch" echoes behind its text and be
 * shaken on demand. The jitter itself uses the @anim/glitch animation; the
 * colour-split is drawn here because no XML primitive can do it.
 *
 * Extends [AppCompatTextView] rather than the framework TextView so tinting and
 * the app's theme attributes apply; AppCompat cannot substitute custom views.
 */
class GlitchTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var glitchColor = Color.parseColor("#FF0040")
    private var glitchOffset = 2f
    private var intensity = 1f
    private var glitchOn = false

    init {
        attrs?.let { a ->
            val ta = context.obtainStyledAttributes(a, R.styleable.GlitchTextView)
            glitchColor = ta.getColor(R.styleable.GlitchTextView_glitchColor, glitchColor)
            glitchOffset = ta.getDimension(R.styleable.GlitchTextView_glitchOffset, glitchOffset)
            intensity = ta.getFloat(R.styleable.GlitchTextView_glitchIntensity, intensity)
            ta.recycle()
        }
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        if (!glitchOn) {
            super.onDraw(canvas)
            return
        }
        val saved = currentTextColor
        setTextColor(glitchColor)
        canvas.save()
        canvas.translate(-glitchOffset * intensity, 0f)
        super.onDraw(canvas)
        canvas.restore()

        canvas.save()
        canvas.translate(glitchOffset * intensity, 0f)
        super.onDraw(canvas)
        canvas.restore()

        setTextColor(saved)
        super.onDraw(canvas)
    }

    /** Fires the shake animation and holds the colour-split for its duration. */
    fun glitch(durationMs: Long = 540L) {
        if (glitchOn) return
        glitchOn = true
        invalidate()
        startAnimation(android.view.animation.AnimationUtils.loadAnimation(context, R.anim.glitch))
        postDelayed({
            glitchOn = false
            invalidate()
        }, durationMs)
    }

    fun setGlitchIntensity(value: Float) {
        intensity = value.coerceIn(0f, 4f)
        invalidate()
    }
}
