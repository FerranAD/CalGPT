package com.calgapt.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptics = LocalHapticFeedback.current
    var showApiKey by remember { mutableStateOf(false) }
    var showCalDavPass by remember { mutableStateOf(false) }

    var apiKeyField by remember(uiState.settings.openAiApiKey) { mutableStateOf(TextFieldValue(uiState.settings.openAiApiKey)) }
    var calDavUrlField by remember(uiState.settings.calDavUrl) { mutableStateOf(TextFieldValue(uiState.settings.calDavUrl)) }
    var calDavUsernameField by remember(uiState.settings.calDavUsername) { mutableStateOf(TextFieldValue(uiState.settings.calDavUsername)) }
    var calDavPasswordField by remember(uiState.settings.calDavPassword) { mutableStateOf(TextFieldValue(uiState.settings.calDavPassword)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // OpenAI Section
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "OpenAI Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    OutlinedTextField(
                        value = apiKeyField,
                        onValueChange = { apiKeyField = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Visibility"
                                )
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.updateOpenAiApiKey(apiKeyField.text)
                            },
                            enabled = apiKeyField.text.isNotBlank() && apiKeyField.text != uiState.settings.openAiApiKey
                        ) {
                            Text("Save")
                        }

                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.testOpenAIConnection(apiKeyField.text)
                            },
                            enabled = !uiState.isOpenAITesting && apiKeyField.text.isNotBlank()
                        ) {
                            if (uiState.isOpenAITesting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Testing...")
                            } else {
                                Text("Test Connection")
                            }
                        }
                    }

                    TestResult(uiState.openAITestResult, uiState.openAITestMessage)
                }
            }

            // CalDAV Section
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "CalDAV Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = calDavUrlField,
                        onValueChange = { calDavUrlField = it },
                        label = { Text("CalDAV URL") },
                        singleLine = true,
                        placeholder = { Text("https://caldav.example.com/calendars/user/calendar/") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )

                    OutlinedTextField(
                        value = calDavUsernameField,
                        onValueChange = { calDavUsernameField = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = calDavPasswordField,
                        onValueChange = { calDavPasswordField = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showCalDavPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showCalDavPass = !showCalDavPass }) {
                                Icon(
                                    if (showCalDavPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Visibility"
                                )
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.updateCalDavSettings(
                                    calDavUrlField.text,
                                    calDavUsernameField.text,
                                    calDavPasswordField.text
                                )
                            },
                            enabled = calDavUrlField.text.isNotBlank() &&
                                calDavUsernameField.text.isNotBlank() &&
                                calDavPasswordField.text.isNotBlank() &&
                                (
                                    calDavUrlField.text != uiState.settings.calDavUrl ||
                                        calDavUsernameField.text != uiState.settings.calDavUsername ||
                                        calDavPasswordField.text != uiState.settings.calDavPassword
                                    )
                        ) {
                            Text("Save")
                        }

                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.testCalDAVConnection(
                                    calDavUrlField.text,
                                    calDavUsernameField.text,
                                    calDavPasswordField.text
                                )
                            },
                            enabled = !uiState.isCalDAVTesting &&
                                calDavUrlField.text.isNotBlank() &&
                                calDavUsernameField.text.isNotBlank() &&
                                calDavPasswordField.text.isNotBlank()
                        ) {
                            if (uiState.isCalDAVTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Testing...")
                            } else {
                                Text("Test Connection")
                            }
                        }
                    }

                    TestResult(uiState.calDAVTestResult, uiState.calDAVTestMessage)
                }
            }
        }
    }
}

@Composable
fun TestResult(result: Boolean?, message: String?) {
    if (result != null) {
        val containerColor = if (result) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
        val contentColor = if (result) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = containerColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (result) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = contentColor
                )
                Text(
                    text = message ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
