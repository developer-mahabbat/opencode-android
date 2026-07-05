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
    val createdAt: Long = System.currentTimeMillis()
)

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCalls: List<ToolCallData>? = null
)

data class ToolCallData(
    val id: String,
    val name: String,
    val arguments: String,
    val result: String? = null
)

class SessionManager {
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    fun createSession(title: String, workspacePath: String, modelId: String, providerId: String): Session {
        val session = Session(title = title, workspacePath = workspacePath, modelId = modelId, providerId = providerId)
        _sessions.value = _sessions.value + session
        _currentSessionId.value = session.id
        return session
    }

    fun getSession(id: String): Session? = _sessions.value.find { it.id == id }

    fun getCurrentSession(): Session? = _currentSessionId.value?.let { getSession(it) }

    fun addMessage(sessionId: String, role: String, content: String, toolCalls: List<ToolCallData>? = null): Message {
        val msg = Message(sessionId = sessionId, role = role, content = content, toolCalls = toolCalls)
        _messages.value = _messages.value + msg
        return msg
    }

    fun getMessages(sessionId: String): List<Message> = _messages.value.filter { it.sessionId == sessionId }

    fun getMessagesFlow(sessionId: String): StateFlow<List<Message>> {
        val filtered = MutableStateFlow(getMessages(sessionId))
        return filtered.asStateFlow()
    }

    fun deleteSession(id: String) {
        _sessions.value = _sessions.value.filter { it.id != id }
        _messages.value = _messages.value.filter { it.sessionId != id }
        if (_currentSessionId.value == id) _currentSessionId.value = null
    }

    fun setCurrentSession(id: String) { _currentSessionId.value = id }
}
