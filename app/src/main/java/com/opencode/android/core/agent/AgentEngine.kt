package com.opencode.android.core.agent

import com.opencode.android.core.config.ConfigManager
import com.opencode.android.core.provider.*
import com.opencode.android.core.session.*
import com.opencode.android.core.tool.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.util.UUID

class AgentEngine(
    private val sessionManager: SessionManager,
    private val configManager: ConfigManager
) {
    private val llmClient = LLMClient()
    private val toolRegistry = ToolRegistry().apply {
        register(ReadFileTool())
        register(WriteFileTool())
        register(EditFileTool())
        register(CreateFileTool())
        register(DeleteFileTool())
        register(RenameFileTool())
        register(CreateFolderTool())
        register(DeleteFolderTool())
        register(ListFolderTool())
        register(GlobFilesTool())
        register(GrepSearchTool())
        register(ShellExecTool())
        register(GitStatusTool())
        register(GitDiffTool())
        register(GitLogTool())
    }

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _currentOutput = MutableStateFlow("")
    val currentOutput: StateFlow<String> = _currentOutput.asStateFlow()

    private val _toolEvents = MutableSharedFlow<ToolEvent>()
    val toolEvents: SharedFlow<ToolEvent> = _toolEvents

    suspend fun processMessage(sessionId: String, userMessage: String) {
        val config = configManager.config.value
        val provider = config.providers[config.defaultProvider] ?: return
        val session = sessionManager.getSession(sessionId) ?: return

        _isProcessing.value = true
        _currentOutput.value = ""

        sessionManager.addMessage(sessionId, "user", userMessage)

        var conversationHistory = buildConversationHistory(sessionId, config.systemPrompt)

        var iterations = 0
        val maxIterations = 20

        while (iterations < maxIterations) {
            iterations++

            val responseBuilder = StringBuilder()
            val toolCallsList = mutableListOf<ToolCallData>()

            llmClient.streamChat(provider, conversationHistory).collect { event ->
                when (event) {
                    is StreamEvent.Token -> {
                        responseBuilder.append(event.text)
                        _currentOutput.value = responseBuilder.toString()
                    }
                    is StreamEvent.Done -> {}
                    is StreamEvent.Error -> {
                        sessionManager.addMessage(sessionId, "assistant", "Error: ${event.message}")
                        _currentOutput.value = "Error: ${event.message}"
                    }
                    else -> {}
                }
            }

            val assistantContent = responseBuilder.toString()

            if (assistantContent.contains("```json") && assistantContent.contains("\"tool_call\"")) {
                val toolCalls = parseToolCalls(assistantContent)
                if (toolCalls.isNotEmpty()) {
                    sessionManager.addMessage(sessionId, "assistant", assistantContent, toolCalls)
                    val results = executeTools(toolCalls, session.workspacePath)
                    val resultsText = results.joinToString("\n\n") { "${it.name}: ${it.result}" }
                    sessionManager.addMessage(sessionId, "tool", resultsText)
                    conversationHistory = buildConversationHistory(sessionId, config.systemPrompt)
                    continue
                }
            }

            sessionManager.addMessage(sessionId, "assistant", assistantContent)
            _currentOutput.value = ""
            break
        }

        _isProcessing.value = false
    }

    private fun buildConversationHistory(sessionId: String, systemPrompt: String): List<ChatMessage> {
        val messages = sessionManager.getMessages(sessionId)
        val history = mutableListOf(ChatMessage("system", systemPrompt))
        messages.forEach { msg ->
            when (msg.role) {
                "user" -> history.add(ChatMessage("user", msg.content))
                "assistant" -> history.add(ChatMessage("assistant", msg.content))
                "tool" -> history.add(ChatMessage("user", "Tool results:\n${msg.content}"))
            }
        }
        return history
    }

    private fun parseToolCalls(content: String): List<ToolCallData> {
        val calls = mutableListOf<ToolCallData>()
        try {
            val jsonPattern = Regex("```json\\s*(\\{.*?\\})\\s*```", RegexOption.DOT_MATCHES_ALL)
            jsonPattern.findAll(content).forEach { match ->
                val obj = Json.parseToJsonElement(match.groupValues[1]).jsonObject
                val name = obj["tool"]?.jsonPrimitive?.content ?: return@forEach
                val args = obj["args"]?.jsonObject ?: buildJsonObject {}
                calls.add(ToolCallData(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    arguments = args.toString()
                ))
            }
        } catch (_: Exception) {}
        return calls
    }

    private suspend fun executeTools(calls: List<ToolCallData>, workspace: String): List<ToolCallData> {
        return calls.map { call ->
            _toolEvents.emit(ToolEvent.Started(call.name))
            val tool = toolRegistry.get(call.name)
            val result = if (tool != null) {
                try {
                    val args = Json.parseToJsonElement(call.arguments).jsonObject
                    tool.execute(args, workspace)
                } catch (e: Exception) { "Error: ${e.message}" }
            } else { "Error: Unknown tool: ${call.name}" }
            _toolEvents.emit(ToolEvent.Completed(call.name, result))
            call.copy(result = result)
        }
    }

    fun cancel() {
        _isProcessing.value = false
        _currentOutput.value = ""
    }
}

sealed class ToolEvent {
    data class Started(val name: String) : ToolEvent()
    data class Completed(val name: String, val result: String) : ToolEvent()
}
