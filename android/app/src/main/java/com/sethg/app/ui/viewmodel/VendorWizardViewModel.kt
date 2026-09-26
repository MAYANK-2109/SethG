package com.sethg.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethg.app.data.local.LastLocation
import com.sethg.app.data.local.PricePredictor
import com.sethg.app.data.local.PriceRateRepository
import com.sethg.app.data.local.db.VendorTransactionDao
import com.sethg.app.data.local.db.VendorTransactionEntity
import com.sethg.app.domain.model.MaterialCategory
import com.sethg.app.domain.model.PriceEstimate
import com.sethg.app.domain.model.PriceEstimator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class EWasteGrade(val label: String, val multiplier: Double) {
    NEW_LIKE("New-like / जैसा नया", 1.0),
    GOOD("Good / अच्छा", 0.75),
    BAD("Bad / खराब", 0.45),
    VERY_BAD("Very Bad / बहुत खराब", 0.20)
}

/** Steps in the vendor customer-addition wizard (must be completed in order). */
enum class VendorWizardStep {
    PHOTO,           // Step 1: take photo of e-waste
    WEIGHT,          // Step 2: enter weight + quantity
    GRADE,           // Step 3: quality/grade selection
    RUNNABLE,        // Step 4a: is it a runnable device?
    WORKING,         // Step 4b: (only if runnable) still working?
    PRICE_ESTIMATE,  // Step 5: show ML price estimate
    RECORD,          // Step 6: confirm / record transaction
    DONE             // Final: payment marked, location captured
}

