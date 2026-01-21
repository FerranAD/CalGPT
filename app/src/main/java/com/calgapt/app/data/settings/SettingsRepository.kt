package com.calgapt.app.data.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<SettingsData>
    
    suspend fun updateOpenAiApiKey(apiKey: String)
    suspend fun updateCalDavSettings(url: String, username: String, password: String)
}
