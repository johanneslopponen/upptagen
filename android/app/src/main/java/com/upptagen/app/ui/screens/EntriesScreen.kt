package com.upptagen.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.upptagen.app.data.model.TimeEntryWithTags
import com.upptagen.app.ui.components.DatePickerDialogComponent
import com.upptagen.app.ui.components.DurationBadge
import com.upptagen.app.ui.components.ProjectBadge
import com.upptagen.app.ui.components.TagChip
import com.upptagen.app.ui.components.TimePickerDialogComponent
import com.upptagen.app.ui.viewmodel.EntriesViewModel
import com.upptagen.app.util.DateTimeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntriesScreen(
    viewModel: EntriesViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var editingEntry by remember { mutableStateOf<TimeEntryWithTags?>(null) }
    var entryToDelete by remember { mutableStateOf<TimeEntryWithTags?>(null) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search & Filter Header
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search description, project, tags...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            // Project & Tag Filter Chips Row
            if (uiState.availableProjects.isNotEmpty() || uiState.availableTags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.projectFilter != null || uiState.tagFilter != null) {
                        AssistChip(
                            onClick = { viewModel.clearFilters() },
                            label = { Text("Reset Filters") },
                            leadingIcon = { Icon(Icons.Default.FilterAltOff, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }

                    uiState.availableProjects.forEach { proj ->
                        val isSelected = uiState.projectFilter == proj
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.setProjectFilter(if (isSelected) null else proj)
                            },
                            label = { Text(proj) },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }

                    uiState.availableTags.forEach { tag ->
                        val isSelected = uiState.tagFilter == tag
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.setTagFilter(if (isSelected) null else tag)
                            },
                            label = { Text("#$tag") }
                        )
                    }
                }
            }

            // Summary Card (Total Tracked Time & Count)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TOTAL TRACKED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Text(
                            text = DateTimeUtils.formatDuration(uiState.totalDurationSeconds),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "${uiState.filteredEntries.size} entries",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Entries List
            if (uiState.filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.entries.isEmpty()) "No time entries recorded yet." else "No entries matching filter.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(uiState.filteredEntries, key = { it.entry.id }) { item ->
                        TimeEntryCard(
                            item = item,
                            onEdit = { editingEntry = item },
                            onDelete = { entryToDelete = item }
                        )
                    }
                }
            }
        }
    }

    // Edit Dialog
    editingEntry?.let { item ->
        EditEntryDialog(
            entryWithTags = item,
            onDismiss = { editingEntry = null },
            onSave = { project, description, startTime, endTime, tags ->
                viewModel.editEntry(
                    id = item.entry.id,
                    project = project,
                    description = description,
                    startTime = startTime,
                    endTime = endTime,
                    tags = tags
                )
                editingEntry = null
            }
        )
    }

    // Delete Confirmation Dialog
    entryToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete Entry #${item.entry.id}?") },
            text = {
                Text("Are you sure you want to permanently delete this time entry (${item.entry.description ?: "No description"})?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteEntry(item.entry.id)
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeEntryCard(
    item: TimeEntryWithTags,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entry = item.entry

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!entry.project.isNullOrEmpty()) {
                    ProjectBadge(project = entry.project)
                } else {
                    Text(
                        text = "No Project",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DurationBadge(durationSeconds = entry.durationSeconds, highlight = true)
            }

            if (!entry.description.isNullOrEmpty()) {
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DateTimeUtils.formatDisplayDateTime(entry.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (item.tagNames.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.tagNames.forEach { tag ->
                        TagChip(tag = tag)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditEntryDialog(
    entryWithTags: TimeEntryWithTags,
    onDismiss: () -> Unit,
    onSave: (project: String?, description: String?, startTime: Instant?, endTime: Instant?, tags: List<String>) -> Unit
) {
    val existingStartInstant = DateTimeUtils.parseIsoOrFlexible(entryWithTags.entry.startTime) ?: Instant.now()
    val existingEndInstant = DateTimeUtils.parseIsoOrFlexible(entryWithTags.entry.endTime) ?: Instant.now()

    val startZdt = existingStartInstant.atZone(ZoneId.systemDefault())
    val endZdt = existingEndInstant.atZone(ZoneId.systemDefault())

    var project by remember { mutableStateOf(entryWithTags.entry.project ?: "") }
    var description by remember { mutableStateOf(entryWithTags.entry.description ?: "") }
    var tagsInput by remember { mutableStateOf(entryWithTags.tagNames.joinToString(", ")) }

    var selectedDate by remember { mutableStateOf(startZdt.toLocalDate()) }
    var startTime by remember { mutableStateOf(startZdt.toLocalTime()) }
    var endTime by remember { mutableStateOf(endZdt.toLocalTime()) }

    var durationMinutesInput by remember {
        val mins = ChronoUnit.MINUTES.between(startZdt, endZdt).coerceAtLeast(0L)
        mutableStateOf(mins.toString())
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    fun updateDurationFromTimes(sTime: LocalTime, eTime: LocalTime) {
        val sDt = LocalDateTime.of(selectedDate, sTime)
        var eDt = LocalDateTime.of(selectedDate, eTime)
        if (eDt.isBefore(sDt)) {
            eDt = eDt.plusDays(1)
        }
        val mins = ChronoUnit.MINUTES.between(sDt, eDt).coerceAtLeast(0L)
        durationMinutesInput = mins.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Entry #${entryWithTags.entry.id}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = project,
                    onValueChange = { project = it },
                    label = { Text("Project") },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Task Description") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Date Selection Section
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = DateTimeUtils.formatDisplayDateFriendly(selectedDate),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Date Performed") },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.EditCalendar, contentDescription = "Select Date")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val today = LocalDate.now()
                        val yesterday = today.minusDays(1)

                        FilterChip(
                            selected = selectedDate == today,
                            onClick = { selectedDate = today },
                            label = { Text("Today") }
                        )
                        FilterChip(
                            selected = selectedDate == yesterday,
                            onClick = { selectedDate = yesterday },
                            label = { Text("Yesterday") }
                        )
                        FilterChip(
                            selected = selectedDate != today && selectedDate != yesterday,
                            onClick = { showDatePicker = true },
                            label = { Text("Pick Date...") },
                            leadingIcon = { Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                }

                // Time & Duration Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = DateTimeUtils.formatTime(startTime),
                        onValueChange = { input ->
                            val parsed = DateTimeUtils.parseTimeOrNull(input)
                            if (parsed != null) {
                                startTime = parsed
                                updateDurationFromTimes(parsed, endTime)
                            }
                        },
                        label = { Text("Start") },
                        leadingIcon = {
                            IconButton(onClick = { showStartTimePicker = true }) {
                                Icon(Icons.Default.Schedule, contentDescription = "Pick Start Time", modifier = Modifier.size(18.dp))
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = DateTimeUtils.formatTime(endTime),
                        onValueChange = { input ->
                            val parsed = DateTimeUtils.parseTimeOrNull(input)
                            if (parsed != null) {
                                endTime = parsed
                                updateDurationFromTimes(startTime, parsed)
                            }
                        },
                        label = { Text("End") },
                        leadingIcon = {
                            IconButton(onClick = { showEndTimePicker = true }) {
                                Icon(Icons.Default.Schedule, contentDescription = "Pick End Time", modifier = Modifier.size(18.dp))
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = durationMinutesInput,
                    onValueChange = { input ->
                        durationMinutesInput = input
                        val mins = input.toLongOrNull()
                        if (mins != null && mins >= 0) {
                            endTime = startTime.plusMinutes(mins)
                        }
                    },
                    label = { Text("Duration (minutes)") },
                    leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tagsInput,
                    onValueChange = { tagsInput = it },
                    label = { Text("Tags (comma separated)") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val (newStartInstant, newEndInstant) = DateTimeUtils.editedEntryTimes(
                        existingStartInstant, existingEndInstant, selectedDate, startTime, endTime
                    )

                    val tags = tagsInput.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    onSave(
                        project.ifBlank { null },
                        description.ifBlank { null },
                        newStartInstant,
                        newEndInstant,
                        tags
                    )
                }
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        DatePickerDialogComponent(
            initialDate = selectedDate,
            onDateSelected = { selectedDate = it },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showStartTimePicker) {
        TimePickerDialogComponent(
            initialTime = startTime,
            title = "Select Start Time",
            onTimeSelected = { newStartTime ->
                startTime = newStartTime
                updateDurationFromTimes(newStartTime, endTime)
            },
            onDismiss = { showStartTimePicker = false }
        )
    }

    if (showEndTimePicker) {
        TimePickerDialogComponent(
            initialTime = endTime,
            title = "Select End Time",
            onTimeSelected = { newEndTime ->
                endTime = newEndTime
                updateDurationFromTimes(startTime, newEndTime)
            },
            onDismiss = { showEndTimePicker = false }
        )
    }
}
