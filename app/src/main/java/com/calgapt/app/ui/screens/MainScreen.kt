package com.calgapt.app.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.Manifest

import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onSettingsClick: () -> Unit = {},
    onEventGenerated: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val permissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val haptics = LocalHapticFeedback.current
    
    // Navigate when event is generated
    LaunchedEffect(uiState.generatedEvent) {
        uiState.generatedEvent?.let { event ->
            val json = Json.encodeToString(event)
            val encodedJson = Uri.encode(json)
            onEventGenerated(encodedJson)
            viewModel.clearEvent()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSettingsClick()
                }
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "CalGPT",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(32.dp))

                // Microphone Button
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer)
                        .clickable {
                            if (permissionState.status.isGranted) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.toggleRecording()
                            } else {
                                permissionState.launchPermissionRequest()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                     Icon(
                         if (uiState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                         contentDescription = "Record",
                         modifier = Modifier.size(48.dp),
                         tint = if (uiState.isRecording) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                     )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                AnimatedVisibility(visible = uiState.isRecording) {
                    Text("Recording...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                }

                AnimatedVisibility(visible = uiState.isProcessing) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(uiState.processingStatus)
                    }
                }
                
                if (uiState.error != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
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
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Text Input option
                var textInput by remember { mutableStateOf("") }
                
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Or type your event here") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (textInput.isNotBlank() && !uiState.isProcessing) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.processText(textInput)
                                }
                            },
                            enabled = textInput.isNotBlank() && !uiState.isProcessing
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                )
            }
        }
    }
}

