package com.amkyawdev.chatserver.ui

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.amkyawdev.chatserver.AmkyawApp
import com.amkyawdev.chatserver.R
import com.amkyawdev.chatserver.ServerManager
import com.amkyawdev.chatserver.chat.ChatServer
import com.amkyawdev.chatserver.databinding.ActivityChatBinding
import com.amkyawdev.chatserver.telegram.TelegramBotService
import kotlinx.coroutines.launch

/**
 * SCREEN 3 — Chat room.
 *
 * Header status dot tracks the LAN connection, the list renders the shared
 * transcript, and the input bar posts through [ServerManager] so the message
 * reaches both the LAN room and the Telegram relay.
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val adapter = MessageAdapter()
    private val manager: ServerManager get() = (application as AmkyawApp).serverManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerMessages.layoutManager =
            LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.recyclerMessages.adapter = adapter

        binding.txtChatTitle.text = getString(R.string.chat_title)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSend.setOnClickListener { send() }
        binding.inputMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send()
                true
            } else {
                false
            }
        }

        observeTranscript()
        observeState()
    }

    private fun send() {
        val text = binding.inputMessage.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        manager.sendLocal(text)
        binding.inputMessage.text?.clear()
        binding.recyclerMessages.smoothScrollToPosition(
            (adapter.itemCount - 1).coerceAtLeast(0)
        )
    }

    private fun observeTranscript() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                manager.room.messages.collect { messages ->
                    adapter.submit(messages)
                    binding.txtEmpty.visibility =
                        if (messages.isEmpty()) View.VISIBLE else View.GONE
                    if (messages.isNotEmpty()) {
                        binding.recyclerMessages.scrollToPosition(messages.lastIndex)
                    }
                }
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                manager.state.collect { state ->
                    val connected = state.serverState == ChatServer.State.ONLINE
                    binding.statusDot.isSelected = connected
                    binding.statusDot.isActivated =
                        state.serverState == ChatServer.State.STARTING

                    val live = connected &&
                        state.relayState == TelegramBotService.State.ONLINE
                    binding.txtLive.text = if (live) "[LIVE·RELAY]" else getString(R.string.chat_live)
                    binding.txtLive.setTextColor(
                        getColor(if (connected) R.color.red_alert else R.color.text_dim)
                    )
                }
            }
        }
    }
}
