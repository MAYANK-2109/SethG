package com.sethg.app.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.sethg.app.R
import com.sethg.app.data.remote.model.NearbyLot
import com.sethg.app.data.remote.model.RemoteOffer
import com.sethg.app.data.remote.model.RemoteTrip
import com.sethg.app.domain.model.MaterialCategory
import com.sethg.app.ui.theme.*
import com.sethg.app.ui.viewmodel.HandoverViewModel
import com.sethg.app.ui.viewmodel.LotDetailViewModel
import com.sethg.app.ui.viewmodel.RecyclerViewModel
import com.sethg.app.work.formatSlot
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private fun rupees(v: Number) = "₹%,d".format(v.toLong())

@Composable
private fun categoryLabel(name: String): String =
    MaterialCategory.entries.firstOrNull { it.name == name }?.let { stringResource(it.labelRes) } ?: name

@Composable
private fun statusLabel(status: String): String = when (status) {
    "LISTED"      -> stringResource(R.string.status_listed)
    "ACCEPTED"    -> stringResource(R.string.status_accepted)
    "SCHEDULED"   -> stringResource(R.string.status_scheduled)
    "HANDED_OVER" -> stringResource(R.string.status_handed_over)
    else          -> status
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            content()
        }
    }
}

// ═════════════════════════ Collector: lot detail ═════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotDetailScreen(onBack: () -> Unit, viewModel: LotDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val server = state.server
    val local = state.local
    val status = server?.status ?: local?.status ?: "LISTED"
    var confirmOffer by remember { mutableStateOf<RemoteOffer?>(null) }

    confirmOffer?.let { offer ->
        AlertDialog(
            onDismissRequest = { confirmOffer = null },
            title = { Text(stringResource(R.string.accept_offer_title)) },
            text = { Text(stringResource(R.string.accept_offer_body, offer.recyclerName, rupees(offer.offerTotal))) },
            confirmButton = {
                Button(onClick = { viewModel.accept(offer.id); confirmOffer = null },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)) {
                    Text(stringResource(R.string.accept))
                }
            },
            dismissButton = { TextButton(onClick = { confirmOffer = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Scaffold(
        containerColor = LightBackground,
        topBar = {
            TopAppBar(
                title = { Text(viewModel.lotId, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isLoading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = GreenPrimary)
            state.error?.let { Text(it, color = ErrorColor) }

            // Summary
            local?.let { lot ->
                Section(statusLabel(status)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = lot.photos.firstOrNull()?.let { File(it.filePath) }, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(LightSurfaceVariant)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("${stringResource(lot.category.labelRes)} · ${lot.weightKg} kg", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("${rupees(lot.estimate.low)} – ${rupees(lot.estimate.high)}", color = OchreSecondary, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(if (lot.syncStatus == "PENDING") R.string.sync_pending else R.string.sent_to_recyclers),
                                color = TextSecondary, fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Stage 2: offers
            if (status == "LISTED") {
                val offers = server?.offers?.filter { it.status == "PENDING" }.orEmpty()
                Section(stringResource(R.string.offers_title, offers.size)) {
                    if (offers.isEmpty()) Text(stringResource(R.string.no_offers_yet), color = TextSecondary)
                    offers.forEach { offer -> OfferRow(offer, enabled = !state.busy) { confirmOffer = offer } }
                }
            }

            // Stage 4 prep: the code the driver must enter
            if (status == "ACCEPTED" || status == "SCHEDULED") {
                local?.handoverOtp?.let { otp ->
                    Card(colors = CardDefaults.cardColors(containerColor = GreenPrimary), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.handover_code_title), color = Color.White)
                            Text(
                                otp.chunked(3).joinToString(" "), color = Color.White, fontSize = 40.sp,
                                fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace
                            )
                            Text(stringResource(R.string.handover_code_hint), color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
                server?.offers?.firstOrNull { it.status == "ACCEPTED" }?.let { offer ->
                    Section(stringResource(R.string.accepted_offer)) {
                        Text("${offer.recyclerName} · ₹${offer.ratePerKg.toInt()}/kg · ${rupees(offer.offerTotal)}", color = TextPrimary)
                        Text(stringResource(R.string.price_locked_note), color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }

            // Stage 3: how the material moves
            if (status == "ACCEPTED" && server?.transportMode == null) {
                val options = state.transportOptions
                Section(stringResource(R.string.transport_title)) {
                    TransportButton(
                        Icons.Filled.LocalShipping, stringResource(R.string.mode_pickup),
                        if (options?.pickupAllowed == false)
                            stringResource(R.string.mode_pickup_min, options.vehicleMinKg.toInt())
                        else stringResource(R.string.mode_pickup_desc),
                        enabled = options?.pickupAllowed != false && !state.busy
                    ) { viewModel.chooseTransport("PICKUP") }
                    TransportButton(Icons.Filled.Route, stringResource(R.string.mode_pooled), stringResource(R.string.mode_pooled_desc),
                        enabled = !state.busy) { viewModel.chooseTransport("POOLED") }
                    options?.hubs.orEmpty().forEach { hub ->
                        TransportButton(Icons.Filled.Store, stringResource(R.string.mode_hub, hub.name),
                            stringResource(R.string.km_away, hub.distanceKm), enabled = !state.busy) {
                            viewModel.chooseTransport("HUB", hub.id)
                        }
                    }
                }
            }
            if (status == "ACCEPTED" && server?.transportMode == "POOLED") {
                Section(stringResource(R.string.mode_pooled)) { Text(stringResource(R.string.pooled_waiting), color = TextSecondary) }
            }
            if (status == "SCHEDULED" && server != null) {
                Section(stringResource(R.string.pickup_scheduled)) {
                    Text(formatSlot(server.slotStart, server.slotEnd), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    server.hubName?.let { Text(stringResource(R.string.drop_at_hub, it), color = TextSecondary) }
                }
            }

            // Stage 4: verified handover
            server?.handover?.let { h ->
                Section(stringResource(R.string.handover_done)) {
                    Text(h.id, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary)
                    Text(stringResource(R.string.handover_summary, h.actualWeightKg, rupees(h.finalAmount)), color = TextPrimary)
                    if (h.weightFlagged) Text(stringResource(R.string.weight_flagged), color = OchreSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun OfferRow(offer: RemoteOffer, enabled: Boolean, onAccept: () -> Unit) {
    Surface(color = LightSurfaceVariant, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(offer.recyclerName, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("₹${offer.ratePerKg.toInt()}/kg → ${rupees(offer.offerTotal)}", color = OchreSecondary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(stringResource(R.string.offer_meta, offer.pickupDate.take(10), offer.distanceKm), color = TextSecondary, fontSize = 12.sp)
                offer.note?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
            }
            Button(onClick = onAccept, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)) {
                Text(stringResource(R.string.accept))
            }
        }
    }
}

@Composable
private fun TransportButton(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String,
                            enabled: Boolean, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (enabled) GreenPrimary else TextMuted, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, color = if (enabled) TextPrimary else TextMuted)
                Text(subtitle, fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

// ═════════════════════════ Recycler: market + trips ══════════════════════════

@Composable
fun RecyclerMarketSection(onHandover: (lotId: String, declaredKg: Double) -> Unit,
                          viewModel: RecyclerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var offerFor by remember { mutableStateOf<NearbyLot?>(null) }
    var showFacility by remember { mutableStateOf(false) }

    val locationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    LaunchedEffect(Unit) {
        locationPermission.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    offerFor?.let { lot -> OfferDialog(lot, onDismiss = { offerFor = null }) { rate, date, note ->
        viewModel.makeOffer(lot.id, rate, date, note); offerFor = null } }
    if (showFacility || state.needsFacility) FacilityDialog(
        onDismiss = { showFacility = false },
        onSave = { materials, minKg -> viewModel.saveFacilityHere(materials, minKg); showFacility = false }
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.message?.let { msg ->
            val text = when (msg) {
                "OFFER_SENT" -> stringResource(R.string.offer_sent)
                "LOCATION"   -> stringResource(R.string.location_unavailable)
                else         -> msg
            }
            Surface(color = GreenContainer, shape = RoundedCornerShape(12.dp), onClick = viewModel::clearMessage) {
                Text(text, Modifier.padding(12.dp).fillMaxWidth(), color = TextPrimary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TabRow(selectedTabIndex = tab, modifier = Modifier.weight(1f), containerColor = LightBackground) {
                Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.nearby_lots, state.nearby.size)) })
                Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.my_trips, state.trips.size)) })
            }
            IconButton(onClick = { showFacility = true }) { Icon(Icons.Filled.Factory, stringResource(R.string.facility)) }
            IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, null) }
        }
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = GreenPrimary)

        if (tab == 0) {
            if (state.nearby.isEmpty()) Text(stringResource(R.string.no_nearby_lots), color = TextSecondary)
            state.nearby.forEach { lot ->
                Card(colors = CardDefaults.cardColors(containerColor = LightSurface), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${categoryLabel(lot.category)} · ${lot.weightKg} kg", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("${rupees(lot.estimateLow)} – ${rupees(lot.estimateHigh)}", color = OchreSecondary, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.nearby_meta, lot.distanceKm, lot.collectorFirstName, lot.offerCount),
                                color = TextSecondary, fontSize = 12.sp)
                            lot.myRatePerKg?.let { Text(stringResource(R.string.your_offer, it.toInt()), color = GreenPrimary, fontSize = 12.sp) }
                        }
                        Button(onClick = { offerFor = lot }, colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)) {
                            Text(stringResource(if (lot.myRatePerKg == null) R.string.make_offer else R.string.revise_offer))
                        }
                    }
                }
            }
        } else {
            if (state.waiting.isNotEmpty()) {
                Section(stringResource(R.string.waiting_for_pool)) {
                    state.waiting.forEach { l ->
                        Text("${l.id} · ${categoryLabel(l.category)} ${l.weightKg} kg · ${l.collectorName} · ${l.transportMode ?: "—"}",
                            color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
            if (state.trips.isEmpty()) Text(stringResource(R.string.no_trips), color = TextSecondary)
            state.trips.forEach { trip -> TripCard(trip, onHandover) { lat, lon ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon")))
            } }
        }
    }
}

@Composable
private fun TripCard(trip: RemoteTrip, onHandover: (String, Double) -> Unit, onNavigate: (Double, Double) -> Unit) {
    Section("${trip.mode} · ${trip.scheduledDate.take(10)} · ${trip.totalKg} kg") {
        trip.hubName?.let { Text(stringResource(R.string.drop_at_hub, it), color = TextSecondary) }
        trip.stops.forEach { stop ->
            Surface(color = LightSurfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("${stop.stopSeq}. ${stop.collectorName} · ${formatSlot(stop.slotStart, stop.slotEnd)}",
                        fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("${categoryLabel(stop.category)} ${stop.weightKg} kg · ${statusLabel(stop.status)}" +
                        (stop.collectorPhone?.let { " · $it" } ?: ""), color = TextSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onNavigate(stop.lat, stop.lon) }) {
                            Icon(Icons.Filled.Directions, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.navigate))
                        }
                        if (stop.status != "HANDED_OVER") Button(
                            onClick = { onHandover(stop.lotId, stop.weightKg) },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                        ) { Text(stringResource(R.string.do_handover)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfferDialog(lot: NearbyLot, onDismiss: () -> Unit, onSend: (Double, String, String?) -> Unit) {
    val suggested = ((lot.estimateLow + lot.estimateHigh) / 2.0 / lot.weightKg).toInt().coerceAtLeast(1)
    var rate by remember { mutableStateOf(suggested.toString()) }
    var dayOffset by remember { mutableIntStateOf(1) }
    var note by remember { mutableStateOf("") }
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val labelFmt = SimpleDateFormat("EEE d MMM", Locale.getDefault())
    fun day(offset: Int) = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }.time

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${categoryLabel(lot.category)} · ${lot.weightKg} kg") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(rate, { if (it.length <= 6 && it.all(Char::isDigit)) rate = it },
                    label = { Text(stringResource(R.string.rate_per_kg)) }, prefix = { Text("₹") }, suffix = { Text("/kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                rate.toIntOrNull()?.let { Text(stringResource(R.string.offer_total_preview, rupees(it * lot.weightKg)), color = OchreSecondary) }
                Text(stringResource(R.string.pickup_day), color = TextSecondary)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..4).forEach { d -> FilterChip(dayOffset == d, { dayOffset = d }, label = { Text(labelFmt.format(day(d))) }) }
                }
                OutlinedTextField(note, { if (it.length <= 200) note = it }, label = { Text(stringResource(R.string.note_optional)) })
            }
        },
        confirmButton = {
            Button(onClick = { rate.toIntOrNull()?.let { onSend(it.toDouble(), fmt.format(day(dayOffset)), note.ifBlank { null }) } },
                enabled = (rate.toIntOrNull() ?: 0) > 0, colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)) {
                Text(stringResource(R.string.send_offer))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FacilityDialog(onDismiss: () -> Unit, onSave: (List<String>?, Double?) -> Unit) {
    var selected by remember { mutableStateOf(setOf<MaterialCategory>()) }
    var minKg by remember { mutableStateOf("100") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.facility)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.facility_hint), color = TextSecondary, fontSize = 13.sp)
                Text(stringResource(R.string.materials_accepted), fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MaterialCategory.entries.forEach { c ->
                        FilterChip(c in selected, { selected = if (c in selected) selected - c else selected + c },
                            label = { Text(stringResource(c.labelRes), fontSize = 12.sp) })
                    }
                }
                OutlinedTextField(minKg, { if (it.length <= 5 && it.all(Char::isDigit)) minKg = it },
                    label = { Text(stringResource(R.string.vehicle_min_kg)) }, suffix = { Text("kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selected.map { it.name }.ifEmpty { null }, minKg.toDoubleOrNull()) },
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)) {
                Icon(Icons.Filled.MyLocation, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.save_facility_here))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// ═════════════════════════ Recycler: handover ════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandoverScreen(onDone: () -> Unit, viewModel: HandoverViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var showCamera by remember { mutableStateOf(false) }

    if (showCamera) {
        BackHandler { showCamera = false }
        CameraCapture(newPhotoFile = viewModel::newPhotoFile,
            onPhotoCaptured = { viewModel.onPhoto(it); showCamera = false }, onClose = { showCamera = false })
        return
    }

    Scaffold(
        containerColor = LightBackground,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.do_handover), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val done = state.done
            if (done != null) {
                Section(stringResource(R.string.handover_done)) {
                    Text(done.id, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, color = GreenPrimary)
                    Text(stringResource(R.string.handover_summary, done.actualWeightKg, rupees(done.finalAmount)), color = TextPrimary)
                    if (done.weightFlagged) Text(stringResource(R.string.weight_flagged), color = OchreSecondary)
                    Button(onClick = onDone, colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)) { Text(stringResource(R.string.done)) }
                }
                return@Column
            }
            Text("${viewModel.lotId} · ${stringResource(R.string.declared_weight, viewModel.declaredKg)}", color = TextSecondary)

            Section("1. " + stringResource(R.string.measured_weight)) {
                OutlinedTextField(state.weightText, viewModel::setWeight, suffix = { Text("kg") }, singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                state.weightText.toDoubleOrNull()?.let { actual ->
                    if (kotlin.math.abs(actual - viewModel.declaredKg) / viewModel.declaredKg > 0.15)
                        Text(stringResource(R.string.weight_flagged), color = OchreSecondary, fontSize = 12.sp)
                }
            }
            Section("2. " + stringResource(R.string.handover_photos)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.photos.forEach { p ->
                        AsyncImage(File(p.path), null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)))
                    }
                    if (state.photos.size < 5) Box(
                        Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)).background(LightSurfaceVariant)
                            .clickable { showCamera = true }, contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.PhotoCamera, null, tint = GreenPrimary, modifier = Modifier.size(32.dp)) }
                }
                Text(stringResource(R.string.handover_photos_hint), color = TextSecondary, fontSize = 12.sp)
            }
            Section("3. " + stringResource(R.string.enter_code)) {
                OutlinedTextField(state.otp, viewModel::setOtp, singleLine = true,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 6.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.enter_code_hint), color = TextSecondary, fontSize = 12.sp)
            }
            Text("📍 " + stringResource(R.string.gps_auto), color = TextSecondary, fontSize = 12.sp)
            state.error?.let { Text(if (it == "LOCATION") stringResource(R.string.location_unavailable) else it, color = ErrorColor) }
            Button(onClick = viewModel::submit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)) {
                if (state.busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text(stringResource(R.string.confirm_handover), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
