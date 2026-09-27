package com.sethg.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethg.app.data.local.db.UserDao
import com.sethg.app.data.remote.SethGApiService
import com.sethg.app.data.remote.model.PostMessageRequest
import com.sethg.app.ui.screen.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val api: SethGApiService,
    private val userDao: UserDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatState())
    val uiState: StateFlow<ChatState> = _uiState.asStateFlow()

    private var currentLotId: String? = null
    private var isPolling = false

    fun startChat(lotId: String) {
        currentLotId = lotId
        isPolling = true
        pollMessages()
    }

    fun stopChat() {
        isPolling = false
        currentLotId = null
    }

    private fun pollMessages() {
        viewModelScope.launch {
            val myUserId = userDao.getUser()?.id ?: ""
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            while (isPolling && currentLotId != null) {
                try {
                    val response = api.getMessages(currentLotId!!)
                    if (response.isSuccessful) {
                        val newMessages = response.body()?.map { msg ->
                            var timestamp = System.currentTimeMillis()
                            try {
                                val date = dateFormat.parse(msg.createdAt)
                                if (date != null) timestamp = date.time
                            } catch (e: Exception) {
                                // Ignore
                            }
                            ChatMessage(
                                id = msg.id,
                                text = msg.content,
                                isMine = msg.senderId == myUserId,
                                timestamp = timestamp
                            )
                        } ?: emptyList()
                        _uiState.update { it.copy(messages = newMessages, error = null) }
                    }
                } catch (e: Exception) {
                    // Ignore transient errors during polling
                }
                delay(2000)
            }
        }
    }

    fun sendMessage(content: String) {
        val lotId = currentLotId ?: return
        if (content.isBlank()) return
        
        viewModelScope.launch {
            try {
                val response = api.postMessage(lotId, PostMessageRequest(content))
                if (response.isSuccessful) {
                    // It will be picked up by the next poll
                } else {
                    _uiState.update { it.copy(error = "Failed to send message") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
