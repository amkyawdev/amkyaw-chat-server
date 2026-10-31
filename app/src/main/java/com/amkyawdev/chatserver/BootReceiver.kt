package com.amkyawdev.chatserver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.amkyawdev.chatserver.util.LogHelper

/**
 * Restarts the server after a reboot when the user opted in via
 * "auto-start on boot".
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val app = context.applicationContext as? AmkyawApp ?: return
        if (!app.prefs.autoStartOnBoot) {
            LogHelper.d("boot ignored: auto-start disabled")
            return
        }
        LogHelper.i("boot completed · restarting server")
        ServerService.start(context)
    }
}
