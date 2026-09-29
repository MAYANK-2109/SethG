package com.sethg.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sethg.app.R
import com.sethg.app.ui.theme.*
import com.sethg.app.ui.viewmodel.PoolChatViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoolChatScreen(
    poolId: String,
    simpleId: String,
    category: String,
    status: String,
    isAdmin: Boolean,
    onBack: () -> Unit,
    viewModel: PoolChatViewModel = hiltViewModel()
) {
    var messageText by remember { mutableStateOf("") }
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    DisposableEffect(poolId) {
        viewModel.initPool(poolId, simpleId, category, status, isAdmin)
        onDispose { viewModel.stopChat() }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        containerColor = Color(0xFFECE5DD),   // WhatsApp chat background
        topBar = {
            Surface(color = GreenPrimary, shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    // Avatar Circle
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Groups, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Pool #${state.simpleId.ifBlank { simpleId }}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = if (state.isAdmin) Color(0xFFFFD700) else Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (state.isAdmin) "👑 ADMIN" else "MEMBER",
                                    color = if (state.isAdmin) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (state.status == "POSTED") SapphireAccent else Color(0xFF4CAF50))
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${state.category.ifBlank { category }} · ${state.status}",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Chat Input Bar
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = {
                            Text("Type pool message…", color = TextMuted, fontSize = 14.sp)
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF0F2F5),
                            unfocusedContainerColor = Color(0xFFF0F2F5),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp),
                        maxLines = 4
                    )

                    FloatingActionButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendMessage(messageText)
                                messageText = ""
                            }
                        },
                        containerColor = GreenPrimary,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(46.dp),
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
                    ) {
                        if (state.isSending) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Top Section: Post Pool Option (Admin Only) ─────────────────────────
            PoolPostHeaderSection(
                isAdmin = state.isAdmin,
                status = state.status,
                adminName = state.adminName,
                isPosting = state.isPosting,
                postSuccess = state.postSuccess,
                errorMessage = state.errorMessage,
                onPostPool = { viewModel.postPool() },
                onDismissError = { viewModel.clearMessages() }
            )

            // ── Messages List ──────────────────────────────────────────────────────
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GreenPrimary)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        val isMine = msg.senderId == viewModel.currentUserId
                        PoolChatBubble(msg = msg, isMine = isMine)
                    }
                }
            }
        }
    }
}

@Composable
private fun PoolPostHeaderSection(
    isAdmin: Boolean,
    status: String,
    adminName: String,
    isPosting: Boolean,
    postSuccess: Boolean,
    errorMessage: String?,
    onPostPool: () -> Unit,
    onDismissError: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        errorMessage?.let { err ->
            Surface(
                color = Color(0xFFFFEBEE),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(err, color = Color.Red, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismissError, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (isAdmin) {
            // ADMIN VIEW: Has the right to post the pool
            if (status == "OPEN") {
                Surface(
                    color = GreenContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Campaign, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Post Pool to Marketplace", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GreenPrimary)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "You are the pool admin. Post this pool for recyclers to place bulk bids.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        Button(
                            onClick = onPostPool,
                            enabled = !isPosting,
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            if (isPosting) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            } else {
                                Text("Post Pool", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Pool Posted to Market ✓ (Recyclers are bidding on these lots)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = GreenPrimary
                        )
                    }
                }
            }
        } else {
            // MEMBER VIEW: Right to post is with admin only
            Surface(
                color = LightSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (status == "POSTED") Icons.Filled.CheckCircle else Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (status == "POSTED") GreenPrimary else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (status == "POSTED") "Pool Posted by Admin" else "Pool Admin: ${adminName.ifBlank { "Creator" }}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = if (status == "POSTED") "This pool is now live for recycler bidding." else "Right to post this pool is with the pool admin only.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PoolChatBubble(
    msg: com.sethg.app.data.remote.model.PoolMessageItem,
    isMine: Boolean
) {
    val bubbleColor = if (isMine) Color(0xFFE7FFDB) else Color.White
    val timeFormatted = remember(msg.createdAt) {
        formatMessageTime(msg.createdAt)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isMine) 14.dp else 2.dp,
                bottomEnd = if (isMine) 2.dp else 14.dp
            ),
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                if (!isMine) {
                    Text(
                        text = msg.senderName.ifBlank { "Member" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = OchreSecondary
                    )
                    Spacer(Modifier.height(2.dp))
                }

                Text(
                    text = msg.content,
                    fontSize = 14.sp,
                    color = TextPrimary
                )

                Spacer(Modifier.height(2.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeFormatted,
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                    if (isMine) {
                        Spacer(Modifier.width(3.dp))
                        Icon(Icons.Filled.DoneAll, contentDescription = null, tint = SapphireAccent, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
    }
}

private fun formatMessageTime(isoString: String): String {
    if (isoString.isBlank()) return ""
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val date = sdf.parse(isoString) ?: return ""
        val out = SimpleDateFormat("hh:mm a", Locale.getDefault())
        out.format(date)
    } catch (_: Exception) {
        ""
    }
}
