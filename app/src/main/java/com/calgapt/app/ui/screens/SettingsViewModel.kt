package com.calgapt.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calgapt.app.data.caldav.CalDAVService
import com.calgapt.app.data.openai.ChatCompletionRequest
import com.calgapt.app.data.openai.Message
import com.calgapt.app.data.openai.OpenAIService
import com.calgapt.app.data.settings.SettingsData
import com.calgapt.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: SettingsData = SettingsData(),
    val isOpenAITesting: Boolean = false,
    val openAITestResult: Boolean? = null, // null = not tested, true = success, false = failure
    val openAITestMessage: String? = null,
    val isCalDAVTesting: Boolean = false,
    val calDAVTestResult: Boolean? = null,
    val calDAVTestMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val openAIService: OpenAIService,
    private val calDAVService: CalDAVService
) : ViewModel() {

    private fun formatError(e: Throwable): String {
        val primary = e.message?.takeIf { it.isNotBlank() } ?: e::class.java.simpleName
        val cause = e.cause?.message?.takeIf { it.isNotBlank() }
        return if (cause != null) "$primary (cause: $cause)" else primary
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsData())
                .collect { data ->
                    _uiState.update { it.copy(settings = data) }
                }
        }
    }

    fun updateOpenAiApiKey(apiKey: String) {
        viewModelScope.launch {
            settingsRepository.updateOpenAiApiKey(apiKey)
            // Reset test result when changing key
            _uiState.update { it.copy(openAITestResult = null, openAITestMessage = null) }
        }
    }

    fun updateCalDavSettings(url: String, username: String, pass: String) {
        viewModelScope.launch {
            settingsRepository.updateCalDavSettings(url, username, pass)
            _uiState.update { it.copy(calDAVTestResult = null, calDAVTestMessage = null) }
        }
    }

    fun testOpenAIConnection(apiKey: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOpenAITesting = true, openAITestResult = null, openAITestMessage = null) }
            try {
                // Determine model to use - defaulting to gpt-3.5-turbo for a cheap test, or usage of the key's allowed models
                // We'll just try a simple chat completion with max tokens 1 to authenticate
                val request = ChatCompletionRequest(
                    model = "gpt-3.5-turbo",
                    messages = listOf(Message(role = "user", content = "ping"))
                )
                openAIService.createChatCompletionWithAuth("Bearer $apiKey", request)
                _uiState.update { it.copy(isOpenAITesting = false, openAITestResult = true, openAITestMessage = "Connection successful") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isOpenAITesting = false, openAITestResult = false, openAITestMessage = "Error: ${formatError(e)}") }
            }
        }
    }

    fun testCalDAVConnection(url: String, username: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCalDAVTesting = true, calDAVTestResult = null, calDAVTestMessage = null) }
            try {
                calDAVService.testConnection(url, username, password)
                _uiState.update { it.copy(isCalDAVTesting = false, calDAVTestResult = true, calDAVTestMessage = "Connection successful") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isCalDAVTesting = false, calDAVTestResult = false, calDAVTestMessage = "Error: ${formatError(e)}") }
            }
        }
    }
}
