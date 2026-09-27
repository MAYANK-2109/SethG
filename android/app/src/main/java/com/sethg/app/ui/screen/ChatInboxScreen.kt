package com.sethg.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sethg.app.data.remote.model.ChatConversation
import com.sethg.app.ui.theme.*
import com.sethg.app.ui.viewmodel.ChatInboxViewModel
import java.text.SimpleDateFormat
import java.util.*

// ── Chat Inbox Screen ─────────────────────────────────────────────────────────
// Shows all lot-conversations for the current user (vendor or recycler).
// Vendors see chats recyclers started; recyclers see chats they initiated from feed.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInboxScreen(
    onOpenChat: (lotId: String, vendorName: String) -> Unit,
    onBack: () -> Unit = {},
    viewModel: ChatInboxViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = Color(0xFFF0F2F5),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Chats",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Text(
                            text = "${state.conversations.size} conversations",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GreenPrimary,
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { viewModel.startPolling() }) {
                        Icon(Icons.Filled.Chat, contentDescription = "Refresh", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = GreenPrimary)
                    Text("Loading chats…", color = TextSecondary)
                }
            }
            return@Scaffold
        }

        if (state.conversations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(GreenContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Chat, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(40.dp))
                    }
                    Text("No conversations yet", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                    Text(
                        text = "Recyclers can start a chat from the\nmarketplace feed card",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(state.conversations, key = { it.lotId }) { conversation ->
                ChatInboxRow(
                    conversation = conversation,
                    onClick = {
                        val displayName = conversation.otherName ?: "Unknown"
                        onOpenChat(conversation.lotId, displayName)
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatInboxRow(conversation: ChatConversation, onClick: () -> Unit) {
    val timeStr = remember(conversation.lastMessageAt) {
        conversation.lastMessageAt?.let { parseTime(it) } ?: ""
    }
    val avatarColor = if (conversation.myRole == "vendor") SapphireAccent else GreenPrimary
    val initials = (conversation.otherName ?: "?").take(1).uppercase()

    Surface(
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(avatarColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = avatarColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.otherName ?: "Unknown",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = timeStr,
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Role badge
                    Surface(
                        color = if (conversation.myRole == "vendor") SapphireContainer else GreenContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (conversation.myRole == "vendor") Icons.Filled.Store else Icons.Filled.Recycling,
                                contentDescription = null,
                                tint = if (conversation.myRole == "vendor") SapphireAccent else GreenPrimary,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = conversation.lotId,
                                fontSize = 10.sp,
                                color = if (conversation.myRole == "vendor") SapphireAccent else GreenPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    // Last message preview (bcrypt hash — shown as encrypted)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = if (conversation.messageCount > 0) "Encrypted message" else "No messages yet",
                            fontSize = 13.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Unread badge
                    if (conversation.messageCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(GreenPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = conversation.messageCount.toString().take(2),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // WhatsApp-style divider
    HorizontalDivider(
        modifier = Modifier.padding(start = 82.dp),
        color = LightBorder,
        thickness = 0.5.dp
    )
}

private fun parseTime(iso: String): String {
    return try {
        val inputFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = inputFmt.parse(iso) ?: return ""
        val now = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { time = date }

        if (now.get(Calendar.DATE) == msgCal.get(Calendar.DATE)) {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
        } else if (now.get(Calendar.DATE) - msgCal.get(Calendar.DATE) == 1) {
            "Yesterday"
        } else {
            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(date)
        }
    } catch (e: Exception) {
        ""
    }
}
