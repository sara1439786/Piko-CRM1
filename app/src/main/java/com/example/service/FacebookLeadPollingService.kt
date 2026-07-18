package com.example.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.room.Room
import com.example.database.CRMDatabase
import com.example.database.FacebookAdLeadEntity
import com.example.database.LeadEntity
import com.example.database.SyncLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FacebookLeadPollingService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var database: CRMDatabase

    companion object {
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private val _lastPollTimestamp = MutableStateFlow(0L)
        val lastPollTimestamp = _lastPollTimestamp.asStateFlow()

        private val _leadsPolledTotal = MutableStateFlow(0)
        val leadsPolledTotal = _leadsPolledTotal.asStateFlow()

        private val _recentLogs = MutableStateFlow<List<String>>(emptyList())
        val recentLogs = _recentLogs.asStateFlow()

        fun addLog(message: String) {
            val current = _recentLogs.value.toMutableList()
            current.add(0, "[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(System.currentTimeMillis())}] $message")
            if (current.size > 20) {
                current.removeAt(current.size - 1)
            }
            _recentLogs.value = current
        }
    }

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            CRMDatabase::class.java, "crm_telecall_db"
        )
            .fallbackToDestructiveMigration()
            .build()
        
        _isServiceRunning.value = true
        addLog("Facebook Polling Service initialized.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        addLog("Facebook Polling Service started running in background.")
        startPolling()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        _isServiceRunning.value = false
        addLog("Facebook Polling Service stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startPolling() {
        serviceScope.launch {
            val crmDao = database.crmDao()

            while (_isServiceRunning.value) {
                // Wait for the polling interval (e.g. 15 seconds)
                delay(15000)

                if (!_isServiceRunning.value) break

                _lastPollTimestamp.value = System.currentTimeMillis()
                addLog("Polling Facebook Lead Ads API...")

                try {
                    val settings = crmDao.getAdminSettings().first()
                    val pageId = settings?.fbPageId ?: ""
                    val accessToken = settings?.fbPageAccessToken ?: ""

                    if (pageId.isBlank() || accessToken.isBlank()) {
                        addLog("Poll complete: Facebook integration credentials not configured yet.")
                        continue
                    }

                    addLog("Polling Facebook Lead Ads API for Page: $pageId...")
                    val urlStr = "https://graph.facebook.com/v18.0/$pageId/leads?fields=id,created_time,field_data,form_id,ad_id,ad_name&access_token=$accessToken"
                    val url = java.net.URL(urlStr)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.setRequestProperty("Accept", "application/json")

                    val responseCode = conn.responseCode
                    if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = org.json.JSONObject(responseText)
                        val dataArray = json.optJSONArray("data")
                        
                        var newLeadsCount = 0
                        if (dataArray != null && dataArray.length() > 0) {
                            for (i in 0 until dataArray.length()) {
                                val leadObj = dataArray.getJSONObject(i)
                                val leadId = leadObj.optString("id")
                                
                                // Parse field_data
                                var leadName = ""
                                var leadPhone = ""
                                var leadEmail = ""
                                val fieldData = leadObj.optJSONArray("field_data")
                                if (fieldData != null) {
                                    for (j in 0 until fieldData.length()) {
                                        val field = fieldData.getJSONObject(j)
                                        val fieldName = field.optString("name").lowercase()
                                        val valuesArray = field.optJSONArray("values")
                                        val value = if (valuesArray != null && valuesArray.length() > 0) valuesArray.getString(0) else ""
                                        if (value.isNotEmpty()) {
                                            if (fieldName.contains("name") || fieldName.contains("client") || fieldName.contains("customer")) {
                                                if (leadName.isEmpty() || fieldName == "full_name" || fieldName == "name") {
                                                    leadName = value
                                                }
                                            } else if (fieldName.contains("phone") || fieldName.contains("contact") || fieldName.contains("number") || fieldName.contains("mobile")) {
                                                leadPhone = value
                                            } else if (fieldName.contains("email") || fieldName.contains("mail")) {
                                                leadEmail = value
                                            }
                                        }
                                    }
                                }
                                
                                if (leadName.isEmpty()) {
                                    leadName = "FB Lead $leadId"
                                }
                                if (leadPhone.isEmpty()) {
                                    // Skip leads with no contact details
                                    continue
                                }
                                
                                // Check duplicate in DB
                                val isDuplicate = crmDao.checkFacebookLeadExists(leadName, leadPhone) > 0
                                if (!isDuplicate) {
                                    val formName = leadObj.optString("form_id", "Form $leadId")
                                    val adName = leadObj.optString("ad_name", leadObj.optString("ad_id", "Facebook Ad Campaign"))
                                    
                                    val fbLead = FacebookAdLeadEntity(
                                        adName = adName,
                                        formName = formName,
                                        leadName = leadName,
                                        leadPhone = leadPhone,
                                        leadEmail = leadEmail,
                                        submittedTimestamp = System.currentTimeMillis(),
                                        syncStatus = "Pending"
                                    )
                                    
                                    val insertedId = crmDao.insertFacebookLead(fbLead)
                                    newLeadsCount++
                                    _leadsPolledTotal.value = _leadsPolledTotal.value + 1
                                    addLog("API Ingested 1 new lead from Ad Form: '$leadName'")
                                    
                                    crmDao.insertSyncLog(
                                        SyncLogEntity(
                                            service = "Facebook Polling Service",
                                            timestamp = System.currentTimeMillis(),
                                            status = "Success",
                                            details = "API Polled & Ingested lead: '$leadName' from Ad Form: '$formName'"
                                        )
                                    )
                                    
                                    // Check sync rules to auto-distribute
                                    val rules = crmDao.getAllSyncRules().first()
                                    val isAutoSyncActive = rules.find { it.ruleName == "Facebook Lead Capture Sync" }?.isActive ?: true
                                    
                                    if (isAutoSyncActive) {
                                        val insertedFbLead = fbLead.copy(id = insertedId.toInt())
                                        
                                        // Perform Round-Robin Routing
                                        val nextCaller = crmDao.getNextAvailableTelecaller()
                                        val assignedCallerName = nextCaller?.name ?: "Unassigned"
                                        
                                        if (nextCaller != null) {
                                            crmDao.updateTelecaller(
                                                nextCaller.copy(
                                                    assignedCount = nextCaller.assignedCount + 1,
                                                    lastAssignedTimestamp = System.currentTimeMillis()
                                                )
                                            )
                                        }
                                        
                                        val newLead = LeadEntity(
                                            name = insertedFbLead.leadName,
                                            phone = insertedFbLead.leadPhone,
                                            email = insertedFbLead.leadEmail,
                                            status = "New",
                                            source = "Facebook Ads",
                                            company = "FB Lead Org",
                                            lastContacted = insertedFbLead.submittedTimestamp,
                                            assignedCaller = assignedCallerName
                                        )
                                        
                                        crmDao.insertLead(newLead)
                                        crmDao.markFacebookLeadSynced(insertedFbLead.id)
                                        
                                        crmDao.insertSyncLog(
                                            SyncLogEntity(
                                                service = "Facebook Polling Service",
                                                timestamp = System.currentTimeMillis(),
                                                status = "Success",
                                                details = "Auto-Synced lead '${insertedFbLead.leadName}' via Round-Robin. Assigned to: $assignedCallerName"
                                            )
                                        )
                                        
                                        if (nextCaller != null) {
                                            crmDao.insertSyncLog(
                                                SyncLogEntity(
                                                    service = "Round-Robin Router",
                                                    timestamp = System.currentTimeMillis(),
                                                    status = "Success",
                                                    details = "Polled Lead '${insertedFbLead.leadName}' auto-routed to ${nextCaller.name} (Load: ${nextCaller.assignedCount + 1})"
                                                )
                                            )
                                            addLog("Auto-routed '${insertedFbLead.leadName}' to ${nextCaller.name}")
                                        } else {
                                            crmDao.insertSyncLog(
                                                SyncLogEntity(
                                                    service = "Round-Robin Router",
                                                    timestamp = System.currentTimeMillis(),
                                                    status = "Warning",
                                                    details = "No available telecallers found. Polled Lead '${insertedFbLead.leadName}' left Unassigned."
                                                )
                                            )
                                            addLog("No telecaller available for '${insertedFbLead.leadName}' - left Unassigned")
                                        }
                                    }
                                }
                            }
                        }
                        if (newLeadsCount == 0) {
                            addLog("Poll complete: No new leads found on Facebook server.")
                        } else {
                            addLog("Poll complete: Ingested $newLeadsCount new leads.")
                        }
                    } else {
                        val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        var errMsg = "HTTP error $responseCode"
                        if (errorText.isNotEmpty()) {
                            try {
                                val errJson = org.json.JSONObject(errorText)
                                val errorObj = errJson.getJSONObject("error")
                                errMsg = errorObj.getString("message")
                            } catch (e: Exception) {
                                // use raw errorText
                            }
                        }
                        addLog("Error polling Facebook: $errMsg")
                        crmDao.insertSyncLog(
                            SyncLogEntity(
                                service = "Facebook Polling Service",
                                timestamp = System.currentTimeMillis(),
                                status = "Failed",
                                details = "Facebook Graph API returned error: $errMsg (HTTP $responseCode)"
                            )
                        )
                    }
                } catch (e: Exception) {
                    addLog("Error during polling: ${e.message}")
                    crmDao.insertSyncLog(
                        SyncLogEntity(
                            service = "Facebook Polling Service",
                            timestamp = System.currentTimeMillis(),
                            status = "Failed",
                            details = "Polling error: ${e.message}"
                        )
                    )
                }
            }
        }
    }
}
