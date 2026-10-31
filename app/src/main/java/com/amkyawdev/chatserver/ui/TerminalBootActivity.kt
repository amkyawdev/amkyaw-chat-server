package com.amkyawdev.chatserver.ui

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.amkyawdev.chatserver.MainActivity
import com.amkyawdev.chatserver.R
import com.amkyawdev.chatserver.databinding.ActivityBootBinding

/**
 * SCREEN 1 — Boot / splash.
 *
 * Types the boot log line by line over the matrix rain, then hands off to the
 * dashboard. The hand-off is scheduled on a fixed 3.5s budget so a slow
 * typewriter never strands the user on the splash.
 */
class TerminalBootActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBootBinding
    private var launched = false

    private val bootLines by lazy {
        listOf(
            R.string.boot_line_1,
            R.string.boot_line_2,
            R.string.boot_line_3,
            R.string.boot_line_4,
            R.string.boot_line_5,
            R.string.boot_line_6,
            R.string.boot_line_7
        ).map { getString(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBootBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bootCursor.startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.blink)
        )

        binding.bootLog.typeText(bootLines.joinToString("\n"))
        binding.bootLog.onComplete = { }

        binding.root.postDelayed({ launchDashboard() }, BOOT_BUDGET_MS)
    }

    private fun launchDashboard() {
        if (launched) return
        launched = true
        startActivity(Intent(this, MainActivity::class.java))
        // The fade is declared in Theme.AmkyawServer via windowAnimationStyle,
        // so no per-call transition override is needed.
        finish()
    }

    private companion object {
        const val BOOT_BUDGET_MS = 3500L
    }
}
