package com.opencode.android.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.android.core.agent.AgentEngine
import com.opencode.android.core.agent.AgentRegistry
import com.opencode.android.core.config.ConfigManager
import com.opencode.android.core.session.SessionManager

@OptIn(ExperimentalMaterial3Api::class)
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
    val sessionList by sessions.sessions.collectAsState()
    val cfg by config.config.collectAsState()
    val isProcessing by agent.isProcessing.collectAsState()
    val currentAgent by agent.currentAgent.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Welcome header
        item {
            Column {
                Text(
                    "OpenCode",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "AI-Powered Coding Assistant",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Status cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusCard(
                    modifier = Modifier.weight(1f),
                    title = "Status",
                    value = if (isProcessing) "Processing" else "Ready",
                    icon = if (isProcessing) Icons.Default.Sync else Icons.Default.CheckCircle,
                    color = if (isProcessing) Color(0xFFFFB74D) else Color(0xFF81C784),
                )
                StatusCard(
                    modifier = Modifier.weight(1f),
                    title = "Agent",
                    value = AgentRegistry.getAgent(currentAgent).name,
                    icon = Icons.Default.SmartToy,
                    color = Color(android.graphics.Color.parseColor(AgentRegistry.getAgent(currentAgent).color)),
                )
                StatusCard(
                    modifier = Modifier.weight(1f),
                    title = "Sessions",
                    value = "${sessionList.size}",
                    icon = Icons.Default.Forum,
                    color = Color(0xFF90CAF9),
                )
            }
        }

        // Model selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Model", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text(cfg.defaultModel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(config.getAvailableModels()) { model ->
                            FilterChip(
                                selected = cfg.defaultModel == model.id,
                                onClick = { config.update { c -> c.copy(defaultModel = model.id) } },
                                label = { Text(model.name, fontSize = 12.sp) },
                                leadingIcon = {
                                    if (cfg.defaultModel == model.id) {
                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                            )
                        }
                    }
                }
            }
        }

        // Quick actions
        item {
            Text("Quick Actions", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    title = "New Chat",
                    subtitle = "Start conversation",
                    icon = Icons.Default.Add,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = {
                        val session = sessions.createSession(
                            title = "New Chat",
                            workspacePath = cfg.workspacePath,
                            modelId = cfg.defaultModel,
                            providerId = cfg.defaultProvider,
                        )
                        onNewChat(session.id)
                    }
                )
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    title = "Explore",
                    subtitle = "Browse files",
                    icon = Icons.Default.FolderOpen,
                    color = Color(0xFF81C784),
                    onClick = onOpenWorkspace,
                )
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    title = "Settings",
                    subtitle = "Configure",
                    icon = Icons.Default.Settings,
                    color = Color(0xFFFFB74D),
                    onClick = onSettings,
                )
            }
        }

        // Recent sessions
        if (sessionList.isNotEmpty()) {
            item {
                Text("Recent Sessions", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            }

            items(sessionList.take(5)) { session ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenChat(session.id) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Chat, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(session.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(session.modelId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Capabilities
        item {
            Text("Capabilities", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CapabilityBadge("File Operations", Icons.Default.Edit, Modifier.weight(1f))
                CapabilityBadge("Web Search", Icons.Default.Search, Modifier.weight(1f))
                CapabilityBadge("Shell Exec", Icons.Default.Terminal, Modifier.weight(1f))
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CapabilityBadge("Git", Icons.Default.Code, Modifier.weight(1f))
                CapabilityBadge("Code Edit", Icons.Default.EditNote, Modifier.weight(1f))
                CapabilityBadge("Sub-Agents", Icons.Default.AccountTree, Modifier.weight(1f))
            }
        }

        // Spacer for bottom nav
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun StatusCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
fun QuickActionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
fun CapabilityBadge(title: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}
