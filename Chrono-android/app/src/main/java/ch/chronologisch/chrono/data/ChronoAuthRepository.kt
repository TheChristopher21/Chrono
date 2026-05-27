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

class ChronoAuthRepository(
    private val baseUrl: String,
    private val sessionStore: SessionStore,
) {
    suspend fun restoreSession(): AuthenticatedSession? {
        val token = sessionStore.getToken() ?: return null
        return try {
            AuthenticatedSession(token = token, user = fetchCurrentUser(token))
        } catch (_: Exception) {
            sessionStore.clear()
            null
        }
    }

    suspend fun login(username: String, password: String): AuthenticatedSession {
        val token = requestToken(username, password)
        val user = fetchCurrentUser(token)
        sessionStore.saveToken(token)
        return AuthenticatedSession(token = token, user = user)
    }

    suspend fun demoLogin(): AuthenticatedSession {
        val token = requestDemoToken()
        val user = fetchCurrentUser(token)
        sessionStore.saveToken(token)
        return AuthenticatedSession(token = token, user = user)
    }

    fun logout() {
        sessionStore.clear()
    }

    private suspend fun requestToken(username: String, password: String): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("username", username)
            .put("password", password)
            .toString()

        val response = postJson(path = "/api/auth/login", body = payload)
        JSONObject(response).optString("token").takeIf { it.isNotBlank() }
            ?: throw AuthException("Login erfolgreich, aber kein Token erhalten.")
    }

    private suspend fun requestDemoToken(): String = withContext(Dispatchers.IO) {
        val response = postJson(path = "/api/auth/demo", body = "{}")
        JSONObject(response).optString("token").takeIf { it.isNotBlank() }
            ?: throw AuthException("Demo gestartet, aber kein Token erhalten.")
    }

    private suspend fun fetchCurrentUser(token: String): UserProfile = withContext(Dispatchers.IO) {
        val response = request(
            path = "/api/auth/me",
            method = "GET",
            token = token,
        )
        parseUser(JSONObject(response))
    }

    private fun postJson(path: String, body: String): String = request(
        path = path,
        method = "POST",
        body = body,
    )

    private fun request(
        path: String,
        method: String,
        body: String? = null,
        token: String? = null,
    ): String {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            if (token != null) {
                setRequestProperty("Authorization", "Bearer $token")
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
                throw AuthException(extractErrorMessage(responseBody, connection.responseCode))
            }

            responseBody
        } catch (_: SocketTimeoutException) {
            throw AuthException("Backend antwortet nicht. Bitte Verbindung pruefen.")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseUser(json: JSONObject): UserProfile {
        return UserProfile(
            username = json.optString("username"),
            firstName = json.optNullableString("firstName"),
            lastName = json.optNullableString("lastName"),
            roles = json.optJSONArray("roles").toStringList(),
            dailyWorkHours = json.optNullableDouble("dailyWorkHours"),
            trackingBalanceInMinutes = json.optInt("trackingBalanceInMinutes", 0),
            isHourly = json.optBoolean("isHourly", false),
            isPercentage = json.optBoolean("isPercentage", false),
            workPercentage = json.optInt("workPercentage", 100),
            customerTrackingEnabled = json.optBoolean("customerTrackingEnabled", false),
            lastCustomerId = json.optNullableLong("lastCustomerId"),
            lastCustomerName = json.optNullableString("lastCustomerName"),
            companyFeatureKeys = json.optJSONArray("companyFeatureKeys").toStringSet(),
            pagePermissions = json.optJSONObject("pagePermissions").toStringMap(),
        )
    }

    private fun extractErrorMessage(body: String, statusCode: Int): String {
        val message = runCatching { JSONObject(body).optString("message") }.getOrNull()
        return message?.takeIf { it.isNotBlank() } ?: "Login fehlgeschlagen ($statusCode)."
    }

    private fun readBody(stream: InputStream?): String {
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }
}

private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) null else optString(name)

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (!has(name) || isNull(name)) null else optDouble(name)

private fun JSONObject.optNullableLong(name: String): Long? =
    if (!has(name) || isNull(name)) null else optLong(name)

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return List(length()) { index -> optString(index) }.filter { it.isNotBlank() }
}

private fun JSONArray?.toStringSet(): Set<String> =
    toStringList().toSet()

private fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    return keys().asSequence().associateWith { key -> optString(key) }.filterValues { it.isNotBlank() }
}
