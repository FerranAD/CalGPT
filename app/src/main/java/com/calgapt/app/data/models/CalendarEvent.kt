package com.calgapt.app.data.models

import kotlinx.serialization.Serializable

@Serializable
data class CalendarEvent(
    val title: String,
    val start: String, // ISO 8601
    val end: String, // ISO 8601
    val description: String,
    val location: String = "",
    val remindersMinutes: List<Int> = emptyList()
)
