package com.sethg.app.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.sethg.app.domain.model.EarningsSummary
import com.sethg.app.ui.navigation.Screen
import com.sethg.app.ui.theme.*
import com.sethg.app.ui.viewmodel.AuthViewModel
import com.sethg.app.ui.viewmodel.DashboardViewModel
import com.sethg.app.ui.viewmodel.ProfileViewModel

// ── Bottom-nav wrapper ────────────────────────────────────────────────────────

@Composable
fun MainScreen(navController: NavController, onLogout: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val authVm: AuthViewModel = hiltViewModel()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 },
                    icon     = {
                        Icon(
                            if (selectedTab == 0) Icons.Filled.Dashboard else Icons.Outlined.Dashboard,
                            contentDescription = "Dashboard"
                        )
                    },
                    label    = { Text("होम") },
                    colors   = NavigationBarItemDefaults.colors(
                        selectedIconColor   = GreenPrimary,
                        selectedTextColor   = GreenPrimary,
                        indicatorColor      = GreenPrimary.copy(alpha = 0.15f),
                        unselectedIconColor = SubText,
                        unselectedTextColor = SubText
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 },
                    icon     = {
                        Icon(
                            if (selectedTab == 1) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2,
                            contentDescription = "Lots"
                        )
                    },
                    label    = { Text("माल") },
                    colors   = NavigationBarItemDefaults.colors(
                        selectedIconColor   = GreenPrimary,
                        selectedTextColor   = GreenPrimary,
                        indicatorColor      = GreenPrimary.copy(alpha = 0.15f),
                        unselectedIconColor = SubText,
                        unselectedTextColor = SubText
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick  = { selectedTab = 2 },
                    icon     = {
                        Icon(
                            if (selectedTab == 2) Icons.Filled.Person else Icons.Outlined.Person,
                            contentDescription = "Profile"
                        )
                    },
                    label    = { Text("प्रोफ़ाइल") },
                    colors   = NavigationBarItemDefaults.colors(
                        selectedIconColor   = GreenPrimary,
                        selectedTextColor   = GreenPrimary,
                        indicatorColor      = GreenPrimary.copy(alpha = 0.15f),
                        unselectedIconColor = SubText,
                        unselectedTextColor = SubText
                    )
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> DashboardScreen()
                1 -> LotsScreen(onNewLot = { navController.navigate(Screen.NewLot.route) })
                2 -> ProfileScreen(
                    onLogout  = {
                        authVm.logout()
                        onLogout()
                    }
                )
            }
        }
    }
}

