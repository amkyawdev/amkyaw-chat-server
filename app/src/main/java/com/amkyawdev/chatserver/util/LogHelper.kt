package com.amkyawdev.chatserver.util

import android.util.Log

/**
 * Thin Log wrapper that prefixes every line with the terminal-style tag and
 * honours the [TAG] namespace so logcat can be filtered with
 * `adb logcat -s amkyaw`.
 */
object LogHelper {

    private const val TAG = "amkyaw"
    private var enabled = true

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun d(message: String) {
        if (enabled) Log.d(TAG, "[+] $message")
    }

    fun i(message: String) {
        if (enabled) Log.i(TAG, "[*] $message")
    }

    fun w(message: String) {
        if (enabled) Log.w(TAG, "[!] $message")
    }

    fun e(message: String, tr: Throwable? = null) {
        if (enabled) Log.e(TAG, "[#] $message", tr)
    }
}
