package com.sethg.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethg.app.data.remote.SethGApiService
import com.sethg.app.data.remote.model.ChatConversation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatInboxState(
    val conversations: List<ChatConversation> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ChatInboxViewModel @Inject constructor(
    private val api: SethGApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatInboxState())
    val uiState: StateFlow<ChatInboxState> = _uiState.asStateFlow()

    private var isPolling = false

    init {
        startPolling()
    }

    fun startPolling() {
        if (isPolling) return
        isPolling = true
        viewModelScope.launch {
            while (isPolling) {
                try {
                    val response = api.getMyChats()
                    if (response.isSuccessful) {
                        val sorted = (response.body()?.chats ?: emptyList())
                            .sortedByDescending { it.lastMessageAt }
                        _uiState.update { it.copy(conversations = sorted, isLoading = false, error = null) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = "Could not load chats") }
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isLoading = false) }
                }
                delay(5_000)
            }
        }
    }

    override fun onCleared() {
        isPolling = false
        super.onCleared()
    }
}
