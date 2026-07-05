package com.opencode.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.android.core.config.ConfigManager
import com.opencode.android.core.config.ProviderConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(config: ConfigManager, onBack: () -> Unit) {
    val cfg by config.config.collectAsState()
    var providerName by remember { mutableStateOf(cfg.defaultProvider) }
    var apiKey by remember { mutableStateOf(cfg.providers[cfg.defaultProvider]?.apiKey ?: "") }
    var baseUrl by remember { mutableStateOf(cfg.providers[cfg.defaultProvider]?.baseUrl ?: "") }
    var model by remember { mutableStateOf(cfg.defaultModel) }
    var systemPrompt by remember { mutableStateOf(cfg.systemPrompt) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Medium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("LLM Provider", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(value = providerName, onValueChange = { providerName = it },
                label = { Text("Provider ID (openai/anthropic/gemini/openrouter)") },
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(value = apiKey, onValueChange = { apiKey = it },
                label = { Text("API Key") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it },
                label = { Text("Base URL (optional)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(value = model, onValueChange = { model = it },
                label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))

            Text("System Prompt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(value = systemPrompt, onValueChange = { systemPrompt = it },
                label = { Text("System Prompt") }, modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp))
            Spacer(Modifier.height(16.dp))

            Button(onClick = {
                val id = providerName.lowercase().trim()
                config.update { c ->
                    c.copy(
                        defaultProvider = id,
                        defaultModel = model,
                        systemPrompt = systemPrompt,
                        providers = c.providers + (id to ProviderConfig(
                            id = id, name = providerName, apiKey = apiKey, baseUrl = baseUrl, defaultModel = model
                        ))
                    )
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Save Settings")
            }
        }
    }
}
