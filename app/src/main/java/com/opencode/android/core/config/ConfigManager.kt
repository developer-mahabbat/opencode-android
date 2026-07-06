package com.opencode.android.core.config

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AppConfig(
    val defaultProvider: String = "zen",
    val defaultModel: String = "deepseek-v4-flash-free",
    val workspacePath: String = "",
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val providers: Map<String, ProviderConfig> = emptyMap(),
    val activeAgent: String = "build",
    val themeMode: String = "system",
    val fontSize: Int = 14,
    val tabSize: Int = 4,
    val wordWrap: Boolean = false,
    val showLineNumbers: Boolean = true,
    val autoSave: Boolean = true,
    val webSearchEnabled: Boolean = true,
    val maxTokens: Int = 8192,
    val temperature: Double = 0.7,
    val enableSubAgents: Boolean = true,
    val enableThinking: Boolean = true,
)

@Serializable
data class ProviderConfig(
    val id: String,
    val name: String,
    val apiKey: String = "",
    val baseUrl: String = "",
    val defaultModel: String = "",
    val enabled: Boolean = true,
    val headers: Map<String, String> = emptyMap(),
)

const val DEFAULT_SYSTEM_PROMPT = """You are OpenCode, an expert AI coding assistant running natively on Android.

CAPABILITIES:
- Read, write, edit, create, delete files and folders
- Execute shell commands
- Search codebases with regex and glob patterns
- Web search for documentation and references
- Analyze project structure and dependencies
- Generate diffs and patches
- Manage git repositories

BEHAVIOR:
- Be concise, direct, and actionable
- Show code changes when editing files
- Use tools to interact with the filesystem
- Think step by step for complex tasks
- Break large tasks into smaller manageable steps
- Explain your reasoning before making changes"""

const val PLAN_SYSTEM_PROMPT = """You are OpenCode Plan, a read-only analysis assistant.

You can read files, search codebases, and analyze projects, but you CANNOT modify anything.

Your job is to:
- Analyze code and provide recommendations
- Create implementation plans in .opencode/plans/*.md files
- Review code for bugs, security issues, and improvements
- Explain complex code architectures
- Suggest refactoring strategies"""

class ConfigManager(context: Context) {
    private val prefs = context.getSharedPreferences("opencode_config", Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(load())
    val config: StateFlow<AppConfig> = _config.asStateFlow()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

    private fun load(): AppConfig {
        val s = prefs.getString("config", null)
        return if (s != null) try { json.decodeFromString(s) } catch (_: Exception) { AppConfig() }
        else AppConfig()
    }

    fun update(block: (AppConfig) -> AppConfig) {
        val c = block(_config.value)
        _config.value = c
        prefs.edit().putString("config", json.encodeToString(c)).apply()
    }

    fun getProvider(id: String): ProviderConfig? = _config.value.providers[id]

    fun getAvailableModels(): List<ModelInfo> = listOf(
        ModelInfo("deepseek-v4-flash-free", "DeepSeek V4 Flash", "Free", "Fast & capable", listOf("chat", "code", "reasoning")),
        ModelInfo("mimo-v2.5-free", "MiMo V2.5", "Free", "Advanced reasoning", listOf("chat", "code", "reasoning", "math")),
        ModelInfo("north-mini-code-free", "North Mini Code", "Free", "Code specialist", listOf("code", "autocomplete")),
        ModelInfo("nemotron-3-ultra-free", "Nemotron 3 Ultra", "Free", "Ultra performance", listOf("chat", "code", "reasoning")),
        ModelInfo("big-pickle", "Big Pickle", "Free", "Maximum capability", listOf("chat", "code", "reasoning", "math", "creative")),
    )
}

@Serializable
data class ModelInfo(
    val id: String,
    val name: String,
    val tier: String,
    val description: String,
    val capabilities: List<String>,
)
