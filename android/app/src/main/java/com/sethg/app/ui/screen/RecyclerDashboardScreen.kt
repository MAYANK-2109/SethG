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
import androidx.compose.material3.*
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

    Scaffold(
        containerColor = LightBackground,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = SapphireAccent,
                contentColor   = Color.White,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add Purchase", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Header ────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF1D4ED8), Color(0xFF1E3A8A))
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Recycling, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Recycler Dashboard", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.ExtraBold)
                        }

                        // Quick language switch button
                        Surface(
                            modifier = Modifier.clickable { showLanguagePicker = true },
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Language,
                                    contentDescription = "Language",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = languageOptions.find { it.code == langState.selectedLanguage }?.nativeName ?: "Language",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Verification badge
                    if (profileState.user?.isVerified == true) {
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.2f)) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Verified, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Verified Recycler", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        RecyclerStat(label = "Total Spent", value = "₹${"%,.0f".format(totalSpent ?: 0.0)}", icon = Icons.Filled.CurrencyRupee)
                        RecyclerStat(label = "Purchases", value = "${purchases.size}", icon = Icons.Filled.ShoppingBag)
                    }
                }
            }

            // ── Verification gate for market section ─────────────────────────
            if (profileState.user?.isVerified != true) {
                NotVerifiedBanner(onUpload = {
                    profileVm.updateProfile(certificateUrl = "mock_cert_url_123")
                })
            }

            // ── Market (nearby lots, trips, handover) + purchase ledger ────────
            LazyColumn(
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (profileState.user?.isVerified == true) {
                    item { RecyclerMarketSection(onHandover = onHandover) }
                }
                item {
                    Text(
                        "Purchase Ledger",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                if (purchases.isEmpty()) {
                    item {
                        Text(
                            "No purchase records yet.\nTap + Add Purchase to log material bought from a vendor.",
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
private fun RecyclerStat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.18f)) {
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
                Text("Account Not Verified", color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("Upload government certificate to see nearby lots.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onUpload) {
                Text("Upload", color = OchreSecondary, fontWeight = FontWeight.Bold)
            }
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
        "NEW_LIKE" -> "New-like"
        "GOOD"     -> "Good"
        "BAD"      -> "Bad"
        "VERY_BAD" -> "Very Bad"
        else       -> purchase.grade
    }

    Card(
        colors    = CardDefaults.cardColors(containerColor = LightSurface),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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

            // Date
            Spacer(Modifier.height(4.dp))
            Text(
                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(purchase.createdAt)),
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
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
                title = { Text("Add Purchase Record", fontWeight = FontWeight.Bold) },
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
                    else Text("Save Purchase Record", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
            SectionHeader(icon = "👤", title = "Vendor Contact")

            OutlinedTextField(
                value = form.vendorName,
                onValueChange = { v -> onUpdate { copy(vendorName = v) } },
                label = { Text("Vendor Name *") },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = form.vendorPhone,
                onValueChange = { v -> onUpdate { copy(vendorPhone = v) } },
                label = { Text("Vendor Phone (optional)") },
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
                        "Vendor location will be recorded from the location saved when they completed their sale.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Material details
            SectionHeader(icon = "📦", title = "Material Details")

            OutlinedTextField(
                value = form.material,
                onValueChange = { v -> onUpdate { copy(material = v) } },
                label = { Text("Material / E-Waste Type *") },
                leadingIcon = { Icon(Icons.Filled.Category, contentDescription = null) },
                placeholder = { Text("e.g. Mobile Phones, Cables, PCBs...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.weightText,
                    onValueChange = { v -> onUpdate { copy(weightText = v) } },
                    label = { Text("Weight (kg) *") },
                    suffix = { Text("kg") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = form.quantityText,
                    onValueChange = { v -> onUpdate { copy(quantityText = v) } },
                    label = { Text("Quantity") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            // Grade selector
            Text("Quality / Grade", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
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
            SectionHeader(icon = "💰", title = "Payment")

            OutlinedTextField(
                value = form.amountText,
                onValueChange = { v -> onUpdate { copy(amountText = v) } },
                label = { Text("Amount Paid (₹) *") },
                prefix = { Text("₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = form.notes,
                onValueChange = { v -> onUpdate { copy(notes = v) } },
                label = { Text("Notes (optional)") },
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
