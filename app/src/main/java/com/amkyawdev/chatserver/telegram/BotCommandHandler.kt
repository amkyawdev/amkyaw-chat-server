package com.amkyawdev.chatserver.telegram

import com.amkyawdev.chatserver.chat.ChatRoom
import com.amkyawdev.chatserver.util.LogHelper

/**
 * Handles the bot's slash commands.
 *
 * Commands return a reply string; message commands return null because the
 * text is injected into the room by the caller. Keeping the reply as a return
 * value (rather than calling the API) makes the handler trivially testable.
 */
class BotCommandHandler(
    private val room: ChatRoom,
    private val hostName: () -> String,
    private val endpoint: () -> String
) {

    /** @return the reply text, or null when the input is a room message. */
    fun handle(sender: String, text: String): String? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("/")) return null

        val command = trimmed.substringBefore(' ').lowercase().substringAfter('/')
        val args = trimmed.substringAfter(' ', "").trim()

        return when (command) {
            "start" -> welcome()
            "help" -> welcome()
            "status" -> status()
            "ip" -> "[+] IP : ${endpoint()}"
            "whoami" -> "[+] you are @$sender"
            "peers" -> "[+] host: ${hostName()}"
            "clear" -> {
                room.clear()
                "[ OK ] transcript cleared"
            }
            "echo" -> if (args.isEmpty()) "[!] usage: /echo <text>" else "[+] $args"
            else -> "[!] unknown command: /$command"
        }.also { LogHelper.d("telegram cmd /$command from @$sender") }
    }

    private fun welcome(): String = buildString {
        appendLine("[ OK ] amkyaw server · telegram relay ONLINE")
        appendLine("[+] host : ${hostName()}")
        appendLine("[+] ip   : ${endpoint()}")
        appendLine("─────")
        appendLine("Any message you send here is relayed to the LAN room.")
        appendLine("/status /ip /whoami /peers /clear /echo")
    }

    private fun status(): String = buildString {
        appendLine("[+] STATE : ONLINE")
        appendLine("[+] HOST  : ${hostName()}")
        appendLine("[+] IP    : ${endpoint()}")
        append("[+] MSGS  : ${room.messages.value.size}")
    }
}
