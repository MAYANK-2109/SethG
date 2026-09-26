package com.sethg.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethg.app.data.repository.AuthRepository
import com.sethg.app.domain.model.Result
import com.sethg.app.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
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
    var loginIdentifier = ""    // phone or email
    var loginPassword   = ""

    // Register fields
    var regName             = ""
    var regPhone            = ""
    var regEmail            = ""
    var regPassword         = ""
    var regConfirmPassword  = ""
    var regLanguage         = "en"
    var regRole             = "user"

    fun login() {
        if (loginIdentifier.isBlank() || loginPassword.isBlank()) {
            emitError("Please fill in all fields")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val phone = if (loginIdentifier.startsWith("+") || loginIdentifier.all { c -> c.isDigit() || c == '+' || c == '-' })
                loginIdentifier else null
            val email = if (phone == null) loginIdentifier else null
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
                regPhone.ifBlank { null },
                regEmail.ifBlank { null },
                regPassword,
                regLanguage,
                regRole
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
