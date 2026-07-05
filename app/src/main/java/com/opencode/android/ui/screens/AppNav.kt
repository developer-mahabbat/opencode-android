package com.opencode.android.ui.screens

import androidx.compose.runtime.*
import com.opencode.android.core.config.ConfigManager
import com.opencode.android.core.session.SessionManager
import com.opencode.android.core.agent.AgentEngine
import com.opencode.android.core.filesystem.ProjectScanner

enum class Screen { HOME, CHAT, EDITOR, EXPLORER, TERMINAL, SETTINGS }

@Composable
fun AppNavHost(config: ConfigManager, sessions: SessionManager, agent: AgentEngine, scanner: ProjectScanner) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var sessionId by remember { mutableStateOf<String?>(null) }
    var filePath by remember { mutableStateOf<String?>(null) }

    when (screen) {
        Screen.HOME -> HomeScreen(
            config = config,
            sessions = sessions,
            onNewChat = { id -> sessionId = id; screen = Screen.CHAT },
            onOpenChat = { id -> sessionId = id; screen = Screen.CHAT },
            onOpenWorkspace = { screen = Screen.EXPLORER },
            onSettings = { screen = Screen.SETTINGS }
        )
        Screen.CHAT -> ChatScreen(
            sessionId = sessionId ?: return,
            sessions = sessions,
            agent = agent,
            config = config,
            onBack = { screen = Screen.HOME },
            onOpenEditor = { path -> filePath = path; screen = Screen.EDITOR },
            onOpenTerminal = { screen = Screen.TERMINAL }
        )
        Screen.EDITOR -> EditorScreen(
            filePath = filePath ?: return,
            onBack = { screen = Screen.CHAT }
        )
        Screen.EXPLORER -> ExplorerScreen(
            scanner = scanner,
            config = config,
            onOpenFile = { path -> filePath = path; screen = Screen.EDITOR },
            onBack = { screen = Screen.HOME }
        )
        Screen.TERMINAL -> TerminalScreen(
            onBack = { screen = Screen.CHAT }
        )
        Screen.SETTINGS -> SettingsScreen(
            config = config,
            onBack = { screen = Screen.HOME }
        )
    }
}
