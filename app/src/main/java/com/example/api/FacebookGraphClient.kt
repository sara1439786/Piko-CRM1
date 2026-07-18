package com.example.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

enum class FacebookConnectionStatus {
    UNCONFIGURED,
    ACTIVE,
    INVALID_TOKEN,
    OFFLINE,
    SIMULATED
}

data class FacebookHealthResult(
    val status: FacebookConnectionStatus,
    val message: String,
    val pageId: String? = null,
    val pageName: String? = null,
    val checkTime: Long = System.currentTimeMillis()
)

object FacebookGraphClient {
    private const val TAG = "FacebookGraphClient"

    suspend fun verifyTokenHealth(token: String): FacebookHealthResult = withContext(Dispatchers.IO) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            return@withContext FacebookHealthResult(
                status = FacebookConnectionStatus.UNCONFIGURED,
                message = "No Page Access Token configured. Setup page integration in Admin Settings."
            )
        }

        try {
            val url = URL("https://graph.facebook.com/v18.0/me?fields=id,name&access_token=$trimmed")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/json")

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val id = json.optString("id")
                val name = json.optString("name")
                return@withContext FacebookHealthResult(
                    status = FacebookConnectionStatus.ACTIVE,
                    message = "Graph API connection healthy. Authenticated page: $name.",
                    pageId = id,
                    pageName = name
                )
            } else {
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                var errMsg = "Validation failed with response code $responseCode."
                if (errorText.isNotEmpty()) {
                    try {
                        val errJson = JSONObject(errorText)
                        val errorObj = errJson.getJSONObject("error")
                        errMsg = errorObj.getString("message")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing Facebook error JSON", e)
                    }
                }
                return@withContext FacebookHealthResult(
                    status = FacebookConnectionStatus.INVALID_TOKEN,
                    message = "Graph API Error: $errMsg"
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network exception verifying Facebook token", e)
            return@withContext FacebookHealthResult(
                status = FacebookConnectionStatus.OFFLINE,
                message = "Graph API unreachable: ${e.localizedMessage}. Please check internet connection."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception verifying Facebook token", e)
            return@withContext FacebookHealthResult(
                status = FacebookConnectionStatus.INVALID_TOKEN,
                message = "Unexpected API verification exception: ${e.localizedMessage}"
            )
        }
    }
}
