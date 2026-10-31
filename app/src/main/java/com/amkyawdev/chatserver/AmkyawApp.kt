package com.amkyawdev.chatserver

import android.app.Application
import com.amkyawdev.chatserver.util.LogHelper
import com.amkyawdev.chatserver.util.PrefsHelper

/**
 * Process-wide wiring: preferences, logging, and the server manager instance
 * that the activities and the foreground service share.
 */
class AmkyawApp : Application() {

    lateinit var prefs: PrefsHelper
        private set

    lateinit var serverManager: ServerManager
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsHelper(this)
        serverManager = ServerManager(this, prefs)
        LogHelper.i("amkyaw kernel up · pid=${android.os.Process.myPid()}")
    }
}
