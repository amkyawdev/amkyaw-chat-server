package com.amkyawdev.chatserver.chat

import com.amkyawdev.chatserver.util.LogHelper
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.Collections

/**
 * WebSocket server bound to the phone's LAN address.
 *
 * Every accepted message is broadcast to all other peers and surfaced through
 * [onMessage] so the room and the Telegram relay can react to it.
 */
class ChatServer(
    port: Int,
    private val onMessage: (ChatMessage) -> Unit,
    private val onStateChange: (State) -> Unit
) : WebSocketServer(InetSocketAddress(port)) {

    enum class State { STOPPED, STARTING, ONLINE, ERROR }

    private val peers: MutableSet<WebSocket> =
        Collections.synchronizedSet(mutableSetOf<WebSocket>())

    val peerCount: Int get() = peers.size

    init {
        connectionLostTimeout = 15
        setReuseAddr(true)
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        peers.add(conn)
        LogHelper.d("peer link up: ${conn.remoteSocketAddress} (${peers.size} total)")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        peers.remove(conn)
        LogHelper.d("peer link down: ${conn.remoteSocketAddress} code=$code")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        val parsed = ChatMessage.fromWire(message, System.nanoTime())
            ?: run {
                LogHelper.w("dropped malformed frame")
                return
            }
        broadcast(message, exclude = conn)
        onMessage(parsed)
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        LogHelper.e("server error: ${ex.message}", ex)
        if (conn == null) onStateChange(State.ERROR)
    }

    override fun onStart() {
        LogHelper.i("bound port :$port · ONLINE")
        onStateChange(State.ONLINE)
    }

    /** Fans a frame out to every connected peer except [exclude]. */
    fun broadcast(raw: String, exclude: WebSocket? = null) {
        synchronized(peers) {
            peers.forEach { ws ->
                if (ws !== exclude && ws.isOpen) {
                    runCatching { ws.send(raw) }
                        .onFailure { LogHelper.w("send failed: ${it.message}") }
                }
            }
        }
    }

    fun shutdown() {
        onStateChange(State.STOPPED)
        peers.clear()
        // stop() joins the worker threads and would block the main thread.
        Thread({
            runCatching { stop(1000) }
                .onFailure { LogHelper.w("stop: ${it.message}") }
        }, "amkyaw-server-stop").start()
    }
}
