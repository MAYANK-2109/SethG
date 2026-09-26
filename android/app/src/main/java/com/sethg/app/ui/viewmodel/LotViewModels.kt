package com.sethg.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethg.app.data.local.AppPreferences
import com.sethg.app.data.local.City
import com.sethg.app.data.local.LastLocation
import com.sethg.app.data.local.PriceRateRepository
import com.sethg.app.data.local.ResolvedRate
import com.sethg.app.data.repository.LotRepository
import com.sethg.app.domain.model.CapturedPhoto
import com.sethg.app.domain.model.Lot
import com.sethg.app.domain.model.MaterialCategory
import com.sethg.app.domain.model.PriceEstimate
import com.sethg.app.domain.model.PriceEstimator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LotsViewModel @Inject constructor(
    lotRepo: LotRepository
) : ViewModel() {
    val lots: StateFlow<List<Lot>> = lotRepo.observeLots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@HiltViewModel
class NewLotViewModel @Inject constructor(
    private val lotRepo: LotRepository,
    private val rates: PriceRateRepository,
    private val prefs: AppPreferences,
    private val lastLocation: LastLocation
) : ViewModel() {

    companion object {
        const val MAX_PHOTOS = 3
    }

    data class UiState(
        val lotId     : String,
        val photos    : List<CapturedPhoto> = emptyList(),
        val category  : MaterialCategory? = null,
        val weightText: String = "",
        val city      : City? = null,          // whose scrap rates price this lot
        val rate      : ResolvedRate? = null,  // rate actually used (city / zone / national)
        val estimate  : PriceEstimate? = null,
        val isSealing : Boolean = false,
        val isSaving  : Boolean = false,
        val savedLotId: String? = null,
        val rejectedPhoto: RejectedPhoto? = null,
        val error     : String? = null
    ) {
        val weightKg: Double? get() = weightText.toDoubleOrNull()?.takeIf { it > 0 }
        val canAddPhoto: Boolean get() = photos.size < MAX_PHOTOS && !isSealing
        val canSave: Boolean get() = photos.isNotEmpty() && estimate != null && !isSaving && !isSealing
    }

    /** Shown as a "not e-waste, take another photo" dialog. */
    data class RejectedPhoto(val detectedLabel: String?)

    private val _uiState = MutableStateFlow(UiState(lotId = lotRepo.newLotId()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Cities offered in the "choose your area" picker. */
    val cities: List<City> get() = rates.cities

    init {
        viewModelScope.launch {
            // Saved choice first; otherwise try the phone's last known location
            val saved = rates.cityByName(prefs.priceCityFlow.first())
            if (saved != null) setCity(saved, remember = false) else detectCity()
        }
    }

    /** Nearest listed city from the last known location. Returns false if none available. */
    fun detectCity(): Boolean {
        val location = lastLocation.get() ?: return false
        val city = rates.nearestCity(location.latitude, location.longitude) ?: return false
        setCity(city)
        return true
    }

    fun setCity(city: City, remember: Boolean = true) {
        _uiState.update { it.copy(city = city) }
        recomputeEstimate()
        if (remember) viewModelScope.launch { prefs.setPriceCity(city.name) }
    }

    private fun recomputeEstimate() {
        _uiState.update { state ->
            val rate = state.category?.let { rates.rateFor(it, state.city) }
            val kg = state.weightKg
            state.copy(
                rate = rate,
                estimate = if (rate != null && kg != null)
                    PriceEstimator.estimate(rate.range.low, rate.range.high, kg) else null
            )
        }
    }

    /** Only the in-app camera writes here — there is no gallery import path. */
    fun newPhotoFile(): File = lotRepo.newPhotoFile()

    fun onPhotoCaptured(file: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSealing = true, error = null) }
            try {
                when (val result = lotRepo.checkAndSealPhoto(_uiState.value.lotId, file)) {
                    is LotRepository.PhotoCheck.Accepted -> _uiState.update {
                        it.copy(photos = it.photos + result.photo, isSealing = false)
                    }
                    is LotRepository.PhotoCheck.NotEWaste -> _uiState.update {
                        it.copy(isSealing = false, rejectedPhoto = RejectedPhoto(result.detectedLabel))
                    }
                }
            } catch (e: Exception) {
                file.delete()
                _uiState.update { it.copy(isSealing = false, error = e.localizedMessage ?: "Photo error") }
            }
        }
    }

    fun dismissRejection() {
        _uiState.update { it.copy(rejectedPhoto = null) }
    }

    fun removePhoto(photo: CapturedPhoto) {
        _uiState.update { it.copy(photos = it.photos - photo) }
        viewModelScope.launch { lotRepo.deletePhotoFiles(listOf(photo)) }
    }

    fun selectCategory(category: MaterialCategory) {
        _uiState.update { it.copy(category = category) }
        recomputeEstimate()
    }

    fun setWeightText(text: String) {
        // Digits with at most one decimal point, max 5 digits before it
        if (text.isEmpty() || Regex("""^\d{0,5}(\.\d{0,2})?$""").matches(text)) {
            _uiState.update { it.copy(weightText = text) }
            recomputeEstimate()
        }
    }

    fun addWeight(deltaKg: Int) {
        val current = _uiState.value.weightText.toDoubleOrNull() ?: 0.0
        val next = (current + deltaKg).coerceIn(0.0, 99_999.0)
        val text = if (next % 1.0 == 0.0) next.toInt().toString() else "%.2f".format(next).trimEnd('0')
        _uiState.update { it.copy(weightText = text) }
        recomputeEstimate()
    }

    fun save() {
        val state = _uiState.value
        val category = state.category ?: return
        val weightKg = state.weightKg ?: return
        val estimate = state.estimate ?: return
        if (!state.canSave) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                lotRepo.createLot(state.lotId, category, weightKg, estimate, state.rate?.regionName, state.photos)
                _uiState.update { it.copy(isSaving = false, savedLotId = state.lotId) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.localizedMessage ?: "Save error") }
            }
        }
    }

    override fun onCleared() {
        // Abandoned draft → don't leave orphan photos on the device
        if (_uiState.value.savedLotId == null) {
            _uiState.value.photos.forEach { File(it.filePath).delete() }
        }
    }
}
