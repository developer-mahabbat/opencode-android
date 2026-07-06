package com.opencode.android.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.opencode.android.core.filesystem.ProjectScanner
import com.opencode.android.core.session.SessionManager
import com.opencode.android.ui.screens.*
import kotlinx.coroutines.launch

private val GradientStart = Color(0xFF667eea)
private val GradientEnd = Color(0xFF764ba2)

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
    val isProcessing by agent.isProcessing.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerContainerColor = Color(0xFF0D1117),
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(GradientStart.copy(alpha = 0.15f), Color(0xFF0D1117))
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
                                    .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("OC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("OpenCode", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFE6EDF3))
                                Text("v2.0.0", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8B949E))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        val cfg by config.config.collectAsState()
                        if (cfg.workspacePath.isNotEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF161B22),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, null, tint = GradientStart, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(cfg.workspacePath, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFF8B949E))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                val items = listOf(Screen.Dashboard, Screen.Chat, Screen.Editor, Screen.Explorer, Screen.Terminal, Screen.Settings)
                items.forEach { screen ->
                    val isSelected = currentScreen.route == screen.route
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                if (isSelected) screen.selectedIcon else screen.icon,
                                contentDescription = screen.label,
                                tint = if (isSelected) GradientStart else Color(0xFF8B949E)
                            )
                        },
                        label = {
                            Text(
                                screen.label,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = if (isSelected) Color(0xFFE6EDF3) else Color(0xFF8B949E)
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
                            selectedContainerColor = GradientStart.copy(alpha = 0.1f),
                        ),
                    )
                }

                Spacer(Modifier.weight(1f))

                // Active agent
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    color = Color(0xFF161B22),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Active Agent", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8B949E))
                        Spacer(Modifier.height(4.dp))
                        val currentAgent by agent.currentAgent.collectAsState()
                        val agentDef = AgentRegistry.getAgent(currentAgent)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(agentDef.color))))
                            Spacer(Modifier.width(8.dp))
                            Text(agentDef.name, fontWeight = FontWeight.Medium, color = Color(0xFFE6EDF3))
                        }
                    }
                }
            }
        }
    ) {
        // Hide top/bottom bars when in Chat for full-screen
        if (currentScreen == Screen.Chat && sessionId != null) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
                ChatScreen(
                    sessionId = sessionId!!,
                    sessions = sessions,
                    agent = agent,
                    config = config,
                    onBack = { currentScreen = Screen.Dashboard },
                    onOpenEditor = { path -> filePath = path; currentScreen = Screen.Editor },
                    onOpenTerminal = { currentScreen = Screen.Terminal },
                )
            }
        } else {
            Scaffold(
                containerColor = Color(0xFF0D1117),
                topBar = {
                    if (currentScreen != Screen.Chat) {
                        CenterAlignedTopAppBar(
                            title = {
                                Column {
                                    Text(currentScreen.label, fontWeight = FontWeight.SemiBold, color = Color(0xFFE6EDF3))
                                    if (isProcessing) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = GradientStart)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Processing...", style = MaterialTheme.typography.bodySmall, color = GradientStart)
                                        }
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, "Menu", tint = Color(0xFFE6EDF3))
                                }
                            },
                            actions = {
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
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = Color(0xFF21262D),
                                            labelColor = Color(0xFFE6EDF3),
                                        ),
                                    )
                                    DropdownMenu(expanded = showAgentMenu, onDismissRequest = { showAgentMenu = false }) {
                                        agents.forEach { a ->
                                            DropdownMenuItem(
                                                text = { Text(a.name, color = Color(0xFFE6EDF3)) },
                                                leadingIcon = { Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(a.color)))) },
                                                onClick = { agent.switchAgent(a.id); showAgentMenu = false }
                                            )
                                        }
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = Color(0xFF161B22),
                            )
                        )
                    }
                },
                bottomBar = {
                    if (currentScreen != Screen.Chat) {
                        NavigationBar(
                            containerColor = Color(0xFF161B22),
                            tonalElevation = 0.dp,
                        ) {
                            val navItems = listOf(Screen.Dashboard, Screen.Chat, Screen.Editor, Screen.Explorer, Screen.Terminal)
                            navItems.forEach { screen ->
                                val isSelected = currentScreen.route == screen.route
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            if (isSelected) screen.selectedIcon else screen.icon,
                                            contentDescription = screen.label
                                        )
                                    },
                                    label = { Text(screen.label, fontSize = 10.sp) },
                                    selected = isSelected,
                                    onClick = {
                                        if (screen == Screen.Chat && sessionId == null) {
                                            val cfg = config.config.value
                                            val session = sessions.createSession(
                                                title = "New Chat",
                                                workspacePath = cfg.workspacePath.ifEmpty { "/sdcard" },
                                                modelId = cfg.defaultModel,
                                                providerId = cfg.defaultProvider,
                                                agentId = agent.currentAgent.value,
                                            )
                                            sessionId = session.id
                                        }
                                        currentScreen = screen
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = GradientStart,
                                        unselectedIconColor = Color(0xFF8B949E),
                                        selectedTextColor = GradientStart,
                                        unselectedTextColor = Color(0xFF8B949E),
                                        indicatorColor = GradientStart.copy(alpha = 0.1f),
                                    ),
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding).background(Color(0xFF0D1117))) {
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
                            onBack = { currentScreen = Screen.Dashboard },
                        )
                        Screen.Settings -> SettingsScreen(
                            config = config,
                            onBack = { currentScreen = Screen.Dashboard },
                        )
                        else -> {}
                    }
                }
            }
        }
    }
}
