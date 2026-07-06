package com.opencode.android.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.android.core.agent.AgentEngine
import com.opencode.android.core.agent.AgentRegistry
import com.opencode.android.core.config.ConfigManager
import com.opencode.android.core.session.SessionManager

private val GradientStart = Color(0xFF667eea)
private val GradientEnd = Color(0xFF764ba2)
private val UserBubbleColor = Color(0xFF667eea)
private val AssistantBubbleColor = Color(0xFF1E1E2E)
private val ToolBubbleColor = Color(0xFF2D2D3F)
private val ErrorBubbleColor = Color(0xFF3D1F1F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String,
    sessions: SessionManager,
    agent: AgentEngine,
    config: ConfigManager,
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    onOpenTerminal: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val isProcessing by agent.isProcessing.collectAsState()
    val output by agent.currentOutput.collectAsState()
    val allMessages by sessions.messages.collectAsState()
    val messages = remember(allMessages, sessionId) { allMessages.filter { it.sessionId == sessionId } }
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val currentAgent by agent.currentAgent.collectAsState()
    val agentDef = AgentRegistry.getAgent(currentAgent)
    val activeTools by agent.activeTools.collectAsState()
    val errorMessage by agent.errorMessage.collectAsState()

    LaunchedEffect(messages.size, output) {
        if (messages.isNotEmpty() || output.isNotEmpty()) {
            val target = (messages.size - 1).coerceAtLeast(0) + if (output.isNotEmpty()) 1 else 0
            listState.animateScrollToItem(target)
        }
    }

    Scaffold(
        containerColor = Color(0xFF0D1117),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0D1117))
        ) {
            // Premium Top Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF161B22),
                shadowElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFF8B949E))
                    }
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(GradientStart, GradientEnd))
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("OC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val session = sessions.getSession(sessionId)
                        Text(
                            session?.title ?: "New Chat",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = Color(0xFFE6EDF3),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(agentDef.color)))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                agentDef.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8B949E),
                                fontSize = 11.sp,
                            )
                            if (isProcessing) {
                                Spacer(Modifier.width(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = GradientStart,
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenTerminal) {
                        Icon(Icons.Default.Code, "Terminal", tint = Color(0xFF8B949E))
                    }
                }
            }

            // Messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (messages.isEmpty() && output.isEmpty()) {
                    item { PremiumEmptyChatView() }
                }

                items(messages, key = { it.id }) { msg ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    ) {
                        PremiumChatBubble(msg.role, msg.content, msg.toolCalls)
                    }
                }

                if (output.isNotEmpty()) {
                    item {
                        PremiumChatBubble("assistant", output, null, isStreaming = true)
                    }
                }
            }

            // Error banner
            if (errorMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = ErrorBubbleColor,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFF85149), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            errorMessage ?: "",
                            color = Color(0xFFF85149),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { agent.clearError() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "Dismiss", tint = Color(0xFFF85149), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Active tools
            if (activeTools.isNotEmpty()) {
                Surface(color = Color(0xFF161B22)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = GradientStart)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Using: ${activeTools.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = GradientStart,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // Input area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF161B22),
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text("Ask ${agentDef.name}...", color = Color(0xFF484F58), fontSize = 14.sp)
                        },
                        enabled = !isProcessing,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (input.isNotBlank() && !isProcessing) {
                                val msg = input
                                input = ""
                                keyboard?.hide()
                                agent.processMessage(sessionId, msg)
                            }
                        }),
                        maxLines = 6,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color(0xFF30363D),
                            focusedBorderColor = GradientStart,
                            unfocusedContainerColor = Color(0xFF0D1117),
                            focusedContainerColor = Color(0xFF0D1117),
                            cursorColor = GradientStart,
                            focusedTextColor = Color(0xFFE6EDF3),
                            unfocusedTextColor = Color(0xFFE6EDF3),
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    )
                    Spacer(Modifier.width(8.dp))
                    if (isProcessing) {
                        FilledIconButton(
                            onClick = { agent.cancel() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFFF85149),
                            ),
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Default.Stop, "Stop", tint = Color.White)
                        }
                    } else {
                        FilledIconButton(
                            onClick = {
                                if (input.isNotBlank()) {
                                    val msg = input
                                    input = ""
                                    keyboard?.hide()
                                    agent.processMessage(sessionId, msg)
                                }
                            },
                            enabled = input.isNotBlank(),
                            modifier = Modifier
                                .size(48.dp)
                                .shadow(
                                    elevation = if (input.isNotBlank()) 4.dp else 0.dp,
                                    shape = CircleShape,
                                    ambientColor = GradientStart,
                                    spotColor = GradientStart,
                                ),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (input.isNotBlank()) GradientStart else Color(0xFF21262D),
                                disabledContainerColor = Color(0xFF21262D),
                            ),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                "Send",
                                tint = if (input.isNotBlank()) Color.White else Color(0xFF484F58),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumEmptyChatView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(GradientStart, GradientEnd))
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color.White,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "How can I help?",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE6EDF3),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "I can read, write, and edit files,\nsearch the web, and execute commands",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8B949E),
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(32.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SuggestionChip("Write code", Modifier)
            SuggestionChip("Debug error", Modifier)
            SuggestionChip("Search files", Modifier)
        }
    }
}

