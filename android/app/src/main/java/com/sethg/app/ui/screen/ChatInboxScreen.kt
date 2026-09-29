package com.sethg.app.ui.screen

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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import com.sethg.app.data.remote.model.PoolItem
import com.sethg.app.domain.model.MaterialCategory
import com.sethg.app.ui.theme.*
import com.sethg.app.ui.viewmodel.ChatInboxViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInboxScreen(
    onOpenChat: (lotId: String, vendorName: String) -> Unit,
    onOpenPoolChat: (poolId: String, simpleId: String, category: String, status: String, isAdmin: Boolean) -> Unit = { _, _, _, _, _ -> },
    onBack: () -> Unit = {},
    viewModel: ChatInboxViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showCreatePoolDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color(0xFFF0F2F5),
        topBar = {
            Column {
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
                                text = if (state.selectedTab == 0) "${state.conversations.size} direct chats" else "${state.pools.size} existing pools",
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
                        IconButton(onClick = { viewModel.refreshAll() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White)
                        }
                    }
                )

                // ── Direct Chat vs Pool Chat Tabs ─────────────────────────────
                TabRow(
                    selectedTabIndex = state.selectedTab,
                    containerColor = GreenPrimary,
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[state.selectedTab]),
                            color = Color.White,
                            height = 3.dp
                        )
                    }
                ) {
                    Tab(
                        selected = state.selectedTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Direct Chats (${state.conversations.size})",
                                    fontWeight = if (state.selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    )
                    Tab(
                        selected = state.selectedTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Pool Chats (${state.pools.size})",
                                    fontWeight = if (state.selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            if (state.selectedTab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { showCreatePoolDialog = true },
                    containerColor = GreenPrimary,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New Pool", fontWeight = FontWeight.Bold) }
                )
            }
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

        if (state.selectedTab == 0) {
            // ── Tab 0: Direct Chats ──────────────────────────────────────────
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
            } else {
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
        } else {
            // ── Tab 1: Pool Chats (User's Existing Pools) ───────────────────
            if (state.pools.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(24.dp)) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(GreenContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Groups, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(40.dp))
                        }
                        Text("No Pool Chats Yet", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                        Text(
                            text = "Collaborate with other local kabadiwalas by creating or joining a pool to bulk sell materials to recyclers at higher rates.",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = { showCreatePoolDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Create a New Pool", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(state.pools, key = { it.id }) { pool ->
                        PoolInboxRow(
                            pool = pool,
                            onClick = {
                                onOpenPoolChat(pool.id, pool.simpleId, pool.category, pool.status, pool.isAdmin)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreatePoolDialog) {
        CreatePoolDialog(
            isCreating = state.isCreatingPool,
            onDismiss = { showCreatePoolDialog = false },
            onConfirm = { category ->
                viewModel.createPool(category) { createdPool ->
                    showCreatePoolDialog = false
                    onOpenPoolChat(createdPool.id, createdPool.simpleId, createdPool.category, createdPool.status, true)
                }
            }
        )
    }
}

@Composable
private fun PoolInboxRow(pool: PoolItem, onClick: () -> Unit) {
    val timeStr = remember(pool.lastMessageAt, pool.createdAt) {
        parseTime(pool.lastMessageAt ?: pool.createdAt)
    }

    Surface(
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Group Avatar
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(if (pool.isAdmin) GreenContainer else LightSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = null,
                    tint = if (pool.isAdmin) GreenPrimary else TextSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Simple Pool ID
                    Text(
                        text = "Pool #${pool.simpleId}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Formatted Timestamp
                    Text(
                        text = timeStr,
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Role Badge (Admin / Member)
                    Surface(
                        color = if (pool.isAdmin) Color(0xFFFFF9C4) else LightSurfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (pool.isAdmin) "👑 ADMIN" else "MEMBER",
                            color = if (pool.isAdmin) Color(0xFFF57F17) else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Status Badge (OPEN / POSTED)
                    Surface(
                        color = if (pool.status == "POSTED") SapphireContainer else GreenContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = pool.status,
                            color = if (pool.status == "POSTED") SapphireAccent else GreenPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Category
                    Surface(
                        color = LightSurfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = pool.category,
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "${"%.1f".format(pool.totalWeight)} kg · ${pool.lotCount} lots",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Last message snippet
                Text(
                    text = pool.lastMessage?.takeIf { it.isNotBlank() } ?: "Tap to coordinate in pool chat…",
                    fontSize = 13.sp,
                    color = if (pool.lastMessage.isNullOrBlank()) TextMuted else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    HorizontalDivider(color = Color(0xFFE8EAF0), thickness = 0.5.dp)
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (conversation.myRole == "vendor") SapphireContainer else GreenContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (conversation.myRole == "vendor") Icons.Filled.Store else Icons.Filled.Recycling,
                                contentDescription = null,
                                tint = if (conversation.myRole == "vendor") SapphireAccent else GreenPrimary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = if (conversation.myRole == "vendor") "VENDOR" else "RECYCLER",
                                color = if (conversation.myRole == "vendor") SapphireAccent else GreenPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    Surface(
                        color = LightSurfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = conversation.category,
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    Text(
                        text = "Lot · ${conversation.lotId}",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = conversation.lastMessage?.takeIf { it.isNotBlank() } ?: "No messages yet",
                    fontSize = 13.sp,
                    color = if (conversation.lastMessage.isNullOrBlank()) TextMuted else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    HorizontalDivider(color = Color(0xFFE8EAF0), thickness = 0.5.dp)
}

@Composable
private fun CreatePoolDialog(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (category: String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(MaterialCategory.CABLE.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create Material Pool", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Select the scrap material category for this pool. You will be the Pool Admin.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                MaterialCategory.entries.chunked(2).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { cat ->
                            val isSelected = selectedCategory == cat.name
                            Surface(
                                color = if (isSelected) GreenContainer else LightSurfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, GreenPrimary) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedCategory = cat.name }
                            ) {
                                Text(
                                    text = cat.name,
                                    color = if (isSelected) GreenPrimary else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedCategory) },
                enabled = !isCreating,
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                if (isCreating) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Text("Create Pool", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

private fun parseTime(isoString: String): String {
    if (isoString.isBlank()) return ""
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val date = sdf.parse(isoString) ?: return ""
        val now = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { time = date }

        if (now.get(Calendar.DATE) == msgCal.get(Calendar.DATE) &&
            now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)) {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
        } else {
            SimpleDateFormat("d MMM, hh:mm a", Locale.getDefault()).format(date)
        }
    } catch (_: Exception) {
        ""
    }
}
