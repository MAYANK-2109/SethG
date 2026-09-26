package com.sethg.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethg.app.data.local.CaptureSigner
import com.sethg.app.data.local.LastLocation
import com.sethg.app.data.remote.model.AcceptedLot
import com.sethg.app.data.remote.model.HandoverRequest
import com.sethg.app.data.remote.model.NearbyLot
import com.sethg.app.data.remote.model.RemoteHandover
import com.sethg.app.data.remote.model.RemoteLot
import com.sethg.app.data.remote.model.RemoteTrip
import com.sethg.app.data.remote.model.TransportOptions
import com.sethg.app.data.repository.LotRepository
import com.sethg.app.data.repository.RecyclerRepository
import com.sethg.app.domain.model.Lot
import com.sethg.app.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ── Collector: one lot → offers → accept → transport → schedule → handover ──

@HiltViewModel
class LotDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val lotRepo: LotRepository
) : ViewModel() {

    val lotId: String = checkNotNull(savedState["lotId"])

    data class UiState(
        val local: Lot? = null,                       // on this phone (photos, handover code)
        val server: RemoteLot? = null,                // offers, schedule, handover record
        val transportOptions: TransportOptions? = null,
        val isLoading: Boolean = false,
        val busy: Boolean = false,
        val error: String? = null,
        val codeCheck: LotRepository.CodeCheck? = null   // result of the last code entry
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { lotRepo.observeLot(lotId).collect { l -> _uiState.update { it.copy(local = l) } } }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            lotRepo.syncPending()
            when (val r = lotRepo.refreshFromServer()) {
                is Result.Success -> _uiState.update { it.copy(isLoading = false, server = r.data.firstOrNull { l -> l.id == lotId }) }
                is Result.Error   -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                Result.Loading    -> Unit
            }
        }
    }

    fun accept(offerId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            when (val r = lotRepo.acceptOffer(lotId, offerId)) {
                is Result.Success -> _uiState.update {
                    it.copy(busy = false, server = r.data.lot, transportOptions = r.data.transportOptions)
                }
                is Result.Error -> _uiState.update { it.copy(busy = false, error = r.message) }
                Result.Loading  -> Unit
            }
        }
    }

    /** Vendor types the code from the recycler's phone; checked on this phone, synced later. */
    fun confirmHandover(code: String) {
        viewModelScope.launch {
            val check = lotRepo.confirmHandover(lotId, code)
            _uiState.update { it.copy(codeCheck = check) }
            if (check == LotRepository.CodeCheck.OK) refresh()
        }
    }

    fun chooseTransport(mode: String, hubId: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            when (val r = lotRepo.chooseTransport(lotId, mode, hubId)) {
                is Result.Success -> _uiState.update { it.copy(busy = false, server = r.data) }
                is Result.Error   -> _uiState.update { it.copy(busy = false, error = r.message) }
                Result.Loading    -> Unit
            }
        }
    }
}

// ── Recycler: facility, nearby lots, offers, trips ──────────────────────────

