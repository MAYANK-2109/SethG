package com.sethg.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethg.app.data.local.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val prefs: AppPreferences
) : ViewModel() {

    data class UiState(
        val selectedLanguage: String = "en",
        val isLanguageAlreadySelected: Boolean = false,
        val isLoading: Boolean = true
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                prefs.languageFlow,
                prefs.languageSelectedFlow
            ) { lang, selected ->
                UiState(
                    selectedLanguage          = lang,
                    isLanguageAlreadySelected = selected,
                    isLoading                 = false
                )
            }.collect { _uiState.value = it }
        }
    }

    fun selectLanguage(code: String) {
        _uiState.update { it.copy(selectedLanguage = code) }
    }

    fun confirmLanguage() {
        viewModelScope.launch {
            prefs.setLanguage(_uiState.value.selectedLanguage)
        }
    }
}
