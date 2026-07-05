package com.opencode.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.android.core.filesystem.ProjectScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(onBack: () -> Unit) {
    var logs by remember { mutableStateOf(listOf<String>()) }
    var command by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scanner = remember { ProjectScanner() }

    LaunchedEffect(logs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.verticalScroll(scrollState).padding(12.dp)) {
                    if (logs.isEmpty()) {
                        Text(
                            "No output yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    logs.forEach { line ->
                        Text(
                            line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Enter command...") },
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    if (command.isNotBlank()) {
                        val cmd = command
                        command = ""
                        scope.launch {
                            logs = logs + "$ $cmd"
                            val result = withContext(Dispatchers.IO) {
                                try {
                                    val p = ProcessBuilder("sh", "-c", cmd)
                                        .redirectErrorStream(true)
                                        .start()
                                    val output = p.inputStream.bufferedReader().readText()
                                    p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
                                    val exitCode = p.exitValue()
                                    if (output.isNotEmpty()) output.trimEnd()
                                    else "(exit code $exitCode, no output)"
                                } catch (e: Exception) {
                                    "Error: ${e.message}"
                                }
                            }
                            logs = logs + result
                        }
                    }
                }) { Text("Run") }
            }
        }
    }
}
