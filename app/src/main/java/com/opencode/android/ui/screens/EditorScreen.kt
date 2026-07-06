package com.opencode.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.android.core.filesystem.ProjectScanner
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(filePath: String, onBack: () -> Unit) {
    var content by remember { mutableStateOf("") }
    var isModified by remember { mutableStateOf(false) }
    val scanner = remember { ProjectScanner() }
    val scrollState = rememberScrollState()
    val fileName = File(filePath).name
    val lineCount = content.lines().size.coerceAtLeast(1)

    LaunchedEffect(filePath) {
        content = scanner.readFile(filePath) ?: "Error: Could not read file"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(fileName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(
                            File(filePath).parentFile?.name ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    if (isModified) {
                        FilledTonalButton(
                            onClick = { scanner.writeFile(filePath, content); isModified = false },
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save", fontSize = 12.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Status bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Lines: $lineCount", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Chars: ${content.length}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isModified) Text("Modified", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            // Editor
            Row(modifier = Modifier.weight(1f)) {
                // Line numbers
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    modifier = Modifier.width(48.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(scrollState)
                            .padding(top = 12.dp),
                    ) {
                        for (i in 1..lineCount) {
                            Text(
                                "$i",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                            )
                        }
                    }
                }

                // Code
                BasicTextField(
                    value = content,
                    onValueChange = { content = it; isModified = true },
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(12.dp),
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
