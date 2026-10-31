package com.amkyawdev.chatserver

import android.content.Context
import com.amkyawdev.chatserver.chat.ChatRoom
import com.amkyawdev.chatserver.chat.ChatServer
import com.amkyawdev.chatserver.chat.MessageSource
import com.amkyawdev.chatserver.chat.PeerDiscovery
import com.amkyawdev.chatserver.chat.PeerRelay
import com.amkyawdev.chatserver.chat.WebClientServer
import com.amkyawdev.chatserver.telegram.BotCommandHandler
import com.amkyawdev.chatserver.telegram.TelegramBotService
import com.amkyawdev.chatserver.telegram.TelegramConfig
import com.amkyawdev.chatserver.util.IpUtils
import com.amkyawdev.chatserver.util.LogHelper
import com.amkyawdev.chatserver.util.PrefsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the process-wide runtime state: the LAN WebSocket server, the shared
 * transcript, and the Telegram relay.
 *
 * Held by [AmkyawApp] so the UI and the foreground service see the same
 * instance. All public methods are safe to call from the main thread.
 */
class ServerManager(
    private val context: Context,
    private val prefs: PrefsHelper
) {

    val room = ChatRoom()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(ServerState())
    val state: StateFlow<ServerState> = _state.asStateFlow()

    private var server: ChatServer? = null
    private var telegram: TelegramBotService? = null
    private var webClient: WebClientServer? = null
    private var discovery: PeerDiscovery? = null

    val username: String get() = prefs.username
    val port: Int get() = prefs.serverPort
    val endpoint: String get() = IpUtils.localEndpoint(port)

    /** Starts the LAN server (idempotent). */
    fun startServer() {
        if (server != null) return
        _state.value = _state.value.copy(serverState = ChatServer.State.STARTING)
        LogHelper.i("starting server on :$port")

        val relay = PeerRelay(
            sendToTelegram = { sender, text -> sendTelegram(sender, text) },
            sendToLan = { sender, text ->
                room.addRemote(sender, text, MessageSource.TELEGRAM)
                server?.broadcast("[telegram] $sender\t$text")
            }
        )

        val srv = ChatServer(
            port = port,
            onMessage = { message ->
                room.addRemote(message.sender, message.text, message.source)
                relay.relayLanToTelegram(message)
            },
            onStateChange = { s ->
                _state.value = _state.value.copy(
                    serverState = s,
                    peerCount = server?.peerCount ?: 0
                )
            }
        )
        server = srv
        runCatching { srv.start() }
            .onFailure {
                LogHelper.e("server start failed: ${it.message}", it)
                _state.value = _state.value.copy(serverState = ChatServer.State.ERROR)
            }

        startWebClient()
        startDiscovery()
    }

    /**
     * Serves `assets/chat.html` on [port] so LAN peers can join from a browser
     * without installing anything. The page talks to [ChatServer] over WebSocket.
     */
    private fun startWebClient() {
        if (webClient != null) return
        val client = WebClientServer(port + 1) {
            runCatching {
                context.assets.open("chat.html").use { it.readBytes() }
                    .toString(Charsets.UTF_8)
                    .replace(WS_PORT_TOKEN, port.toString())
                    .toByteArray(Charsets.UTF_8)
            }
                .onFailure { LogHelper.e("chat.html unavailable: ${it.message}", it) }
                .getOrNull()
        }
        webClient = client
        client.start()
    }

    /** Advertises `_amkyaw._tcp` so peers find the host without typing an IP. */
    private fun startDiscovery() {
        if (discovery != null) return
        val d = PeerDiscovery(
            context = context,
            serviceName = username,
            port = port,
            onFound = { info -> LogHelper.i("peer found: ${info.serviceName}") }
        )
        discovery = d
        d.advertise()
        d.discover()
    }

    fun stopServer() {
        server?.shutdown()
        server = null
        webClient?.stop()
        webClient = null
        discovery?.stop()
        discovery = null
        _state.value = _state.value.copy(serverState = ChatServer.State.STOPPED, peerCount = 0)
        LogHelper.i("server stopped")
    }

    /** Sends a message typed into the local UI: room + LAN + Telegram. */
    fun sendLocal(text: String) {
        val me = username
        room.addLocal(me, text)
        server?.broadcast("$me\t$text")
        if (telegram?.isRunning == true) sendTelegram(me, text)
    }

    /** Starts the Telegram relay using the stored token. */
    fun startTelegram() {
        val config = TelegramConfig(context)
        if (!config.isConfigured) {
            _state.value = _state.value.copy(
                relayState = TelegramBotService.State.ERROR,
                relayMessage = "[!] token missing or malformed"
            )
            return
        }
        if (telegram?.isRunning == true) return

        val svc = TelegramBotService(
            scope = scope,
            config = config,
            room = room,
            handler = BotCommandHandler(
                room = room,
                hostName = { username },
                endpoint = { endpoint }
            ),
            onStateChange = { state, message ->
                _state.value = _state.value.copy(relayState = state, relayMessage = message)
            }
        )
        telegram = svc
        svc.start()
    }

    fun stopTelegram() {
        telegram?.stop()
        telegram = null
    }

    /** Saves a token and immediately attempts to bind the relay. */
    fun activateTelegram(token: String) {
        prefs.botToken = token
        startTelegram()
    }

    private fun sendTelegram(sender: String, text: String) {
        val svc = telegram ?: return
        if (!svc.isRunning) return
        val config = TelegramConfig(context)
        val chatId = config.relayChatId
        if (chatId == 0L) {
            LogHelper.d("telegram relay has no target chat yet")
            return
        }
        scope.launch {
            com.amkyawdev.chatserver.telegram.TelegramApi(config.token)
                .sendMessage(chatId, "[$sender] $text")
        }
    }

    /** Snapshot of everything the dashboard renders. */
    data class ServerState(
        val serverState: ChatServer.State = ChatServer.State.STOPPED,
        val relayState: TelegramBotService.State = TelegramBotService.State.STOPPED,
        val relayMessage: String = "[IDLE] relay not bound",
        val peerCount: Int = 0
    )

    private companion object {
        /** Replaced with the real WebSocket port when chat.html is served. */
        const val WS_PORT_TOKEN = "__WS_PORT__"
    }
}
