package com.sethg.app.ui.screen

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.sethg.app.R
import com.sethg.app.data.local.City
import com.sethg.app.data.local.ResolvedRate
import com.sethg.app.data.local.Zone
import com.sethg.app.domain.model.Lot
import com.sethg.app.domain.model.MaterialCategory
import com.sethg.app.domain.model.PriceEstimate
import com.sethg.app.ui.theme.*
import com.sethg.app.ui.viewmodel.LotsViewModel
import com.sethg.app.ui.viewmodel.NewLotViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Category visuals (icon-first for low-literacy users) ─────────────────────

private val MaterialCategory.icon: ImageVector
    get() = when (this) {
        MaterialCategory.CABLE   -> Icons.Filled.Cable
        MaterialCategory.CHARGER -> Icons.Filled.Power
        MaterialCategory.PCB     -> Icons.Filled.Memory
        MaterialCategory.MOBILE  -> Icons.Filled.Smartphone
        MaterialCategory.BATTERY -> Icons.Filled.BatteryFull
        MaterialCategory.MOTOR   -> Icons.Filled.Settings
        MaterialCategory.SWITCH  -> Icons.Filled.ToggleOn
        MaterialCategory.LCD     -> Icons.Filled.Monitor
        MaterialCategory.CRT     -> Icons.Filled.Tv
        MaterialCategory.PLASTIC -> Icons.Filled.Recycling
        MaterialCategory.OTHER   -> Icons.Filled.Category
    }

private val MaterialCategory.labelRes: Int
    get() = when (this) {
        MaterialCategory.CABLE   -> R.string.cat_cable
        MaterialCategory.CHARGER -> R.string.cat_charger
        MaterialCategory.PCB     -> R.string.cat_pcb
        MaterialCategory.MOBILE  -> R.string.cat_mobile
        MaterialCategory.BATTERY -> R.string.cat_battery
        MaterialCategory.MOTOR   -> R.string.cat_motor
        MaterialCategory.SWITCH  -> R.string.cat_switch
        MaterialCategory.LCD     -> R.string.cat_lcd
        MaterialCategory.CRT     -> R.string.cat_crt
        MaterialCategory.PLASTIC -> R.string.cat_plastic
        MaterialCategory.OTHER   -> R.string.cat_other
    }

private fun PriceEstimate.format() = "₹%,d – ₹%,d".format(low, high)

private val Zone.labelRes: Int
    get() = when (this) {
        Zone.NORTH     -> R.string.zone_north
        Zone.WEST      -> R.string.zone_west
        Zone.SOUTH     -> R.string.zone_south
        Zone.EAST      -> R.string.zone_east
        Zone.CENTRAL   -> R.string.zone_central
        Zone.NORTHEAST -> R.string.zone_northeast
    }

// ── My lots (bottom-nav tab) ──────────────────────────────────────────────────

@Composable
fun LotsScreen(
    onNewLot: () -> Unit,
    viewModel: LotsViewModel = hiltViewModel()
) {
    val lots by viewModel.lots.collectAsState()

    Scaffold(
        containerColor = LightBackground,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewLot,
                containerColor = GreenPrimary,
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.AddAPhoto, contentDescription = null) },
                text = { Text(stringResource(R.string.new_lot), fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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
                Text(
                    stringResource(R.string.my_lots),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            if (lots.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📦", fontSize = 56.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.no_lots_yet),
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(lots, key = { it.lotId }) { LotCard(it) }
                }
            }
        }
    }
}

