package com.calgapt.app.data.caldav

import com.calgapt.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.io.IOException
import javax.inject.Inject

class CalDAVService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository
) {
    private val calendarPropfindBody = """
        <?xml version="1.0" encoding="utf-8" ?>
        <d:propfind xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
          <d:prop>
            <d:resourcetype />
          </d:prop>
        </d:propfind>
    """.trimIndent()

    private fun escapeIcalText(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
    }

    private fun toIcalDateTime(iso8601: String): String {
        // Expects ISO8601 local datetime like 2026-01-20T17:00:00
        // iCal DATE-TIME is typically YYYYMMDDTHHMMSS (no separators).
        return iso8601
            .trim()
            .replace("-", "")
            .replace(":", "")
    }

    private fun normalizeUrl(url: String): String {
        // Many CalDAV servers expect a trailing slash at a collection root.
        // If the user provides https://example.com (no path), we'll add a trailing slash.
        val trimmed = url.trim()
        return if (trimmed.isNotBlank() && !trimmed.endsWith("/")) "$trimmed/" else trimmed
    }

    private fun resolveRedirectUrl(baseUrl: String, location: String): String {
        val base = baseUrl.toHttpUrlOrNull()
        val resolved = base?.resolve(location)
        return resolved?.toString() ?: location
    }

    private fun executePropfind(url: String, credential: String): Response {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .method("PROPFIND", calendarPropfindBody.toRequestBody("text/xml".toMediaType()))
            .header("Depth", "0")
            .build()

        return okHttpClient.newCall(request).execute()
    }

    private fun isCalendarCollection(propfindResponseBody: String): Boolean {
        // Minimal heuristic: a calendar collection should advertise a CalDAV calendar resource type.
        // Example tags: <cal:calendar/> or <caldav:calendar/>
        val calendarTag = Regex("""<\s*[^>]*:calendar\b""", RegexOption.IGNORE_CASE)
        val resourcetypeTag = Regex("""<\s*[^>]*:resourcetype\b""", RegexOption.IGNORE_CASE)
        return resourcetypeTag.containsMatchIn(propfindResponseBody) && calendarTag.containsMatchIn(propfindResponseBody)
    }

    @Throws(IOException::class)
    suspend fun testConnection(): Boolean {
        val settings = settingsRepository.settings.first()
        return testConnection(settings.calDavUrl, settings.calDavUsername, settings.calDavPassword)
    }

    @Throws(IOException::class)
    suspend fun testConnection(url: String, username: String, password: String): Boolean {
        if (url.isBlank() || username.isBlank() || password.isBlank()) {
            throw IllegalArgumentException("Missing CalDAV credentials")
        }

        val credential = Credentials.basic(username, password)
        val initialUrl = normalizeUrl(url)

        return withContext(Dispatchers.IO) {
            executePropfind(initialUrl, credential).use { response ->
                val code = response.code

                if (code == 401 || code == 403) {
                    throw IOException("Authentication failed (HTTP $code). Please verify the username/password and try 'Test Connection' again.")
                }

                if (code == 404) {
                    throw IOException("No calendar was found at this URL (HTTP 404). Please paste the URL of a specific calendar collection.")
                }

                // 207 Multi-Status is typical for WebDAV, 200 OK is also fine
                if (response.isSuccessful || code == 207) {
                    val body = response.body?.string().orEmpty().take(50_000)
                    if (!isCalendarCollection(body)) {
                        throw IOException(
                            "Connected, but this URL does not appear to be a calendar collection. Please paste the URL of a specific calendar."
                        )
                    }
                    return@withContext true
                }

                // Some servers redirect unauthenticated clients, and OkHttp may not keep Authorization.
                // Retry once with the redirected URL, explicitly keeping Authorization.
                if (code in listOf(301, 302, 307, 308)) {
                    val location = response.header("Location")
                    if (!location.isNullOrBlank()) {
                        val redirectUrl = resolveRedirectUrl(initialUrl, location)
                        executePropfind(redirectUrl, credential).use { redirectedResponse ->
                            val redirectedCode = redirectedResponse.code

                            if (redirectedCode == 401 || redirectedCode == 403) {
                                throw IOException(
                                    "Authentication failed after redirect (HTTP $redirectedCode). Please verify the username/password and try 'Test Connection' again."
                                )
                            }

                            if (redirectedCode == 404) {
                                throw IOException(
                                    "No calendar was found at this URL after redirect (HTTP 404). Please paste the URL of a specific calendar collection."
                                )
                            }

                            if (redirectedResponse.isSuccessful || redirectedCode == 207) {
                                val body = redirectedResponse.body?.string().orEmpty().take(50_000)
                                if (!isCalendarCollection(body)) {
                                    throw IOException(
                                        "Connected, but this URL does not appear to be a calendar collection. Please paste the URL of a specific calendar."
                                    )
                                }
                                return@withContext true
                            }

                            val body = redirectedResponse.body?.string().orEmpty().take(500)
                            throw IOException(
                                "Connection failed after redirect: ${redirectedResponse.code} ${redirectedResponse.message}. Response: $body"
                            )
                        }
                    }
                }

                val body = response.body?.string().orEmpty().take(500)
                throw IOException("Connection failed: ${response.code} ${response.message}. Response: $body")
            }
        }
    }

    suspend fun putEvent(event: com.calgapt.app.data.models.CalendarEvent): Boolean {
        val settings = settingsRepository.settings.first()
        if (settings.calDavUrl.isBlank() || settings.calDavUsername.isBlank() || settings.calDavPassword.isBlank()) {
            throw IllegalArgumentException("Missing CalDAV credentials. Please go to Settings and fill them in, then test the CalDAV connection.")
        }
        val credential = Credentials.basic(settings.calDavUsername, settings.calDavPassword)

        val dtstamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())

        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//CalGPT//Android App//EN",
            "CALSCALE:GREGORIAN",
            "BEGIN:VEVENT",
            "UID:${java.util.UUID.randomUUID()}",
            "DTSTAMP:$dtstamp",
            "DTSTART:${toIcalDateTime(event.start)}",
            "DTEND:${toIcalDateTime(event.end)}",
            "SUMMARY:${escapeIcalText(event.title)}",
            "DESCRIPTION:${escapeIcalText(event.description)}",
            "LOCATION:${escapeIcalText(event.location)}"
        )

        event.remindersMinutes
            .distinct()
            .filter { it > 0 }
            .sortedDescending()
            .forEach { minutes ->
                val trigger = "-PT${minutes}M"
                lines += "BEGIN:VALARM"
                lines += "ACTION:DISPLAY"
                lines += "DESCRIPTION:Reminder"
                lines += "TRIGGER:$trigger"
                lines += "END:VALARM"
            }

        lines += "END:VEVENT"
        lines += "END:VCALENDAR"

        // Use CRLF as required by iCalendar (and avoid leading spaces which would be treated as folded lines)
        val vCalendar = lines.joinToString("\r\n", postfix = "\r\n")

        // We need a filename, usually UUID.ics
        val filename = "${java.util.UUID.randomUUID()}.ics"
        val url = if (settings.calDavUrl.endsWith("/")) settings.calDavUrl + filename else "${settings.calDavUrl}/$filename"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .put(vCalendar.toRequestBody("text/calendar".toMediaType()))
            .build()
            
        return withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code !in 200..299) {
                    val body = response.body?.string().orEmpty().take(800)
                    throw IOException("Failed to save event: ${response.code} ${response.message}. Response: $body")
                }
                true
            }
        }
    }
}
