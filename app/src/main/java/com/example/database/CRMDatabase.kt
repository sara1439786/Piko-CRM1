package com.example.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// --- ENTITIES ---

@Entity(tableName = "leads")
data class LeadEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String,
    val email: String,
    val status: String, // "New", "Contacted", "Interested", "Converted", "Lost"
    val source: String, // "Manual", "Facebook Ads", "WhatsApp", "CRM Hub"
    val company: String,
    val lastContacted: Long = System.currentTimeMillis(),
    val assignedCaller: String = "Unassigned",
    val scheduledFollowUp: Long? = null,
    val sentiment: String = "Neutral",
    val tagIds: String = "" // Comma-separated list of Tag IDs or names
)

@Entity(tableName = "custom_tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val colorHex: String // e.g., "#E11D48" for deep red or "#10B981" for emerald
)

@Entity(tableName = "call_records")
data class CallRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val leadId: Int,
    val callerName: String,
    val durationSeconds: Int,
    val timestamp: Long,
    val audioUrl: String, // Mock file name or path
    val notes: String,    // User annotation
    val transcript: String, // Speech-to-text simulation
    val recordingStatus: String // "Recorded", "Uploading", "Synced"
)

@Entity(tableName = "whatsapp_messages")
data class WhatsAppMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val leadId: Int,
    val sender: String, // "Lead", "Agent"
    val messageText: String,
    val timestamp: Long
)

@Entity(tableName = "whatsapp_templates")
data class WhatsAppTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val text: String
)

@Entity(tableName = "facebook_leads")
data class FacebookAdLeadEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val adName: String,
    val formName: String,
    val leadName: String,
    val leadPhone: String,
    val leadEmail: String,
    val submittedTimestamp: Long,
    val syncStatus: String // "Pending", "Synced"
)

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val service: String, // "Facebook", "WhatsApp", "Salesforce Sync"
    val timestamp: Long,
    val status: String, // "Success", "Failed"
    val details: String
)

@Entity(tableName = "fb_diagnostic_logs")
data class FacebookDiagnosticLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val endpoint: String,
    val method: String,
    val category: String,
    val httpStatus: Int,
    val responseMessage: String,
    val isSuccess: Boolean,
    val tokenSample: String
)

@Entity(tableName = "sync_rules")
data class SyncRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ruleName: String,
    val sourceService: String,
    val targetService: String,
    val triggerEvent: String,
    val isActive: Boolean
)

@Entity(tableName = "telecallers")
data class TelecallerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val status: String, // "Available", "Busy", "Offline"
    val assignedCount: Int = 0,
    val lastAssignedTimestamp: Long = 0L,
    val isAuthorized: Boolean = true,
    val email: String = "",
    val phone: String = "",
    val accessLevel: String = "Full Edit", // "Full Edit" or "Read-Only"
    val lastActiveSession: Long = 0L
)

@Entity(tableName = "admin_settings")
data class AdminSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val fbPageAccessToken: String = "",
    val fbFormId: String = "",
    val fbAppId: String = "",
    val fbAppSecret: String = "",
    val whatsappBusinessPhoneNumberId: String = "",
    val whatsappAccessToken: String = "",
    val fbPageId: String = "",
    val fbPageName: String = "",
    val fbWebhookActive: Boolean = false,
    val fbWebhookVerifyToken: String = "",
    val geminiApiKey: String = ""
)

@Entity(tableName = "reminder_rules")
data class ReminderRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val timeframeHours: Int,
    val targetStatus: String, // "All", "New", "Contacted", "Interested", "Converted", "Lost"
    val isEnabled: Boolean = true
)

@Entity(tableName = "dismissed_reminders")
data class DismissedReminderEntity(
    @PrimaryKey val leadId: Int,
    val dismissedAt: Long
)

// --- DAO ---

@Dao
interface CRMDao {
    // Leads
    @Query("SELECT * FROM leads ORDER BY lastContacted DESC")
    fun getAllLeads(): Flow<List<LeadEntity>>

    @Query("SELECT * FROM leads WHERE id = :id")
    fun getLeadById(id: Int): Flow<LeadEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLead(lead: LeadEntity): Long

    @Update
    suspend fun updateLead(lead: LeadEntity)

    @Query("DELETE FROM leads WHERE id = :id")
    suspend fun deleteLeadById(id: Int)

    @Query("DELETE FROM leads")
    suspend fun clearLeads()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeads(leads: List<LeadEntity>)

    // Call Records
    @Query("SELECT * FROM call_records ORDER BY timestamp DESC")
    fun getAllCallRecords(): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_records WHERE leadId = :leadId ORDER BY timestamp DESC")
    fun getCallRecordsForLead(leadId: Int): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_records WHERE leadId = :leadId ORDER BY timestamp DESC")
    suspend fun getCallRecordsForLeadOnce(leadId: Int): List<CallRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallRecord(record: CallRecordEntity): Long

    @Update
    suspend fun updateCallRecord(record: CallRecordEntity)

