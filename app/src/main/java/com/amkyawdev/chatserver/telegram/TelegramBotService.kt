package com.amkyawdev.chatserver.telegram

import com.amkyawdev.chatserver.chat.ChatRoom
import com.amkyawdev.chatserver.chat.MessageSource
import com.amkyawdev.chatserver.util.LogHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Long-polling bridge between a Telegram chat and the LAN room.
 *
 * The loop tracks the highest `update_id` seen so restarts do not replay old
 * messages, and backs off on repeated failures instead of hammering the API.
 */
class TelegramBotService(
    private val scope: CoroutineScope,
    private val config: TelegramConfig,
    private val room: ChatRoom,
    private val handler: BotCommandHandler,
    private val onStateChange: (State, String) -> Unit
) {

    enum class State { STOPPED, CONNECTING, ONLINE, ERROR }

    private var pollJob: Job? = null
    private var offset = 0L

    /** Resolves the bot identity, registers commands, then starts polling. */
    fun start() {
        if (pollJob?.isActive == true) return
        if (!config.isConfigured) {
            onStateChange(State.ERROR, "[!] token missing or malformed")
            return
        }

        onStateChange(State.CONNECTING, "[*] handshaking with telegram...")
        val api = TelegramApi(config.token)

        pollJob = scope.launch {
            api.getMe()
                .onSuccess { username ->
                    LogHelper.i("relay bound to @$username")
                    onStateChange(State.ONLINE, "[ OK ] relay bound to @$username")
                }
                .onFailure {
                    onStateChange(State.ERROR, "[!] auth rejected · check token")
                    return@launch
                }

            api.setMyCommands(COMMANDS)
            config.setEnabled(true)
            pollLoop(api)
        }
    }

    private suspend fun pollLoop(api: TelegramApi) {
        var failures = 0
        while (scope.isActive) {
            api.getUpdates(offset)
                .onSuccess { updates ->
                    failures = 0
                    updates.forEach { update ->
                        offset = maxOf(offset, update.updateId + 1)
                        dispatch(api, update)
                    }
                }
                .onFailure {
                    failures++
                    onStateChange(State.ERROR, "[!] poll failed x$failures")
                    delay(backoffMs(failures))
                }
        }
    }

    private suspend fun dispatch(api: TelegramApi, update: TelegramApi.Update) {
        val reply = handler.handle(update.from, update.text)
        if (reply != null) {
            api.sendMessage(update.chatId, reply)
        } else {
            config.relayChatId = update.chatId
            room.addRemote(update.from, update.text, MessageSource.TELEGRAM)
        }
    }

    /** Linear backoff: 2s, 4s, 6s … capped at 30s. */
    private fun backoffMs(failures: Int): Long =
        (2_000L * failures.coerceIn(1, 15)).coerceAtMost(30_000L)

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        config.setEnabled(false)
        onStateChange(State.STOPPED, "[IDLE] relay not bound")
    }

    val isRunning: Boolean get() = pollJob?.isActive == true

    private companion object {
        val COMMANDS = listOf(
            "status" to "server status readout",
            "ip" to "show LAN endpoint",
            "whoami" to "show your telegram handle",
            "peers" to "show connected host",
            "clear" to "clear the transcript",
            "echo" to "echo text back",
            "help" to "command list"
        )
    }
}
