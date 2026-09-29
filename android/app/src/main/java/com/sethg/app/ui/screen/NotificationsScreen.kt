package com.sethg.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import com.sethg.app.data.remote.model.RemoteNotification
import com.sethg.app.ui.theme.*
import com.sethg.app.ui.viewmodel.NotificationsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredNotifications = remember(state.notifications, selectedFilter) {
        when (selectedFilter) {
            "UNREAD" -> state.notifications.filter { !it.isRead }
            "PRICE" -> state.notifications.filter { it.type.contains("PRICE", ignoreCase = true) || it.type.contains("MARKET", ignoreCase = true) }
            "POOL" -> state.notifications.filter { it.type.equals("POOL", ignoreCase = true) }
            else -> state.notifications
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.notifications_title), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        if (state.unreadCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = GreenContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    "${state.unreadCount} new",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.unreadCount > 0) {
                        IconButton(onClick = { viewModel.markAllRead() }) {
                            Icon(Icons.Filled.DoneAll, contentDescription = "Mark all as read", tint = GreenPrimary)
                        }
                    }
                    IconButton(onClick = { viewModel.loadNotifications() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LightBackground,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = LightBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter Pills Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NotificationFilterChip(
                    label = "All (${state.notifications.size})",
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" }
                )
                if (state.unreadCount > 0) {
                    NotificationFilterChip(
                        label = "Unread (${state.unreadCount})",
                        selected = selectedFilter == "UNREAD",
                        onClick = { selectedFilter = "UNREAD" }
                    )
                }
                NotificationFilterChip(
                    label = "Price Alerts",
                    selected = selectedFilter == "PRICE",
                    onClick = { selectedFilter = "PRICE" }
                )
                NotificationFilterChip(
                    label = "Pools",
                    selected = selectedFilter == "POOL",
                    onClick = { selectedFilter = "POOL" }
                )
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GreenPrimary)
                }
            } else if (filteredNotifications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .background(LightSurfaceVariant, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsNone,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = Color.Gray.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            if (selectedFilter != "ALL") "No notifications in this filter" else stringResource(R.string.no_notifications_yet),
                            color = TextSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredNotifications, key = { it.id }) { notification ->
                        NotificationCard(
                            notification = notification,
                            onClick = { if (!notification.isRead) viewModel.markRead(notification.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) GreenPrimary else Color.White,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (selected) GreenPrimary else LightBorder),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun NotificationCard(notification: RemoteNotification, onClick: () -> Unit = {}) {
    val isPool = notification.type.equals("POOL", ignoreCase = true)
    val isPrice = notification.type.contains("PRICE", ignoreCase = true) || notification.type.contains("MARKET", ignoreCase = true)
    val isBid = notification.type.equals("BID", ignoreCase = true)
    val isHandover = notification.type.equals("HANDOVER", ignoreCase = true)

    val accentColor = when {
        isPrice -> Color(0xFFF59E0B)
        isPool -> GreenPrimary
        isBid -> Color(0xFF2563EB)
        isHandover -> Color(0xFF0D9488)
        else -> Color(0xFF64748B)
    }

    val iconBgColor = when {
        isPrice -> Color(0xFFFEF3C7)
        isPool -> GreenContainer
        isBid -> Color(0xFFDBEAFE)
        isHandover -> Color(0xFFCCFBF1)
        else -> Color(0xFFF1F5F9)
    }

    val icon = when {
        isPrice -> Icons.AutoMirrored.Filled.TrendingUp
        isPool -> Icons.Filled.Groups
        isBid -> Icons.Filled.LocalOffer
        isHandover -> Icons.Filled.CheckCircle
        else -> Icons.Filled.Notifications
    }

    val badgeLabel = when {
        isPrice -> "PRICE ALERT"
        isPool -> "POOL"
        isBid -> "BID OFFER"
        isHandover -> "HANDOVER"
        else -> notification.type.uppercase()
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) Color.White else if (isPrice) Color(0xFFFFFDF5) else Color(0xFFF7FDF9)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (notification.isRead) 1.dp else 3.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (notification.isRead) 1.dp else 1.5.dp,
            color = if (notification.isRead) LightBorder else accentColor.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left Accent Stripe
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(if (notification.isRead) Color(0xFFE2E8F0) else accentColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top Meta Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Category Chip
                        Surface(
                            color = iconBgColor,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = badgeLabel,
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }

                        // Unread Dot Indicator
                        if (!notification.isRead) {
                            Surface(
                                color = accentColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(accentColor, CircleShape)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "NEW",
                                        color = accentColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }

                    // Timestamp
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AccessTime,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = formatNotificationTime(notification.createdAt),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Title + Icon Row
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Icon Box
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(iconBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = notification.title,
                            fontWeight = if (notification.isRead) FontWeight.SemiBold else FontWeight.ExtraBold,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = notification.message,
                            color = if (notification.isRead) TextSecondary else TextPrimary.copy(alpha = 0.85f),
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatNotificationTime(isoString: String): String {
    if (isoString.isBlank()) return ""
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val date = sdf.parse(isoString) ?: return ""
        val now = Calendar.getInstance()
        val notifCal = Calendar.getInstance().apply { time = date }

        val diffMillis = now.timeInMillis - date.time
        val diffMinutes = diffMillis / (60 * 1000)
        val diffHours = diffMillis / (60 * 60 * 1000)

        when {
            diffMinutes < 1 -> "Just now"
            diffMinutes < 60 -> "${diffMinutes}m ago"
            diffHours < 24 && now.get(Calendar.DATE) == notifCal.get(Calendar.DATE) -> "${diffHours}h ago"
            now.get(Calendar.DATE) - notifCal.get(Calendar.DATE) == 1 &&
            now.get(Calendar.YEAR) == notifCal.get(Calendar.YEAR) -> "Yesterday, " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
            now.get(Calendar.YEAR) == notifCal.get(Calendar.YEAR) -> SimpleDateFormat("d MMM, hh:mm a", Locale.getDefault()).format(date)
            else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
        }
    } catch (_: Exception) {
        isoString.take(10)
    }
}