    @Query("DELETE FROM call_records WHERE id = :id")
    suspend fun deleteCallRecordById(id: Int)

    // WhatsApp
    @Query("SELECT * FROM whatsapp_messages ORDER BY timestamp DESC")
    fun getAllWhatsAppMessages(): Flow<List<WhatsAppMessageEntity>>

    @Query("SELECT * FROM whatsapp_messages WHERE leadId = :leadId ORDER BY timestamp ASC")
    fun getWhatsAppMessagesForLead(leadId: Int): Flow<List<WhatsAppMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWhatsAppMessage(message: WhatsAppMessageEntity)

    @Query("SELECT * FROM whatsapp_templates ORDER BY id ASC")
    fun getAllWhatsAppTemplates(): Flow<List<WhatsAppTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWhatsAppTemplate(template: WhatsAppTemplateEntity): Long

    @Update
    suspend fun updateWhatsAppTemplate(template: WhatsAppTemplateEntity)

    @Query("DELETE FROM whatsapp_templates WHERE id = :id")
    suspend fun deleteWhatsAppTemplateById(id: Int)

    // Facebook Leads
    @Query("SELECT * FROM facebook_leads ORDER BY submittedTimestamp DESC")
    fun getAllFacebookLeads(): Flow<List<FacebookAdLeadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFacebookLead(lead: FacebookAdLeadEntity): Long

    @Query("UPDATE facebook_leads SET syncStatus = 'Synced' WHERE id = :id")
    suspend fun markFacebookLeadSynced(id: Int)

    @Query("SELECT COUNT(*) FROM facebook_leads WHERE leadPhone = :phone AND leadName = :name")
    suspend fun checkFacebookLeadExists(name: String, phone: String): Int

    // Sync Logs
    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC")
    fun getAllSyncLogs(): Flow<List<SyncLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncLog(log: SyncLogEntity)

    @Query("DELETE FROM sync_logs")
    suspend fun clearAllSyncLogs()

    // Sync Rules
    @Query("SELECT * FROM sync_rules")
    fun getAllSyncRules(): Flow<List<SyncRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncRule(rule: SyncRuleEntity)

    @Update
    suspend fun updateSyncRule(rule: SyncRuleEntity)

    // Telecallers
    @Query("SELECT * FROM telecallers ORDER BY id ASC")
    fun getAllTelecallers(): Flow<List<TelecallerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelecaller(telecaller: TelecallerEntity)

    @Update
    suspend fun updateTelecaller(telecaller: TelecallerEntity)

    @Query("SELECT * FROM telecallers WHERE status = 'Available' ORDER BY lastAssignedTimestamp ASC, id ASC LIMIT 1")
    suspend fun getNextAvailableTelecaller(): TelecallerEntity?

    @Query("DELETE FROM telecallers")
    suspend fun clearTelecallers()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelecallers(items: List<TelecallerEntity>)

    // Reminder Rules
    @Query("SELECT * FROM reminder_rules ORDER BY id ASC")
    fun getAllReminderRules(): Flow<List<ReminderRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminderRule(rule: ReminderRuleEntity): Long

    @Update
    suspend fun updateReminderRule(rule: ReminderRuleEntity)

    @Query("DELETE FROM reminder_rules WHERE id = :id")
    suspend fun deleteReminderRuleById(id: Int)

    // Dismissed Reminders
    @Query("SELECT * FROM dismissed_reminders")
    fun getAllDismissedReminders(): Flow<List<DismissedReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDismissedReminder(dismissed: DismissedReminderEntity)

    @Query("DELETE FROM dismissed_reminders WHERE leadId = :leadId")
    suspend fun deleteDismissedReminderByLeadId(leadId: Int)

    @Query("DELETE FROM dismissed_reminders")
    suspend fun clearAllDismissedReminders()

    // Admin Settings Queries
    @Query("SELECT * FROM admin_settings WHERE id = 1")
    fun getAdminSettings(): Flow<AdminSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdminSettings(settings: AdminSettingsEntity)

    // Facebook Graph API Diagnostic Log Queries
    @Query("SELECT * FROM fb_diagnostic_logs ORDER BY timestamp DESC")
    fun getAllFacebookDiagnosticLogs(): Flow<List<FacebookDiagnosticLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFacebookDiagnosticLog(log: FacebookDiagnosticLogEntity): Long

    @Query("DELETE FROM fb_diagnostic_logs")
    suspend fun clearFacebookDiagnosticLogs()
}

// --- DATABASE ---

@Database(
    entities = [
        LeadEntity::class,
        CallRecordEntity::class,
        WhatsAppMessageEntity::class,
        WhatsAppTemplateEntity::class,
        FacebookAdLeadEntity::class,
        SyncLogEntity::class,
        SyncRuleEntity::class,
        TelecallerEntity::class,
        ReminderRuleEntity::class,
        DismissedReminderEntity::class,
        AdminSettingsEntity::class,
        FacebookDiagnosticLogEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class CRMDatabase : RoomDatabase() {
    abstract fun crmDao(): CRMDao
}