@HiltViewModel
class RecyclerViewModel @Inject constructor(
    private val repo: RecyclerRepository,
    private val lastLocation: LastLocation
) : ViewModel() {

    data class UiState(
        val nearby: List<NearbyLot> = emptyList(),
        val waiting: List<AcceptedLot> = emptyList(),    // accepted, not yet on a trip
        val awaitingVendor: List<AcceptedLot> = emptyList(), // weighed; vendor still has to enter the code
        val trips: List<RemoteTrip> = emptyList(),
        val isLoading: Boolean = false,
        val message: String? = null,
        val needsFacility: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val nearby = repo.nearbyLots()
            val trips = repo.trips()                     // plans pooled / hub trips first
            val accepted = (repo.acceptedLots() as? Result.Success)?.data
            _uiState.update { s ->
                s.copy(
                    isLoading = false,
                    nearby = (nearby as? Result.Success)?.data?.lots ?: s.nearby,
                    trips = (trips as? Result.Success)?.data ?: s.trips,
                    waiting = accepted?.filter { it.status == "ACCEPTED" } ?: s.waiting,
                    awaitingVendor = accepted?.filter { it.status == "WEIGHED" } ?: s.awaitingVendor,
                    needsFacility = (nearby as? Result.Error)?.code == 409,
                    message = (nearby as? Result.Error)?.message?.takeIf { (nearby as Result.Error).code != 409 }
                )
            }
        }
    }

    /** Facility = where the recycler is right now; materials null = accepts everything. */
    fun saveFacilityHere(materials: List<String>?, vehicleMinKg: Double?) {
        viewModelScope.launch {
            val here = lastLocation.fresh()
            if (here == null) {
                _uiState.update { it.copy(message = "LOCATION") }
                return@launch
            }
            when (val r = repo.saveFacility(here.latitude, here.longitude, materials, vehicleMinKg)) {
                is Result.Success -> refresh()
                is Result.Error   -> _uiState.update { it.copy(message = r.message) }
                Result.Loading    -> Unit
            }
        }
    }

    fun makeOffer(lotId: String, ratePerKg: Double, pickupDate: String, note: String?) {
        viewModelScope.launch {
            when (val r = repo.makeOffer(lotId, ratePerKg, pickupDate, note)) {
                is Result.Success -> { _uiState.update { it.copy(message = "OFFER_SENT") }; refresh() }
                is Result.Error   -> _uiState.update { it.copy(message = r.message) }
                Result.Loading    -> Unit
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
}

// ── Recycler: verified handover at the collector's door ─────────────────────

@HiltViewModel
class HandoverViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: RecyclerRepository,
    private val lotRepo: LotRepository,
    private val signer: CaptureSigner,
    private val lastLocation: LastLocation
) : ViewModel() {

    val lotId: String = checkNotNull(savedState["lotId"])
    val declaredKg: Double = checkNotNull(savedState.get<String>("declaredKg")).toDouble()

    data class Photo(val path: String, val sha256: String)

    data class UiState(
        val weightText: String = "",
        val photos: List<Photo> = emptyList(),
        val busy: Boolean = false,
        val done: RemoteHandover? = null,
        val code: String? = null,        // shown to the vendor, who types it in to confirm
        val error: String? = null
    ) {
        val canSubmit get() = weightText.toDoubleOrNull()?.let { it > 0 } == true && photos.isNotEmpty() && !busy
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun newPhotoFile(): File = lotRepo.newPhotoFile()

    /** Material + scale-reading photos: in-app camera only, hashed at capture. */
    fun onPhoto(file: File) {
        viewModelScope.launch {
            val proof = signer.seal(file, lotId)
            _uiState.update { it.copy(photos = it.photos + Photo(file.absolutePath, proof.sha256)) }
        }
    }

    fun setWeight(text: String) {
        if (text.isEmpty() || Regex("""^\d{0,5}(\.\d{0,2})?$""").matches(text)) _uiState.update { it.copy(weightText = text) }
    }

    fun submit() {
        val s = _uiState.value
        if (!s.canSubmit) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            val here = lastLocation.fresh()
            if (here == null) {
                _uiState.update { it.copy(busy = false, error = "LOCATION") }
                return@launch
            }
            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
            val request = HandoverRequest(
                actualWeightKg = s.weightText.toDouble(), lat = here.latitude, lon = here.longitude,
                capturedAt = now, photoHashes = s.photos.map { it.sha256 }
            )
            when (val r = repo.handover(lotId, request)) {
                is Result.Success -> _uiState.update { it.copy(busy = false, done = r.data.handover, code = r.data.handoverOtp) }
                is Result.Error   -> _uiState.update { it.copy(busy = false, error = r.message) }
                Result.Loading    -> Unit
            }
        }
    }
}
