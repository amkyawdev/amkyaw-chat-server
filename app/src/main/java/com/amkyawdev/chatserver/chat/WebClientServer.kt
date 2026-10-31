package com.amkyawdev.chatserver.chat

import com.amkyawdev.chatserver.util.LogHelper
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket

/**
 * Minimal HTTP server that hands the browser chat client to LAN peers.
 *
 * Answers any GET with `assets/chat.html`; the page then opens a WebSocket back
 * to [ChatServer], so a peer joins from any browser by opening
 * `http://<host-ip>:<port>/`. Deliberately dependency-free — the whole job is
 * one static file, so a socket and a hand-written response is enough.
 *
 * Plain HTTP on the LAN only: no TLS, no auth. That matches the app's
 * "trusted local network" model.
 */
class WebClientServer(
    private val port: Int,
    private val htmlProvider: () -> ByteArray?
) {

    @Volatile
    private var socket: ServerSocket? = null
    private var thread: Thread? = null

    val isRunning: Boolean get() = socket?.isClosed == false

    /** Binds [port] and starts serving. Failures are logged, not thrown. */
    fun start() {
        if (thread?.isAlive == true) return
        // Binding is a network operation, so it happens on the worker thread
        // rather than on whichever thread called start().
        thread = Thread({
            runCatching {
                val server = ServerSocket(port).apply { reuseAddress = true }
                socket = server
                LogHelper.i("browser client served on :$port")
                acceptLoop(server)
            }.onFailure {
                LogHelper.e("web client bind failed: ${it.message}", it)
            }
        }, "amkyaw-webclient").apply {
            isDaemon = true
            start()
        }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (!server.isClosed) {
            val client = runCatching { server.accept() }.getOrNull() ?: continue
            // One short-lived thread per request; requests are rare and tiny.
            Thread({ respond(client) }, "amkyaw-webclient-req").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun respond(client: Socket) {
        client.use { c ->
            runCatching {
                c.getInputStream().bufferedReader().useLines { lines ->
                    // Drain the request head; method, path and headers are ignored.
                    for (line in lines) if (line.isEmpty()) break
                }
                val out = BufferedWriter(OutputStreamWriter(c.getOutputStream(), Charsets.UTF_8))
                val body = htmlProvider()
                if (body == null) {
                    out.write("HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                    out.flush()
                } else {
                    out.write("HTTP/1.1 200 OK\r\n")
                    out.write("Content-Type: text/html; charset=utf-8\r\n")
                    out.write("Content-Length: ${body.size}\r\n")
                    out.write("Connection: close\r\n\r\n")
                    out.flush()
                    c.getOutputStream().write(body)
                    c.getOutputStream().flush()
                }
            }.onFailure { LogHelper.d("web client request failed: ${it.message}") }
        }
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
        thread = null
    }
}
