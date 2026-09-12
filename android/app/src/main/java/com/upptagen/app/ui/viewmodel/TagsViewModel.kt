package com.upptagen.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.upptagen.app.data.model.TagStat
import com.upptagen.app.data.repository.TimeTrackingRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TagsUiState(
    val tagStats: List<TagStat> = emptyList(),
    val sortByDuration: Boolean = true,
    val isLoading: Boolean = false
) {
    val sortedStats: List<TagStat>
        get() = if (sortByDuration) {
            tagStats.sortedByDescending { it.totalDurationSeconds }
        } else {
            tagStats.sortedBy { it.name.lowercase() }
        }
}

class TagsViewModel(
    private val repository: TimeTrackingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TagsUiState())
    val uiState: StateFlow<TagsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getTagStatsFlow().collect { stats ->
                _uiState.update { it.copy(tagStats = stats) }
            }
        }
    }

    fun toggleSortOrder() {
        _uiState.update { it.copy(sortByDuration = !it.sortByDuration) }
    }

    class Factory(private val repository: TimeTrackingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TagsViewModel(repository) as T
        }
    }
}
