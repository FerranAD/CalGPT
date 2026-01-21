package com.calgapt.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private object PreferencesKeys {
        val OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        val CALDAV_URL = stringPreferencesKey("caldav_url")
        val CALDAV_USERNAME = stringPreferencesKey("caldav_username")
        val CALDAV_PASSWORD = stringPreferencesKey("caldav_password")
    }

    override val settings: Flow<SettingsData> = dataStore.data
        .map { preferences ->
            SettingsData(
                openAiApiKey = preferences[PreferencesKeys.OPENAI_API_KEY] ?: "",
                calDavUrl = preferences[PreferencesKeys.CALDAV_URL] ?: "",
                calDavUsername = preferences[PreferencesKeys.CALDAV_USERNAME] ?: "",
                calDavPassword = preferences[PreferencesKeys.CALDAV_PASSWORD] ?: ""
            )
        }

    override suspend fun updateOpenAiApiKey(apiKey: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.OPENAI_API_KEY] = apiKey
        }
    }

    override suspend fun updateCalDavSettings(url: String, username: String, password: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CALDAV_URL] = url
            preferences[PreferencesKeys.CALDAV_USERNAME] = username
            preferences[PreferencesKeys.CALDAV_PASSWORD] = password
        }
    }
}
