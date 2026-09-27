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
import com.sethg.app.ui.viewmodel.ChatViewModel
import com.sethg.app.ui.viewmodel.ProfileViewModel
import java.text.SimpleDateFormat
import java.util.*

data class ChatMessage(
    val id: String,
    val text: String,
    val isMine: Boolean,
    val timestamp: Long,
    val senderName: String = ""
)

// ── Chat Screen (WhatsApp-inspired) ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vendorName: String,
    lotId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
    profileVm: ProfileViewModel = hiltViewModel()
) {
    var messageText by remember { mutableStateOf("") }
    val state by viewModel.uiState.collectAsState()
    val profileState by profileVm.uiState.collectAsState()
    val listState = rememberLazyListState()

    val isVendor = profileState.user?.role == "vendor" || profileState.user?.role == "user"
    val canInitiate = !isVendor || state.messages.isNotEmpty()

    DisposableEffect(lotId) {
        viewModel.startChat(lotId)
        onDispose { viewModel.stopChat() }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    val avatarInitial = vendorName.firstOrNull()?.uppercase() ?: "V"

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

                    // Avatar circle
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(avatarInitial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(vendorName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Lot · $lotId",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Encryption badge
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Encrypted", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column {
                // Vendor cannot initiate — show info banner
                if (isVendor && state.messages.isEmpty()) {
                    Surface(color = Color(0xFFFFF9C4)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = OchreSecondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Vendors cannot start chats. Wait for the recycler to message first.",
                                color = OchreOnContainer,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Surface(color = Color(0xFFF0F2F5), shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Input field
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = messageText,
                                onValueChange = { messageText = it },
                                placeholder = {
                                    Text(
                                        if (!canInitiate) "Wait for recycler to start the chat…"
                                        else stringResource(R.string.type_message_hint),
                                        color = TextMuted
                                    )
                                },
                                enabled = canInitiate,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedBorderColor = Color.Transparent,
                                    disabledBorderColor = Color.Transparent,
                                    disabledContainerColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 4
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        // Send button
                        val sendEnabled = messageText.isNotBlank() && canInitiate
                        FloatingActionButton(
                            onClick = {
                                if (sendEnabled) {
                                    viewModel.sendMessage(messageText.trim())
                                    messageText = ""
                                }
                            },
                            containerColor = if (sendEnabled) GreenPrimary else Color(0xFFB0BEC5),
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
                        ) {
                            Icon(
                                Icons.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(state.messages, key = { it.id }) { msg ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically { it / 2 }
                ) {
                    WhatsAppChatBubble(message = msg)
                }
            }
        }
    }
}

@Composable
fun WhatsAppChatBubble(message: ChatMessage) {
    val formatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeString = formatter.format(Date(message.timestamp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start
    ) {
        val bubbleColor = if (message.isMine) Color(0xFFDCF8C6) else Color.White
        val textColor = TextPrimary

        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (message.isMine) 18.dp else 4.dp,
                bottomEnd = if (message.isMine) 4.dp else 18.dp
            ),
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(min = 80.dp, max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Sender name for incoming messages
                if (!message.isMine && message.senderName.isNotBlank()) {
                    Text(
                        text = message.senderName,
                        color = GreenPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Message content — bcrypt hash shown as encrypted indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = message.text,
                        color = textColor,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                }

                Spacer(Modifier.height(3.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = timeString,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    if (message.isMine) {
                        Spacer(Modifier.width(4.dp))
                        // Double tick (delivered indicator)
                        Icon(
                            Icons.Filled.DoneAll,
                            contentDescription = null,
                            tint = SapphireAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
