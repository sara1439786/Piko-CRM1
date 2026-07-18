package com.example.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    @Serializable
    data class GenerateContentRequest(
        val contents: List<Content>
    )

    @Serializable
    data class Content(
        val parts: List<Part>
    )

    @Serializable
    data class Part(
        val text: String
    )

    @Serializable
    data class GenerateContentResponse(
        val candidates: List<Candidate>? = null
    )

    @Serializable
    data class Candidate(
        val content: Content? = null
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun analyzeSentiment(transcript: String, customApiKey: String? = null): String = withContext(Dispatchers.IO) {
        val apiKey = if (!customApiKey.isNullOrEmpty()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "mock_api_key_placeholder") {
            Log.e(TAG, "API Key is missing or default placeholder")
            return@withContext "Neutral"
        }

        val prompt = """
            You are a helpful CRM sales assistant. Analyze the sentiment of the following call transcription between a tele-caller Agent Sarah and a lead.
            Based on the tone, language, and context, categorize the sentiment of this lead as exactly one of the following words: "Positive", "Neutral", or "Negative".
            Do not return any explanations, punctuation, or formatting. Only return the single word ("Positive", "Neutral", or "Negative").

            Transcription:
            $transcript
        """.trimIndent()

        val requestObj = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(Part(text = prompt))
                )
            )
        )

        val requestBody = json.encodeToString(GenerateContentRequest.serializer(), requestObj)

        try {
            val url = URL("$BASE_URL?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 30000

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val responseObj = json.decodeFromString(GenerateContentResponse.serializer(), responseText)
                val rawText = responseObj.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() ?: "Neutral"
                
                // Sanitize response to make sure we only match Positive/Neutral/Negative
                val cleaned = rawText.lowercase()
                when {
                    cleaned.contains("positive") -> "Positive"
                    cleaned.contains("negative") -> "Negative"
                    else -> "Neutral"
                }
            } else {
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Gemini call failed with response code $responseCode: $errorText")
                "Neutral"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini API call", e)
            "Neutral"
        }
    }
}
