package com.opencode.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

private val GradientStart = Color(0xFF667eea)
private val GradientEnd = Color(0xFF764ba2)
private val SurfaceBg = Color(0xFF0D1117)
private val CodeBg = Color(0xFF1E1E2E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    workspace: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf("") }
    var outputLines by remember { mutableStateOf<List<Pair<String, Boolean>>>(emptyList()) }
    var isRunning by remember { mutableStateOf(false) }
    var currentDir by remember { mutableStateOf(workspace.ifEmpty { "/sdcard" }) }

    fun executeCommand(cmd: String) {
        if (cmd.isBlank()) return
        scope.launch {
            isRunning = true
            outputLines = outputLines + Pair("$ $cmd", false)

            // Handle cd locally
            if (cmd.trimStart().startsWith("cd ")) {
                val target = cmd.trimStart().removePrefix("cd ").trim()
                val newDir = if (target.startsWith("/")) target else "$currentDir/$target"
                currentDir = newDir
                outputLines = outputLines + Pair("Directory changed to $newDir", true)
                isRunning = false
                command = ""
                return@launch
            }

            try {
                val result = withContext(Dispatchers.IO) {
                    val process = ProcessBuilder("sh", "-c", cmd)
                        .directory(java.io.File(currentDir))
                        .redirectErrorStream(true)
                        .start()

                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val lines = mutableListOf<String>()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        lines.add(line!!)
                    }
                    process.waitFor()
                    val exitCode = process.exitValue()
                    if (lines.isEmpty() && exitCode == 0) {
                        listOf("(no output)")
                    } else {
                        lines
                    }
                }

                outputLines = outputLines + result.map { Pair(it, true) }
            } catch (e: Exception) {
                outputLines = outputLines + Pair("Error: ${e.message}", false)
            }
            isRunning = false
            command = ""
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Terminal", fontWeight = FontWeight.SemiBold, color = Color(0xFFE6EDF3)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFFE6EDF3))
                    }
                },
                actions = {
                    IconButton(onClick = { outputLines = emptyList() }) {
                        Icon(Icons.Default.DeleteSweep, "Clear", tint = Color(0xFFF85149))
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
                .fillMaxSize()
                .background(CodeBg)
        ) {
            // Output area
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(outputLines) { (line, isOutput) ->
                    Text(
                        line,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = when {
                            line.startsWith("$") -> GradientStart
                            isOutput && line.startsWith("Error") -> Color(0xFFF85149)
                            isOutput -> Color(0xFFC9D1D9)
                            line.contains("changed to") -> Color(0xFF38ef7d)
                            else -> Color(0xFFE6EDF3)
                        },
                        lineHeight = 18.sp,
                    )
                }
            }

            // Current directory indicator
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF161B22),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, null, tint = GradientStart, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        currentDir,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF8B949E),
                        maxLines = 1,
                    )
                }
            }

            // Input
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF161B22),
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = command,
                        onValueChange = { command = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Enter command...", color = Color(0xFF484F58), fontSize = 14.sp) },
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = Color(0xFFE6EDF3),
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0D1117),
                            unfocusedContainerColor = Color(0xFF0D1117),
                            focusedIndicatorColor = GradientStart,
                            unfocusedIndicatorColor = Color(0xFF30363D),
                            cursorColor = GradientStart,
                        ),
                        singleLine = true,
                        enabled = !isRunning,
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { executeCommand(command) },
                        enabled = command.isNotBlank() && !isRunning,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (command.isNotBlank() && !isRunning)
                                        Brush.linearGradient(listOf(GradientStart, GradientEnd))
                                    else
                                        Brush.linearGradient(listOf(Color(0xFF30363D), Color(0xFF30363D)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                )
                            } else {
                                Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
