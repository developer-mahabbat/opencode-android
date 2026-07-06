package com.opencode.android.core.agent

import com.opencode.android.core.config.ConfigManager
import com.opencode.android.core.config.ProviderConfig
import com.opencode.android.core.provider.*
import com.opencode.android.core.session.*
import com.opencode.android.core.tool.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
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
            id = "build",
            name = "Build",
            description = "Full access agent with all tools",
            mode = AgentMode.PRIMARY,
            systemPrompt = com.opencode.android.core.config.DEFAULT_SYSTEM_PROMPT,
            color = "#90CAF9",
        ),
        "plan" to AgentDefinition(
            id = "plan",
            name = "Plan",
            description = "Read-only analysis and planning",
            mode = AgentMode.PRIMARY,
            systemPrompt = com.opencode.android.core.config.PLAN_SYSTEM_PROMPT,
            deniedTools = setOf("write_file", "edit_file", "create_file", "delete_file", "rename_file", "create_folder", "delete_folder", "shell_exec"),
            color = "#A5D6A7",
        ),
        "coder" to AgentDefinition(
            id = "coder",
            name = "Coder",
            description = "Code specialist with deep analysis",
            mode = AgentMode.PRIMARY,
            systemPrompt = "You are an expert coder. Focus on writing clean, efficient, well-documented code. Always explain your approach before implementing.",
            color = "#CE93D8",
        ),
        "researcher" to AgentDefinition(
            id = "researcher",
            name = "Researcher",
            description = "Web search and documentation specialist",
            mode = AgentMode.PRIMARY,
            systemPrompt = "You are a research specialist. Use web search to find documentation, examples, and best practices. Always cite your sources.",
            color = "#FFB74D",
        ),
        "general" to AgentDefinition(
            id = "general",
            name = "General",
            description = "General-purpose sub-agent",
            mode = AgentMode.SUBAGENT,
            systemPrompt = "You are a helpful assistant. Complete the given task using available tools.",
            color = "#80CBC4",
        ),
        "explore" to AgentDefinition(
            id = "explore",
            name = "Explore",
            description = "Fast codebase exploration",
            mode = AgentMode.SUBAGENT,
            systemPrompt = "You are a fast codebase explorer. Quickly find and report relevant files and code. Be concise.",
            deniedTools = setOf("write_file", "edit_file", "create_file", "delete_file", "rename_file", "create_folder", "delete_folder", "shell_exec"),
            color = "#F48FB1",
        ),
        "reviewer" to AgentDefinition(
            id = "reviewer",
            name = "Reviewer",
            description = "Code review specialist",
            mode = AgentMode.SUBAGENT,
            systemPrompt = "You are a code reviewer. Analyze code for bugs, security issues, performance problems, and style violations. Be specific about line numbers and provide fix suggestions.",
            deniedTools = setOf("write_file", "edit_file", "create_file", "delete_file", "rename_file", "create_folder", "delete_folder", "shell_exec"),
            color = "#EF9A9A",
        ),
        "title" to AgentDefinition(
            id = "title",
            name = "Title",
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

    private var job: Job? = null

    fun switchAgent(agentId: String) {
        _currentAgent.value = agentId
    }

    fun processMessage(sessionId: String, userMessage: String) {
        job?.cancel()
        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            _isProcessing.value = true
            _currentOutput.value = ""
            _activeTools.value = emptyList()

            try {
                val config = configManager.config.value
                val provider = config.providers[config.defaultProvider]
                    ?: ProviderConfig(id = "zen", name = "Zen", baseUrl = "https://opencode.ai/zen/v1")
                val session = sessionManager.getSession(sessionId) ?: return@launch
                val agentDef = AgentRegistry.getAgent(_currentAgent.value)

                sessionManager.addMessage(sessionId, "user", userMessage)

                var conversationHistory = buildConversationHistory(sessionId, agentDef.systemPrompt)
                var iterations = 0

                while (iterations < agentDef.maxSteps && _isProcessing.value) {
                    iterations++
                    val responseBuilder = StringBuilder()
                    val toolCallsList = mutableListOf<PendingToolCall>()

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
                                if (agentDef.allowedTools.isEmpty() || event.name in agentDef.allowedTools) {
                                    if (event.name !in agentDef.deniedTools) {
                                        toolCallsList.add(PendingToolCall(event.id, event.name, event.arguments))
                                        _activeTools.value = _activeTools.value + event.name
                                    }
                                }
                            }
                            is StreamEvent.Error -> {
                                sessionManager.addMessage(sessionId, "assistant", "Error: ${event.message}")
                                _currentOutput.value = "Error: ${event.message}"
                            }
                            else -> {}
                        }
                    }

                    val assistantContent = responseBuilder.toString()

                    if (toolCallsList.isNotEmpty()) {
                        sessionManager.addMessage(sessionId, "assistant", assistantContent,
                            toolCalls = toolCallsList.map { ToolCallData(it.id, it.name, it.arguments) })

                        val toolResults = mutableListOf<String>()
                        for (tc in toolCallsList) {
                            _toolEvents.emit(ToolEvent.Started(tc.name, tc.arguments))
                            val tool = toolRegistry.get(tc.name)
                            val result = if (tool != null) {
                                try {
                                    val args = Json.parseToJsonElement(tc.arguments).jsonObject
                                    tool.execute(args, session.workspacePath)
                                } catch (e: Exception) { "Error: ${e.message}" }
                            } else { "Error: Unknown tool: ${tc.name}" }
                            toolResults.add("${tc.name}: $result")
                            _toolEvents.emit(ToolEvent.Completed(tc.name, result))
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
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _currentOutput.value = "Error: ${e.message}"
                }
            } finally {
                _isProcessing.value = false
                _activeTools.value = emptyList()
            }
        }
    }

    suspend fun spawnSubAgent(
        parentSessionId: String,
        agentId: String,
        task: String,
        workspace: String,
    ): String {
        val agentDef = AgentRegistry.getAgent(agentId)
        val childSessionId = sessionManager.createSession(
            title = "Sub-agent: ${agentDef.name}",
            workspacePath = workspace,
            modelId = configManager.config.value.defaultModel,
            providerId = configManager.config.value.defaultProvider,
        ).id

        val provider = configManager.config.value.let { config ->
            config.providers[config.defaultProvider]
                ?: ProviderConfig(id = "zen", name = "Zen", baseUrl = "https://opencode.ai/zen/v1")
        }

        sessionManager.addMessage(childSessionId, "user", task)

        val messages = buildConversationHistory(childSessionId, agentDef.systemPrompt)
        val responseBuilder = StringBuilder()

        llmClient.streamChat(provider, messages).collect { event ->
            when (event) {
                is StreamEvent.Token -> responseBuilder.append(event.text)
                else -> {}
            }
        }

        val result = responseBuilder.toString()
        sessionManager.addMessage(childSessionId, "assistant", result)
        return result
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
        job?.cancel()
        job = null
        _isProcessing.value = false
        _currentOutput.value = ""
        _activeTools.value = emptyList()
    }
}

data class PendingToolCall(val id: String, val name: String, val arguments: String)

sealed class ToolEvent {
    data class Started(val name: String, val args: String = "") : ToolEvent()
    data class Completed(val name: String, val result: String) : ToolEvent()
}