@Composable
fun SuggestionChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF21262D),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30363D)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = Color(0xFF8B949E),
            fontSize = 12.sp,
        )
    }
}

@Composable
fun PremiumChatBubble(
    role: String,
    content: String,
    toolCalls: List<com.opencode.android.core.session.ToolCallData>?,
    isStreaming: Boolean = false,
) {
    val isUser = role == "user"
    val isTool = role == "tool"
    val isError = content.startsWith("Error:")

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        // Avatar + label for assistant
        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isError -> Color(0xFFF85149).copy(alpha = 0.2f)
                                isTool -> Color(0xFFF9E2AF).copy(alpha = 0.2f)
                                else -> Color.Transparent
                            }
                        )
                        .then(
                            if (!isError && !isTool) Modifier.background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        when {
                            isError -> Icons.Default.Warning
                            isTool -> Icons.Default.Build
                            else -> Icons.Default.SmartToy
                        },
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = when {
                            isError -> Color(0xFFF85149)
                            isTool -> Color(0xFFF9E2AF)
                            else -> Color.White
                        },
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        isError -> "Error"
                        isTool -> "Tool Result"
                        else -> "Assistant"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8B949E),
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                )
            }
        }

        // Message bubble
        Surface(
            modifier = Modifier.widthIn(max = 340.dp),
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp,
            ),
            color = when {
                isUser -> UserBubbleColor
                isError -> ErrorBubbleColor
                isTool -> ToolBubbleColor
                else -> AssistantBubbleColor
            },
            shadowElevation = if (isUser) 2.dp else 0.dp,
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Check if content contains code blocks
                val parts = content.split(Regex("```(\\w*)\\n?"))
                if (parts.size > 1) {
                    // Has code blocks
                    parts.forEachIndexed { index, part ->
                        if (part.isNotBlank()) {
                            if (index % 2 == 0) {
                                // Regular text
                                Text(
                                    part.trim(),
                                    color = when {
                                        isUser -> Color.White
                                        isError -> Color(0xFFF85149)
                                        isTool -> Color(0xFFF9E2AF)
                                        else -> Color(0xFFE6EDF3)
                                    },
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                )
                            } else {
                                // Code block
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0D1117),
                                ) {
                                    Text(
                                        part.trim(),
                                        modifier = Modifier.padding(10.dp),
                                        color = Color(0xFFA5D6FF),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        content,
                        color = when {
                            isUser -> Color.White
                            isError -> Color(0xFFF85149)
                            isTool -> Color(0xFFF9E2AF)
                            else -> Color(0xFFE6EDF3)
                        },
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }

                if (isStreaming) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StreamingDots()
                        Spacer(Modifier.width(6.dp))
                        Text("Thinking...", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8B949E), fontSize = 11.sp)
                    }
                }
            }
        }

        // Tool calls
        if (!toolCalls.isNullOrEmpty()) {
            Spacer(Modifier.height(4.dp))
            toolCalls.forEach { tc ->
                Surface(
                    modifier = Modifier.padding(vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = ToolBubbleColor,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Build, null, modifier = Modifier.size(12.dp), tint = Color(0xFFF9E2AF))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            tc.name,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFF9E2AF),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StreamingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "streaming")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(GradientStart.copy(alpha = alpha))
            )
        }
    }
}
