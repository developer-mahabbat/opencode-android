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
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val max_tokens: Int = 8192,
    val temperature: Double = 0.7,
    val stream: Boolean = true
)

sealed class StreamEvent {
    data object Connected : StreamEvent()
    data class Token(val text: String) : StreamEvent()
    data class Done(val fullText: String) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}

class LLMClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun streamChat(provider: ProviderConfig, messages: List<ChatMessage>): Flow<StreamEvent> = withContext(Dispatchers.IO) {
        callbackFlow {
            val url = resolveUrl(provider)
            val body = buildRequestBody(provider, messages)
            val reqBody = body.toString().toRequestBody("application/json".toMediaType())

            val requestBuilder = Request.Builder().url(url).post(reqBody)
            when (provider.id) {
                "anthropic" -> {
                    requestBuilder.addHeader("x-api-key", provider.apiKey)
                    requestBuilder.addHeader("anthropic-version", "2023-06-01")
                }
                else -> requestBuilder.addHeader("Authorization", "Bearer ${provider.apiKey}")
            }

            val emitter = EventSources.createFactory(client)
            val es = emitter.newEventSource(requestBuilder.build(), object : EventSourceListener() {
                private val buffer = StringBuilder()

                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (data == "[DONE]") {
                        trySend(StreamEvent.Done(buffer.toString()))
                        return
                    }
                    try {
                        val obj = json.parseToJsonElement(data).jsonObject
                        val choices = obj["choices"]?.jsonArray ?: return
                        if (choices.isEmpty()) return
                        val delta = choices[0].jsonObject["delta"]?.jsonObject ?: return
                        val content = delta["content"]?.jsonPrimitive?.contentOrNull ?: return
                        buffer.append(content)
                        trySend(StreamEvent.Token(content))
                    } catch (_: Exception) {}
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val msg = t?.message ?: response?.let { "HTTP ${it.code}" } ?: "Unknown error"
                    trySend(StreamEvent.Error(msg))
                    close()
                }

                override fun onClosed(eventSource: EventSource) {
                    close()
                }
            })

            awaitClose { es.cancel() }
        }
    }

    private fun resolveUrl(provider: ProviderConfig): String {
        val base = provider.baseUrl.ifEmpty {
            when (provider.id) {
                "openai" -> "https://api.openai.com"
                "anthropic" -> "https://api.anthropic.com"
                "gemini" -> "https://generativelanguage.googleapis.com"
                "openrouter" -> "https://openrouter.ai/api"
                else -> "https://api.openai.com"
            }
        }
        return when (provider.id) {
            "anthropic" -> "$base/v1/messages"
            "gemini" -> "$base/v1beta/models/${provider.defaultModel}:streamGenerateContent"
            else -> "$base/v1/chat/completions"
        }
    }

    private fun buildRequestBody(provider: ProviderConfig, messages: List<ChatMessage>): JsonObject {
        return when (provider.id) {
            "anthropic" -> buildAnthropicBody(provider, messages)
            "gemini" -> buildGeminiBody(messages)
            else -> buildOpenAIBody(provider, messages)
        }
    }

    private fun buildOpenAIBody(provider: ProviderConfig, messages: List<ChatMessage>): JsonObject {
        val arr = buildJsonArray {
            messages.forEach { m ->
                add(buildJsonObject { put("role", m.role); put("content", m.content) })
            }
        }
        return buildJsonObject {
            put("model", provider.defaultModel)
            put("max_tokens", 8192)
            put("temperature", 0.7)
            put("stream", true)
            put("messages", arr)
        }
    }

    private fun buildAnthropicBody(provider: ProviderConfig, messages: List<ChatMessage>): JsonObject {
        val sys = messages.firstOrNull { it.role == "system" }
        val msgs = messages.filter { it.role != "system" }
        val arr = buildJsonArray {
            msgs.forEach { m ->
                add(buildJsonObject { put("role", m.role); put("content", m.content) })
            }
        }
        return buildJsonObject {
            put("model", provider.defaultModel)
            put("max_tokens", 8192)
            put("stream", true)
            sys?.let { put("system", it.content) }
            put("messages", arr)
        }
    }

    private fun buildGeminiBody(messages: List<ChatMessage>): JsonObject {
        val contents = buildJsonArray {
            messages.filter { it.role != "system" }.forEach { m ->
                add(buildJsonObject {
                    put("role", if (m.role == "assistant") "model" else "user")
                    putJsonArray("parts") { add(buildJsonObject { put("text", m.content) }) }
                })
            }
        }
        return buildJsonObject { put("contents", contents) }
    }
}
