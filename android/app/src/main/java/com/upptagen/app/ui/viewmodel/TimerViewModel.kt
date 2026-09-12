package com.upptagen.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.upptagen.app.data.model.ActiveTimerEntity
import com.upptagen.app.data.repository.TimeTrackingRepository
import com.upptagen.app.service.TimerService
import com.upptagen.app.util.DateTimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

data class TimerUiState(
    val activeTimer: ActiveTimerEntity? = null,
    val elapsedSeconds: Long = 0L,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class TimerViewModel(
    private val repository: TimeTrackingRepository,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getActiveTimerFlow().collect { activeTimer ->
                _uiState.update { it.copy(activeTimer = activeTimer) }
            }
        }

        // Ticking loop for elapsed seconds
        viewModelScope.launch {
            while (isActive) {
                val timer = _uiState.value.activeTimer
                if (timer != null) {
                    val startInstant = DateTimeUtils.parseIsoOrFlexible(timer.startTime)
                    if (startInstant != null) {
                        val elapsed = (Instant.now().epochSecond - startInstant.epochSecond).coerceAtLeast(0)
                        _uiState.update { it.copy(elapsedSeconds = elapsed) }
                    }
                } else {
                    _uiState.update { it.copy(elapsedSeconds = 0L) }
                }
                delay(1000L)
            }
        }
    }

    fun startTimer(project: String, description: String, tags: List<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.startTimer(
                project = project.ifBlank { null },
                description = description.ifBlank { null },
                tags = tags
            )
            result.onSuccess {
                TimerService.startService(getApplication())
                _uiState.update { it.copy(isLoading = false, successMessage = "Timer started!") }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun stopTimer(overrideDescription: String? = null, additionalTags: List<String> = emptyList()) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.stopTimer(
                overrideDescription = overrideDescription?.ifBlank { null },
                additionalTags = additionalTags
            )
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, successMessage = "Timer stopped & entry recorded!") }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun addManualEntry(
        project: String?,
        description: String?,
        start: Instant,
        end: Instant,
        tags: List<String>
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.addManualEntry(
                project = project?.ifBlank { null },
                description = description?.ifBlank { null },
                startTime = start,
                endTime = end,
                tags = tags
            )
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, successMessage = "Entry added successfully!") }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    class Factory(
        private val repository: TimeTrackingRepository,
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TimerViewModel(repository, application) as T
        }
    }
}
