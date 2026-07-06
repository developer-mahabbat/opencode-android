package com.opencode.android.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.android.core.config.ConfigManager
import com.opencode.android.core.filesystem.FileNode
import com.opencode.android.core.filesystem.ProjectScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GradientStart = Color(0xFF667eea)
private val GradientEnd = Color(0xFF764ba2)
private val SurfaceBg = Color(0xFF0D1117)
private val CardBg = Color(0xFF161B22)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    scanner: ProjectScanner,
    config: ConfigManager,
    onOpenFile: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg by config.config.collectAsState()
    var root by remember { mutableStateOf<FileNode?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedDir by remember { mutableStateOf(cfg.workspacePath.ifEmpty { null }) }
    var expandedDirs by remember { mutableStateOf(setOf<String>()) }

    // Load on mount or when selectedDir changes
    LaunchedEffect(selectedDir) {
        if (selectedDir != null) {
            isLoading = true
            root = withContext(Dispatchers.IO) { scanner.scan(selectedDir!!) }
            isLoading = false
        }
    }

    // Folder picker
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            uri?.let {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val path = uriToPath(it, context)
                if (path.isNotEmpty()) {
                    selectedDir = path
                    config.update { it.copy(workspacePath = path) }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("File Explorer", fontWeight = FontWeight.SemiBold, color = Color(0xFFE6EDF3)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFFE6EDF3))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        }
                        folderLauncher.launch(intent)
                    }) {
                        Icon(Icons.Default.FolderOpen, "Open Folder", tint = GradientStart)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF161B22))
            )
        },
        containerColor = SurfaceBg,
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                selectedDir == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.FolderOpen, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                            Spacer(Modifier.height(20.dp))
                            Text("No Workspace Selected", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                            Spacer(Modifier.height(8.dp))
                            Text("Select a folder to explore", color = Color(0xFF8B949E))
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    }
                                    folderLauncher.launch(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GradientStart),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Open Folder")
                            }
                        }
                    }
                }
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GradientStart)
                    }
                }
                root != null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(root!!.children) { node ->
                            FileNodeItem(
                                node = node,
                                expandedDirs = expandedDirs,
                                onToggleExpand = { path ->
                                    expandedDirs = if (expandedDirs.contains(path)) expandedDirs - path else expandedDirs + path
                                },
                                onOpenFile = onOpenFile,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileNodeItem(
    node: FileNode,
    expandedDirs: Set<String>,
    onToggleExpand: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    depth: Int = 0,
) {
    val isExpanded = expandedDirs.contains(node.path)
    val isDir = node.isDirectory

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                if (isDir) onToggleExpand(node.path) else onOpenFile(node.path)
            }
            .padding(start = (depth * 16 + 8).dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isDir) {
            Icon(
                if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                null,
                tint = Color(0xFF8B949E),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                null,
                tint = GradientStart,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Spacer(Modifier.width(22.dp))
            val fileType = when {
                node.name.endsWith(".kt") || node.name.endsWith(".kts") -> "kotlin"
                node.name.endsWith(".json") -> "json"
                node.name.endsWith(".xml") -> "xml"
                node.name.endsWith(".gradle") || node.name.endsWith(".gradle.kts") -> "gradle"
                node.name.endsWith(".gitignore") || node.name.endsWith(".git") -> "git"
                else -> "other"
            }
            Icon(
                when (fileType) {
                    "kotlin" -> Icons.Default.Code
                    "json" -> Icons.Default.DataObject
                    "xml" -> Icons.Default.FilePresent
                    "gradle" -> Icons.Default.Build
                    "git" -> Icons.Default.History
                    else -> Icons.Default.InsertDriveFile
                },
                null,
                tint = when (fileType) {
                    "kotlin" -> Color(0xFFA97BFF)
                    "json" -> Color(0xFF38ef7d)
                    "xml" -> Color(0xFFf7b733)
                    "gradle" -> Color(0xFF11998e)
                    else -> Color(0xFF8B949E)
                },
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            node.name,
            fontSize = 14.sp,
            color = if (isDir) Color(0xFFE6EDF3) else Color(0xFFC9D1D9),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (node.size != null && node.size > 0) {
            Text(
                formatSize(node.size),
                fontSize = 11.sp,
                color = Color(0xFF484F58),
            )
        }
    }

    if (isExpanded && isDir) {
        node.children.forEach { child ->
            FileNodeItem(
                node = child,
                expandedDirs = expandedDirs,
                onToggleExpand = onToggleExpand,
                onOpenFile = onOpenFile,
                depth = depth + 1,
            )
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024))}MB"
}

private fun uriToPath(uri: Uri, context: android.content.Context): String {
    val docId = uri.lastPathSegment ?: return ""
    // SAF DocumentUri: /tree/primary:path or /tree/documentId:path
    val path = docId.substringAfter(":", "")
    val volume = docId.substringBefore(":", "")
    return "/storage/$volume/$path"
}