@HiltViewModel
class VendorWizardViewModel @Inject constructor(
    private val dao: VendorTransactionDao,
    private val rates: PriceRateRepository,
    private val pricePredictor: PricePredictor,
    private val lastLocation: LastLocation
) : ViewModel() {

    data class UiState(
        val txnId: String = newId(),
        // Step 1
        val photoPath: String? = null,
        // Step 2
        val category: MaterialCategory? = null,
        val weightText: String = "",
        val quantity: Int = 1,
        // Step 3
        val grade: EWasteGrade? = null,
        // Step 4
        val isRunnable: Boolean? = null,
        val isWorking: Boolean? = null,
        // Step 5
        val estimate: PriceEstimate? = null,
        // Step 6
        val customerName: String = "",
        val finalPrice: String = "",
        // State
        val currentStep: VendorWizardStep = VendorWizardStep.PHOTO,
        val isSaving: Boolean = false,
        val savedTxnId: String? = null,
        val error: String? = null
    ) {
        val weightKg: Double? get() = weightText.toDoubleOrNull()?.takeIf { it > 0 }
        val canProceedPhoto   get() = photoPath != null
        val canProceedWeight  get() = category != null && weightKg != null && quantity >= 1
        val canProceedGrade   get() = grade != null
        val canProceedRunnable get() = isRunnable != null
        val canProceedWorking get() = isWorking != null
        val canRecord         get() = customerName.isNotBlank() && (finalPrice.toDoubleOrNull() ?: 0.0) > 0
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val transactions = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalRevenue = dao.totalRevenue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    // ── Navigation ────────────────────────────────────────────────────────────

    fun nextStep() {
        val state = _uiState.value
        val next = when (state.currentStep) {
            VendorWizardStep.PHOTO    -> if (state.canProceedPhoto) VendorWizardStep.WEIGHT else return
            VendorWizardStep.WEIGHT   -> if (state.canProceedWeight) VendorWizardStep.GRADE else return
            VendorWizardStep.GRADE    -> if (state.canProceedGrade) VendorWizardStep.RUNNABLE else return
            VendorWizardStep.RUNNABLE -> {
                if (state.canProceedRunnable) {
                    // Compute estimate when we first know category + grade + runnable
                    recomputeEstimate()
                    if (state.isRunnable == true) VendorWizardStep.WORKING else VendorWizardStep.PRICE_ESTIMATE
                } else return
            }
            VendorWizardStep.WORKING  -> if (state.canProceedWorking) {
                recomputeEstimate()
                VendorWizardStep.PRICE_ESTIMATE
            } else return
            VendorWizardStep.PRICE_ESTIMATE -> VendorWizardStep.RECORD
            VendorWizardStep.RECORD   -> return // saved via save()
            VendorWizardStep.DONE     -> return
        }
        _uiState.update { it.copy(currentStep = next, error = null) }
    }

    fun prevStep() {
        val state = _uiState.value
        val prev = when (state.currentStep) {
            VendorWizardStep.WEIGHT          -> VendorWizardStep.PHOTO
            VendorWizardStep.GRADE           -> VendorWizardStep.WEIGHT
            VendorWizardStep.RUNNABLE        -> VendorWizardStep.GRADE
            VendorWizardStep.WORKING         -> VendorWizardStep.RUNNABLE
            VendorWizardStep.PRICE_ESTIMATE  -> if (state.isRunnable == true) VendorWizardStep.WORKING else VendorWizardStep.RUNNABLE
            VendorWizardStep.RECORD          -> VendorWizardStep.PRICE_ESTIMATE
            else -> return
        }
        _uiState.update { it.copy(currentStep = prev, error = null) }
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    fun setPhoto(path: String) = _uiState.update { it.copy(photoPath = path) }

    fun setCategory(cat: MaterialCategory) {
        _uiState.update { it.copy(category = cat) }
        recomputeEstimate()
    }

    fun setWeightText(text: String) {
        if (text.isEmpty() || Regex("""^\d{0,5}(\.\d{0,2})?$""").matches(text)) {
            _uiState.update { it.copy(weightText = text) }
            recomputeEstimate()
        }
    }

    fun incrementQuantity() = _uiState.update { it.copy(quantity = it.quantity + 1) }
    fun decrementQuantity() = _uiState.update { if (it.quantity > 1) it.copy(quantity = it.quantity - 1) else it }

    fun setGrade(grade: EWasteGrade) {
        _uiState.update { it.copy(grade = grade) }
        recomputeEstimate()
    }

    fun setRunnable(runnable: Boolean) {
        _uiState.update { it.copy(isRunnable = runnable, isWorking = if (!runnable) false else it.isWorking) }
    }

    fun setWorking(working: Boolean) = _uiState.update { it.copy(isWorking = working) }

    fun setCustomerName(name: String) = _uiState.update { it.copy(customerName = name) }
    fun setFinalPrice(price: String)  = _uiState.update { it.copy(finalPrice = price) }

    // ── Price Estimate ────────────────────────────────────────────────────────

    private fun recomputeEstimate() {
        val state = _uiState.value
        val category = state.category ?: return
        val kg = state.weightKg ?: return

        val location = lastLocation.get()
        val city = location?.let { rates.nearestCity(it.latitude, it.longitude) }

        // Grade multiplier — better grade → closer to high end of range
        val gradeMulti = state.grade?.multiplier ?: 0.75

        // Working device adds premium (25%)
        val workingBonus = when {
            state.isRunnable == true && state.isWorking == true -> 1.25
            else -> 1.0
        }

        val ml = city?.let { pricePredictor.predict(category, it.zone, it.lat, it.lon) }
        val rate = rates.rateFor(category, city)

        val baseEstimate = when {
            ml != null -> PriceEstimator.estimate(
                ml.lowPerKg * gradeMulti * workingBonus,
                ml.highPerKg * gradeMulti * workingBonus,
                kg
            )
            rate != null -> PriceEstimator.estimate(
                rate.range.low * gradeMulti * workingBonus,
                rate.range.high * gradeMulti * workingBonus,
                kg
            )
            else -> null
        }

        _uiState.update { s ->
            val mid = baseEstimate?.let { ((it.low + it.high) / 2.0) }?.toString() ?: ""
            s.copy(
                estimate   = baseEstimate,
                finalPrice = if (s.finalPrice.isBlank() && mid.isNotBlank()) mid else s.finalPrice
            )
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    fun save() {
        val state = _uiState.value
        if (!state.canRecord) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val location = lastLocation.get()
                val txn = VendorTransactionEntity(
                    txnId        = state.txnId,
                    customerName = state.customerName,
                    photoPath    = state.photoPath,
                    category     = state.category?.name ?: "OTHER",
                    weightKg     = state.weightKg ?: 0.0,
                    quantity     = state.quantity,
                    grade        = state.grade?.name ?: "GOOD",
                    isRunnable   = state.isRunnable ?: false,
                    isWorking    = state.isWorking ?: false,
                    estimateLow  = state.estimate?.low ?: 0,
                    estimateHigh = state.estimate?.high ?: 0,
                    finalPrice   = state.finalPrice.toDoubleOrNull() ?: 0.0,
                    vendorLat    = location?.latitude,
                    vendorLon    = location?.longitude,
                    isPaid       = true
                )
                dao.upsert(txn)
                _uiState.update { it.copy(isSaving = false, savedTxnId = state.txnId, currentStep = VendorWizardStep.DONE) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.localizedMessage ?: "Save error") }
            }
        }
    }

    /** Reset for the next customer */
    fun resetWizard() {
        _uiState.value = UiState()
    }

    companion object {
        fun newId(): String {
            val now = java.util.Date()
            val fmt = java.text.SimpleDateFormat("yyMMdd", java.util.Locale.getDefault()).format(now)
            val rand = (0..9999).random().toString(16).uppercase().padStart(4, '0')
            return "VTX-$fmt-$rand"
        }
    }
}
