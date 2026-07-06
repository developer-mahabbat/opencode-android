package com.opencode.android.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.android.core.agent.AgentEngine
import com.opencode.android.core.agent.AgentRegistry
import com.opencode.android.core.config.ConfigManager
import com.opencode.android.core.session.SessionManager
import java.text.SimpleDateFormat
import java.util.*

private val GradientStart = Color(0xFF667eea)
private val GradientEnd = Color(0xFF764ba2)
private val CardBg = Color(0xFF161B22)
private val SurfaceBg = Color(0xFF0D1117)

@Composable
fun DashboardScreen(
    config: ConfigManager,
    sessions: SessionManager,
    agent: AgentEngine,
    onNewChat: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenWorkspace: () -> Unit,
    onSettings: () -> Unit,
) {
    val cfg by config.config.collectAsState()
    val sessionsList by sessions.sessions.collectAsState()
    val currentAgent by agent.currentAgent.collectAsState()
    val isProcessing by agent.isProcessing.collectAsState()
    val context = LocalContext.current
    val agentDef = AgentRegistry.getAgent(currentAgent)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SurfaceBg),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("OpenCode", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                    Text("AI Coding Assistant", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF8B949E))
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("OC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        // Status cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusCard(
                    modifier = Modifier.weight(1f),
                    title = "Model",
                    value = cfg.defaultModel,
                    icon = Icons.Default.SmartToy,
                    gradient = listOf(GradientStart, GradientEnd)
                )
                StatusCard(
                    modifier = Modifier.weight(1f),
                    title = "Agent",
                    value = agentDef.name,
                    icon = Icons.Default.AccountTree,
                    gradient = listOf(Color(0xFF11998e), Color(0xFF38ef7d))
                )
            }
        }

        // Agent selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Active Agent", fontWeight = FontWeight.SemiBold, color = Color(0xFFE6EDF3))
                    Spacer(Modifier.height(12.dp))
                    val agents = AgentRegistry.getPrimaryAgents()
                    agents.forEach { a ->
                        val isSelected = currentAgent == a.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) GradientStart.copy(alpha = 0.1f) else Color.Transparent
                                )
                                .clickable { agent.switchAgent(a.id) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(a.color)))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(a.name, fontWeight = FontWeight.Medium, color = Color(0xFFE6EDF3))
                                Text(
                                    when (a.id) {
                                        "coder" -> "Write & refactor code"
                                        "task" -> "Complex multi-step tasks"
                                        "title" -> "Generate chat titles"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF8B949E)
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, null, tint = GradientStart)
                            }
                        }
                    }
                }
            }
        }

        // Quick actions
        item {
            Text("Quick Actions", fontWeight = FontWeight.SemiBold, color = Color(0xFFE6EDF3))
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Add,
                    label = "New Chat",
                    gradient = listOf(GradientStart, GradientEnd),
                    onClick = {
                        val session = sessions.createSession(
                            title = "New Chat",
                            workspacePath = cfg.workspacePath.ifEmpty { "/sdcard" },
                            modelId = cfg.defaultModel,
                            providerId = cfg.defaultProvider,
                            agentId = currentAgent,
                        )
                        onNewChat(session.id)
                    }
                )
                QuickAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.FolderOpen,
                    label = "Open Folder",
                    gradient = listOf(Color(0xFF11998e), Color(0xFF38ef7d)),
                    onClick = onOpenWorkspace
                )
                QuickAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    gradient = listOf(Color(0xFFfc4a1a), Color(0xFFf7b733)),
                    onClick = onSettings
                )
            }
        }

        // Recent sessions
        if (sessionsList.isNotEmpty()) {
            item {
                Text("Recent Sessions", fontWeight = FontWeight.SemiBold, color = Color(0xFFE6EDF3))
            }
            items(sessionsList.take(5)) { session ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChat(session.id) },
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(GradientStart.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Chat, null, tint = GradientStart, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(session.title, fontWeight = FontWeight.Medium, color = Color(0xFFE6EDF3), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${sessions.getMessages(session.id).size} messages · ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.createdAt))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8B949E)
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF8B949E))
                    }
                }
            }
        }

        // Workspace info
        if (cfg.workspacePath.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF11998e).copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Folder, null, tint = Color(0xFF11998e), modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Workspace", fontWeight = FontWeight.Medium, color = Color(0xFFE6EDF3))
                            Text(cfg.workspacePath, style = MaterialTheme.typography.bodySmall, color = Color(0xFF8B949E), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    gradient: List<Color>,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(brush = Brush.linearGradient(gradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.bodySmall, color = Color(0xFF8B949E))
            Spacer(Modifier.height(2.dp))
            Text(value, fontWeight = FontWeight.SemiBold, color = Color(0xFFE6EDF3), fontSize = 14.sp)
        }
    }
}

@Composable
private fun QuickAction(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    gradient: List<Color>,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(brush = Brush.linearGradient(gradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFE6EDF3))
        }
    }
}
