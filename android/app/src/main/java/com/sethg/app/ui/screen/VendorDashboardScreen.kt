package com.sethg.app.ui.screen

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.sethg.app.data.local.db.VendorTransactionEntity
import com.sethg.app.domain.model.MaterialCategory
import com.sethg.app.ui.theme.*
import com.sethg.app.ui.viewmodel.EWasteGrade
import com.sethg.app.ui.viewmodel.LanguageViewModel
import com.sethg.app.ui.viewmodel.VendorWizardStep
import com.sethg.app.ui.viewmodel.VendorWizardViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ── Vendor Dashboard Screen ───────────────────────────────────────────────────

@Composable
fun VendorDashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: VendorWizardViewModel = hiltViewModel(),
    languageVm: LanguageViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val langState by languageVm.uiState.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val totalRevenue by viewModel.totalRevenue.collectAsState()
    var showWizard by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Location permission
    val locLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    LaunchedEffect(Unit) {
        locLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    if (showWizard) {
        if (showCamera) {
            BackHandler { showCamera = false }
            CameraCapture(
                newPhotoFile = {
                    val dir = java.io.File(context.filesDir, "vtx").also { it.mkdirs() }
                    java.io.File.createTempFile("vtx_", ".jpg", dir)
                },
                onPhotoCaptured = { file ->
                    viewModel.setPhoto(file.absolutePath)
                    showCamera = false
                },
                onClose = { showCamera = false }
            )
            return
        }

        BackHandler {
            if (state.currentStep == VendorWizardStep.PHOTO) {
                showWizard = false
                viewModel.resetWizard()
            } else {
                viewModel.prevStep()
            }
        }

        VendorCustomerWizard(
            state = state,
            onBack = {
                if (state.currentStep == VendorWizardStep.PHOTO) {
                    showWizard = false
                    viewModel.resetWizard()
                } else {
                    viewModel.prevStep()
                }
            },
            onTakePhoto = { showCamera = true },
            onSetCategory = viewModel::setCategory,
            onSetWeight = viewModel::setWeightText,
            onIncrementQty = viewModel::incrementQuantity,
            onDecrementQty = viewModel::decrementQuantity,
            onSetGrade = viewModel::setGrade,
            onSetRunnable = viewModel::setRunnable,
            onSetWorking = viewModel::setWorking,
            onSetCustomerName = viewModel::setCustomerName,
            onSetFinalPrice = viewModel::setFinalPrice,
            onNext = viewModel::nextStep,
            onSave = viewModel::save,
            onDone = {
                showWizard = false
                viewModel.resetWizard()
            }
        )
        return
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────
    Scaffold(
        containerColor = LightBackground,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showWizard = true },
                containerColor = GreenPrimary,
                contentColor   = Color.White,
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text("Add Customer", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF047857), Color(0xFF065F46))
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
                            Icon(
                                Icons.Filled.ShoppingCart,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Vendor Dashboard",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        // Quick language switcher pill
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
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        VendorStat(
                            label = "Total Revenue",
                            value = "₹${"%,.0f".format(totalRevenue ?: 0.0)}",
                            icon = Icons.Filled.CurrencyRupee
                        )
                        VendorStat(
                            label = "Transactions",
                            value = "${transactions.size}",
                            icon = Icons.Filled.Receipt
                        )
                    }
                }
            }

            if (transactions.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📦", fontSize = 56.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No transactions yet.\nTap + Add Customer to begin.",
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Recent Transactions",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(transactions, key = { it.txnId }) { txn ->
                        VendorTransactionCard(txn)
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
private fun VendorStat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.18f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
private fun VendorTransactionCard(txn: VendorTransactionEntity) {
    val gradeColor = when (txn.grade) {
        "NEW_LIKE" -> GreenPrimary
        "GOOD"     -> SapphireAccent
        "BAD"      -> OchreSecondary
        "VERY_BAD" -> ErrorColor
        else       -> TextMuted
    }
    val gradeLabel = when (txn.grade) {
        "NEW_LIKE" -> "New-like"
        "GOOD"     -> "Good"
        "BAD"      -> "Bad"
        "VERY_BAD" -> "Very Bad"
        else       -> txn.grade
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape  = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // Photo / placeholder
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LightSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (txn.photoPath != null) {
                    AsyncImage(
                        model = File(txn.photoPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Filled.BrokenImage, contentDescription = null, tint = TextMuted, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(txn.customerName, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    "${txn.category.replace("_", " ")} · ${txn.weightKg} kg · qty ${txn.quantity}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(6.dp), color = gradeColor.copy(alpha = 0.15f)) {
                        Text(gradeLabel, color = gradeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    if (txn.isRunnable) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = SapphireAccent.copy(alpha = 0.12f)) {
                            Text(
                                if (txn.isWorking) "⚡ Working" else "🔇 Not Working",
                                color = SapphireAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(txn.createdAt)),
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "₹${"%,.0f".format(txn.finalPrice)}",
                    color = GreenPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
                if (txn.isPaid) {
                    Surface(shape = RoundedCornerShape(6.dp), color = GreenContainer) {
                        Text("PAID", color = GreenOnContainer, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}

// ── Vendor Customer Wizard ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VendorCustomerWizard(
    state: VendorWizardViewModel.UiState,
    onBack: () -> Unit,
    onTakePhoto: () -> Unit,
    onSetCategory: (MaterialCategory) -> Unit,
    onSetWeight: (String) -> Unit,
    onIncrementQty: () -> Unit,
    onDecrementQty: () -> Unit,
    onSetGrade: (EWasteGrade) -> Unit,
    onSetRunnable: (Boolean) -> Unit,
    onSetWorking: (Boolean) -> Unit,
    onSetCustomerName: (String) -> Unit,
    onSetFinalPrice: (String) -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
    onDone: () -> Unit
) {
    if (state.currentStep == VendorWizardStep.DONE) {
        WizardDoneScreen(state = state, onDone = onDone)
        return
    }

    val steps = VendorWizardStep.values().filter { it != VendorWizardStep.DONE }
    val stepIndex = steps.indexOf(state.currentStep).coerceAtLeast(0)
    val totalSteps = steps.size

    Scaffold(
        containerColor = LightBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            wizardStepTitle(state.currentStep),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Step ${stepIndex + 1} of $totalSteps",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        },
        bottomBar = {
            Surface(color = LightSurface, tonalElevation = 8.dp) {
                Column(Modifier.navigationBarsPadding()) {
                    // Progress bar
                    LinearProgressIndicator(
                        progress = { (stepIndex + 1).toFloat() / totalSteps.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = GreenPrimary,
                        trackColor = GreenContainer
                    )
                    Button(
                        onClick = if (state.currentStep == VendorWizardStep.RECORD) onSave else onNext,
                        enabled = canProceed(state) && !state.isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(56.dp),
                        shape  = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                if (state.currentStep == VendorWizardStep.RECORD) "Mark as Paid & Save" else "Next →",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = state.currentStep,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
            },
            label = "wizard_step"
        ) { step ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                when (step) {
                    VendorWizardStep.PHOTO -> WizardPhotoStep(state, onTakePhoto)
                    VendorWizardStep.WEIGHT -> WizardWeightStep(state, onSetCategory, onSetWeight, onIncrementQty, onDecrementQty)
                    VendorWizardStep.GRADE -> WizardGradeStep(state, onSetGrade)
                    VendorWizardStep.RUNNABLE -> WizardRunnableStep(state, onSetRunnable)
                    VendorWizardStep.WORKING -> WizardWorkingStep(state, onSetWorking)
                    VendorWizardStep.PRICE_ESTIMATE -> WizardPriceStep(state)
                    VendorWizardStep.RECORD -> WizardRecordStep(state, onSetCustomerName, onSetFinalPrice)
                    VendorWizardStep.DONE -> { /* handled above */ }
                }
                state.error?.let { Text(it, color = ErrorColor) }
            }
        }
    }
}

private fun canProceed(state: VendorWizardViewModel.UiState) = when (state.currentStep) {
    VendorWizardStep.PHOTO           -> state.canProceedPhoto
    VendorWizardStep.WEIGHT          -> state.canProceedWeight
    VendorWizardStep.GRADE           -> state.canProceedGrade
    VendorWizardStep.RUNNABLE        -> state.canProceedRunnable
    VendorWizardStep.WORKING         -> state.canProceedWorking
    VendorWizardStep.PRICE_ESTIMATE  -> true
    VendorWizardStep.RECORD          -> state.canRecord
    VendorWizardStep.DONE            -> false
}

private fun wizardStepTitle(step: VendorWizardStep) = when (step) {
    VendorWizardStep.PHOTO          -> "📷 Take E-Waste Photo"
    VendorWizardStep.WEIGHT         -> "⚖️ Weight & Category"
    VendorWizardStep.GRADE          -> "🏷️ Quality / Grade"
    VendorWizardStep.RUNNABLE       -> "🔌 Is it Runnable?"
    VendorWizardStep.WORKING        -> "✅ Is it Still Working?"
    VendorWizardStep.PRICE_ESTIMATE -> "💰 Price Estimate"
    VendorWizardStep.RECORD         -> "📋 Record Transaction"
    VendorWizardStep.DONE           -> "✅ Done!"
}

// ── Step Screens ──────────────────────────────────────────────────────────────

@Composable
private fun WizardPhotoStep(state: VendorWizardViewModel.UiState, onTakePhoto: () -> Unit) {
    WizardStepCard(
        icon = "📷",
        title = "Take a photo of the E-Waste",
        subtitle = "ई-कचरे की फोटो लें"
    )

    if (state.photoPath != null) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().aspectRatio(4 / 3f)
        ) {
            AsyncImage(
                model = File(state.photoPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Button(
            onClick = onTakePhoto,
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = GreenPrimary)
            Spacer(Modifier.width(8.dp))
            Text("Retake Photo", color = GreenPrimary)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4 / 3f)
                .clip(RoundedCornerShape(20.dp))
                .border(2.dp, GreenPrimary, RoundedCornerShape(20.dp))
                .background(GreenContainer.copy(alpha = 0.3f))
                .clickable(onClick = onTakePhoto),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(12.dp))
            Text("Tap to Open Camera", color = GreenPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("कैमरा खोलने के लिए टैप करें", color = TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun WizardWeightStep(
    state: VendorWizardViewModel.UiState,
    onSetCategory: (MaterialCategory) -> Unit,
    onSetWeight: (String) -> Unit,
    onIncrementQty: () -> Unit,
    onDecrementQty: () -> Unit
) {
    WizardStepCard(icon = "⚖️", title = "Enter weight & select category", subtitle = "वज़न और प्रकार भरें")

    // Category grid
    Text("E-Waste Type", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
    MaterialCategory.entries.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { cat ->
                val sel = state.category == cat
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .border(2.dp, if (sel) GreenPrimary else LightBorder, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSetCategory(cat) },
                    color = if (sel) GreenPrimary.copy(alpha = 0.15f) else LightSurface
                ) {
                    Column(
                        Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(categoryEmoji(cat), fontSize = 26.sp)
                        Text(
                            cat.name.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() },
                            color = if (sel) GreenPrimary else TextPrimary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }

    // Weight
    Text("Weight (kg) · वज़न", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
    OutlinedTextField(
        value = state.weightText,
        onValueChange = onSetWeight,
        suffix = { Text("kg") },
        placeholder = { Text("0.0") },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )

    // Quantity
    Text("Quantity · मात्रा", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        FilledIconButton(onClick = onDecrementQty, colors = IconButtonDefaults.filledIconButtonColors(containerColor = GreenContainer)) {
            Icon(Icons.Filled.Remove, contentDescription = null, tint = GreenPrimary)
        }
        Text(
            "${state.quantity}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 28.dp)
        )
        FilledIconButton(onClick = onIncrementQty, colors = IconButtonDefaults.filledIconButtonColors(containerColor = GreenPrimary)) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
private fun WizardGradeStep(state: VendorWizardViewModel.UiState, onSetGrade: (EWasteGrade) -> Unit) {
    WizardStepCard(icon = "🏷️", title = "Select Quality Grade", subtitle = "क्वालिटी / दर्जा चुनें")

    EWasteGrade.entries.forEach { grade ->
        val sel = state.grade == grade
        val (color, bg, emoji) = when (grade) {
            EWasteGrade.NEW_LIKE -> Triple(GreenPrimary,    GreenContainer,    "✨")
            EWasteGrade.GOOD     -> Triple(SapphireAccent,  SapphireContainer, "👍")
            EWasteGrade.BAD      -> Triple(OchreSecondary,  OchreContainer,    "👌")
            EWasteGrade.VERY_BAD -> Triple(ErrorColor,      ErrorContainer,    "⚠️")
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, if (sel) color else LightBorder, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .clickable { onSetGrade(grade) },
            color = if (sel) bg else LightSurface
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(emoji, fontSize = 28.sp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(grade.label.split(" / ").first(), color = if (sel) color else TextPrimary, fontWeight = FontWeight.Bold)
                    Text(grade.label.split(" / ").last(), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                if (sel) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = color)
            }
        }
    }
}

@Composable
private fun WizardRunnableStep(state: VendorWizardViewModel.UiState, onSetRunnable: (Boolean) -> Unit) {
    WizardStepCard(icon = "🔌", title = "Is it a runnable device?", subtitle = "क्या यह चलने वाला उपकरण है?")

    Text(
        "A runnable device is something that has an on/off switch — like a phone, TV, computer, printer, mixer, etc.",
        color = TextSecondary,
        style = MaterialTheme.typography.bodyMedium
    )

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        YesNoCard(
            label = "Yes · हाँ",
            emoji = "✅",
            selected = state.isRunnable == true,
            color = GreenPrimary,
            bg = GreenContainer,
            modifier = Modifier.weight(1f),
            onClick = { onSetRunnable(true) }
        )
        YesNoCard(
            label = "No · नहीं",
            emoji = "❌",
            selected = state.isRunnable == false,
            color = ErrorColor,
            bg = ErrorContainer,
            modifier = Modifier.weight(1f),
            onClick = { onSetRunnable(false) }
        )
    }

    if (state.isRunnable == false) {
        Surface(
            color = GreenContainer,
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = GreenPrimary)
                Spacer(Modifier.width(10.dp))
                Text("Non-runnable parts (cables, PCBs, batteries) will be priced by weight only.", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WizardWorkingStep(state: VendorWizardViewModel.UiState, onSetWorking: (Boolean) -> Unit) {
    WizardStepCard(icon = "✅", title = "Is the device still working?", subtitle = "क्या उपकरण अभी भी काम करता है?")

    Text(
        "Working devices command a significantly higher price — up to 25% premium.",
        color = TextSecondary,
        style = MaterialTheme.typography.bodyMedium
    )

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        YesNoCard(
            label = "Yes, Working · काम करता है",
            emoji = "⚡",
            selected = state.isWorking == true,
            color = GreenPrimary,
            bg = GreenContainer,
            modifier = Modifier.weight(1f),
            onClick = { onSetWorking(true) }
        )
        YesNoCard(
            label = "No, Not Working · काम नहीं करता",
            emoji = "🔇",
            selected = state.isWorking == false,
            color = ErrorColor,
            bg = ErrorContainer,
            modifier = Modifier.weight(1f),
            onClick = { onSetWorking(false) }
        )
    }
}

@Composable
private fun WizardPriceStep(state: VendorWizardViewModel.UiState) {
    WizardStepCard(icon = "💰", title = "ML Price Estimate", subtitle = "अनुमानित कीमत (AI मॉडल)")

    val estimate = state.estimate
    if (estimate != null) {
        Surface(
            color = OchreContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Estimated Price Range", color = OchreOnContainer, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "₹${"%,d".format(estimate.low)} – ₹${"%,d".format(estimate.high)}",
                    color = TextPrimary,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(4.dp))
                Text("🤖 Based on location, category, grade & working status", color = OchreSecondary, fontSize = 13.sp)
            }
        }

        // Breakdown
        Surface(color = LightSurface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Inputs used:", color = TextSecondary, fontWeight = FontWeight.SemiBold)
                PriceDetailRow("Category", state.category?.name?.replace("_", " ") ?: "—")
                PriceDetailRow("Weight", "${state.weightText} kg × ${state.quantity} unit(s)")
                PriceDetailRow("Grade", state.grade?.label?.split(" / ")?.first() ?: "—")
                PriceDetailRow("Runnable?", if (state.isRunnable == true) "Yes" else "No")
                if (state.isRunnable == true) PriceDetailRow("Working?", if (state.isWorking == true) "Yes (+25%)" else "No")
            }
        }

        Text(
            "Final price is negotiated with the customer. You can adjust it in the next step.",
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    } else {
        Text("Could not estimate price. Please check location permissions and try again.", color = ErrorColor)
    }
}

@Composable
private fun PriceDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WizardRecordStep(
    state: VendorWizardViewModel.UiState,
    onSetCustomerName: (String) -> Unit,
    onSetFinalPrice: (String) -> Unit
) {
    WizardStepCard(icon = "📋", title = "Record Transaction", subtitle = "लेन-देन दर्ज करें")

    // Transaction summary card
    Surface(color = GreenContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Transaction Summary", color = GreenOnContainer, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("📦 ${state.category?.name?.replace("_", " ") ?: "—"} · ${state.weightText} kg · qty ${state.quantity}", color = TextPrimary)
            Text("🏷️ Grade: ${state.grade?.label?.split("/")?.first()?.trim() ?: "—"}", color = TextPrimary)
            Text("🔌 Runnable: ${if (state.isRunnable == true) "Yes" else "No"}" +
                    if (state.isRunnable == true) " | Working: ${if (state.isWorking == true) "Yes" else "No"}" else "", color = TextPrimary)
            state.estimate?.let {
                Text("💰 Estimate: ₹${"%,d".format(it.low)} – ₹${"%,d".format(it.high)}", color = GreenPrimary, fontWeight = FontWeight.Bold)
            }
            Text("📍 Location captured automatically at payment", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }

    Text("Customer Name / ग्राहक का नाम", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
    OutlinedTextField(
        value = state.customerName,
        onValueChange = onSetCustomerName,
        placeholder = { Text("Enter customer name") },
        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Text("Final Price (₹) / असली कीमत", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
    OutlinedTextField(
        value = state.finalPrice,
        onValueChange = onSetFinalPrice,
        prefix = { Text("₹") },
        placeholder = { Text("0") },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )

    Surface(color = OchreContainer, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = OchreSecondary)
            Spacer(Modifier.width(10.dp))
            Text(
                "Your current GPS location will be captured and stored when you mark this as paid.",
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun WizardDoneScreen(state: VendorWizardViewModel.UiState, onDone: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(LightBackground), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("✅", fontSize = 80.sp)
            Spacer(Modifier.height(16.dp))
            Text("Transaction Recorded!", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text("लेन-देन दर्ज हो गया!", color = TextSecondary, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            Text("ID: ${state.savedTxnId ?: state.txnId}", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text("📍 Location recorded · Payment marked ✓", color = GreenPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                Text("Back to Dashboard", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Shared Composables ────────────────────────────────────────────────────────

@Composable
private fun WizardStepCard(icon: String, title: String, subtitle: String) {
    Surface(
        color = LightSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 36.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun YesNoCard(
    label: String,
    emoji: String,
    selected: Boolean,
    color: Color,
    bg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .border(2.dp, if (selected) color else LightBorder, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = if (selected) bg else LightSurface
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(emoji, fontSize = 36.sp)
            Spacer(Modifier.height(8.dp))
            Text(label, color = if (selected) color else TextPrimary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center, fontSize = 13.sp)
        }
    }
}

private fun categoryEmoji(cat: MaterialCategory) = when (cat) {
    MaterialCategory.CABLE   -> "🔌"
    MaterialCategory.CHARGER -> "🔋"
    MaterialCategory.PCB     -> "💾"
    MaterialCategory.MOBILE  -> "📱"
    MaterialCategory.BATTERY -> "🔋"
    MaterialCategory.MOTOR   -> "⚙️"
    MaterialCategory.SWITCH  -> "🔘"
    MaterialCategory.LCD     -> "🖥️"
    MaterialCategory.CRT     -> "📺"
    MaterialCategory.PLASTIC -> "♻️"
    MaterialCategory.OTHER   -> "📦"
}
