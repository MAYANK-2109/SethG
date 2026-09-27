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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import com.sethg.app.R
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
    onNavigateToNotifications: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onViewPriceGuide: () -> Unit = {},
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

        if (state.photoRejected) {
            AlertDialog(
                onDismissRequest = viewModel::dismissPhotoRejection,
                icon = { Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = OchreSecondary) },
                title = { Text(stringResource(R.string.not_ewaste_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.not_ewaste_message))
                        state.rejectedLabel?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.detected_in_photo, it), color = TextSecondary)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissPhotoRejection(); showCamera = true },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                    ) { Text(stringResource(R.string.retake_photo)) }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissPhotoRejection) { Text(stringResource(R.string.cancel)) }
                }
            )
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

    // ── Clean Reference Dashboard Layout ──────────────────────────────────────────
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
                    onButtonClick = { showWizard = true }
                )
            }

            // Local Price Guide Preview Section
            item {
                SethGSectionHeader(
                    title = stringResource(R.string.local_price_guide),
                    actionText = stringResource(R.string.see_all),
                    onActionClick = onViewPriceGuide
                )
                SethGCard {
                    PriceGuideRow(icon = Icons.Outlined.Smartphone, name = "Mobile phone", price = "₹140.00/kg")
                    HorizontalDivider(color = LightBorder, thickness = 0.5.dp)
                    PriceGuideRow(icon = Icons.Outlined.Laptop, name = "Laptop", price = "₹140.00/kg")
                    HorizontalDivider(color = LightBorder, thickness = 0.5.dp)
                    PriceGuideRow(icon = Icons.Outlined.TabletAndroid, name = "Tablet", price = "₹140.00/kg")
                }
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
                    WorkspaceStatCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.active_lots),
                        value = "${transactions.size}",
                        icon = Icons.Outlined.Inventory2
                    )
                    WorkspaceStatCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.total_revenue),
                        value = "₹${"%,.0f".format(totalRevenue ?: 0.0)}",
                        icon = Icons.Outlined.AccountBalanceWallet
                    )
                }
            }

            // Recent Transactions / Lots
            if (transactions.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.recent_transactions),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(transactions, key = { it.txnId }) { txn ->
                    VendorTransactionCard(txn)
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
private fun WorkspaceStatCard(
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
private fun VendorTransactionCard(txn: VendorTransactionEntity) {
    val gradeColor = when (txn.grade) {
        "NEW_LIKE" -> GreenPrimary
        "GOOD"     -> SapphireAccent
        "BAD"      -> OchreSecondary
        "VERY_BAD" -> ErrorColor
        else       -> TextMuted
    }
    val gradeLabel = when (txn.grade) {
        "NEW_LIKE" -> stringResource(R.string.grade_new_like)
        "GOOD"     -> stringResource(R.string.grade_good)
        "BAD"      -> stringResource(R.string.grade_bad)
        "VERY_BAD" -> stringResource(R.string.grade_very_bad)
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
                Text(txn.customerName.ifBlank { txn.txnId }, color = TextPrimary, fontWeight = FontWeight.Bold)
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
                                if (txn.isWorking) stringResource(R.string.yes_working_label) else stringResource(R.string.no_not_working_label),
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
                        Text(stringResource(R.string.mark_as_paid_save), color = GreenOnContainer, fontSize = 10.sp, fontWeight = FontWeight.Bold,
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
                            stringResource(R.string.step_progress, stepIndex + 1, totalSteps),
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
                                if (state.currentStep == VendorWizardStep.RECORD) stringResource(R.string.mark_as_paid_save) else stringResource(R.string.next_btn),
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

@Composable
private fun wizardStepTitle(step: VendorWizardStep) = when (step) {
    VendorWizardStep.PHOTO          -> "📷 " + stringResource(R.string.wizard_take_photo)
    VendorWizardStep.WEIGHT         -> "⚖️ " + stringResource(R.string.wizard_weight_title)
    VendorWizardStep.GRADE          -> "🏷️ " + stringResource(R.string.wizard_grade_title)
    VendorWizardStep.RUNNABLE       -> "🔌 " + stringResource(R.string.wizard_runnable_title)
    VendorWizardStep.WORKING        -> "✅ " + stringResource(R.string.wizard_working_title)
    VendorWizardStep.PRICE_ESTIMATE -> "💰 " + stringResource(R.string.wizard_price_title)
    VendorWizardStep.RECORD         -> "📋 " + stringResource(R.string.wizard_record_title)
    VendorWizardStep.DONE           -> "✅ " + stringResource(R.string.done)
}

// ── Step Screens ──────────────────────────────────────────────────────────────

@Composable
private fun WizardPhotoStep(state: VendorWizardViewModel.UiState, onTakePhoto: () -> Unit) {
    WizardStepCard(
        icon = "📷",
        title = stringResource(R.string.wizard_take_photo),
        subtitle = ""
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
            Text(stringResource(R.string.retake_photo), color = GreenPrimary)
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
            Text(stringResource(R.string.tap_to_open_camera), color = GreenPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
    WizardStepCard(icon = "⚖️", title = stringResource(R.string.wizard_weight_title), subtitle = "")

    // Category grid
    Text(stringResource(R.string.ewaste_type), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
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
    Text(stringResource(R.string.weight_label), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
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
    Text(stringResource(R.string.quantity_label), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
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
    WizardStepCard(icon = "🏷️", title = stringResource(R.string.wizard_grade_title), subtitle = "")

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
    WizardStepCard(icon = "🔌", title = stringResource(R.string.wizard_runnable_title), subtitle = "")

    Text(
        stringResource(R.string.wizard_runnable_desc),
        color = TextSecondary,
        style = MaterialTheme.typography.bodyMedium
    )

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        YesNoCard(
            label = stringResource(R.string.yes_label),
            emoji = "✅",
            selected = state.isRunnable == true,
            color = GreenPrimary,
            bg = GreenContainer,
            modifier = Modifier.weight(1f),
            onClick = { onSetRunnable(true) }
        )
        YesNoCard(
            label = stringResource(R.string.no_label),
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
                Text(stringResource(R.string.non_runnable_hint), color = TextPrimary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WizardWorkingStep(state: VendorWizardViewModel.UiState, onSetWorking: (Boolean) -> Unit) {
    WizardStepCard(icon = "✅", title = stringResource(R.string.wizard_working_title), subtitle = "")

    Text(
        stringResource(R.string.wizard_working_desc),
        color = TextSecondary,
        style = MaterialTheme.typography.bodyMedium
    )

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        YesNoCard(
            label = stringResource(R.string.yes_working_label),
            emoji = "⚡",
            selected = state.isWorking == true,
            color = GreenPrimary,
            bg = GreenContainer,
            modifier = Modifier.weight(1f),
            onClick = { onSetWorking(true) }
        )
        YesNoCard(
            label = stringResource(R.string.no_not_working_label),
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
    WizardStepCard(icon = "💰", title = stringResource(R.string.wizard_price_title), subtitle = "")

    val estimate = state.estimate
    if (estimate != null) {
        Surface(
            color = OchreContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.est_price_range), color = OchreOnContainer, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "₹${"%,d".format(estimate.low)} – ₹${"%,d".format(estimate.high)}",
                    color = TextPrimary,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(4.dp))
                Text("🤖 " + stringResource(R.string.price_basis_factors), color = OchreSecondary, fontSize = 13.sp)
            }
        }

        // Breakdown
        Surface(color = LightSurface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.inputs_used), color = TextSecondary, fontWeight = FontWeight.SemiBold)
                PriceDetailRow(stringResource(R.string.category_label), state.category?.name?.replace("_", " ") ?: "—")
                PriceDetailRow(stringResource(R.string.weight_label), "${state.weightText} kg × ${state.quantity}")
                PriceDetailRow(stringResource(R.string.grade_label), state.grade?.label?.split(" / ")?.first() ?: "—")
                PriceDetailRow(stringResource(R.string.runnable_label), if (state.isRunnable == true) stringResource(R.string.yes_label) else stringResource(R.string.no_label))
                if (state.isRunnable == true) PriceDetailRow(stringResource(R.string.working_label), if (state.isWorking == true) stringResource(R.string.yes_label) else stringResource(R.string.no_label))
            }
        }

        Text(
            stringResource(R.string.price_negotiate_hint),
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    } else {
        Text(stringResource(R.string.network_error), color = ErrorColor)
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
    WizardStepCard(icon = "📋", title = stringResource(R.string.wizard_record_title), subtitle = "")

    // Transaction summary card
    Surface(color = GreenContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.txn_summary), color = GreenOnContainer, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("📦 ${state.category?.name?.replace("_", " ") ?: "—"} · ${state.weightText} kg · ${state.quantity}", color = TextPrimary)
            Text("🏷️ ${stringResource(R.string.grade_label)}: ${state.grade?.label?.split("/")?.first()?.trim() ?: "—"}", color = TextPrimary)
            Text("🔌 ${stringResource(R.string.runnable_label)}: ${if (state.isRunnable == true) stringResource(R.string.yes_label) else stringResource(R.string.no_label)}" +
                    if (state.isRunnable == true) " | ${stringResource(R.string.working_label)}: ${if (state.isWorking == true) stringResource(R.string.yes_label) else stringResource(R.string.no_label)}" else "", color = TextPrimary)
            state.estimate?.let {
                Text("💰 Estimate: ₹${"%,d".format(it.low)} – ₹${"%,d".format(it.high)}", color = GreenPrimary, fontWeight = FontWeight.Bold)
            }
            Text("📍 " + stringResource(R.string.gps_auto_payment_note), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }

    Text(stringResource(R.string.final_price_label), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
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
                stringResource(R.string.gps_auto_payment_note),
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
            Text(stringResource(R.string.txn_recorded_title), color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            Text("ID: ${state.savedTxnId ?: state.txnId}", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text("📍 " + stringResource(R.string.location_recorded_note), color = GreenPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                Text(stringResource(R.string.back_to_dashboard), fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
