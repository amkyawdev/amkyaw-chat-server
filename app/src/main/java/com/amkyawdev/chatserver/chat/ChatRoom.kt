package com.amkyawdev.chatserver.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory transcript shared by the LAN server, the Telegram relay, and the
 * chat UI. Holds a bounded window of recent messages.
 */
class ChatRoom(private val capacity: Int = MAX_MESSAGES) {

    private val sequence = AtomicLong(0)
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())

    /** Oldest-first transcript. Collect from a lifecycle-aware scope. */
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    /** Appends a remote (LAN or Telegram) message and returns it. */
    fun addRemote(sender: String, text: String, source: MessageSource): ChatMessage {
        val msg = ChatMessage(
            id = sequence.incrementAndGet(),
            sender = sender,
            text = text,
            source = source
        )
        append(msg)
        return msg
    }

    /** Appends a locally authored message and returns it. */
    fun addLocal(sender: String, text: String): ChatMessage {
        val msg = ChatMessage(
            id = sequence.incrementAndGet(),
            sender = sender,
            text = text,
            source = MessageSource.LOCAL,
            isMine = true
        )
        append(msg)
        return msg
    }

    private fun append(msg: ChatMessage) {
        _messages.value = (_messages.value + msg).let {
            if (it.size > capacity) it.takeLast(capacity) else it
        }
    }

    fun clear() {
        _messages.value = emptyList()
    }

    companion object {
        private const val MAX_MESSAGES = 500
    }
}
