package com.opencode.android.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
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
import com.opencode.android.core.filesystem.ProjectScanner
import com.opencode.android.core.session.SessionManager
import com.opencode.android.ui.screens.*
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Home", Icons.Outlined.Dashboard, Icons.Filled.Dashboard)
    data object Chat : Screen("chat", "Chat", Icons.Outlined.Chat, Icons.Filled.Chat)
    data object Editor : Screen("editor", "Editor", Icons.Outlined.Code, Icons.Filled.Code)
    data object Explorer : Screen("explorer", "Files", Icons.Outlined.FolderOpen, Icons.Filled.FolderOpen)
    data object Terminal : Screen("terminal", "Terminal", Icons.Outlined.Terminal, Icons.Filled.Terminal)
    data object Settings : Screen("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    config: ConfigManager,
    sessions: SessionManager,
    agent: AgentEngine,
    scanner: ProjectScanner,
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
    var sessionId by remember { mutableStateOf<String?>(null) }
    var filePath by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    MaterialTheme.colorScheme.surface,
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("OC", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("OpenCode", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text("v1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        val cfg by config.config.collectAsState()
                        if (cfg.workspacePath.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(cfg.workspacePath, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Navigation items
                val items = listOf(Screen.Dashboard, Screen.Chat, Screen.Editor, Screen.Explorer, Screen.Terminal, Screen.Settings)
                items.forEach { screen ->
                    val isSelected = currentScreen.route == screen.route
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                if (isSelected) screen.selectedIcon else screen.icon,
                                contentDescription = screen.label,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        label = {
                            Text(
                                screen.label,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        selected = isSelected,
                        onClick = {
                            currentScreen = screen
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        ),
                    )
                }

                Spacer(Modifier.weight(1f))

                // Agent selector
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Active Agent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        val currentAgent by agent.currentAgent.collectAsState()
                        val agentDef = AgentRegistry.getAgent(currentAgent)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(agentDef.color))))
                            Spacer(Modifier.width(8.dp))
                            Text(agentDef.name, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column {
                            Text(currentScreen.label, fontWeight = FontWeight.SemiBold)
                            val isProcessing by agent.isProcessing.collectAsState()
                            if (isProcessing) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Processing...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    },
                    actions = {
                        // Agent switcher
                        val currentAgent by agent.currentAgent.collectAsState()
                        val agents = AgentRegistry.getPrimaryAgents()
                        var showAgentMenu by remember { mutableStateOf(false) }

                        Box {
                            FilterChip(
                                selected = false,
                                onClick = { showAgentMenu = true },
                                label = { Text(AgentRegistry.getAgent(currentAgent).name, fontSize = 12.sp) },
                                leadingIcon = {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(AgentRegistry.getAgent(currentAgent).color))))
                                },
                                shape = RoundedCornerShape(20.dp),
                            )
                            DropdownMenu(expanded = showAgentMenu, onDismissRequest = { showAgentMenu = false }) {
                                agents.forEach { a ->
                                    DropdownMenuItem(
                                        text = { Text(a.name) },
                                        leadingIcon = { Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(a.color)))) },
                                        onClick = { agent.switchAgent(a.id); showAgentMenu = false }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    val items = listOf(Screen.Dashboard, Screen.Chat, Screen.Editor, Screen.Explorer, Screen.Terminal)
                    items.forEach { screen ->
                        val isSelected = currentScreen.route == screen.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (isSelected) screen.selectedIcon else screen.icon,
                                    contentDescription = screen.label
                                )
                            },
                            label = { Text(screen.label, fontSize = 11.sp) },
                            selected = isSelected,
                            onClick = { currentScreen = screen },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            ),
                        )
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (currentScreen) {
                    Screen.Dashboard -> DashboardScreen(
                        config = config,
                        sessions = sessions,
                        agent = agent,
                        onNewChat = { id -> sessionId = id; currentScreen = Screen.Chat },
                        onOpenChat = { id -> sessionId = id; currentScreen = Screen.Chat },
                        onOpenWorkspace = { currentScreen = Screen.Explorer },
                        onSettings = { currentScreen = Screen.Settings },
                    )
                    Screen.Chat -> ChatScreen(
                        sessionId = sessionId ?: run { currentScreen = Screen.Dashboard; return@Scaffold },
                        sessions = sessions,
                        agent = agent,
                        config = config,
                        onBack = { currentScreen = Screen.Dashboard },
                        onOpenEditor = { path -> filePath = path; currentScreen = Screen.Editor },
                        onOpenTerminal = { currentScreen = Screen.Terminal },
                    )
                    Screen.Editor -> EditorScreen(
                        filePath = filePath ?: run { currentScreen = Screen.Dashboard; return@Scaffold },
                        onBack = { currentScreen = Screen.Chat },
                    )
                    Screen.Explorer -> ExplorerScreen(
                        scanner = scanner,
                        config = config,
                        onOpenFile = { path -> filePath = path; currentScreen = Screen.Editor },
                        onBack = { currentScreen = Screen.Dashboard },
                    )
                    Screen.Terminal -> TerminalScreen(
                        workspace = config.config.value.workspacePath,
                        onBack = { currentScreen = Screen.Chat },
                    )
                    Screen.Settings -> SettingsScreen(
                        config = config,
                        onBack = { currentScreen = Screen.Dashboard },
                    )
                }
            }
        }
    }
}
