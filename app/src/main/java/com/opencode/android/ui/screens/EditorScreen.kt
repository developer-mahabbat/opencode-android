package com.opencode.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.opencode.android.core.diff.DiffEngine
import com.opencode.android.core.filesystem.ProjectScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val GradientStart = Color(0xFF667eea)
private val GradientEnd = Color(0xFF764ba2)
private val SurfaceBg = Color(0xFF0D1117)
private val CardBg = Color(0xFF161B22)
private val CodeBg = Color(0xFF1E1E2E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    filePath: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf("") }
    var modified by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var diffMode by remember { mutableStateOf(false) }
    var originalContent by remember { mutableStateOf("") }
    var showSaveSuccess by remember { mutableStateOf(false) }
    val codeScrollState = rememberScrollState()
    val lineScrollState = rememberScrollState()

    LaunchedEffect(filePath) {
        isLoading = true
        error = null
        try {
            val file = File(filePath)
            withContext(Dispatchers.IO) {
                if (file.exists()) {
                    content = file.readText()
                    originalContent = content
                } else {
                    error = "File not found: $filePath"
                }
            }
        } catch (e: Exception) {
            error = "Error reading file: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    // Sync scrolling between line numbers and code
    LaunchedEffect(codeScrollState.value) {
        lineScrollState.scrollTo(codeScrollState.value)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            filePath.substringAfterLast("/"),
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE6EDF3),
                            fontSize = 16.sp
                        )
                        Text(
                            filePath.substringBeforeLast("/").substringAfterLast("/"),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8B949E)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFFE6EDF3))
                    }
                },
                actions = {
                    if (modified) {
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    File(filePath).writeText(content)
                                    originalContent = content
                                    modified = false
                                    showSaveSuccess = true
                                    kotlinx.coroutines.delay(2000)
                                    showSaveSuccess = false
                                } catch (e: Exception) {
                                    error = "Save failed: ${e.message}"
                                }
                            }
                        }) {
                            Icon(Icons.Default.Save, "Save", tint = GradientStart)
                        }
                    }
                    IconButton(onClick = { diffMode = !diffMode }) {
                        Icon(
                            Icons.Default.Compare,
                            "Diff",
                            tint = if (diffMode) GradientStart else Color(0xFF8B949E)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF161B22)
                )
            )
        },
        containerColor = SurfaceBg,
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GradientStart)
                    }
                }
                error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFF85149), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(error!!, color = Color(0xFFF85149))
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onBack) {
                                Text("Go Back", color = GradientStart)
                            }
                        }
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Save success toast
                        if (showSaveSuccess) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF11998e).copy(alpha = 0.2f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF38ef7d), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Saved", color = Color(0xFF38ef7d), fontSize = 14.sp)
                                }
                            }
                        }

                        // Diff header
                        if (diffMode) {
                            val diffResult = DiffEngine().diff(originalContent, content)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = CardBg,
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, null, tint = GradientStart, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (diffResult.additions == 0 && diffResult.deletions == 0) "No changes" else "${diffResult.additions} additions, ${diffResult.deletions} deletions",
                                        fontSize = 13.sp,
                                        color = Color(0xFF8B949E)
                                    )
                                }
                            }
                        }

                        // Editor
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(CodeBg)
                        ) {
                            // Line numbers
                            val lineCount = content.lines().size
                            Column(
                                modifier = Modifier
                                    .width(48.dp)
                                    .verticalScroll(lineScrollState)
                                    .padding(top = 12.dp, bottom = 12.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                for (i in 1..lineCount) {
                                    Text(
                                        "$i",
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF484F58),
                                        modifier = Modifier.padding(end = 8.dp, bottom = 0.dp),
                                        lineHeight = 20.sp,
                                    )
                                }
                            }

                            // Code content
                            TextField(
                                value = content,
                                onValueChange = { content = it; modified = it != originalContent },
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(codeScrollState)
                                    .padding(12.dp),
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = Color(0xFFE6EDF3),
                                    lineHeight = 20.sp,
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = GradientStart,
                                ),
                                singleLine = false,
                            )
                        }
                    }
                }
            }
        }
    }
}
