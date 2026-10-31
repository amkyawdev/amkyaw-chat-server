package com.amkyawdev.chatserver.chat

import com.amkyawdev.chatserver.util.LogHelper
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

/**
 * Outbound WebSocket link used to join another phone's room.
 *
 * Reconnect is intentionally caller-driven: the UI decides when to retry so
 * the terminal never silently re-establishes a link the user closed.
 */
class ChatClient(
    url: String,
    private val onMessage: (ChatMessage) -> Unit,
    private val onStateChange: (State) -> Unit
) : WebSocketClient(URI(url)) {

    enum class State { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

    init {
        connectionLostTimeout = 15
    }

    override fun onOpen(handshake: ServerHandshake) {
        LogHelper.i("linked to $uri")
        onStateChange(State.CONNECTED)
    }

    override fun onMessage(message: String) {
        ChatMessage.fromWire(message, System.nanoTime())?.let(onMessage)
    }

    override fun onClose(code: Int, reason: String, remote: Boolean) {
        LogHelper.d("link closed code=$code reason=$reason")
        onStateChange(State.DISCONNECTED)
    }

    override fun onError(ex: Exception) {
        LogHelper.e("client error: ${ex.message}", ex)
        onStateChange(State.FAILED)
    }

    /** Opens the link, reporting a failure state instead of throwing. */
    fun connectSafely() {
        onStateChange(State.CONNECTING)
        runCatching { connect() }
            .onFailure {
                LogHelper.e("connect failed: ${it.message}", it)
                onStateChange(State.FAILED)
            }
    }

    fun send(sender: String, text: String) {
        if (!isOpen) {
            LogHelper.w("send skipped: link not open")
            return
        }
        runCatching { send(ChatMessage(0, sender, text).toWire()) }
            .onFailure { LogHelper.w("send failed: ${it.message}") }
    }
}
