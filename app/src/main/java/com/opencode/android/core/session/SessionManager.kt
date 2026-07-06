package com.opencode.android.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class Session(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val workspacePath: String,
    val modelId: String,
    val providerId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val agentId: String = "build",
)

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCalls: List<ToolCallData>? = null,
)

data class ToolCallData(
    val id: String,
    val name: String,
    val arguments: String,
    val result: String? = null,
)

class SessionManager {
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    fun createSession(title: String, workspacePath: String, modelId: String, providerId: String, agentId: String = "build"): Session {
        val session = Session(title = title, workspacePath = workspacePath, modelId = modelId, providerId = providerId, agentId = agentId)
        _sessions.value = _sessions.value + session
        return session
    }

    fun getSession(id: String): Session? = _sessions.value.find { it.id == id }

    fun addMessage(sessionId: String, role: String, content: String, toolCalls: List<ToolCallData>? = null): Message {
        val msg = Message(sessionId = sessionId, role = role, content = content, toolCalls = toolCalls)
        _messages.value = _messages.value + msg
        return msg
    }

    fun getMessages(sessionId: String): List<Message> = _messages.value.filter { it.sessionId == sessionId }

    fun deleteSession(id: String) {
        _sessions.value = _sessions.value.filter { it.id != id }
        _messages.value = _messages.value.filter { it.sessionId != id }
    }

    fun updateSessionTitle(id: String, title: String) {
        _sessions.value = _sessions.value.map { if (it.id == id) it.copy(title = title) else it }
    }
}
