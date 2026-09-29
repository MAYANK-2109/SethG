package com.sethg.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethg.app.data.local.db.UserDao
import com.sethg.app.data.remote.SethGApiService
import com.sethg.app.data.remote.model.PoolMessageItem
import com.sethg.app.data.remote.model.PostMessageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PoolChatUiState(
    val poolId: String = "",
    val simpleId: String = "",
    val category: String = "",
    val status: String = "OPEN",
    val isAdmin: Boolean = false,
    val adminName: String = "",
    val totalWeight: Double = 0.0,
    val lotCount: Int = 0,
    val messages: List<PoolMessageItem> = emptyList(),
    val isLoading: Boolean = true,
    val isPosting: Boolean = false,
    val isSending: Boolean = false,
    val postSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class PoolChatViewModel @Inject constructor(
    private val api: SethGApiService,
    private val userDao: UserDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(PoolChatUiState())
    val uiState: StateFlow<PoolChatUiState> = _uiState.asStateFlow()

    private var activePoolId: String? = null
    private var isPolling = false
    var currentUserId: String = ""
        private set

    init {
        viewModelScope.launch {
            currentUserId = userDao.getUser()?.id ?: ""
        }
    }

    fun initPool(poolId: String, simpleId: String, category: String, status: String, isAdmin: Boolean) {
        activePoolId = poolId
        _uiState.update {
            it.copy(
                poolId = poolId,
                simpleId = simpleId.ifBlank { "SG-P" + poolId.replace("-", "").take(4).uppercase() },
                category = category,
                status = status,
                isAdmin = isAdmin
            )
        }
        startChat(poolId)
    }

    private fun startChat(poolId: String) {
        isPolling = true

        viewModelScope.launch {
            currentUserId = userDao.getUser()?.id ?: ""
            refreshPoolDetails(poolId)
            fetchMessages(poolId)

            // Fast periodic sync every 2.5 seconds
            while (isPolling && activePoolId == poolId) {
                delay(2_500)
                fetchMessages(poolId)
            }
        }
    }

    private suspend fun refreshPoolDetails(poolId: String) {
        try {
            val res = api.getMyPools()
            if (res.isSuccessful) {
                val pool = res.body()?.pools?.find { it.id == poolId }
                if (pool != null) {
                    _uiState.update {
                        it.copy(
                            simpleId = pool.simpleId,
                            category = pool.category,
                            status = pool.status,
                            isAdmin = pool.isAdmin,
                            adminName = pool.adminName,
                            totalWeight = pool.totalWeight,
                            lotCount = pool.lotCount
                        )
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun fetchMessages(poolId: String) {
        try {
            val res = api.getPoolMessages(poolId)
            if (res.isSuccessful) {
                val list = res.body() ?: emptyList()
                _uiState.update { it.copy(messages = list, isLoading = false) }
            }
        } catch (_: Exception) {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun sendMessage(content: String) {
        val poolId = activePoolId ?: return
        if (content.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            try {
                val res = api.postPoolMessage(poolId, PostMessageRequest(content = content.trim()))
                if (res.isSuccessful && res.body() != null) {
                    val msg = res.body()!!
                    _uiState.update { state ->
                        if (state.messages.none { it.id == msg.id }) {
                            state.copy(messages = state.messages + msg)
                        } else state
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to send: ${e.localizedMessage}") }
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    fun postPool() {
        val poolId = activePoolId ?: return
        if (!_uiState.value.isAdmin) {
            _uiState.update { it.copy(errorMessage = "Right to post a pool is with the pool admin only.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPosting = true, errorMessage = null) }
            try {
                val res = api.postPool(poolId)
                if (res.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            status = "POSTED",
                            postSuccess = true,
                            isPosting = false
                        )
                    }
                    // Refresh messages so system announcement shows immediately
                    fetchMessages(poolId)
                } else {
                    val errorMsg = res.errorBody()?.string() ?: "Failed to post pool"
                    _uiState.update { it.copy(isPosting = false, errorMessage = errorMsg) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isPosting = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, postSuccess = false) }
    }

    fun stopChat() {
        isPolling = false
        activePoolId = null
    }

    override fun onCleared() {
        stopChat()
        super.onCleared()
    }
}
