package com.opencode.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var maxTokens by remember { mutableStateOf(cfg.maxTokens.toString()) }
    var temperature by remember { mutableStateOf(cfg.temperature.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Model Selection
            item {
                SettingsSection("Model", Icons.Default.Memory) {
                    Text("Available Models", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    config.getAvailableModels().forEach { modelInfo ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (cfg.defaultModel == modelInfo.id)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            onClick = { config.update { c -> c.copy(defaultModel = modelInfo.id) } },
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(modelInfo.name, fontWeight = FontWeight.Medium)
                                    Text(modelInfo.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        modelInfo.capabilities.forEach { cap ->
                                            AssistChip(
                                                onClick = {},
                                                label = { Text(cap, fontSize = 10.sp) },
                                                modifier = Modifier.height(24.dp),
                                                shape = RoundedCornerShape(12.dp),
                                            )
                                        }
                                    }
                                }
                                if (cfg.defaultModel == modelInfo.id) {
                                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            // Provider Configuration
            item {
                SettingsSection("Provider", Icons.Default.Cloud) {
                    OutlinedTextField(
                        value = providerName,
                        onValueChange = { providerName = it },
                        label = { Text("Provider ID") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key (optional for free models)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }

            // Generation Settings
            item {
                SettingsSection("Generation", Icons.Default.Tune) {
                    OutlinedTextField(
                        value = maxTokens,
                        onValueChange = { maxTokens = it },
                        label = { Text("Max Tokens") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = temperature,
                        onValueChange = { temperature = it },
                        label = { Text("Temperature") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }

            // System Prompt
            item {
                SettingsSection("System Prompt", Icons.Default.Description) {
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        label = { Text("System Prompt") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }

            // Save button
            item {
                Button(
                    onClick = {
                        val id = providerName.lowercase().trim()
                        config.update { c ->
                            c.copy(
                                defaultProvider = id,
                                defaultModel = model,
                                systemPrompt = systemPrompt,
                                maxTokens = maxTokens.toIntOrNull() ?: 8192,
                                temperature = temperature.toDoubleOrNull() ?: 0.7,
                                providers = c.providers + (id to ProviderConfig(
                                    id = id,
                                    name = providerName,
                                    apiKey = apiKey,
                                    baseUrl = baseUrl,
                                    defaultModel = model,
                                ))
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Settings", fontWeight = FontWeight.Medium)
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun SettingsSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
