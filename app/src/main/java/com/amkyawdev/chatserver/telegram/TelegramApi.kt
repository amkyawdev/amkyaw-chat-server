package com.amkyawdev.chatserver.telegram

import com.amkyawdev.chatserver.util.LogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal Telegram Bot API client (no SDK dependency).
 *
 * Only the endpoints this app needs are implemented: getMe, getUpdates,
 * sendMessage and sendChatAction. All calls are blocking and must be invoked
 * from [Dispatchers.IO].
 */
class TelegramApi(private val token: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    private fun endpoint(method: String) = "$BASE_URL/bot$token/$method"

    /** Verifies the token and returns the bot's @username. */
    suspend fun getMe(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = execute("getMe", null)
            body.getJSONObject("result").getString("username")
        }.onFailure { LogHelper.e("getMe failed: ${it.message}", it) }
    }

    /**
     * Long-polls for updates.
     *
     * @param offset id of the first update to return (last seen id + 1).
     * @param timeoutSec server-side long-poll window.
     */
    suspend fun getUpdates(offset: Long, timeoutSec: Int = 25): Result<List<Update>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val form = FormBody.Builder()
                    .add("offset", offset.toString())
                    .add("timeout", timeoutSec.toString())
                    .add("allowed_updates", "[\"message\"]")
                    .build()
                val json = execute("getUpdates", form)
                val arr = json.getJSONArray("result")
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val msg = o.optJSONObject("message") ?: return@map null
                    val chat = msg.optJSONObject("chat") ?: return@map null
                    val text = msg.optString("text")
                    if (text.isEmpty()) return@map null
                    Update(
                        updateId = o.getLong("update_id"),
                        chatId = chat.getLong("id"),
                        from = chat.optString("username")
                            .ifEmpty { chat.optString("first_name", "telegram") },
                        text = text
                    )
                }.filterNotNull()
            }.onFailure { LogHelper.e("getUpdates failed: ${it.message}", it) }
        }

    /** Sends a plain-text message. */
    suspend fun sendMessage(chatId: Long, text: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val form = FormBody.Builder()
                    .add("chat_id", chatId.toString())
                    .add("text", text)
                    .add("disable_web_page_preview", "true")
                    .build()
                execute("sendMessage", form)
                Unit
            }.onFailure { LogHelper.e("sendMessage failed: ${it.message}", it) }
        }

    /** Sets the persistent command menu shown in Telegram clients. */
    suspend fun setMyCommands(commands: List<Pair<String, String>>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val arr = org.json.JSONArray()
                commands.forEach { (cmd, desc) ->
                    arr.put(JSONObject().put("command", cmd).put("description", desc))
                }
                val form = FormBody.Builder().add("commands", arr.toString()).build()
                execute("setMyCommands", form)
                Unit
            }.onFailure { LogHelper.e("setMyCommands failed: ${it.message}", it) }
        }

    private fun execute(method: String, form: FormBody?): JSONObject {
        val builder = Request.Builder().url(endpoint(method))
        if (form != null) builder.post(form) else builder.get()
        client.newCall(builder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val json = JSONObject(raw)
            if (!json.optBoolean("ok", false)) {
                error("telegram $method not ok: ${json.optString("description")}")
            }
            return json
        }
    }

    data class Update(val updateId: Long, val chatId: Long, val from: String, val text: String)

    private companion object {
        const val BASE_URL = "https://api.telegram.org"
    }
}
