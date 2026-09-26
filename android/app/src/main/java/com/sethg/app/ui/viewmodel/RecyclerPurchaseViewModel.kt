package com.sethg.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethg.app.data.local.db.RecyclerPurchaseDao
import com.sethg.app.data.local.db.RecyclerPurchaseEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class RecyclerPurchaseViewModel @Inject constructor(
    private val dao: RecyclerPurchaseDao
) : ViewModel() {

    val purchases = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalSpent = dao.totalSpent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    data class NewPurchaseForm(
        val vendorName: String = "",
        val vendorPhone: String = "",
        val material: String = "",
        val weightText: String = "",
        val quantityText: String = "1",
        val grade: String = "GOOD",
        val amountText: String = "",
        val notes: String = "",
        val vendorLat: Double? = null,
        val vendorLon: Double? = null
    ) {
        val isValid get() = vendorName.isNotBlank() &&
                material.isNotBlank() &&
                (weightText.toDoubleOrNull() ?: 0.0) > 0 &&
                (amountText.toDoubleOrNull() ?: 0.0) > 0
    }

    private val _form = MutableStateFlow(NewPurchaseForm())
    val form: StateFlow<NewPurchaseForm> = _form.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _savedId = MutableSharedFlow<String>()
    val savedId: SharedFlow<String> = _savedId.asSharedFlow()

    fun update(block: NewPurchaseForm.() -> NewPurchaseForm) = _form.update { it.block() }

    fun save() {
        val f = _form.value
        if (!f.isValid) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val id = newId()
                dao.upsert(
                    RecyclerPurchaseEntity(
                        purchaseId  = id,
                        vendorName  = f.vendorName,
                        vendorPhone = f.vendorPhone.ifBlank { null },
                        vendorLat   = f.vendorLat,
                        vendorLon   = f.vendorLon,
                        material    = f.material,
                        weightKg    = f.weightText.toDoubleOrNull() ?: 0.0,
                        quantity    = f.quantityText.toIntOrNull() ?: 1,
                        grade       = f.grade,
                        amountPaid  = f.amountText.toDoubleOrNull() ?: 0.0,
                        notes       = f.notes.ifBlank { null }
                    )
                )
                _savedId.emit(id)
                _form.value = NewPurchaseForm()
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun delete(purchaseId: String) = viewModelScope.launch { dao.delete(purchaseId) }

    private fun newId(): String {
        val fmt = SimpleDateFormat("yyMMdd", Locale.getDefault()).format(Date())
        val rand = (0..9999).random().toString(16).uppercase().padStart(4, '0')
        return "RCP-$fmt-$rand"
    }
}
