package com.sethg.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethg.app.data.repository.AuthRepository
import com.sethg.app.domain.model.Result
import com.sethg.app.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.sethg.app.data.local.AppPreferences
import javax.inject.Inject
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val prefs: AppPreferences
) : ViewModel() {

    sealed class UiEvent {
        object NavigateToDashboard : UiEvent()
        object NavigateToLogin : UiEvent()
        data class ShowError(val message: String) : UiEvent()
    }

    data class UiState(
        val isLoading: Boolean = false,
        val isLoggedIn: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState(isLoggedIn = authRepository.isLoggedIn))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    // Login fields
    var loginIdentifier by mutableStateOf("")    // phone or email
    var loginPassword   by mutableStateOf("")

    // Register fields
    var regName             by mutableStateOf("")
    var regPhone            by mutableStateOf("")
    var regEmail            by mutableStateOf("")
    var regPassword         by mutableStateOf("")
    var regConfirmPassword  by mutableStateOf("")
    var regLanguage         by mutableStateOf("en")
    var regRole             by mutableStateOf("recycler")   // must match a role the server accepts (recycler | vendor)
    var regCertificateUrl   by mutableStateOf("")

    init {
        viewModelScope.launch {
            prefs.languageFlow.collect { lang ->
                regLanguage = lang
            }
        }
    }

    fun login() {
        if (loginIdentifier.isBlank() || loginPassword.isBlank()) {
            emitError("Please fill in all fields")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // "+91 91317 11386", "91317-11386" … are phone numbers too; the server normalizes the format
            val id = loginIdentifier.trim()
            val phone = if ('@' !in id && id.all { c -> c.isDigit() || c in "+- " }) id else null
            val email = if (phone == null) id else null
            val result = authRepository.login(phone, email, loginPassword)
            _uiState.update { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> _events.emit(UiEvent.NavigateToDashboard)
                is Result.Error   -> _events.emit(UiEvent.ShowError(result.message))
                else -> Unit
            }
        }
    }

    fun register() {
        if (regName.isBlank()) { emitError("Name is required"); return }
        if (regPhone.isBlank() && regEmail.isBlank()) { emitError("Phone or email required"); return }
        if (regPassword.length < 6) { emitError("Password must be at least 6 characters"); return }
        if (regPassword != regConfirmPassword) { emitError("Passwords do not match"); return }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = authRepository.register(
                regName.trim(),
                regPhone.trim().ifBlank { null },
                regEmail.trim().ifBlank { null },
                regPassword,
                regLanguage,
                regRole,
                regCertificateUrl.trim().ifBlank { null }
            )
            _uiState.update { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> _events.emit(UiEvent.NavigateToDashboard)
                is Result.Error   -> _events.emit(UiEvent.ShowError(result.message))
                else -> Unit
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _events.emit(UiEvent.NavigateToLogin)
        }
    }

    private fun emitError(msg: String) {
        viewModelScope.launch { _events.emit(UiEvent.ShowError(msg)) }
    }
}
