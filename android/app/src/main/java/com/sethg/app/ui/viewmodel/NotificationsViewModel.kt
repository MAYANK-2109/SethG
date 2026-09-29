package com.sethg.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethg.app.data.remote.SethGApiService
import com.sethg.app.data.remote.model.RemoteNotification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val notifications: List<RemoteNotification> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val api: SethGApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val res = api.getNotifications()
                if (res.isSuccessful && res.body() != null) {
                    val notifs = res.body()!!.notifications
                    _uiState.update {
                        it.copy(
                            notifications = notifs,
                            unreadCount = res.body()!!.unreadCount,
                            isLoading = false,
                            error = null
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch {
            try {
                api.markNotificationRead(id)
                _uiState.update { state ->
                    val updated = state.notifications.map {
                        if (it.id == id) it.copy(isRead = true) else it
                    }
                    val count = updated.count { !it.isRead }
                    state.copy(notifications = updated, unreadCount = count)
                }
            } catch (_: Exception) {}
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            try {
                api.markAllNotificationsRead()
                _uiState.update { state ->
                    val updated = state.notifications.map { it.copy(isRead = true) }
                    state.copy(notifications = updated, unreadCount = 0)
                }
            } catch (_: Exception) {}
        }
    }
}
