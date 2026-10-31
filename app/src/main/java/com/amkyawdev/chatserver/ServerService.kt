package com.amkyawdev.chatserver

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.content.getSystemService
import com.amkyawdev.chatserver.util.LogHelper

/**
 * Foreground service that keeps the LAN server and the Telegram relay alive
 * while the app is backgrounded. The notification mirrors the current
 * endpoint so the user can read the address without opening the app.
 */
class ServerService : Service() {

    private val manager: ServerManager
        get() = (application as AmkyawApp).serverManager

    private var notifications: NotificationHelper? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications = NotificationHelper(this)
        startForeground(NotificationHelper.NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                manager.stopTelegram()
                manager.stopServer()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                manager.startServer()
                if ((application as AmkyawApp).prefs.botEnabled) manager.startTelegram()
                refreshNotification()
            }
        }
        return START_STICKY
    }

    private fun buildNotification() =
        notifications?.build(manager.endpoint, manager.state.value.peerCount)

    private fun refreshNotification() {
        val n = buildNotification() ?: return
        getSystemService<NotificationManager>()?.notify(NotificationHelper.NOTIF_ID, n)
    }

    override fun onDestroy() {
        LogHelper.i("service destroyed")
        manager.stopTelegram()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.amkyawdev.chatserver.START"
        const val ACTION_STOP = "com.amkyawdev.chatserver.STOP"

        fun start(context: Context) {
            val intent = Intent(context, ServerService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            // stopService avoids the background-start restriction that
            // startService would hit when invoked from outside the app.
            context.stopService(Intent(context, ServerService::class.java))
        }
    }
}
