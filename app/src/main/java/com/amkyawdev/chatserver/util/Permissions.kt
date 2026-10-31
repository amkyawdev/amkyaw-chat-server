package com.amkyawdev.chatserver.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Runtime permissions the server depends on.
 *
 * * `POST_NOTIFICATIONS` (API 33+) is what makes the foreground service
 *   notification visible. Without it the "listening on ..." notification is
 *   suppressed.
 * * `ACCESS_FINE_LOCATION` is required by [android.net.nsd.NsdManager] on
 *   API 24-32, so mDNS peer discovery fails silently without it.
 *
 * Neither is granted at install time, so the app has to ask.
 */
object Permissions {

    fun required(): Array<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.ACCESS_FINE_LOCATION
        }
        return list.toTypedArray()
    }

    fun missing(context: Context): Array<String> =
        required().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
}
