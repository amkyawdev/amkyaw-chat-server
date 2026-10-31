package com.amkyawdev.chatserver.chat

import com.amkyawdev.chatserver.util.LogHelper

/**
 * Bridges the LAN room and the Telegram relay.
 *
 * Messages that arrive over the LAN are forwarded to Telegram, and Telegram
 * replies are injected back into the LAN. Loops are prevented by tagging
 * relays with [MessageSource] and refusing to re-relay the same message id.
 */
class PeerRelay(
    private val sendToTelegram: (sender: String, text: String) -> Unit,
    private val sendToLan: (sender: String, text: String) -> Unit
) {

    private val relayed = ArrayDeque<Long>()

    /** LAN -> Telegram. Local and Telegram-origin messages are ignored. */
    fun relayLanToTelegram(message: ChatMessage) {
        if (message.source != MessageSource.LAN) return
        if (alreadyRelayed(message.id)) return
        sendToTelegram(message.sender, message.text)
    }

    /** Telegram -> LAN. */
    fun relayTelegramToLan(sender: String, text: String) {
        sendToLan(sender, text)
    }

    private fun alreadyRelayed(id: Long): Boolean {
        if (relayed.contains(id)) {
            LogHelper.d("relay loop suppressed for id=$id")
            return true
        }
        relayed.addLast(id)
        if (relayed.size > WINDOW) relayed.removeFirst()
        return false
    }

    private companion object {
        const val WINDOW = 200
    }
}
