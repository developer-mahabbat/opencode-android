package com.opencode.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.opencode.android.core.agent.AgentEngine
import com.opencode.android.core.config.ConfigManager
import com.opencode.android.core.session.SessionManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String,
    sessions: SessionManager,
    agent: AgentEngine,
    config: ConfigManager,
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    onOpenTerminal: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val isProcessing by agent.isProcessing.collectAsState()
    val output by agent.currentOutput.collectAsState()
    var messages by remember { mutableStateOf(sessions.getMessages(sessionId)) }
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size, output) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Chat", fontWeight = FontWeight.Medium)
                        if (isProcessing) Text(
                            "Thinking...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = onOpenTerminal) { Icon(Icons.Default.Term, "Terminal") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(msg.role, msg.content)
                }
                if (output.isNotEmpty()) {
                    item { ChatBubble("assistant", output, isStreaming = true) }
                }
            }

            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 120.dp),
                        placeholder = { Text("Ask OpenCode...") },
                        enabled = !isProcessing,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (input.isNotBlank() && !isProcessing) {
                                val msg = input
                                input = ""
                                keyboard?.hide()
                                scope.launch {
                                    agent.processMessage(sessionId, msg)
                                    messages = sessions.getMessages(sessionId)
                                }
                            }
                        }),
                        maxLines = 4
                    )
                    Spacer(Modifier.width(8.dp))
                    if (isProcessing) {
                        FilledIconButton(
                            onClick = { agent.cancel() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Stop, "Stop")
                        }
                    } else {
                        FilledIconButton(
                            onClick = {
                                if (input.isNotBlank()) {
                                    val msg = input
                                    input = ""
                                    keyboard?.hide()
                                    scope.launch {
                                        agent.processMessage(sessionId, msg)
                                        messages = sessions.getMessages(sessionId)
                                    }
                                }
                            },
                            enabled = input.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(role: String, content: String, isStreaming: Boolean = false) {
    val isUser = role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Text(
            if (isUser) "You" else "OpenCode",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Card(
            modifier = Modifier.widthIn(max = 340.dp).clip(
                RoundedCornerShape(
                    16.dp, 16.dp,
                    if (isUser) 16.dp else 4.dp,
                    if (isUser) 4.dp else 16.dp
                )
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    content,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isStreaming) {
                    Spacer(Modifier.height(4.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}
