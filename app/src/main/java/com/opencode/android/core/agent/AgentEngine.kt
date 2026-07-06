package com.opencode.android.core.agent

import com.opencode.android.core.config.ConfigManager
import com.opencode.android.core.config.ProviderConfig
import com.opencode.android.core.provider.*
import com.opencode.android.core.session.*
import com.opencode.android.core.tool.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import timber.log.Timber
import java.util.UUID

enum class AgentMode { PRIMARY, SUBAGENT, HIDDEN }

data class AgentDefinition(
    val id: String,
    val name: String,
    val description: String,
    val mode: AgentMode,
    val systemPrompt: String,
    val allowedTools: Set<String> = emptySet(),
    val deniedTools: Set<String> = emptySet(),
    val temperature: Double = 0.7,
    val maxSteps: Int = 20,
    val color: String = "#90CAF9",
)

object AgentRegistry {
    val agents = mapOf(
        "build" to AgentDefinition(
            id = "build", name = "Build",
            description = "Full access agent with all tools",
            mode = AgentMode.PRIMARY,
            systemPrompt = com.opencode.android.core.config.DEFAULT_SYSTEM_PROMPT,
            color = "#90CAF9",
        ),
        "plan" to AgentDefinition(
            id = "plan", name = "Plan",
            description = "Read-only analysis and planning",
            mode = AgentMode.PRIMARY,
            systemPrompt = com.opencode.android.core.config.PLAN_SYSTEM_PROMPT,
            deniedTools = setOf("write_file", "edit_file", "create_file", "delete_file", "rename_file", "create_folder", "delete_folder", "shell_exec"),
            color = "#A5D6A7",
        ),
        "coder" to AgentDefinition(
            id = "coder", name = "Coder",
            description = "Code specialist with deep analysis",
            mode = AgentMode.PRIMARY,
            systemPrompt = "You are an expert coder. Focus on writing clean, efficient, well-documented code. Always explain your approach before implementing.",
            color = "#CE93D8",
        ),
        "researcher" to AgentDefinition(
            id = "researcher", name = "Researcher",
            description = "Web search and documentation specialist",
            mode = AgentMode.PRIMARY,
            systemPrompt = "You are a research specialist. Use web search to find documentation, examples, and best practices. Always cite your sources.",
            color = "#FFB74D",
        ),
        "general" to AgentDefinition(
            id = "general", name = "General",
            description = "General-purpose sub-agent",
            mode = AgentMode.SUBAGENT,
            systemPrompt = "You are a helpful assistant. Complete the given task using available tools.",
            color = "#80CBC4",
        ),
        "explore" to AgentDefinition(
            id = "explore", name = "Explore",
            description = "Fast codebase exploration",
            mode = AgentMode.SUBAGENT,
            systemPrompt = "You are a fast codebase explorer. Quickly find and report relevant files and code. Be concise.",
            deniedTools = setOf("write_file", "edit_file", "create_file", "delete_file", "rename_file", "create_folder", "delete_folder", "shell_exec"),
            color = "#F48FB1",
        ),
        "reviewer" to AgentDefinition(
            id = "reviewer", name = "Reviewer",
            description = "Code review specialist",
            mode = AgentMode.SUBAGENT,
            systemPrompt = "You are a code reviewer. Analyze code for bugs, security issues, performance problems, and style violations. Be specific about line numbers and provide fix suggestions.",
            deniedTools = setOf("write_file", "edit_file", "create_file", "delete_file", "rename_file", "create_folder", "delete_folder", "shell_exec"),
            color = "#EF9A9A",
        ),
        "title" to AgentDefinition(
            id = "title", name = "Title",
            description = "Session title generator",
            mode = AgentMode.HIDDEN,
            systemPrompt = "Generate a short, descriptive title (max 50 chars) for this conversation. Only output the title, nothing else.",
            temperature = 0.5,
            color = "#B0BEC5",
        ),
    )

    fun getAgent(id: String): AgentDefinition = agents[id] ?: agents["build"]!!
    fun getPrimaryAgents(): List<AgentDefinition> = agents.values.filter { it.mode == AgentMode.PRIMARY }
    fun getSubAgents(): List<AgentDefinition> = agents.values.filter { it.mode == AgentMode.SUBAGENT }
}

