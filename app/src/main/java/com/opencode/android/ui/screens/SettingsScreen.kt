package com.opencode.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.android.core.config.ConfigManager

private val GradientStart = Color(0xFF667eea)
private val GradientEnd = Color(0xFF764ba2)
private val SurfaceBg = Color(0xFF0D1117)
private val CardBg = Color(0xFF161B22)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: ConfigManager,
    onBack: () -> Unit,
) {
    val cfg by config.config.collectAsState()
    val activeProvider = cfg.providers[cfg.defaultProvider]
    var tempUrl by remember(activeProvider) { mutableStateOf(activeProvider?.baseUrl ?: "") }
    var tempKey by remember(activeProvider) { mutableStateOf(activeProvider?.apiKey ?: "") }
    var tempModel by remember(cfg.defaultModel) { mutableStateOf(cfg.defaultModel) }
    var tempMaxTokens by remember(cfg.maxTokens.toString()) { mutableStateOf(cfg.maxTokens.toString()) }
    var tempProvider by remember(cfg.defaultProvider) { mutableStateOf(cfg.defaultProvider) }
    var showSaveSuccess by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold, color = Color(0xFFE6EDF3)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFFE6EDF3))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val maxTokens = tempMaxTokens.toIntOrNull() ?: cfg.maxTokens
                        config.update { c ->
                            val providers = c.providers.toMutableMap()
                            val existing = providers[tempProvider] ?: com.opencode.android.core.config.ProviderConfig(
                                id = tempProvider,
                                name = tempProvider.replaceFirstChar { it.uppercase() },
                            )
                            providers[tempProvider] = existing.copy(
                                baseUrl = tempUrl.trim(),
                                apiKey = tempKey.trim(),
                            )
                            c.copy(
                                defaultProvider = tempProvider,
                                defaultModel = tempModel.trim(),
                                maxTokens = maxTokens.coerceIn(100, 16000),
                                providers = providers,
                            )
                        }
                        showSaveSuccess = true
                    }) {
                        Icon(Icons.Default.Check, "Save", tint = GradientStart)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF161B22))
            )
        },
        containerColor = SurfaceBg,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showSaveSuccess) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF11998e).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF38ef7d), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Settings saved", fontWeight = FontWeight.Medium, color = Color(0xFF38ef7d))
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Provider", fontWeight = FontWeight.SemiBold, color = Color(0xFFE6EDF3))
                    Spacer(Modifier.height(12.dp))
                    listOf(
                        "Zen (OpenCode Free)" to "zen",
                        "OpenRouter" to "openrouter",
                        "Custom" to "custom"
                    ).forEach { (label, value) ->
                        val isSelected = tempProvider == value
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) GradientStart.copy(alpha = 0.1f) else Color.Transparent),
                            color = Color.Transparent,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { tempProvider = value },
                                    colors = RadioButtonDefaults.colors(selectedColor = GradientStart),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(label, color = Color(0xFFE6EDF3), fontSize = 14.sp)
                                    if (value == "zen") {
                                        Text(
                                            "Free models, no API key needed",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF8B949E)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("API Settings", fontWeight = FontWeight.SemiBold, color = Color(0xFFE6EDF3))
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GradientStart,
                            unfocusedBorderColor = Color(0xFF30363D),
                            focusedContainerColor = Color(0xFF0D1117),
                            unfocusedContainerColor = Color(0xFF0D1117),
                            focusedTextColor = Color(0xFFE6EDF3),
                            unfocusedTextColor = Color(0xFFE6EDF3),
                            focusedLabelColor = GradientStart,
                            unfocusedLabelColor = Color(0xFF8B949E),
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))

                    if (tempProvider != "zen") {
                        OutlinedTextField(
                            value = tempKey,
                            onValueChange = { tempKey = it },
                            label = { Text("API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GradientStart,
                                unfocusedBorderColor = Color(0xFF30363D),
                                focusedContainerColor = Color(0xFF0D1117),
                                unfocusedContainerColor = Color(0xFF0D1117),
                                focusedTextColor = Color(0xFFE6EDF3),
                                unfocusedTextColor = Color(0xFFE6EDF3),
                                focusedLabelColor = GradientStart,
                                unfocusedLabelColor = Color(0xFF8B949E),
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = tempModel,
                        onValueChange = { tempModel = it },
                        label = { Text("Model") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GradientStart,
                            unfocusedBorderColor = Color(0xFF30363D),
                            focusedContainerColor = Color(0xFF0D1117),
                            unfocusedContainerColor = Color(0xFF0D1117),
                            focusedTextColor = Color(0xFFE6EDF3),
                            unfocusedTextColor = Color(0xFFE6EDF3),
                            focusedLabelColor = GradientStart,
                            unfocusedLabelColor = Color(0xFF8B949E),
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = tempMaxTokens,
                        onValueChange = { tempMaxTokens = it.filter { c -> c.isDigit() } },
                        label = { Text("Max Tokens") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GradientStart,
                            unfocusedBorderColor = Color(0xFF30363D),
                            focusedContainerColor = Color(0xFF0D1117),
                            unfocusedContainerColor = Color(0xFF0D1117),
                            focusedTextColor = Color(0xFFE6EDF3),
                            unfocusedTextColor = Color(0xFFE6EDF3),
                            focusedLabelColor = GradientStart,
                            unfocusedLabelColor = Color(0xFF8B949E),
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                }
            }

            if (tempProvider == "zen") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Available Free Models", fontWeight = FontWeight.SemiBold, color = Color(0xFFE6EDF3))
                        Spacer(Modifier.height(8.dp))
                        listOf(
                            "deepseek-v4-flash-free" to "Fast general model",
                            "mimo-v2.5-free" to "Advanced coding model",
                            "north-mini-code-free" to "Lightweight code model",
                            "nemotron-3-ultra-free" to "NVIDIA reasoning model",
                            "big-pickle" to "Top-tier coding model"
                        ).forEach { (model, desc) ->
                            val isSelected = tempModel == model
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) GradientStart.copy(alpha = 0.1f) else Color.Transparent)
                                    .padding(10.dp),
                                color = Color.Transparent,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { tempModel = model },
                                        colors = RadioButtonDefaults.colors(selectedColor = GradientStart),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(model, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFE6EDF3))
                                        Text(desc, style = MaterialTheme.typography.bodySmall, color = Color(0xFF8B949E))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
