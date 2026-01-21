package com.calgapt.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import com.calgapt.app.data.models.CalendarEvent
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailsScreen(
    eventJson: String,
    viewModel: EventDetailsViewModel = hiltViewModel(),
    onConfirm: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptics = LocalHapticFeedback.current

    val event = uiState.event
    val (startDateTime, endDateTime) = rememberParsedDateTimes(event)
    val effectiveStart = uiState.draftStart ?: startDateTime
    val effectiveEnd = if (effectiveStart != null && uiState.draftDurationMinutes != null) {
        effectiveStart.plusMinutes(uiState.draftDurationMinutes!!.toLong())
    } else {
        endDateTime
    }
    val dateLabel = startDateTime?.let {
        it.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()))
    }
    val timeRangeLabel = if (effectiveStart != null && effectiveEnd != null) {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        val startTime = effectiveStart.format(timeFormatter)
        val endTime = effectiveEnd.format(timeFormatter)
        "$startTime – $endTime"
    } else {
        null
    }
    val durationLabel = if (effectiveStart != null && effectiveEnd != null) {
        val duration = Duration.between(effectiveStart, effectiveEnd)
        val totalMinutes = duration.toMinutes().coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        buildString {
            if (hours > 0) {
                append(hours)
                append("h")
            }
            if (minutes > 0) {
                if (isNotEmpty()) append(" ")
                append(minutes)
                append("m")
            }
            if (isEmpty()) {
                append("0m")
            }
        }
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event Preview") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (event != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!uiState.saved) {
                        OutlinedButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onBack()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Discard")
                        }
                    }

                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.confirmEvent()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading && !uiState.saved
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else if (uiState.saved) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Saved")
                        } else {
                            Text("Confirm & Save")
                        }
                    }

                    if (uiState.saved) {
                        OutlinedButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onConfirm()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (event != null) {
            var showTitleEditor by remember { mutableStateOf(false) }
            var showNotesEditor by remember { mutableStateOf(false) }
            var showDurationEditor by remember { mutableStateOf(false) }
            var showReminderEditor by remember { mutableStateOf(false) }

            var showDatePicker by remember { mutableStateOf(false) }
            var showTimePicker by remember { mutableStateOf(false) }
            var pendingDate by remember { mutableStateOf<LocalDate?>(null) }
            val currentStart = effectiveStart ?: LocalDateTime.now()

            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = currentStart
                    .toLocalDate()
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            )
            val timePickerState = rememberTimePickerState(
                initialHour = currentStart.hour,
                initialMinute = currentStart.minute,
                is24Hour = true
            )

            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(uiState.draftTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { showTitleEditor = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit title")
                            }
                        }

                        Divider()

                        ListItem(
                            headlineContent = { Text("When") },
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    val dateText = effectiveStart?.format(
                                        DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())
                                    )
                                    Text(dateText ?: event.start)
                                    if (timeRangeLabel != null) {
                                        val suffix = if (durationLabel != null) " ($durationLabel)" else ""
                                        Text("$timeRangeLabel$suffix")
                                    } else {
                                        Text("End: ${event.end}")
                                    }
                                }
                            },
                            leadingContent = { Icon(Icons.Default.Schedule, contentDescription = null) }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    pendingDate = null
                                    showDatePicker = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Edit date/time")
                            }
                            OutlinedButton(
                                onClick = { showDurationEditor = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Edit duration")
                            }
                        }

                        if (event.location.isNotBlank()) {
                            ListItem(
                                headlineContent = { Text("Where") },
                                supportingContent = { Text(event.location) },
                                leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null) }
                            )
                        }

                        ListItem(
                            headlineContent = { Text("Notes") },
                            supportingContent = {
                                Text(if (uiState.draftDescription.isNotBlank()) uiState.draftDescription else "No notes")
                            },
                            leadingContent = { Icon(Icons.Default.Description, contentDescription = null) },
                            trailingContent = {
                                IconButton(onClick = { showNotesEditor = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit notes")
                                }
                            }
                        )

                        if (uiState.draftRemindersMinutes.isNotEmpty()) {
                            ListItem(
                                headlineContent = { Text("Reminders") },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        uiState.draftRemindersMinutes
                                            .sortedDescending()
                                            .forEach { minutes ->
                                                Text(formatReminder(minutes), style = MaterialTheme.typography.bodySmall)
                                            }
                                    }
                                },
                                leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) }
                            )
                        }

                        OutlinedButton(
                            onClick = { showReminderEditor = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Edit reminders")
                        }
                    }
                }

                if (uiState.error != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = uiState.error ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val selectedMillis = datePickerState.selectedDateMillis
                                if (selectedMillis != null) {
                                    pendingDate = java.time.Instant
                                        .ofEpochMilli(selectedMillis)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()
                                    showDatePicker = false
                                    showTimePicker = true
                                }
                            }
                        ) {
                            Text("Next")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            if (showTimePicker) {
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val date = pendingDate ?: currentStart.toLocalDate()
                                val time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                                viewModel.updateStart(LocalDateTime.of(date, time))
                                showTimePicker = false
                            }
                        ) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text("Cancel")
                        }
                    },
                    title = { Text("Select time") },
                    text = {
                        TimePicker(state = timePickerState)
                    }
                )
            }

            if (showTitleEditor) {
                SimpleTextEditDialog(
                    title = "Edit title",
                    label = "Title",
                    initial = uiState.draftTitle,
                    singleLine = true,
                    onDismiss = { showTitleEditor = false },
                    onSave = {
                        viewModel.updateTitle(it)
                        showTitleEditor = false
                    }
                )
            }

            if (showNotesEditor) {
                SimpleTextEditDialog(
                    title = "Edit notes",
                    label = "Notes",
                    initial = uiState.draftDescription,
                    singleLine = false,
                    onDismiss = { showNotesEditor = false },
                    onSave = {
                        viewModel.updateDescription(it)
                        showNotesEditor = false
                    }
                )
            }

            if (showDurationEditor) {
                DurationEditDialog(
                    initialMinutes = uiState.draftDurationMinutes,
                    onDismiss = { showDurationEditor = false },
                    onSave = {
                        viewModel.updateDurationMinutes(it)
                        showDurationEditor = false
                    }
                )
            }

            if (showReminderEditor) {
                RemindersEditDialog(
                    remindersMinutes = uiState.draftRemindersMinutes,
                    onDismiss = { showReminderEditor = false },
                    onAdd = { viewModel.addReminderMinutes(it) },
                    onRemove = { viewModel.removeReminderMinutes(it) }
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (uiState.error != null) {
                    Text("Error: ${uiState.error}")
                } else {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun rememberParsedDateTimes(event: CalendarEvent?): Pair<LocalDateTime?, LocalDateTime?> {
    return androidx.compose.runtime.remember(event?.start, event?.end) {
        val start = event?.start?.let(::parseToLocalDateTime)
        val end = event?.end?.let(::parseToLocalDateTime)
        start to end
    }
}

private fun parseToLocalDateTime(value: String): LocalDateTime? {
    return try {
        LocalDateTime.parse(value)
    } catch (_: Exception) {
        try {
            LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
private fun SimpleTextEditDialog(
    title: String,
    label: String,
    initial: String,
    singleLine: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var textValue by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                label = { Text(label) },
                singleLine = singleLine,
                modifier = Modifier.fillMaxWidth(),
                minLines = if (singleLine) 1 else 4
            )
        },
        confirmButton = {
            Button(onClick = { onSave(textValue.trim()) }) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DurationEditDialog(
    initialMinutes: Int?,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var hoursText by remember(initialMinutes) {
        mutableStateOf(((initialMinutes ?: 60) / 60).toString())
    }
    var minutesText by remember(initialMinutes) {
        mutableStateOf(((initialMinutes ?: 60) % 60).toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit duration") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it },
                    label = { Text("Hours") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it },
                    label = { Text("Minutes") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val h = hoursText.trim().toIntOrNull() ?: 0
                    val m = minutesText.trim().toIntOrNull() ?: 0
                    val total = (h * 60 + m).coerceAtLeast(1)
                    onSave(total)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemindersEditDialog(
    remindersMinutes: List<Int>,
    onDismiss: () -> Unit,
    onAdd: (Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    var newAmountText by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(ReminderUnit.MINUTES) }

    val presets = listOf(
        5 to "5m",
        10 to "10m",
        15 to "15m",
        30 to "30m",
        60 to "1h",
        120 to "2h",
        240 to "4h",
        1440 to "1d",
        4320 to "3d",
        10080 to "1w"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit reminders") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Presets")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (minutes, label) ->
                        AssistChip(
                            onClick = { onAdd(minutes) },
                            label = { Text(label) }
                        )
                    }
                }

                Divider()

                Text("Current")
                if (remindersMinutes.isEmpty()) {
                    Text("No reminders")
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        remindersMinutes.sortedDescending().forEach { minutes ->
                            InputChip(
                                selected = false,
                                onClick = { onRemove(minutes) },
                                label = { Text(formatReminderShort(minutes)) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove") }
                            )
                        }
                    }
                }

                Divider()

                Text("Custom")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newAmountText,
                        onValueChange = { newAmountText = it },
                        label = { Text("Amount") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    UnitDropdown(
                        unit = unit,
                        onUnitSelected = { unit = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        val amount = newAmountText.trim().toIntOrNull()
                        if (amount != null && amount > 0) {
                            val minutes = unit.toMinutes(amount)
                            onAdd(minutes)
                            newAmountText = ""
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Add")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}

private enum class ReminderUnit {
    MINUTES,
    HOURS,
    DAYS;

    fun toMinutes(amount: Int): Int {
        return when (this) {
            MINUTES -> amount
            HOURS -> amount * 60
            DAYS -> amount * 60 * 24
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun UnitDropdown(
    unit: ReminderUnit,
    onUnitSelected: (ReminderUnit) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = when (unit) {
                ReminderUnit.MINUTES -> "Minutes"
                ReminderUnit.HOURS -> "Hours"
                ReminderUnit.DAYS -> "Days"
            },
            onValueChange = { },
            readOnly = true,
            label = { Text("Unit") },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ReminderUnit.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (option) {
                                ReminderUnit.MINUTES -> "Minutes"
                                ReminderUnit.HOURS -> "Hours"
                                ReminderUnit.DAYS -> "Days"
                            }
                        )
                    },
                    onClick = {
                        onUnitSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun formatReminderShort(minutesBefore: Int): String {
    val days = minutesBefore / (60 * 24)
    val hours = (minutesBefore % (60 * 24)) / 60
    val minutes = minutesBefore % 60

    return when {
        days > 0 && minutesBefore % (60 * 24) == 0 -> "-${days}d"
        hours > 0 && minutes == 0 -> "-${hours}h"
        else -> "-${minutesBefore}m"
    }
}

private fun formatReminder(minutesBefore: Int): String {
    if (minutesBefore <= 0) return "At time of event"
    val days = minutesBefore / (60 * 24)
    val hours = (minutesBefore % (60 * 24)) / 60
    val minutes = minutesBefore % 60

    return buildString {
        append("-")
        if (days > 0) {
            append(" ")
            append(days)
            append(" day")
            if (days != 1) append("s")
        }
        if (hours > 0) {
            append(" ")
            append(hours)
            append(" hour")
            if (hours != 1) append("s")
        }
        if (minutes > 0) {
            append(" ")
            append(minutes)
            append(" min")
        }
        append(" before")
    }.trim()
}
