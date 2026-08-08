package ch.chronologisch.chrono.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TimeTrackingRepository(
    private val baseUrl: String,
) {
    suspend fun loadCustomers(token: String): List<CustomerOption> =
        withContext(Dispatchers.IO) {
            val response = request(
                path = "/api/customers/recent",
                method = "GET",
                token = token,
            )
            response.toJsonArray().toCustomerOptions()
        }

    suspend fun loadProjects(token: String): List<ProjectOption> =
        withContext(Dispatchers.IO) {
            val response = request(
                path = "/api/projects",
                method = "GET",
                token = token,
            )
            response.toJsonArray().toProjectOptions()
        }

    suspend fun loadTasks(token: String, projectId: Long): List<TaskOption> =
        withContext(Dispatchers.IO) {
            val response = request(
                path = "/api/tasks?projectId=$projectId",
                method = "GET",
                token = token,
            )
            response.toJsonArray().toTaskOptions()
        }

    suspend fun loadHistory(token: String, username: String): List<DailyTimeSummary> =
        withContext(Dispatchers.IO) {
            val response = request(
                path = "/api/timetracking/history?username=${username.urlEncoded()}",
                method = "GET",
                token = token,
            )
            response.toJsonArray().toDailySummaries()
        }

    suspend fun punch(
        token: String,
        username: String,
        customerId: Long?,
        projectId: Long?,
        taskId: Long?,
    ): TimeTrackingEntry =
        withContext(Dispatchers.IO) {
            val params = buildList {
                add("username=${username.urlEncoded()}")
                add("source=MANUAL_PUNCH")
                customerId?.let { add("customerId=$it") }
                projectId?.let { add("projectId=$it") }
                taskId?.let { add("taskId=$it") }
            }.joinToString("&")
            val response = request(
                path = "/api/timetracking/punch?$params",
                method = "POST",
                token = token,
            )
            JSONObject(response).toTimeTrackingEntry()
        }

    suspend fun saveDailyNote(token: String, username: String, date: LocalDate, note: String) =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("note", note)
                .toString()
            request(
                path = "/api/timetracking/daily-note?username=${username.urlEncoded()}&date=$date",
                method = "POST",
                token = token,
                body = payload,
            )
        }

    private fun request(
        path: String,
        method: String,
        token: String,
        body: String? = null,
    ): String {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }

        return try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }
            }
            val responseBody = readBody(
                if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream,
            )

            if (connection.responseCode !in 200..299) {
                throw TimeTrackingException(extractErrorMessage(responseBody, connection.responseCode))
            }

            responseBody
        } catch (_: SocketTimeoutException) {
            throw TimeTrackingException("Backend antwortet nicht. Bitte Verbindung prüfen.")
        } finally {
            connection.disconnect()
        }
    }

    private fun extractErrorMessage(body: String, statusCode: Int): String {
        val message = runCatching { JSONObject(body).optString("message") }.getOrNull()
        return message?.takeIf { it.isNotBlank() } ?: "Zeiterfassung fehlgeschlagen ($statusCode)."
    }

    private fun readBody(stream: InputStream?): String {
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }
}

private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")

private fun String.toJsonArray(): JSONArray =
    if (isBlank()) JSONArray() else JSONArray(this)

private fun JSONArray.toDailySummaries(): List<DailyTimeSummary> =
    List(length()) { index -> getJSONObject(index).toDailyTimeSummary() }

private fun JSONArray.toCustomerOptions(): List<CustomerOption> =
    List(length()) { index -> getJSONObject(index).toCustomerOption() }
        .filter { it.name.isNotBlank() }

private fun JSONArray.toProjectOptions(): List<ProjectOption> =
    List(length()) { index -> getJSONObject(index).toProjectOption() }
        .filter { it.name.isNotBlank() }

private fun JSONArray.toTaskOptions(): List<TaskOption> =
    List(length()) { index -> getJSONObject(index).toTaskOption() }
        .filter { it.name.isNotBlank() }

private fun JSONObject.toCustomerOption(): CustomerOption =
    CustomerOption(
        id = optLong("id"),
        name = optString("name"),
    )

private fun JSONObject.toProjectOption(): ProjectOption =
    ProjectOption(
        id = optLong("id"),
        name = optString("name"),
        customerId = optJSONObject("customer")?.optNullableLong("id"),
    )

private fun JSONObject.toTaskOption(): TaskOption =
    TaskOption(
        id = optLong("id"),
        name = optString("name"),
        billable = optBoolean("billable", false),
    )

private fun JSONObject.toDailyTimeSummary(): DailyTimeSummary {
    val entries = optJSONArray("entries")?.let { array ->
        List(array.length()) { index -> array.getJSONObject(index).toTimeTrackingEntry() }
    }.orEmpty()

    val primaryTimesJson = optJSONObject("primaryTimes")
    return DailyTimeSummary(
        username = optString("username"),
        date = optLocalDate("date") ?: LocalDate.now(),
        workedMinutes = optInt("workedMinutes", 0),
        breakMinutes = optInt("breakMinutes", 0),
        entries = entries,
        dailyNote = optNullableString("dailyNote"),
        needsCorrection = optBoolean("needsCorrection", false),
        primaryTimes = PrimaryTimes(
            firstStartTime = primaryTimesJson?.optLocalTime("firstStartTime"),
            lastEndTime = primaryTimesJson?.optLocalTime("lastEndTime"),
            isOpen = primaryTimesJson?.optOpenValue() ?: false,
        ),
    )
}

private fun JSONObject.toTimeTrackingEntry(): TimeTrackingEntry =
    TimeTrackingEntry(
        id = optNullableLong("id"),
        entryTimestamp = optLocalDateTime("entryTimestamp"),
        punchType = when (optString("punchType")) {
            "START" -> PunchType.START
            "ENDE" -> PunchType.ENDE
            else -> PunchType.UNKNOWN
        },
        source = optNullableString("source"),
        customerName = optNullableString("customerName"),
        projectName = optNullableString("projectName"),
        taskName = optNullableString("taskName"),
        durationMinutes = optNullableInt("durationMinutes"),
        description = optNullableString("description"),
        correctedByUser = optBoolean("correctedByUser", false),
        systemGeneratedNote = optNullableString("systemGeneratedNote"),
    )

private fun JSONObject.optNullableString(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

private fun JSONObject.optNullableLong(name: String): Long? =
    if (!has(name) || isNull(name)) null else optLong(name)

private fun JSONObject.optNullableInt(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name)

private fun JSONObject.optLocalDate(name: String): LocalDate? =
    optNullableString(name)?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }

private fun JSONObject.optLocalTime(name: String): LocalTime? =
    optNullableString(name)?.let { value -> runCatching { LocalTime.parse(value) }.getOrNull() }

private fun JSONObject.optLocalDateTime(name: String): LocalDateTime? =
    optNullableString(name)?.let { value -> runCatching { LocalDateTime.parse(value) }.getOrNull() }

private fun JSONObject.optOpenValue(): Boolean =
    optBoolean("isOpen", optBoolean("open", false))
