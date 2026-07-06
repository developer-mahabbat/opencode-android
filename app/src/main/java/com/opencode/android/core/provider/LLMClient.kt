package com.opencode.android.core.provider

import com.opencode.android.core.config.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import timber.log.Timber
import java.util.concurrent.TimeUnit

@Serializable
data class ChatMessage(val role: String, val content: String)

sealed class StreamEvent {
    data object Connected : StreamEvent()
    data class Token(val text: String) : StreamEvent()
    data class Done(val fullText: String) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
    data class ToolCall(val id: String, val name: String, val arguments: String) : StreamEvent()
}

class LLMClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        const val ZEN_BASE_URL = "https://opencode.ai/zen/v1"
    }

    suspend fun streamChat(
        provider: ProviderConfig,
        messages: List<ChatMessage>,
        model: String = "",
        maxTokens: Int = 8192,
        temperature: Double = 0.7,
    ): Flow<StreamEvent> = withContext(Dispatchers.IO) {
        callbackFlow {
            val useModel = model.ifEmpty { provider.defaultModel }
            val url = resolveUrl(provider)
            val body = buildRequestBody(provider, messages, useModel, maxTokens, temperature)
            val reqBody = body.toString().toRequestBody("application/json".toMediaType())

            val requestBuilder = Request.Builder().url(url).post(reqBody)
            requestBuilder.addHeader("Content-Type", "application/json")
            requestBuilder.addHeader("User-Agent", "OpenCode-Android/1.0")

            when {
                provider.apiKey.isNotEmpty() -> {
                    requestBuilder.addHeader("Authorization", "Bearer ${provider.apiKey}")
                }
                provider.id == "zen" -> {
                    requestBuilder.addHeader("Authorization", "Bearer public")
                }
            }

            provider.headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val emitter = EventSources.createFactory(client)
            val es = emitter.newEventSource(requestBuilder.build(), object : EventSourceListener() {
                private val buffer = StringBuilder()

                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    try {
                        if (data == "[DONE]") {
                            trySend(StreamEvent.Done(buffer.toString()))
                            return
                        }
                        val obj = json.parseToJsonElement(data).jsonObject
                        val choices = obj["choices"]?.jsonArray ?: return
                        if (choices.isEmpty()) return
                        val choice = choices[0].jsonObject
                        val delta = choice["delta"]?.jsonObject ?: return

                        val content = delta["content"]?.jsonPrimitive?.contentOrNull
                        if (content != null) {
                            buffer.append(content)
                            trySend(StreamEvent.Token(content))
                        }

                        val toolCalls = delta["tool_calls"]?.jsonArray
                        toolCalls?.forEach { tc ->
                            try {
                                val tcObj = tc.jsonObject
                                val fn = tcObj["function"]?.jsonObject ?: return@forEach
                                val name = fn["name"]?.jsonPrimitive?.contentOrNull
                                val args = fn["arguments"]?.jsonPrimitive?.contentOrNull
                                if (name != null) {
                                    trySend(StreamEvent.ToolCall(
                                        id = tcObj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                                        name = name,
                                        arguments = args ?: "{}"
                                    ))
                                }
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to parse tool call chunk")
                            }
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to parse SSE chunk")
                    }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    try {
                        val msg = t?.message ?: response?.let { "HTTP ${it.code}" } ?: "Unknown error"
                        Timber.e("SSE failure: %s", msg)
                        trySend(StreamEvent.Error(msg))
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to send error event")
                    } finally {
                        try { close() } catch (_: Exception) {}
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    try { close() } catch (_: Exception) {}
                }
            })

            awaitClose {
                try { es.cancel() } catch (_: Exception) {}
            }
        }
    }

    private fun resolveUrl(provider: ProviderConfig): String {
        val base = provider.baseUrl.ifEmpty {
            when (provider.id) {
                "zen" -> ZEN_BASE_URL
                "openai" -> "https://api.openai.com/v1"
                "anthropic" -> "https://api.anthropic.com/v1"
                "openrouter" -> "https://openrouter.ai/api/v1"
                else -> ZEN_BASE_URL
            }
        }
        return when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/chat/completions"
        }
    }

    private fun buildRequestBody(
        provider: ProviderConfig,
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int,
        temperature: Double,
    ): JsonObject {
        val arr = buildJsonArray {
            messages.forEach { m ->
                add(buildJsonObject {
                    put("role", m.role)
                    put("content", m.content)
                })
            }
        }

        return buildJsonObject {
            put("model", model)
            put("messages", arr)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            put("stream", true)
        }
    }
}