// ── Dashboard Screen ──────────────────────────────────────────────────────────

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(Color(0xFF081A08), BackgroundDark))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(GreenPrimary.copy(alpha = 0.8f), Color(0xFF1B5E20))
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("♻️", fontSize = 28.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Seth G",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        "आपकी कमाई  ·  Your Earnings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                // Offline badge
                if (state.isOffline) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd),
                        shape = RoundedCornerShape(8.dp),
                        color = AmberSecondary.copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.WifiOff,
                                contentDescription = "Offline",
                                tint = AmberSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Offline",
                                color = AmberSecondary,
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (state.isLoading && state.today == null) {
                // Initial loading skeleton
                repeat(3) {
                    LoadingEarningsCard()
                    Spacer(Modifier.height(16.dp))
                }
            } else {
                // Earnings cards
                EarningsCard(
                    icon    = "☀️",
                    title   = "आज की कमाई",
                    subtitle = "Today's Earnings",
                    summary = state.today,
                    gradient = listOf(Color(0xFF1B5E20), Color(0xFF2E7D32))
                )
                Spacer(Modifier.height(16.dp))

                EarningsCard(
                    icon    = "📅",
                    title   = "इस हफ्ते",
                    subtitle = "This Week",
                    summary = state.weekly,
                    gradient = listOf(Color(0xFF0D47A1), Color(0xFF1565C0))
                )
                Spacer(Modifier.height(16.dp))

                EarningsCard(
                    icon    = "🏆",
                    title   = "इस महीने",
                    subtitle = "This Month",
                    summary = state.monthly,
                    gradient = listOf(Color(0xFF4A148C), Color(0xFF6A1B9A))
                )
            }

            // Error state
            state.error?.let { errMsg ->
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorColor.copy(alpha = 0.1f)),
                    shape  = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = ErrorColor)
                        Spacer(Modifier.width(8.dp))
                        Text(errMsg, color = ErrorColor, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Pull-to-refresh hint
            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick  = { viewModel.refresh() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = GreenPrimary)
                Spacer(Modifier.width(8.dp))
                Text("ताज़ा करें  ·  Refresh", color = GreenPrimary)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EarningsCard(
    icon: String,
    title: String,
    subtitle: String,
    summary: EarningsSummary?,
    gradient: List<Color>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape  = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(gradient))
                .padding(24.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(icon, fontSize = 28.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                if (summary != null) {
                    Text(
                        "₹ ${"%,.2f".format(summary.total)}",
                        style      = MaterialTheme.typography.displayMedium,
                        color      = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Receipt,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${summary.transactionCount} लेन-देन  ·  transactions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    Text(
                        "—",
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingEarningsCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue  = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .padding(horizontal = 20.dp),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = SurfaceVariant.copy(alpha = alpha))
    ) {}
}

// ── Profile Screen ────────────────────────────────────────────────────────────

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProfileViewModel.UiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                else -> Unit
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(colors = listOf(Color(0xFF081A08), BackgroundDark))
                )
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(GreenPrimary.copy(alpha = 0.8f), Color(0xFF1B5E20))
                            )
                        )
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👤", fontSize = 36.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        if (state.isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text(
                                state.user?.name ?: "—",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                state.user?.phone ?: state.user?.email ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Info cards
                state.user?.let { user ->
                    ProfileInfoRow(icon = "📱", label = "फोन / Phone", value = user.phone ?: "—")
                    ProfileInfoRow(icon = "📧", label = "ईमेल / Email", value = user.email ?: "—")
                    ProfileInfoRow(icon = "🌐", label = "भाषा / Language",
                        value = languageOptions.find { it.code == user.language }?.nativeName ?: user.language)
                }

                Spacer(Modifier.height(24.dp))

                // Edit Profile button
                OutlinedButton(
                    onClick  = { showEditDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    border   = BorderStroke(1.5.dp, GreenPrimary)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = GreenPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("प्रोफ़ाइल बदलें  ·  Edit Profile", color = GreenPrimary)
                }

                Spacer(Modifier.height(16.dp))

                // About card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    shape  = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ℹ️", fontSize = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Seth G के बारे में  ·  About",
                                style = MaterialTheme.typography.titleMedium,
                                color = OnSurfaceDark,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Seth G कबाड़ी वालों के लिए एक डिजिटल कमाई ट्रैकर है। " +
                            "यह आपकी रोज़, हफ्ते और महीने की कमाई दिखाता है। " +
                            "Seth G is a digital earnings tracker for informal waste collectors — " +
                            "track your daily, weekly, and monthly income with ease.",
                            style     = MaterialTheme.typography.bodyMedium,
                            color     = SubText,
                            textAlign = TextAlign.Start
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Logout button
                Button(
                    onClick  = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = ErrorColor.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Filled.Logout, contentDescription = null, tint = ErrorColor)
                    Spacer(Modifier.width(8.dp))
                    Text("लॉगआउट  ·  Logout", color = ErrorColor, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }

    // Edit profile dialog
    if (showEditDialog) {
        EditProfileDialog(
            currentUser = state.user,
            isSaving    = state.isSaving,
            onDismiss   = { showEditDialog = false },
            onSave      = { name, phone, email ->
                viewModel.updateProfile(
                    name  = name.ifBlank { null },
                    phone = phone.ifBlank { null },
                    email = email.ifBlank { null }
                )
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun ProfileInfoRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = SubText)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = OnSurfaceDark)
        }
    }
}

@Composable
private fun EditProfileDialog(
    currentUser: com.sethg.app.domain.model.User?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, email: String) -> Unit
) {
    var name  by remember { mutableStateOf(currentUser?.name  ?: "") }
    var phone by remember { mutableStateOf(currentUser?.phone ?: "") }
    var email by remember { mutableStateOf(currentUser?.email ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceDark,
        title = {
            Text(
                "प्रोफ़ाइल बदलें  ·  Edit Profile",
                style = MaterialTheme.typography.titleLarge,
                color = OnSurfaceDark
            )
        },
        text = {
            Column {
                SethGTextField(value = name,  onValueChange = { name  = it }, label = "👤 नाम / Name")
                Spacer(Modifier.height(12.dp))
                SethGTextField(value = phone, onValueChange = { phone = it }, label = "📱 फोन / Phone", keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                Spacer(Modifier.height(12.dp))
                SethGTextField(value = email, onValueChange = { email = it }, label = "📧 ईमेल / Email", keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
            }
        },
        confirmButton = {
            Button(
                onClick  = { onSave(name, phone, email) },
                enabled  = !isSaving,
                colors   = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("सेव करें  ·  Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("रद्द करें  ·  Cancel", color = SubText)
            }
        }
    )
}
