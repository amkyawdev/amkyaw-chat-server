package com.amkyawdev.chatserver.chat

/** Where a message entered the system. */
enum class MessageSource { LAN, TELEGRAM, LOCAL }

/**
 * One chat line.
 *
 * [id] is a monotonic local sequence so ordering survives relays; the wire
 * format is plain text (`sender\ttext`) to stay readable on a terminal.
 */
data class ChatMessage(
    val id: Long,
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val source: MessageSource = MessageSource.LAN,
    val isMine: Boolean = false
) {

    fun toWire(): String = "$sender\t$text"

    companion object {
        fun fromWire(raw: String, id: Long): ChatMessage? {
            val idx = raw.indexOf('\t')
            if (idx <= 0) return null
            val sender = raw.substring(0, idx).trim()
            val text = raw.substring(idx + 1)
            if (sender.isEmpty() || text.isEmpty()) return null
            return ChatMessage(id = id, sender = sender, text = text)
        }

        /** "14:07" — terminal-friendly clock stamp. */
        fun clock(ts: Long): String {
            val f = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
            return f.format(java.util.Date(ts))
        }
    }
}