class AgentEngine(
    private val sessionManager: SessionManager,
    private val configManager: ConfigManager,
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
        register(WebSearchTool())
        register(WebFetchTool())
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var processingJob: Job? = null

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _currentOutput = MutableStateFlow("")
    val currentOutput: StateFlow<String> = _currentOutput.asStateFlow()

    private val _currentAgent = MutableStateFlow("build")
    val currentAgent: StateFlow<String> = _currentAgent.asStateFlow()

    private val _toolEvents = MutableSharedFlow<ToolEvent>()
    val toolEvents: SharedFlow<ToolEvent> = _toolEvents

    private val _activeTools = MutableStateFlow<List<String>>(emptyList())
    val activeTools: StateFlow<List<String>> = _activeTools.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun switchAgent(agentId: String) {
        _currentAgent.value = agentId
    }

    fun processMessage(sessionId: String, userMessage: String) {
        Timber.d("processMessage called: sessionId=$sessionId, message=${userMessage.take(50)}")

        if (_isProcessing.value) {
            Timber.w("Already processing, ignoring")
            return
        }
        processingJob?.cancel()

        processingJob = scope.launch {
            _isProcessing.value = true
            _currentOutput.value = ""
            _activeTools.value = emptyList()
            _errorMessage.value = null

            try {
                // Validate config
                val config = configManager.config.value
                Timber.d("Config loaded: provider=${config.defaultProvider}, model=${config.defaultModel}")

                val provider = config.providers[config.defaultProvider] ?: run {
                    val msg = "Provider not found: ${config.defaultProvider}"
                    Timber.e(msg)
                    _errorMessage.value = msg
                    _currentOutput.value = msg
                    return@launch
                }
                Timber.d("Provider resolved: ${provider.id}, baseUrl=${provider.baseUrl}")

                val session = sessionManager.getSession(sessionId) ?: run {
                    val msg = "Session not found: $sessionId"
                    Timber.e(msg)
                    _errorMessage.value = msg
                    _currentOutput.value = msg
                    return@launch
                }
                Timber.d("Session found: ${session.id}")

                val agentDef = AgentRegistry.getAgent(_currentAgent.value)
                Timber.d("Agent: ${agentDef.id} (${agentDef.name})")

                sessionManager.addMessage(sessionId, "user", userMessage)
                Timber.d("User message added")

                var conversationHistory = buildConversationHistory(sessionId, agentDef.systemPrompt)
                Timber.d("Conversation history built: ${conversationHistory.size} messages")

                var iterations = 0

                while (iterations < agentDef.maxSteps && _isProcessing.value) {
                    iterations++
                    Timber.d("Iteration $iterations/${agentDef.maxSteps}")
                    val responseBuilder = StringBuilder()
                    val toolCallsList = mutableListOf<PendingToolCall>()

                    try {
                        Timber.d("Starting stream chat...")
                        llmClient.streamChat(
                            provider = provider,
                            messages = conversationHistory,
                            model = config.defaultModel,
                            maxTokens = config.maxTokens,
                            temperature = agentDef.temperature,
                        ).collect { event ->
                            when (event) {
                                is StreamEvent.Token -> {
                                    responseBuilder.append(event.text)
                                    _currentOutput.value = responseBuilder.toString()
                                }
                                is StreamEvent.ToolCall -> {
                                    Timber.d("Tool call: ${event.name}")
                                    if (agentDef.allowedTools.isEmpty() || event.name in agentDef.allowedTools) {
                                        if (event.name !in agentDef.deniedTools) {
                                            toolCallsList.add(PendingToolCall(event.id, event.name, event.arguments))
                                            _activeTools.value = _activeTools.value + event.name
                                        }
                                    }
                                }
                                is StreamEvent.Error -> {
                                    Timber.e("Stream error: ${event.message}")
                                    _errorMessage.value = event.message
                                    _currentOutput.value = "Error: ${event.message}"
                                }
                                is StreamEvent.Done -> {
                                    Timber.d("Stream done, full text length: ${event.fullText.length}")
                                }
                                else -> {}
                            }
                        }
                        Timber.d("Stream collection completed")
                    } catch (e: CancellationException) {
                        Timber.d("Stream collection cancelled")
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Stream collection failed")
                        _errorMessage.value = e.message ?: "Connection failed"
                        _currentOutput.value = "Error: ${e.message}"
                    }

                    val assistantContent = responseBuilder.toString()
                    Timber.d("Assistant response length: ${assistantContent.length}")

                    if (toolCallsList.isNotEmpty()) {
                        Timber.d("Processing ${toolCallsList.size} tool calls")
                        sessionManager.addMessage(sessionId, "assistant", assistantContent,
                            toolCalls = toolCallsList.map { ToolCallData(it.id, it.name, it.arguments) })

                        val toolResults = mutableListOf<String>()
                        for (tc in toolCallsList) {
                            try {
                                _toolEvents.emit(ToolEvent.Started(tc.name, tc.arguments))
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to emit tool event")
                            }
                            val tool = toolRegistry.get(tc.name)
                            val result = if (tool != null) {
                                try {
                                    val args = Json.parseToJsonElement(tc.arguments).jsonObject
                                    tool.execute(args, session.workspacePath)
                                } catch (e: Exception) {
                                    Timber.e(e, "Tool execution failed: ${tc.name}")
                                    "Error: ${e.message}"
                                }
                            } else {
                                Timber.w("Unknown tool: ${tc.name}")
                                "Error: Unknown tool: ${tc.name}"
                            }
                            toolResults.add("${tc.name}: $result")
                            try {
                                _toolEvents.emit(ToolEvent.Completed(tc.name, result))
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to emit tool event")
                            }
                        }

                        sessionManager.addMessage(sessionId, "tool", toolResults.joinToString("\n\n"))
                        conversationHistory = buildConversationHistory(sessionId, agentDef.systemPrompt)
                        _activeTools.value = emptyList()
                        continue
                    }

                    if (assistantContent.isNotBlank()) {
                        sessionManager.addMessage(sessionId, "assistant", assistantContent)
                    }
                    _currentOutput.value = ""
                    break
                }
                Timber.d("Processing completed successfully")
            } catch (e: CancellationException) {
                Timber.d("Processing cancelled")
            } catch (e: Exception) {
                Timber.e(e, "Processing failed")
                _errorMessage.value = e.message ?: "Unknown error"
                _currentOutput.value = "Error: ${e.message}"
            } finally {
                _isProcessing.value = false
                _activeTools.value = emptyList()
            }
        }
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

    fun cancel() {
        processingJob?.cancel()
        processingJob = null
        _isProcessing.value = false
        _currentOutput.value = ""
        _activeTools.value = emptyList()
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

data class PendingToolCall(val id: String, val name: String, val arguments: String)

sealed class ToolEvent {
    data class Started(val name: String, val args: String = "") : ToolEvent()
    data class Completed(val name: String, val result: String) : ToolEvent()
}
