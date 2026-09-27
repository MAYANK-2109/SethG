package com.sethg.app.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

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
import androidx.compose.foundation.lazy.LazyColumn
import com.sethg.app.ui.theme.*
import com.sethg.app.ui.viewmodel.AuthViewModel
import com.sethg.app.ui.viewmodel.DashboardViewModel
import com.sethg.app.ui.viewmodel.LanguageViewModel
import com.sethg.app.ui.viewmodel.ProfileViewModel
import com.sethg.app.ui.screen.RecyclerDashboardScreen
import com.sethg.app.ui.screen.VendorDashboardScreen

// ── Bottom-nav wrapper ────────────────────────────────────────────────────────

@Composable
fun MainScreen(navController: NavController, onLogout: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val authVm: AuthViewModel = hiltViewModel()
    val profileVm: ProfileViewModel = hiltViewModel()
    val profileState by profileVm.uiState.collectAsState()
    // Only the generic 'user' and 'vendor' roles create lots via the lot flow.
    val showLotsTab = profileState.user?.role == "user" || profileState.user?.role == "vendor" || profileState.user?.role == null

    // Alerts for new offers (collectors) and nearby lots (recyclers)
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            Surface(
                color = Color.White,
                border = BorderStroke(1.dp, LightBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 1. Home
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick  = { selectedTab = 0 },
                        icon     = {
                            Icon(
                                if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = stringResource(R.string.tab_home),
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label    = {
                            Text(
                                stringResource(R.string.tab_home),
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        },
                        colors   = NavigationBarItemDefaults.colors(
                            selectedIconColor   = GreenPrimary,
                            selectedTextColor   = GreenPrimary,
                            indicatorColor      = Color.Transparent,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )

                    // 2. Listings
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick  = { selectedTab = 1 },
                        icon     = {
                            Icon(
                                if (selectedTab == 1) Icons.Filled.ReceiptLong else Icons.Outlined.ReceiptLong,
                                contentDescription = stringResource(R.string.tab_listings),
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label    = {
                            Text(
                                stringResource(R.string.tab_listings),
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        },
                        colors   = NavigationBarItemDefaults.colors(
                            selectedIconColor   = GreenPrimary,
                            selectedTextColor   = GreenPrimary,
                            indicatorColor      = Color.Transparent,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )

                    // 3. Sell (Triggers Sell / New Lot flow)
                    NavigationBarItem(
                        selected = false,
                        onClick  = {
                            navController.navigate(Screen.NewLot.route)
                        },
                        icon     = {
                            Icon(
                                Icons.Outlined.CropFree,
                                contentDescription = stringResource(R.string.tab_sell),
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label    = {
                            Text(
                                stringResource(R.string.tab_sell),
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        },
                        colors   = NavigationBarItemDefaults.colors(
                            selectedIconColor   = GreenPrimary,
                            selectedTextColor   = GreenPrimary,
                            indicatorColor      = Color.Transparent,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )

                    // 4. Prices
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick  = { selectedTab = 3 },
                        icon     = {
                            Icon(
                                if (selectedTab == 3) Icons.Filled.LocalOffer else Icons.Outlined.LocalOffer,
                                contentDescription = stringResource(R.string.tab_prices),
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label    = {
                            Text(
                                stringResource(R.string.tab_prices),
                                fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        },
                        colors   = NavigationBarItemDefaults.colors(
                            selectedIconColor   = GreenPrimary,
                            selectedTextColor   = GreenPrimary,
                            indicatorColor      = Color.Transparent,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> {
                    when (profileState.user?.role) {
                        "recycler" -> RecyclerDashboardScreen(
                            onHandover = { lotId, declaredKg ->
                                navController.navigate(Screen.Handover.of(lotId, declaredKg))
                            },
                            onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                            onProfileClick = { selectedTab = 4 }
                        )
                        "vendor" -> VendorDashboardScreen(
                            onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                            onProfileClick = { selectedTab = 4 },
                            onViewPriceGuide = { selectedTab = 3 }
                        )
                        else -> DashboardScreen(
                            user = profileState.user,
                            onUploadCertificate = { profileVm.updateProfile(certificateUrl = "mock_cert_url_123") },
                            onProfileClick = { selectedTab = 4 },
                            onNotificationClick = { navController.navigate(Screen.Notifications.route) },
                            onCreateListing = { navController.navigate(Screen.NewLot.route) },
                            onViewPrices = { selectedTab = 3 }
                        )
                    }
                }
                1 -> LotsScreen(
                    onNewLot = { navController.navigate(Screen.NewLot.route) },
                    onOpenLot = { navController.navigate(Screen.LotDetail.of(it)) },
                    onProfileClick = { selectedTab = 4 },
                    onNotificationClick = { navController.navigate(Screen.Notifications.route) }
                )
                3 -> PricesScreen(
                    onProfileClick = { selectedTab = 4 },
                    onNotificationClick = { navController.navigate(Screen.Notifications.route) }
                )
                4 -> ProfileScreen(
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
    user: com.sethg.app.domain.model.User?,
    onUploadCertificate: () -> Unit,
    onProfileClick: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onCreateListing: () -> Unit = {},
    onViewPrices: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Universal Top Header (SethG logo + SCAN • SELL • RECYCLE + notification + profile)
        SethGTopHeader(
            onProfileClick = onProfileClick,
            onNotificationClick = onNotificationClick
        )

        // Mint Sub-Banner
        SethGStatusBanner(
            isOffline = state.isOffline,
            pendingSyncCount = 0
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Screen Title
            item {
                Text(
                    text = stringResource(R.string.vendor_screen_title),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = TextPrimary
                    )
                )
            }

            // Mint Hero Card
            item {
                SethGHeroCard(
                    tag = "PUNE",
                    title = stringResource(R.string.vendor_hero_title),
                    description = stringResource(R.string.vendor_hero_desc),
                    buttonText = stringResource(R.string.create_listing),
                    onButtonClick = onCreateListing
                )
            }

            // Local Price Guide Preview Section
            item {
                SethGSectionHeader(
                    title = stringResource(R.string.local_price_guide),
                    actionText = stringResource(R.string.see_all),
                    onActionClick = onViewPrices
                )
                SethGCard {
                    PriceGuideRow(icon = Icons.Outlined.Smartphone, name = stringResource(R.string.cat_mobile), price = "₹140.00/kg")
                    HorizontalDivider(color = LightBorder, thickness = 0.5.dp)
                    PriceGuideRow(icon = Icons.Outlined.Laptop, name = stringResource(R.string.cat_pcb), price = "₹280.00/kg")
                    HorizontalDivider(color = LightBorder, thickness = 0.5.dp)
                    PriceGuideRow(icon = Icons.Outlined.TabletAndroid, name = stringResource(R.string.cat_battery), price = "₹75.00/kg")
                }
            }

            // Workspace Stats Section
            item {
                SethGSectionHeader(title = stringResource(R.string.your_workspace))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SethGCard(modifier = Modifier.weight(1f)) {
                        Column {
                            Surface(
                                shape = CircleShape,
                                color = MintBackground,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.ReceiptLong,
                                        contentDescription = null,
                                        tint = GreenPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "${state.today?.transactionCount ?: 0}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = TextPrimary
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.recent_transactions),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    SethGCard(modifier = Modifier.weight(1f)) {
                        Column {
                            Surface(
                                shape = CircleShape,
                                color = MintBackground,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = GreenPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "₹ ${"%,.0f".format(state.today?.total ?: 0.0)}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = TextPrimary
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.total_revenue),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            // Role / Verification Card if applicable
            if (user?.role == "recycler" || user?.isVerified == true) {
                item {
                    SethGCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.Verified,
                                contentDescription = null,
                                tint = GreenPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    stringResource(R.string.verified_recycler_badge),
                                    fontWeight = FontWeight.Bold,
                                    color = GreenPrimary,
                                    fontSize = 15.sp
                                )
                                Text(
                                    stringResource(R.string.recycler_certificate_approved),
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            } else if (user?.role != "vendor") {
                item {
                    SethGCard(onClick = onUploadCertificate) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Gavel,
                                contentDescription = null,
                                tint = GreenPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.recycler_verification_title),
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 15.sp
                                )
                                Text(
                                    stringResource(R.string.recycler_verification_desc),
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextMuted)
                        }
                    }
                }
            }
        }
    }
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
    @Suppress("SpellCheckingInspection")
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
                // Universal Top Header
                SethGTopHeader()
                SethGStatusBanner()

                Spacer(Modifier.height(16.dp))

                // Profile Title
                Text(
                    text = stringResource(R.string.my_profile),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )

                Spacer(Modifier.height(16.dp))

                // Clean Profile Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, LightBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = GreenContainer,
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = "Profile",
                                    tint = GreenPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (state.isLoading) {
                                CircularProgressIndicator(color = GreenPrimary, modifier = Modifier.size(20.dp))
                            } else {
                                Text(
                                    state.user?.name ?: "—",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    state.user?.phone ?: state.user?.email ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                Spacer(Modifier.height(6.dp))
                                val roleDisplayName = when {
                                    state.user?.role?.equals("vendor", true) == true -> stringResource(R.string.role_vendor)
                                    state.user?.role?.equals("recycler", true) == true -> stringResource(R.string.role_recycler)
                                    else -> (state.user?.role ?: "user").uppercase()
                                }
                                SethGBadge(text = roleDisplayName)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

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
                    AccountTypeRow(role = user.role)
                }

                Spacer(Modifier.height(24.dp))

                // Edit Profile button
                Button(
                    onClick  = { showEditDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(52.dp)
                        .shadow(4.dp, RoundedCornerShape(14.dp), spotColor = GreenPrimary.copy(alpha = 0.2f)),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.edit_profile), color = Color.White, fontWeight = FontWeight.Bold)
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
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (onClick != null) GreenPrimary else TextPrimary.copy(alpha = 0.85f))
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

// ── Account Type Row ─────────────────────────────────────────────────────────

@Composable
private fun AccountTypeRow(role: String) {
    val isVendor   = role.equals("vendor", ignoreCase = true)
    val isRecycler = role.equals("recycler", ignoreCase = true)

    val icon         = when {
        isVendor   -> Icons.Filled.Store
        isRecycler -> Icons.Filled.Recycling
        else       -> Icons.Filled.Person
    }
    val label        = stringResource(R.string.account_type)
    val vendorLabel  = stringResource(R.string.role_vendor)
    val recyclerLabel = stringResource(R.string.role_recycler)
    val displayName  = when {
        isVendor   -> vendorLabel
        isRecycler -> recyclerLabel
        else       -> role.replaceFirstChar { it.uppercaseChar() }
    }
    val accentColor  = when {
        isVendor   -> SapphireAccent
        isRecycler -> GreenPrimary
        else       -> Color(0xFF4A5568)
    }
    val badgeBg      = when {
        isVendor   -> SapphireContainer
        isRecycler -> GreenContainer
        else       -> LightSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x0A000000), spotColor = Color(0x14000000))
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = LightSurface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon container
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = badgeBg,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(4.dp))
                // Colored pill badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = badgeBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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
