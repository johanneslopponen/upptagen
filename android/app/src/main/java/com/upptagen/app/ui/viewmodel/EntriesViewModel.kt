package com.upptagen.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.upptagen.app.data.model.TimeEntryWithTags
import com.upptagen.app.data.repository.TimeTrackingRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant

data class EntriesUiState(
    val entries: List<TimeEntryWithTags> = emptyList(),
    val projectFilter: String? = null,
    val tagFilter: String? = null,
    val searchQuery: String = "",
    val availableProjects: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null
) {
    val filteredEntries: List<TimeEntryWithTags>
        get() = entries.filter { item ->
            val matchesProject = projectFilter.isNullOrEmpty() ||
                    item.entry.project.equals(projectFilter, ignoreCase = true)

            val matchesTag = tagFilter.isNullOrEmpty() ||
                    item.tagNames.any { it.equals(tagFilter, ignoreCase = true) }

            val matchesSearch = searchQuery.isBlank() ||
                    (item.entry.project?.contains(searchQuery, ignoreCase = true) == true) ||
                    (item.entry.description?.contains(searchQuery, ignoreCase = true) == true) ||
                    item.tagNames.any { it.contains(searchQuery, ignoreCase = true) }

            matchesProject && matchesTag && matchesSearch
        }

    val totalDurationSeconds: Long
        get() = filteredEntries.sumOf { it.entry.durationSeconds }
}

class EntriesViewModel(
    private val repository: TimeTrackingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntriesUiState())
    val uiState: StateFlow<EntriesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllEntriesWithTagsFlow().collect { allEntries ->
                val projects = allEntries.mapNotNull { it.entry.project }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()

                val tags = allEntries.flatMap { it.tagNames }
                    .distinct()
                    .sorted()

                _uiState.update { current ->
                    current.copy(
                        entries = allEntries,
                        availableProjects = projects,
                        availableTags = tags
                    )
                }
            }
        }
    }

    fun setProjectFilter(project: String?) {
        _uiState.update { it.copy(projectFilter = project?.ifBlank { null }) }
    }

    fun setTagFilter(tag: String?) {
        _uiState.update { it.copy(tagFilter = tag?.ifBlank { null }) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearFilters() {
        _uiState.update { it.copy(projectFilter = null, tagFilter = null, searchQuery = "") }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            val deleted = repository.deleteEntry(id)
            if (deleted) {
                _uiState.update { it.copy(message = "Entry #$id deleted.") }
            }
        }
    }

    fun editEntry(
        id: Long,
        project: String?,
        description: String?,
        startTime: Instant?,
        endTime: Instant?,
        tags: List<String>?
    ) {
        viewModelScope.launch {
            val result = repository.editEntry(
                id = id,
                project = project,
                description = description,
                startTime = startTime,
                endTime = endTime,
                tags = tags
            )
            result.onSuccess {
                _uiState.update { it.copy(message = "Entry #$id updated successfully!") }
            }.onFailure { err ->
                _uiState.update { it.copy(message = "Error: ${err.message}") }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    class Factory(private val repository: TimeTrackingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EntriesViewModel(repository) as T
        }
    }
}
