package com.sethg.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethg.app.data.repository.UserRepository
import com.sethg.app.domain.model.Result
import com.sethg.app.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepo: UserRepository
) : ViewModel() {

    sealed class UiEvent {
        data class ShowMessage(val message: String) : UiEvent()
        object ProfileUpdated : UiEvent()
    }

    data class UiState(
        val user      : User? = null,
        val isLoading : Boolean = false,
        val isSaving  : Boolean = false,
        val error     : String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        // Observe local cache for instant display
        viewModelScope.launch {
            userRepo.observeUser().collect { user ->
                _uiState.update { it.copy(user = user) }
            }
        }
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = userRepo.getProfile()) {
                is Result.Success -> _uiState.update { it.copy(isLoading = false, user = result.data) }
                is Result.Error   -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                else -> Unit
            }
        }
    }

    fun updateProfile(
        name: String? = null,
        phone: String? = null,
        email: String? = null,
        language: String? = null,
        currentPassword: String? = null,
        newPassword: String? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = userRepo.updateProfile(name, phone, email, language, currentPassword, newPassword)
            _uiState.update { it.copy(isSaving = false) }
            when (result) {
                is Result.Success -> {
                    _events.emit(UiEvent.ProfileUpdated)
                    _events.emit(UiEvent.ShowMessage("Profile updated"))
                }
                is Result.Error -> _events.emit(UiEvent.ShowMessage(result.message))
                else -> Unit
            }
        }
    }
}
