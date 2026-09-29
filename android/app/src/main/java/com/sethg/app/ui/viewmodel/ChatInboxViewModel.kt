package com.sethg.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethg.app.data.remote.SethGApiService
import com.sethg.app.data.remote.model.ChatConversation
import com.sethg.app.data.remote.model.CreatePoolRequest
import com.sethg.app.data.remote.model.PoolItem
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
    val pools: List<PoolItem> = emptyList(),
    val selectedTab: Int = 0, // 0 = Direct Chats, 1 = Pool Chats
    val isLoading: Boolean = true,
    val isCreatingPool: Boolean = false,
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

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun startPolling() {
        if (isPolling) return
        isPolling = true
        viewModelScope.launch {
            while (isPolling) {
                refreshAll()
                delay(5_000)
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            // Fetch direct lot chats
            try {
                val chatRes = api.getMyChats()
                if (chatRes.isSuccessful) {
                    val sortedChats = (chatRes.body()?.chats ?: emptyList())
                        .sortedByDescending { it.lastMessageAt }
                    _uiState.update { it.copy(conversations = sortedChats, isLoading = false, error = null) }
                }
            } catch (e: Exception) {
                // Keep existing chats
            }

            // Fetch user's existing pools
            try {
                val poolRes = api.getMyPools()
                if (poolRes.isSuccessful) {
                    val pools = poolRes.body()?.pools ?: emptyList()
                    _uiState.update { it.copy(pools = pools, isLoading = false) }
                }
            } catch (e: Exception) {
                // Keep existing pools
            }
        }
    }

    fun createPool(category: String, onDone: (PoolItem) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingPool = true) }
            try {
                val res = api.createPool(CreatePoolRequest(category = category))
                if (res.isSuccessful && res.body() != null) {
                    val created = res.body()!!
                    refreshAll()
                    onDone(created)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to create pool: ${e.localizedMessage}") }
            } finally {
                _uiState.update { it.copy(isCreatingPool = false) }
            }
        }
    }

    override fun onCleared() {
        isPolling = false
        super.onCleared()
    }
}
