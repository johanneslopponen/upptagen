package com.upptagen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.upptagen.app.data.repository.TimeTrackingRepository
import com.upptagen.app.ui.components.DatePickerDialogComponent
import com.upptagen.app.ui.components.ProjectBadge
import com.upptagen.app.ui.components.TagChip
import com.upptagen.app.ui.components.TimePickerDialogComponent
import com.upptagen.app.ui.viewmodel.TimerViewModel
import com.upptagen.app.util.DateTimeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    availableProjects: List<String> = emptyList(),
    availableTags: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    var startProject by remember { mutableStateOf("") }
    var startDescription by remember { mutableStateOf("") }
    var startTagsInput by remember { mutableStateOf("") }

    var showStopDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Top Section: Live Active Timer OR Inactive State
            val activeTimer = uiState.activeTimer
            if (activeTimer != null) {
                // Active Running Timer Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Text(
                                text = "ACTIVE TIMER RUNNING",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.2.sp
                            )
                        }

                        // Big Monospace Clock
                        Text(
                            text = DateTimeUtils.formatDuration(uiState.elapsedSeconds),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 44.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (!activeTimer.project.isNullOrEmpty()) {
                            ProjectBadge(project = activeTimer.project)
                        }

                        if (!activeTimer.description.isNullOrEmpty()) {
                            Text(
                                text = activeTimer.description,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        val tags = TimeTrackingRepository.jsonToTags(activeTimer.tagsJson)
                        if (tags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                tags.forEach { tag ->
                                    TagChip(tag = tag)
                                }
                            }
                        }

                        Text(
                            text = "Started at " + DateTimeUtils.formatDisplayDateTime(activeTimer.startTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Big Stop Button
                        Button(
                            onClick = { showStopDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop Timer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            } else {
                // Inactive: Quick Start Form Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Start Live Timer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedTextField(
                            value = startProject,
                            onValueChange = { startProject = it },
                            label = { Text("Project (e.g. CoreApp, Marketing)") },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Suggestions for projects
                        if (startProject.isEmpty() && availableProjects.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableProjects.take(4).forEach { proj ->
                                    SuggestionChip(
                                        onClick = { startProject = proj },
                                        label = { Text(proj, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = startDescription,
                            onValueChange = { startDescription = it },
                            label = { Text("Description / Task name") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = startTagsInput,
                            onValueChange = { startTagsInput = it },
                            label = { Text("Tags (comma separated, e.g. rust, meeting)") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Suggestions for tags
                        if (startTagsInput.isEmpty() && availableTags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableTags.take(5).forEach { tag ->
                                    SuggestionChip(
                                        onClick = {
                                            startTagsInput = if (startTagsInput.isBlank()) tag else "$startTagsInput, $tag"
                                        },
                                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val tagsList = startTagsInput.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                viewModel.startTimer(
                                    project = startProject,
                                    description = startDescription,
                                    tags = tagsList
                                )
                                startDescription = ""
                                startTagsInput = ""
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Timer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            // Secondary Card: Log Manual Entry Retroactively
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Forgot to start a timer?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Add past work hours retroactively",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = { showManualDialog = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Log Time")
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Stop Timer Dialog (Optional override of description & additional tags)
    if (showStopDialog) {
        val active = uiState.activeTimer
        var overrideDesc by remember { mutableStateOf(active?.description ?: "") }
        var additionalTags by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("Stop Active Timer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Current Elapsed: " + DateTimeUtils.formatDuration(uiState.elapsedSeconds),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = overrideDesc,
                        onValueChange = { overrideDesc = it },
                        label = { Text("Description (optional override)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = additionalTags,
                        onValueChange = { additionalTags = it },
                        label = { Text("Additional tags (comma separated)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val tagsList = additionalTags.split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        viewModel.stopTimer(overrideDesc, tagsList)
                        showStopDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Stop & Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Manual Entry Dialog
    if (showManualDialog) {
        ManualEntryDialog(
            availableProjects = availableProjects,
            availableTags = availableTags,
            onDismiss = { showManualDialog = false },
            onConfirm = { project, description, start, end, tags ->
                viewModel.addManualEntry(project, description, start, end, tags)
                showManualDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualEntryDialog(
    availableProjects: List<String>,
    availableTags: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (project: String?, description: String?, start: Instant, end: Instant, tags: List<String>) -> Unit
) {
    var project by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tagsInput by remember { mutableStateOf("") }

    val now = LocalDateTime.now()
    var selectedDate by remember { mutableStateOf(now.toLocalDate()) }
    var startTime by remember { mutableStateOf(now.minusHours(1).toLocalTime().withSecond(0).withNano(0)) }
    var endTime by remember { mutableStateOf(now.toLocalTime().withSecond(0).withNano(0)) }

    var durationMinutesInput by remember {
        val mins = ChronoUnit.MINUTES.between(startTime, endTime).coerceAtLeast(1L)
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
        title = { Text("Log Past Time Entry") },
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

                if (project.isEmpty() && availableProjects.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableProjects.take(4).forEach { proj ->
                            SuggestionChip(
                                onClick = { project = proj },
                                label = { Text(proj, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

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

                // Duration Input
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

                if (tagsInput.isEmpty() && availableTags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableTags.take(5).forEach { tag ->
                            SuggestionChip(
                                onClick = {
                                    tagsInput = if (tagsInput.isBlank()) tag else "$tagsInput, $tag"
                                },
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sDt = LocalDateTime.of(selectedDate, startTime)
                    var eDt = LocalDateTime.of(selectedDate, endTime)
                    if (eDt.isBefore(sDt)) {
                        eDt = eDt.plusDays(1)
                    }

                    val startInstant = sDt.atZone(ZoneId.systemDefault()).toInstant()
                    val endInstant = eDt.atZone(ZoneId.systemDefault()).toInstant()

                    val tags = tagsInput.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    onConfirm(
                        project.ifBlank { null },
                        description.ifBlank { null },
                        startInstant,
                        endInstant,
                        tags
                    )
                }
            ) {
                Text("Add Entry")
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
