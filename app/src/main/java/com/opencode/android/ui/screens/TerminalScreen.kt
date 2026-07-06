package com.opencode.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.android.core.filesystem.ProjectScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    workspace: String = "",
    onBack: () -> Unit,
) {
    var logs by remember { mutableStateOf(listOf<TerminalLine>()) }
    var command by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }

    LaunchedEffect(logs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    if (logs.isNotEmpty()) {
                        IconButton(onClick = { logs = emptyList() }) {
                            Icon(Icons.Default.DeleteSweep, "Clear")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Terminal output
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(12.dp),
                ) {
                    if (logs.isEmpty()) {
                        Text(
                            "Terminal ready. Type a command below.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFF6C7086),
                        )
                    }
                    logs.forEach { line ->
                        Row(modifier = Modifier.padding(vertical = 1.dp)) {
                            if (line.isCommand) {
                                Text(
                                    "$ ",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = Color(0xFFA6E3A1),
                                )
                                Text(
                                    line.text,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = Color(0xFFCDD6F4),
                                )
                            } else {
                                Text(
                                    line.text,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = if (line.isError) Color(0xFFF38BA8) else Color(0xFFCDD6F4),
                                )
                            }
                        }
                    }
                    if (isRunning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.dp,
                                color = Color(0xFFF9E2AF),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Running...", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFF9E2AF))
                        }
                    }
                }
            }

            // Command input
            Card(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Enter command...", fontSize = 13.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (command.isNotBlank() && !isRunning) {
                                val cmd = command
                                command = ""
                                isRunning = true
                                logs = logs + TerminalLine(cmd, isCommand = true)
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        try {
                                            val pb = ProcessBuilder("sh", "-c", cmd)
                                                .redirectErrorStream(true)
                                            if (workspace.isNotEmpty()) pb.directory(java.io.File(workspace))
                                            val p = pb.start()
                                            val output = p.inputStream.bufferedReader().readText()
                                            p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
                                            val exitCode = p.exitValue()
                                            if (output.isNotEmpty()) output.trimEnd()
                                            else "(exit code $exitCode)"
                                        } catch (e: Exception) {
                                            "Error: ${e.message}"
                                        }
                                    }
                                    logs = logs + TerminalLine(result, isError = result.startsWith("Error") || result.startsWith("exit code [^0]"))
                                    isRunning = false
                                }
                            }
                        }),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            if (command.isNotBlank() && !isRunning) {
                                val cmd = command
                                command = ""
                                isRunning = true
                                logs = logs + TerminalLine(cmd, isCommand = true)
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        try {
                                            val pb = ProcessBuilder("sh", "-c", cmd)
                                                .redirectErrorStream(true)
                                            if (workspace.isNotEmpty()) pb.directory(java.io.File(workspace))
                                            val p = pb.start()
                                            val output = p.inputStream.bufferedReader().readText()
                                            p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
                                            val exitCode = p.exitValue()
                                            if (output.isNotEmpty()) output.trimEnd()
                                            else "(exit code $exitCode)"
                                        } catch (e: Exception) {
                                            "Error: ${e.message}"
                                        }
                                    }
                                    logs = logs + TerminalLine(result, isError = result.startsWith("Error") || result.startsWith("exit code [^0]"))
                                    isRunning = false
                                }
                            }
                        },
                        enabled = command.isNotBlank() && !isRunning,
                    ) {
                        Icon(Icons.Default.Send, "Run", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

data class TerminalLine(val text: String, val isCommand: Boolean = false, val isError: Boolean = false)
