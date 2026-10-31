package com.amkyawdev.chatserver.telegram

import android.content.Context

/**
 * Reads the Telegram bot configuration from [PrefsHelper].
 *
 * The token is never logged; [maskedToken] exists for terminal readouts that
 * need to show *which* bot is bound without leaking the secret.
 */
class TelegramConfig(private val context: Context) {

    private val prefs = com.amkyawdev.chatserver.util.PrefsHelper(context)

    val token: String get() = prefs.botToken

    val isConfigured: Boolean get() = token.matches(TOKEN_PATTERN)

    val isEnabled: Boolean get() = prefs.botEnabled && isConfigured

    /** "123456789:AA…" — safe to print. */
    fun maskedToken(): String {
        val t = token
        if (t.isEmpty()) return "[NONE]"
        val id = t.substringBefore(':')
        return "$id:****"
    }

    fun save(token: String) {
        prefs.botToken = token
    }

    fun setEnabled(enabled: Boolean) {
        prefs.botEnabled = enabled
    }

    /**
     * Telegram chat the LAN room relays into.
     *
     * Learned from the first inbound message: the Bot API gives no way to
     * address a user until they have written to the bot at least once.
     */
    var relayChatId: Long
        get() = prefs.relayChatId
        set(value) {
            prefs.relayChatId = value
        }

    companion object {
        /** Bot tokens look like `123456789:AA...` (35+ chars after the colon). */
        private val TOKEN_PATTERN = Regex("^\\d{6,}:[A-Za-z0-9_-]{30,}$")
    }
}
