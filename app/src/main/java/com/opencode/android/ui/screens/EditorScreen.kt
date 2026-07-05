package com.opencode.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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

    LaunchedEffect(filePath) {
        content = scanner.readFile(filePath) ?: "Error: Could not read file"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(File(filePath).name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            File(filePath).parentFile?.path ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    if (isModified) {
                        IconButton(onClick = {
                            scanner.writeFile(filePath, content)
                            isModified = false
                        }) { Icon(Icons.Default.Save, "Save") }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.weight(1f)) {
                val lineCount = content.lines().size.coerceAtLeast(1)
                Column(
                    modifier = Modifier
                        .width(48.dp)
                        .verticalScroll(scrollState)
                        .padding(top = 8.dp)
                ) {
                    for (i in 1..lineCount) {
                        Text(
                            "$i",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                BasicTextField(
                    value = content,
                    onValueChange = { content = it; isModified = true },
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(8.dp),
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
