package com.sethg.app.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import com.sethg.app.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sethg.app.data.local.db.RecyclerPurchaseEntity
import com.sethg.app.ui.theme.*
import com.sethg.app.ui.viewmodel.LanguageViewModel
import com.sethg.app.ui.viewmodel.ProfileViewModel
import com.sethg.app.ui.viewmodel.RecyclerPurchaseViewModel
import java.text.SimpleDateFormat
import java.util.*

// ── Recycler Dashboard Screen ────────────────────────────────────────────────
// Shows:
//   • Summary stats (total spent, total purchases)
//   • List of all material purchases with vendor details & location
//   • FAB to add a new purchase entry
//   • "Verified status" gating for the market section

@Composable
fun RecyclerDashboardScreen(
    modifier: Modifier = Modifier,
    onHandover: (lotId: String, declaredKg: Double) -> Unit = { _, _ -> },
    onChat: (lotId: String, vendorName: String) -> Unit = { _, _ -> },
    onNavigateToNotifications: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    profileVm: ProfileViewModel = hiltViewModel(),
    purchaseVm: RecyclerPurchaseViewModel = hiltViewModel(),
    languageVm: LanguageViewModel = hiltViewModel()
) {
    val profileState by profileVm.uiState.collectAsState()
    val langState by languageVm.uiState.collectAsState()
    val purchases by purchaseVm.purchases.collectAsState()
    val totalSpent by purchaseVm.totalSpent.collectAsState()
    val form by purchaseVm.form.collectAsState()
    val isSaving by purchaseVm.isSaving.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    LaunchedEffect(purchaseVm) {
        purchaseVm.savedId.collect { showAddSheet = false }
    }

    if (showAddSheet) {
        AddPurchaseSheet(
            form     = form,
            isSaving = isSaving,
            onUpdate = purchaseVm::update,
            onSave   = purchaseVm::save,
            onDismiss = { showAddSheet = false }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Universal Top Header
        SethGTopHeader(
            onProfileClick = onProfileClick,
            onNotificationClick = onNavigateToNotifications,
            onLanguageClick = { showLanguagePicker = true }
        )

        // Mint Sub-Banner
        SethGStatusBanner()

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Screen Title
            item {
                Text(
                    text = stringResource(R.string.recycler_dashboard_title),
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
                    tag = if (profileState.user?.isVerified == true) stringResource(R.string.verified_recycler_badge) else stringResource(R.string.recycler_workspace_badge),
                    title = stringResource(R.string.recycler_hero_title),
                    description = stringResource(R.string.recycler_hero_desc),
                    buttonText = stringResource(R.string.add_purchase),
                    onButtonClick = { showAddSheet = true }
                )
            }

            // Workspace Stats Section
            item {
                Text(
                    text = stringResource(R.string.your_workspace),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RecyclerWorkspaceCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.purchases_label),
                        value = "${purchases.size}",
                        icon = Icons.Outlined.ShoppingBag
                    )
                    RecyclerWorkspaceCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.total_spent_label),
                        value = "₹${"%,.0f".format(totalSpent ?: 0.0)}",
                        icon = Icons.Outlined.CurrencyRupee
                    )
                }
            }

            // Verification gate for market section
            if (profileState.user?.isVerified != true) {
                item {
                    NotVerifiedBanner(onUpload = {
                        profileVm.updateProfile(certificateUrl = "mock_cert_url_123")
                    })
                }
            }

            // Market (nearby lots, trips, handover)
            if (profileState.user?.isVerified == true) {
                item { RecyclerMarketSection(onHandover = onHandover, onChat = onChat) }
            }
            item {
                Text(
                    stringResource(R.string.purchase_ledger),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            if (purchases.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_purchases_yet),
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    )
                }
            } else {
                items(purchases, key = { it.purchaseId }) { purchase ->
                    RecyclerPurchaseCard(purchase = purchase, onDelete = { purchaseVm.delete(it) })
                }
            }
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    if (showLanguagePicker) {
        LanguagePickerDialog(
            currentCode = langState.selectedLanguage,
            onDismiss   = { showLanguagePicker = false },
            onConfirm   = { code ->
                languageVm.selectLanguage(code)
                languageVm.confirmLanguage()
                showLanguagePicker = false
            }
        )
    }
}

