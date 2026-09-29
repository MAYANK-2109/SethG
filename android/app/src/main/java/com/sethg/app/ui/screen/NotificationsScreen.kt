package com.sethg.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.notifications_title), fontWeight = FontWeight.Bold)
                        if (state.unreadCount > 0) {
                            Text("${state.unreadCount} unread", fontSize = 12.sp, color = GreenPrimary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GreenPrimary)
            }
        } else if (state.notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(LightSurfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = Color.Gray.copy(alpha = 0.6f)
                        )
                    }
                    Text(stringResource(R.string.no_notifications_yet), color = TextSecondary, fontSize = 16.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.notifications, key = { it.id }) { notification ->
                    NotificationCard(
                        notification = notification,
                        onClick = { if (!notification.isRead) viewModel.markRead(notification.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationCard(notification: RemoteNotification, onClick: () -> Unit = {}) {
    val isPool = notification.type.equals("POOL", ignoreCase = true)
    val isPrice = notification.type.contains("PRICE", ignoreCase = true) || notification.type.contains("MARKET", ignoreCase = true)
    val icon = when {
        isPool -> Icons.Filled.Groups
        isPrice -> Icons.AutoMirrored.Filled.TrendingUp
        notification.type.equals("BID", ignoreCase = true) -> Icons.Filled.LocalOffer
        notification.type.equals("HANDOVER", ignoreCase = true) -> Icons.Filled.CheckCircle
        else -> Icons.Filled.Notifications
    }

    val iconBg = when {
        isPool -> if (notification.isRead) Color.LightGray else GreenPrimary
        isPrice -> if (notification.isRead) Color.LightGray else Color(0xFFF59E0B)
        else -> if (notification.isRead) Color.LightGray else Color(0xFF3B82F6)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) Color.White else if (isPrice) Color(0xFFFFFBEB) else Color(0xFFE8F5E9)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (notification.isRead) 1.dp else 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(iconBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = notification.title,
                            fontWeight = if (notification.isRead) FontWeight.Medium else FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        if (isPool) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = GreenContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "POOL",
                                    color = GreenPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        } else if (isPrice) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "PRICE ALERT",
                                    color = Color(0xFFD97706),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = formatNotificationTime(notification.createdAt),
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = notification.message,
                    color = if (notification.isRead) TextSecondary else TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
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

        if (now.get(Calendar.DATE) == notifCal.get(Calendar.DATE) &&
            now.get(Calendar.YEAR) == notifCal.get(Calendar.YEAR)) {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
        } else {
            SimpleDateFormat("d MMM, hh:mm a", Locale.getDefault()).format(date)
        }
    } catch (_: Exception) {
        ""
    }
}
