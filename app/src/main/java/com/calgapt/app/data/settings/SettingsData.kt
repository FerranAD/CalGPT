package com.calgapt.app.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class SettingsData(
    val openAiApiKey: String = "",
    val calDavUrl: String = "",
    val calDavUsername: String = "",
    val calDavPassword: String = ""
)