@Composable
private fun RecyclerStat(modifier: Modifier = Modifier, label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.18f)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Column {
                Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun NotVerifiedBanner(onUpload: () -> Unit) {
    Surface(
        color = OchreContainer,
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = OchreSecondary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.account_not_verified), color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.upload_cert_to_see_lots), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onUpload) {
                Text(stringResource(R.string.upload), color = OchreSecondary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RecyclerWorkspaceCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, LightBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GreenPrimary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun RecyclerPurchaseCard(purchase: RecyclerPurchaseEntity, onDelete: (String) -> Unit) {
    val gradeColor = when (purchase.grade) {
        "NEW_LIKE" -> GreenPrimary
        "GOOD"     -> SapphireAccent
        "BAD"      -> OchreSecondary
        "VERY_BAD" -> ErrorColor
        else       -> TextMuted
    }
    val gradeLabel = when (purchase.grade) {
        "NEW_LIKE" -> stringResource(R.string.grade_new_like)
        "GOOD"     -> stringResource(R.string.grade_good)
        "BAD"      -> stringResource(R.string.grade_bad)
        "VERY_BAD" -> stringResource(R.string.grade_very_bad)
        else       -> purchase.grade
    }

    Surface(
        shape  = RoundedCornerShape(16.dp),
        color  = Color.White,
        border = BorderStroke(1.dp, LightBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Material icon
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SapphireAccent.copy(alpha = 0.12f),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(materialEmoji(purchase.material), fontSize = 26.sp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(purchase.material, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        "${purchase.weightKg} kg · qty ${purchase.quantity}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Surface(shape = RoundedCornerShape(6.dp), color = gradeColor.copy(alpha = 0.15f)) {
                        Text(gradeLabel, color = gradeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("₹${"%,.0f".format(purchase.amountPaid)}", color = SapphireAccent, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    IconButton(onClick = { onDelete(purchase.purchaseId) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = ErrorColor, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Divider(color = LightBorder, thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            // Vendor contact row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(purchase.vendorName, color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (!purchase.vendorPhone.isNullOrBlank()) {
                    Surface(shape = RoundedCornerShape(8.dp), color = GreenContainer) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Phone, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(purchase.vendorPhone, color = GreenPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Location
            if (purchase.vendorLat != null && purchase.vendorLon != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = SapphireAccent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Vendor at %.4f°, %.4f°".format(purchase.vendorLat, purchase.vendorLon),
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Notes
            if (!purchase.notes.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("📝 ${purchase.notes}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }

            // Date & Dispute actions
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(purchase.createdAt)),
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
                
                var showDisputeDialog by remember { mutableStateOf(false) }
                var disputeReason by remember { mutableStateOf("") }
                var disputeSubmitted by remember { mutableStateOf(false) }

                if (disputeSubmitted) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFEBEE),
                        border = BorderStroke(1.dp, ErrorColor)
                    ) {
                        Text(
                            "🚨 Dispute Open",
                            color = ErrorColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { showDisputeDialog = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorColor),
                        border = BorderStroke(1.dp, ErrorColor.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Raise Dispute", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (showDisputeDialog) {
                    AlertDialog(
                        onDismissRequest = { showDisputeDialog = false },
                        title = { Text("🚨 Raise Escrow & Grade Dispute", fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                Text("If physical weight or grade differs from declared values (>15% variance), raising a dispute will freeze digital escrow funds until resolved.", fontSize = 13.sp, color = TextSecondary)
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = disputeReason,
                                    onValueChange = { disputeReason = it },
                                    label = { Text("Reason (e.g. Weight variance 22% lower)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    disputeSubmitted = true
                                    showDisputeDialog = false
                                },
                                enabled = disputeReason.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)
                            ) {
                                Text("Freeze Escrow & Submit")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDisputeDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }
}

// ── Add Purchase Sheet ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPurchaseSheet(
    form: RecyclerPurchaseViewModel.NewPurchaseForm,
    isSaving: Boolean,
    onUpdate: (RecyclerPurchaseViewModel.NewPurchaseForm.() -> RecyclerPurchaseViewModel.NewPurchaseForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val grades = listOf(
        "NEW_LIKE" to "New-like / जैसा नया",
        "GOOD"     to "Good / अच्छा",
        "BAD"      to "Bad / खराब",
        "VERY_BAD" to "Very Bad / बहुत खराब"
    )

    Scaffold(
        containerColor = LightBackground,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_purchase_record_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        },
        bottomBar = {
            Surface(color = LightSurface, tonalElevation = 8.dp) {
                Button(
                    onClick = onSave,
                    enabled = form.isValid && !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .height(56.dp),
                    shape  = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SapphireAccent)
                ) {
                    if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    else Text(stringResource(R.string.save_purchase_record), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Vendor details section
            SectionHeader(icon = "👤", title = stringResource(R.string.vendor_contact_section))

            OutlinedTextField(
                value = form.vendorName,
                onValueChange = { v -> onUpdate { copy(vendorName = v) } },
                label = { Text(stringResource(R.string.vendor_name_label)) },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = form.vendorPhone,
                onValueChange = { v -> onUpdate { copy(vendorPhone = v) } },
                label = { Text(stringResource(R.string.vendor_phone_label)) },
                leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )

            // Vendor location from last known GPS (or enter manually later)
            Surface(color = SapphireAccent.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = SapphireAccent)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.vendor_location_note),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Material details
            SectionHeader(icon = "📦", title = stringResource(R.string.material_details_section))

            OutlinedTextField(
                value = form.material,
                onValueChange = { v -> onUpdate { copy(material = v) } },
                label = { Text(stringResource(R.string.material_type_label)) },
                leadingIcon = { Icon(Icons.Filled.Category, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.material_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.weightText,
                    onValueChange = { v -> onUpdate { copy(weightText = v) } },
                    label = { Text(stringResource(R.string.weight_kg_label)) },
                    suffix = { Text("kg") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = form.quantityText,
                    onValueChange = { v -> onUpdate { copy(quantityText = v) } },
                    label = { Text(stringResource(R.string.quantity_short_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            // Grade selector
            Text(stringResource(R.string.wizard_grade_title), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                grades.forEach { (code, label) ->
                    val sel = form.grade == code
                    val color = when (code) {
                        "NEW_LIKE" -> GreenPrimary
                        "GOOD"     -> SapphireAccent
                        "BAD"      -> OchreSecondary
                        "VERY_BAD" -> ErrorColor
                        else       -> ErrorColor
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.5.dp, if (sel) color else LightBorder, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onUpdate { copy(grade = code) } },
                        color = if (sel) color.copy(alpha = 0.12f) else LightSurface
                    ) {
                        Text(
                            label.split(" / ").first(),
                            color = if (sel) color else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }

            // Amount
            SectionHeader(icon = "💰", title = stringResource(R.string.payment_section))

            OutlinedTextField(
                value = form.amountText,
                onValueChange = { v -> onUpdate { copy(amountText = v) } },
                label = { Text(stringResource(R.string.amount_paid_label)) },
                prefix = { Text("₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = form.notes,
                onValueChange = { v -> onUpdate { copy(notes = v) } },
                label = { Text(stringResource(R.string.notes_optional_label)) },
                leadingIcon = { Icon(Icons.Filled.Notes, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionHeader(icon: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
    }
}

private fun materialEmoji(material: String): String {
    val m = material.lowercase()
    return when {
        "mobile" in m || "phone" in m -> "📱"
        "cable" in m -> "🔌"
        "battery" in m -> "🔋"
        "pcb" in m || "board" in m -> "💾"
        "tv" in m || "crt" in m -> "📺"
        "lcd" in m || "screen" in m -> "🖥️"
        "motor" in m -> "⚙️"
        "plastic" in m -> "♻️"
        "charger" in m -> "🔌"
        else -> "📦"
    }
}
