package com.amkyawdev.chatserver.chat

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.amkyawdev.chatserver.util.LogHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * mDNS/DNS-SD advertisement + discovery for the local room.
 *
 * Advertises `_amkyaw._tcp` so other phones on the same WiFi can find the host
 * without typing an IP. Both operations are wrapped in retry loops: NSD
 * refuses requests that arrive while a previous one is still settling
 * (FAILURE_ALREADY_ACTIVE), which is common when the user toggles the server.
 */
class PeerDiscovery(
    context: Context,
    private val serviceName: String,
    private val port: Int,
    private val onFound: (NsdServiceInfo) -> Unit
) {

    private val nsdManager =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val scope = CoroutineScope(Job() + Dispatchers.Default)
    private var advertiseJob: Job? = null
    private var discoverJob: Job? = null

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    /** Publishes this device as a chat host, retrying while NSD is busy. */
    fun advertise() {
        val manager = nsdManager ?: run {
            LogHelper.w("NSD unavailable; skipping advertise")
            return
        }
        advertiseJob?.cancel()
        advertiseJob = scope.launch {
            val info = NsdServiceInfo().apply {
                this.serviceName = this@PeerDiscovery.serviceName
                this.port = this@PeerDiscovery.port
                setServiceType(SERVICE_TYPE)
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    LogHelper.i("advertised ${info.serviceName} on :$port")
                }

                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    LogHelper.w("advertise failed code=$errorCode")
                }

                override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            }
            registrationListener = listener

            repeat(RETRIES) { attempt ->
                if (!isActive) return@launch
                val started = runCatching {
                    manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
                }
                if (started.isSuccess) {
                    LogHelper.i("advertise requested (attempt ${attempt + 1})")
                    return@launch
                }
                LogHelper.w("registerService: ${started.exceptionOrNull()?.message}")
                delay(RETRY_DELAY_MS)
            }
        }
    }

    /** Starts browsing for other amkyaw hosts, retrying while NSD is busy. */
    fun discover() {
        val manager = nsdManager ?: return
        discoverJob?.cancel()
        discoverJob = scope.launch {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {
                    LogHelper.i("browsing $serviceType")
                }

                override fun onServiceFound(info: NsdServiceInfo) {
                    if (info.serviceType.contains(SERVICE_NAME)) onFound(info)
                }

                override fun onServiceLost(info: NsdServiceInfo) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    LogHelper.w("discovery failed code=$errorCode")
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            }
            discoveryListener = listener

            repeat(RETRIES) { attempt ->
                if (!isActive) return@launch
                val started = runCatching {
                    manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
                }
                if (started.isSuccess) {
                    LogHelper.i("discovery requested (attempt ${attempt + 1})")
                    return@launch
                }
                LogHelper.w("discoverServices: ${started.exceptionOrNull()?.message}")
                delay(RETRY_DELAY_MS)
            }
        }
    }

    /** Tears down both advertisement and discovery. */
    fun stop() {
        advertiseJob?.cancel()
        discoverJob?.cancel()
        val manager = nsdManager ?: return
        registrationListener?.let { runCatching { manager.unregisterService(it) } }
        discoveryListener?.let { runCatching { manager.stopServiceDiscovery(it) } }
        registrationListener = null
        discoveryListener = null
    }

    companion object {
        const val SERVICE_NAME = "amkyaw"
        const val SERVICE_TYPE = "_amkyaw._tcp."

        private const val RETRIES = 5
        private const val RETRY_DELAY_MS = 600L
    }
}
