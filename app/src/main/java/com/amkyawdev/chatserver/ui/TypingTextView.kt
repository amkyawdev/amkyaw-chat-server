package com.amkyawdev.chatserver.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.TextView
import com.amkyawdev.chatserver.R

/**
 * TextView that reveals its text one character at a time, with a trailing
 * block cursor. Used for the boot log; [onComplete] fires once the last
 * character is shown.
 */
class TypingTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : TextView(context, attrs, defStyleAttr) {

    private val handler = Handler(Looper.getMainLooper())
    private var fullText: String = ""
    private var index = 0
    private var delayMs = 45L
    private var showCursor = true
    private var cursorChar = "_"
    private var cursorVisible = true

    private val cursorBlink = object : Runnable {
        override fun run() {
            cursorVisible = !cursorVisible
            render()
            handler.postDelayed(this, 500)
        }
    }

    var onComplete: (() -> Unit)? = null

    init {
        attrs?.let { a ->
            val ta = context.obtainStyledAttributes(a, R.styleable.TypingTextView)
            delayMs = ta.getInt(R.styleable.TypingTextView_typeDelay, delayMs.toInt()).toLong()
            showCursor = ta.getBoolean(R.styleable.TypingTextView_typeCursor, true)
            cursorChar = ta.getString(R.styleable.TypingTextView_typeCursorChar) ?: "_"
            ta.recycle()
        }
    }

    /** Starts the typewriter for [text], cancelling any previous run. */
    fun typeText(text: String) {
        handler.removeCallbacksAndMessages(null)
        fullText = text
        index = 0
        cursorVisible = true
        handler.postDelayed(step, delayMs)
        handler.postDelayed(cursorBlink, 500)
    }

    private val step = object : Runnable {
        override fun run() {
            if (index < fullText.length) {
                index++
                render()
                handler.postDelayed(this, delayMs)
            } else {
                onComplete?.invoke()
            }
        }
    }

    private fun render() {
        val shown = fullText.take(index)
        text = if (showCursor) shown + if (cursorVisible) cursorChar else " " else shown
    }

    /** True once the full string has been revealed. */
    fun isFinished(): Boolean = index >= fullText.length

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }
}
