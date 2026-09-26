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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.sethg.app.R
import com.sethg.app.domain.model.EarningsSummary
import com.sethg.app.ui.navigation.Screen
import com.sethg.app.ui.theme.*
import com.sethg.app.ui.viewmodel.AuthViewModel
import com.sethg.app.ui.viewmodel.DashboardViewModel
import com.sethg.app.ui.viewmodel.LanguageViewModel
import com.sethg.app.ui.viewmodel.ProfileViewModel

// ── Bottom-nav wrapper ────────────────────────────────────────────────────────

@Composable
fun MainScreen(navController: NavController, onLogout: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val authVm: AuthViewModel = hiltViewModel()
    val profileVm: ProfileViewModel = hiltViewModel()
    val profileState by profileVm.uiState.collectAsState()

    Scaffold(
        containerColor = LightBackground,
        bottomBar = {
            NavigationBar(
                containerColor = LightSurface,
                tonalElevation = 8.dp,
                modifier = Modifier.shadow(8.dp)
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
                    label    = { Text(stringResource(R.string.home), fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium) },
                    colors   = NavigationBarItemDefaults.colors(
                        selectedIconColor   = GreenPrimary,
                        selectedTextColor   = GreenPrimary,
                        indicatorColor      = GreenContainer,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
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
                    label    = { Text(stringResource(R.string.my_lots), fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium) },
                    colors   = NavigationBarItemDefaults.colors(
                        selectedIconColor   = GreenPrimary,
                        selectedTextColor   = GreenPrimary,
                        indicatorColor      = GreenContainer,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
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
                    label    = { Text(stringResource(R.string.profile), fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium) },
                    colors   = NavigationBarItemDefaults.colors(
                        selectedIconColor   = GreenPrimary,
                        selectedTextColor   = GreenPrimary,
                        indicatorColor      = GreenContainer,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> {
                    when (profileState.user?.role) {
                        "recycler" -> RecyclerDashboardScreen()
                        "vendor" -> VendorDashboardScreen()
                        else -> DashboardScreen()
                    }
                }
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
            .background(LightBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Top Banner Header ──────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                color = GreenPrimary
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF065F46), GreenPrimary, Color(0xFF047857))
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Autorenew,
                                        contentDescription = "Logo",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.your_earnings),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Offline badge
                    if (state.isOffline) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd),
                            shape = RoundedCornerShape(10.dp),
                            color = OchreSecondary.copy(alpha = 0.9f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.WifiOff,
                                    contentDescription = "Offline",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.offline_mode),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (state.isLoading && state.today == null) {
                // Initial loading skeleton
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    repeat(3) {
                        LoadingEarningsCard(modifier = Modifier.width(150.dp))
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    EarningsCard(
                        modifier       = Modifier.width(150.dp),
                        icon           = Icons.Filled.WbSunny,
                        title          = stringResource(R.string.today_earnings),
                        summary        = state.today,
                        accentColor    = GreenPrimary,
                        containerColor = GreenContainer,
                        iconTint       = Color(0xFF064E3B)
                    )
                    EarningsCard(
                        modifier       = Modifier.width(150.dp),
                        icon           = Icons.Filled.DateRange,
                        title          = stringResource(R.string.weekly_earnings),
                        summary        = state.weekly,
                        accentColor    = SapphireAccent,
                        containerColor = SapphireContainer,
                        iconTint       = Color(0xFF1E40AF)
                    )
                    EarningsCard(
                        modifier       = Modifier.width(150.dp),
                        icon           = Icons.Filled.Star,
                        title          = stringResource(R.string.monthly_earnings),
                        summary        = state.monthly,
                        accentColor    = AmethystAccent,
                        containerColor = AmethystContainer,
                        iconTint       = Color(0xFF5B21B6)
                    )
                }
            }

            // Error state
            state.error?.let { errMsg ->
                Spacer(Modifier.height(16.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    color = ErrorContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = ErrorColor)
                        Spacer(Modifier.width(8.dp))
                        Text(errMsg, color = ErrorColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Pull-to-refresh hint
            Spacer(Modifier.height(20.dp))
            TextButton(
                onClick  = { viewModel.refresh() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = GreenPrimary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.refresh), color = GreenPrimary, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EarningsCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    summary: EarningsSummary?,
    accentColor: Color,
    containerColor: Color,
    iconTint: Color
) {
    Surface(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color(0x0F000000),
                spotColor = Color(0x1F000000)
            )
            .border(1.dp, LightBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = LightSurface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = containerColor,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(14.dp))

            if (summary != null) {
                Text(
                    "₹ ${"%,.2f".format(summary.total)}",
                    style      = MaterialTheme.typography.headlineSmall,
                    color      = TextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Receipt,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${summary.transactionCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Text(
                    "—",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun LoadingEarningsCard(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue  = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Surface(
        modifier  = modifier
            .height(110.dp)
            .border(1.dp, LightBorder, RoundedCornerShape(16.dp)),
        shape     = RoundedCornerShape(16.dp),
        color     = LightSurfaceVariant.copy(alpha = alpha)
    ) {}
}

// ── Profile Screen ────────────────────────────────────────────────────────────

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
    languageVm: LanguageViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val langState by languageVm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditDialog by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProfileViewModel.UiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                else -> Unit
            }
        }
    }

    Scaffold(
        containerColor = LightBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header (Popped Banner)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(6.dp, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
                    shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                    color = GreenPrimary
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF065F46), GreenPrimary, Color(0xFF047857))
                                )
                            )
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Avatar Badge
                            Surface(
                                modifier = Modifier
                                    .size(84.dp)
                                    .shadow(8.dp, CircleShape),
                                shape = CircleShape,
                                color = Color.White
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Person,
                                        contentDescription = "Profile",
                                        tint = GreenPrimary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
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
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Popped Info Cards
                state.user?.let { user ->
                    ProfileInfoRow(icon = Icons.Filled.Phone, label = stringResource(R.string.phone), value = user.phone ?: "—")
                    ProfileInfoRow(icon = Icons.Filled.Email, label = stringResource(R.string.email), value = user.email ?: "—")
                    ProfileInfoRow(
                        icon = Icons.Filled.Language,
                        label = stringResource(R.string.change_language),
                        value = languageOptions.find { it.code == langState.selectedLanguage }?.nativeName
                            ?: languageOptions.find { it.code == user.language }?.nativeName
                            ?: user.language,
                        showChevron = true,
                        onClick = { showLanguagePicker = true }
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Edit Profile button
                OutlinedButton(
                    onClick  = { showEditDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(52.dp)
                        .shadow(4.dp, RoundedCornerShape(14.dp), spotColor = GreenPrimary.copy(alpha = 0.2f)),
                    shape    = RoundedCornerShape(14.dp),
                    border   = BorderStroke(1.5.dp, GreenPrimary),
                    colors   = ButtonDefaults.outlinedButtonColors(containerColor = LightSurface)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = GreenPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.edit_profile), color = GreenPrimary, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(16.dp))

                // About card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .shadow(4.dp, RoundedCornerShape(18.dp))
                        .border(1.dp, LightBorder, RoundedCornerShape(18.dp)),
                    shape  = RoundedCornerShape(18.dp),
                    color = LightSurface
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = GreenPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.about_app),
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.about_app_desc),
                            style     = MaterialTheme.typography.bodyMedium,
                            color     = TextSecondary,
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
                        .height(52.dp)
                        .shadow(4.dp, RoundedCornerShape(14.dp), spotColor = ErrorColor.copy(alpha = 0.3f)),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = ErrorContainer)
                ) {
                    Icon(Icons.Filled.Logout, contentDescription = null, tint = ErrorColor)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.logout), color = ErrorColor, fontWeight = FontWeight.Bold)
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

    // Language picker dialog
    if (showLanguagePicker) {
        LanguagePickerDialog(
            currentCode = langState.selectedLanguage,
            onDismiss   = { showLanguagePicker = false },
            onConfirm   = { code ->
                languageVm.selectLanguage(code)
                languageVm.confirmLanguage()          // saves to DataStore → triggers ProvideAppLocale
                viewModel.updateProfile(language = code) // sync to server (best-effort)
                showLanguagePicker = false
            }
        )
    }
}

@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x0A000000), spotColor = Color(0x14000000))
            .border(
                width = if (onClick != null) 1.5.dp else 1.dp,
                color = if (onClick != null) GreenPrimary.copy(alpha = 0.35f) else LightBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .then(clickModifier),
        shape = RoundedCornerShape(16.dp),
        color = LightSurface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (onClick != null) GreenPrimary.copy(alpha = 0.12f) else GreenContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (onClick != null) GreenPrimary else GreenOnContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (onClick != null) GreenPrimary else TextSecondary)
                Text(value, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
            if (showChevron) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = GreenPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
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
        containerColor   = LightSurface,
        title = {
            Text(
                stringResource(R.string.edit_profile),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                SethGTextField(value = name,  onValueChange = { name  = it }, label = stringResource(R.string.full_name), leadingIcon = Icons.Filled.Person)
                Spacer(Modifier.height(12.dp))
                SethGTextField(value = phone, onValueChange = { phone = it }, label = stringResource(R.string.phone), leadingIcon = Icons.Filled.Phone, keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                Spacer(Modifier.height(12.dp))
                SethGTextField(value = email, onValueChange = { email = it }, label = stringResource(R.string.email), leadingIcon = Icons.Filled.Email, keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
            }
        },
        confirmButton = {
            Button(
                onClick  = { onSave(name, phone, email) },
                enabled  = !isSaving,
                colors   = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(stringResource(R.string.save), color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = TextSecondary)
            }
        }
    )
}

// ── Language Picker Dialog (reused from LanguageScreen) ───────────────────────

@Composable
private fun LanguagePickerDialog(
    currentCode: String,
    onDismiss: () -> Unit,
    onConfirm: (code: String) -> Unit
) {
    var selected by remember { mutableStateOf(currentCode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = LightSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Language,
                    contentDescription = null,
                    tint = GreenPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.change_language),
                    style      = MaterialTheme.typography.titleLarge,
                    color      = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier            = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                languageOptions.forEach { lang ->
                    LanguageCard(
                        option     = lang,
                        isSelected = selected == lang.code,
                        onClick    = { selected = lang.code }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selected) },
                colors  = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                shape   = RoundedCornerShape(12.dp)
            ) {
                Text(
                    stringResource(R.string.continue_btn),
                    color      = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = TextSecondary)
            }
        }
    )
}