@Composable
private fun LotCard(lot: Lot) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = lot.photos.firstOrNull()?.let { File(it.filePath) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LightSurfaceVariant)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(lot.category.icon, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${stringResource(lot.category.labelRes)} · ${formatKg(lot.weightKg)} kg",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(lot.estimate.format(), color = OchreSecondary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                lot.priceRegion?.let { region ->
                    val zone = Zone.entries.firstOrNull { it.name == region }
                    Text(
                        "📍 ${if (zone != null) stringResource(zone.labelRes) else region}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Text(
                    "${lot.lotId} · ${SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(lot.createdAt))}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(stringResource(R.string.status_listed), GreenPrimary)
                if (lot.syncStatus == "PENDING") {
                    Spacer(Modifier.height(4.dp))
                    StatusChip(stringResource(R.string.sync_pending), OchreSecondary)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.2f)) {
        Text(
            text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun formatKg(kg: Double) = if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString()

// ── New lot: photo → category → weight → price range → save ──────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewLotScreen(
    onDone: () -> Unit,
    viewModel: NewLotViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showCamera by remember { mutableStateOf(false) }
    var showCityPicker by remember { mutableStateOf(false) }

    if (showCityPicker) {
        CityPickerDialog(
            cities = viewModel.cities,
            selected = state.city,
            onDetect = viewModel::detectCity,
            onPick = { viewModel.setCity(it); showCityPicker = false },
            onDismiss = { showCityPicker = false }
        )
    }

    if (showCamera) {
        BackHandler { showCamera = false }
        CameraCapture(
            newPhotoFile = viewModel::newPhotoFile,
            onPhotoCaptured = { file ->
                viewModel.onPhotoCaptured(file)
                showCamera = false
            },
            onClose = { showCamera = false }
        )
        return
    }

    state.savedLotId?.let { lotId ->
        LotSavedView(lotId = lotId, estimate = state.estimate, onDone = onDone)
        return
    }

    state.rejectedPhoto?.let { rejected ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRejection,
            icon = { Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = OchreSecondary) },
            title = { Text(stringResource(R.string.not_ewaste_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.not_ewaste_message))
                    rejected.detectedLabel?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.detected_in_photo, it), color = TextSecondary)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissRejection()
                        showCamera = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) { Text(stringResource(R.string.retake_photo)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRejection) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        containerColor = LightBackground,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_lot), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        },
        bottomBar = {
            Surface(color = LightSurface, tonalElevation = 8.dp) {
                Button(
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    if (state.isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    else Text(stringResource(R.string.create_lot), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(state.lotId, color = TextSecondary, style = MaterialTheme.typography.bodySmall)

            // 1 ── Photos (in-app camera only)
            StepHeader(1, stringResource(R.string.step_photo))
            Text(stringResource(R.string.step_photo_hint), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.photos.forEach { photo ->
                    Box(Modifier.size(104.dp)) {
                        AsyncImage(
                            model = File(photo.filePath),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                        )
                        Icon(
                            Icons.Filled.Verified,
                            contentDescription = stringResource(R.string.photo_verified),
                            tint = GreenPrimary,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .size(20.dp)
                                .background(Color.White, CircleShape)
                        )
                        IconButton(
                            onClick = { viewModel.removePhoto(photo) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(28.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                if (state.isSealing) {
                    Column(
                        modifier = Modifier.size(104.dp).background(LightSurfaceVariant, RoundedCornerShape(12.dp)),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = GreenPrimary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.checking_photo), color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                } else if (state.canAddPhoto) {
                    Column(
                        modifier = Modifier
                            .size(104.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, GreenPrimary, RoundedCornerShape(12.dp))
                            .clickable { showCamera = true },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(36.dp))
                        Text(stringResource(R.string.take_photo), color = GreenPrimary, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }

            // 2 ── Category
            StepHeader(2, stringResource(R.string.step_category))
            MaterialCategory.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { category ->
                        CategoryTile(
                            category = category,
                            selected = state.category == category,
                            onClick = { viewModel.selectCategory(category) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // 3 ── Weight
            StepHeader(3, stringResource(R.string.step_weight))
            OutlinedTextField(
                value = state.weightText,
                onValueChange = viewModel::setWeightText,
                suffix = { Text("kg") },
                placeholder = { Text("0") },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 10, 50).forEach { kg ->
                    AssistChip(onClick = { viewModel.addWeight(kg) }, label = { Text("+$kg kg") })
                }
            }

            // Area whose scrap rates are used
            AreaRow(city = state.city, onChange = { showCityPicker = true })

            // 4 ── Price range
            state.estimate?.let { estimate ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = OchreSecondary.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(stringResource(R.string.estimated_price), color = OchreSecondary, fontWeight = FontWeight.SemiBold)
                        Text(
                            estimate.format(),
                            color = TextPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        val ml = state.mlPrediction
                        if (ml != null && state.city != null) {
                            Text(
                                "🤖 " + stringResource(R.string.ml_price_basis, state.city!!.name, ml.trainingRows),
                                color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium
                            )
                        } else {
                            state.rate?.let { RateBasis(it, state.city) }
                        }
                        Text(stringResource(R.string.price_range_note), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            state.error?.let { Text(it, color = ErrorColor) }
        }
    }
}

@Composable
private fun AreaRow(city: City?, onChange: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (city == null) OchreSecondary.copy(alpha = 0.12f) else LightSurfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onChange)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Place, contentDescription = null, tint = if (city == null) OchreSecondary else GreenPrimary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.price_area), color = TextSecondary, fontSize = 12.sp)
                Text(
                    city?.let { "${it.name} · ${stringResource(it.zone.labelRes)}" } ?: stringResource(R.string.choose_area),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TextButton(onClick = onChange) { Text(stringResource(R.string.change)) }
        }
    }
}

@Composable
private fun RateBasis(rate: ResolvedRate, city: City?) {
    val text = when (rate.level) {
        ResolvedRate.Level.CITY     -> stringResource(R.string.rate_basis_city, rate.regionName)
        ResolvedRate.Level.ZONE     -> stringResource(R.string.rate_basis_zone, stringResource(city!!.zone.labelRes))
        ResolvedRate.Level.NATIONAL -> stringResource(R.string.rate_basis_national)
    }
    Text("📍 $text", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun CityPickerDialog(
    cities: List<City>,
    selected: City?,
    onDetect: () -> Boolean,
    onPick: (City) -> Unit,
    onDismiss: () -> Unit
) {
    var locationFailed by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        locationFailed = !(granted && onDetect())
        if (!locationFailed) onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_area)) },
        text = {
            Column {
                OutlinedButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.use_my_location))
                }
                if (locationFailed) {
                    Text(stringResource(R.string.location_unavailable), color = ErrorColor, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    cities.groupBy { it.zone }.forEach { (zone, zoneCities) ->
                        item(key = zone.name) {
                            Text(
                                stringResource(zone.labelRes),
                                color = GreenPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(zoneCities, key = { it.name }) { city ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPick(city) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${city.name}, ${city.state}", color = TextPrimary, modifier = Modifier.weight(1f))
                                if (city == selected) Icon(Icons.Filled.Check, contentDescription = null, tint = GreenPrimary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun StepHeader(number: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).background(GreenPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text("$number", color = Color.White, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp))
        Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun CategoryTile(
    category: MaterialCategory,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) GreenPrimary else LightSurfaceVariant
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) GreenPrimary.copy(alpha = 0.2f) else LightSurface)
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            category.icon,
            contentDescription = null,
            tint = if (selected) GreenContainer else TextPrimary,
            modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(category.labelRes),
            color = TextPrimary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun LotSavedView(lotId: String, estimate: PriceEstimate?, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBackground)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(88.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.lot_created), color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("${stringResource(R.string.lot_id)}: $lotId", color = TextPrimary, fontSize = 18.sp)
        estimate?.let { Text(it.format(), color = OchreSecondary, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.saved_offline_note), color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
        ) { Text(stringResource(R.string.done), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}
