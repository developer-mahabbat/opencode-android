package com.opencode.android.ui.screens

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.android.core.config.ConfigManager
import com.opencode.android.core.filesystem.ProjectScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    scanner: ProjectScanner,
    config: ConfigManager,
    onOpenFile: (String) -> Unit,
    onBack: () -> Unit,
) {
    val cfg by config.config.collectAsState()
    var currentPath by remember { mutableStateOf(cfg.workspacePath) }
    var files by remember { mutableStateOf(listOf<com.opencode.android.core.filesystem.FileNode>()) }
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            val path = getRealPathFromUri(context, it)
            if (path.isNotEmpty()) {
                config.update { c -> c.copy(workspacePath = path) }
                currentPath = path
            }
        }
    }

    LaunchedEffect(currentPath) {
        if (currentPath.isNotEmpty()) {
            val info = scanner.scan(currentPath)
            files = info.structure
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Explorer", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { launcher.launch(null) }) {
                        Icon(Icons.Default.FolderOpen, "Open Folder")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (currentPath.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOpen, null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("No workspace selected", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { launcher.launch(null) }) { Text("Open Folder") }
                    }
                }
            } else {
                // Path breadcrumb
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)) {
                    Text(
                        currentPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                LazyColumn(contentPadding = PaddingValues(8.dp)) {
                    items(files) { node ->
                        FileNodeItem(node, 0, onOpenFile)
                    }
                }
            }
        }
    }
}

@Composable
fun FileNodeItem(
    node: com.opencode.android.core.filesystem.FileNode,
    depth: Int,
    onOpenFile: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp, top = 2.dp, bottom = 2.dp)
            .clickable {
                if (node.isDirectory) expanded = !expanded
                else onOpenFile(node.path)
            },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (node.isDirectory) {
                Icon(
                    if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.Folder, null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Spacer(Modifier.width(22.dp))
                Icon(
                    getFileIcon(node.name), null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(node.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (node.isFile) {
                Text(
                    formatSize(node.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (expanded && node.isDirectory) {
        node.children.forEach { child ->
            FileNodeItem(child, depth + 1, onOpenFile)
        }
    }
}

private fun getFileIcon(name: String) = when {
    name.endsWith(".kt") || name.endsWith(".kts") -> Icons.Default.Code
    name.endsWith(".java") -> Icons.Default.Code
    name.endsWith(".py") -> Icons.Default.Code
    name.endsWith(".js") || name.endsWith(".ts") -> Icons.Default.Code
    name.endsWith(".json") || name.endsWith(".xml") -> Icons.Default.DataObject
    name.endsWith(".md") -> Icons.Default.Description
    name.endsWith(".gradle") || name.endsWith(".gradle.kts") -> Icons.Default.Build
    else -> Icons.Default.InsertDriveFile
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1048576 -> "${bytes / 1024}KB"
    else -> "${bytes / 1048576}MB"
}

private fun getRealPathFromUri(context: android.content.Context, uri: Uri): String {
    val docId = DocumentsContract.getTreeDocumentId(uri)
    if (docId.isNotEmpty()) {
        val split = docId.split(":")
        if (split.size >= 2) {
            val type = split[0]
            val path = split.subList(1, split.size).joinToString(":")
            return when (type) {
                "primary" -> "${Environment.getExternalStorageDirectory()}/$path"
                "home" -> "${Environment.getExternalStorageDirectory()}/$path"
                else -> "/storage/emulated/0/$path"
            }
        }
    }
    return uri.path ?: ""
}
