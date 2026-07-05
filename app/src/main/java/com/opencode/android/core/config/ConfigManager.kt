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
    val defaultProvider: String = "openai",
    val defaultModel: String = "gpt-4o",
    val workspacePath: String = "",
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val providers: Map<String, ProviderConfig> = emptyMap()
)

@Serializable
data class ProviderConfig(
    val id: String,
    val name: String,
    val apiKey: String = "",
    val baseUrl: String = "",
    val defaultModel: String = "gpt-4o",
    val enabled: Boolean = true
)

const val DEFAULT_SYSTEM_PROMPT = """You are OpenCode, an expert AI coding assistant running on Android.

You have direct access to the user's project files and can read, write, edit, create, delete, and rename files and folders.

CAPABILITIES:
- Read and analyze source code in any language
- Write new files and modify existing ones
- Search through entire codebases
- Execute shell commands
- Manage git repositories
- Generate diffs and patches
- Understand project structure and dependencies

BEHAVIOR:
- Be concise and direct
- Show code changes when editing files
- Explain what you're doing before making changes
- Ask for confirmation before destructive operations
- Use tools to interact with the filesystem
- Think step by step for complex tasks"""

class ConfigManager(context: Context) {
    private val prefs = context.getSharedPreferences("opencode_prefs", Context.MODE_PRIVATE)
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
}
