package com.amkyawdev.chatserver

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.amkyawdev.chatserver.chat.ChatServer
import com.amkyawdev.chatserver.databinding.ActivityMainBinding
import com.amkyawdev.chatserver.telegram.TelegramBotService
import com.amkyawdev.chatserver.telegram.TelegramConfig
import com.amkyawdev.chatserver.ui.ChatActivity
import com.amkyawdev.chatserver.util.Permissions
import kotlinx.coroutines.launch

/**
 * SCREEN 2 — Dashboard.
 *
 * Renders the terminal status readout, opens the chat room, and binds the
 * Telegram relay. Text colours follow the state flow emitted by
 * [ServerManager].
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val manager: ServerManager get() = (application as AmkyawApp).serverManager

    private var askedForPermissions = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.inputToken.setText(TelegramConfig(this).token)
        binding.btnOpenChat.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
        binding.btnActivateBot.setOnClickListener { toggleRelay() }

        observeState()
    }

    /** Starts the relay, or stops it when it is already bound. */
    private fun toggleRelay() {
        if (manager.state.value.relayState == TelegramBotService.State.ONLINE) {
            manager.stopTelegram()
            return
        }
        val token = binding.inputToken.text?.toString()?.trim().orEmpty()
        val config = TelegramConfig(this)
        if (!config.isConfigured && token.isEmpty()) {
            reject("[!] enter a bot token first")
            return
        }
        config.save(token)
        manager.activateTelegram(token)
    }

    /** Error path: red status line plus the glitch shake. */
    private fun reject(message: String) {
        binding.txtRelayStatus.text = message
        binding.txtRelayStatus.setTextColor(getColor(R.color.red_alert))
        binding.terminalCard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.glitch))
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                manager.state.collect { state ->
                    binding.txtIp.text = manager.endpoint
                    binding.txtUser.text = manager.username

                    val label: String
                    val colorRes: Int
                    when (state.serverState) {
                        ChatServer.State.ONLINE -> {
                            label = getString(R.string.dash_state_online)
                            colorRes = R.color.yellow_alert
                        }
                        ChatServer.State.STARTING -> {
                            label = "BINDING"
                            colorRes = R.color.cyan
                        }
                        ChatServer.State.ERROR -> {
                            label = "FAULT"
                            colorRes = R.color.red_alert
                        }
                        ChatServer.State.STOPPED -> {
                            label = getString(R.string.dash_state_offline)
                            colorRes = R.color.text_secondary
                        }
                    }
                    binding.txtState.text = label
                    binding.txtState.setTextColor(getColor(colorRes))

                    binding.txtRelayStatus.text = state.relayMessage
                    binding.txtRelayStatus.setTextColor(
                        if (state.relayState == TelegramBotService.State.ONLINE) {
                            getColor(R.color.cyan)
                        } else {
                            getColor(R.color.text_dim)
                        }
                    )

                    binding.btnActivateBot.text =
                        if (state.relayState == TelegramBotService.State.ONLINE) {
                            getString(R.string.dash_btn_stop_bot)
                        } else {
                            getString(R.string.dash_btn_activate_bot)
                        }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ServerService.start(this)
        binding.txtIp.text = manager.endpoint
        requestMissingPermissions()
    }

    /**
     * The server starts without these, but the notification stays hidden and
     * mDNS discovery fails, which reads as "the app does not work".
     */
    private fun requestMissingPermissions() {
        if (askedForPermissions) return
        val missing = Permissions.missing(this)
        if (missing.isEmpty()) return
        askedForPermissions = true
        permissionLauncher.launch(missing)
    }
}
