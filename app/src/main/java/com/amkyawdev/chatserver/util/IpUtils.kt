package com.amkyawdev.chatserver.util

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

/** LAN address + username helpers. */
object IpUtils {

    /**
     * Best-effort IPv4 address of the device on the local network, or
     * "0.0.0.0" when no interface is up.
     */
    fun localIpv4(): String {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.filter { it.isUp && !it.isLoopback }
                ?.flatMap { it.inetAddresses.toList() }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
                ?: "0.0.0.0"
        } catch (t: Throwable) {
            LogHelper.w("localIpv4 failed: ${t.message}")
            "0.0.0.0"
        }
    }

    /** "192.168.1.5:8080" */
    fun localEndpoint(port: Int): String = "${localIpv4()}:$port"

    /** Last octet of the IPv4 address, used to build the default handle. */
    fun lastOctet(ip: String = localIpv4()): String =
        ip.split('.').lastOrNull()?.takeIf { it.isNotEmpty() } ?: "0"

    /** "User-5" — stable across launches for the same network address. */
    fun defaultUsername(context: Context): String {
        val suffix = lastOctet()
        val stored = context.getSharedPreferences("amkyaw_prefs", Context.MODE_PRIVATE)
            .getString("username", null)
        return stored ?: "User-$suffix"
    }

    /** Suppresses the "WiFi is off" lint path while keeping the check explicit. */
    @Suppress("DEPRECATION")
    fun isWifiEnabled(context: Context): Boolean {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wm?.isWifiEnabled == true
    }
}
