package com.sethg.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethg.app.data.repository.EarningsRepository
import com.sethg.app.domain.model.EarningsSummary
import com.sethg.app.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val earningsRepo: EarningsRepository
) : ViewModel() {

    data class UiState(
        val today   : EarningsSummary? = null,
        val weekly  : EarningsSummary? = null,
        val monthly : EarningsSummary? = null,
        val isLoading: Boolean = false,
        val isOffline: Boolean = false,
        val error   : String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Observe cached values immediately so UI shows data even before network call
        viewModelScope.launch {
            combine(
                earningsRepo.observeEarnings("today"),
                earningsRepo.observeEarnings("weekly"),
                earningsRepo.observeEarnings("monthly")
            ) { today, weekly, monthly ->
                Triple(today, weekly, monthly)
            }.collect { (today, weekly, monthly) ->
                _uiState.update { state ->
                    state.copy(today = today, weekly = weekly, monthly = monthly)
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val todayDeferred   = async { earningsRepo.fetchToday() }
            val weeklyDeferred  = async { earningsRepo.fetchWeekly() }
            val monthlyDeferred = async { earningsRepo.fetchMonthly() }

            val todayResult   = todayDeferred.await()
            val weeklyResult  = weeklyDeferred.await()
            val monthlyResult = monthlyDeferred.await()

            val hasError = listOf(todayResult, weeklyResult, monthlyResult)
                .any { it is Result.Error }
            val isOffline = hasError && _uiState.value.today != null

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    isOffline = isOffline,
                    error     = if (hasError && !isOffline) "Could not load earnings" else null,
                    today     = (todayResult   as? Result.Success)?.data ?: state.today,
                    weekly    = (weeklyResult  as? Result.Success)?.data ?: state.weekly,
                    monthly   = (monthlyResult as? Result.Success)?.data ?: state.monthly
                )
            }
        }
    }
}
