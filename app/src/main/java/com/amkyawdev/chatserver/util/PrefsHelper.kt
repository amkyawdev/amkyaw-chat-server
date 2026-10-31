package com.amkyawdev.chatserver.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Single access point for persisted settings.
 *
 * The Telegram bot token is the only genuinely sensitive value; it is stored
 * in the private app sandbox and excluded from cloud backup (see
 * res/xml/backup_rules.xml).
 */
class PrefsHelper(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var username: String
        get() = prefs.getString(KEY_USERNAME, null) ?: IpUtils.defaultUsername(context)
        set(value) = prefs.edit { putString(KEY_USERNAME, value.trim()) }

    var botToken: String
        get() = prefs.getString(KEY_BOT_TOKEN, "") ?: ""
        set(value) = prefs.edit { putString(KEY_BOT_TOKEN, value.trim()) }

    var botEnabled: Boolean
        get() = prefs.getBoolean(KEY_BOT_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_BOT_ENABLED, value) }

    var relayChatId: Long
        get() = prefs.getLong(KEY_RELAY_CHAT, 0L)
        set(value) = prefs.edit { putLong(KEY_RELAY_CHAT, value) }

    var serverPort: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = prefs.edit { putInt(KEY_PORT, value) }

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_BOOT, false)
        set(value) = prefs.edit { putBoolean(KEY_BOOT, value) }

    /** True once the boot splash has been shown at least once. */
    var firstRunDone: Boolean
        get() = prefs.getBoolean(KEY_FIRST_RUN, false)
        set(value) = prefs.edit { putBoolean(KEY_FIRST_RUN, value) }

    companion object {
        private const val PREFS = "amkyaw_prefs"
        private const val KEY_USERNAME = "username"
        private const val KEY_BOT_TOKEN = "bot_token"
        private const val KEY_BOT_ENABLED = "bot_enabled"
        private const val KEY_RELAY_CHAT = "relay_chat_id"
        private const val KEY_PORT = "server_port"
        private const val KEY_BOOT = "auto_start_boot"
        private const val KEY_FIRST_RUN = "first_run_done"

        const val DEFAULT_PORT = 8080
    }
}
