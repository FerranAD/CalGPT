package com.calgapt.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calgapt.app.data.caldav.CalDAVService
import com.calgapt.app.data.models.CalendarEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.io.IOException
import javax.inject.Inject

data class EventDetailsUiState(
    val event: CalendarEvent? = null,
    val draftTitle: String = "",
    val draftDescription: String = "",
    val draftStart: LocalDateTime? = null,
    val draftDurationMinutes: Int? = null,
    val draftRemindersMinutes: List<Int> = emptyList(),
    val isLoading: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class EventDetailsViewModel @Inject constructor(
    private val calDAVService: CalDAVService,
    private val json: Json,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventDetailsUiState())
    val uiState: StateFlow<EventDetailsUiState> = _uiState.asStateFlow()

    init {
        val eventJson = savedStateHandle.get<String>("eventJson")
        if (eventJson != null) {
            try {
                // Decode if needed, but navigation probably passed decoded string or we need to decode manually if encoded in path
                // Actually navigation component arguments are usually decoded strings if taken from path, but let's be safe.
                val event = json.decodeFromString<CalendarEvent>(eventJson)
                val start = parseToLocalDateTime(event.start) ?: LocalDateTime.now()
                val end = parseToLocalDateTime(event.end) ?: start.plusHours(1)
                val duration = Duration.between(start, end).toMinutes().toInt().let { if (it > 0) it else 60 }
                _uiState.update {
                    it.copy(
                        event = event,
                        draftTitle = event.title,
                        draftDescription = event.description,
                        draftStart = start,
                        draftDurationMinutes = duration,
                        draftRemindersMinutes = event.remindersMinutes
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to parse event: ${e.message}") }
            }
        }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(draftTitle = title, saved = false) }
    }

    fun updateDescription(description: String) {
        _uiState.update { it.copy(draftDescription = description, saved = false) }
    }

    fun updateStart(newStart: LocalDateTime) {
        _uiState.update { it.copy(draftStart = newStart, saved = false) }
    }

    fun updateDurationMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(1, 24 * 60)
        _uiState.update { it.copy(draftDurationMinutes = clamped, saved = false) }
    }

    fun addReminderMinutes(minutes: Int) {
        if (minutes <= 0) return
        _uiState.update {
            val updated = (it.draftRemindersMinutes + minutes).distinct().sortedDescending()
            it.copy(draftRemindersMinutes = updated, saved = false)
        }
    }

    fun removeReminderMinutes(minutes: Int) {
        _uiState.update {
            val updated = it.draftRemindersMinutes.filterNot { m -> m == minutes }
            it.copy(draftRemindersMinutes = updated, saved = false)
        }
    }

    private fun buildEditedEvent(): CalendarEvent? {
        val base = uiState.value.event ?: return null
        val start = uiState.value.draftStart ?: parseToLocalDateTime(base.start) ?: LocalDateTime.now()
        val durationMinutes = uiState.value.draftDurationMinutes
            ?: run {
                val end = parseToLocalDateTime(base.end) ?: start.plusHours(1)
                Duration.between(start, end).toMinutes().toInt().let { if (it > 0) it else 60 }
            }
        val end = start.plusMinutes(durationMinutes.toLong())
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        return base.copy(
            title = uiState.value.draftTitle,
            start = start.format(formatter),
            end = end.format(formatter),
            description = uiState.value.draftDescription,
            remindersMinutes = uiState.value.draftRemindersMinutes
        )
    }

    fun confirmEvent() {
        val event = buildEditedEvent() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                calDAVService.putEvent(event)
                _uiState.update { it.copy(isLoading = false, saved = true) }
                Toast.makeText(context, "Event saved to Calendar!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = mapCalDavError(e)) }
            }
        }
    }

    private fun mapCalDavError(e: Exception): String {
        return when (e) {
            is IllegalArgumentException -> e.message
                ?: "CalDAV settings are missing. Please go to Settings, save them, then test the CalDAV connection."
            is IOException -> {
                val msg = e.message.orEmpty()
                if (msg.contains("401") || msg.contains("403") || msg.contains("Unauthorized", ignoreCase = true)) {
                    "Failed to save: CalDAV authentication failed. Please go to Settings and use 'Test Connection'."
                } else {
                    "Failed to save: CalDAV connection error. Please go to Settings and use 'Test Connection'."
                }
            }
            else -> "Failed to save: please go to Settings and test the CalDAV connection."
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
}
