package com.opencode.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.opencode.android.core.config.ConfigManager
import com.opencode.android.core.session.SessionManager
import com.opencode.android.core.agent.AgentEngine
import com.opencode.android.core.filesystem.ProjectScanner
import com.opencode.android.ui.theme.OpenCodeTheme
import com.opencode.android.ui.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val configManager = ConfigManager(this)
        val sessionManager = SessionManager()
        val agentEngine = AgentEngine(sessionManager, configManager)
        val projectScanner = ProjectScanner()

        setContent {
            OpenCodeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(configManager, sessionManager, agentEngine, projectScanner)
                }
            }
        }
    }
}
