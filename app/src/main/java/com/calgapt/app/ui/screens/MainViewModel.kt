package com.calgapt.app.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calgapt.app.data.audio.AudioRecorder
import com.calgapt.app.data.caldav.CalDAVService
import com.calgapt.app.data.models.CalendarEvent
import com.calgapt.app.data.openai.ChatCompletionRequest
import com.calgapt.app.data.openai.Message
import com.calgapt.app.data.openai.OpenAIService
import com.calgapt.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import timber.log.Timber
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class MainUiState(
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val processingStatus: String = "", // Transcribing, Generating Event...
    val generatedEvent: CalendarEvent? = null,
    val error: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val openAIService: OpenAIService,
    private val calDAVService: CalDAVService,
    private val json: Json,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private suspend fun ensureCredentialsConfigured(): Boolean {
        val settings = settingsRepository.settings.first()
        val missing = buildList {
            if (settings.openAiApiKey.isBlank()) add("OpenAI API key")
            if (settings.calDavUrl.isBlank()) add("CalDAV URL")
            if (settings.calDavUsername.isBlank()) add("CalDAV username")
            if (settings.calDavPassword.isBlank()) add("CalDAV password")
        }

        if (missing.isEmpty()) return true

        val message = buildString {
            append("Missing configuration: ")
            append(missing.joinToString())
            append(". Please go to Settings and fill them in, save, then test each connection.")
        }

        _uiState.update {
            it.copy(
                isProcessing = false,
                processingStatus = "",
                error = message,
                generatedEvent = null
            )
        }
        return false
    }

    private fun startRecording() {
        try {
            audioRecorder.startRecording()
            _uiState.update { it.copy(isRecording = true, error = null, generatedEvent = null) }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Failed to start recording: ${e.message}") }
        }
    }

    private fun stopRecording() {
        val file = audioRecorder.stopRecording()
        _uiState.update { it.copy(isRecording = false) }
        
        if (file != null) {
            processAudio(file)
        } else {
            _uiState.update { it.copy(error = "Recording failed") }
        }
    }
    
    fun processText(text: String) {
        viewModelScope.launch {
            if (!ensureCredentialsConfigured()) return@launch
            generateEventFromText(text)
        }
    }

    private fun processAudio(file: File) {
        viewModelScope.launch {
            if (!ensureCredentialsConfigured()) {
                _uiState.update { it.copy(isProcessing = false) }
                return@launch
            }
            _uiState.update { it.copy(isProcessing = true, processingStatus = "Transcribing audio...", error = null) }
            try {
                val requestFile = file.asRequestBody("audio/mp4".toMediaType())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

                val model = "whisper-1".toRequestBody("text/plain".toMediaType())
                val response = openAIService.createTranscription(body, model)
                generateEventFromText(response.text)
            } catch (e: Exception) {
                Timber.e(e, "Transcribing failed")
                _uiState.update { it.copy(isProcessing = false, error = mapOpenAiError(e, stage = "Transcribing") ) }
            }
        }
    }

    private fun generateEventFromText(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingStatus = "Generating Event...", error = null) }
            try {
                // Initial update to user if text input
                 
                val now = ZonedDateTime.now()
                val nowFormatted = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                val systemPrompt = """
                    You are a smart assistant that extracts calendar events from user text.
                    Current date/time: $nowFormatted (${now.zone.id}). Use this to resolve relative dates like "today", "tomorrow", "next Monday".
                    You MUST return ONLY a valid JSON object matching this structure:
                    {
                      "title": "String",
                      "start": "String (ISO 8601 format YYYY-MM-DDTHH:mm:ss)",
                      "end": "String (ISO 8601 format YYYY-MM-DDTHH:mm:ss)",
                      "description": "String",
                      "location": "String",
                      "remindersMinutes": [Int]
                    }
                    remindersMinutes MUST be a list of minutes BEFORE the event start.
                    Example: "remind me 4h before and 1 day before" -> [240, 1440]
                    If no specific date is mentioned, assume relative to Current date/time.
                    Do not include markdown formatting like ```json. Just raw JSON.
                """.trimIndent()

                val request = ChatCompletionRequest(
                    model = "gpt-3.5-turbo",
                    messages = listOf(
                        Message("system", systemPrompt),
                        Message("user", text)
                    )
                )

                val response = openAIService.createChatCompletion(request)
                val content = response.choices.firstOrNull()?.message?.content ?: throw Exception("No response from OpenAI")
                
                // Clean content if it has markdown code blocks
                val cleanContent = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                
                val event = json.decodeFromString<CalendarEvent>(cleanContent)
                
                _uiState.update { it.copy(isProcessing = false, generatedEvent = event) }
            } catch (e: Exception) {
                Timber.e(e, "Event generation failed")
                _uiState.update { it.copy(isProcessing = false, error = mapOpenAiError(e, stage = "Generating event") ) }
            }
        }
    }

    private fun mapOpenAiError(e: Exception, stage: String): String {
        return when (e) {
            is HttpException -> {
                when (e.code()) {
                    401, 403 -> "$stage failed: your OpenAI API key was rejected. Please go to Settings, save your API key, then use 'Test Connection'."
                    429 -> "$stage failed: OpenAI rate limit exceeded. Please try again later."
                    else -> "$stage failed: OpenAI request error (${e.code()}). Please check Settings and try again."
                }
            }
            else -> {
                val msg = e.message.orEmpty()
                if (msg.contains("Unable to resolve host", ignoreCase = true) ||
                    msg.contains("timeout", ignoreCase = true) ||
                    msg.contains("Failed to connect", ignoreCase = true)
                ) {
                    "$stage failed: network error. Please check your connection and try again."
                } else {
                    "$stage failed. Please make sure your OpenAI API key is saved and tested in Settings."
                }
            }
        }
    }

    fun clearEvent() {
        _uiState.update { it.copy(generatedEvent = null) }
    }
    
    fun confirmEvent() {
        val event = uiState.value.generatedEvent ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingStatus = "Saving to Calendar...") }
            try {
                calDAVService.putEvent(event)
                _uiState.update { it.copy(isProcessing = false, generatedEvent = null) }
                // Show success toast or message, managed by UI observing state or explicit channel
                Toast.makeText(context, "Event saved successfully!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, error = "Failed to save event: ${e.message}") }
            }
        }
    }
}
