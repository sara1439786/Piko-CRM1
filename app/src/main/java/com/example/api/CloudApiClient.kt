package com.example.api

import com.example.database.LeadEntity
import com.example.database.TelecallerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class CloudApiException(message: String) : Exception(message)

data class LoginResult(val token: String, val name: String, val email: String, val role: String)

data class CloudSnapshot(
    val leads: List<LeadEntity>,
    val telecallers: List<TelecallerEntity>
)

class CloudApiClient(
    private val baseUrl: String,
    private var token: String? = null
) {
    fun setToken(value: String?) { token = value }

    suspend fun login(email: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email.trim()).put("password", password)
        val json = request("POST", "login", body, requireAuth = false)
        val user = json.getJSONObject("user")
        LoginResult(
            token = json.getString("token"),
            name = user.optString("name"),
            email = user.optString("email"),
            role = user.optString("role", "staff")
        )
    }

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        request("GET", "health", null, requireAuth = false).optBoolean("ok", false)
    }

    suspend fun snapshot(): CloudSnapshot = withContext(Dispatchers.IO) {
        val json = request("GET", "sync")
        val leadsJson = json.optJSONArray("leads") ?: JSONArray()
        val usersJson = json.optJSONArray("telecallers") ?: JSONArray()
        val leads = buildList {
            for (i in 0 until leadsJson.length()) add(parseLead(leadsJson.getJSONObject(i)))
        }
        val callers = buildList {
            for (i in 0 until usersJson.length()) {
                val o = usersJson.getJSONObject(i)
                add(TelecallerEntity(
                    id = o.optInt("id"),
                    name = o.optString("name"),
                    email = o.optString("email"),
                    phone = o.optString("phone"),
                    status = o.optString("status", "Available"),
                    accessLevel = o.optString("accessLevel", "Full Edit"),
                    isAuthorized = o.optBoolean("isAuthorized", true),
                    assignedCount = o.optInt("assignedCount", 0),
                    lastAssignedTimestamp = o.optLong("lastAssignedTimestamp", 0L),
                    lastActiveSession = o.optLong("lastActiveSession", 0L)
                ))
            }
        }
        CloudSnapshot(leads, callers)
    }

    suspend fun createLead(lead: LeadEntity): LeadEntity = withContext(Dispatchers.IO) {
        val json = request("POST", "lead", leadToJson(lead))
        parseLead(json.getJSONObject("lead"))
    }

    suspend fun updateLead(lead: LeadEntity): LeadEntity = withContext(Dispatchers.IO) {
        val json = request("PUT", "lead&id=${lead.id}", leadToJson(lead))
        parseLead(json.getJSONObject("lead"))
    }

    suspend fun deleteLead(id: Int) = withContext(Dispatchers.IO) {
        request("DELETE", "lead&id=$id")
        Unit
    }

    suspend fun addActivity(leadId: Int, type: String, content: String) = withContext(Dispatchers.IO) {
        request("POST", "activity", JSONObject()
            .put("leadId", leadId)
            .put("type", type)
            .put("content", content))
        Unit
    }

    private fun statusToServer(status: String): String = when (status.lowercase()) {
        "new", "fresh" -> "fresh"
        "contacted" -> "contacted"
        "interested", "qualified" -> "qualified"
        "converted" -> "converted"
        "follow-up", "follow_up" -> "follow_up"
        "junk" -> "junk"
        "spam" -> "spam"
        else -> "lost"
    }

    private fun statusFromServer(status: String): String = when (status.lowercase()) {
        "fresh" -> "New"
        "contacted" -> "Contacted"
        "qualified" -> "Interested"
        "converted" -> "Converted"
        "follow_up" -> "Contacted"
        "junk", "spam", "lost" -> "Lost"
        else -> "New"
    }

    private fun sourceToServer(source: String): String = when {
        source.contains("facebook", true) -> "facebook"
        source.contains("whatsapp", true) -> "whatsapp"
        source.contains("website", true) -> "website"
        source.contains("import", true) -> "import"
        else -> "manual"
    }

    private fun sourceFromServer(source: String): String = when (source.lowercase()) {
        "facebook" -> "Facebook Ads"
        "whatsapp" -> "WhatsApp"
        "website" -> "Website"
        "import" -> "Import"
        else -> "Manual"
    }

    private fun leadToJson(lead: LeadEntity) = JSONObject()
        .put("id", lead.id)
        .put("name", lead.name)
        .put("phone", lead.phone)
        .put("email", lead.email)
        .put("status", statusToServer(lead.status))
        .put("source", sourceToServer(lead.source))
        .put("company", lead.company)
        .put("assignedCaller", lead.assignedCaller)
        .put("lastContacted", lead.lastContacted)
        .put("scheduledFollowUp", lead.scheduledFollowUp ?: JSONObject.NULL)
        .put("sentiment", lead.sentiment)
        .put("tagIds", lead.tagIds)

    private fun parseLead(o: JSONObject) = LeadEntity(
        id = o.optInt("id"),
        name = o.optString("name"),
        phone = o.optString("phone"),
        email = o.optString("email"),
        status = statusFromServer(o.optString("status")),
        source = sourceFromServer(o.optString("source")),
        company = o.optString("company"),
        lastContacted = o.optLong("lastContacted", System.currentTimeMillis()),
        assignedCaller = o.optString("assignedCaller", "Unassigned"),
        scheduledFollowUp = if (o.isNull("scheduledFollowUp")) null else o.optLong("scheduledFollowUp"),
        sentiment = o.optString("sentiment", "Neutral"),
        tagIds = o.optString("tagIds")
    )

    private fun endpoint(actionAndQuery: String): URL {
        val separator = if (baseUrl.contains("?")) "&" else "?"
        return URL(baseUrl.trimEnd('/') + separator + "action=" + actionAndQuery)
    }

    private fun request(method: String, actionAndQuery: String, body: JSONObject? = null, requireAuth: Boolean = true): JSONObject {
        val conn = endpoint(actionAndQuery).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        if (requireAuth) {
            val currentToken = token ?: throw CloudApiException("Please log in again.")
            conn.setRequestProperty("Authorization", "Bearer $currentToken")
        }
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        val json = try { if (text.isBlank()) JSONObject() else JSONObject(text) }
        catch (_: Exception) { JSONObject().put("message", text.ifBlank { "Server returned HTTP $code" }) }
        if (code !in 200..299 || !json.optBoolean("ok", false)) {
            throw CloudApiException(json.optString("message", "Server request failed ($code)"))
        }
        return json
    }
}
