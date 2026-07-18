package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.CallRecordEntity
import com.example.database.FacebookAdLeadEntity
import com.example.database.LeadEntity
import com.example.database.SyncRuleEntity
import com.example.database.WhatsAppMessageEntity
import com.example.database.ReminderRuleEntity
import com.example.database.AdminSettingsEntity
import com.example.database.FacebookDiagnosticLogEntity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontStyle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- THEME COLOR PALETTE (Cosmic Slate Theme) ---
val CosmicBackground = Color(0xFF0F172A) // Deep Slate Navy
val CosmicSurface = Color(0xFF1E293B)     // Dark Slate Gray
val CosmicSurfaceVariant = Color(0xFF334155) // Medium Slate Gray
val CosmicPrimary = Color(0xFF3B82F6)     // Electric Blue
val CosmicSecondary = Color(0xFF10B981)   // Bright Emerald
val CosmicAccent = Color(0xFF8B5CF6)      // Bright Purple
val CosmicTextPrimary = Color(0xFFF8FAFC) // Ice White
val CosmicTextSecondary = Color(0xFF94A3B8) // Slate Gray

fun launchRealCall(context: android.content.Context, phone: String, viewModel: CRMViewModel, lead: com.example.database.LeadEntity) {
    val mode = viewModel.callingMode.value
    val sanitizedPhone = phone.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")

    // Seamlessly link any outbound cellular/SIM call to our in-app ongoing call tracking and transcription overlay!
    viewModel.startCall(lead)

    if (mode == "Pure In-App Simulation") {
        // Only run virtual in-app ongoing call simulation, do not trigger external phone intents
        return
    }

    try {
        if (mode == "Direct SIM Call (ACTION_CALL)") {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.CALL_PHONE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                // Device SIM direct call
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_CALL,
                    android.net.Uri.parse("tel:$sanitizedPhone")
                ).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                // Fallback to ACTION_DIAL if permission not granted
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_DIAL,
                    android.net.Uri.parse("tel:$sanitizedPhone")
                ).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } else {
            // "Native Prefill Dialer (ACTION_DIAL)"
            val intent = android.content.Intent(
                android.content.Intent.ACTION_DIAL,
                android.net.Uri.parse("tel:$sanitizedPhone")
            ).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_DIAL,
                android.net.Uri.parse("tel:$sanitizedPhone")
            ).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (inner: Exception) {
            inner.printStackTrace()
        }
    }

    viewModel.startCall(lead)
}

fun launchWhatsAppChat(
    context: android.content.Context,
    phone: String,
    messageText: String? = null,
    viewModel: CRMViewModel? = null,
    leadId: Int? = null
) {
    val cleanPhone = phone.replace(Regex("[^0-9]"), "")
    val uriString = if (!messageText.isNullOrBlank()) {
        "https://wa.me/$cleanPhone?text=${android.net.Uri.encode(messageText)}"
    } else {
        "https://wa.me/$cleanPhone"
    }

    if (viewModel != null && leadId != null) {
        viewModel.logWhatsAppInteraction(leadId, messageText ?: "Opened external WhatsApp chat session")
    }

    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uriString)).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Opening in web browser fallback...", android.widget.Toast.LENGTH_SHORT).show()
        try {
            val webIntent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone" + if (!messageText.isNullOrBlank()) "&text=${android.net.Uri.encode(messageText)}" else "")
            ).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        } catch (inner: Exception) {
            inner.printStackTrace()
        }
    }
}

fun exportLeadsToCsv(context: android.content.Context, leadsToExport: List<com.example.database.LeadEntity>) {
    val csvHeader = "ID,Name,Phone,Email,Status,Source,Company,Last Contacted,Assigned Caller,Scheduled Follow-Up,Sentiment\n"
    val csvBody = leadsToExport.joinToString("\n") { lead ->
        val id = lead.id
        val name = escapeCsv(lead.name)
        val phone = escapeCsv(lead.phone)
        val email = escapeCsv(lead.email)
        val status = escapeCsv(lead.status)
        val source = escapeCsv(lead.source)
        val company = escapeCsv(lead.company)
        val lastContacted = try {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(lead.lastContacted))
        } catch (e: Exception) {
            "N/A"
        }
        val assignedCaller = escapeCsv(lead.assignedCaller)
        val scheduledFollowUp = if (lead.scheduledFollowUp != null) {
            try {
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(lead.scheduledFollowUp))
            } catch (e: Exception) {
                "N/A"
            }
        } else {
            "N/A"
        }
        val sentiment = escapeCsv(lead.sentiment)
        "$id,\"$name\",\"$phone\",\"$email\",\"$status\",\"$source\",\"$company\",\"$lastContacted\",\"$assignedCaller\",\"$scheduledFollowUp\",\"$sentiment\""
    }
    val csvText = csvHeader + csvBody

    try {
        // 1. Copy to clipboard
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("CRM Filtered Leads CSV", csvText)
        clipboard.setPrimaryClip(clip)
        
        android.widget.Toast.makeText(context, "CSV copied to clipboard & share sheet opened!", android.widget.Toast.LENGTH_LONG).show()

        // 2. Open Share Chooser
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "CRM_Filtered_Leads_${System.currentTimeMillis()}.csv")
            putExtra(android.content.Intent.EXTRA_TEXT, csvText)
        }
        val chooserIntent = android.content.Intent.createChooser(intent, "Share/Save Filtered Leads CSV").apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(context, "Failed to export: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun exportDashboardMetricsToCsv(
    context: android.content.Context,
    leads: List<com.example.database.LeadEntity>,
    callRecords: List<com.example.database.CallRecordEntity>,
    telecallers: List<com.example.database.TelecallerEntity>,
    viewModel: CRMViewModel
) {
    val totalLeads = leads.size
    val totalConverted = leads.count { it.status == "Converted" }
    val conversionRate = if (totalLeads > 0) (totalConverted.toDouble() / totalLeads * 100) else 0.0
    val callsMadeToday = callRecords.count { 
        val delta = System.currentTimeMillis() - it.timestamp
        delta < 24 * 3600 * 1000 
    } + 5
    val totalPipelineValue = leads.count { it.status != "Lost" } * 250000

    val statusNew = leads.count { it.status == "New" }
    val statusContacted = leads.count { it.status == "Contacted" }
    val statusInterested = leads.count { it.status == "Interested" }
    val statusConverted = totalConverted
    val statusLost = leads.count { it.status == "Lost" }

    val csvBuilder = java.lang.StringBuilder()
    csvBuilder.append("--- CRM DASHBOARD PERFORMANCE METRICS SUMMARY ---\n")
    csvBuilder.append("Export Timestamp,${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")

    csvBuilder.append("Metric,Value\n")
    csvBuilder.append("Total Leads,$totalLeads\n")
    csvBuilder.append("Calls Made Today,$callsMadeToday\n")
    csvBuilder.append("Conversion Rate,${String.format(java.util.Locale.US, "%.1f%%", conversionRate)}\n")
    csvBuilder.append("Total Converted Leads,$totalConverted\n")
    csvBuilder.append("Active Pipeline Worth (INR),Rs. $totalPipelineValue\n\n")

    csvBuilder.append("--- LEAD CONVERSION FUNNEL DISTRIBUTION ---\n")
    csvBuilder.append("Lead Status,Count\n")
    csvBuilder.append("New,$statusNew\n")
    csvBuilder.append("Contacted,$statusContacted\n")
    csvBuilder.append("Interested,$statusInterested\n")
    csvBuilder.append("Converted,$statusConverted\n")
    csvBuilder.append("Lost,$statusLost\n\n")

    csvBuilder.append("--- TELECALLER TEAM PERFORMANCE BREAKDOWN ---\n")
    csvBuilder.append("Telecaller Name,Status,Access Level,Assigned Leads,Calls Made,Conversion Rate\n")
    telecallers.forEach { caller ->
        val report = viewModel.getTelecallerPerformanceReport(caller)
        csvBuilder.append("\"${escapeCsv(caller.name)}\",")
        csvBuilder.append("\"${escapeCsv(caller.status)}\",")
        csvBuilder.append("\"${escapeCsv(caller.accessLevel)}\",")
        csvBuilder.append("${report.leadsAssigned},")
        csvBuilder.append("${report.totalCallsMade},")
        csvBuilder.append("${String.format(java.util.Locale.US, "%.1f%%", report.conversionRatePercent)}\n")
    }

    val csvText = csvBuilder.toString()

    try {
        // 1. Copy to clipboard
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("CRM Dashboard Performance Metrics CSV", csvText)
        clipboard.setPrimaryClip(clip)
        
        android.widget.Toast.makeText(context, "Dashboard metrics CSV copied to clipboard & share sheet opened!", android.widget.Toast.LENGTH_LONG).show()

        // 2. Open Share Chooser
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "CRM_Dashboard_Metrics_${System.currentTimeMillis()}.csv")
            putExtra(android.content.Intent.EXTRA_TEXT, csvText)
        }
        val chooserIntent = android.content.Intent.createChooser(intent, "Share/Save Dashboard Metrics CSV").apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(context, "Failed to export: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun escapeCsv(value: String): String {
    return value.replace("\"", "\"\"")
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CRMScreen(viewModel: CRMViewModel) {
    val currentTab = remember { mutableStateOf("dashboard") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val leads by viewModel.leads.collectAsStateWithLifecycle()
    val leadReminders by viewModel.leadReminders.collectAsStateWithLifecycle()
    val selectedLead by viewModel.selectedLead.collectAsStateWithLifecycle()
    val isCallOngoing by viewModel.isCallOngoing.collectAsStateWithLifecycle()
    val ongoingCallLead by viewModel.ongoingCallLead.collectAsStateWithLifecycle()
    val ongoingCallSeconds by viewModel.ongoingCallSeconds.collectAsStateWithLifecycle()

    val currentRole by viewModel.userRole.collectAsStateWithLifecycle()
    val telecallers by viewModel.telecallers.collectAsStateWithLifecycle()

    val isAutoDialerRunning by viewModel.isAutoDialerRunning.collectAsStateWithLifecycle()
    val callingMode by viewModel.callingMode.collectAsStateWithLifecycle()
    val pendingCallLead by viewModel.pendingCallLead.collectAsStateWithLifecycle()
    val nextCallPromptLead by viewModel.nextCallPromptLead.collectAsStateWithLifecycle()
    val adminSettings by viewModel.adminSettings.collectAsStateWithLifecycle()
    val fbHealthState by viewModel.facebookHealth.collectAsStateWithLifecycle()
    val isCheckingFb by viewModel.isCheckingFbHealth.collectAsStateWithLifecycle()

    var showConnectionDialog by remember { mutableStateOf(false) }

    var hasCallPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CALL_PHONE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCallPermission = isGranted
        }
    )


    // Automatic hook to device's native telephony system for sequential Auto-Dialer calls
    LaunchedEffect(ongoingCallLead) {
        val lead = ongoingCallLead
        if (lead != null && isAutoDialerRunning && callingMode != "Pure In-App Simulation") {
            val sanitizedPhone = lead.phone.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
            try {
                if (callingMode == "Direct SIM Call (ACTION_CALL)") {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.CALL_PHONE
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_CALL,
                            android.net.Uri.parse("tel:$sanitizedPhone")
                        ).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } else {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_DIAL,
                            android.net.Uri.parse("tel:$sanitizedPhone")
                        ).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                } else {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_DIAL,
                        android.net.Uri.parse("tel:$sanitizedPhone")
                    ).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val isAgentSarahRevoked = remember(telecallers, currentRole) {
        val agentSarah = telecallers.find { it.name.contains("Sarah", ignoreCase = true) }
        currentRole == "Agent" && agentSarah != null && !agentSarah.isAuthorized
    }

    val isReadOnly = remember(telecallers, currentRole) {
        val agentSarah = telecallers.find { it.name.contains("Sarah", ignoreCase = true) }
        currentRole == "Agent" && agentSarah?.accessLevel == "Read-Only"
    }

    var showAddLeadDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "CRM Telecaller Suite",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            color = CosmicTextPrimary,
                            fontSize = 18.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            val simActive = hasCallPermission
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CosmicSurface)
                                    .clickable { showConnectionDialog = true }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("status_sim_indicator")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                color = if (simActive) CosmicSecondary else Color(0xFFF59E0B),
                                                shape = CircleShape
                                            )
                                    )
                                    Text(
                                        text = "SIM Calling",
                                        color = if (simActive) CosmicTextPrimary else CosmicTextSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            val waActive = (adminSettings?.whatsappAccessToken?.isNotEmpty() == true) && 
                                           (adminSettings?.whatsappBusinessPhoneNumberId?.isNotEmpty() == true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CosmicSurface)
                                    .clickable { showConnectionDialog = true }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("status_whatsapp_indicator")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                color = if (waActive) CosmicAccent else Color(0xFFF59E0B),
                                                shape = CircleShape
                                            )
                                    )
                                    Text(
                                        text = "WhatsApp API",
                                        color = if (waActive) CosmicTextPrimary else CosmicTextSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            val fbColor = when (fbHealthState.status) {
                                com.example.api.FacebookConnectionStatus.ACTIVE -> Color(0xFF25D366)
                                com.example.api.FacebookConnectionStatus.SIMULATED -> CosmicSecondary
                                com.example.api.FacebookConnectionStatus.INVALID_TOKEN -> Color(0xFFEF4444)
                                com.example.api.FacebookConnectionStatus.OFFLINE -> Color(0xFFF59E0B)
                                com.example.api.FacebookConnectionStatus.UNCONFIGURED -> Color.Gray
                            }
                            val fbText = when (fbHealthState.status) {
                                com.example.api.FacebookConnectionStatus.ACTIVE -> "FB Live"
                                com.example.api.FacebookConnectionStatus.SIMULATED -> "FB Demo"
                                com.example.api.FacebookConnectionStatus.INVALID_TOKEN -> "FB Error"
                                com.example.api.FacebookConnectionStatus.OFFLINE -> "FB Offline"
                                com.example.api.FacebookConnectionStatus.UNCONFIGURED -> "FB Inactive"
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CosmicSurface)
                                    .clickable { showConnectionDialog = true }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("status_facebook_indicator")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                color = fbColor,
                                                shape = CircleShape
                                            )
                                    )
                                    Text(
                                        text = fbText,
                                        color = if (fbHealthState.status != com.example.api.FacebookConnectionStatus.UNCONFIGURED) CosmicTextPrimary else CosmicTextSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    val currentRole by viewModel.userRole.collectAsStateWithLifecycle()
                    var showRoleDropdown by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        Button(
                            onClick = { showRoleDropdown = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentRole == "Admin") CosmicAccent.copy(alpha = 0.2f) else CosmicPrimary.copy(alpha = 0.2f),
                                contentColor = if (currentRole == "Admin") CosmicAccent else CosmicPrimary
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("role_selector_button")
                        ) {
                            Icon(
                                imageVector = if (currentRole == "Admin") Icons.Default.Security else Icons.Default.Person,
                                contentDescription = "Role",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(currentRole, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(12.dp))
                        }

                        DropdownMenu(
                            expanded = showRoleDropdown,
                            onDismissRequest = { showRoleDropdown = false },
                            modifier = Modifier.background(CosmicSurface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Admin (Super Access)", color = CosmicTextPrimary, fontSize = 12.sp) },
                                onClick = {
                                    viewModel.setUserRole("Admin")
                                    showRoleDropdown = false
                                },
                                leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, tint = CosmicAccent, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("select_role_admin")
                            )
                            DropdownMenuItem(
                                text = { Text("Agent (Telecaller Mode)", color = CosmicTextPrimary, fontSize = 12.sp) },
                                onClick = {
                                    viewModel.setUserRole("Agent")
                                    showRoleDropdown = false
                                },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = CosmicPrimary, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("select_role_agent")
                            )
                        }
                    }

                    if (currentTab.value == "calls" || currentTab.value == "kanban") {
                        IconButton(
                            onClick = { showAddLeadDialog = true },
                            modifier = Modifier.testTag("add_lead_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Lead", tint = CosmicPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CosmicBackground,
                    titleContentColor = CosmicTextPrimary,
                    actionIconContentColor = CosmicPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CosmicBackground,
                tonalElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                val tabs = buildList {
                    add(Triple("dashboard", "Dashboard", Icons.Default.BarChart))
                    add(Triple("calls", "Telecaller", Icons.Default.Call))
                    add(Triple("kanban", "Pipeline", Icons.Default.ViewColumn))
                    add(Triple("whatsapp", "WhatsApp", Icons.Default.Send))
                    add(Triple("facebook", "Facebook", Icons.Default.Share))
                    if (currentRole == "Admin") {
                        add(Triple("admin", "Admin Hub", Icons.Default.Settings))
                    }
                }
                tabs.forEach { (route, label, icon) ->
                    val selected = currentTab.value == route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab.value = route },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CosmicPrimary,
                            unselectedIconColor = CosmicTextSecondary,
                            selectedTextColor = CosmicPrimary,
                            unselectedTextColor = CosmicTextSecondary,
                            indicatorColor = CosmicSurfaceVariant
                        ),
                        modifier = Modifier.testTag("tab_$route")
                    )
                }
            }
        },
        containerColor = CosmicBackground
    ) { padding ->
        val toastNotification by viewModel.toastNotification.collectAsStateWithLifecycle()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
            if (isReadOnly) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF59E0B).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.25f))
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Read Only",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "🔒 Read-Only Access: Outbound calling is active, but lead edits and admin settings are restricted. Switch to Admin for full edit rights.",
                            color = Color(0xFFF59E0B),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (isAgentSarahRevoked) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CosmicBackground)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth().widthIn(max = 450.dp),
                            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(Color(0xFFEF4444).copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Access Revoked",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                
                                Text(
                                    text = "Agent Access Suspended",
                                    color = CosmicTextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Text(
                                    text = "Your agent account (Agent Sarah) has been suspended by the administrator.\n\nAll outbound dialing, lead tracking, and CRM synchronization are locked until access is re-authorized.",
                                    color = CosmicTextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Button(
                                    onClick = { viewModel.setUserRole("Admin") },
                                    colors = ButtonDefaults.buttonColors(containerColor = CosmicAccent),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("bypass_revoked_role_button")
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Switch to Admin Role", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                } else {
                    when (currentTab.value) {
                        "dashboard" -> DashboardTab(viewModel, onNavigateToTab = { currentTab.value = it })
                        "calls" -> TelecallerTab(viewModel, leads, selectedLead, showAddLeadDialog, onDismissDialog = { showAddLeadDialog = false })
                        "kanban" -> PipelineKanbanTab(viewModel, leads, showAddLeadDialog, onDismissDialog = { showAddLeadDialog = false })
                        "whatsapp" -> WhatsAppTab(viewModel, leads, selectedLead)
                        "facebook" -> FacebookLeadsTab(viewModel)
                        "admin" -> AdminHubTab(viewModel, onTabNavigate = { currentTab.value = it })
                        else -> DashboardTab(viewModel, onNavigateToTab = { currentTab.value = it })
                    }
                }

                // Global ongoing phone call overlay (Draggable & Floating!)
                if (isCallOngoing && ongoingCallLead != null) {
                    FloatingCallTimer(
                        viewModel = viewModel,
                        lead = ongoingCallLead!!,
                        seconds = ongoingCallSeconds,
                        onEndCall = { notes -> viewModel.endCallAndSave(ongoingCallLead!!, notes) },
                        onCancelCall = { viewModel.cancelCall() }
                    )
                }

                if (pendingCallLead != null) {
                    CallIntegrationPromptDialog(
                        lead = pendingCallLead!!,
                        onDismiss = { viewModel.dismissCallPrompt() },
                        onSelectMode = { mode ->
                            viewModel.dismissCallPrompt()
                            viewModel.setCallingMode(mode)
                            launchRealCall(context, pendingCallLead!!.phone, viewModel, pendingCallLead!!)
                        }
                    )
                }

                if (nextCallPromptLead != null) {
                    NextCallPromptDialog(
                        lead = nextCallPromptLead!!,
                        onDismiss = { viewModel.dismissNextCallPrompt() },
                        onDial = { viewModel.dialPromptedLead(nextCallPromptLead!!) },
                        onSkip = { viewModel.skipPromptedLead(nextCallPromptLead!!) },
                        onStopQueue = { viewModel.stopAutoDialer(); viewModel.dismissNextCallPrompt() }
                    )
                }

                if (showConnectionDialog) {
                    ConnectionStatusDialog(
                        hasCallPermission = hasCallPermission,
                        callingMode = callingMode,
                        whatsappAccessToken = adminSettings?.whatsappAccessToken ?: "",
                        whatsappBusinessId = adminSettings?.whatsappBusinessPhoneNumberId ?: "",
                        facebookHealth = fbHealthState,
                        isCheckingFbHealth = isCheckingFb,
                        onReVerifyFacebook = { viewModel.verifyFacebookTokenHealth() },
                        onRequestPermission = {
                            requestPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
                        },
                        onNavigateToSettings = {
                            currentTab.value = "admin"
                            showConnectionDialog = false
                        },
                        showSettingsButton = currentRole == "Admin",
                        onDismiss = { showConnectionDialog = false }
                    )
                }
            }

            // Global elegant toast notification overlay
            toastNotification?.let { notification ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    contentAlignment = Alignment.TopCenter
                ) {
                    GlobalToastNotification(
                        notification = notification,
                        onDismiss = { viewModel.dismissToast() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionStatusDialog(
    hasCallPermission: Boolean,
    callingMode: String,
    whatsappAccessToken: String,
    whatsappBusinessId: String,
    facebookHealth: com.example.api.FacebookHealthResult,
    isCheckingFbHealth: Boolean,
    onReVerifyFacebook: () -> Unit,
    onRequestPermission: () -> Unit,
    onNavigateToSettings: () -> Unit,
    showSettingsButton: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CosmicPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Connection Console",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = CosmicTextPrimary
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = CosmicTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = CosmicSurfaceVariant.copy(alpha = 0.5f))

                // 1. SIM Calling Service Status
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = CosmicSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "SIM Calling Service",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = CosmicTextPrimary
                            )
                        }
                        
                        // Status badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (hasCallPermission) CosmicSecondary.copy(alpha = 0.15f)
                                    else Color(0xFFF59E0B).copy(alpha = 0.15f)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (hasCallPermission) "ACTIVE & READY" else "NEEDS PERMISSION",
                                color = if (hasCallPermission) CosmicSecondary else Color(0xFFF59E0B),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "Allows outbound automatic sequencers and manual dialed calls to route directly via your physical cellular SIM card.",
                        fontSize = 10.5.sp,
                        color = CosmicTextSecondary,
                        lineHeight = 14.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mode: $callingMode",
                            fontSize = 10.sp,
                            color = CosmicTextSecondary,
                            fontWeight = FontWeight.Medium
                        )

                        if (!hasCallPermission) {
                            Button(
                                onClick = onRequestPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp).testTag("grant_call_permission_btn")
                            ) {
                                Text("Grant Permission", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = CosmicSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text("NATIVE TELEPHONY READY", color = CosmicSecondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                HorizontalDivider(color = CosmicSurfaceVariant.copy(alpha = 0.3f))

                // 2. WhatsApp API Status
                val isWhatsAppConfigured = whatsappAccessToken.isNotEmpty() && whatsappBusinessId.isNotEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = CosmicAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "WhatsApp Cloud API",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = CosmicTextPrimary
                            )
                        }
                        
                        // Status badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isWhatsAppConfigured) CosmicAccent.copy(alpha = 0.15f)
                                    else Color(0xFFF59E0B).copy(alpha = 0.15f)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isWhatsAppConfigured) "ACTIVE & SYNCED" else "DEMO / SANDBOX",
                                color = if (isWhatsAppConfigured) CosmicAccent else Color(0xFFF59E0B),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "Connects the local room message database to Meta's developer endpoint to send/receive real-time CRM updates.",
                        fontSize = 10.5.sp,
                        color = CosmicTextSecondary,
                        lineHeight = 14.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isWhatsAppConfigured) "Phone ID: $whatsappBusinessId" else "Using default sandbox connection",
                            fontSize = 10.sp,
                            color = CosmicTextSecondary,
                            fontWeight = FontWeight.Medium
                        )

                        if (!isWhatsAppConfigured && showSettingsButton) {
                            Button(
                                onClick = onNavigateToSettings,
                                colors = ButtonDefaults.buttonColors(containerColor = CosmicAccent),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp).testTag("nav_settings_whatsapp_btn")
                            ) {
                                Text("Setup API", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (isWhatsAppConfigured) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = CosmicAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text("META INTEGRATION LIVE", color = CosmicAccent, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                HorizontalDivider(color = CosmicSurfaceVariant.copy(alpha = 0.3f))

                // 3. Facebook Graph API Status
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = CosmicSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Facebook Graph API",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = CosmicTextPrimary
                            )
                        }

                        // Status badge
                        val fbBadgeText = when (facebookHealth.status) {
                            com.example.api.FacebookConnectionStatus.ACTIVE -> "ACTIVE & VERIFIED"
                            com.example.api.FacebookConnectionStatus.SIMULATED -> "DEMO / SIMULATED"
                            com.example.api.FacebookConnectionStatus.INVALID_TOKEN -> "INVALID TOKEN"
                            com.example.api.FacebookConnectionStatus.OFFLINE -> "OFFLINE / ERROR"
                            com.example.api.FacebookConnectionStatus.UNCONFIGURED -> "UNCONFIGURED"
                        }
                        val fbBadgeBg = when (facebookHealth.status) {
                            com.example.api.FacebookConnectionStatus.ACTIVE -> Color(0xFF25D366).copy(alpha = 0.15f)
                            com.example.api.FacebookConnectionStatus.SIMULATED -> CosmicSecondary.copy(alpha = 0.15f)
                            com.example.api.FacebookConnectionStatus.INVALID_TOKEN -> Color(0xFFEF4444).copy(alpha = 0.15f)
                            com.example.api.FacebookConnectionStatus.OFFLINE -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                            com.example.api.FacebookConnectionStatus.UNCONFIGURED -> CosmicSurfaceVariant.copy(alpha = 0.5f)
                        }
                        val fbBadgeColor = when (facebookHealth.status) {
                            com.example.api.FacebookConnectionStatus.ACTIVE -> Color(0xFF25D366)
                            com.example.api.FacebookConnectionStatus.SIMULATED -> CosmicSecondary
                            com.example.api.FacebookConnectionStatus.INVALID_TOKEN -> Color(0xFFEF4444)
                            com.example.api.FacebookConnectionStatus.OFFLINE -> Color(0xFFF59E0B)
                            com.example.api.FacebookConnectionStatus.UNCONFIGURED -> CosmicTextSecondary
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(fbBadgeBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = fbBadgeText,
                                color = fbBadgeColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "Synchronizes with Facebook Webhook Lead Capture and automatically registers lead submissions.",
                        fontSize = 10.5.sp,
                        color = CosmicTextSecondary,
                        lineHeight = 14.sp
                    )

                    // Message / detail
                    Text(
                        text = facebookHealth.message,
                        fontSize = 10.sp,
                        color = if (facebookHealth.status == com.example.api.FacebookConnectionStatus.INVALID_TOKEN || facebookHealth.status == com.example.api.FacebookConnectionStatus.OFFLINE) Color(0xFFEF4444) else CosmicTextSecondary,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 13.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (facebookHealth.pageId != null) "Page ID: ${facebookHealth.pageId}" else "Webhook Queue Offline",
                            fontSize = 10.sp,
                            color = CosmicTextSecondary,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (facebookHealth.status != com.example.api.FacebookConnectionStatus.UNCONFIGURED) {
                                Button(
                                    onClick = onReVerifyFacebook,
                                    colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp).testTag("reverify_facebook_btn"),
                                    enabled = !isCheckingFbHealth
                                ) {
                                    if (isCheckingFbHealth) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            color = CosmicTextPrimary,
                                            strokeWidth = 1.5.dp
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text("Verify Now", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (facebookHealth.status == com.example.api.FacebookConnectionStatus.UNCONFIGURED && showSettingsButton) {
                                Button(
                                    onClick = onNavigateToSettings,
                                    colors = ButtonDefaults.buttonColors(containerColor = CosmicAccent),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp).testTag("nav_settings_facebook_btn")
                                ) {
                                    Text("Setup API", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("close_connection_status_dialog"),
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicSurfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close Console", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                }
            }
        }
    }
}

@Composable
fun AutoDialerControlPanel(
    viewModel: CRMViewModel,
    leads: List<LeadEntity>
) {
    val isAutoDialerRunning by viewModel.isAutoDialerRunning.collectAsStateWithLifecycle()
    val autoDialLeads by viewModel.autoDialLeads.collectAsStateWithLifecycle()
    val autoDialCurrentIndex by viewModel.autoDialCurrentIndex.collectAsStateWithLifecycle()
    val autoDialCountdown by viewModel.autoDialCountdown.collectAsStateWithLifecycle()
    val autoDialDelaySeconds by viewModel.autoDialDelaySeconds.collectAsStateWithLifecycle()
    val isCallOngoing by viewModel.isCallOngoing.collectAsStateWithLifecycle()
    val ongoingCallSeconds by viewModel.ongoingCallSeconds.collectAsStateWithLifecycle()
    val isPromptNextCallEnabled by viewModel.isPromptNextCallEnabled.collectAsStateWithLifecycle()

    // Filter and Pre-flight state
    var filterStatus by remember { mutableStateOf("All") } // "All", "New", "Interested", "Facebook Ads"

    val filteredLeads = remember(leads, filterStatus) {
        when (filterStatus) {
            "New" -> leads.filter { it.status == "New" }
            "Interested" -> leads.filter { it.status == "Interested" }
            "Facebook Ads" -> leads.filter { it.source == "Facebook Ads" }
            else -> leads
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .testTag("auto_dialer_card"),
        colors = CardDefaults.cardColors(
            containerColor = if (isAutoDialerRunning) CosmicAccent.copy(alpha = 0.08f) else CosmicSurface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp, 
            if (isAutoDialerRunning) CosmicAccent.copy(alpha = 0.6f) else CosmicSurfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isAutoDialerRunning) Icons.Default.PhoneInTalk else Icons.Default.PlayArrow,
                        contentDescription = "Dialer Icon",
                        tint = if (isAutoDialerRunning) CosmicAccent else CosmicPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAutoDialerRunning) "Auto-Dialer Running" else "Power Auto-Dialer Control",
                        color = CosmicTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isAutoDialerRunning) {
                    val progressText = "${autoDialCurrentIndex + 1} / ${autoDialLeads.size}"
                    Box(
                        modifier = Modifier
                            .background(CosmicAccent.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = progressText,
                            color = CosmicAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF10B981).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Ready (${filteredLeads.size})",
                            color = Color(0xFF10B981),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!isAutoDialerRunning) {
                // Pre-flight settings
                Text(
                    text = "Configure targets and sequentially auto-dial from active leads database.",
                    color = CosmicTextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Filters Row
                Text(
                    text = "Target List Filter",
                    color = CosmicTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val filters = listOf("All", "New", "Interested", "Facebook Ads")
                    filters.forEach { filter ->
                        val isSelected = filterStatus == filter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) CosmicPrimary else CosmicSurfaceVariant)
                                .clickable { filterStatus = filter }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("dialer_filter_$filter")
                        ) {
                            Text(
                                text = filter,
                                color = if (isSelected) Color.White else CosmicTextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Adjustable Delay Settings
                Text(
                    text = "Inter-Call Auto-Transition Delay",
                    color = CosmicTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val delayOptions = listOf(2, 3, 5, 8, 12, 15, 20)
                    delayOptions.forEach { secondsOption ->
                        val isSelected = autoDialDelaySeconds == secondsOption
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) CosmicSecondary else CosmicSurfaceVariant)
                                .clickable { viewModel.setAutoDialDelaySeconds(secondsOption) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("dialer_delay_${secondsOption}s")
                        ) {
                            Text(
                                text = "${secondsOption}s",
                                color = if (isSelected) Color.White else CosmicTextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Auto-Prompt Toggle Mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CosmicSurfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Auto-Prompt Next Call",
                            color = CosmicTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Require manual prompt confirmation before initiating the next sequential call in the queue.",
                            color = CosmicTextSecondary,
                            fontSize = 9.sp,
                            lineHeight = 12.sp
                        )
                    }
                    Switch(
                        checked = isPromptNextCallEnabled,
                        onCheckedChange = { viewModel.setPromptNextCallEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CosmicSecondary,
                            checkedTrackColor = CosmicSecondary.copy(alpha = 0.4f),
                            uncheckedThumbColor = CosmicTextSecondary,
                            uncheckedTrackColor = CosmicSurfaceVariant
                        ),
                        modifier = Modifier.testTag("dialer_prompt_next_call_switch")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Queue Preview Timeline
                if (filteredLeads.isNotEmpty()) {
                    Text(
                        text = "Dial Queue Preview (${filteredLeads.size} leads in order)",
                        color = CosmicTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 100.dp)
                            .background(CosmicBackground.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .border(1.dp, CosmicSurfaceVariant, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredLeads) { lead ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = lead.name,
                                            color = CosmicTextPrimary,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${lead.company} • ${lead.phone}",
                                            color = CosmicTextSecondary,
                                            fontSize = 9.sp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                when (lead.status) {
                                                    "New" -> CosmicPrimary.copy(alpha = 0.15f)
                                                    "Interested" -> CosmicSecondary.copy(alpha = 0.15f)
                                                    else -> CosmicTextSecondary.copy(alpha = 0.15f)
                                                },
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = lead.status,
                                            color = when (lead.status) {
                                                "New" -> CosmicPrimary
                                                "Interested" -> CosmicSecondary
                                                else -> CosmicTextSecondary
                                            },
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFEF4444).copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No leads matching this filter status.",
                            color = Color(0xFFEF4444),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Start button
                Button(
                    onClick = { viewModel.startAutoDialer(filteredLeads) },
                    enabled = filteredLeads.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CosmicSecondary,
                        contentColor = Color.White,
                        disabledContainerColor = CosmicSurfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("start_auto_dialer_btn")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Start Sequential Auto-Dialer",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

            } else {
                // Running live dial cockpit
                val total = autoDialLeads.size
                val currentIdx = autoDialCurrentIndex
                val currentLead = if (currentIdx < total) autoDialLeads[currentIdx] else null

                if (currentLead != null) {
                    // Current Target Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CosmicBackground.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .border(1.dp, CosmicAccent.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column {
                                    Text(
                                        text = "CURRENT TARGET",
                                        color = CosmicAccent,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = currentLead.name,
                                        color = CosmicTextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${currentLead.company} • ${currentLead.phone}",
                                        color = CosmicTextSecondary,
                                        fontSize = 10.sp
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .background(CosmicAccent.copy(alpha = 0.15f), CircleShape)
                                        .padding(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneInTalk,
                                        contentDescription = null,
                                        tint = CosmicAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Status Indicator
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                if (isCallOngoing) Color(0xFF10B981) else Color(0xFFF59E0B),
                                                CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isCallOngoing) "Active Connection Live" else "Preparing Dialer Line",
                                        color = CosmicTextPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (isCallOngoing) {
                                    Text(
                                        text = "Duration: ${ongoingCallSeconds}s",
                                        color = Color(0xFF10B981),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Next/Delay timer or Call active phase
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (autoDialCountdown > 0) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    progress = { autoDialCountdown / autoDialDelaySeconds.toFloat() },
                                    modifier = Modifier.size(16.dp),
                                    color = CosmicPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Dialing next in ${autoDialCountdown}s...",
                                    color = CosmicTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = { viewModel.dialNextNow() },
                                colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("dialer_dial_now_btn")
                            ) {
                                Text("Dial Now", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isCallOngoing) {
                                    Text(
                                        text = "📞 Talking with client...",
                                        color = Color(0xFF10B981),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text(
                                        text = "Connecting line...",
                                        color = CosmicTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Button(
                                onClick = { viewModel.skipCurrentAutoDial() },
                                colors = ButtonDefaults.buttonColors(containerColor = CosmicSurfaceVariant),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("dialer_skip_btn")
                            ) {
                                Text("Skip Lead", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                            }
                        }

                        // Stop Dialer Button
                        Button(
                            onClick = { viewModel.stopAutoDialer() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("dialer_stop_btn")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    // Upcoming Leads in running queue timeline (if any are left)
                    val remainingCount = total - (currentIdx + 1)
                    if (remainingCount > 0) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Next in Dialer Queue ($remainingCount remaining)",
                            color = CosmicTextPrimary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .background(CosmicBackground.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .border(1.dp, CosmicSurfaceVariant, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (offset in 1..2) {
                                    val nextIdx = currentIdx + offset
                                    if (nextIdx < total) {
                                        val nextL = autoDialLeads[nextIdx]
                                        Row(
                                            modifier = Modifier
                                                .background(CosmicSurface, RoundedCornerShape(6.dp))
                                                .border(1.dp, CosmicSurfaceVariant, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "${nextIdx + 1}.",
                                                color = CosmicAccent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = nextL.name,
                                                color = CosmicTextPrimary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (offset == 1 && currentIdx + 2 < total) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowForward,
                                                contentDescription = null,
                                                tint = CosmicTextSecondary,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Dialer queue completed state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Queue Completed Successfully!",
                                color = CosmicSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.stopAutoDialer() },
                                colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Reset Dialer Panel", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- TELECALLER / CALL RECORDING MANAGEMENT TAB ---
@Composable
fun TelecallerTab(
    viewModel: CRMViewModel,
    leads: List<LeadEntity>,
    selectedLead: LeadEntity?,
    showAddLeadDialog: Boolean,
    onDismissDialog: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val leadReminders by viewModel.leadReminders.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All") }
    var viewMode by remember { mutableStateOf("list") } // "list" or "table"

    val filteredLeadsForTab = remember(leads, searchQuery, statusFilter) {
        leads.filter { lead ->
            val matchesQuery = lead.name.contains(searchQuery, ignoreCase = true) || 
                               lead.company.contains(searchQuery, ignoreCase = true) || 
                               lead.phone.contains(searchQuery, ignoreCase = true) ||
                               lead.email.contains(searchQuery, ignoreCase = true)
            val matchesStatus = if (statusFilter == "All") true else lead.status.equals(statusFilter, ignoreCase = true)
            matchesQuery && matchesStatus
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompact = maxWidth < 600.dp

        if (isCompact) {
            if (selectedLead == null) {
                // Mobile/Compact View: Leads List ONLY
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Lead Directory",
                                    color = CosmicTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CosmicSurfaceVariant.copy(alpha = 0.3f))
                                        .padding(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (viewMode == "list") CosmicPrimary else Color.Transparent)
                                            .clickable { viewMode = "list" }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .testTag("view_mode_list_compact")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.List,
                                            contentDescription = "List View",
                                            tint = if (viewMode == "list") Color.White else CosmicTextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (viewMode == "table") CosmicPrimary else Color.Transparent)
                                            .clickable { viewMode = "table" }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .testTag("view_mode_table_compact")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ViewColumn,
                                            contentDescription = "Table View",
                                            tint = if (viewMode == "table") Color.White else CosmicTextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            
                            // Export CSV Button
                            Button(
                                onClick = { exportLeadsToCsv(context, filteredLeadsForTab) },
                                colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("export_csv_button_compact")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Export CSV",
                                    tint = CosmicTextPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export CSV", color = CosmicTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search by name, company, phone...", color = CosmicTextSecondary, fontSize = 11.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .testTag("lead_search_input_compact"),
                            textStyle = androidx.compose.ui.text.TextStyle(color = CosmicTextPrimary, fontSize = 12.sp),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CosmicPrimary,
                                unfocusedBorderColor = CosmicSurfaceVariant,
                                focusedContainerColor = CosmicBackground,
                                unfocusedContainerColor = CosmicBackground
                            ),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = CosmicTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = CosmicTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        )

                        // Filter Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val statuses = listOf("All", "New", "Contacted", "Interested", "Converted")
                            statuses.forEach { status ->
                                val isSelected = statusFilter == status
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) CosmicPrimary else CosmicSurfaceVariant)
                                        .clickable { statusFilter = status }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                        .testTag("compact_lead_filter_$status")
                                ) {
                                    Text(
                                        text = status,
                                        color = if (isSelected) Color.White else CosmicTextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        AutoDialerControlPanel(viewModel, leads)
                        if (leads.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = CosmicPrimary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Loading Leads...", color = CosmicTextSecondary, fontSize = 12.sp)
                                }
                            }
                        } else if (filteredLeadsForTab.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = CosmicTextSecondary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No leads match search / filter.", color = CosmicTextSecondary, fontSize = 12.sp)
                                }
                            }
                        } else {
                            if (viewMode == "table") {
                                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                    LeadsDataTable(
                                        leads = filteredLeadsForTab,
                                        selectedLead = null,
                                        onLeadClick = { viewModel.selectLead(it) },
                                        onDeleteLead = { viewModel.deleteLead(it) },
                                        onCallClick = { viewModel.showCallPromptForLead(it) },
                                        onWhatsAppClick = { launchWhatsAppChat(context, it, null, viewModel, null) }
                                    )
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(filteredLeadsForTab) { lead ->
                                        LeadItemCard(
                                            lead = lead,
                                            isSelected = false,
                                            onClick = { viewModel.selectLead(lead) },
                                            onDelete = { viewModel.deleteLead(lead.id) },
                                            onWhatsAppClick = { launchWhatsAppChat(context, lead.phone, null, viewModel, lead.id) },
                                            onCallClick = { viewModel.showCallPromptForLead(lead) },
                                            reminders = leadReminders
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Mobile/Compact View: Detail and Recording Panel with Back Navigation
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.selectLead(null) },
                            modifier = Modifier.testTag("back_to_directory_button")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back to directory", tint = CosmicPrimary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Back to Lead Directory",
                            color = CosmicPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        LeadDetailRecordingPanel(viewModel, selectedLead)
                    }
                }
            }
        } else {
            // Tablet/Wide View: Side-by-Side Dual-Pane layout
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Column: Leads list
                Card(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .padding(start = 12.dp, top = 8.dp, end = 6.dp, bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Lead Directory",
                                    color = CosmicTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CosmicSurfaceVariant.copy(alpha = 0.3f))
                                        .padding(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (viewMode == "list") CosmicPrimary else Color.Transparent)
                                            .clickable { viewMode = "list" }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .testTag("view_mode_list_wide")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.List,
                                            contentDescription = "List View",
                                            tint = if (viewMode == "list") Color.White else CosmicTextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (viewMode == "table") CosmicPrimary else Color.Transparent)
                                            .clickable { viewMode = "table" }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .testTag("view_mode_table_wide")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ViewColumn,
                                            contentDescription = "Table View",
                                            tint = if (viewMode == "table") Color.White else CosmicTextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            
                            // Export CSV Button
                            Button(
                                onClick = { exportLeadsToCsv(context, filteredLeadsForTab) },
                                colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("export_csv_button_wide")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Export CSV",
                                    tint = CosmicTextPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export CSV", color = CosmicTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search by name, company, phone...", color = CosmicTextSecondary, fontSize = 11.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .testTag("lead_search_input_wide"),
                            textStyle = androidx.compose.ui.text.TextStyle(color = CosmicTextPrimary, fontSize = 12.sp),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CosmicPrimary,
                                unfocusedBorderColor = CosmicSurfaceVariant,
                                focusedContainerColor = CosmicBackground,
                                unfocusedContainerColor = CosmicBackground
                            ),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = CosmicTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = CosmicTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        )

                        // Filter Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val statuses = listOf("All", "New", "Contacted", "Interested", "Converted")
                            statuses.forEach { status ->
                                val isSelected = statusFilter == status
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) CosmicPrimary else CosmicSurfaceVariant)
                                        .clickable { statusFilter = status }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                        .testTag("wide_lead_filter_$status")
                                ) {
                                    Text(
                                        text = status,
                                        color = if (isSelected) Color.White else CosmicTextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        AutoDialerControlPanel(viewModel, leads)
                        if (leads.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = CosmicPrimary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Loading Leads...", color = CosmicTextSecondary, fontSize = 12.sp)
                                }
                            }
                        } else if (filteredLeadsForTab.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = CosmicTextSecondary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No leads match search / filter.", color = CosmicTextSecondary, fontSize = 12.sp)
                                }
                            }
                        } else {
                            if (viewMode == "table") {
                                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                    LeadsDataTable(
                                        leads = filteredLeadsForTab,
                                        selectedLead = selectedLead,
                                        onLeadClick = { viewModel.selectLead(it) },
                                        onDeleteLead = { viewModel.deleteLead(it) },
                                        onCallClick = { viewModel.showCallPromptForLead(it) },
                                        onWhatsAppClick = { launchWhatsAppChat(context, it, null, viewModel, null) }
                                    )
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(filteredLeadsForTab) { lead ->
                                        val isSelected = selectedLead?.id == lead.id
                                        LeadItemCard(
                                            lead = lead,
                                            isSelected = isSelected,
                                            onClick = { viewModel.selectLead(lead) },
                                            onDelete = { viewModel.deleteLead(lead.id) },
                                            onWhatsAppClick = { launchWhatsAppChat(context, lead.phone, null, viewModel, lead.id) },
                                            onCallClick = { viewModel.showCallPromptForLead(lead) },
                                            reminders = leadReminders
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Right Column: Detail & Record Management Panel
                Card(
                    modifier = Modifier
                        .weight(1.8f)
                        .fillMaxHeight()
                        .padding(start = 6.dp, top = 8.dp, end = 12.dp, bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (selectedLead == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Phone,
                                    contentDescription = "Select Lead",
                                    tint = CosmicPrimary,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Select a Lead record to initiate outbound tele-calling or access stored call recordings & annotations.",
                                    color = CosmicTextSecondary,
                                    textAlign = TextAlign.Center,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        LeadDetailRecordingPanel(viewModel, selectedLead)
                    }
                }
            }
        }
    }

    if (showAddLeadDialog) {
        AddLeadDialog(
            onDismiss = onDismissDialog,
            onConfirm = { name, phone, email, company, source, status ->
                viewModel.addNewLead(name, phone, email, company, source, status)
                onDismissDialog()
            }
        )
    }
}

@Composable
fun LeadItemCard(
    lead: LeadEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onWhatsAppClick: (() -> Unit)? = null,
    onCallClick: (() -> Unit)? = null,
    reminders: List<LeadReminder> = emptyList()
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("lead_item_${lead.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CosmicPrimary.copy(alpha = 0.2f) else CosmicSurfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    lead.name,
                    color = CosmicTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    lead.company,
                    color = CosmicTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = when (lead.status) {
                                    "Converted" -> CosmicSecondary
                                    "Interested" -> CosmicAccent
                                    "Contacted" -> CosmicPrimary
                                    "Lost" -> Color.Red
                                    else -> CosmicTextSecondary
                                },
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            lead.status,
                            color = CosmicTextPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "via ${lead.source}",
                        color = CosmicTextSecondary,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (sentimentColor, sentimentBg, sentimentLabel) = when (lead.sentiment) {
                        "Positive" -> Triple(Color(0xFF10B981), Color(0xFF10B981).copy(alpha = 0.15f), "Positive")
                        "Negative" -> Triple(Color(0xFFEF4444), Color(0xFFEF4444).copy(alpha = 0.15f), "Negative")
                        else -> Triple(Color(0xFF9CA3AF), Color(0xFF9CA3AF).copy(alpha = 0.15f), "Neutral")
                    }
                    Box(
                        modifier = Modifier
                            .background(sentimentBg, RoundedCornerShape(4.dp))
                            .border(1.dp, sentimentColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(sentimentColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sentiment: $sentimentLabel",
                                color = sentimentColor,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                val upcomingReminder = remember(reminders, lead.id) {
                    reminders.find { it.leadId == lead.id && !it.isCompleted }
                }
                if (upcomingReminder != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val isOverdue = System.currentTimeMillis() > upcomingReminder.timestamp
                    val timeText = getRemainingTimeText(upcomingReminder.timestamp)
                    val reminderPriorityColor = when (upcomingReminder.priority) {
                        "High" -> Color(0xFFEF4444)
                        "Medium" -> Color(0xFFF59E0B)
                        else -> Color(0xFF10B981)
                    }
                    Box(
                        modifier = Modifier
                            .background(CosmicAccent.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                            .border(0.5.dp, CosmicAccent.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("lead_upcoming_reminder_indicator_${lead.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = "Upcoming Scheduled Call",
                                tint = CosmicAccent,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Reminder: ${upcomingReminder.type} ($timeText)",
                                color = CosmicTextPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(reminderPriorityColor, CircleShape)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Assigned Telecaller",
                        tint = CosmicAccent,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Agent: ${lead.assignedCaller}",
                        color = CosmicTextSecondary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                if (onCallClick != null) {
                                    onCallClick()
                                } else {
                                    val sanitizedPhone = lead.phone.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
                                    try {
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_DIAL,
                                            android.net.Uri.parse("tel:$sanitizedPhone")
                                        ).apply {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            .testTag("lead_phone_click_${lead.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Dial Phone",
                            tint = CosmicPrimary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = lead.phone,
                            color = CosmicPrimary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "WhatsApp Chat",
                        tint = Color(0xFF25D366),
                        modifier = Modifier
                            .size(14.dp)
                            .clickable {
                                if (onWhatsAppClick != null) {
                                    onWhatsAppClick()
                                } else {
                                    launchWhatsAppChat(context, lead.phone)
                                }
                            }
                            .testTag("lead_whatsapp_click_${lead.id}")
                    )
                }
                if (lead.scheduledFollowUp != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Alarm,
                            contentDescription = "Scheduled Follow-up",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val formattedDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(lead.scheduledFollowUp))
                        Text(
                            "Follow-up: $formattedDate",
                            color = Color(0xFFF59E0B),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("delete_lead_${lead.id}")
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Lead", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun LeadDetailRecordingPanel(viewModel: CRMViewModel, lead: LeadEntity) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val callRecords by viewModel.leadCallRecords.collectAsStateWithLifecycle()
    val reminderRules by viewModel.reminderRules.collectAsStateWithLifecycle()
    val whatsappMessages by viewModel.leadWhatsAppMessages.collectAsStateWithLifecycle()
    var selectedRecordForAnnotation by remember { mutableStateOf<CallRecordEntity?>(null) }
    var activePanelTab by remember { mutableStateOf("list") } // "list", "call_history", "calendar", or "script"

    var historyFilter by remember { mutableStateOf("All") }
    var showLogInteractionDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CosmicSurfaceVariant)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(lead.name, color = CosmicTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Company: ${lead.company} | ${lead.email}", color = CosmicTextSecondary, fontSize = 11.sp)
                    Text(
                        text = "Phone: ${lead.phone}",
                        color = CosmicPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable {
                                viewModel.showCallPromptForLead(lead)
                            }
                            .testTag("detail_phone_click_${lead.id}")
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val (sentimentColor, sentimentBg, sentimentLabel) = when (lead.sentiment) {
                            "Positive" -> Triple(Color(0xFF10B981), Color(0xFF10B981).copy(alpha = 0.15f), "Positive")
                            "Negative" -> Triple(Color(0xFFEF4444), Color(0xFFEF4444).copy(alpha = 0.15f), "Negative")
                            else -> Triple(Color(0xFF9CA3AF), Color(0xFF9CA3AF).copy(alpha = 0.15f), "Neutral")
                        }
                        Box(
                            modifier = Modifier
                                .background(sentimentBg, RoundedCornerShape(4.dp))
                                .border(1.dp, sentimentColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(sentimentColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Sentiment: $sentimentLabel",
                                    color = sentimentColor,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Re-analyze Button with Gemini Sparkles
                        Box(
                            modifier = Modifier
                                .background(CosmicSurfaceVariant, RoundedCornerShape(4.dp))
                                .border(1.dp, CosmicAccent.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .clickable { viewModel.analyzeLeadSentimentWithGemini(lead) }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .testTag("analyze_sentiment_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Gemini",
                                    tint = CosmicAccent,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Analyze Sentiment",
                                    color = CosmicTextPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { launchWhatsAppChat(context, lead.phone, null, viewModel, lead.id) },
                        modifier = Modifier
                            .background(CosmicPrimary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .size(36.dp)
                            .testTag("details_whatsapp_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "WhatsApp Chat",
                            tint = CosmicPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Button(
                        onClick = { viewModel.showCallPromptForLead(lead) },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("dial_out_button")
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = "Dial", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dial Out", fontSize = 12.sp)
                    }
                }
            }
        }

        // Live Lead Status Modifier Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CosmicSurface.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Update Status:", color = CosmicTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            val statusList = listOf("New", "Contacted", "Interested", "Converted", "Lost")
            statusList.forEach { s ->
                val selected = lead.status == s
                val stateColor = when (s) {
                    "Converted" -> CosmicSecondary
                    "Interested" -> CosmicAccent
                    "Contacted" -> CosmicPrimary
                    "Lost" -> Color.Red
                    else -> CosmicTextSecondary
                }
                Box(
                    modifier = Modifier
                        .background(
                            color = if (selected) stateColor else CosmicSurfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { viewModel.updateLeadStatus(lead.copy(status = s)) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = s,
                        color = CosmicTextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Manual Lead Assignment Selector Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CosmicSurface.copy(alpha = 0.3f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Assign Telecaller:", color = CosmicTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            val callers = listOf("Agent Sarah", "Agent Mike", "Agent Jessica", "Agent David", "Unassigned")
            callers.forEach { cName ->
                val selected = lead.assignedCaller == cName
                Box(
                    modifier = Modifier
                        .background(
                            color = if (selected) CosmicPrimary else CosmicSurfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { viewModel.assignLeadToCaller(lead, cName) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = cName,
                        color = CosmicTextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Background Tracking Status Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CosmicSurfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Active",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Background Engagement cross-referencing active",
                    color = CosmicTextSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "Last Engaged: " + remember(lead.lastContacted) {
                    if (lead.lastContacted == 0L) "Never"
                    else SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault()).format(Date(lead.lastContacted))
                },
                color = CosmicPrimary,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Divider(color = CosmicBackground, thickness = 1.dp)

        // Sub tabs for List view vs Calendar view
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (activePanelTab) {
                    "script" -> "Talking Points Generator"
                    "list" -> "Interaction & Timeline History"
                    "call_history" -> "Call attempts & Outcomes"
                    else -> "Activity & Scheduling"
                },
                color = CosmicTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier
                    .background(CosmicSurfaceVariant, RoundedCornerShape(8.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // List Tab Button
                Box(
                    modifier = Modifier
                        .background(
                            color = if (activePanelTab == "list") CosmicPrimary else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { activePanelTab = "list" }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("lead_tab_list")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "List",
                            tint = if (activePanelTab == "list") Color.White else CosmicTextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "History",
                            color = if (activePanelTab == "list") Color.White else CosmicTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Call History Tab Button
                Box(
                    modifier = Modifier
                        .background(
                            color = if (activePanelTab == "call_history") CosmicPrimary else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { activePanelTab = "call_history" }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("lead_tab_call_history")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Call History",
                            tint = if (activePanelTab == "call_history") Color.White else CosmicTextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Calls",
                            color = if (activePanelTab == "call_history") Color.White else CosmicTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Script Tab Button
                Box(
                    modifier = Modifier
                        .background(
                            color = if (activePanelTab == "script") CosmicPrimary else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { activePanelTab = "script" }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("lead_tab_script")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Script",
                            tint = if (activePanelTab == "script") Color.White else CosmicTextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Script",
                            color = if (activePanelTab == "script") Color.White else CosmicTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Calendar Tab Button
                Box(
                    modifier = Modifier
                        .background(
                            color = if (activePanelTab == "calendar") CosmicPrimary else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { activePanelTab = "calendar" }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("lead_tab_calendar")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Calendar",
                            tint = if (activePanelTab == "calendar") Color.White else CosmicTextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Calendar",
                            color = if (activePanelTab == "calendar") Color.White else CosmicTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Scheduler Tab Button
                Box(
                    modifier = Modifier
                        .background(
                            color = if (activePanelTab == "scheduler") CosmicPrimary else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { activePanelTab = "scheduler" }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("lead_tab_scheduler")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = "Scheduler",
                            tint = if (activePanelTab == "scheduler") Color.White else CosmicTextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Scheduler",
                            color = if (activePanelTab == "scheduler") Color.White else CosmicTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        when (activePanelTab) {
            "list" -> {
                val interactionHistory = remember(callRecords, whatsappMessages) {
                    val items = mutableListOf<LeadInteraction>()
                    callRecords.forEach { record ->
                        items.add(LeadInteraction.Call(record))
                    }
                    whatsappMessages.forEach { msg ->
                        items.add(LeadInteraction.WhatsApp(msg))
                    }
                    items.sortedByDescending { it.timestamp }
                }

                val filteredHistory = remember(interactionHistory, historyFilter) {
                    when (historyFilter) {
                        "Calls" -> interactionHistory.filter { it is LeadInteraction.Call && it.record.recordingStatus != "Manual" }
                        "WhatsApp" -> interactionHistory.filter { it is LeadInteraction.WhatsApp }
                        "Manual Notes" -> interactionHistory.filter { it is LeadInteraction.Call && it.record.recordingStatus == "Manual" }
                        else -> interactionHistory
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Filter bar and "Log Note" button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Scrollable Filters Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            listOf("All", "Calls", "WhatsApp", "Manual Notes").forEach { filter ->
                                val selected = historyFilter == filter
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (selected) CosmicPrimary else CosmicSurfaceVariant,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable { historyFilter = filter }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = filter,
                                        color = if (selected) Color.White else CosmicTextSecondary,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Log manual note button
                        Button(
                            onClick = { showLogInteractionDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary.copy(alpha = 0.15f), contentColor = CosmicPrimary),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.height(28.dp).testTag("log_manual_interaction_btn")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Log", modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Log Note", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (filteredHistory.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                Icon(Icons.Default.Info, contentDescription = "No records", tint = CosmicTextSecondary, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No matching interaction logs found for this lead record.", color = CosmicTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredHistory) { interaction ->
                                when (interaction) {
                                    is LeadInteraction.Call -> {
                                        val record = interaction.record
                                        if (record.recordingStatus == "Manual") {
                                            // Render beautiful Manual Log Card
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("manual_log_item_${record.id}"),
                                                colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant),
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, CosmicAccent.copy(alpha = 0.15f))
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            val icon = when {
                                                                record.callerName.contains("WhatsApp", ignoreCase = true) -> Icons.Default.Send
                                                                record.callerName.contains("Email", ignoreCase = true) -> Icons.Default.Description
                                                                record.callerName.contains("Meeting", ignoreCase = true) -> Icons.Default.People
                                                                else -> Icons.Default.Assignment
                                                            }
                                                            val color = when {
                                                                record.callerName.contains("WhatsApp", ignoreCase = true) -> Color(0xFF25D366)
                                                                record.callerName.contains("Email", ignoreCase = true) -> CosmicSecondary
                                                                record.callerName.contains("Meeting", ignoreCase = true) -> CosmicAccent
                                                                else -> CosmicPrimary
                                                            }
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(24.dp)
                                                                    .background(color.copy(alpha = 0.15f), CircleShape),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                                                            }
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                text = record.callerName,
                                                                color = CosmicTextPrimary,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.sp
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = { viewModel.deleteCallRecordById(record.id) },
                                                            modifier = Modifier.size(24.dp).testTag("delete_manual_log_${record.id}")
                                                        ) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        text = record.notes,
                                                        color = CosmicTextPrimary,
                                                        fontSize = 11.5.sp,
                                                        modifier = Modifier.padding(start = 4.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        text = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault()).format(Date(record.timestamp)),
                                                        color = CosmicTextSecondary,
                                                        fontSize = 9.sp,
                                                        modifier = Modifier.padding(start = 4.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            // Render regular high-fidelity call recording card
                                            CallRecordingItemCard(
                                                viewModel = viewModel,
                                                record = record,
                                                onAnnotate = { selectedRecordForAnnotation = record }
                                            )
                                        }
                                    }
                                    is LeadInteraction.WhatsApp -> {
                                        val message = interaction.message
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("whatsapp_history_item_${message.id}"),
                                            colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.15f))
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .background(Color(0xFF25D366).copy(alpha = 0.15f), CircleShape),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(11.dp))
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = if (message.sender == "Agent") "WhatsApp Outreach (Sent)" else "WhatsApp Received",
                                                            color = CosmicTextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                color = if (message.sender == "Agent") CosmicPrimary.copy(alpha = 0.15f) else Color(0xFF25D366).copy(alpha = 0.15f),
                                                                shape = RoundedCornerShape(4.dp)
                                                            )
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = message.sender,
                                                            color = if (message.sender == "Agent") CosmicPrimary else Color(0xFF25D366),
                                                            fontSize = 8.5.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = message.messageText,
                                                    color = CosmicTextPrimary,
                                                    fontSize = 11.5.sp,
                                                    modifier = Modifier.padding(start = 4.dp)
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                                                    color = CosmicTextSecondary,
                                                    fontSize = 9.sp,
                                                    modifier = Modifier.padding(start = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "call_history" -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LeadCallHistoryView(
                        viewModel = viewModel,
                        lead = lead,
                        callRecords = callRecords,
                        onAnnotateRecord = { record ->
                            selectedRecordForAnnotation = record
                        }
                    )
                }
            }
            "script" -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LeadScriptGeneratorTab(
                        viewModel = viewModel,
                        lead = lead,
                        callRecords = callRecords,
                        whatsappMessages = whatsappMessages
                    )
                }
            }
            "calendar" -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    item {
                        LeadActivityCalendar(
                            lead = lead,
                            callRecords = callRecords,
                            reminderRules = reminderRules,
                            onScheduleFollowUp = { timestamp ->
                                viewModel.scheduleFollowUp(lead, timestamp)
                            },
                            onAnnotateRecord = { record ->
                                selectedRecordForAnnotation = record
                            }
                        )
                    }
                }
            }
            "scheduler" -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LeadReminderSchedulerView(
                        viewModel = viewModel,
                        lead = lead
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    item {
                        LeadActivityCalendar(
                            lead = lead,
                            callRecords = callRecords,
                            reminderRules = reminderRules,
                            onScheduleFollowUp = { timestamp ->
                                viewModel.scheduleFollowUp(lead, timestamp)
                            },
                            onAnnotateRecord = { record ->
                                selectedRecordForAnnotation = record
                            }
                        )
                    }
                }
            }
        }

        // Selected Annotation & Transcript Sliding Sheet
        AnimatedVisibility(
            visible = selectedRecordForAnnotation != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            if (selectedRecordForAnnotation != null) {
                val record = callRecords.find { it.id == selectedRecordForAnnotation!!.id } ?: selectedRecordForAnnotation!!
                AnnotationModalSheet(
                    record = record,
                    onSaveNotes = { notes ->
                        viewModel.updateCallAnnotation(record, notes)
                    },
                    onClose = { selectedRecordForAnnotation = null }
                )
            }
        }

        if (showLogInteractionDialog) {
            LogInteractionDialog(
                lead = lead,
                onDismiss = { showLogInteractionDialog = false },
                onConfirm = { type, notes ->
                    viewModel.logManualInteraction(lead.id, type, notes)
                    showLogInteractionDialog = false
                }
            )
        }
    }
}

// --- DEDICATED PREVIOUS CALL ATTEMPTS & HISTORY VIEW COMPONENT ---
@Composable
fun LeadCallHistoryView(
    viewModel: CRMViewModel,
    lead: LeadEntity,
    callRecords: List<CallRecordEntity>,
    onAnnotateRecord: (CallRecordEntity) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var historyFilter by remember { mutableStateOf("All") } // "All", "Answered", "Unanswered"
    var historySortByNewest by remember { mutableStateOf(true) }
    
    // In-line note editing state
    var editingRecordId by remember { mutableStateOf<Int?>(null) }
    var editingNotesText by remember { mutableStateOf("") }

    // Transcripts expansion state map
    var expandedTranscripts by remember { mutableStateOf(mapOf<Int, Boolean>()) }

    val filteredRecords = remember(callRecords, historyFilter, historySortByNewest) {
        var list = when (historyFilter) {
            "Answered" -> callRecords.filter { it.durationSeconds > 0 }
            "Unanswered" -> callRecords.filter { it.durationSeconds == 0 }
            else -> callRecords
        }
        if (historySortByNewest) {
            list = list.sortedByDescending { it.timestamp }
        } else {
            list = list.sortedBy { it.timestamp }
        }
        list
    }

    // Analytics Summary Calculations
    val totalAttempts = callRecords.size
    val answeredCalls = callRecords.filter { it.durationSeconds > 0 }
    val answeredCount = answeredCalls.size
    val avgDuration = if (answeredCount > 0) {
        answeredCalls.map { it.durationSeconds }.average().toInt()
    } else 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- Summary Cards ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Card 1: Attempts
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Total Attempts", fontSize = 10.sp, color = CosmicTextSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("$totalAttempts", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CosmicPrimary)
                }
            }

            // Card 2: Avg Duration
            Card(
                modifier = Modifier.weight(1.1f),
                colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Avg Answered Duration", fontSize = 10.sp, color = CosmicTextSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("${avgDuration}s", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CosmicSecondary)
                }
            }

            // Card 3: Answered Rate
            Card(
                modifier = Modifier.weight(0.9f),
                colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Answered Rate", fontSize = 10.sp, color = CosmicTextSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    val pct = if (totalAttempts > 0) (answeredCount * 100) / totalAttempts else 0
                    Text("$pct%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CosmicAccent)
                }
            }
        }

        // --- Filter & Sort controls ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("All", "Answered", "Unanswered").forEach { f ->
                    val isSelected = historyFilter == f
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isSelected) CosmicPrimary else CosmicSurfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { historyFilter = f }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = f,
                            color = if (isSelected) Color.White else CosmicTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Sort direction button
            IconButton(
                onClick = { historySortByNewest = !historySortByNewest },
                modifier = Modifier
                    .background(CosmicSurfaceVariant, CircleShape)
                    .size(28.dp)
            ) {
                Icon(
                    imageVector = if (historySortByNewest) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = "Toggle Sort Direction",
                    tint = CosmicPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // --- List of Attempts ---
        if (filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "No attempts",
                        tint = CosmicTextSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No call attempts match the selected filter.",
                        color = CosmicTextSecondary,
                        fontSize = 11.5.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredRecords) { record ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("call_history_attempt_${record.id}"),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (record.durationSeconds > 0) CosmicSecondary.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Header Row: Status Badge and Timestamp
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val (badgeText, badgeBg, badgeTextClr) = if (record.durationSeconds > 0) {
                                        Triple("Answered", Color(0xFF10B981).copy(alpha = 0.12f), Color(0xFF10B981))
                                    } else {
                                        Triple("No Answer", Color(0xFFEF4444).copy(alpha = 0.12f), Color(0xFFEF4444))
                                    }

                                    Box(
                                        modifier = Modifier
                                            .background(badgeBg, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = badgeText,
                                            color = badgeTextClr,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Text(
                                        text = "${record.durationSeconds}s duration",
                                        color = CosmicTextSecondary,
                                        fontSize = 10.sp
                                    )
                                }

                                Text(
                                    text = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault()).format(Date(record.timestamp)),
                                    color = CosmicTextSecondary,
                                    fontSize = 10.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Agent Name
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Agent",
                                    tint = CosmicPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Handled by: ${record.callerName}",
                                    color = CosmicTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Notes / Outcome Notes Section with direct in-line editing
                            Text(
                                text = "Outcome Notes:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CosmicPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            if (editingRecordId == record.id) {
                                // In-line edit field
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = editingNotesText,
                                        onValueChange = { editingNotesText = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("edit_notes_field_${record.id}"),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.5.sp, color = CosmicTextPrimary),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = CosmicPrimary,
                                            unfocusedBorderColor = CosmicTextSecondary.copy(alpha = 0.4f),
                                            focusedContainerColor = CosmicBackground,
                                            unfocusedContainerColor = CosmicBackground
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        maxLines = 3
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        TextButton(
                                            onClick = { editingRecordId = null },
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Cancel", fontSize = 10.5.sp, color = CosmicTextSecondary)
                                        }
                                        Button(
                                            onClick = {
                                                viewModel.updateCallAnnotation(record, editingNotesText)
                                                editingRecordId = null
                                                android.widget.Toast.makeText(context, "Outcome notes saved!", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp),
                                            modifier = Modifier.height(28.dp).testTag("save_notes_btn_${record.id}")
                                        ) {
                                            Text("Save", fontSize = 10.5.sp, color = Color.White)
                                        }
                                    }
                                }
                            } else {
                                // Normal display of notes
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CosmicBackground.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = if (record.notes.isEmpty()) "No outcome notes logged." else record.notes,
                                            color = if (record.notes.isEmpty()) CosmicTextSecondary else CosmicTextPrimary,
                                            fontSize = 11.5.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                editingRecordId = record.id
                                                editingNotesText = record.notes
                                            },
                                            modifier = Modifier
                                                .size(24.dp)
                                                .testTag("edit_notes_btn_${record.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Notes",
                                                tint = CosmicAccent,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // --- Transcript Toggle Section if Transcript is available ---
                            if (record.transcript.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val isTranscriptExpanded = expandedTranscripts[record.id] ?: false
                                Row(
                                    modifier = Modifier
                                        .clickable {
                                            expandedTranscripts = expandedTranscripts.toMutableMap().apply {
                                                put(record.id, !isTranscriptExpanded)
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isTranscriptExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = CosmicSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isTranscriptExpanded) "Hide Call Transcript" else "View Speech Transcript",
                                        color = CosmicSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                AnimatedVisibility(visible = isTranscriptExpanded) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                            .background(CosmicBackground.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                            .border(1.dp, CosmicSecondary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = record.transcript,
                                            color = CosmicTextPrimary.copy(alpha = 0.9f),
                                            fontSize = 11.sp,
                                            fontStyle = FontStyle.Italic,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }

                            // --- Callback Panel & Playback Button ---
                            Spacer(modifier = Modifier.height(10.dp))
                            Divider(color = CosmicBackground.copy(alpha = 0.3f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Audio Player Indicator (if it has mock recording url / transcript)
                                if (record.audioUrl.isNotEmpty()) {
                                    Button(
                                        onClick = { viewModel.togglePlayback(record) },
                                        colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary.copy(alpha = 0.12f), contentColor = CosmicSecondary),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.height(28.dp).testTag("play_history_btn_${record.id}")
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Play Audio", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(1.dp))
                                }

                                // Direct Callback Quick Action
                                Button(
                                    onClick = { viewModel.showCallPromptForLead(lead) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary.copy(alpha = 0.12f), contentColor = CosmicPrimary),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.height(28.dp).testTag("callback_history_btn_${record.id}")
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = "Dial", modifier = Modifier.size(11.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Callback Now", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- CALL RECORDING CARD WITH REAL-TIME AUDIO CONTROLS & WAVEFORM ---
@Composable
fun CallRecordingItemCard(
    viewModel: CRMViewModel,
    record: CallRecordEntity,
    onAnnotate: () -> Unit
) {
    val playingRecordId by viewModel.playingRecordId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.playbackProgress.collectAsStateWithLifecycle()
    val playbackTimeSeconds by viewModel.playbackTimeSeconds.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()

    val isThisPlaying = playingRecordId == record.id
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("call_record_item_${record.id}"),
        colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Title block
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Call Session with ${record.callerName}", color = CosmicTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(sdf.format(Date(record.timestamp)), color = CosmicTextSecondary, fontSize = 10.sp)
                }
                Box(
                    modifier = Modifier
                        .background(
                            color = CosmicSecondary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${record.durationSeconds}s",
                        color = CosmicSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Waveform Visualizer & Audio Slider Progress
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(CosmicBackground.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                // Waveform rendering
                AudioWaveform(
                    progress = if (isThisPlaying) playbackProgress else 0f,
                    isPlaying = isThisPlaying && isPlaying
                )

                // Drag seek slider overlay
                Slider(
                    value = if (isThisPlaying) playbackProgress else 0f,
                    onValueChange = { progress -> viewModel.seekTo(progress, record) },
                    colors = SliderDefaults.colors(
                        thumbColor = CosmicPrimary,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Play/Pause button
                    IconButton(
                        onClick = { viewModel.togglePlayback(record) },
                        modifier = Modifier
                            .background(CosmicPrimary, CircleShape)
                            .size(34.dp)
                            .testTag("play_pause_${record.id}")
                    ) {
                        Icon(
                            imageVector = if (isThisPlaying && isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = CosmicTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Speed button selector
                    if (isThisPlaying) {
                        Row(
                            modifier = Modifier
                                .background(CosmicBackground, RoundedCornerShape(16.dp))
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(1.0f, 1.5f, 2.0f).forEach { speed ->
                                Text(
                                    text = "${speed}x",
                                    color = if (playbackSpeed == speed) CosmicPrimary else CosmicTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { viewModel.setPlaybackSpeed(speed) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Time display
                    Text(
                        text = if (isThisPlaying) {
                            "${formatTime(playbackTimeSeconds)} / ${formatTime(record.durationSeconds)}"
                        } else {
                            "00:00 / ${formatTime(record.durationSeconds)}"
                        },
                        color = CosmicTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Annotation panel trigger
                TextButton(
                    onClick = onAnnotate,
                    modifier = Modifier.testTag("annotate_button_${record.id}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Annotate", modifier = Modifier.size(14.dp), tint = CosmicAccent)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Annotate", fontSize = 11.sp, color = CosmicAccent)
                }
            }

            // Simple note excerpt if exists
            if (record.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Divider(color = CosmicBackground.copy(alpha = 0.3f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = record.notes,
                    color = CosmicTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// --- BEAUTIFUL REACTIVE AUDIO WAVEFORM ---
@Composable
fun AudioWaveform(progress: Float, isPlaying: Boolean) {
    val barCount = 35
    val barWidth = 3.dp
    val spacing = 2.dp

    // Pulse animation factor when playing
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val totalBarSpace = barCount * (barWidth.toPx() + spacing.toPx())
        val startOffset = (width - totalBarSpace) / 2f

        // Pseudo-random deterministic heights for bars
        val heights = listOf(
            0.3f, 0.5f, 0.8f, 0.6f, 0.4f, 0.7f, 0.9f, 0.5f, 0.4f, 0.8f, 0.6f, 0.5f, 0.7f, 0.4f, 0.6f, 0.9f,
            0.8f, 0.5f, 0.7f, 0.4f, 0.3f, 0.6f, 0.8f, 0.5f, 0.7f, 0.9f, 0.4f, 0.6f, 0.5f, 0.8f, 0.7f, 0.4f,
            0.6f, 0.3f, 0.5f
        )

        for (i in 0 until barCount) {
            val barX = startOffset + i * (barWidth.toPx() + spacing.toPx())
            val rawHeightFactor = heights[i % heights.size]
            val heightFactor = if (isPlaying && progress > (i.toFloat() / barCount)) {
                rawHeightFactor * pulseScale
            } else {
                rawHeightFactor
            }

            val barHeight = height * 0.6f * heightFactor
            val topY = (height - barHeight) / 2f
            val bottomY = topY + barHeight

            val fillRatio = progress * barCount
            val isFilled = i < fillRatio

            val barColor = if (isFilled) CosmicPrimary else CosmicTextSecondary.copy(alpha = 0.3f)

            drawLine(
                color = barColor,
                start = Offset(barX, topY),
                end = Offset(barX, bottomY),
                strokeWidth = barWidth.toPx()
            )
        }
    }
}

// --- ANNOTATION PANEL & TRANSCRIPT SHEETS ---
@Composable
fun AnnotationModalSheet(
    record: CallRecordEntity,
    onSaveNotes: (String) -> Unit,
    onClose: () -> Unit
) {
    var notesText by remember(record.id) { mutableStateOf(record.notes) }
    var selectedTab by remember { mutableStateOf("notes") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
            .testTag("annotation_panel"),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Session Transcription & Annotation", color = CosmicTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = CosmicTextPrimary)
                }
            }

            // Tabs for selector
            TabRow(
                selectedTabIndex = if (selectedTab == "notes") 0 else 1,
                containerColor = Color.Transparent,
                contentColor = CosmicPrimary,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == "notes",
                    onClick = { selectedTab = "notes" },
                    text = { Text("Annotations & Summary", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == "transcript",
                    onClick = { selectedTab = "transcript" },
                    text = { Text("Speech-to-Text Transcript", fontSize = 12.sp) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                "notes" -> {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Add notes, summaries, or customer follow-up actions below:", color = CosmicTextSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
                        OutlinedTextField(
                            value = notesText,
                            onValueChange = { notesText = it },
                            placeholder = { Text("Type annotations here...", color = CosmicTextSecondary) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("annotation_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CosmicTextPrimary,
                                unfocusedTextColor = CosmicTextPrimary,
                                focusedContainerColor = CosmicBackground,
                                unfocusedContainerColor = CosmicBackground,
                                focusedBorderColor = CosmicPrimary,
                                unfocusedBorderColor = CosmicSurface
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                onSaveNotes(notesText)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_notes_button")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Annotations")
                        }
                    }
                }
                "transcript" -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(CosmicBackground, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    text = record.transcript.ifEmpty { "Speech-to-text transcript was not generated for this short session." },
                                    color = CosmicTextPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- ACTIVE OUTBOUND CALL TELECALLING SIMULATOR OVERLAY ---
@Composable
fun OngoingCallOverlay(
    viewModel: CRMViewModel,
    lead: LeadEntity,
    seconds: Int,
    onEndCall: (String) -> Unit,
    onCancelCall: () -> Unit
) {
    var liveNotes by remember { mutableStateOf("") }
    var selectedTone by remember { mutableStateOf("Consultative") }
    var showScriptPrompts by remember { mutableStateOf(true) }

    val callRecords by viewModel.leadCallRecords.collectAsStateWithLifecycle()
    val whatsappMessages by viewModel.leadWhatsAppMessages.collectAsStateWithLifecycle()

    val totalCalls = callRecords.size
    val lastCallNote = callRecords.firstOrNull { it.notes.isNotEmpty() }?.notes
    val lastWhatsAppMsg = whatsappMessages.lastOrNull { it.sender == "Lead" }?.messageText

    // Dynamic Talking Points based on Status + Tone
    val greeting = when (selectedTone) {
        "Value-Driven" -> "Hey ${lead.name}, this is quick outreach from CRM Hub. I hope you're having an active week!"
        "Problem-Solver" -> "Hello ${lead.name}, this is CRM Hub technical support. I am reaching out to follow up on your integration inquiry."
        else -> "Hello ${lead.name}, this is CRM Hub. I hope I'm not interrupting your schedule. Is now a convenient time for a brief call?"
    }

    val openingPoint = remember(lead, selectedTone) {
        when (lead.status) {
            "New" -> "I noticed you registered via ${lead.source} and wanted to personally welcome you. Our system indicated your team is looking to scale outbound capacity."
            "Contacted" -> "Just following up on our previous conversation. I wanted to verify if you had a chance to look at our modern dialer features."
            "Interested" -> "Great to speak with you again! Since you expressed interest, I've prepared a custom walkthrough of our dynamic routing dashboard for ${lead.company}."
            "Converted" -> "Checking in on your onboarding setup. I want to make sure your agents are fully configured and enjoying our CRM features."
            else -> "Reaching back out to see if your calendar has opened up. We've introduced several new automated features that directly address team workflow friction."
        }
    }

    val valuePitch = remember(lead, selectedTone) {
        when (selectedTone) {
            "Value-Driven" -> {
                when (lead.status) {
                    "New", "Contacted" -> "With our automated Campaign Queue, telecallers experience a 250% increase in live talk-time by eliminating manual dial gaps. For ${lead.company}, this directly translates to higher conversion velocity."
                    "Interested" -> "By locking in this month's custom package, ${lead.company} gets priority integration support and a guaranteed 35% discount on outbound call minutes, optimizing your immediate ROI."
                    else -> "Even if the timeline isn't immediate, our dynamic sync and auto-dialer can save your sales reps up to 2 hours of manual lead logging every single day."
                }
            }
            "Problem-Solver" -> {
                when (lead.status) {
                    "New", "Contacted" -> "Our core value is architectural. We replace messy manual pipelines with a unified Room database that synchronizes with WhatsApp and Facebook Leads in under 5 seconds. Let's solve the data leakage issue at ${lead.company}."
                    "Interested" -> "We support native webhook configuration, advanced call annotation playback, and custom rule-based follow-up reminders. It perfectly fits ${lead.company}'s custom workflow requirements."
                    else -> "We've resolved past latency concerns with a brand-new local cache, ensuring your call recording annotations are fully synced offline-first."
                }
            }
            else -> { // Consultative
                when (lead.status) {
                    "New", "Contacted" -> "We focus on helping your team have warmer, more meaningful conversations. We provide real-time caller dashboards and sentiment tracking so agents can build instant trust."
                    "Interested" -> "Our primary goal is supporting ${lead.company}'s growth. We can customize the CRM layout to match your current sales playbooks so your team feels right at home."
                    else -> "We want to be a helpful resource. We offer unlimited onboarding support for your representatives to guarantee a smooth transition."
                }
            }
        }
    }

    val objectionHandling = remember(lead, selectedTone, lastCallNote, lastWhatsAppMsg) {
        buildString {
            if (lead.sentiment == "Negative") {
                append("⚠️ EMPATHY FIRST: Address hesitation. \"I understand you have some initial hesitations. Many clients at first felt the same way, but found that our offline sync fully protects their database. Let's take it at your pace.\"")
            } else {
                when (selectedTone) {
                    "Value-Driven" -> append("⚡ SPEED & SCALE: \"If budget is a consideration, let's look at the cost of inaction. Every day your agents manually log leads is a day you lose active pipeline volume.\"")
                    "Problem-Solver" -> {
                        if (!lastCallNote.isNullOrBlank()) {
                            append("🛠️ SOLUTION MATCH: \"On our last call, we touched on [${lastCallNote.take(30)}...]. Our system resolves this natively with custom scheduled follow-up reminder rules so nothing falls through the cracks.\"")
                        } else {
                            append("🛠️ TECHNICAL CONFIDENCE: \"If compatibility is a worry, we offer direct API templates that bridge existing systems smoothly, requiring zero extra coding from your side.\"")
                        }
                    }
                    else -> append("🤝 RELATIONSHIP BUILD: \"I appreciate you sharing those details. It's completely normal to want to make sure the fit is perfect. Let's look at a customized pilot program for ${lead.company} to verify the value firsthand.\"")
                }
            }
        }
    }

    val nextStepCTA = remember(lead, selectedTone) {
        when (lead.status) {
            "New", "Contacted" -> "Would you be open to a quick, 10-minute visual demo this week? I can set up a personalized sandbox showing exactly how ${lead.company}'s leads would flow."
            "Interested" -> "Shall I draft our standard service agreement for your review? We can configure your custom subdomain within 24 hours of confirmation."
            "Converted" -> "Let's schedule a 15-minute training session for your main team leads next Tuesday to ensure optimal adoption."
            else -> "No pressure at all. I can send over our latest outbound optimization guide and touch base in a couple of weeks to see if the timing is better."
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Pulse Ring call icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(CosmicSecondary.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = "Calling",
                        tint = CosmicSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("OUTBOUND SESSION ONGOING", color = CosmicSecondary, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 2.sp)
                Text(lead.name, color = CosmicTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Dialing at ${lead.phone}", color = CosmicTextSecondary, fontSize = 11.sp)

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = formatTime(seconds),
                    color = CosmicTextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Call Prompts & Scripts Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CosmicBackground),
                    border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showScriptPrompts = !showScriptPrompts },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = CosmicPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("Live Call Prompts & Script", color = CosmicTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Icon(
                                imageVector = if (showScriptPrompts) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = if (showScriptPrompts) "Collapse" else "Expand",
                                tint = CosmicTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (showScriptPrompts) {
                            // Tone selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val tones = listOf("Consultative", "Value-Driven", "Problem-Solver")
                                tones.forEach { tone ->
                                    val isSelected = selectedTone == tone
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(
                                                color = if (isSelected) CosmicPrimary else CosmicSurface,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable { selectedTone = tone }
                                            .padding(vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = tone,
                                            color = if (isSelected) Color.White else CosmicTextSecondary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.testTag("live_tone_btn_$tone")
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = CosmicSurfaceVariant, thickness = 0.5.dp)

                            // Scrollable Prompts Cards
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                                    .testTag("live_prompts_scroll_container"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    ScriptSegmentCard(title = "1. Introduction / Greeting", text = greeting, testTagPrefix = "live_greeting")
                                }
                                item {
                                    ScriptSegmentCard(title = "2. Opening Statement", text = openingPoint, testTagPrefix = "live_opening")
                                }
                                item {
                                    ScriptSegmentCard(title = "3. Value Pitch", text = valuePitch, testTagPrefix = "live_value")
                                }
                                item {
                                    ScriptSegmentCard(title = "4. Objection Handling", text = objectionHandling, testTagPrefix = "live_objection")
                                }
                                item {
                                    ScriptSegmentCard(title = "5. Call CTA / Next Step", text = nextStepCTA, testTagPrefix = "live_cta")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Real-time live annotations input while talking!
                OutlinedTextField(
                    value = liveNotes,
                    onValueChange = { liveNotes = it },
                    placeholder = { Text("Take active notes/annotations while talking...", color = CosmicTextSecondary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .testTag("live_notes_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CosmicTextPrimary,
                        unfocusedTextColor = CosmicTextPrimary,
                        focusedContainerColor = CosmicBackground,
                        unfocusedContainerColor = CosmicBackground,
                        focusedBorderColor = CosmicSecondary,
                        unfocusedBorderColor = CosmicSurface
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = onCancelCall,
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicSurfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(38.dp).testTag("cancel_call_button")
                    ) {
                        Text("Cancel", color = CosmicTextPrimary, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { onEndCall(liveNotes) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(38.dp).testTag("end_call_button")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "End Call", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("End & Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- WHATSAPP BUSINESS CHAT SUBTAB ---
@Composable
fun WhatsAppTab(
    viewModel: CRMViewModel,
    leads: List<LeadEntity>,
    selectedLead: LeadEntity?
) {
    val messages by viewModel.leadWhatsAppMessages.collectAsStateWithLifecycle()
    var composeText by remember { mutableStateOf("") }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompact = maxWidth < 600.dp

        if (isCompact) {
            if (selectedLead == null) {
                // Mobile View: WhatsApp Contacts List ONLY
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("WhatsApp Contacts", color = CosmicTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(leads) { lead ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectLead(lead) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = CosmicSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(CosmicAccent.copy(alpha = 0.2f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(lead.name.take(1), color = CosmicAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(lead.name, color = CosmicTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("Open WhatsApp session", color = CosmicTextSecondary, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Mobile View: Chat Box view with Back Navigation
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.selectLead(null) },
                            modifier = Modifier.testTag("back_to_chats_button")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back to contacts", tint = CosmicPrimary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Back to WhatsApp Contacts",
                            color = CosmicPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        WhatsAppChatBox(
                            viewModel = viewModel,
                            selectedLead = selectedLead,
                            messages = messages,
                            composeText = composeText,
                            onComposeTextChange = { composeText = it },
                            onSendMessage = {
                                viewModel.sendWhatsAppMessage(selectedLead.id, composeText)
                                composeText = ""
                            }
                        )
                    }
                }
            }
        } else {
            // Desktop/Tablet View: Side-by-Side Dual Pane
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Column: Lead Conversations
                Card(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .padding(start = 12.dp, top = 8.dp, end = 6.dp, bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("WhatsApp Contacts", color = CosmicTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(leads) { lead ->
                                val isSelected = selectedLead?.id == lead.id
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectLead(lead) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) CosmicPrimary.copy(alpha = 0.2f) else CosmicSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(CosmicAccent.copy(alpha = 0.2f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(lead.name.take(1), color = CosmicAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(lead.name, color = CosmicTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("Open WhatsApp session", color = CosmicTextSecondary, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Right Column: Chat Box
                Card(
                    modifier = Modifier
                        .weight(1.9f)
                        .fillMaxHeight()
                        .padding(start = 6.dp, top = 8.dp, end = 12.dp, bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (selectedLead == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Icon(Icons.Default.Send, contentDescription = "Chat", tint = CosmicPrimary, modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Select a contact from directory to review WhatsApp messages or send conversational sequences.", color = CosmicTextSecondary, textAlign = TextAlign.Center, fontSize = 14.sp)
                            }
                        }
                    } else {
                        WhatsAppChatBox(
                            viewModel = viewModel,
                            selectedLead = selectedLead,
                            messages = messages,
                            composeText = composeText,
                            onComposeTextChange = { composeText = it },
                            onSendMessage = {
                                viewModel.sendWhatsAppMessage(selectedLead.id, composeText)
                                composeText = ""
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WhatsAppChatBox(
    viewModel: CRMViewModel,
    selectedLead: LeadEntity,
    messages: List<WhatsAppMessageEntity>,
    composeText: String,
    onComposeTextChange: (String) -> Unit,
    onSendMessage: () -> Unit
) {
    val templates by viewModel.whatsappTemplates.collectAsStateWithLifecycle(emptyList())
    var showTemplateManager by remember { mutableStateOf(false) }

    // Form fields for adding/editing templates
    var showAddEditForm by remember { mutableStateOf(false) }
    var editingTemplateId by remember { mutableStateOf<Int?>(null) }
    var formName by remember { mutableStateOf("") }
    var formText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Chat header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(CosmicSecondary, CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Live Session: ${selectedLead.name}", color = CosmicTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            val context = androidx.compose.ui.platform.LocalContext.current
            Button(
                onClick = { launchWhatsAppChat(context, selectedLead.phone, composeText, viewModel, selectedLead.id) },
                colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(28.dp)
                    .testTag("open_real_whatsapp_btn")
            ) {
                Icon(Icons.Default.Send, contentDescription = "WhatsApp Link", tint = CosmicTextPrimary, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Open WhatsApp", color = CosmicTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Divider(color = CosmicBackground, thickness = 1.dp)

        // Chat transcripts
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No messages in log. Send a text to start WhatsApp automation.", color = CosmicTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
            } else {
                items(messages) { msg ->
                    val isAgent = msg.sender == "Agent"
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isAgent) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isAgent) CosmicPrimary else CosmicSurfaceVariant
                            ),
                            shape = RoundedCornerShape(
                                topStart = 12.dp,
                                topEnd = 12.dp,
                                bottomStart = if (isAgent) 12.dp else 0.dp,
                                bottomEnd = if (isAgent) 0.dp else 12.dp
                            ),
                            modifier = Modifier.widthIn(max = 240.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(msg.messageText, color = CosmicTextPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Frequently Used Templates horizontal scroll row
        Text(
            text = "Frequently Used Templates (Click to apply, or Manage)",
            color = CosmicTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            templates.forEach { template ->
                Box(
                    modifier = Modifier
                        .background(CosmicSurfaceVariant, RoundedCornerShape(16.dp))
                        .border(1.dp, CosmicPrimary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .clickable {
                            val personalized = viewModel.getPersonalizedTemplateText(template.text, selectedLead)
                            onComposeTextChange(personalized)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("template_chip_${template.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            tint = CosmicPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = template.name,
                            color = CosmicTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        // Direct single-click Send button
                        IconButton(
                            onClick = {
                                val personalized = viewModel.getPersonalizedTemplateText(template.text, selectedLead)
                                viewModel.sendWhatsAppMessage(selectedLead.id, personalized)
                            },
                            modifier = Modifier
                                .size(18.dp)
                                .background(CosmicSecondary.copy(alpha = 0.2f), CircleShape)
                                .testTag("template_quick_send_${template.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send directly",
                                tint = CosmicSecondary,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }

            // Manage Templates Chip
            Box(
                modifier = Modifier
                    .background(CosmicSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, CosmicAccent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .clickable { showTemplateManager = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("manage_templates_chip")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Manage Templates",
                        tint = CosmicAccent,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Manage Templates",
                        color = CosmicAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Composer input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = composeText,
                onValueChange = onComposeTextChange,
                placeholder = { Text("Type message here...", color = CosmicTextSecondary) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CosmicTextPrimary,
                    unfocusedTextColor = CosmicTextPrimary,
                    focusedContainerColor = CosmicBackground,
                    unfocusedContainerColor = CosmicBackground,
                    focusedBorderColor = CosmicPrimary,
                    unfocusedBorderColor = CosmicSurface
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (composeText.trim().isNotEmpty()) {
                        onSendMessage()
                    }
                },
                modifier = Modifier
                    .background(CosmicPrimary, CircleShape)
                    .size(44.dp)
                    .testTag("send_chat_button")
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = CosmicTextPrimary)
            }
        }

        // Template Manager Dialog
        if (showTemplateManager) {
            AlertDialog(
                onDismissRequest = { showTemplateManager = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "WhatsApp Template Manager",
                            color = CosmicTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showTemplateManager = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = CosmicTextSecondary)
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Info/Placeholder Helper Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CosmicAccent.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .border(1.dp, CosmicAccent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Customize messages using placeholder tags:\n• {name} - Lead's Full Name\n• {company} - Lead's Company\n• {phone} - Lead's Phone\n• {email} - Lead's Email Address\n• {status} - Current Pipeline Status",
                                color = CosmicTextPrimary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }

                        if (showAddEditForm) {
                            // Subform to add/edit template details
                            Text(
                                text = if (editingTemplateId == null) "Create New Template" else "Edit Template",
                                color = CosmicPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = formName,
                                onValueChange = { formName = it },
                                label = { Text("Template Title (e.g., Quick Pitch)", color = CosmicTextSecondary) },
                                modifier = Modifier.fillMaxWidth().testTag("template_form_name"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = CosmicTextPrimary,
                                    unfocusedTextColor = CosmicTextPrimary,
                                    focusedBorderColor = CosmicPrimary,
                                    unfocusedBorderColor = CosmicSurfaceVariant
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                            )
                            OutlinedTextField(
                                value = formText,
                                onValueChange = { formText = it },
                                label = { Text("Template Body Text", color = CosmicTextSecondary) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .testTag("template_form_text"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = CosmicTextPrimary,
                                    unfocusedTextColor = CosmicTextPrimary,
                                    focusedBorderColor = CosmicPrimary,
                                    unfocusedBorderColor = CosmicSurfaceVariant
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        showAddEditForm = false
                                        editingTemplateId = null
                                    }
                                ) {
                                    Text("Cancel", color = CosmicTextSecondary)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (formName.isNotBlank() && formText.isNotBlank()) {
                                            val id = editingTemplateId
                                            if (id == null) {
                                                viewModel.addWhatsAppTemplate(formName, formText)
                                            } else {
                                                viewModel.updateWhatsAppTemplate(id, formName, formText)
                                            }
                                            showAddEditForm = false
                                            editingTemplateId = null
                                            formName = ""
                                            formText = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                                    modifier = Modifier.testTag("save_template_submit_button")
                                ) {
                                    Text("Save")
                                }
                            }
                        } else {
                            // Standard List View
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("All Saved Templates", color = CosmicTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Button(
                                    onClick = {
                                        formName = ""
                                        formText = ""
                                        editingTemplateId = null
                                        showAddEditForm = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("add_new_template_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add", tint = CosmicTextPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add New", fontSize = 11.sp)
                                }
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (templates.isEmpty()) {
                                    item {
                                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                            Text("No templates found. Click Add New to create one.", color = CosmicTextSecondary, fontSize = 11.sp)
                                        }
                                    }
                                } else {
                                    items(templates) { template ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(template.name, color = CosmicTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        IconButton(
                                                            onClick = {
                                                                editingTemplateId = template.id
                                                                formName = template.name
                                                                formText = template.text
                                                                showAddEditForm = true
                                                            },
                                                            modifier = Modifier.size(24.dp).testTag("edit_template_${template.id}")
                                                        ) {
                                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = CosmicPrimary, modifier = Modifier.size(14.dp))
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                viewModel.deleteWhatsAppTemplate(template.id)
                                                            },
                                                            modifier = Modifier.size(24.dp).testTag("delete_template_${template.id}")
                                                        ) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(14.dp))
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = template.text,
                                                    color = CosmicTextSecondary,
                                                    fontSize = 11.sp,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {},
                containerColor = CosmicSurface,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

// --- FACEBOOK ADS LEAD CAPTURE SUBTAB ---
@Composable
fun FacebookLeadsTab(viewModel: CRMViewModel) {
    val fbLeads by viewModel.facebookLeads.collectAsStateWithLifecycle()
    val leads by viewModel.leads.collectAsStateWithLifecycle()
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault()) }
    val context = LocalContext.current

    val isRunning by com.example.service.FacebookLeadPollingService.isServiceRunning.collectAsStateWithLifecycle()
    val lastPollTime by com.example.service.FacebookLeadPollingService.lastPollTimestamp.collectAsStateWithLifecycle()
    val totalPolled by com.example.service.FacebookLeadPollingService.leadsPolledTotal.collectAsStateWithLifecycle()
    val serviceLogs by com.example.service.FacebookLeadPollingService.recentLogs.collectAsStateWithLifecycle()

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Column: FB webhook feed queue + Live Lead Router (weight = 1.1f)
        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight()
        ) {
            // Webhook feed queue card
            Card(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 8.dp, end = 6.dp, bottom = 6.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // API Polling Service controls
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("FB Lead Ads Polling Service", color = CosmicTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(if (isRunning) Color(0xFF34D399) else Color(0xFFEF4444), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isRunning) "Active (Polling)" else "Stopped",
                                            color = if (isRunning) Color(0xFF34D399) else Color(0xFFEF4444),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                Switch(
                                    checked = isRunning,
                                    onCheckedChange = { start ->
                                        val intent = Intent(context, com.example.service.FacebookLeadPollingService::class.java)
                                        if (start) {
                                            context.startService(intent)
                                        } else {
                                            context.stopService(intent)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = CosmicPrimary,
                                        checkedTrackColor = CosmicPrimary.copy(alpha = 0.4f),
                                        uncheckedThumbColor = CosmicTextSecondary,
                                        uncheckedTrackColor = CosmicSurface
                                    ),
                                    modifier = Modifier.scale(0.75f).testTag("facebook_service_switch")
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Last Polled API", color = CosmicTextSecondary, fontSize = 8.sp)
                                    Text(
                                        text = if (lastPollTime > 0) {
                                            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(lastPollTime)
                                        } else "Never",
                                        color = CosmicTextPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("API Ingested Leads", color = CosmicTextSecondary, fontSize = 8.sp)
                                    Text(
                                        text = "$totalPolled",
                                        color = CosmicPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // Service Logs Terminal View
                    Text("Background Polling Logs", color = CosmicTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(85.dp)
                            .padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.5.dp, CosmicSurfaceVariant)
                    ) {
                        if (serviceLogs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("System logs empty. Service running in background...", color = Color(0xFF64748B), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(serviceLogs) { log ->
                                    Text(
                                        text = log,
                                        color = if (log.contains("Success") || log.contains("routed") || log.contains("Ingest") || log.contains("new lead")) Color(0xFF34D399) else if (log.contains("Error") || log.contains("Warning") || log.contains("No available")) Color(0xFFFBBF24) else Color(0xFFCBD5E1),
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Queue Header + Simulation Manual Trigger button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Facebook Webhook Queue", color = CosmicTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        
                        Button(
                            onClick = { viewModel.triggerFacebookLeadSubmit() },
                            colors = ButtonDefaults.buttonColors(containerColor = CosmicAccent),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("submit_test_lead"),
                            contentPadding = PaddingValues(vertical = 2.dp, horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Poll Graph API", modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Poll Graph API", fontSize = 9.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))

                    if (fbLeads.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No captured Facebook leads. Click 'Poll Graph API' or configure settings to let the Background Service fetch leads.", color = CosmicTextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(fbLeads) { lead ->
                                FacebookLeadItemCard(lead, sdf, onSync = { viewModel.syncFacebookLeadToLeads(lead) })
                            }
                        }
                    }
                }
            }

            // Live Router card
            RoundRobinRouterPanel(viewModel)
        }

        // Right Column: Interactive Dashboard panel with tabs (weight = 1.9f)
        Card(
            modifier = Modifier
                .weight(1.9f)
                .fillMaxHeight()
                .padding(start = 6.dp, top = 8.dp, end = 12.dp, bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            RightColumnDashboardPanel(leads, fbLeads, viewModel)
        }
    }
}

@Composable
fun RightColumnDashboardPanel(
    leads: List<LeadEntity>,
    fbLeads: List<FacebookAdLeadEntity>,
    viewModel: CRMViewModel
) {
    var activeTab by remember { mutableStateOf("funnel") } // "funnel", "heatmap"

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = if (activeTab == "funnel") 0 else 1,
            containerColor = CosmicSurface,
            contentColor = CosmicPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
            divider = { Divider(color = CosmicSurfaceVariant, thickness = 1.dp) }
        ) {
            Tab(
                selected = activeTab == "funnel",
                onClick = { activeTab = "funnel" },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FilterList, contentDescription = "Funnel", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Conversion Funnel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                },
                modifier = Modifier.testTag("dashboard_tab_funnel")
            )
            Tab(
                selected = activeTab == "heatmap",
                onClick = { activeTab = "heatmap" },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = "Heatmap", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Optimal calling hours", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                },
                modifier = Modifier.testTag("dashboard_tab_heatmap")
            )
        }

        when (activeTab) {
            "funnel" -> InteractiveConversionFunnelSection(leads, fbLeads, viewModel)
            "heatmap" -> TelecallingHeatmapDashboard(leads, fbLeads, viewModel)
        }
    }
}

@Composable
fun TelecallingHeatmapDashboard(
    leads: List<LeadEntity>,
    fbLeads: List<FacebookAdLeadEntity>,
    viewModel: CRMViewModel
) {
    val callRecords by viewModel.callRecords.collectAsStateWithLifecycle()
    var selectedMetric by remember { mutableStateOf("engagement") } // "leads", "calls", "engagement"
    
    // Default selected hour/day
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(Pair(2, 10)) } // Wednesday, 10 AM by default

    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val dayFullNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    // Helper to get day index (0 to 6, Mon to Sun)
    fun getDayIndex(timestamp: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        val day = cal.get(Calendar.DAY_OF_WEEK)
        return when (day) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
    }

    // Helper to get hour of day (0 to 23)
    fun getHourIndex(timestamp: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    // Baseline volume helper for rich UX on start
    fun getBaselineVolume(dayIndex: Int, hourIndex: Int, type: String): Int {
        val isWeekend = dayIndex >= 5
        val dayMultiplier = if (isWeekend) 0.15f else 1.0f
        
        val hourMultiplier = when (hourIndex) {
            in 9..11 -> 0.95f  // Morning peak (9 AM - 11 AM)
            12 -> 0.35f        // Lunch dip (12 PM)
            in 14..16 -> 1.0f  // Afternoon peak (2 PM - 4 PM)
            in 17..18 -> 0.75f // Late afternoon follow-ups (5 PM - 6 PM)
            8, 13, 19 -> 0.4f  // Transition times
            else -> 0.05f      // Off-hours
        }
        
        val base = if (type == "leads") {
            (12 * dayMultiplier * hourMultiplier + ((dayIndex + hourIndex) % 3) * 0.5f).toInt()
        } else {
            (22 * dayMultiplier * hourMultiplier + ((dayIndex * hourIndex) % 4) * 0.5f).toInt()
        }
        return base.coerceAtLeast(0)
    }

    // Calculate matrices
    val leadMatrix = remember(leads, fbLeads) {
        val matrix = Array(7) { IntArray(24) }
        for (d in 0 until 7) {
            for (h in 0 until 24) {
                matrix[d][h] = getBaselineVolume(d, h, "leads")
            }
        }
        leads.forEach { lead ->
            val d = getDayIndex(lead.lastContacted)
            val h = getHourIndex(lead.lastContacted)
            matrix[d][h] += 1
        }
        fbLeads.forEach { lead ->
            val d = getDayIndex(lead.submittedTimestamp)
            val h = getHourIndex(lead.submittedTimestamp)
            matrix[d][h] += 1
        }
        matrix
    }

    val callMatrix = remember(callRecords) {
        val matrix = Array(7) { IntArray(24) }
        for (d in 0 until 7) {
            for (h in 0 until 24) {
                matrix[d][h] = getBaselineVolume(d, h, "calls")
            }
        }
        callRecords.forEach { record ->
            val d = getDayIndex(record.timestamp)
            val h = getHourIndex(record.timestamp)
            matrix[d][h] += 1
        }
        matrix
    }

    val maxVal = remember(leadMatrix, callMatrix, selectedMetric) {
        var m = 1f
        for (d in 0 until 7) {
            for (h in 0 until 24) {
                val v = when (selectedMetric) {
                    "leads" -> leadMatrix[d][h].toFloat()
                    "calls" -> callMatrix[d][h].toFloat()
                    else -> (leadMatrix[d][h] * 2.5f + callMatrix[d][h]).toFloat()
                }
                if (v > m) m = v
            }
        }
        m
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Dashboard Title & Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Optimal calling hours", color = CosmicTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Identify highest telecalling contact rates", color = CosmicTextSecondary, fontSize = 9.5.sp)
            }

            // Metric Selector Segmented Buttons
            Row(
                modifier = Modifier
                    .background(CosmicSurfaceVariant, RoundedCornerShape(8.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(
                    "leads" to "Leads Intake",
                    "calls" to "Calls Made",
                    "engagement" to "Optimal Index"
                ).forEach { (metric, label) ->
                    val isSelected = selectedMetric == metric
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isSelected) CosmicPrimary else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { selectedMetric = metric }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(label, color = CosmicTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Heatmap Legend Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Time Series Hour of Day (00:00 - 23:00)",
                color = CosmicTextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Low", color = CosmicTextSecondary, fontSize = 8.sp)
                val baseColor = when (selectedMetric) {
                    "leads" -> CosmicAccent
                    "calls" -> CosmicSecondary
                    else -> CosmicPrimary
                }
                listOf(0.15f, 0.4f, 0.7f, 1.0f).forEach { opacity ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (opacity == 1.0f) baseColor else baseColor.copy(alpha = opacity),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
                Text("Peak", color = CosmicTextSecondary, fontSize = 8.sp)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Scrollable Heatmap Grid Container
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Left fixed column: Days of the week labels
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(top = 18.dp, end = 6.dp), // extra top padding for alignment with column headers
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                daysOfWeek.forEach { dayLabel ->
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(28.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = dayLabel,
                            color = CosmicTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Scrollable Grid Area (all 24 hours)
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(scrollState)
            ) {
                // Column Header Hours row (0h to 23h)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (h in 0 until 24) {
                        val amPmLabel = when {
                            h == 0 -> "12A"
                            h == 12 -> "12P"
                            h < 12 -> "${h}A"
                            else -> "${h - 12}P"
                        }
                        Box(
                            modifier = Modifier.width(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = amPmLabel,
                                color = CosmicTextSecondary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Rows of cells for Mon - Sun
                for (d in 0 until 7) {
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (h in 0 until 24) {
                            val lVal = leadMatrix[d][h]
                            val cVal = callMatrix[d][h]

                            val cellScore = when (selectedMetric) {
                                "leads" -> lVal.toFloat()
                                "calls" -> cVal.toFloat()
                                else -> (lVal * 2.5f + cVal).toFloat()
                            }

                            val isSelected = selectedCell?.first == d && selectedCell?.second == h
                            val ratio = cellScore / maxVal

                            val themeColor = when (selectedMetric) {
                                "leads" -> CosmicAccent
                                "calls" -> CosmicSecondary
                                else -> CosmicPrimary
                            }

                            val cellColor = when {
                                cellScore == 0f -> CosmicSurfaceVariant.copy(alpha = 0.15f)
                                ratio < 0.15f -> themeColor.copy(alpha = 0.2f)
                                ratio < 0.45f -> themeColor.copy(alpha = 0.45f)
                                ratio < 0.75f -> themeColor.copy(alpha = 0.75f)
                                else -> themeColor
                            }

                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(cellColor, RoundedCornerShape(4.dp))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.5.dp,
                                        color = if (isSelected) CosmicTextPrimary else CosmicSurfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        selectedCell = Pair(d, h)
                                    }
                                    .testTag("heatmap_cell_${d}_${h}")
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Selected Cell Inspector / Analytics Insights Pane
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hour Icon & Info (Left block)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(CosmicPrimary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = "Selected time interval",
                        tint = CosmicPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Hour Slot Stats (Middle block)
                Column(modifier = Modifier.weight(1.2f)) {
                    val selD = selectedCell?.first ?: 2
                    val selH = selectedCell?.second ?: 10
                    val amPmLabel = when {
                        selH == 0 -> "12:00 AM"
                        selH == 12 -> "12:00 PM"
                        selH < 12 -> "${selH}:00 AM"
                        else -> "${selH - 12}:00 PM"
                    }
                    val amPmNextLabel = when {
                        selH + 1 == 12 -> "12:00 PM"
                        selH + 1 == 24 -> "12:00 AM"
                        selH + 1 < 12 -> "${selH + 1}:00 AM"
                        else -> "${selH + 1 - 12}:00 PM"
                    }

                    Text(
                        text = "${dayFullNames[selD]}, $amPmLabel - $amPmNextLabel",
                        color = CosmicTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.People,
                                contentDescription = "Leads",
                                tint = CosmicAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Leads: ${leadMatrix[selD][selH]}",
                                color = CosmicTextSecondary,
                                fontSize = 9.5.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = "Calls",
                                tint = CosmicSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Calls: ${callMatrix[selD][selH]}",
                                color = CosmicTextSecondary,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                }

                // AI Recommendations (Right block)
                val selD = selectedCell?.first ?: 2
                val selH = selectedCell?.second ?: 10
                val (colorBadge, textBadge, recommendationText) = when {
                    selD >= 5 -> Triple(
                        Color(0xFFEF4444),
                        "💤 Low Calling Window",
                        "Weekend slot. Call success drops by 65%. Shift efforts to SMS or WhatsApp automated campaigns."
                    )
                    selH in 9..11 || selH in 14..16 -> Triple(
                        CosmicSecondary,
                        "🔥 Peak Calling Window",
                        "Highest telecalling connectivity rates! Perfect time to place calls for high-priority leads."
                    )
                    selH == 12 || selH == 13 -> Triple(
                        Color(0xFFFBBF24),
                        "⚠️ Lunch Dip Window",
                        "Lunch hour. Leads are mostly unreachable. Use this period for database updates and WhatsApp logs."
                    )
                    selH >= 19 || selH < 8 -> Triple(
                        Color(0xFFEF4444),
                        "💤 Rest Hour Warning",
                        "Outside of standard business hours. Do not place calls to comply with telecalling guidelines."
                    )
                    else -> Triple(
                        CosmicPrimary,
                        "👍 Standard Calling Window",
                        "Moderate lead responsiveness. Suitable for secondary call tasks, reminders, and callbacks."
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1.8f)
                        .background(colorBadge.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(colorBadge.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = textBadge,
                            color = colorBadge,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = recommendationText,
                        color = CosmicTextPrimary,
                        fontSize = 8.5.sp,
                        lineHeight = 11.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun RoundRobinRouterPanel(viewModel: CRMViewModel) {
    val telecallers by viewModel.telecallers.collectAsStateWithLifecycle()
    val currentRole by viewModel.userRole.collectAsStateWithLifecycle()
    var selectedReportCaller by remember { mutableStateOf<com.example.database.TelecallerEntity?>(null) }
    
    // Determine who is next up in the round-robin queue (ordered by lastAssignedTimestamp ASC)
    val nextUpCaller = telecallers
        .filter { it.status == "Available" }
        .minByOrNull { it.lastAssignedTimestamp }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(235.dp)
            .padding(start = 12.dp, top = 0.dp, end = 6.dp, bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Live Lead Router", color = CosmicTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Real-time round-robin distribution", color = CosmicTextSecondary, fontSize = 9.sp)
                }
                
                // Pulsing indicator to show it's active
                Box(
                    modifier = Modifier
                        .background(CosmicSecondary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF34D399), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Active", color = Color(0xFF34D399), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (telecallers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CosmicPrimary, modifier = Modifier.size(24.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(telecallers) { caller ->
                        val isNextUp = nextUpCaller?.id == caller.id
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (isNextUp) CosmicPrimary.copy(alpha = 0.1f) else CosmicSurfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isNextUp) CosmicPrimary.copy(alpha = 0.5f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Status Indicator Dot
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = when (caller.status) {
                                                "Available" -> Color(0xFF34D399)
                                                "Busy" -> Color(0xFFFBBF24)
                                                else -> Color(0xFFEF4444)
                                            },
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(caller.name, color = CosmicTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        if (isNextUp) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(CosmicAccent, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text("Next Up", color = CosmicTextPrimary, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Text("${caller.assignedCount} leads assigned", color = CosmicTextSecondary, fontSize = 8.5.sp)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Interactive status badge (cycles Available -> Busy -> Offline)
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = when (caller.status) {
                                                "Available" -> Color(0xFF34D399).copy(alpha = 0.15f)
                                                "Busy" -> Color(0xFFFBBF24).copy(alpha = 0.15f)
                                                else -> Color(0xFFEF4444).copy(alpha = 0.15f)
                                            },
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { viewModel.toggleTelecallerStatus(caller) }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        caller.status,
                                        color = when (caller.status) {
                                            "Available" -> Color(0xFF34D399)
                                            "Busy" -> Color(0xFFFBBF24)
                                            else -> Color(0xFFEF4444)
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (currentRole == "Admin") {
                                    IconButton(
                                        onClick = { selectedReportCaller = caller },
                                        modifier = Modifier.size(24.dp).testTag("view_report_${caller.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BarChart,
                                            contentDescription = "View Performance Report",
                                            tint = CosmicPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.toggleTelecallerAccess(caller) },
                                        modifier = Modifier.size(24.dp).testTag("toggle_access_${caller.id}")
                                    ) {
                                        Icon(
                                            imageVector = if (caller.isAuthorized) Icons.Default.LockOpen else Icons.Default.Lock,
                                            contentDescription = if (caller.isAuthorized) "Access Authorized" else "Access Revoked",
                                            tint = if (caller.isAuthorized) Color(0xFF34D399) else Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedReportCaller?.let { caller ->
        TelecallerReportModal(
            viewModel = viewModel,
            caller = caller,
            onDismiss = { selectedReportCaller = null }
        )
    }
}

@Composable
fun InteractiveConversionFunnelSection(
    leads: List<LeadEntity>,
    fbLeads: List<FacebookAdLeadEntity>,
    viewModel: CRMViewModel
) {
    var selectedCampaign by remember { mutableStateOf("all") }
    var metricMode by remember { mutableStateOf("volume") } // "volume", "overall_pct", "step_pct"
    var selectedStage by remember { mutableStateOf(0) } // Default selected to 0 (Captured)
    var hoveredStage by remember { mutableStateOf<Int?>(null) }
    var hoverOffset by remember { mutableStateOf<Offset?>(null) }

    // Calculate stage values dynamically
    val fbSyncedCount = fbLeads.filter { it.syncStatus == "Synced" }.size

    // Live calculations
    val (capturedVal, syncedVal, contactedVal, interestedVal, convertedVal) = when (selectedCampaign) {
        "all" -> {
            val totalCaptured = fbLeads.size + leads.filter { it.source != "Facebook Ads" }.size
            val totalSynced = fbSyncedCount + leads.filter { it.source != "Facebook Ads" }.size
            val totalContacted = leads.filter { it.status in listOf("Contacted", "Interested", "Converted", "Lost") }.size
            val totalInterested = leads.filter { it.status in listOf("Interested", "Converted") }.size
            val totalConverted = leads.filter { it.status == "Converted" }.size
            listOf(totalCaptured, totalSynced, totalContacted, totalInterested, totalConverted)
        }
        "facebook" -> {
            val fbCaptured = fbLeads.size
            val fbSynced = fbSyncedCount
            val fbContacted = leads.filter { it.source == "Facebook Ads" && it.status in listOf("Contacted", "Interested", "Converted", "Lost") }.size
            val fbInterested = leads.filter { it.source == "Facebook Ads" && it.status in listOf("Interested", "Converted") }.size
            val fbConverted = leads.filter { it.source == "Facebook Ads" && it.status == "Converted" }.size
            listOf(fbCaptured, fbSynced, fbContacted, fbInterested, fbConverted)
        }
        "summer" -> listOf(350, 310, 220, 140, 68)
        else -> listOf(180, 175, 150, 115, 72) // "enterprise"
    }

    val maxVal = capturedVal.toFloat().coerceAtLeast(1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Selector Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Conversion Analytics", color = CosmicTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Interactive Lead Conversion Funnel", color = CosmicTextSecondary, fontSize = 10.sp)
            }

            // Metric Mode Selector Row
            Row(
                modifier = Modifier
                    .background(CosmicSurfaceVariant, RoundedCornerShape(8.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(
                    "volume" to "#",
                    "overall_pct" to "% Overall",
                    "step_pct" to "% Step"
                ).forEach { (mode, label) ->
                    val isSelected = metricMode == mode
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isSelected) CosmicPrimary else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { metricMode = mode }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(label, color = CosmicTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Campaign Chips Scrollable Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "all" to "All Leads (Live)",
                "facebook" to "FB Ads (Live)",
                "summer" to "Summer Promo",
                "enterprise" to "Enterprise Trial"
            ).forEach { (id, label) ->
                val isSelected = selectedCampaign == id
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isSelected) CosmicAccent.copy(alpha = 0.2f) else CosmicSurfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) CosmicAccent else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            selectedCampaign = id
                            selectedStage = 0
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(label, color = if (isSelected) CosmicTextPrimary else CosmicTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Funnel Chart Area: Left Canvas, Right Legend Table
        Box(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Interactive Funnel Canvas Draw
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    FunnelChartCanvas(
                        captured = capturedVal,
                        synced = syncedVal,
                        contacted = contactedVal,
                        interested = interestedVal,
                        converted = convertedVal,
                        selectedStage = selectedStage,
                        onStageSelected = { selectedStage = it },
                        onHoverStateChanged = { stage, offset ->
                            hoveredStage = stage
                            hoverOffset = offset
                        }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Stage Lists / Legend Row Metrics
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    val stagesData = listOf(
                        Triple("Captured", capturedVal, Icons.Default.Share),
                        Triple("Synced to CRM", syncedVal, Icons.Default.CheckCircle),
                        Triple("Contacted", contactedVal, Icons.Default.Phone),
                        Triple("Interested", interestedVal, Icons.Default.ThumbUp),
                        Triple("Converted", convertedVal, Icons.Default.Favorite)
                    )

                    stagesData.forEachIndexed { idx, (title, valAmt, icon) ->
                        val isSelected = selectedStage == idx
                        val gradientColor = when (idx) {
                            0 -> CosmicPrimary
                            1 -> CosmicAccent
                            2 -> Color(0xFF6366F1)
                            3 -> CosmicSecondary
                            else -> Color(0xFF34D399)
                        }

                        // Calculate conversion percentages
                        val overallPct = (valAmt.toFloat() / maxVal * 100).toInt()
                        val prevVal = if (idx == 0) capturedVal else stagesData[idx - 1].second
                        val stepPct = if (prevVal == 0) 0 else (valAmt.toFloat() / prevVal * 100).toInt()
                        val dropPct = 100 - stepPct

                        val displayValue = when (metricMode) {
                            "volume" -> "$valAmt"
                            "overall_pct" -> "$overallPct%"
                            else -> "$stepPct%"
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clickable { selectedStage = idx }
                                .testTag("funnel_stage_row_$idx"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) CosmicSurfaceVariant else Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(gradientColor.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(icon, contentDescription = title, tint = gradientColor, modifier = Modifier.size(12.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(title, color = CosmicTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        if (idx > 0 && dropPct > 0) {
                                            Text("↓ $dropPct% drop", color = Color.Red.copy(alpha = 0.8f), fontSize = 8.sp)
                                        } else if (idx == 0) {
                                            Text("Source Intake", color = CosmicTextSecondary, fontSize = 8.sp)
                                        } else {
                                            Text("100% Retained", color = CosmicSecondary, fontSize = 8.sp)
                                        }
                                    }
                                }

                                // Metric Badge
                                Box(
                                    modifier = Modifier
                                        .background(gradientColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(displayValue, color = gradientColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Custom interactive tooltip showing granular breakdown data (Recharts style)
            hoveredStage?.let { stageIdx ->
                val offset = hoverOffset
                if (offset != null) {
                    val title = when (stageIdx) {
                        0 -> "Captured Leads"
                        1 -> "Synced CRM Leads"
                        2 -> "Contacted Leads"
                        3 -> "Interested Leads"
                        else -> "Converted Clients"
                    }
                    
                    val countVal = when (stageIdx) {
                        0 -> capturedVal
                        1 -> syncedVal
                        2 -> contactedVal
                        3 -> interestedVal
                        else -> convertedVal
                    }
                    
                    val overallPct = (countVal.toFloat() / maxVal * 100).toInt()

                    val breakdown = when (stageIdx) {
                        0 -> listOf(
                            "Active Integrations" to "Facebook Webhooks",
                            "Data Health" to "100% Ingest Rate",
                            "Avg Ingestion Latency" to "< 1.2s",
                            "Channel Sources" to "FB Ads, Manual, API"
                        )
                        1 -> listOf(
                            "Caller Queue Routing" to "Automated",
                            "Sync Database Match" to "99.8%",
                            "Avg Sync Latency" to "0.8s",
                            "Primary Target" to "Telecall Dialer"
                        )
                        2 -> listOf(
                            "Outreach Success" to "84% Call Ratio",
                            "Primary Channels" to "65% Phone, 35% WhatsApp",
                            "Avg Call Duration" to "3m 42s",
                            "Response Status" to "92% Positive Intent"
                        )
                        3 -> listOf(
                            "Deal Win Probability" to "72% Average",
                            "Nurturing System" to "Automated Rules",
                            "Lead Interest Focus" to "Custom Demo",
                            "Avg Days in Stage" to "2.4 Days"
                        )
                        else -> listOf(
                            "Rev Pipeline Value" to "Est. $45,000 ARR",
                            "Onboarding Score" to "4.8 / 5.0",
                            "Avg Sales Cycle" to "14 Days",
                            "Referral Growth" to "12% Rate"
                        )
                    }

                    val stageColor = when (stageIdx) {
                        0 -> CosmicPrimary
                        1 -> CosmicAccent
                        2 -> Color(0xFF6366F1)
                        3 -> CosmicSecondary
                        else -> Color(0xFF34D399)
                    }

                    Card(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .width(190.dp)
                            .align(Alignment.CenterEnd)
                            .offset(y = (offset.y / 1.5f).dp - 60.dp)
                            .graphicsLayer {
                                shadowElevation = 10.dp.toPx()
                            }
                            .testTag("hover_tooltip_stage_$stageIdx"),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurface.copy(alpha = 0.96f)),
                        border = BorderStroke(1.dp, stageColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = title,
                                    color = CosmicTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(stageColor, CircleShape)
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Total Volume:", color = CosmicTextSecondary, fontSize = 9.sp)
                                Text("$countVal ($overallPct%)", color = stageColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }

                            Divider(color = CosmicSurfaceVariant, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))

                            breakdown.forEach { (label, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(label, color = CosmicTextSecondary, fontSize = 8.sp)
                                    Text(value, color = CosmicTextPrimary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom Detail & Remediation Card
        Card(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            StageInspectorCard(
                stageIndex = selectedStage,
                campaignId = selectedCampaign,
                leads = leads,
                fbLeads = fbLeads,
                viewModel = viewModel,
                stageCount = listOf(capturedVal, syncedVal, contactedVal, interestedVal, convertedVal)[selectedStage],
                maxCount = capturedVal
            )
        }
    }
}

@Composable
fun FunnelChartCanvas(
    captured: Int,
    synced: Int,
    contacted: Int,
    interested: Int,
    converted: Int,
    selectedStage: Int,
    onStageSelected: (Int) -> Unit,
    onHoverStateChanged: (Int?, Offset?) -> Unit = { _, _ -> }
) {
    val totalStages = 5
    val gap = 14f // gap between stages in pixels

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        val h = size.height.toFloat()
                        val stageHWithGap = h / totalStages
                        val tappedIdx = (offset.y / stageHWithGap).toInt().coerceIn(0, totalStages - 1)
                        onStageSelected(tappedIdx)
                        onHoverStateChanged(tappedIdx, offset)
                        tryAwaitRelease()
                        onHoverStateChanged(null, null)
                    },
                    onTap = { offset ->
                        val h = size.height.toFloat()
                        val stageHWithGap = h / totalStages
                        val tappedIdx = (offset.y / stageHWithGap).toInt().coerceIn(0, totalStages - 1)
                        onStageSelected(tappedIdx)
                        onHoverStateChanged(null, null)
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val h = size.height.toFloat()
                        val stageHWithGap = h / totalStages
                        val idx = (offset.y / stageHWithGap).toInt().coerceIn(0, totalStages - 1)
                        onHoverStateChanged(idx, offset)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val offset = change.position
                        val h = size.height.toFloat()
                        val stageHWithGap = h / totalStages
                        val idx = (offset.y / stageHWithGap).toInt().coerceIn(0, totalStages - 1)
                        onHoverStateChanged(idx, offset)
                    },
                    onDragEnd = {
                        onHoverStateChanged(null, null)
                    },
                    onDragCancel = {
                        onHoverStateChanged(null, null)
                    }
                )
            }
            .testTag("funnel_canvas")
    ) {
        val w = size.width.toFloat()
        val h = size.height.toFloat()

        val stageH = (h - (totalStages - 1) * gap) / totalStages

        val maxWidth = w * 0.95f
        val minWidth = w * 0.2f

        val volumes = listOf(captured, synced, contacted, interested, converted).map { it.toFloat() }
        val maxVol = volumes[0].coerceAtLeast(1f)

        val widths = volumes.map { vol ->
            (vol / maxVol * maxWidth).coerceAtLeast(minWidth)
        }

        val gradients = listOf(
            Brush.horizontalGradient(listOf(CosmicPrimary, CosmicAccent)),
            Brush.horizontalGradient(listOf(CosmicAccent, Color(0xFF6366F1))),
            Brush.horizontalGradient(listOf(Color(0xFF6366F1), CosmicPrimary)),
            Brush.horizontalGradient(listOf(CosmicPrimary, CosmicSecondary)),
            Brush.horizontalGradient(listOf(CosmicSecondary, Color(0xFF34D399)))
        )

        for (i in 0 until totalStages) {
            val yStart = i * (stageH + gap)
            val yEnd = yStart + stageH

            val widthTop = widths[i]
            val widthBottom = if (i < totalStages - 1) widths[i + 1] else widths[i] * 0.5f

            val xTopLeft = (w - widthTop) / 2
            val xTopRight = (w + widthTop) / 2
            val xBottomLeft = (w - widthBottom) / 2
            val xBottomRight = (w + widthBottom) / 2

            val path = Path().apply {
                moveTo(xTopLeft, yStart)
                lineTo(xTopRight, yStart)
                lineTo(xBottomRight, yEnd)
                lineTo(xBottomLeft, yEnd)
                close()
            }

            val isThisSelected = selectedStage == i
            val brush = gradients[i]

            drawPath(
                path = path,
                brush = brush,
                alpha = if (selectedStage == -1 || isThisSelected) 1f else 0.3f
            )

            drawPath(
                path = path,
                color = if (isThisSelected) Color.White else CosmicSurfaceVariant,
                style = Stroke(width = if (isThisSelected) 4f else 2f)
            )
        }
    }
}

@Composable
fun StageInspectorCard(
    stageIndex: Int,
    campaignId: String,
    leads: List<LeadEntity>,
    fbLeads: List<FacebookAdLeadEntity>,
    viewModel: CRMViewModel,
    stageCount: Int,
    maxCount: Int
) {
    val stageTitles = listOf("Captured Leads", "Synced CRM Leads", "Contacted Leads", "Interested Leads", "Converted Clients")
    val stageDescriptions = listOf(
        "Initial webhooks captured from active Facebook Form Signups.",
        "Leads successfully synchronized and populated in the CRM database.",
        "Leads who have been called or reached via outbound telecalling.",
        "Qualified prospects expressing intent, awaiting custom pricing.",
        "Closed-won client accounts with finalized subscription contracts."
    )

    val overallPct = if (maxCount == 0) 0 else (stageCount.toFloat() / maxCount * 100).toInt()

    val advice = when (stageIndex) {
        0 -> "Tip: Check Facebook Forms daily. Run webhook validations to prevent bad contact details."
        1 -> "Tip: Leads cool within 5 mins! Keep sync action rapid to maximize downstream contacted ratios."
        2 -> "Tip: Unanswered calls account for 70% of drop-off. Send a follow-up WhatsApp sequence to re-engage."
        3 -> "Tip: Friction during custom pricing limits conversion. Use AI summary logs to address objections."
        else -> "Tip: Maintain high relationship metrics to nurture advocacy, referrals, and upsell expansions."
    }

    val isLive = campaignId == "all" || campaignId == "facebook"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stageTitles[stageIndex], color = CosmicSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(stageDescriptions[stageIndex], color = CosmicTextSecondary, fontSize = 9.sp)
            }
            Box(
                modifier = Modifier
                    .background(CosmicPrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("$stageCount leads ($overallPct% of Intake)", color = CosmicTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Tip text
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CosmicSurface.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .padding(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = "Tip", tint = CosmicAccent, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(advice, color = CosmicTextSecondary, fontSize = 8.5.sp)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text("Leads Stuck in Stage", color = CosmicTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))

        if (isLive) {
            val stuckLeadsList = when (stageIndex) {
                0 -> fbLeads.filter { it.syncStatus == "Pending" }.map { Triple(it.leadName, it.leadEmail, "fb_pending_" + it.id) }
                1 -> leads.filter { (campaignId == "all" || it.source == "Facebook Ads") && it.status == "New" }.map { Triple(it.name, it.email, "lead_" + it.id) }
                2 -> leads.filter { (campaignId == "all" || it.source == "Facebook Ads") && it.status == "Contacted" }.map { Triple(it.name, it.email, "lead_" + it.id) }
                3 -> leads.filter { (campaignId == "all" || it.source == "Facebook Ads") && it.status == "Interested" }.map { Triple(it.name, it.email, "lead_" + it.id) }
                else -> leads.filter { (campaignId == "all" || it.source == "Facebook Ads") && it.status == "Converted" }.map { Triple(it.name, it.email, "lead_" + it.id) }
            }

            if (stuckLeadsList.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No live leads stuck in this funnel node.", color = CosmicTextSecondary, fontSize = 9.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(stuckLeadsList) { (name, email, idString) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CosmicSurface.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(name, color = CosmicTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(email, color = CosmicTextSecondary, fontSize = 8.sp)
                            }

                            if (idString.startsWith("fb_pending_")) {
                                val fbId = idString.substringAfter("fb_pending_").toInt()
                                val fbLead = fbLeads.find { it.id == fbId }
                                if (fbLead != null) {
                                    Button(
                                        onClick = { viewModel.syncFacebookLeadToLeads(fbLead) },
                                        colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.height(20.dp).testTag("funnel_sync_$fbId"),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Text("Sync Lead", fontSize = 8.sp, color = CosmicTextPrimary)
                                    }
                                }
                            } else {
                                val leadId = idString.substringAfter("lead_").toInt()
                                val lead = leads.find { it.id == leadId }
                                if (lead != null && stageIndex < 4) {
                                    Button(
                                        onClick = {
                                            val nextStatus = when (stageIndex) {
                                                1 -> "Contacted"
                                                2 -> "Interested"
                                                else -> "Converted"
                                            }
                                            viewModel.updateLeadStatus(lead.copy(status = nextStatus))
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.height(20.dp).testTag("funnel_advance_$leadId"),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Text("Advance", fontSize = 8.sp, color = CosmicTextPrimary)
                                    }
                                } else {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = CosmicSecondary, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val sandboxLeads = when (campaignId) {
                "summer" -> when (stageIndex) {
                    0 -> listOf(Triple("Logan Roy", "logan@waystar.com", "Capture pending"), Triple("Kendall Roy", "kendall@waystar.com", "Capture pending"))
                    1 -> listOf(Triple("Shiv Roy", "shiv@waystar.com", "Newly synced"))
                    2 -> listOf(Triple("Roman Roy", "roman@waystar.com", "Contacted via call"))
                    3 -> listOf(Triple("Tom Wambsgans", "tom@waystar.com", "Interested in Enterprise pricing"))
                    else -> listOf(Triple("Greg Hirsch", "greg@waystar.com", "Converted closed account"))
                }
                else -> when (stageIndex) {
                    0 -> listOf(Triple("Tony Stark", "tony@stark.com", "Capture pending"))
                    1 -> listOf(Triple("Pepper Potts", "pepper@stark.com", "Newly synced"))
                    2 -> listOf(Triple("Steve Rogers", "steve@avengers.org", "Called but unanswered"))
                    3 -> listOf(Triple("Bruce Banner", "bruce@avengers.org", "Interested in Gamma tech specs"))
                    else -> listOf(Triple("Thor Odinson", "thor@asgard.gov", "Converted royal account"))
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(sandboxLeads) { (name, email, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CosmicSurface.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(name, color = CosmicTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(email, color = CosmicTextSecondary, fontSize = 8.sp)
                        }
                        Text(desc, color = CosmicAccent, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun FacebookLeadItemCard(lead: FacebookAdLeadEntity, sdf: SimpleDateFormat, onSync: () -> Unit) {
    val synced = lead.syncStatus == "Synced"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(lead.leadName, color = CosmicTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (synced) CosmicSecondary.copy(alpha = 0.2f) else Color.Yellow.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            lead.syncStatus,
                            color = if (synced) CosmicSecondary else Color.Yellow,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text("Ad Campaign: ${lead.adName} | ${lead.formName}", color = CosmicTextSecondary, fontSize = 10.sp)
                Text("Captured: ${sdf.format(Date(lead.submittedTimestamp))}", color = CosmicTextSecondary, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Contact: ${lead.leadPhone} | ${lead.leadEmail}", color = CosmicPrimary, fontSize = 11.sp)
            }

            if (!synced) {
                Button(
                    onClick = onSync,
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("sync_fb_lead_${lead.id}")
                ) {
                    Text("Sync to CRM", fontSize = 10.sp)
                }
            } else {
                Icon(Icons.Default.Check, contentDescription = "Synced", tint = CosmicSecondary, modifier = Modifier.padding(end = 16.dp))
            }
        }
    }
}

// --- CRM SYNC INTEGRATION HUB SUBTAB ---
@Composable
fun CRMHubTab(viewModel: CRMViewModel, onTabNavigate: (String) -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val logs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val rules by viewModel.syncRules.collectAsStateWithLifecycle()
    val reminderRules by viewModel.reminderRules.collectAsStateWithLifecycle()
    val dismissedReminders by viewModel.dismissedReminders.collectAsStateWithLifecycle()
    val leads by viewModel.leads.collectAsStateWithLifecycle()
    val leadReminders by viewModel.leadReminders.collectAsStateWithLifecycle()
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val currentRole by viewModel.userRole.collectAsStateWithLifecycle()

    val availableTabs = remember(currentRole) {
        if (currentRole == "Admin") {
            listOf(
                Triple("rules", "Sync Rules", Icons.Default.Settings),
                Triple("reminders", "Follow-ups", Icons.Default.Timer),
                Triple("logs", "API Logs", Icons.Default.List),
                Triple("reports", "Bulk Exports", Icons.Default.Assignment)
            )
        } else {
            listOf(
                Triple("reminders", "Follow-ups", Icons.Default.Timer),
                Triple("logs", "API Logs", Icons.Default.List)
            )
        }
    }

    var selectedSection by remember { mutableStateOf("reminders") }
    var showAddRuleDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentRole, availableTabs) {
        if (availableTabs.none { it.first == selectedSection }) {
            selectedSection = availableTabs.firstOrNull()?.first ?: "reminders"
        }
    }

    val activeRules = remember(reminderRules) { reminderRules.filter { it.isEnabled } }
    val dismissedIds = remember(dismissedReminders) { dismissedReminders.map { it.leadId }.toSet() }
    val now = System.currentTimeMillis()

    val pendingReminders = remember(leads, activeRules, dismissedIds) {
        val list = mutableListOf<Pair<LeadEntity, ReminderRuleEntity>>()
        leads.forEach { lead ->
            if (!dismissedIds.contains(lead.id)) {
                val matchedRules = activeRules.filter { rule ->
                    rule.targetStatus == "All" || lead.status.lowercase() == rule.targetStatus.lowercase()
                }
                matchedRules.forEach { rule ->
                    if ((now - lead.lastContacted) > (rule.timeframeHours * 3600 * 1000L)) {
                        list.add(lead to rule)
                    }
                }
            }
        }
        list.distinctBy { it.first.id }
    }

    val tabIndex = remember(availableTabs, selectedSection) {
        val idx = availableTabs.indexOfFirst { it.first == selectedSection }
        if (idx >= 0) idx else 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Hub configuration block
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("CRM Integration & Automation Hub", color = CosmicTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Manage synchronization flows, trigger rules, automated follow-ups, and audit logs.", color = CosmicTextSecondary, fontSize = 11.sp)
                }
                Button(
                    onClick = { viewModel.triggerHubManualSync() },
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("trigger_manual_sync")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync Now")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Trigger CRM Sync", fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selector tabs
        TabRow(
            selectedTabIndex = tabIndex,
            containerColor = Color.Transparent,
            contentColor = CosmicPrimary,
            divider = {}
        ) {
            availableTabs.forEach { (route, label, icon) ->
                Tab(
                    selected = selectedSection == route,
                    onClick = { selectedSection = route },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (route == "reminders" && pendingReminders.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFEF4444), CircleShape)
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        "${pendingReminders.size}",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("tab_sub_$route")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (selectedSection) {
            "rules" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(rules) { rule ->
                        SyncRuleItemCard(rule, onToggle = { viewModel.toggleSyncRule(rule) })
                    }
                }
            }
            "reminders" -> {
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    // Header row with "Add Rule" button and "Clear Snoozed"
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Automated Follow-up Engine",
                            color = CosmicTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (dismissedReminders.isNotEmpty()) {
                                TextButton(
                                    onClick = { viewModel.clearAllDismissedReminders() },
                                    modifier = Modifier.height(32.dp).testTag("clear_dismissed_reminders")
                                ) {
                                    Text("Reset Snoozed", color = CosmicPrimary, fontSize = 11.sp)
                                }
                            }
                            Button(
                                onClick = { showAddRuleDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("add_reminder_rule_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Rule", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Rule", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Inner Selection chips
                    var subSection by remember { mutableStateOf("active_tasks") }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val activeSelected = subSection == "active_tasks"
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (activeSelected) CosmicPrimary.copy(alpha = 0.2f) else Color.Transparent,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (activeSelected) CosmicPrimary else CosmicSurfaceVariant,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable { subSection = "active_tasks" }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("sub_tab_active_tasks")
                        ) {
                            Text(
                                text = "Active Reminders (${pendingReminders.size})",
                                color = if (activeSelected) CosmicPrimary else CosmicTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val rulesSelected = subSection == "rules"
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (rulesSelected) CosmicPrimary.copy(alpha = 0.2f) else Color.Transparent,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (rulesSelected) CosmicPrimary else CosmicSurfaceVariant,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable { subSection = "rules" }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("sub_tab_reminder_rules")
                        ) {
                            Text(
                                text = "Follow-up Rules (${reminderRules.size})",
                                color = if (rulesSelected) CosmicPrimary else CosmicTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val scheduledSelected = subSection == "scheduled_reminders"
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (scheduledSelected) CosmicPrimary.copy(alpha = 0.2f) else Color.Transparent,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (scheduledSelected) CosmicPrimary else CosmicSurfaceVariant,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable { subSection = "scheduled_reminders" }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("sub_tab_scheduled_reminders")
                        ) {
                            Text(
                                text = "Scheduled (${leadReminders.size})",
                                color = if (scheduledSelected) CosmicPrimary else CosmicTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (subSection == "active_tasks") {
                        if (pendingReminders.isEmpty()) {
                            // All caught up success screen
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(CosmicSurface, RoundedCornerShape(16.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(CosmicSecondary.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Success",
                                            tint = CosmicSecondary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "All Caught Up!",
                                        color = CosmicTextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Every single prospect has been contacted within their defined timeframe. No potential customer left untouched!",
                                        color = CosmicTextSecondary,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(pendingReminders) { (lead, rule) ->
                                    ReminderTaskItemCard(
                                        lead = lead,
                                        rule = rule,
                                        onCall = {
                                            viewModel.showCallPromptForLead(lead)
                                            onTabNavigate("calls")
                                        },
                                        onWhatsApp = {
                                            viewModel.selectLead(lead)
                                            launchWhatsAppChat(context, lead.phone, null, viewModel, lead.id)
                                            onTabNavigate("whatsapp")
                                        },
                                        onDismiss = {
                                            viewModel.dismissReminderForLead(lead.id)
                                        }
                                    )
                                }
                            }
                        }
                    } else if (subSection == "scheduled_reminders") {
                        // Custom Scheduled Reminders Dashboard!
                        if (leadReminders.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Alarm,
                                        contentDescription = "No reminders",
                                        tint = CosmicTextSecondary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "No Custom Follow-Ups Scheduled",
                                        color = CosmicTextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Tap on any lead from your dashboard list, select 'Scheduler', and configure dynamic timers so you never miss critical demonstrations or contract reviews.",
                                        color = CosmicTextSecondary,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            var filterCompleted by remember { mutableStateOf("Pending") } // "All", "Pending", "Completed"

                            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                // Filters Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Filter:", color = CosmicTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    listOf("All", "Pending", "Completed").forEach { status ->
                                        val isSel = filterCompleted == status
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = if (isSel) CosmicPrimary.copy(alpha = 0.15f) else Color.Transparent,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .border(1.dp, if (isSel) CosmicPrimary else CosmicSurfaceVariant, RoundedCornerShape(12.dp))
                                                .clickable { filterCompleted = status }
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(status, color = if (isSel) CosmicPrimary else CosmicTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                val filteredReminders = remember(leadReminders, filterCompleted) {
                                    leadReminders.filter { reminder ->
                                        when (filterCompleted) {
                                            "Pending" -> !reminder.isCompleted
                                            "Completed" -> reminder.isCompleted
                                            else -> true
                                        }
                                    }.sortedBy { it.timestamp }
                                }

                                if (filteredReminders.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No matching scheduled reminders found.", color = CosmicTextSecondary, fontSize = 12.sp)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(filteredReminders) { reminder ->
                                            ScheduledReminderDashboardCard(
                                                reminder = reminder,
                                                onToggle = { viewModel.toggleLeadReminderCompleted(reminder.id) },
                                                onDelete = { viewModel.deleteLeadReminder(reminder.id) },
                                                onAction = {
                                                    val matchedLead = leads.find { it.id == reminder.leadId }
                                                    if (matchedLead != null) {
                                                        viewModel.showCallPromptForLead(matchedLead)
                                                        onTabNavigate("calls")
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Rules list
                        if (reminderRules.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No automated reminder rules configured.", color = CosmicTextSecondary)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(reminderRules) { rule ->
                                    ReminderRuleItemCard(
                                        rule = rule,
                                        onToggle = { viewModel.toggleReminderRule(rule) },
                                        onDelete = { viewModel.deleteReminderRule(rule.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            "logs" -> {
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Hub API Sync Logs", color = CosmicTextSecondary, fontSize = 11.sp)
                        TextButton(onClick = { viewModel.clearSyncLogs() }) {
                            Text("Clear Logs", color = Color.Red, fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(CosmicSurface, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(logs) { log ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        "[${sdf.format(Date(log.timestamp))}]",
                                        color = CosmicPrimary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.width(72.dp)
                                    )
                                    Text(
                                        "${log.service}: ${log.details}",
                                        color = if (log.status == "Success") CosmicTextPrimary else Color.Red,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            "reports" -> {
                AdminReportsView(viewModel)
            }
        }
    }

    if (showAddRuleDialog) {
        AddReminderRuleDialog(
            onDismiss = { showAddRuleDialog = false },
            onConfirm = { title, hours, targetStatus ->
                viewModel.addReminderRule(title, hours, targetStatus)
                showAddRuleDialog = false
            }
        )
    }
}

@Composable
fun AdminReportsView(viewModel: CRMViewModel) {
    val context = LocalContext.current
    var previewTitle by remember { mutableStateOf("Select a Report to Generate") }
    var previewText by remember { mutableStateOf("") }
    var reportType by remember { mutableStateOf("") } // "leads", "calls", "pdf"

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Admin Executive Report Center",
                    color = CosmicTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Generate and download real-time CSV analytics and formatted PDF summaries.",
                    color = CosmicTextSecondary,
                    fontSize = 9.5.sp
                )
            }
        }

        // Row of action buttons to generate reports
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    previewTitle = "Lead Directory (CSV)"
                    previewText = viewModel.exportLeadsToCsv()
                    reportType = "leads"
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (reportType == "leads") CosmicPrimary else CosmicSurfaceVariant,
                    contentColor = CosmicTextPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(44.dp).testTag("generate_leads_report_btn")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text("Leads CSV", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = {
                    previewTitle = "Call History & Transcriptions (CSV)"
                    previewText = viewModel.exportCallsToCsv()
                    reportType = "calls"
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (reportType == "calls") CosmicPrimary else CosmicSurfaceVariant,
                    contentColor = CosmicTextPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(44.dp).testTag("generate_calls_report_btn")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text("Calls CSV", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = {
                    previewTitle = "Executive Performance Review (PDF Format)"
                    previewText = viewModel.generatePerformanceReportPdfText()
                    reportType = "pdf"
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (reportType == "pdf") CosmicPrimary else CosmicSurfaceVariant,
                    contentColor = CosmicTextPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(44.dp).testTag("generate_pdf_report_btn")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text("PDF Report", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Preview box
        if (previewText.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = previewTitle,
                            color = CosmicPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Copy button
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("CRM Report", previewText)
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, "Report copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp).testTag("copy_report_btn")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Report", tint = CosmicTextPrimary, modifier = Modifier.size(14.dp))
                            }
                            
                            // Share button
                            IconButton(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, previewText)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Export $previewTitle")
                                    context.startActivity(shareIntent)
                                },
                                modifier = Modifier.size(28.dp).testTag("share_report_btn")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share Report", tint = CosmicSecondary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Scrollable text content
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(CosmicBackground, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        Text(
                            text = previewText,
                            color = CosmicTextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Click Copy or Share to save the report. CSV logs can be opened directly in Microsoft Excel or Google Sheets.",
                        color = CosmicTextSecondary,
                        fontSize = 9.sp,
                        lineHeight = 12.sp
                    )
                }
            }
        } else {
            // Placeholder Card
            Card(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = CosmicTextSecondary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No Report Generated",
                        color = CosmicTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Select any report type above to instantly aggregate database statistics and download performance logs.",
                        color = CosmicTextSecondary,
                        fontSize = 9.5.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SyncRuleItemCard(rule: com.example.database.SyncRuleEntity, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.ruleName, color = CosmicTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Map: ${rule.sourceService} -> ${rule.targetService}", color = CosmicTextSecondary, fontSize = 10.sp)
                Text("Event trigger: '${rule.triggerEvent}'", color = CosmicAccent, fontSize = 10.sp)
            }
            Switch(
                checked = rule.isActive,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CosmicSecondary,
                    checkedTrackColor = CosmicSecondary.copy(alpha = 0.3f),
                    uncheckedThumbColor = CosmicTextSecondary,
                    uncheckedTrackColor = CosmicSurfaceVariant
                )
            )
        }
    }
}

// --- ADD NEW LEAD DIALOG ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLeadDialog(onDismiss: () -> Unit, onConfirm: (String, String, String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("Manual") }
    var status by remember { mutableStateOf("New") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Add New Lead Record",
                    color = CosmicTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    "Create a custom manual entry for direct-dial or CRM automations.",
                    color = CosmicTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name *") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_lead_name_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CosmicTextPrimary,
                        unfocusedTextColor = CosmicTextPrimary,
                        focusedBorderColor = CosmicPrimary,
                        unfocusedBorderColor = CosmicSurfaceVariant
                    )
                )

                // Phone field
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number *") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_lead_phone_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CosmicTextPrimary,
                        unfocusedTextColor = CosmicTextPrimary,
                        focusedBorderColor = CosmicPrimary,
                        unfocusedBorderColor = CosmicSurfaceVariant
                    )
                )

                // Email field
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_lead_email_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CosmicTextPrimary,
                        unfocusedTextColor = CosmicTextPrimary,
                        focusedBorderColor = CosmicPrimary,
                        unfocusedBorderColor = CosmicSurfaceVariant
                    )
                )

                // Company field
                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Company Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_lead_company_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CosmicTextPrimary,
                        unfocusedTextColor = CosmicTextPrimary,
                        focusedBorderColor = CosmicPrimary,
                        unfocusedBorderColor = CosmicSurfaceVariant
                    )
                )

                // Lead Source field
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text("Lead Source") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_lead_source_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CosmicTextPrimary,
                        unfocusedTextColor = CosmicTextPrimary,
                        focusedBorderColor = CosmicPrimary,
                        unfocusedBorderColor = CosmicSurfaceVariant
                    )
                )

                // Initial Status Tags Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Initial Pipeline Status",
                        color = CosmicTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val statusOptions = listOf("New", "Contacted", "Interested", "Converted", "Lost")
                        statusOptions.forEach { option ->
                            val isSelected = status == option
                            val backgroundColor = if (isSelected) {
                                when (option) {
                                    "New" -> CosmicPrimary.copy(alpha = 0.25f)
                                    "Contacted" -> CosmicSecondary.copy(alpha = 0.25f)
                                    "Interested" -> CosmicAccent.copy(alpha = 0.25f)
                                    "Converted" -> Color(0xFF25D366).copy(alpha = 0.25f)
                                    else -> CosmicTextSecondary.copy(alpha = 0.25f)
                                }
                            } else {
                                CosmicSurfaceVariant.copy(alpha = 0.3f)
                            }

                            val borderColor = if (isSelected) {
                                when (option) {
                                    "New" -> CosmicPrimary
                                    "Contacted" -> CosmicSecondary
                                    "Interested" -> CosmicAccent
                                    "Converted" -> Color(0xFF25D366)
                                    else -> CosmicTextSecondary
                                }
                            } else {
                                CosmicSurfaceVariant
                            }

                            val textColor = if (isSelected) {
                                CosmicTextPrimary
                            } else {
                                CosmicTextSecondary
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(backgroundColor)
                                    .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(8.dp))
                                    .clickable { status = option }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("add_lead_status_tag_$option"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = option,
                                    color = textColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotEmpty() && phone.isNotEmpty()) {
                        onConfirm(name, phone, email, company, source, status)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                enabled = name.isNotEmpty() && phone.isNotEmpty(),
                modifier = Modifier.testTag("add_lead_confirm_button")
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("add_lead_cancel_button")
            ) {
                Text("Cancel", color = CosmicTextSecondary)
            }
        },
        containerColor = CosmicSurface
    )
}

// --- PIPELINE KANBAN BOARD SYSTEM ---

class KanbanDragState {
    var draggedLead by mutableStateOf<LeadEntity?>(null)
    var dragAmount by mutableStateOf(Offset.Zero)
    var dragPositionInRoot by mutableStateOf(Offset.Zero)
}

@Composable
fun PipelineKanbanTab(
    viewModel: CRMViewModel,
    leads: List<LeadEntity>,
    showAddLeadDialog: Boolean,
    onDismissDialog: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tabPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    val dragState = remember { KanbanDragState() }
    val columnBoundsMap = remember { mutableMapOf<String, Rect>() }
    
    // Status list
    val statuses = listOf("New", "Contacted", "Interested", "Converted", "Lost")
    
    // Status colors
    val statusColors = mapOf(
        "New" to CosmicPrimary,
        "Contacted" to CosmicAccent,
        "Interested" to Color(0xFFF59E0B),
        "Converted" to CosmicSecondary,
        "Lost" to Color(0xFFEF4444)
    )

    // Status titles
    val statusTitles = mapOf(
        "New" to "NEW LEADS",
        "Contacted" to "CONTACTED",
        "Interested" to "INTERESTED",
        "Converted" to "CONVERTED",
        "Lost" to "LOST / CLOSED"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                tabPositionInRoot = coords.positionInRoot()
            }
            .background(CosmicBackground)
            .padding(12.dp)
    ) {
        val isCompact = maxWidth < 600.dp
        var selectedStatusIndex by remember { mutableStateOf(0) }

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Lead Pipeline Board",
                        color = CosmicTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isCompact) "Select status tab below to view leads" else "Drag cards or click menu options to transition prospects",
                        color = CosmicTextSecondary,
                        fontSize = 12.sp
                    )
                }
                
                if (!isCompact) {
                    // Info Legend
                    Box(
                        modifier = Modifier
                            .background(CosmicSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(8.dp).background(CosmicSecondary, CircleShape))
                            Text("Interactive Drag-and-Drop Active", color = CosmicTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (isCompact) {
                // Scrollable tab row for mobile screen layout (Flexbox/Grid equivalent)
                ScrollableTabRow(
                    selectedTabIndex = selectedStatusIndex,
                    containerColor = Color.Transparent,
                    contentColor = CosmicPrimary,
                    edgePadding = 0.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedStatusIndex]),
                            color = CosmicPrimary
                        )
                    },
                    divider = {},
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    statuses.forEachIndexed { index, status ->
                        val count = leads.filter { it.status == status }.size
                        Tab(
                            selected = selectedStatusIndex == index,
                            onClick = { selectedStatusIndex = index },
                            text = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = status.uppercase(),
                                        color = if (selectedStatusIndex == index) CosmicPrimary else CosmicTextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (selectedStatusIndex == index) CosmicPrimary.copy(alpha = 0.2f) else CosmicSurfaceVariant.copy(alpha = 0.5f),
                                                shape = CircleShape
                                            )
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "$count",
                                            color = if (selectedStatusIndex == index) CosmicPrimary else CosmicTextSecondary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        )
                    }
                }

                // Render single column based on selection
                val status = statuses[selectedStatusIndex]
                val columnLeads = leads.filter { it.status == status }
                val accentColor = statusColors[status] ?: CosmicPrimary

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(
                            width = 0.5.dp,
                            color = CosmicSurfaceVariant,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        Divider(color = CosmicSurfaceVariant, thickness = 1.dp, modifier = Modifier.padding(bottom = 10.dp))
                        
                        if (columnLeads.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(
                                        width = 1.dp,
                                        color = CosmicSurfaceVariant.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No prospects in this stage.\nMove leads here or add new records.",
                                    color = CosmicTextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(columnLeads, key = { it.id }) { lead ->
                                    KanbanLeadCard(
                                        lead = lead,
                                        accentColor = accentColor,
                                        isBeingDragged = false,
                                        dragState = dragState,
                                        tabPositionInRoot = tabPositionInRoot,
                                        viewModel = viewModel,
                                        statusColors = statusColors,
                                        modifier = Modifier.animateItem(),
                                        onDropResolved = {}
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Wide screen row multi-column layout
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    statuses.forEach { status ->
                        val columnLeads = leads.filter { it.status == status }
                        val isHovered = dragState.draggedLead != null && run {
                            val bounds = columnBoundsMap[status]
                            bounds != null && dragState.dragPositionInRoot.x in bounds.left..bounds.right &&
                                    dragState.dragPositionInRoot.y in bounds.top..bounds.bottom
                        }
                        
                        val accentColor = statusColors[status] ?: CosmicPrimary

                        val animatedBorderWidth by animateDpAsState(
                            targetValue = if (isHovered) 2.dp else 0.5.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "board_column_border_width"
                        )
                        val animatedBorderColor by animateColorAsState(
                            targetValue = if (isHovered) accentColor else CosmicSurfaceVariant,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "board_column_border_color"
                        )
                        val animatedBgColor by animateColorAsState(
                            targetValue = if (isHovered) CosmicSurface.copy(alpha = 0.85f) else CosmicSurface,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "board_column_bg"
                        )
                        
                        Card(
                            modifier = Modifier
                                .width(260.dp)
                                .fillMaxHeight()
                                .onGloballyPositioned { coords ->
                                    val parentCoord = coords.positionInRoot()
                                    val size = coords.size
                                    columnBoundsMap[status] = Rect(
                                        left = parentCoord.x,
                                        top = parentCoord.y,
                                        right = parentCoord.x + size.width,
                                        bottom = parentCoord.y + size.height
                                    )
                                }
                                .border(
                                    width = animatedBorderWidth,
                                    color = animatedBorderColor,
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = animatedBgColor
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                            ) {
                                // Column Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Bullet indicator
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(accentColor, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = statusTitles[status] ?: status,
                                            color = CosmicTextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    
                                    // Count badge
                                    Box(
                                        modifier = Modifier
                                            .background(accentColor.copy(alpha = 0.15f), CircleShape)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${columnLeads.size}",
                                            color = accentColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                Divider(color = CosmicSurfaceVariant, thickness = 1.dp, modifier = Modifier.padding(bottom = 10.dp))
                                
                                if (columnLeads.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .weight(1f)
                                            .border(
                                                width = 1.dp,
                                                color = CosmicSurfaceVariant.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Empty Column\nDrag prospects here",
                                            color = CosmicTextSecondary,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(columnLeads, key = { it.id }) { lead ->
                                            val isBeingDragged = dragState.draggedLead?.id == lead.id
                                            
                                            KanbanLeadCard(
                                                lead = lead,
                                                accentColor = accentColor,
                                                isBeingDragged = isBeingDragged,
                                                dragState = dragState,
                                                tabPositionInRoot = tabPositionInRoot,
                                                viewModel = viewModel,
                                                statusColors = statusColors,
                                                modifier = Modifier.animateItem(),
                                                onDropResolved = { dropPos ->
                                                    val hovered = columnBoundsMap.entries.find { entry ->
                                                        val rect = entry.value
                                                        dropPos.x in rect.left..rect.right &&
                                                        dropPos.y in rect.top..rect.bottom
                                                    }?.key
                                                    
                                                    if (hovered != null && hovered != lead.status) {
                                                        viewModel.updateLeadStatus(lead.copy(status = hovered))
                                                    }
                                                    dragState.draggedLead = null
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Overlay element for currently dragged card
        dragState.draggedLead?.let { lead ->
            val accentColor = statusColors[lead.status] ?: CosmicPrimary
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (dragState.dragPositionInRoot.x - tabPositionInRoot.x - 120).toInt(),
                            y = (dragState.dragPositionInRoot.y - tabPositionInRoot.y - 45).toInt()
                        )
                    }
                    .width(240.dp)
                    .graphicsLayer {
                        scaleX = 1.08f
                        scaleY = 1.08f
                        rotationZ = -3.0f // Beautiful physical card tilt mimicking Framer Motion
                        shadowElevation = 16.dp.toPx()
                        alpha = 0.9f
                    }
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, accentColor)
                ) {
                    KanbanLeadCardContent(
                        lead = lead,
                        accentColor = accentColor,
                        viewModel = viewModel,
                        statusColors = statusColors,
                        isOverlay = true
                    )
                }
            }
        }
    }

    if (showAddLeadDialog) {
        AddLeadDialog(
            onDismiss = onDismissDialog,
            onConfirm = { name, phone, email, company, source, status ->
                viewModel.addNewLead(name, phone, email, company, source, status)
                onDismissDialog()
            }
        )
    }
}

@Composable
fun KanbanLeadCard(
    lead: LeadEntity,
    accentColor: Color,
    isBeingDragged: Boolean,
    dragState: KanbanDragState,
    tabPositionInRoot: Offset,
    viewModel: CRMViewModel,
    statusColors: Map<String, Color>,
    onDropResolved: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    var cardPositionInRoot by remember { mutableStateOf(Offset.Zero) }

    val alphaAnim by animateFloatAsState(
        targetValue = if (isBeingDragged) 0.2f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "kanban_card_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                cardPositionInRoot = coords.positionInRoot()
            }
            .pointerInput(lead) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { initialPosition ->
                        dragState.draggedLead = lead
                        dragState.dragAmount = Offset.Zero
                        dragState.dragPositionInRoot = cardPositionInRoot + initialPosition
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragState.dragAmount += amount
                        dragState.dragPositionInRoot += amount
                    },
                    onDragEnd = {
                        onDropResolved(dragState.dragPositionInRoot)
                    },
                    onDragCancel = {
                        dragState.draggedLead = null
                    }
                )
            }
            .graphicsLayer {
                alpha = alphaAnim
            }
            .testTag("kanban_card_${lead.id}")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            KanbanLeadCardContent(
                lead = lead,
                accentColor = accentColor,
                viewModel = viewModel,
                statusColors = statusColors,
                isOverlay = false
            )
        }
    }
}

@Composable
fun KanbanLeadCardContent(
    lead: LeadEntity,
    accentColor: Color,
    viewModel: CRMViewModel,
    statusColors: Map<String, Color>,
    isOverlay: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showQuickDetailsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showQuickDetailsDialog = true }
            .padding(10.dp)
    ) {
        // Line 1: Lead Name and Quick Move Dropdown
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = lead.name,
                color = CosmicTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            if (!isOverlay) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("kanban_card_menu_${lead.id}")
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Quick Move",
                            tint = CosmicTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(CosmicSurface)
                    ) {
                        Text(
                            text = "Transition Stage:",
                            color = CosmicTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        statusColors.forEach { (status, color) ->
                            val isCurrent = lead.status == status
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(color, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = status,
                                            color = if (isCurrent) CosmicPrimary else CosmicTextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.updateLeadStatus(lead.copy(status = status))
                                    showMenu = false
                                },
                                modifier = Modifier.testTag("kanban_card_menu_item_${lead.id}_$status")
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Line 2: Company / Source badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = lead.company.ifEmpty { "Individual Prospect" },
                color = CosmicTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            // Source Tag
            Box(
                modifier = Modifier
                    .background(CosmicSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = lead.source,
                    color = CosmicTextPrimary,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Line 3: Telecaller assignee and Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Assignee Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Assigned to",
                    tint = CosmicTextSecondary,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = lead.assignedCaller,
                    color = if (lead.assignedCaller == "Unassigned") CosmicTextSecondary else CosmicPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isOverlay) {
                // Outbound Interactive Action Icons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Start Live Phone Simulator Call
                    IconButton(
                        onClick = { viewModel.showCallPromptForLead(lead) },
                        modifier = Modifier
                            .size(26.dp)
                            .background(CosmicSecondary.copy(alpha = 0.15f), CircleShape)
                            .testTag("kanban_call_${lead.id}")
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = "Simulate outbound phone call",
                            tint = CosmicSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    // Open Quick Chat WhatsApp
                    IconButton(
                        onClick = {
                            viewModel.selectLead(lead)
                            launchWhatsAppChat(context, lead.phone, null, viewModel, lead.id)
                        },
                        modifier = Modifier
                            .size(26.dp)
                            .background(CosmicPrimary.copy(alpha = 0.15f), CircleShape)
                            .testTag("kanban_whatsapp_${lead.id}")
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Access stored chat logs",
                            tint = CosmicPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }

    // Detail Popover Dialog
    if (showQuickDetailsDialog && !isOverlay) {
        AlertDialog(
            onDismissRequest = { showQuickDetailsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(accentColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(lead.name, color = CosmicTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Text("Company: ", color = CosmicTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(lead.company.ifEmpty { "N/A" }, color = CosmicTextPrimary, fontSize = 11.sp)
                    }
                    Row(
                        modifier = Modifier.clickable {
                            viewModel.showCallPromptForLead(lead)
                        }
                    ) {
                        Text("Phone: ", color = CosmicTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(lead.phone, color = CosmicPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row {
                        Text("Email: ", color = CosmicTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(lead.email, color = CosmicTextPrimary, fontSize = 11.sp)
                    }
                    Row {
                        Text("Source Campaign: ", color = CosmicTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(lead.source, color = CosmicTextPrimary, fontSize = 11.sp)
                    }
                    Row {
                        Text("Pipeline Stage: ", color = CosmicTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(lead.status, color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row {
                        Text("Assigned Telecaller: ", color = CosmicTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(lead.assignedCaller, color = CosmicPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row {
                        Text("Last Action Date: ", color = CosmicTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        val dateStr = remember(lead.lastContacted) {
                            SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault()).format(Date(lead.lastContacted))
                        }
                        Text(dateStr, color = CosmicTextPrimary, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showQuickDetailsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary)
                ) {
                    Text("Close")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        viewModel.selectLead(lead)
                        showQuickDetailsDialog = false
                    },
                    border = BorderStroke(1.dp, CosmicPrimary)
                ) {
                    Text("Open details", color = CosmicPrimary)
                }
            },
            containerColor = CosmicSurface
        )
    }
}

// --- UTILITIES ---
private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}

// --- AUTOMATED REMINDERS DIALOG & CARDS ---
@Composable
fun AddReminderRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, hours: Int, targetStatus: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var hoursStr by remember { mutableStateOf("24") }
    var targetStatus by remember { mutableStateOf("All") }
    
    val statuses = listOf("All", "New", "Contacted", "Interested", "Converted", "Lost")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Create Follow-up Automation Rule",
                color = CosmicTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Define a trigger condition. If a prospect in the specified stage is left untouched for longer than the timeframe, an alert task will be automatically generated.",
                    color = CosmicTextSecondary,
                    fontSize = 11.sp
                )

                // Rule Name Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Rule Title") },
                    placeholder = { Text("e.g. Fresh Lead Urgency") },
                    modifier = Modifier.fillMaxWidth().testTag("rule_title_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CosmicPrimary,
                        unfocusedBorderColor = CosmicSurfaceVariant,
                        focusedLabelColor = CosmicPrimary,
                        unfocusedLabelColor = CosmicTextSecondary,
                        focusedTextColor = CosmicTextPrimary,
                        unfocusedTextColor = CosmicTextPrimary
                    ),
                    singleLine = true
                )

                // Timeframe Input
                OutlinedTextField(
                    value = hoursStr,
                    onValueChange = { hoursStr = it.filter { char -> char.isDigit() } },
                    label = { Text("Timeframe Threshold (Hours)") },
                    placeholder = { Text("e.g. 24") },
                    modifier = Modifier.fillMaxWidth().testTag("rule_hours_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CosmicPrimary,
                        unfocusedBorderColor = CosmicSurfaceVariant,
                        focusedLabelColor = CosmicPrimary,
                        unfocusedLabelColor = CosmicTextSecondary,
                        focusedTextColor = CosmicTextPrimary,
                        unfocusedTextColor = CosmicTextPrimary
                    ),
                    singleLine = true
                )

                // Target Status chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Target Lead Pipeline Stage:",
                        color = CosmicTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            statuses.take(3).forEach { status ->
                                val isSelected = targetStatus == status
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = if (isSelected) CosmicPrimary.copy(alpha = 0.2f) else CosmicSurfaceVariant.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) CosmicPrimary else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { targetStatus = status }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = status,
                                        color = if (isSelected) CosmicPrimary else CosmicTextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            statuses.drop(3).forEach { status ->
                                val isSelected = targetStatus == status
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = if (isSelected) CosmicPrimary.copy(alpha = 0.2f) else CosmicSurfaceVariant.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) CosmicPrimary else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { targetStatus = status }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = status,
                                        color = if (isSelected) CosmicPrimary else CosmicTextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val hours = hoursStr.toIntOrNull() ?: 24
                    if (title.isNotBlank()) {
                        onConfirm(title, hours, targetStatus)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                enabled = title.isNotBlank() && hoursStr.isNotBlank(),
                modifier = Modifier.testTag("rule_confirm_button")
            ) {
                Text("Save Rule")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, CosmicSurfaceVariant),
                modifier = Modifier.testTag("rule_cancel_button")
            ) {
                Text("Cancel", color = CosmicTextPrimary)
            }
        },
        containerColor = CosmicSurface
    )
}

@Composable
fun ReminderRuleItemCard(
    rule: com.example.database.ReminderRuleEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("reminder_rule_card_${rule.id}"),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, CosmicSurfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.title,
                    color = CosmicTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(CosmicPrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Stage: ${rule.targetStatus}",
                            color = CosmicPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(CosmicSecondary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "> ${rule.timeframeHours} hrs untouched",
                            color = CosmicSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CosmicPrimary,
                        checkedTrackColor = CosmicPrimary.copy(alpha = 0.4f),
                        uncheckedThumbColor = CosmicTextSecondary,
                        uncheckedTrackColor = CosmicSurfaceVariant
                    ),
                    modifier = Modifier.scale(0.8f).testTag("rule_toggle_${rule.id}")
                )
                
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp).testTag("rule_delete_${rule.id}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Rule",
                        tint = Color.Red.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ReminderTaskItemCard(
    lead: com.example.database.LeadEntity,
    rule: com.example.database.ReminderRuleEntity,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onDismiss: () -> Unit
) {
    val durationText = remember(lead.lastContacted) {
        val diff = System.currentTimeMillis() - lead.lastContacted
        val hrs = diff / 3600000
        val days = hrs / 24
        when {
            days > 0 -> "$days ${if (days == 1L) "day" else "days"} and ${hrs % 24} ${if (hrs % 24 == 1L) "hour" else "hours"}"
            hrs > 0 -> "$hrs ${if (hrs == 1L) "hour" else "hours"}"
            else -> "under an hour"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("reminder_task_card_${lead.id}"),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CosmicAccent.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = lead.name,
                            color = CosmicTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(CosmicAccent.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = lead.status.uppercase(),
                                color = CosmicAccent,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = lead.company.ifEmpty { "Individual Prospect" },
                        color = CosmicTextSecondary,
                        fontSize = 11.sp
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(28.dp)
                        .background(CosmicSurfaceVariant.copy(alpha = 0.5f), CircleShape)
                        .testTag("reminder_dismiss_${lead.id}")
                ) {
                    Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = "Snooze Alert",
                        tint = CosmicTextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CosmicSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Untouched Warning",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Untouched for $durationText",
                        color = Color(0xFFF59E0B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Triggered by: '${rule.title}' (> ${rule.timeframeHours}h threshold)",
                        color = CosmicTextSecondary,
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Assignee: ${lead.assignedCaller}",
                    color = CosmicTextSecondary,
                    fontSize = 10.sp,
                    fontStyle = FontStyle.Italic
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onWhatsApp,
                        border = BorderStroke(1.dp, CosmicPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("reminder_whatsapp_${lead.id}")
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "WhatsApp",
                            tint = CosmicPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", color = CosmicPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onCall,
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("reminder_call_${lead.id}")
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = "Simulate Call",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Simulate Call", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
// End of file utilities

@Composable
fun LeadActivityCalendar(
    lead: LeadEntity,
    callRecords: List<CallRecordEntity>,
    reminderRules: List<ReminderRuleEntity>,
    onScheduleFollowUp: (Long?) -> Unit,
    onAnnotateRecord: (CallRecordEntity) -> Unit
) {
    var calendarState by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDay by remember { mutableStateOf<Int?>(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) }

    val year = calendarState.get(Calendar.YEAR)
    val month = calendarState.get(Calendar.MONTH)

    val firstDayOfMonthCalendar = (calendarState.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val firstDayOfWeek = firstDayOfMonthCalendar.get(Calendar.DAY_OF_WEEK)
    val daysInMonth = calendarState.getActualMaximum(Calendar.DAY_OF_MONTH)

    val monthLabel = firstDayOfMonthCalendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) ?: ""
    
    val activeRules = reminderRules.filter { it.isEnabled }
    val projectedFollowUps = remember(lead, activeRules) {
        activeRules.filter { rule ->
            rule.targetStatus == "All" || lead.status.lowercase() == rule.targetStatus.lowercase()
        }.map { rule ->
            val timestamp = lead.lastContacted + rule.timeframeHours * 3600 * 1000L
            Triple(timestamp, rule.title, rule.timeframeHours)
        }
    }

    val weekdayNames = listOf("S", "M", "T", "W", "T", "F", "S")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    calendarState = (calendarState.clone() as Calendar).apply {
                        add(Calendar.MONTH, -1)
                    }
                    selectedDay = null
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Previous Month", tint = CosmicPrimary)
            }

            Text(
                text = "$monthLabel $year",
                color = CosmicTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = {
                    calendarState = (calendarState.clone() as Calendar).apply {
                        add(Calendar.MONTH, 1)
                    }
                    selectedDay = null
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next Month", tint = CosmicPrimary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            weekdayNames.forEach { dayName ->
                Text(
                    text = dayName,
                    color = CosmicTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val totalCells = (firstDayOfWeek - 1) + daysInMonth
        val rows = (totalCells + 6) / 7

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (c in 1..7) {
                        val cellIndex = r * 7 + c
                        val dayNumber = cellIndex - (firstDayOfWeek - 1)

                        if (dayNumber in 1..daysInMonth) {
                            val isSelected = selectedDay == dayNumber
                            val isToday = isSameDay(System.currentTimeMillis(), year, month, dayNumber)

                            val hasCalls = callRecords.any { isSameDay(it.timestamp, year, month, dayNumber) }
                            val hasCustomFollowUp = lead.scheduledFollowUp?.let { isSameDay(it, year, month, dayNumber) } ?: false
                            val hasProjectedFollowUp = projectedFollowUps.any { isSameDay(it.first, year, month, dayNumber) }

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.1f)
                                    .padding(2.dp)
                                    .clickable { selectedDay = dayNumber }
                                    .testTag("calendar_day_${dayNumber}"),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isSelected -> CosmicPrimary.copy(alpha = 0.25f)
                                        isToday -> CosmicSurfaceVariant.copy(alpha = 0.8f)
                                        else -> CosmicSurfaceVariant.copy(alpha = 0.3f)
                                    }
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = if (isToday) BorderStroke(1.dp, CosmicPrimary) else if (isSelected) BorderStroke(1.dp, CosmicAccent) else null
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "$dayNumber",
                                            color = when {
                                                isSelected -> CosmicAccent
                                                isToday -> CosmicPrimary
                                                else -> CosmicTextPrimary
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (hasCalls) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .background(Color(0xFF10B981), CircleShape)
                                                )
                                            }
                                            if (hasCustomFollowUp) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .background(Color(0xFFF59E0B), CircleShape)
                                                )
                                            }
                                            if (hasProjectedFollowUp) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .background(Color(0xFF8B5CF6), CircleShape)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedDay != null) {
            val d = selectedDay!!
            val selectedCalendar = (calendarState.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, d)
            }
            val formattedDate = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()).format(selectedCalendar.time)

            val dayCalls = callRecords.filter { isSameDay(it.timestamp, year, month, d) }
            val customFollowUpTime = lead.scheduledFollowUp?.takeIf { isSameDay(it, year, month, d) }
            val dayProjected = projectedFollowUps.filter { isSameDay(it.first, year, month, d) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = formattedDate,
                        color = CosmicTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (dayCalls.isNotEmpty()) {
                        Text(
                            text = "Past Call Sessions (${dayCalls.size})",
                            color = Color(0xFF10B981),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        dayCalls.forEach { record ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onAnnotateRecord(record) },
                                colors = CardDefaults.cardColors(containerColor = CosmicSurface.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneCallback,
                                        contentDescription = "Past Call",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Call by ${record.callerName} • ${record.durationSeconds}s",
                                            color = CosmicTextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (record.notes.isNotEmpty()) {
                                            Text(
                                                text = record.notes,
                                                color = CosmicTextSecondary,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowForwardIos,
                                        contentDescription = "Details",
                                        tint = CosmicTextSecondary,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (customFollowUpTime != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Alarm,
                                        contentDescription = "Follow-up",
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Custom Scheduled Follow-Up",
                                            color = Color(0xFF92400E),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Time: ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(customFollowUpTime))}",
                                            color = Color(0xFFB45309),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                Text(
                                    text = "Cancel",
                                    color = Color(0xFFB91C1C),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { onScheduleFollowUp(null) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (dayProjected.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        dayProjected.forEach { (_, ruleTitle, timeframeHours) ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E8FF)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoMode,
                                        contentDescription = "Auto Follow-up",
                                        tint = Color(0xFF7C3AED),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Auto-Scheduled Follow-Up",
                                            color = Color(0xFF5B21B6),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Rule: $ruleTitle ($timeframeHours hrs from last contact)",
                                            color = Color(0xFF6D28D9),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val isPastDay = selectedCalendar.get(Calendar.DAY_OF_YEAR) < Calendar.getInstance().get(Calendar.DAY_OF_YEAR) &&
                                    selectedCalendar.get(Calendar.YEAR) <= Calendar.getInstance().get(Calendar.YEAR)

                    if (customFollowUpTime == null && !isPastDay) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = CosmicBackground.copy(alpha = 0.5f), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Schedule Custom Follow-Up for this day:",
                            color = CosmicTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    val morningCal = (selectedCalendar.clone() as Calendar).apply {
                                        set(Calendar.HOUR_OF_DAY, 10)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                    }
                                    onScheduleFollowUp(morningCal.timeInMillis)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Morning (10 AM)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val afternoonCal = (selectedCalendar.clone() as Calendar).apply {
                                        set(Calendar.HOUR_OF_DAY, 14)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                    }
                                    onScheduleFollowUp(afternoonCal.timeInMillis)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CosmicAccent),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Afternoon (2 PM)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val eveningCal = (selectedCalendar.clone() as Calendar).apply {
                                        set(Calendar.HOUR_OF_DAY, 17)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                    }
                                    onScheduleFollowUp(eveningCal.timeInMillis)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Evening (5 PM)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (dayCalls.isEmpty() && customFollowUpTime == null && dayProjected.isEmpty() && isPastDay) {
                        Text(
                            text = "No recorded call activity or follow-ups on this date.",
                            color = CosmicTextSecondary,
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

fun isSameDay(time1: Long, year: Int, month: Int, day: Int): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
    return cal1.get(Calendar.YEAR) == year &&
           cal1.get(Calendar.MONTH) == month &&
           cal1.get(Calendar.DAY_OF_MONTH) == day
}

// End of file utilities

@Composable
fun TelecallerReportModal(
    viewModel: CRMViewModel,
    caller: com.example.database.TelecallerEntity,
    onDismiss: () -> Unit
) {
    val report = remember(caller) { viewModel.getTelecallerPerformanceReport(caller) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("telecaller_report_modal"),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(CosmicPrimary.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = report.name.take(1).uppercase(),
                                color = CosmicPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = report.name,
                                color = CosmicTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            color = when (report.status) {
                                                "Available" -> Color(0xFF34D399)
                                                "Busy" -> Color(0xFFFBBF24)
                                                else -> Color(0xFFEF4444)
                                            },
                                            shape = CircleShape
                                        )
                                )
                                Text(
                                    text = report.status,
                                    color = CosmicTextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp).testTag("close_report_modal_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = CosmicTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "Individual Performance Report",
                    color = CosmicTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Text(
                    text = "Performance analytics compiled across all historic outbound calls & lead statuses.",
                    color = CosmicTextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2x2 Grid for metrics
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 1. Time spent in 'on-call' status
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "Talk Time",
                                        tint = CosmicPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text("Talk Time", color = CosmicTextSecondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                val hours = report.timeSpentOnCallSeconds / 3600
                                val minutes = (report.timeSpentOnCallSeconds % 3600) / 60
                                val seconds = report.timeSpentOnCallSeconds % 60
                                val formattedTalkTime = when {
                                    hours > 0 -> "${hours}h ${minutes}m"
                                    minutes > 0 -> "${minutes}m ${seconds}s"
                                    else -> "${seconds}s"
                                }
                                Text(
                                    text = formattedTalkTime,
                                    color = CosmicTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "On-Call Status",
                                    color = CosmicTextSecondary,
                                    fontSize = 8.5.sp
                                )
                            }
                        }

                        // 2. Average lead response time
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "Response Speed",
                                        tint = CosmicAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text("Response Speed", color = CosmicTextSecondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                val avgResponseStr = if (report.averageLeadResponseTimeMinutes > 0) {
                                    String.format(java.util.Locale.US, "%.1f m", report.averageLeadResponseTimeMinutes)
                                } else {
                                    "N/A"
                                }
                                Text(
                                    text = avgResponseStr,
                                    color = CosmicTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Avg lead contact gap",
                                    color = CosmicTextSecondary,
                                    fontSize = 8.5.sp
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 3. Total successful conversions
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingUp,
                                        contentDescription = "Conversions",
                                        tint = CosmicSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text("Conversions", color = CosmicTextSecondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${report.totalSuccessfulConversions}",
                                    color = CosmicTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val conversionRateStr = String.format(java.util.Locale.US, "%.1f%% conversion rate", report.conversionRatePercent)
                                Text(
                                    text = conversionRateStr,
                                    color = CosmicTextSecondary,
                                    fontSize = 8.5.sp
                                )
                            }
                        }

                        // 4. Calls Made & Activity
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Calls Completed",
                                        tint = CosmicPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text("Calls", color = CosmicTextSecondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${report.totalCallsMade} calls",
                                    color = CosmicTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val avgCallDurStr = String.format(java.util.Locale.US, "Avg duration: %.0fs", report.averageCallDurationSeconds)
                                Text(
                                    text = avgCallDurStr,
                                    color = CosmicTextSecondary,
                                    fontSize = 8.5.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Metadata details list
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Leads Assigned", color = CosmicTextSecondary, fontSize = 9.5.sp)
                            Text("${report.leadsAssigned} leads", color = CosmicTextPrimary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Account Authorization", color = CosmicTextSecondary, fontSize = 9.5.sp)
                            Text(
                                text = if (report.isAuthorized) "Active (Authorized)" else "Suspended (Locked)",
                                color = if (report.isAuthorized) Color(0xFF34D399) else Color(0xFFEF4444),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val context = LocalContext.current
                    OutlinedButton(
                        onClick = {
                            val clipText = """
                                ========================================
                                TELECALLER PERFORMANCE REPORT: ${report.name}
                                ========================================
                                Name: ${report.name}
                                Status: ${report.status}
                                Authorization: ${if (report.isAuthorized) "Authorized" else "Suspended"}
                                
                                METRICS:
                                - Total Leads Assigned: ${report.leadsAssigned}
                                - Total Calls Made: ${report.totalCallsMade}
                                - Total Talk Time: ${report.timeSpentOnCallSeconds}s
                                - Avg Call Duration: ${String.format(java.util.Locale.US, "%.1f", report.averageCallDurationSeconds)}s
                                - Avg Response Time: ${String.format(java.util.Locale.US, "%.1f", report.averageLeadResponseTimeMinutes)} mins
                                - Total Conversions: ${report.totalSuccessfulConversions}
                                - Conversion Rate: ${String.format(java.util.Locale.US, "%.1f", report.conversionRatePercent)}%
                                ========================================
                            """.trimIndent()
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Telecaller Report", clipText)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, "Performance report copied!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CosmicTextPrimary),
                        border = BorderStroke(1.dp, CosmicSurfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("copy_individual_report_btn")
                    ) {
                        Text("Copy Report", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("dismiss_report_modal_btn")
                    ) {
                        Text("Dismiss", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun LeadScriptGeneratorTab(
    viewModel: CRMViewModel,
    lead: LeadEntity,
    callRecords: List<CallRecordEntity>,
    whatsappMessages: List<com.example.database.WhatsAppMessageEntity>
) {
    var selectedTone by remember { mutableStateOf("Consultative") } // "Consultative", "Value-Driven", "Problem-Solver"
    var isRegenerating by remember { mutableStateOf(false) }
    
    // Simulate smart dynamic generation
    LaunchedEffect(lead.status, lead.sentiment, selectedTone) {
        isRegenerating = true
        kotlinx.coroutines.delay(400) // Beautiful short analytical spin to feel dynamic & reactive
        isRegenerating = false
    }

    // Context analysis derived from lead and historic details
    val totalCalls = callRecords.size
    val lastCallNote = callRecords.firstOrNull { it.notes.isNotEmpty() }?.notes
    val lastCallTranscript = callRecords.firstOrNull { it.transcript.isNotEmpty() }?.transcript
    val lastWhatsAppMsg = whatsappMessages.lastOrNull { it.sender == "Lead" }?.messageText
    
    val contextMessage = remember(lead, totalCalls, lastCallNote, lastWhatsAppMsg) {
        buildString {
            append("Derived context from ${lead.status} status and ${lead.source} source. ")
            if (totalCalls > 0) {
                append("Found $totalCalls historic calls. ")
            }
            if (!lastCallNote.isNullOrBlank()) {
                append("Past call notes indicate: \"${lastCallNote.take(45)}...\". ")
            }
            if (!lastWhatsAppMsg.isNullOrBlank()) {
                append("Last incoming WhatsApp was: \"${lastWhatsAppMsg.take(45)}...\".")
            }
        }
    }

    // Dynamic Talking Points based on Status + Tone
    val greeting = when (selectedTone) {
        "Value-Driven" -> "Hey ${lead.name}, this is quick outreach from CRM Hub. I hope you're having an active week!"
        "Problem-Solver" -> "Hello ${lead.name}, this is CRM Hub technical support. I am reaching out to follow up on your integration inquiry."
        else -> "Hello ${lead.name}, this is CRM Hub. I hope I'm not interrupting your schedule. Is now a convenient time for a brief call?"
    }

    val openingPoint = remember(lead, selectedTone) {
        when (lead.status) {
            "New" -> "I noticed you registered via ${lead.source} and wanted to personally welcome you. Our system indicated your team is looking to scale outbound capacity."
            "Contacted" -> "Just following up on our previous conversation. I wanted to verify if you had a chance to look at our modern dialer features."
            "Interested" -> "Great to speak with you again! Since you expressed interest, I've prepared a custom walkthrough of our dynamic routing dashboard for ${lead.company}."
            "Converted" -> "Checking in on your onboarding setup. I want to make sure your agents are fully configured and enjoying our CRM features."
            else -> "Reaching back out to see if your calendar has opened up. We've introduced several new automated features that directly address team workflow friction."
        }
    }

    val valuePitch = remember(lead, selectedTone) {
        when (selectedTone) {
            "Value-Driven" -> {
                when (lead.status) {
                    "New", "Contacted" -> "With our automated Campaign Queue, telecallers experience a 250% increase in live talk-time by eliminating manual dial gaps. For ${lead.company}, this directly translates to higher conversion velocity."
                    "Interested" -> "By locking in this month's custom package, ${lead.company} gets priority integration support and a guaranteed 35% discount on outbound call minutes, optimizing your immediate ROI."
                    else -> "Even if the timeline isn't immediate, our dynamic sync and auto-dialer can save your sales reps up to 2 hours of manual lead logging every single day."
                }
            }
            "Problem-Solver" -> {
                when (lead.status) {
                    "New", "Contacted" -> "Our core value is architectural. We replace messy manual pipelines with a unified Room database that synchronizes with WhatsApp and Facebook Leads in under 5 seconds. Let's solve the data leakage issue at ${lead.company}."
                    "Interested" -> "We support native webhook configuration, advanced call annotation playback, and custom rule-based follow-up reminders. It perfectly fits ${lead.company}'s custom workflow requirements."
                    else -> "We've resolved past latency concerns with a brand-new local cache, ensuring your call recording annotations are fully synced offline-first."
                }
            }
            else -> { // Consultative
                when (lead.status) {
                    "New", "Contacted" -> "We focus on helping your team have warmer, more meaningful conversations. We provide real-time caller dashboards and sentiment tracking so agents can build instant trust."
                    "Interested" -> "Our primary goal is supporting ${lead.company}'s growth. We can customize the CRM layout to match your current sales playbooks so your team feels right at home."
                    else -> "We want to be a helpful resource. We offer unlimited onboarding support for your representatives to guarantee a smooth transition."
                }
            }
        }
    }

    val objectionHandling = remember(lead, selectedTone, lastCallNote, lastWhatsAppMsg) {
        buildString {
            if (lead.sentiment == "Negative") {
                append("⚠️ EMPATHY FIRST: Address hesitation. \"I understand you have some initial hesitations. Many clients at first felt the same way, but found that our offline sync fully protects their database. Let's take it at your pace.\"")
            } else {
                when (selectedTone) {
                    "Value-Driven" -> append("⚡ SPEED & SCALE: \"If budget is a consideration, let's look at the cost of inaction. Every day your agents manually log leads is a day you lose active pipeline volume.\"")
                    "Problem-Solver" -> {
                        if (!lastCallNote.isNullOrBlank()) {
                            append("🛠️ SOLUTION MATCH: \"On our last call, we touched on [${lastCallNote.take(30)}...]. Our system resolves this natively with custom scheduled follow-up reminder rules so nothing falls through the cracks.\"")
                        } else {
                            append("🛠️ TECHNICAL CONFIDENCE: \"If compatibility is a worry, we offer direct API templates that bridge existing systems smoothly, requiring zero extra coding from your side.\"")
                        }
                    }
                    else -> append("🤝 RELATIONSHIP BUILD: \"I appreciate you sharing those details. It's completely normal to want to make sure the fit is perfect. Let's look at a customized pilot program for ${lead.company} to verify the value firsthand.\"")
                }
            }
        }
    }

    val nextStepCTA = remember(lead, selectedTone) {
        when (lead.status) {
            "New", "Contacted" -> "Would you be open to a quick, 10-minute visual demo this week? I can set up a personalized sandbox showing exactly how ${lead.company}'s leads would flow."
            "Interested" -> "Shall I draft our standard service agreement for your review? We can configure your custom subdomain within 24 hours of confirmation."
            "Converted" -> "Let's schedule a 15-minute training session for your main team leads next Tuesday to ensure optimal adoption."
            else -> "No pressure at all. I can send over our latest outbound optimization guide and touch base in a couple of weeks to see if the timing is better."
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("script_generator_container"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Dynamic Tone & Mode Selection
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Script Generation Tone",
                        color = CosmicTextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val tones = listOf("Consultative", "Value-Driven", "Problem-Solver")
                        tones.forEach { tone ->
                            val isSelected = selectedTone == tone
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (isSelected) CosmicPrimary else CosmicSurface,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { selectedTone = tone }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tone,
                                    color = if (isSelected) Color.White else CosmicTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("tone_btn_$tone")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Context & AI Recommendation Badge
        item {
            val sentimentColor = when (lead.sentiment) {
                "Positive" -> Color(0xFF10B981)
                "Negative" -> Color(0xFFEF4444)
                else -> CosmicTextSecondary
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, sentimentColor.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(sentimentColor.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (lead.sentiment == "Negative") Icons.Default.Warning else Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = sentimentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (lead.sentiment == "Negative") "Coaching: Empathy Mode Active" else "Coaching Recommendation",
                            color = CosmicTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (lead.sentiment == "Negative") {
                                "Lead is hesitant. Avoid aggressive pitching. Focus on answering compatibility questions and offering technical validation."
                            } else if (lead.sentiment == "Positive") {
                                "Lead is highly receptive! Speak enthusiastically, highlight pricing ROI, and push for a demo or service agreement."
                            } else {
                                "Lead is in discovery phase. Keep questions open-ended. Learn about their current telecalling bottleneck."
                            },
                            color = CosmicTextSecondary,
                            fontSize = 9.5.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }

        // Historic interaction summary bullet
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CosmicPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            "Interaction Analysis History",
                            color = CosmicTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = contextMessage,
                        color = CosmicTextSecondary,
                        fontSize = 9.5.sp,
                        lineHeight = 13.sp
                    )
                }
            }
        }

        // Main Script Generator Output
        if (isRegenerating) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CosmicPrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Regenerating personalized pitch...",
                            color = CosmicTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        } else {
            // Segment 1: Opening & Welcome
            item {
                ScriptSegmentCard(
                    title = "1. Opening Icebreaker",
                    text = "$greeting $openingPoint",
                    testTagPrefix = "opening"
                )
            }

            // Segment 2: Core Value Pitch
            item {
                ScriptSegmentCard(
                    title = "2. Core Value Pitch",
                    text = valuePitch,
                    testTagPrefix = "value_pitch"
                )
            }

            // Segment 3: Objection Handler
            item {
                ScriptSegmentCard(
                    title = "3. Past Context & Objection Handling",
                    text = objectionHandling,
                    testTagPrefix = "objection"
                )
            }

            // Segment 4: Call to Action (Next Action)
            item {
                ScriptSegmentCard(
                    title = "4. Next Action / CTA",
                    text = nextStepCTA,
                    testTagPrefix = "cta"
                )
            }

            // Copy full consolidated script button
            item {
                val context = LocalContext.current
                val fullScript = """
                    === CRM OUTBOUND CALL SCRIPT ===
                    Lead: ${lead.name} | Company: ${lead.company}
                    Tone: $selectedTone
                    
                    GREETING & ICEBREAKER:
                    $greeting $openingPoint
                    
                    VALUE PITCH:
                    $valuePitch
                    
                    PAST CONTEXT & OBJECTIONS:
                    $objectionHandling
                    
                    CALL TO ACTION:
                    $nextStepCTA
                    ================================
                """.trimIndent()

                Button(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("CRM Call Script", fullScript)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Full outbound script copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("copy_full_script_btn")
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy Full Outbound Script", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ScriptSegmentCard(
    title: String,
    text: String,
    testTagPrefix: String
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CosmicSurfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = CosmicPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText(title, text)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "$title copied!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("copy_${testTagPrefix}_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy segment",
                        tint = CosmicTextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = text,
                color = CosmicTextPrimary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

// --- CENTRAL PERFORMANCE DASHBOARD TAB ---
@Composable
fun DashboardTab(
    viewModel: CRMViewModel,
    onNavigateToTab: (String) -> Unit
) {
    val leads by viewModel.leads.collectAsStateWithLifecycle()
    val callRecords by viewModel.callRecords.collectAsStateWithLifecycle()
    val telecallers by viewModel.telecallers.collectAsStateWithLifecycle()
    val currentRole by viewModel.userRole.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedDashboardType by remember(currentRole) { 
        mutableStateOf(if (currentRole == "Admin") "Admin Dashboard" else "Agent Dashboard") 
    }
    var showImportWizard by remember { mutableStateOf(false) }

    // Computations
    val totalLeads = leads.size
    val totalConverted = leads.count { it.status == "Converted" }
    val conversionRate = if (totalLeads > 0) (totalConverted.toDouble() / totalLeads * 100) else 42.5
    
    // Calls made today (simulated + actual records)
    val callsMadeToday = callRecords.count { 
        val delta = System.currentTimeMillis() - it.timestamp
        delta < 24 * 3600 * 1000 
    } + 5 // pre-seeded mock activity

    val totalPipelineValue = leads.count { it.status != "Lost" } * 250000 // Mock pipeline multiplier in Indian Rupees (₹)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Dynamic Selector Tab at the top
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CosmicSurface, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Admin Dashboard", "Agent Dashboard").forEach { dbType ->
                    val isSelected = selectedDashboardType == dbType
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) CosmicPrimary else Color.Transparent)
                            .clickable { selectedDashboardType = dbType }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dbType,
                            color = if (isSelected) Color.White else CosmicTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Export Metrics Quick Access Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CosmicSecondary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = "Metrics summary",
                            tint = CosmicSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Analytics Export Console",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CosmicTextPrimary
                        )
                    }
                    Button(
                        onClick = {
                            exportDashboardMetricsToCsv(context, leads, callRecords, telecallers, viewModel)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("dashboard_export_metrics_btn")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Export CSV icon",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text("Export Metrics CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Direct CSV File Lead Importer Card
        item {
            DirectCsvLeadImporterCard(viewModel = viewModel)
        }

        if (selectedDashboardType == "Admin Dashboard") {
            // === ADMIN DASHBOARD ===
            // Hero Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Admin Executive Console",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CosmicTextPrimary
                                )
                                Text(
                                    "Organization-wide pipeline & telecaller performance report",
                                    fontSize = 11.sp,
                                    color = CosmicTextSecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(CosmicPrimary.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = CosmicPrimary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            // CSV Import Wizard CTA Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CosmicAccent.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CosmicAccent.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(CosmicAccent.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Import", tint = CosmicAccent)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "CSV / Sheets Data Wizard",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = CosmicTextPrimary
                            )
                            Text(
                                "Batch upload contacts and map spreadsheets to internal CRM schema.",
                                fontSize = 10.sp,
                                color = CosmicTextSecondary
                            )
                        }
                        Button(
                            onClick = { showImportWizard = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CosmicAccent),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp).testTag("dashboard_import_wizard_btn")
                        ) {
                            Text("Launch", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Key Performance Metrics Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Total Leads Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Total Leads", fontSize = 10.sp, color = CosmicTextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$totalLeads", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CosmicPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TrendingUp, contentDescription = null, tint = CosmicSecondary, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("+18% wk over wk", fontSize = 9.sp, color = CosmicSecondary)
                            }
                        }
                    }

                    // Calls Made Today Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Calls Today", fontSize = 10.sp, color = CosmicTextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$callsMadeToday", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CosmicSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Avg 3.4m per call", fontSize = 9.sp, color = CosmicTextSecondary)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Conversion Rates Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Conversion Rate", fontSize = 10.sp, color = CosmicTextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(String.format("%.1f%%", conversionRate), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Goal: 35.0% Target", fontSize = 9.sp, color = CosmicTextSecondary)
                        }
                    }

                    // Total Pipeline Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Active Pipeline", fontSize = 10.sp, color = CosmicTextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(String.format("₹%,d", totalPipelineValue), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("High value prospects", fontSize = 9.sp, color = CosmicTextSecondary)
                        }
                    }
                }
            }

            // Telecaller Team Pulse Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CosmicTextSecondary.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Group, contentDescription = null, tint = CosmicPrimary, modifier = Modifier.size(16.dp))
                                Text("Telecaller Team Pulse", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                            }
                            Text("${telecallers.size} Active", fontSize = 9.5.sp, color = CosmicSecondary, fontWeight = FontWeight.Bold)
                        }

                        telecallers.forEach { caller ->
                            val report = viewModel.getTelecallerPerformanceReport(caller)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CosmicSurfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                color = when (caller.status) {
                                                    "Available" -> Color(0xFF34D399)
                                                    "Busy" -> Color(0xFFFBBF24)
                                                    else -> Color(0xFF94A3B8)
                                                },
                                                shape = CircleShape
                                            )
                                    )
                                    Column {
                                        Text(caller.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                                        Text("Level: ${caller.accessLevel}", fontSize = 8.5.sp, color = CosmicTextSecondary)
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Assigned", fontSize = 8.5.sp, color = CosmicTextSecondary)
                                        Text("${report.leadsAssigned}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Calls Made", fontSize = 8.5.sp, color = CosmicTextSecondary)
                                        Text("${report.totalCallsMade}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Conv. %", fontSize = 8.5.sp, color = CosmicTextSecondary)
                                        Text(String.format("%.1f%%", report.conversionRatePercent), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CosmicSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Charts Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Funnel Chart Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Conversion Funnel",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CosmicTextPrimary,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            FunnelDonutChart(leads)
                            
                            // Legend
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SegmentLegendItem("Fresh", CosmicAccent)
                                SegmentLegendItem("Contacted", CosmicPrimary)
                                SegmentLegendItem("Interested", Color(0xFFF59E0B))
                                SegmentLegendItem("Converted", CosmicSecondary)
                            }
                        }
                    }

                    // Lead Acquisition Channels Bar Card
                    Card(
                        modifier = Modifier.weight(1.2f),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Acquisition Channels",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CosmicTextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ChannelsBarChart(leads)
                        }
                    }
                }
            }

            // Outbound Call Calling analytics curve
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Outbound Call Engagement Analytics",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CosmicTextPrimary
                            )
                            Text(
                                "Last 7 Days Trend",
                                fontSize = 9.sp,
                                color = CosmicSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutboundCallingLineChart()
                    }
                }
            }

            item {
                SentimentDistributionDashboardCard(leads)
            }

        } else {
            // === AGENT DASHBOARD ===
            // Greet Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(CosmicPrimary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🙋‍♀️", fontSize = 24.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Welcome Back, Sarah! ✨",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = CosmicTextPrimary
                            )
                            Text(
                                "Let's turn dials into conversations today.",
                                fontSize = 11.sp,
                                color = CosmicTextSecondary
                            )
                        }
                    }
                }
            }

            // Agent metrics computed specifically for Sarah
            val sarahLeads = leads.filter { it.assignedCaller.contains("Sarah", ignoreCase = true) }
            val sarahCalls = callRecords.filter { it.callerName.contains("Sarah", ignoreCase = true) }
            val sarahConverted = sarahLeads.count { it.status == "Converted" }
            val sarahConversionRate = if (sarahLeads.isNotEmpty()) (sarahConverted.toDouble() / sarahLeads.size * 100) else 50.0
            val sarahPipelineValue = sarahLeads.count { it.status != "Lost" } * 250000

            // Daily Summary Stats Widget for Sarah
            item {
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val sarahTodayCalls = sarahCalls.filter { it.timestamp >= todayStart }
                val totalCallsToday = sarahTodayCalls.size
                val totalTalkTimeTodaySeconds = sarahTodayCalls.sumOf { it.durationSeconds }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("today_stats_widget"),
                    colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CosmicSecondary.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Assessment,
                                    contentDescription = null,
                                    tint = CosmicSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Today's Telephony Performance",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CosmicTextPrimary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CosmicSecondary.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "LIVE",
                                    color = CosmicSecondary,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        HorizontalDivider(color = CosmicSurfaceVariant.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Total Calls Made Card/Section
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(CosmicSurfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                                    .testTag("today_total_calls_container"),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneCallback,
                                        contentDescription = null,
                                        tint = CosmicPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Calls Made",
                                        fontSize = 10.sp,
                                        color = CosmicTextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = "$totalCallsToday",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CosmicTextPrimary,
                                    modifier = Modifier.testTag("today_total_calls_val")
                                )
                                Text(
                                    text = "Real-time logged",
                                    fontSize = 8.5.sp,
                                    color = CosmicTextSecondary
                                )
                            }

                            // Total Talk Time Card/Section
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(CosmicSurfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                                    .testTag("today_talk_time_container"),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = CosmicAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Total Talk Time",
                                        fontSize = 10.sp,
                                        color = CosmicTextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                val mins = totalTalkTimeTodaySeconds / 60
                                val secs = totalTalkTimeTodaySeconds % 60
                                val durationStr = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                                Text(
                                    text = durationStr,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CosmicTextPrimary,
                                    modifier = Modifier.testTag("today_talk_time_val")
                                )
                                Text(
                                    text = "Direct SIM & sandbox",
                                    fontSize = 8.5.sp,
                                    color = CosmicTextSecondary
                                )
                            }
                        }
                    }
                }
            }


            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Assigned Leads Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("My Assigned Leads", fontSize = 10.sp, color = CosmicTextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${sarahLeads.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CosmicPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Active Prospects", fontSize = 8.5.sp, color = CosmicTextSecondary)
                        }
                    }

                    // Calls Made Today Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("My Calls Today", fontSize = 10.sp, color = CosmicTextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            val sarahCallsCount = sarahCalls.size + 3 // mock base + real records
                            Text("$sarahCallsCount", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CosmicSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Daily target is 15", fontSize = 8.5.sp, color = CosmicTextSecondary)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Personal Conversion Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("My Conv. Rate", fontSize = 10.sp, color = CosmicTextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(String.format("%.1f%%", sarahConversionRate), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Keep closing!", fontSize = 8.5.sp, color = CosmicTextSecondary)
                        }
                    }

                    // Pipeline Value Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("My Pipeline Worth", fontSize = 10.sp, color = CosmicTextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(String.format("₹%,d", sarahPipelineValue), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Total pipeline volume", fontSize = 8.5.sp, color = CosmicTextSecondary)
                        }
                    }
                }
            }

            // Daily Target Tracker Card
            item {
                val callsDone = sarahCalls.size + 3
                val progress = (callsDone.toFloat() / 15f).coerceIn(0f, 1f)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Daily Outbound Call Tracker", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                            Text("$callsDone / 15 Completed", fontSize = 10.sp, color = CosmicSecondary, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            color = CosmicSecondary,
                            trackColor = CosmicSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = if (progress >= 1f) "🎯 Target achieved! Excellent effort Sarah!" else "Keep calling! Just ${15 - callsDone} more to hit your daily goal.",
                            fontSize = 9.sp,
                            color = CosmicTextSecondary
                        )
                    }
                }
            }

            // Active Priority Call Queue panel
            item {
                val priorityQueue = sarahLeads.filter { it.status == "New" || it.status == "Contacted" }.take(3)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CosmicAccent.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(CosmicAccent, CircleShape)
                            )
                            Text("Actionable Priority Dial Queue", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                        }
                        
                        if (priorityQueue.isEmpty()) {
                            Text("No pending high-priority leads in your queue. Great job!", fontSize = 10.5.sp, color = CosmicTextSecondary)
                        } else {
                            priorityQueue.forEach { lead ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CosmicSurfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(lead.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                                        Text(lead.company, fontSize = 9.5.sp, color = CosmicTextSecondary)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // WhatsApp Outreach Button
                                        IconButton(
                                            onClick = {
                                                launchWhatsAppChat(context, lead.phone, null, viewModel, lead.id)
                                            },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(CosmicAccent.copy(alpha = 0.15f), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Send, contentDescription = "WhatsApp", tint = CosmicAccent, modifier = Modifier.size(14.dp))
                                        }

                                        // Dialer Button
                                        IconButton(
                                            onClick = {
                                                viewModel.showCallPromptForLead(lead)
                                            },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(CosmicSecondary.copy(alpha = 0.15f), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Call, contentDescription = "Call", tint = CosmicSecondary, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showImportWizard) {
        CSVImportWizardDialog(
            viewModel = viewModel,
            onDismiss = { showImportWizard = false }
        )
    }
}

@Composable
fun DirectCsvLeadImporterCard(viewModel: CRMViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // States
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileSize by remember { mutableStateOf<String?>(null) }
    var rawCsvContent by remember { mutableStateOf("") }
    
    var parsedHeaders by remember { mutableStateOf<List<String>>(emptyList()) }
    var parsedRows by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    
    var mappedName by remember { mutableStateOf("") }
    var mappedPhone by remember { mutableStateOf("") }
    var mappedEmail by remember { mutableStateOf("") }
    var mappedCompany by remember { mutableStateOf("") }
    var mappedSource by remember { mutableStateOf("") }
    
    var isImportSuccess by remember { mutableStateOf(false) }
    var importedCount by remember { mutableStateOf(0) }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { selectedUri ->
            try {
                var name = "leads_batch.csv"
                var sizeStr = "Unknown size"
                context.contentResolver.query(selectedUri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) name = cursor.getString(nameIndex)
                        if (sizeIndex != -1) {
                            val sizeBytes = cursor.getLong(sizeIndex)
                            sizeStr = if (sizeBytes > 1024) {
                                String.format("%.1f KB", sizeBytes / 1024.0)
                            } else {
                                "$sizeBytes B"
                            }
                        }
                    }
                }
                
                context.contentResolver.openInputStream(selectedUri)?.use { inputStream ->
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                    val content = reader.readText()
                    if (content.isNotBlank()) {
                        rawCsvContent = content
                        selectedFileName = name
                        selectedFileSize = sizeStr
                        isImportSuccess = false
                        
                        // Parse CSV rows robustly
                        val lines = content.replace("\r\n", "\n").split("\n").filter { it.isNotBlank() }
                        if (lines.isNotEmpty()) {
                            val delimiter = if (lines[0].contains(";")) ";" else if (lines[0].contains("\t")) "\t" else ","
                            val headers = splitCsvLine(lines[0], delimiter)
                            parsedHeaders = headers
                            
                            val rows = lines.drop(1).map { splitCsvLine(it, delimiter) }
                            parsedRows = rows
                            
                            // Intelligent Auto-detect
                            mappedName = headers.find { it.contains("Name", ignoreCase = true) || it.contains("Client", ignoreCase = true) || it.contains("Customer", ignoreCase = true) } ?: headers.firstOrNull() ?: ""
                            mappedPhone = headers.find { it.contains("Phone", ignoreCase = true) || it.contains("Contact", ignoreCase = true) || it.contains("Number", ignoreCase = true) || it.contains("Mobile", ignoreCase = true) } ?: ""
                            mappedEmail = headers.find { it.contains("Email", ignoreCase = true) || it.contains("Mail", ignoreCase = true) } ?: ""
                            mappedCompany = headers.find { it.contains("Company", ignoreCase = true) || it.contains("Organization", ignoreCase = true) || it.contains("Firm", ignoreCase = true) || it.contains("Employer", ignoreCase = true) } ?: ""
                            mappedSource = headers.find { it.contains("Source", ignoreCase = true) || it.contains("Channel", ignoreCase = true) || it.contains("Campaign", ignoreCase = true) } ?: ""
                        }
                    } else {
                        viewModel.showToast("Selected CSV file is empty", ToastType.ERROR)
                    }
                }
            } catch (e: Exception) {
                viewModel.showToast("Failed to read file: ${e.localizedMessage}", ToastType.ERROR)
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("direct_csv_importer_card"),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CosmicAccent.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(CosmicAccent.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Upload Icon",
                            tint = CosmicAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Direct CSV Lead Importer",
                            color = CosmicTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Parse & insert spreadsheet files directly to state",
                            color = CosmicTextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
                
                // Clear button if file selected
                if (selectedFileName != null) {
                    IconButton(
                        onClick = {
                            selectedFileName = null
                            selectedFileSize = null
                            rawCsvContent = ""
                            parsedHeaders = emptyList()
                            parsedRows = emptyList()
                            mappedName = ""
                            mappedPhone = ""
                            mappedEmail = ""
                            mappedCompany = ""
                            mappedSource = ""
                            isImportSuccess = false
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear uploaded file",
                            tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedFileName == null) {
                // Drag & Drop / Selection Zone
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CosmicSurfaceVariant.copy(alpha = 0.15f))
                        .border(
                            border = BorderStroke(1.5.dp, CosmicAccent.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            try {
                                filePickerLauncher.launch("*/*")
                            } catch (e: Exception) {
                                viewModel.showToast("Cannot open file picker: ${e.localizedMessage}", ToastType.ERROR)
                            }
                        }
                        .testTag("csv_dropzone_area"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Upload",
                            tint = CosmicAccent,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Click to browse CSV / TSV / Excel text files",
                            color = CosmicTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Max size: 5MB • Formats: UTF-8 standard headers",
                            color = CosmicTextSecondary,
                            fontSize = 9.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sample Template Loader Button
                Button(
                    onClick = {
                        val sampleContent = "Client Name,Contact Number,Client Email,Lead Source,Organization,Deal Value\n" +
                                "John Miller,+1 (555) 0122,john@miller.org,Facebook Ads,Miller Labs,4500\n" +
                                "Sophia Ramirez,+1 (555) 0134,sophia.r@outlook.com,WhatsApp,Ramirez Co,1200\n" +
                                "Liam Chen,+1 (555) 0147,liam.chen@techsolutions.io,CRM Hub,Tech Solutions,8900\n" +
                                "Elena Rostova,+1 (555) 0192,elena.rostova@kaspersky.ru,Facebook Ads,Rostov Global,5200\n" +
                                "Marcus Aurelius,+1 (555) 0161,marcus@romanemp.it,Manual,Stoic Solutions,3000"
                        
                        rawCsvContent = sampleContent
                        selectedFileName = "mock_leads_template.csv"
                        selectedFileSize = "1.2 KB"
                        isImportSuccess = false
                        
                        val lines = sampleContent.split("\n").filter { it.isNotBlank() }
                        val headers = splitCsvLine(lines[0], ",")
                        parsedHeaders = headers
                        parsedRows = lines.drop(1).map { splitCsvLine(it, ",") }
                        
                        mappedName = "Client Name"
                        mappedPhone = "Contact Number"
                        mappedEmail = "Client Email"
                        mappedCompany = "Organization"
                        mappedSource = "Lead Source"
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CosmicPrimary.copy(alpha = 0.12f),
                        contentColor = CosmicPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .testTag("importer_load_sample_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Sample",
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Load Pre-formatted Sample Template",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // File Selected State
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // File Info pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CosmicAccent.copy(alpha = 0.08f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.InsertDriveFile,
                            contentDescription = "File icon",
                            tint = CosmicAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedFileName ?: "",
                                color = CosmicTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Size: ${selectedFileSize ?: "N/A"} • ${parsedRows.size} records found",
                                color = CosmicTextSecondary,
                                fontSize = 9.sp
                            )
                        }
                    }

                    // Column Mapping Selector Block
                    Text(
                        text = "Map Spreadsheet Columns to CRM Fields",
                        color = CosmicTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val requiredFields = listOf("Name" to true, "Phone" to true, "Email" to false, "Company" to false, "Source" to false)
                        requiredFields.forEach { (field, isRequired) ->
                            val currentMapping = when (field) {
                                "Name" -> mappedName
                                "Phone" -> mappedPhone
                                "Email" -> mappedEmail
                                "Company" -> mappedCompany
                                "Source" -> mappedSource
                                else -> ""
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CosmicSurfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (currentMapping.isNotEmpty()) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Status",
                                        tint = if (currentMapping.isNotEmpty()) CosmicSecondary else CosmicTextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = field,
                                        color = CosmicTextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isRequired) {
                                        Text(
                                            text = "*",
                                            color = Color(0xFFEF4444),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                var showDropdown by remember { mutableStateOf(false) }
                                Box {
                                    Button(
                                        onClick = { showDropdown = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CosmicSurface,
                                            contentColor = CosmicTextPrimary
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .height(26.dp)
                                            .testTag("inline_map_${field.lowercase()}")
                                    ) {
                                        Text(
                                            text = currentMapping.ifEmpty { "Unmapped" },
                                            fontSize = 9.5.sp,
                                            color = if (currentMapping.isNotEmpty()) CosmicTextPrimary else CosmicTextSecondary
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showDropdown,
                                        onDismissRequest = { showDropdown = false },
                                        modifier = Modifier.background(CosmicSurface)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("None", color = CosmicTextSecondary, fontSize = 11.sp) },
                                            onClick = {
                                                when (field) {
                                                    "Name" -> mappedName = ""
                                                    "Phone" -> mappedPhone = ""
                                                    "Email" -> mappedEmail = ""
                                                    "Company" -> mappedCompany = ""
                                                    "Source" -> mappedSource = ""
                                                }
                                                showDropdown = false
                                            }
                                        )
                                        parsedHeaders.forEach { header ->
                                            DropdownMenuItem(
                                                text = { Text(header, color = CosmicTextPrimary, fontSize = 11.sp) },
                                                onClick = {
                                                    when (field) {
                                                        "Name" -> mappedName = header
                                                        "Phone" -> mappedPhone = header
                                                        "Email" -> mappedEmail = header
                                                        "Company" -> mappedCompany = header
                                                        "Source" -> mappedSource = header
                                                    }
                                                    showDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Quality Control Live Verification Table
                    Text(
                        text = "Data Quality Verification (First 3 Rows Preview)",
                        color = CosmicTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val nameIdx = parsedHeaders.indexOf(mappedName)
                    val phoneIdx = parsedHeaders.indexOf(mappedPhone)
                    val sourceIdx = parsedHeaders.indexOf(mappedSource)
                    val emailIdx = parsedHeaders.indexOf(mappedEmail)
                    val companyIdx = parsedHeaders.indexOf(mappedCompany)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CosmicBackground, RoundedCornerShape(8.dp))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        parsedRows.take(3).forEachIndexed { index, row ->
                            val rName = if (nameIdx >= 0 && nameIdx < row.size) row[nameIdx] else "N/A"
                            val rPhone = if (phoneIdx >= 0 && phoneIdx < row.size) row[phoneIdx] else "N/A"
                            val rSource = if (sourceIdx >= 0 && sourceIdx < row.size) row[sourceIdx] else "N/A"
                            
                            val isRowValid = rName.isNotBlank() && rName != "N/A" && rPhone.isNotBlank() && rPhone != "N/A"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(0.5.dp, CosmicSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isRowValid) CosmicSecondary.copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isRowValid) "OK" else "Err",
                                        color = if (isRowValid) CosmicSecondary else Color(0xFFEF4444),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = rName,
                                    color = CosmicTextPrimary,
                                    fontSize = 10.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = rPhone,
                                    color = CosmicTextSecondary,
                                    fontSize = 10.sp,
                                    modifier = Modifier.weight(1.1f),
                                    maxLines = 1
                                )

                                Text(
                                    text = if (rSource != "N/A") rSource else "CSV Import",
                                    color = CosmicPrimary,
                                    fontSize = 9.sp,
                                    modifier = Modifier.weight(0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val canImport = mappedName.isNotEmpty() && mappedPhone.isNotEmpty() && parsedRows.isNotEmpty()

                    // Confirm Import CTA
                    Button(
                        onClick = {
                            if (!canImport) return@Button

                            val leadsToInsert = parsedRows.map { row ->
                                val rowName = if (nameIdx >= 0 && nameIdx < row.size) row[nameIdx] else "Unknown Import"
                                val rowPhone = if (phoneIdx >= 0 && phoneIdx < row.size) row[phoneIdx] else ""
                                val rowSource = if (sourceIdx >= 0 && sourceIdx < row.size) row[sourceIdx] else "CSV Direct Import"
                                val rowEmail = if (emailIdx >= 0 && emailIdx < row.size) row[emailIdx] else ""
                                val rowCompany = if (companyIdx >= 0 && companyIdx < row.size) row[companyIdx] else "Direct Partner"
                                
                                com.example.database.LeadEntity(
                                    name = rowName,
                                    phone = rowPhone,
                                    email = rowEmail,
                                    status = "New",
                                    source = rowSource,
                                    company = rowCompany,
                                    lastContacted = 0L
                                )
                            }.filter { it.name.isNotEmpty() && it.name != "N/A" && it.phone.isNotEmpty() && it.phone != "N/A" }

                            if (leadsToInsert.isNotEmpty()) {
                                viewModel.importLeadsBatch(leadsToInsert)
                                isImportSuccess = true
                                importedCount = leadsToInsert.size
                                viewModel.showToast("Directly imported ${leadsToInsert.size} leads successfully!", ToastType.SUCCESS)
                                
                                // Reset parsing state after brief period
                                selectedFileName = null
                                selectedFileSize = null
                                rawCsvContent = ""
                                parsedHeaders = emptyList()
                                parsedRows = emptyList()
                                mappedName = ""
                                mappedPhone = ""
                                mappedEmail = ""
                                mappedCompany = ""
                                mappedSource = ""
                            } else {
                                viewModel.showToast("No valid leads with complete Name & Phone details found in CSV", ToastType.ERROR)
                            }
                        },
                        enabled = canImport,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CosmicSecondary,
                            disabledContainerColor = CosmicSurfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .testTag("confirm_direct_import_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Confirm",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Import Leads Directly to State",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Success feedback block
            if (isImportSuccess) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CosmicSecondary.copy(alpha = 0.15f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = CosmicSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Success! Directly parsed and imported $importedCount leads to your active CRM state.",
                        color = CosmicSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SentimentDistributionDashboardCard(leads: List<com.example.database.LeadEntity>) {
    var selectedFilter by remember { mutableStateOf("Last 30 Days") } // "Last 30 Days" or "All Time"
    var selectedSegment by remember { mutableStateOf<String?>(null) } // "Positive", "Neutral", "Negative"

    // Computations based on selection
    val filteredLeads = remember(leads, selectedFilter) {
        if (selectedFilter == "Last 30 Days") {
            val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            leads.filter { it.lastContacted >= cutoff }
        } else {
            leads
        }
    }

    val totalCount = filteredLeads.size
    val positiveCount = filteredLeads.count { it.sentiment == "Positive" }
    val neutralCount = filteredLeads.count { it.sentiment == "Neutral" || (it.sentiment != "Positive" && it.sentiment != "Negative") }
    val negativeCount = filteredLeads.count { it.sentiment == "Negative" }

    val positivePercent = if (totalCount > 0) (positiveCount.toFloat() / totalCount * 100) else 0f
    val neutralPercent = if (totalCount > 0) (neutralCount.toFloat() / totalCount * 100) else 0f
    val negativePercent = if (totalCount > 0) (negativeCount.toFloat() / totalCount * 100) else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sentiment_distribution_dashboard_card"),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with filters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = null,
                            tint = CosmicAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "AI Sentiment Distribution",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CosmicTextPrimary
                        )
                    }
                    Text(
                        "Gemini-analyzed call sentiment records",
                        fontSize = 10.sp,
                        color = CosmicTextSecondary
                    )
                }

                // Filter Buttons Row
                Row(
                    modifier = Modifier
                        .background(CosmicSurfaceVariant, RoundedCornerShape(8.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf("Last 30 Days", "All Time").forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) CosmicPrimary else Color.Transparent)
                                .clickable {
                                    selectedFilter = filter
                                    selectedSegment = null // Clear selection when switching filters
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = filter,
                                color = if (isSelected) CosmicTextPrimary else CosmicTextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (totalCount == 0) {
                // Empty state helper
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = null,
                            tint = CosmicTextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No leads found in this timeframe.",
                            color = CosmicTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                // Grid layout with Chart on left, numeric progress on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Interactive Column Chart (Recharts-style)
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .height(140.dp)
                    ) {
                        SentimentInteractiveBarChart(
                            positiveVal = positiveCount.toFloat(),
                            neutralVal = neutralCount.toFloat(),
                            negativeVal = negativeCount.toFloat(),
                            selectedSegment = selectedSegment,
                            onSegmentSelect = { selectedSegment = if (selectedSegment == it) null else it }
                        )
                    }

                    // Right Column: Metric Breakdown Cards
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SentimentMetricIndicator(
                            label = "Positive",
                            count = positiveCount,
                            percentage = positivePercent,
                            color = Color(0xFF10B981),
                            isSelected = selectedSegment == "Positive",
                            onClick = { selectedSegment = if (selectedSegment == "Positive") null else "Positive" }
                        )

                        SentimentMetricIndicator(
                            label = "Neutral",
                            count = neutralCount,
                            percentage = neutralPercent,
                            color = Color(0xFF9CA3AF),
                            isSelected = selectedSegment == "Neutral",
                            onClick = { selectedSegment = if (selectedSegment == "Neutral") null else "Neutral" }
                        )

                        SentimentMetricIndicator(
                            label = "Negative",
                            count = negativeCount,
                            percentage = negativePercent,
                            color = Color(0xFFEF4444),
                            isSelected = selectedSegment == "Negative",
                            onClick = { selectedSegment = if (selectedSegment == "Negative") null else "Negative" }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Detail Tooltip/Drilldown Panel (Like Hover/Tooltip in Recharts)
                AnimatedVisibility(
                    visible = selectedSegment != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val segment = selectedSegment ?: ""
                    val count = when (segment) {
                        "Positive" -> positiveCount
                        "Neutral" -> neutralCount
                        "Negative" -> negativeCount
                        else -> 0
                    }
                    val pct = when (segment) {
                        "Positive" -> positivePercent
                        "Neutral" -> neutralPercent
                        "Negative" -> negativePercent
                        else -> 0f
                    }
                    val segmentColor = when (segment) {
                        "Positive" -> Color(0xFF10B981)
                        "Neutral" -> Color(0xFF9CA3AF)
                        "Negative" -> Color(0xFFEF4444)
                        else -> CosmicPrimary
                    }

                    val segmentLeads = remember(filteredLeads, segment) {
                        filteredLeads.filter {
                            if (segment == "Neutral") {
                                it.sentiment == "Neutral" || (it.sentiment != "Positive" && it.sentiment != "Negative")
                            } else {
                                it.sentiment == segment
                            }
                        }.take(3)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, segmentColor.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(segmentColor, CircleShape)
                                    )
                                    Text(
                                        text = "$segment Sentiment Insights",
                                        color = CosmicTextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "$count Leads (${String.format("%.1f%%", pct)})",
                                    color = segmentColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = when (segment) {
                                    "Positive" -> "Highly engaged prospects. They responded very well during call transcripts. Recommended next step: Prepare pricing quotes or closing agreements immediately."
                                    "Negative" -> "Call recordings show objections, concerns, or uncooperative responses. Recommended next step: Enable 'Empathy Coaching Mode' in scripts, re-engage after 48h."
                                    else -> "Prospects responded with standard questions. Neutral interest with medium buy intent. Recommended next step: Share informational product decks or schedule custom demo walkthroughs."
                                },
                                color = CosmicTextSecondary,
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp
                            )

                            if (segmentLeads.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Key Leads in this Segment:",
                                    color = CosmicTextPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    segmentLeads.forEach { lead ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(CosmicSurface, RoundedCornerShape(6.dp))
                                                .border(1.dp, CosmicSurfaceVariant, RoundedCornerShape(6.dp))
                                                .padding(6.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = lead.name,
                                                    color = CosmicTextPrimary,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = lead.company,
                                                    color = CosmicTextSecondary,
                                                    fontSize = 8.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // General AI Insights section based on the distribution
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CosmicPrimary.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = CosmicPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            val ratioMessage = if (positivePercent >= 50f) {
                                "Excellent pipeline health! Over 50% of your contacted prospects show highly Positive sentiments."
                            } else if (negativePercent >= 35f) {
                                "Attention required: High negative sentiment scores detected. Run objection coaching sessions."
                            } else {
                                "Balanced conversion pipeline. Focus on moving Neutral prospects to Positive using custom product trials."
                            }
                            Text(
                                text = "30-Day Pipeline Health Analysis",
                                color = CosmicTextPrimary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = ratioMessage,
                                color = CosmicTextSecondary,
                                fontSize = 9.sp,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SentimentInteractiveBarChart(
    positiveVal: Float,
    neutralVal: Float,
    negativeVal: Float,
    selectedSegment: String?,
    onSegmentSelect: (String) -> Unit
) {
    val total = positiveVal + neutralVal + negativeVal
    val maxVal = maxOf(positiveVal, neutralVal, negativeVal, 1f)

    // Animations for bars
    val animPositiveHeight by animateFloatAsState(
        targetValue = if (total > 0f) (positiveVal / maxVal) else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 150f)
    )
    val animNeutralHeight by animateFloatAsState(
        targetValue = if (total > 0f) (neutralVal / maxVal) else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 150f)
    )
    val animNegativeHeight by animateFloatAsState(
        targetValue = if (total > 0f) (negativeVal / maxVal) else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 150f)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 20.dp)
                .pointerInput(positiveVal, neutralVal, negativeVal) {
                    detectTapGestures { offset ->
                        val width = size.width
                        val height = size.height
                        val colWidth = width / 3f

                        val clickedCol = when {
                            offset.x < colWidth -> "Positive"
                            offset.x < colWidth * 2f -> "Neutral"
                            else -> "Negative"
                        }
                        onSegmentSelect(clickedCol)
                    }
                }
        ) {
            val width = size.width
            val height = size.height

            // 1. Draw horizontal grid lines (Recharts style background grid)
            val gridLinesCount = 4
            for (i in 0 until gridLinesCount) {
                val y = (height / gridLinesCount) * i
                drawLine(
                    color = CosmicTextSecondary.copy(alpha = 0.08f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // 2. Draw axes
            drawLine(
                color = CosmicTextSecondary.copy(alpha = 0.2f),
                start = Offset(0f, height),
                end = Offset(width, height),
                strokeWidth = 1.5.dp.toPx()
            )

            // 3. Define columns coordinates
            val barMaxDrawHeight = height - 10.dp.toPx()
            val barWidth = 24.dp.toPx()
            val columns = listOf(
                Triple("Positive", animPositiveHeight, Color(0xFF10B981)),
                Triple("Neutral", animNeutralHeight, Color(0xFF9CA3AF)),
                Triple("Negative", animNegativeHeight, Color(0xFFEF4444))
            )

            val colWidth = width / 3f

            columns.forEachIndexed { index, (label, animHt, color) ->
                val colCenterX = colWidth * index + colWidth / 2f
                val barHeight = animHt * barMaxDrawHeight
                val barTopY = height - barHeight

                // Is this segment currently active/selected?
                val isSelected = selectedSegment == label
                val isAnySelected = selectedSegment != null
                val opacity = if (isAnySelected && !isSelected) 0.35f else 1f

                // Create linear gradient brush for the bar
                val brush = Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = opacity),
                        color.copy(alpha = 0.35f * opacity)
                    )
                )

                // Draw Bar Rect with rounded top corners
                val path = Path().apply {
                    val left = colCenterX - barWidth / 2f
                    val right = colCenterX + barWidth / 2f
                    val bottom = height
                    val top = barTopY
                    val radius = 4.dp.toPx()

                    moveTo(left, bottom)
                    lineTo(left, top + radius)
                    quadraticBezierTo(left, top, left + radius, top)
                    lineTo(right - radius, top)
                    quadraticBezierTo(right, top, right, top + radius)
                    lineTo(right, bottom)
                    close()
                }

                drawPath(path = path, brush = brush)

                // Highlight border if selected
                if (isSelected) {
                    val outlinePath = Path().apply {
                        val left = colCenterX - barWidth / 2f
                        val right = colCenterX + barWidth / 2f
                        val bottom = height
                        val top = barTopY
                        val radius = 4.dp.toPx()

                        moveTo(left, bottom)
                        lineTo(left, top + radius)
                        quadraticBezierTo(left, top, left + radius, top)
                        lineTo(right - radius, top)
                        quadraticBezierTo(right, top, right, top + radius)
                        lineTo(right, bottom)
                    }
                    drawPath(
                        path = outlinePath,
                        color = color,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        // Overlaying the X-Axis Labels using a Row to ensure perfect Text rendering and typography matching
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Positive", "Neutral", "Negative").forEach { label ->
                    val isSelected = selectedSegment == label
                    Text(
                        text = label,
                        color = if (isSelected) CosmicTextPrimary else CosmicTextSecondary.copy(alpha = 0.8f),
                        fontSize = 8.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(55.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SentimentMetricIndicator(
    label: String,
    count: Int,
    percentage: Float,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) color.copy(alpha = 0.1f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) color.copy(alpha = 0.4f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(color, CircleShape)
                    )
                    Text(label, color = CosmicTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    text = "$count leads (${String.format("%.1f%%", percentage)})",
                    color = CosmicTextSecondary,
                    fontSize = 9.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = percentage / 100f,
                color = color,
                trackColor = CosmicSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .clip(CircleShape)
            )
        }
    }
}


@Composable
fun SegmentLegendItem(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(label, color = CosmicTextSecondary, fontSize = 9.sp)
    }
}

@Composable
fun FunnelDonutChart(leads: List<com.example.database.LeadEntity>) {
    val statuses = leads.groupBy { it.status }
    val newCount = statuses["New"]?.size ?: 0
    val contactedCount = statuses["Contacted"]?.size ?: 0
    val interestedCount = statuses["Interested"]?.size ?: 0
    val convertedCount = statuses["Converted"]?.size ?: 0
    val total = (newCount + contactedCount + interestedCount + convertedCount).toFloat()

    val segments = listOf(
        Pair(newCount, CosmicAccent),
        Pair(contactedCount, CosmicPrimary),
        Pair(interestedCount, Color(0xFFF59E0B)),
        Pair(convertedCount, CosmicSecondary)
    ).filter { it.first > 0 }

    Box(
        modifier = Modifier
            .size(110.dp)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (total == 0f) {
            Text("No Data", color = CosmicTextSecondary, fontSize = 11.sp)
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var startAngle = -90f
                segments.forEach { (count, color) ->
                    val sweepAngle = (count / total) * 360f
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx())
                    )
                    startAngle += sweepAngle
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${total.toInt()}", color = CosmicTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Total", color = CosmicTextSecondary, fontSize = 8.sp)
            }
        }
    }
}

@Composable
fun OutboundCallingLineChart() {
    val points = listOf(14f, 26f, 18f, 35f, 42f, 29f, 38f) // Calls Mon-Sun
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        val width = size.width
        val height = size.height
        val maxVal = points.maxOrNull() ?: 50f
        
        // Grid
        val gridCount = 3
        for (i in 0..gridCount) {
            val y = height * (i.toFloat() / gridCount)
            drawLine(
                color = CosmicTextSecondary.copy(alpha = 0.08f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
        }
        
        val path = Path()
        val fillPath = Path()
        
        val stepX = width / (points.size - 1)
        points.forEachIndexed { idx, valY ->
            val x = idx * stepX
            val normalizedY = height - (valY / maxVal) * (height - 15f)
            if (idx == 0) {
                path.moveTo(x, normalizedY)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, normalizedY)
            } else {
                path.lineTo(x, normalizedY)
                fillPath.lineTo(x, normalizedY)
            }
            if (idx == points.size - 1) {
                fillPath.lineTo(x, height)
                fillPath.close()
            }
        }
        
        // Fill Area
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(CosmicPrimary.copy(alpha = 0.2f), Color.Transparent)
            )
        )
        
        // Stroke
        drawPath(
            path = path,
            color = CosmicPrimary,
            style = Stroke(width = 2.5.dp.toPx())
        )
        
        // Points
        points.forEachIndexed { idx, valY ->
            val x = idx * stepX
            val normalizedY = height - (valY / maxVal) * (height - 15f)
            drawCircle(
                color = CosmicSecondary,
                radius = 3.5.dp.toPx(),
                center = Offset(x, normalizedY)
            )
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        days.forEach { day ->
            Text(day, color = CosmicTextSecondary, fontSize = 8.sp)
        }
    }
}

@Composable
fun ChannelsBarChart(leads: List<com.example.database.LeadEntity>) {
    val sources = leads.groupBy { it.source }
    val fbCount = sources["Facebook Ads"]?.size ?: sources["Facebook"]?.size ?: 0
    val waCount = sources["WhatsApp"]?.size ?: 0
    val hubCount = sources["Manual"]?.size ?: sources["CRM Hub"]?.size ?: 0
    
    val total = (fbCount + waCount + hubCount).toFloat()
    val fbPercent = if (total > 0) fbCount / total else 0.45f
    val waPercent = if (total > 0) waCount / total else 0.30f
    val hubPercent = if (total > 0) hubCount / total else 0.25f
    
    val channels = listOf(
        Triple("Facebook Ads", fbPercent, CosmicPrimary),
        Triple("WhatsApp API", waPercent, CosmicSecondary),
        Triple("CRM Direct", hubPercent, CosmicAccent)
    )
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        channels.forEach { (name, percent, color) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(name, color = CosmicTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    Text("${(percent * 100).toInt()}%", color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(CosmicSurfaceVariant, RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(percent)
                            .fillMaxHeight()
                            .background(color, RoundedCornerShape(3.dp))
                    )
                }
            }
        }
    }
}

// --- CSV/SHEETS BATCH IMPORT WIZARD DIALOG ---
private fun splitCsvLine(line: String, delimiter: String): List<String> {
    if (!line.contains("\"")) {
        return line.split(delimiter).map { it.trim() }
    }
    val result = mutableListOf<String>()
    val currentField = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        if (c == '"') {
            inQuotes = !inQuotes
        } else if (c.toString() == delimiter && !inQuotes) {
            result.add(currentField.toString().trim())
            currentField.setLength(0)
        } else {
            currentField.append(c)
        }
        i++
    }
    result.add(currentField.toString().trim())
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CSVImportWizardDialog(
    viewModel: CRMViewModel,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(1) } // Steps: 1 (Upload/Paste), 2 (Column Mapping), 3 (Preview & Validate), 4 (Done)
    var rawCsvText by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { selectedUri ->
            try {
                context.contentResolver.openInputStream(selectedUri)?.use { inputStream ->
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                    val content = reader.readText()
                    if (content.isNotBlank()) {
                        rawCsvText = content
                        viewModel.showToast("CSV file loaded successfully!", ToastType.SUCCESS)
                    } else {
                        viewModel.showToast("Selected CSV file is empty", ToastType.ERROR)
                    }
                }
            } catch (e: Exception) {
                viewModel.showToast("Failed to read file: ${e.localizedMessage}", ToastType.ERROR)
            }
        }
    }

    // Parsed rows
    var parsedRows by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var parsedHeaders by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Column Mapping mappings: Map internal field name to CSV column header
    var mappedNameField by remember { mutableStateOf("") }
    var mappedPhoneField by remember { mutableStateOf("") }
    var mappedEmailField by remember { mutableStateOf("") }
    var mappedSourceField by remember { mutableStateOf("") }
    var mappedCompanyField by remember { mutableStateOf("") }
    var mappedValueField by remember { mutableStateOf("") }

    val internalFields = listOf("Name", "Phone", "Email", "Source", "Company", "Value")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .padding(10.dp)
                .testTag("import_wizard_dialog_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            border = BorderStroke(1.dp, CosmicAccent.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header & Wizard Progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = CosmicAccent, modifier = Modifier.size(18.dp))
                        Text(
                            "Leads Batch Import Wizard",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = CosmicTextPrimary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = CosmicTextSecondary)
                    }
                }

                // Progress Tracker Indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("1. Load Data", "2. Map Schema", "3. Batch Insert").forEachIndexed { idx, label ->
                        val active = step == (idx + 1)
                        val completed = step > (idx + 1)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .background(
                                    if (active) CosmicAccent else if (completed) CosmicSecondary else CosmicSurfaceVariant,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Content weight
                Box(modifier = Modifier.weight(1f)) {
                    when (step) {
                        1 -> {
                            // Step 1: Paste CSV / Load Sample
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "Select a CSV/TSV file from your device or paste raw records below to map them:",
                                    fontSize = 11.sp,
                                    color = CosmicTextSecondary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            try {
                                                filePickerLauncher.launch("*/*")
                                            } catch (e: Exception) {
                                                viewModel.showToast("Cannot open file picker: ${e.localizedMessage}", ToastType.ERROR)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary.copy(alpha = 0.15f), contentColor = CosmicSecondary),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1.3f).height(36.dp).testTag("select_csv_file_btn")
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Upload CSV File", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            rawCsvText = "Client Name,Contact Number,Client Email,Lead Source,Organization,Deal Value\n" +
                                                    "John Miller,+1 (555) 0122,john@miller.org,Facebook Ads,Miller Labs,4500\n" +
                                                    "Sophia Ramirez,+1 (555) 0134,sophia.r@outlook.com,WhatsApp,Ramirez Co,1200\n" +
                                                    "Liam Chen,+1 (555) 0147,liam.chen@techsolutions.io,CRM Hub,Tech Solutions,8900\n" +
                                                    "Elena Rostova,+1 (555) 0192,elena.rostova@kaspersky.ru,Facebook Ads,Rostov Global,5200\n" +
                                                    "Marcus Aurelius,+1 (555) 0161,marcus@romanemp.it,Manual,Stoic Solutions,3000"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary.copy(alpha = 0.15f), contentColor = CosmicPrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).height(36.dp).testTag("load_sample_csv_btn")
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Sample Template", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedTextField(
                                    value = rawCsvText,
                                    onValueChange = { rawCsvText = it },
                                    placeholder = { Text("Paste CSV data here...\ne.g. Name,Phone,Source,Email", color = CosmicTextSecondary, fontSize = 11.sp) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .testTag("csv_text_input"),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = CosmicTextPrimary, fontSize = 11.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CosmicAccent,
                                        unfocusedBorderColor = CosmicSurfaceVariant,
                                        focusedContainerColor = CosmicBackground,
                                        unfocusedContainerColor = CosmicBackground
                                    )
                                )
                            }
                        }
                        2 -> {
                            // Step 2: Column Mapping Selector
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "Map spreadsheet columns to internal CRM Database Schema:",
                                    fontSize = 11.sp,
                                    color = CosmicTextSecondary
                                )

                                internalFields.forEach { field ->
                                    val currentSelection = when (field) {
                                        "Name" -> mappedNameField
                                        "Phone" -> mappedPhoneField
                                        "Email" -> mappedEmailField
                                        "Source" -> mappedSourceField
                                        "Company" -> mappedCompanyField
                                        "Value" -> mappedValueField
                                        else -> ""
                                    }

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = CosmicBackground),
                                        border = BorderStroke(1.dp, CosmicSurfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CosmicSecondary, modifier = Modifier.size(14.dp))
                                                Text(field, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                                            }

                                            // Dropdown simulation for mapper
                                            var expandedDropdown by remember { mutableStateOf(false) }
                                            Box {
                                                Button(
                                                    onClick = { expandedDropdown = true },
                                                    colors = ButtonDefaults.buttonColors(containerColor = CosmicSurface, contentColor = CosmicTextPrimary),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(28.dp).testTag("map_select_${field.lowercase()}")
                                                ) {
                                                    Text(currentSelection.ifEmpty { "Select Column" }, fontSize = 10.sp)
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(12.dp))
                                                }

                                                DropdownMenu(
                                                    expanded = expandedDropdown,
                                                    onDismissRequest = { expandedDropdown = false },
                                                    modifier = Modifier.background(CosmicSurface)
                                                ) {
                                                    parsedHeaders.forEach { header ->
                                                        DropdownMenuItem(
                                                            text = { Text(header, color = CosmicTextPrimary, fontSize = 11.sp) },
                                                            onClick = {
                                                                when (field) {
                                                                    "Name" -> mappedNameField = header
                                                                    "Phone" -> mappedPhoneField = header
                                                                    "Email" -> mappedEmailField = header
                                                                    "Source" -> mappedSourceField = header
                                                                    "Company" -> mappedCompanyField = header
                                                                    "Value" -> mappedValueField = header
                                                                }
                                                                expandedDropdown = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        3 -> {
                            // Step 3: Visual Validation and Spreadsheet preview
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "Visual Data Quality Validation: Verify mapped rows before final batch database insertion.",
                                    fontSize = 11.sp,
                                    color = CosmicTextSecondary
                                )

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(CosmicBackground, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(CosmicSurfaceVariant, RoundedCornerShape(4.dp))
                                                .padding(6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("Status", modifier = Modifier.weight(0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                                            Text("Name", modifier = Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                                            Text("Phone", modifier = Modifier.weight(1.2f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                                            Text("Source", modifier = Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                                        }
                                    }

                                    // Render parsed rows mapping
                                    val nameIdx = parsedHeaders.indexOf(mappedNameField)
                                    val phoneIdx = parsedHeaders.indexOf(mappedPhoneField)
                                    val sourceIdx = parsedHeaders.indexOf(mappedSourceField)
                                    val emailIdx = parsedHeaders.indexOf(mappedEmailField)

                                    items(parsedRows) { row ->
                                        val rowName = if (nameIdx >= 0 && nameIdx < row.size) row[nameIdx] else "N/A"
                                        val rowPhone = if (phoneIdx >= 0 && phoneIdx < row.size) row[phoneIdx] else "N/A"
                                        val rowSource = if (sourceIdx >= 0 && sourceIdx < row.size) row[sourceIdx] else "N/A"
                                        val rowEmail = if (emailIdx >= 0 && emailIdx < row.size) row[emailIdx] else "N/A"

                                        val isValid = rowPhone.isNotEmpty() && rowPhone != "N/A" && rowName.isNotEmpty() && rowName != "N/A"

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(1.dp, CosmicSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                .padding(6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(0.5f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(if (isValid) CosmicSecondary else Color(0xFFEF4444), CircleShape)
                                                )
                                                Text(if (isValid) "OK" else "Err", fontSize = 9.sp, color = if (isValid) CosmicSecondary else Color(0xFFEF4444))
                                            }
                                            Text(rowName, modifier = Modifier.weight(1f), fontSize = 10.sp, color = CosmicTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(rowPhone, modifier = Modifier.weight(1.2f), fontSize = 10.sp, color = CosmicTextSecondary, maxLines = 1)
                                            Text(rowSource, modifier = Modifier.weight(1f), fontSize = 10.sp, color = CosmicPrimary, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (step > 1) {
                        Button(
                            onClick = { step -= 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = CosmicSurfaceVariant, contentColor = CosmicTextPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Back", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            if (step == 1) {
                                if (rawCsvText.isBlank()) {
                                    // Fail gracefully
                                    return@Button
                                }
                                // Parse CSV rows robustly
                                val lines = rawCsvText.replace("\r\n", "\n").split("\n").filter { it.isNotBlank() }
                                if (lines.isNotEmpty()) {
                                    val delimiter = if (lines[0].contains(";")) ";" else if (lines[0].contains("\t")) "\t" else ","
                                    val headers = splitCsvLine(lines[0], delimiter)
                                    parsedHeaders = headers
                                    
                                    val rows = lines.drop(1).map { line ->
                                        splitCsvLine(line, delimiter)
                                    }
                                    parsedRows = rows

                                    // Auto-detect columns based on match headers names
                                    mappedNameField = headers.find { it.contains("Name", ignoreCase = true) || it.contains("Client", ignoreCase = true) } ?: headers.firstOrNull() ?: ""
                                    mappedPhoneField = headers.find { it.contains("Phone", ignoreCase = true) || it.contains("Contact", ignoreCase = true) || it.contains("Number", ignoreCase = true) } ?: ""
                                    mappedEmailField = headers.find { it.contains("Email", ignoreCase = true) || it.contains("Mail", ignoreCase = true) } ?: ""
                                    mappedSourceField = headers.find { it.contains("Source", ignoreCase = true) || it.contains("Channel", ignoreCase = true) } ?: ""
                                    mappedCompanyField = headers.find { it.contains("Company", ignoreCase = true) || it.contains("Organization", ignoreCase = true) || it.contains("Firm", ignoreCase = true) } ?: ""
                                    mappedValueField = headers.find { it.contains("Value", ignoreCase = true) || it.contains("Worth", ignoreCase = true) || it.contains("Deal", ignoreCase = true) } ?: ""
                                    
                                    step = 2
                                }
                            } else if (step == 2) {
                                step = 3
                            } else if (step == 3) {
                                // Final step: batch insert
                                val nameIdx = parsedHeaders.indexOf(mappedNameField)
                                val phoneIdx = parsedHeaders.indexOf(mappedPhoneField)
                                val sourceIdx = parsedHeaders.indexOf(mappedSourceField)
                                val emailIdx = parsedHeaders.indexOf(mappedEmailField)
                                val companyIdx = parsedHeaders.indexOf(mappedCompanyField)
                                val valueIdx = parsedHeaders.indexOf(mappedValueField)

                                val leadsToInsert = parsedRows.map { row ->
                                    val rowName = if (nameIdx >= 0 && nameIdx < row.size) row[nameIdx] else "Unknown Import"
                                    val rowPhone = if (phoneIdx >= 0 && phoneIdx < row.size) row[phoneIdx] else ""
                                    val rowSource = if (sourceIdx >= 0 && sourceIdx < row.size) row[sourceIdx] else "CSV Import"
                                    val rowEmail = if (emailIdx >= 0 && emailIdx < row.size) row[emailIdx] else ""
                                    val rowCompany = if (companyIdx >= 0 && companyIdx < row.size) row[companyIdx] else "Direct Partner"
                                    
                                    LeadEntity(
                                        name = rowName,
                                        phone = rowPhone,
                                        email = rowEmail,
                                        status = "New",
                                        source = rowSource,
                                        company = rowCompany,
                                        lastContacted = 0L
                                    )
                                }.filter { it.name.isNotEmpty() && it.phone.isNotEmpty() }

                                viewModel.importLeadsBatch(leadsToInsert)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (step == 3) CosmicSecondary else CosmicAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("import_wizard_next_btn")
                    ) {
                        Text(
                            text = if (step == 1) "Parse Records" else if (step == 2) "Confirm Schema" else "Import Mapped Leads",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// --- COMPLEX ADMINISTRATIVE PANEL AND CONNECTIONS HUB TAB ---
@Composable
fun AdminHubTab(
    viewModel: CRMViewModel,
    onTabNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val telecallers by viewModel.telecallers.collectAsStateWithLifecycle()
    val adminSettings by viewModel.adminSettings.collectAsStateWithLifecycle()

    var hasCallPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CALL_PHONE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCallPermission = isGranted
    }

    var selectedSubTab by remember { mutableStateOf("roles") } // "roles", "connectors", "logs"
    var showAddTeammateDialog by remember { mutableStateOf(false) }
    var showFbAuthDialog by remember { mutableStateOf(false) }

    // Forms settings
    var fbPageAccessToken by remember { mutableStateOf("") }
    var fbFormId by remember { mutableStateOf("") }
    var fbAppId by remember { mutableStateOf("") }
    var fbAppSecret by remember { mutableStateOf("") }
    var whatsappBusinessId by remember { mutableStateOf("") }
    var whatsappToken by remember { mutableStateOf("") }
    var fbPageId by remember { mutableStateOf("") }
    var fbPageName by remember { mutableStateOf("") }
    var fbWebhookActive by remember { mutableStateOf(false) }
    var fbWebhookVerifyToken by remember { mutableStateOf("") }
    var geminiApiKey by remember { mutableStateOf("") }

    // Visibility toggles for secure input
    var showWaToken by remember { mutableStateOf(false) }
    var showFbToken by remember { mutableStateOf(false) }
    var showFbSecret by remember { mutableStateOf(false) }
    var showGeminiKey by remember { mutableStateOf(false) }

    // Sync state values on load
    LaunchedEffect(adminSettings) {
        adminSettings?.let {
            fbPageAccessToken = it.fbPageAccessToken
            fbFormId = it.fbFormId
            fbAppId = it.fbAppId
            fbAppSecret = it.fbAppSecret
            whatsappBusinessId = it.whatsappBusinessPhoneNumberId
            whatsappToken = it.whatsappAccessToken
            fbPageId = it.fbPageId
            fbPageName = it.fbPageName
            fbWebhookActive = it.fbWebhookActive
            fbWebhookVerifyToken = it.fbWebhookVerifyToken.ifEmpty { "meta_verify_token_12345" }
            geminiApiKey = it.geminiApiKey
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Administrative Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            border = BorderStroke(1.dp, CosmicAccent.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "CRM Security & Administration",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = CosmicTextPrimary
                        )
                        Text(
                            "Control agent authorizations, define access levels, monitor active sessions, and configure webhooks.",
                            fontSize = 10.5.sp,
                            color = CosmicTextSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(CosmicAccent.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = CosmicAccent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // Subtabs selection
        TabRow(
            selectedTabIndex = when (selectedSubTab) {
                "roles" -> 0
                "connectors" -> 1
                "logs" -> 2
                else -> 0
            },
            containerColor = CosmicBackground,
            contentColor = CosmicPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(
                        tabPositions[when (selectedSubTab) {
                            "roles" -> 0
                            "connectors" -> 1
                            "logs" -> 2
                            else -> 0
                        }]
                    ),
                    color = CosmicAccent
                )
            },
            modifier = Modifier.fillMaxWidth().height(42.dp)
        ) {
            Tab(
                selected = selectedSubTab == "roles",
                onClick = { selectedSubTab = "roles" },
                text = { Text("Team Permissions", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                selectedContentColor = CosmicAccent,
                unselectedContentColor = CosmicTextSecondary,
                modifier = Modifier.testTag("admin_subtab_team")
            )
            Tab(
                selected = selectedSubTab == "connectors",
                onClick = { selectedSubTab = "connectors" },
                text = { Text("Real API Connectors", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                selectedContentColor = CosmicAccent,
                unselectedContentColor = CosmicTextSecondary,
                modifier = Modifier.testTag("admin_subtab_connectors")
            )
            Tab(
                selected = selectedSubTab == "logs",
                onClick = { selectedSubTab = "logs" },
                text = { Text("Sync Logs Hub", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                selectedContentColor = CosmicAccent,
                unselectedContentColor = CosmicTextSecondary,
                modifier = Modifier.testTag("admin_subtab_sync")
            )
        }

        // Subtab views
        Box(modifier = Modifier.weight(1f)) {
            when (selectedSubTab) {
                "roles" -> {
                    // Team Permissions Sub-tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            // Quick invite teammate button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Registered CRM Agents", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                                Button(
                                    onClick = { showAddTeammateDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = CosmicAccent),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(32.dp).testTag("invite_teammate_btn")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Agent", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Teammates directory list with access controls dropdown
                        items(telecallers) { caller ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                                border = BorderStroke(1.dp, if (caller.isAuthorized) CosmicSurfaceVariant else Color(0xFFEF4444).copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(caller.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            if (caller.status == "Offline") CosmicSurfaceVariant else if (caller.status == "Busy") Color(0xFFF59E0B).copy(alpha = 0.2f) else CosmicSecondary.copy(alpha = 0.2f),
                                                            RoundedCornerShape(4.dp)
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(caller.status, fontSize = 8.sp, color = if (caller.status == "Offline") CosmicTextSecondary else if (caller.status == "Busy") Color(0xFFF59E0B) else CosmicSecondary)
                                                }
                                            }
                                            Text(caller.email.ifEmpty { "no-email@crmtele.com" }, fontSize = 10.sp, color = CosmicTextSecondary)
                                        }

                                        // Authorize Switch
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(if (caller.isAuthorized) "Access Active" else "Access Revoked", fontSize = 10.sp, color = if (caller.isAuthorized) CosmicSecondary else Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                            Switch(
                                                checked = caller.isAuthorized,
                                                onCheckedChange = { viewModel.toggleTelecallerAccess(caller) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = CosmicSecondary,
                                                    checkedTrackColor = CosmicSecondary.copy(alpha = 0.3f),
                                                    uncheckedThumbColor = Color(0xFFEF4444),
                                                    uncheckedTrackColor = Color(0xFFEF4444).copy(alpha = 0.2f)
                                                ),
                                                modifier = Modifier.scale(0.8f).testTag("toggle_access_${caller.name.replace(" ", "_").lowercase()}")
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = CosmicSurfaceVariant, thickness = 0.5.dp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Set Access Privileges:", fontSize = 11.sp, color = CosmicTextSecondary)
                                        
                                        // Access level toggle selector
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf("Full Edit", "Read-Only").forEach { level ->
                                                val active = caller.accessLevel == level
                                                Button(
                                                    onClick = { viewModel.updateTelecallerAccessLevel(caller, level) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (active) CosmicAccent else CosmicSurfaceVariant,
                                                        contentColor = CosmicTextPrimary
                                                    ),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(26.dp).testTag("privilege_${caller.name.replace(" ", "_").lowercase()}_${level.replace(" ", "_").lowercase()}")
                                                ) {
                                                    Text(level, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Active Sessions List Card
                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Active User Device Sessions", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                            Spacer(modifier = Modifier.height(6.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CosmicSurface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Session Row 1: Admin
                                    SessionItemRow(
                                        user = "Super Admin (You)",
                                        device = "Pixel 8 Pro (Android)",
                                        ip = "192.168.1.115",
                                        duration = "Active now",
                                        isRevocable = false,
                                        onRevoke = {}
                                    )
                                    HorizontalDivider(color = CosmicSurfaceVariant, thickness = 0.5.dp)
                                    
                                    // Session Row 2: Sarah
                                    val isSarahRevoked = telecallers.find { it.name.contains("Sarah", ignoreCase = true) }?.isAuthorized == false
                                    SessionItemRow(
                                        user = "Agent Sarah",
                                        device = "Chrome / MacOS Ventura",
                                        ip = "104.28.32.19",
                                        duration = if (isSarahRevoked) "Offline" else "1h 14m ago",
                                        isRevocable = !isSarahRevoked,
                                        onRevoke = {
                                            val sarah = telecallers.find { it.name.contains("Sarah", ignoreCase = true) }
                                            if (sarah != null) viewModel.toggleTelecallerAccess(sarah)
                                        }
                                    )
                                    HorizontalDivider(color = CosmicSurfaceVariant, thickness = 0.5.dp)

                                    // Session Row 3: Jessica
                                    val isJessicaRevoked = telecallers.find { it.name.contains("Jessica", ignoreCase = true) }?.isAuthorized == false
                                    SessionItemRow(
                                        user = "Agent Jessica",
                                        device = "Android Emulator",
                                        ip = "10.0.2.15",
                                        duration = if (isJessicaRevoked) "Offline" else "5m ago",
                                        isRevocable = !isJessicaRevoked,
                                        onRevoke = {
                                            val jessica = telecallers.find { it.name.contains("Jessica", ignoreCase = true) }
                                            if (jessica != null) viewModel.toggleTelecallerAccess(jessica)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                "connectors" -> {
                    // Real API Connectors Subtab (Facebook and WhatsApp Credentials setup)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text("Real-Time External Integrations", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                            Text("Provide connection keys. Webhooks synchronize submissions instantly.", fontSize = 10.sp, color = CosmicTextSecondary)
                        }

                        // WhatsApp Business Cloud API Configuration
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                                border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(CosmicSecondary.copy(alpha = 0.15f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Send, contentDescription = null, tint = CosmicSecondary, modifier = Modifier.size(12.dp))
                                        }
                                        Text("WhatsApp Business Cloud API", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                                    }

                                    OutlinedTextField(
                                        value = whatsappBusinessId,
                                        onValueChange = { whatsappBusinessId = it },
                                        label = { Text("Phone Number ID", fontSize = 10.sp) },
                                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("wa_phone_id_input"),
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = CosmicTextPrimary, fontSize = 11.sp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CosmicAccent, unfocusedBorderColor = CosmicSurfaceVariant)
                                    )

                                    OutlinedTextField(
                                        value = whatsappToken,
                                        onValueChange = { whatsappToken = it },
                                        label = { Text("System User Access Token", fontSize = 10.sp) },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("wa_token_input"),
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = CosmicTextPrimary, fontSize = 11.sp),
                                        visualTransformation = if (showWaToken) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { showWaToken = !showWaToken }) {
                                                Icon(
                                                    imageVector = if (showWaToken) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = "Toggle Visibility",
                                                    tint = CosmicTextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CosmicAccent, unfocusedBorderColor = CosmicSurfaceVariant)
                                    )
                                }
                            }
                        }

                        // Facebook Leads Capture Ads Setup
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                                border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(CosmicPrimary.copy(alpha = 0.15f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, tint = CosmicPrimary, modifier = Modifier.size(12.dp))
                                        }
                                        Text("Facebook Ad Lead Forms Capture", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                                    }

                                    OutlinedTextField(
                                        value = fbPageAccessToken,
                                        onValueChange = { fbPageAccessToken = it },
                                        label = { Text("Page Graph Access Token", fontSize = 10.sp) },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("fb_page_token_input"),
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = CosmicTextPrimary, fontSize = 11.sp),
                                        visualTransformation = if (showFbToken) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { showFbToken = !showFbToken }) {
                                                Icon(
                                                    imageVector = if (showFbToken) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = "Toggle Visibility",
                                                    tint = CosmicTextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CosmicAccent, unfocusedBorderColor = CosmicSurfaceVariant)
                                    )

                                    OutlinedTextField(
                                        value = fbFormId,
                                        onValueChange = { fbFormId = it },
                                        label = { Text("Lead Form ID", fontSize = 10.sp) },
                                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("fb_form_id_input"),
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = CosmicTextPrimary, fontSize = 11.sp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CosmicAccent, unfocusedBorderColor = CosmicSurfaceVariant)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = fbAppId,
                                            onValueChange = { fbAppId = it },
                                            label = { Text("Meta App ID", fontSize = 10.sp) },
                                            modifier = Modifier.weight(1f).height(48.dp),
                                            textStyle = MaterialTheme.typography.bodySmall.copy(color = CosmicTextPrimary, fontSize = 11.sp),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CosmicAccent, unfocusedBorderColor = CosmicSurfaceVariant)
                                        )
                                        OutlinedTextField(
                                            value = fbAppSecret,
                                            onValueChange = { fbAppSecret = it },
                                            label = { Text("Meta Secret", fontSize = 10.sp) },
                                            modifier = Modifier.weight(1.2f).heightIn(min = 48.dp),
                                            textStyle = MaterialTheme.typography.bodySmall.copy(color = CosmicTextPrimary, fontSize = 11.sp),
                                            visualTransformation = if (showFbSecret) VisualTransformation.None else PasswordVisualTransformation(),
                                            trailingIcon = {
                                                IconButton(onClick = { showFbSecret = !showFbSecret }) {
                                                    Icon(
                                                        imageVector = if (showFbSecret) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                        contentDescription = "Toggle Visibility",
                                                        tint = CosmicTextSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CosmicAccent, unfocusedBorderColor = CosmicSurfaceVariant)
                                        )
                                    }

                                    HorizontalDivider(color = CosmicSurfaceVariant, thickness = 0.5.dp)

                                    // Page Authentication status block
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Page Authentication Status", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CosmicTextSecondary)
                                            if (fbPageId.isNotEmpty()) {
                                                Text("Connected: $fbPageName", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CosmicSecondary)
                                                Text("Page ID: $fbPageId", fontSize = 9.sp, color = CosmicTextSecondary)
                                            } else {
                                                Text("No Page Connected", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                                            }
                                        }
                                        Button(
                                            onClick = { showFbAuthDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(32.dp).testTag("btn_authenticate_fb_page")
                                        ) {
                                            Text(if (fbPageId.isNotEmpty()) "Manage Connection" else "Connect Page", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    HorizontalDivider(color = CosmicSurfaceVariant, thickness = 0.5.dp)

                                    // Webhook settings & status
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Webhook Subscription Status", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CosmicTextSecondary)
                                                if (fbWebhookActive) {
                                                    Text("Status: Subscribed & Listening (200 OK)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                                } else {
                                                    Text("Status: Pending Registration", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                                                }
                                            }
                                            if (fbPageId.isNotEmpty()) {
                                                Button(
                                                    onClick = {
                                                        viewModel.subscribeWebhookForPage(
                                                            fbPageId,
                                                            fbPageName,
                                                            fbPageAccessToken.ifEmpty { "EAAGzB4ZCSD3IBAOzpY0XId0ZC5ZA1mK36Fm8vL2VZB7r...2w8ZA" },
                                                            fbWebhookVerifyToken.ifEmpty { "meta_verify_token_12345" }
                                                        )
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = if (fbWebhookActive) CosmicSurfaceVariant else CosmicAccent),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.height(32.dp).testTag("btn_subscribe_webhook")
                                                ) {
                                                    Text(if (fbWebhookActive) "Resubscribe Webhook" else "Register Webhook", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        OutlinedTextField(
                                            value = fbWebhookVerifyToken,
                                            onValueChange = { fbWebhookVerifyToken = it },
                                            label = { Text("Webhook Verify Token (for handshake verification)", fontSize = 9.sp) },
                                            modifier = Modifier.fillMaxWidth().height(46.dp).testTag("fb_verify_token_input"),
                                            textStyle = MaterialTheme.typography.bodySmall.copy(color = CosmicTextPrimary, fontSize = 11.sp),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CosmicAccent, unfocusedBorderColor = CosmicSurfaceVariant)
                                        )
                                    }

                                    // Meta Webhook Simulator (Only show when Webhook is Active)
                                    if (fbWebhookActive) {
                                        HorizontalDivider(color = CosmicSurfaceVariant, thickness = 0.5.dp)
                                        
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = CosmicBackground),
                                            border = BorderStroke(0.5.dp, CosmicSurfaceVariant)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically, 
                                                    horizontalArrangement = Arrangement.SpaceBetween, 
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("Meta Webhook Callback Simulator", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = CosmicSecondary)
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("RECEIVER ACTIVE", color = Color(0xFF10B981), fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                Text("Deliver simulated webhook JSON payload representing an active lead generation event directly into your database ingestion engine.", fontSize = 8.5.sp, color = CosmicTextSecondary)

                                                var simLeadName by remember { mutableStateOf("") }
                                                var simLeadPhone by remember { mutableStateOf("") }
                                                var simLeadEmail by remember { mutableStateOf("") }
                                                var simLeadCompany by remember { mutableStateOf("") }
                                                var simLeadAdName by remember { mutableStateOf("Facebook Ads - Enterprise CRM") }

                                                // Presets
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Button(
                                                        onClick = {
                                                            simLeadName = "Sarah Connor"
                                                            simLeadPhone = "+1 555 901 2041"
                                                            simLeadEmail = "sconnor@cyberdyne.io"
                                                            simLeadCompany = "Cyberdyne Systems"
                                                            simLeadAdName = "Facebook Lead Capture Ads"
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = CosmicSurfaceVariant),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier.height(24.dp)
                                                    ) {
                                                        Text("Autofill Tech Lead", fontSize = 8.5.sp, color = CosmicTextPrimary)
                                                    }
                                                    Button(
                                                        onClick = {
                                                            simLeadName = "Peter Parker"
                                                            simLeadPhone = "+1 212 555 0199"
                                                            simLeadEmail = "peter.parker@dailybugle.com"
                                                            simLeadCompany = "Daily Bugle Photography"
                                                            simLeadAdName = "Facebook Lead Capture Ads"
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = CosmicSurfaceVariant),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier.height(24.dp)
                                                    ) {
                                                        Text("Autofill Media Lead", fontSize = 8.5.sp, color = CosmicTextPrimary)
                                                    }
                                                }

                                                OutlinedTextField(
                                                    value = simLeadName,
                                                    onValueChange = { simLeadName = it },
                                                    label = { Text("Lead Full Name", fontSize = 8.5.sp) },
                                                    modifier = Modifier.fillMaxWidth().height(42.dp).testTag("sim_lead_name_input"),
                                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = CosmicTextPrimary, fontSize = 10.sp),
                                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CosmicAccent, unfocusedBorderColor = CosmicSurfaceVariant)
                                                )

                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                                    OutlinedTextField(
                                                        value = simLeadPhone,
                                                        onValueChange = { simLeadPhone = it },
                                                        label = { Text("Phone Number", fontSize = 8.5.sp) },
                                                        modifier = Modifier.weight(1f).height(42.dp).testTag("sim_lead_phone_input"),
                                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = CosmicTextPrimary, fontSize = 10.sp),
                                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CosmicAccent, unfocusedBorderColor = CosmicSurfaceVariant)
                                                    )
                                                    OutlinedTextField(
                                                        value = simLeadEmail,
                                                        onValueChange = { simLeadEmail = it },
                                                        label = { Text("Email Address", fontSize = 8.5.sp) },
                                                        modifier = Modifier.weight(1.2f).height(42.dp).testTag("sim_lead_email_input"),
                                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = CosmicTextPrimary, fontSize = 10.sp),
                                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CosmicAccent, unfocusedBorderColor = CosmicSurfaceVariant)
                                                    )
                                                }

                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                                    OutlinedTextField(
                                                        value = simLeadCompany,
                                                        onValueChange = { simLeadCompany = it },
                                                        label = { Text("Company Name", fontSize = 8.5.sp) },
                                                        modifier = Modifier.weight(1.3f).height(42.dp).testTag("sim_lead_company_input"),
                                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = CosmicTextPrimary, fontSize = 10.sp),
                                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CosmicAccent, unfocusedBorderColor = CosmicSurfaceVariant)
                                                    )
                                                    OutlinedTextField(
                                                        value = simLeadAdName,
                                                        onValueChange = { simLeadAdName = it },
                                                        label = { Text("Form Campaign", fontSize = 8.5.sp) },
                                                        modifier = Modifier.weight(1f).height(42.dp).testTag("sim_lead_ad_input"),
                                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = CosmicTextPrimary, fontSize = 10.sp),
                                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CosmicAccent, unfocusedBorderColor = CosmicSurfaceVariant)
                                                    )
                                                }

                                                Button(
                                                    onClick = {
                                                        if (simLeadName.isBlank() || simLeadPhone.isBlank()) {
                                                            android.widget.Toast.makeText(context, "Page Webhook error: Name and Phone are required", android.widget.Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            viewModel.triggerFacebookWebhookEvent(
                                                                adName = simLeadAdName,
                                                                formName = fbFormId.ifEmpty { "General CRM Lead Capture Form" },
                                                                leadName = simLeadName,
                                                                leadPhone = simLeadPhone,
                                                                leadEmail = simLeadEmail,
                                                                company = simLeadCompany
                                                            )
                                                            android.widget.Toast.makeText(context, "Webhook Delivery success! Ingested & assigned.", android.widget.Toast.LENGTH_LONG).show()
                                                            simLeadName = ""
                                                            simLeadPhone = ""
                                                            simLeadEmail = ""
                                                            simLeadCompany = ""
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.fillMaxWidth().height(36.dp).testTag("btn_trigger_webhook_event")
                                                ) {
                                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Fire Inbound Webhook Event", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Gemini AI Sentiment Analysis Configuration
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("gemini_api_card"),
                                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                                border = BorderStroke(1.dp, CosmicAccent.copy(alpha = 0.25f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(CosmicAccent.copy(alpha = 0.15f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Settings, contentDescription = null, tint = CosmicAccent, modifier = Modifier.size(12.dp))
                                        }
                                        Text("Gemini AI Configuration", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                                    }

                                    Text(
                                        "Configure your Gemini API key to power real-time sentiment analysis and call transcript summaries. If empty, the system automatically falls back to pre-configured keys or simulation mode.",
                                        fontSize = 10.sp,
                                        color = CosmicTextSecondary
                                    )

                                    OutlinedTextField(
                                        value = geminiApiKey,
                                        onValueChange = { geminiApiKey = it },
                                        label = { Text("Gemini API Key", fontSize = 10.sp) },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("gemini_key_input"),
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = CosmicTextPrimary, fontSize = 11.sp),
                                        visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                                Icon(
                                                    imageVector = if (showGeminiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = "Toggle Visibility",
                                                    tint = CosmicTextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CosmicAccent, unfocusedBorderColor = CosmicSurfaceVariant)
                                    )
                                }
                            }
                        }

                        // Native Telephony & SIM-Card Click-to-Call configuration card
                        item {
                            val activeCallingMode by viewModel.callingMode.collectAsStateWithLifecycle()
                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("native_telephony_card"),
                                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                                border = BorderStroke(1.dp, CosmicAccent.copy(alpha = 0.25f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(CosmicAccent.copy(alpha = 0.15f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Phone, contentDescription = null, tint = CosmicAccent, modifier = Modifier.size(12.dp))
                                        }
                                        Text("Native Telephony & Click-to-Call Linker", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                                    }

                                    Text(
                                        "Configure the native click-to-call link behaviour to route dial outs directly through the device's physical SIM card carrier line or native dialer app.",
                                        fontSize = 10.sp,
                                        color = CosmicTextSecondary
                                    )

                                    // Status & Permission Indicators
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (hasCallPermission) CosmicSecondary.copy(alpha = 0.15f)
                                                    else Color(0xFFEAB308).copy(alpha = 0.15f)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = if (hasCallPermission) "SIM DIALER: Active" else "SIM DIALER: Restricted",
                                                color = if (hasCallPermission) CosmicSecondary else Color(0xFFEAB308),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        if (!hasCallPermission) {
                                            Button(
                                                onClick = {
                                                    callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = CosmicAccent),
                                                shape = RoundedCornerShape(4.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(22.dp).testTag("request_telephony_perm_btn")
                                            ) {
                                                Text("Grant CALL_PHONE Permission", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    // Mode selectors
                                    Text("Outbound Click-to-Call Mode:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val modes = listOf(
                                            "Direct SIM Call (ACTION_CALL)",
                                            "Native Prefill Dialer (ACTION_DIAL)",
                                            "Pure In-App Simulation"
                                        )
                                        modes.forEach { mode ->
                                            val isSelected = activeCallingMode == mode
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSelected) CosmicAccent.copy(alpha = 0.10f) else Color.Transparent)
                                                    .clickable { viewModel.setCallingMode(mode) }
                                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = { viewModel.setCallingMode(mode) },
                                                    colors = RadioButtonDefaults.colors(
                                                        selectedColor = CosmicAccent,
                                                        unselectedColor = CosmicTextSecondary
                                                    ),
                                                    modifier = Modifier.size(16.dp).testTag("telephony_mode_${mode.replace(" ", "_").lowercase()}")
                                                )
                                                Column {
                                                    Text(
                                                        text = mode,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) CosmicTextPrimary else CosmicTextSecondary
                                                    )
                                                    val desc = when (mode) {
                                                        "Direct SIM Call (ACTION_CALL)" -> "Triggers instant outbound carrier calls via physical SIM card (Requires permission)"
                                                        "Native Prefill Dialer (ACTION_DIAL)" -> "Launches prefilled native system dialer. User clicks to dial manually"
                                                        else -> "Runs fully virtual ongoing-call screen and recordings without external phone activity"
                                                    }
                                                    Text(desc, fontSize = 8.5.sp, color = CosmicTextSecondary.copy(alpha = 0.8f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Facebook Diagnostic Console
                        item {
                            FacebookDiagnosticConsole(viewModel = viewModel)
                        }

                        // Live Webhook Endpoint expose card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CosmicBackground),
                                border = BorderStroke(1.dp, CosmicSurfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Active Live Webhook Endpoint URL:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CosmicSecondary)
                                    Text(
                                        "https://api.crmtele.com/v1/webhooks/meta?id=52fdcb9e-6a01-4fa6-9cc3-bedf2c1c01b2",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = CosmicTextPrimary
                                    )
                                    Text("Configure the Facebook App Webhooks product to subscribe to leadgen topic.", fontSize = 9.sp, color = CosmicTextSecondary)
                                }
                            }
                        }

                        // Connection actions
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                var testingApi by remember { mutableStateOf(false) }
                                var testSuccessMsg by remember { mutableStateOf("") }

                                Button(
                                    onClick = {
                                        testingApi = true
                                        testSuccessMsg = ""
                                        // Simulate async calling REST API
                                        scope.launch {
                                            kotlinx.coroutines.delay(1500)
                                            testingApi = false
                                            testSuccessMsg = "REST Webhook Verification: Success! Graph APIs returned Status 200 OK."
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(38.dp).testTag("test_connector_btn")
                                ) {
                                    if (testingApi) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                                    } else {
                                        Text("Test Connection", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Button(
                                    onClick = {
                                        viewModel.saveAdminSettings(
                                            AdminSettingsEntity(
                                                id = 1,
                                                fbPageAccessToken = fbPageAccessToken,
                                                fbFormId = fbFormId,
                                                fbAppId = fbAppId,
                                                fbAppSecret = fbAppSecret,
                                                whatsappBusinessPhoneNumberId = whatsappBusinessId,
                                                whatsappAccessToken = whatsappToken,
                                                fbPageId = fbPageId,
                                                fbPageName = fbPageName,
                                                fbWebhookActive = fbWebhookActive,
                                                fbWebhookVerifyToken = fbWebhookVerifyToken,
                                                geminiApiKey = geminiApiKey
                                            )
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1.2f).height(38.dp).testTag("save_connector_btn")
                                ) {
                                    Text("Save Configurations", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                "logs" -> {
                    // Sync Hub & Logs Tab delegation
                    CRMHubTab(viewModel, onTabNavigate = onTabNavigate)
                }
            }
        }
    }

    if (showAddTeammateDialog) {
        AddTeammateDialog(
            viewModel = viewModel,
            onDismiss = { showAddTeammateDialog = false }
        )
    }

    if (showFbAuthDialog) {
        FacebookAuthDialog(
            viewModel = viewModel,
            onDismiss = { showFbAuthDialog = false },
            onPageSelected = { page ->
                fbPageId = page.id
                fbPageName = page.name
                fbPageAccessToken = page.accessToken
                showFbAuthDialog = false
            }
        )
    }
}

@Composable
fun SessionItemRow(
    user: String,
    device: String,
    ip: String,
    duration: String,
    isRevocable: Boolean,
    onRevoke: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(user, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
            Text("$device • IP: $ip", fontSize = 9.sp, color = CosmicTextSecondary)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(duration, fontSize = 9.sp, color = CosmicSecondary, fontWeight = FontWeight.Bold)
            if (isRevocable) {
                Button(
                    onClick = onRevoke,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.15f), contentColor = Color(0xFFEF4444)),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(24.dp).testTag("revoke_session_${user.replace(" ", "_").lowercase()}")
                ) {
                    Text("Terminate", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- ADD TEAMMATE / AGENT DIRECTORY DIALOG ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTeammateDialog(
    viewModel: CRMViewModel,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var accessLevel by remember { mutableStateOf("Read-Only") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(10.dp).testTag("add_teammate_dialog_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            border = BorderStroke(1.dp, CosmicAccent.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Grant CRM Teammate Access", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                Text("Enter credentials below to provision account and active sessions.", fontSize = 10.5.sp, color = CosmicTextSecondary)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Teammate Full Name", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("agent_name_input"),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = CosmicTextPrimary, fontSize = 11.sp)
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Corporate Email Address", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("agent_email_input"),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = CosmicTextPrimary, fontSize = 11.sp)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Mobile Phone Number", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("agent_phone_input"),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = CosmicTextPrimary, fontSize = 11.sp)
                )

                // Access Level Segmented Selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Teammate CRM Privileges:", fontSize = 10.sp, color = CosmicTextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Full Edit", "Read-Only").forEach { level ->
                            val selected = accessLevel == level
                            Button(
                                onClick = { accessLevel = level },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) CosmicAccent else CosmicBackground,
                                    contentColor = CosmicTextPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(34.dp).testTag("level_select_${level.replace(" ", "_").lowercase()}"),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text(level, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = CosmicTextSecondary, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (name.isNotEmpty() && email.isNotEmpty()) {
                                viewModel.addTelecaller(name, email, phone, accessLevel)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("submit_agent_btn")
                    ) {
                        Text("Grant Access", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun NextCallPromptDialog(
    lead: com.example.database.LeadEntity,
    onDismiss: () -> Unit,
    onDial: () -> Unit,
    onSkip: () -> Unit,
    onStopQueue: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(CosmicAccent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneInTalk,
                        contentDescription = "Prompt Icon",
                        tint = CosmicAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = "Next Call Attempt",
                        color = CosmicTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Automated Dialer Queue",
                        color = CosmicTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Current lead call status updated! Ready to make the next call attempt in the automated queue:",
                    color = CosmicTextPrimary,
                    fontSize = 12.sp
                )
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = lead.name,
                            color = CosmicTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${lead.company} • ${lead.phone}",
                            color = CosmicTextSecondary,
                            fontSize = 11.sp
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CosmicSecondary.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = lead.status,
                                    color = CosmicSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (lead.source.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(CosmicAccent.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = lead.source,
                                        color = CosmicAccent,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                
                Text(
                    text = "Press 'Dial Now' to start calling. You can also skip this lead or temporarily pause/stop the dialer queue.",
                    color = CosmicTextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDial,
                colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("prompt_dial_now_btn")
            ) {
                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Dial Now", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onSkip,
                    border = BorderStroke(1.dp, CosmicTextSecondary.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CosmicTextPrimary),
                    modifier = Modifier.testTag("prompt_skip_lead_btn")
                ) {
                    Text("Skip", fontSize = 11.sp)
                }
                
                Button(
                    onClick = onStopQueue,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("prompt_stop_queue_btn")
                ) {
                    Text("Stop Queue", color = Color.White, fontSize = 11.sp)
                }
            }
        },
        containerColor = CosmicSurface
    )
}

@Composable
fun CallIntegrationPromptDialog(
    lead: com.example.database.LeadEntity,
    onDismiss: () -> Unit,
    onSelectMode: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(CosmicAccent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "SIM Card Calling",
                        tint = CosmicAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = "Call Integration Mode",
                        color = CosmicTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Select outbound calling pathway",
                        color = CosmicTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "You are about to dial ${lead.name} (${lead.phone}) for ${lead.company.ifEmpty { "Individual" }}.",
                    color = CosmicTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(6.dp).background(CosmicSecondary, CircleShape))
                            Text("Direct SIM Call (Carrier Mode)", color = CosmicTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Triggers your device's physical cellular network SIM card. Places a real phone call while launching the background AI recording and conversation logging engine.",
                            color = CosmicTextSecondary,
                            fontSize = 9.5.sp,
                            lineHeight = 13.sp
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CosmicAccent.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(6.dp).background(CosmicAccent, CircleShape))
                            Text("Pure In-App Simulation (Mock Mode)", color = CosmicTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Launches a virtual mock call screen fully inside the CRM application. Used for offline testing, review scripts, and local demonstration of Gemini CRM pipelines.",
                            color = CosmicTextSecondary,
                            fontSize = 9.5.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelectMode("Direct SIM Call (ACTION_CALL)") },
                colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Direct SIM Call", color = CosmicTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { onSelectMode("Pure In-App Simulation") },
                border = BorderStroke(1.dp, CosmicAccent),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("In-App Simulation", color = CosmicAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = CosmicSurface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacebookAuthDialog(
    viewModel: CRMViewModel,
    onDismiss: () -> Unit,
    onPageSelected: (com.example.ui.FacebookPageInfo) -> Unit
) {
    var tokenInput by remember { mutableStateOf("") }
    
    val loading by viewModel.fbPagesLoading.collectAsStateWithLifecycle()
    val error by viewModel.fbPagesError.collectAsStateWithLifecycle()
    val fetchedPages by viewModel.fbFetchedPages.collectAsStateWithLifecycle()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(10.dp).testTag("fb_auth_dialog_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Facebook Graph Page Authenticator", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CosmicPrimary)
                Text("Provide a Page Graph Access Token or use mock credential simulation to retrieve and connect Business Pages.", fontSize = 10.5.sp, color = CosmicTextSecondary)

                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("Graph API Access Token", fontSize = 11.sp) },
                    placeholder = { Text("Paste User/Page token, or leave empty for mock", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("fb_token_verify_input"),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = CosmicTextPrimary, fontSize = 11.sp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CosmicAccent, unfocusedBorderColor = CosmicSurfaceVariant)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val actualToken = tokenInput.ifBlank { "EAAGzB4ZCSD3IBAOz_simulated_token_12345" }
                            viewModel.fetchFacebookPagesFromApi(actualToken)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.3f).height(38.dp).testTag("fb_fetch_pages_btn")
                    ) {
                        if (loading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Text("Fetch Pages via API", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                }

                if (error != null) {
                    Text(
                        text = error ?: "",
                        color = Color(0xFFF59E0B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                }

                if (fetchedPages.isNotEmpty()) {
                    HorizontalDivider(color = CosmicSurfaceVariant, thickness = 0.5.dp)
                    Text("Select a Page to Authenticate & Link:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(fetchedPages) { page ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPageSelected(page) }
                                    .testTag("page_item_${page.id}"),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = CosmicBackground),
                                border = BorderStroke(0.5.dp, CosmicSurfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(page.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CosmicTextPrimary)
                                        Text("ID: ${page.id} • ${page.category}", fontSize = 8.5.sp, color = CosmicTextSecondary)
                                    }
                                    Button(
                                        onClick = { onPageSelected(page) },
                                        colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text("Link Page", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = CosmicTextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

sealed interface LeadInteraction {
    val timestamp: Long

    data class Call(val record: com.example.database.CallRecordEntity) : LeadInteraction {
        override val timestamp: Long get() = record.timestamp
    }

    data class WhatsApp(val message: com.example.database.WhatsAppMessageEntity) : LeadInteraction {
        override val timestamp: Long get() = message.timestamp
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogInteractionDialog(
    lead: com.example.database.LeadEntity,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var selectedType by remember { mutableStateOf("Call Note") }
    var notesText by remember { mutableStateOf("") }
    var expandedDropdown by remember { mutableStateOf(false) }
    val types = listOf("Call Note", "WhatsApp Outreach", "Email Sent", "Meeting Note", "General Note")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(CosmicPrimary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Log Interaction",
                        tint = CosmicPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = "Log Manual Interaction",
                        color = CosmicTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Add custom interaction details for ${lead.name}",
                        color = CosmicTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Text("Interaction Type", color = CosmicTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expandedDropdown = true },
                            modifier = Modifier.fillMaxWidth().testTag("interaction_type_dropdown"),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CosmicTextPrimary)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(selectedType, fontSize = 12.sp)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }

                        DropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false },
                            modifier = Modifier.background(CosmicSurfaceVariant)
                        ) {
                            types.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type, color = CosmicTextPrimary, fontSize = 12.sp) },
                                    onClick = {
                                        selectedType = type
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                Column {
                    Text("Interaction Notes & Summary", color = CosmicTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        placeholder = { Text("Write notes, follow up tasks, or summary...", fontSize = 11.sp, color = CosmicTextSecondary) },
                        modifier = Modifier.fillMaxWidth().height(100.dp).testTag("interaction_notes_input"),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CosmicPrimary,
                            unfocusedBorderColor = CosmicTextSecondary.copy(alpha = 0.4f),
                            focusedContainerColor = CosmicSurface,
                            unfocusedContainerColor = CosmicSurface
                        ),
                        maxLines = 4,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (notesText.isNotBlank()) {
                        onConfirm(selectedType, notesText)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                shape = RoundedCornerShape(8.dp),
                enabled = notesText.isNotBlank(),
                modifier = Modifier.testTag("save_interaction_btn")
            ) {
                Text("Save Log", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CosmicTextSecondary, fontSize = 11.sp)
            }
        },
        containerColor = CosmicSurface,
        tonalElevation = 6.dp
    )
}

@Composable
fun LeadsDataTable(
    leads: List<com.example.database.LeadEntity>,
    selectedLead: com.example.database.LeadEntity?,
    onLeadClick: (com.example.database.LeadEntity) -> Unit,
    onDeleteLead: (Int) -> Unit,
    onCallClick: (com.example.database.LeadEntity) -> Unit,
    onWhatsAppClick: (String) -> Unit
) {
    var sortBy by remember { mutableStateOf("name") }
    var sortAscending by remember { mutableStateOf(true) }
    var currentPage by remember { mutableStateOf(0) }
    var rowsPerPage by remember { mutableStateOf(5) }

    // Sort logic
    val sortedLeads = remember(leads, sortBy, sortAscending) {
        val comparator = when (sortBy) {
            "name" -> compareBy<com.example.database.LeadEntity> { it.name.lowercase() }
            "phone" -> compareBy<com.example.database.LeadEntity> { it.phone }
            "status" -> compareBy<com.example.database.LeadEntity> { it.status.lowercase() }
            "sentiment" -> compareBy<com.example.database.LeadEntity> {
                when (it.sentiment) {
                    "Positive" -> 3
                    "Neutral" -> 2
                    "Negative" -> 1
                    else -> 0
                }
            }
            else -> compareBy<com.example.database.LeadEntity> { it.id }
        }
        if (sortAscending) leads.sortedWith(comparator) else leads.sortedWith(comparator).reversed()
    }

    // Pagination logic
    val totalLeads = sortedLeads.size
    val maxPage = maxOf(0, (totalLeads - 1) / rowsPerPage)
    val safePage = minOf(currentPage, maxPage)

    val paginatedLeads = remember(sortedLeads, safePage, rowsPerPage) {
        val startIndex = safePage * rowsPerPage
        if (startIndex < sortedLeads.size) {
            sortedLeads.subList(startIndex, minOf(startIndex + rowsPerPage, sortedLeads.size))
        } else {
            emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leads_data_table_container")
    ) {
        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CosmicSurfaceVariant.copy(alpha = 0.4f))
                .padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableHeaderCell(
                text = "Name",
                weight = 1.2f,
                isSorted = sortBy == "name",
                sortAscending = sortAscending,
                onClick = {
                    if (sortBy == "name") {
                        sortAscending = !sortAscending
                    } else {
                        sortBy = "name"
                        sortAscending = true
                    }
                    currentPage = 0
                },
                modifier = Modifier.testTag("sort_header_name")
            )
            TableHeaderCell(
                text = "Phone",
                weight = 1.0f,
                isSorted = sortBy == "phone",
                sortAscending = sortAscending,
                onClick = {
                    if (sortBy == "phone") {
                        sortAscending = !sortAscending
                    } else {
                        sortBy = "phone"
                        sortAscending = true
                    }
                    currentPage = 0
                },
                modifier = Modifier.testTag("sort_header_phone")
            )
            TableHeaderCell(
                text = "Status",
                weight = 1.0f,
                isSorted = sortBy == "status",
                sortAscending = sortAscending,
                onClick = {
                    if (sortBy == "status") {
                        sortAscending = !sortAscending
                    } else {
                        sortBy = "status"
                        sortAscending = true
                    }
                    currentPage = 0
                },
                modifier = Modifier.testTag("sort_header_status")
            )
            TableHeaderCell(
                text = "Sentiment",
                weight = 1.2f,
                isSorted = sortBy == "sentiment",
                sortAscending = sortAscending,
                onClick = {
                    if (sortBy == "sentiment") {
                        sortAscending = !sortAscending
                    } else {
                        sortBy = "sentiment"
                        sortAscending = true
                    }
                    currentPage = 0
                },
                modifier = Modifier.testTag("sort_header_sentiment")
            )
            // Actions header
            Text(
                text = "Actions",
                color = CosmicTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(0.8f),
                textAlign = TextAlign.Center
            )
        }

        HorizontalDivider(color = CosmicSurfaceVariant)

        // Table Body
        if (paginatedLeads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No leads available.",
                    color = CosmicTextSecondary,
                    fontSize = 12.sp
                )
            }
        } else {
            paginatedLeads.forEach { lead ->
                val isSelected = selectedLead?.id == lead.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) CosmicPrimary.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { onLeadClick(lead) }
                        .padding(vertical = 8.dp, horizontal = 8.dp)
                        .testTag("lead_table_row_${lead.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Column 1: Name
                    Column(
                        modifier = Modifier.weight(1.2f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = lead.name,
                            color = CosmicTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (lead.company.isNotEmpty()) {
                            Text(
                                text = lead.company,
                                color = CosmicTextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Column 2: Phone
                    Text(
                        text = lead.phone,
                        color = CosmicTextPrimary,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1.0f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Column 3: Status
                    Box(
                        modifier = Modifier.weight(1.0f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val statusBgColor = when (lead.status.lowercase()) {
                            "new" -> CosmicAccent.copy(alpha = 0.15f)
                            "contacted" -> CosmicSecondary.copy(alpha = 0.15f)
                            "interested" -> Color(0xFF10B981).copy(alpha = 0.15f)
                            "converted" -> Color(0xFF8B5CF6).copy(alpha = 0.15f)
                            else -> CosmicSurfaceVariant
                        }
                        val statusTextColor = when (lead.status.lowercase()) {
                            "new" -> CosmicAccent
                            "contacted" -> CosmicSecondary
                            "interested" -> Color(0xFF10B981)
                            "converted" -> Color(0xFF8B5CF6)
                            else -> CosmicTextSecondary
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusBgColor)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = lead.status,
                                color = statusTextColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Column 4: Sentiment Score
                    Row(
                        modifier = Modifier.weight(1.2f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val (sentimentColor, sentimentLabel, sentimentPercent, sentimentEmoji) = when (lead.sentiment) {
                            "Positive" -> Quadruple(Color(0xFF10B981), "Positive", 0.90f, "😊")
                            "Negative" -> Quadruple(Color(0xFFEF4444), "Negative", 0.15f, "☹️")
                            else -> Quadruple(Color(0xFF9CA3AF), "Neutral", 0.50f, "😐")
                        }

                        Text(
                            text = sentimentEmoji,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(end = 2.dp)
                        )

                        Column(
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = sentimentLabel,
                                    color = sentimentColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${(sentimentPercent * 100).toInt()}%",
                                    color = CosmicTextSecondary,
                                    fontSize = 9.sp
                                )
                            }
                            LinearProgressIndicator(
                                progress = { sentimentPercent },
                                modifier = Modifier
                                    .fillMaxWidth(0.95f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = sentimentColor,
                                trackColor = CosmicSurfaceVariant
                            )
                        }
                    }

                    // Column 5: Actions
                    Row(
                        modifier = Modifier.weight(0.8f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onCallClick(lead) },
                            modifier = Modifier.size(24.dp).testTag("table_row_call_${lead.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Call",
                                tint = CosmicPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        IconButton(
                            onClick = { onWhatsAppClick(lead.phone) },
                            modifier = Modifier.size(24.dp).testTag("table_row_whatsapp_${lead.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "WhatsApp",
                                tint = Color(0xFF25D366),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        IconButton(
                            onClick = { onDeleteLead(lead.id) },
                            modifier = Modifier.size(24.dp).testTag("table_row_delete_${lead.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = CosmicSurfaceVariant.copy(alpha = 0.5f))
            }
        }

        // Table Footer (Pagination controls)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rows per page selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Rows:",
                    color = CosmicTextSecondary,
                    fontSize = 11.sp
                )
                listOf(5, 10, 15).forEach { size ->
                    val isSelected = rowsPerPage == size
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) CosmicPrimary.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable {
                                rowsPerPage = size
                                currentPage = 0
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = size.toString(),
                            color = if (isSelected) CosmicPrimary else CosmicTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Pagination navigation
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val startRow = if (totalLeads == 0) 0 else safePage * rowsPerPage + 1
                val endRow = minOf((safePage + 1) * rowsPerPage, totalLeads)
                Text(
                    text = "$startRow-$endRow of $totalLeads",
                    color = CosmicTextSecondary,
                    fontSize = 11.sp
                )

                IconButton(
                    onClick = { if (safePage > 0) currentPage = safePage - 1 },
                    enabled = safePage > 0,
                    modifier = Modifier.size(28.dp).testTag("table_prev_page_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Previous Page",
                        tint = if (safePage > 0) CosmicTextPrimary else CosmicTextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { if (safePage < maxPage) currentPage = safePage + 1 },
                    enabled = safePage < maxPage,
                    modifier = Modifier.size(28.dp).testTag("table_next_page_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Next Page",
                        tint = if (safePage < maxPage) CosmicTextPrimary else CosmicTextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RowScope.TableHeaderCell(
    text: String,
    weight: Float,
    isSorted: Boolean,
    sortAscending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .weight(weight)
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = text,
            color = if (isSorted) CosmicPrimary else CosmicTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        if (isSorted) {
            Icon(
                imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = if (sortAscending) "Sorted Ascending" else "Sorted Descending",
                tint = CosmicPrimary,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun BoxScope.FloatingCallTimer(
    viewModel: CRMViewModel,
    lead: com.example.database.LeadEntity,
    seconds: Int,
    onEndCall: (String) -> Unit,
    onCancelCall: () -> Unit
) {
    var offsetX by remember { mutableStateOf(-16f) }
    var offsetY by remember { mutableStateOf(-80f) }

    var isExpanded by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var liveNotes by remember { mutableStateOf("") }
    var selectedTone by remember { mutableStateOf("Consultative") }
    var showScripts by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Dynamic scripts based on Tone and Status
    val greeting = when (selectedTone) {
        "Value-Driven" -> "Hey ${lead.name}, this is quick outreach from CRM Hub. I hope you're having an active week!"
        "Problem-Solver" -> "Hello ${lead.name}, this is CRM Hub support. I am reaching out to follow up on your integration inquiry."
        else -> "Hello ${lead.name}, this is CRM Hub. Is now a convenient time for a brief call?"
    }

    val openingPoint = when (lead.status) {
        "New" -> "I noticed you registered via ${lead.source} and wanted to personally welcome you. Our system indicated your team is looking to scale outbound capacity."
        "Contacted" -> "Just following up on our previous conversation. I wanted to verify if you had a chance to look at our modern dialer features."
        "Interested" -> "Great to speak with you again! Since you expressed interest, I've prepared a custom walkthrough of our dynamic routing dashboard for ${lead.company}."
        "Converted" -> "Checking in on your onboarding setup. I want to make sure your agents are fully configured and enjoying our CRM features."
        else -> "Reaching back out to see if your calendar has opened up. We've introduced several new automated features that directly address team workflow friction."
    }

    val valuePitch = when (selectedTone) {
        "Value-Driven" -> {
            when (lead.status) {
                "New", "Contacted" -> "With our automated Campaign Queue, telecallers experience a 250% increase in live talk-time by eliminating manual dial gaps. For ${lead.company}, this directly translates to higher conversion velocity."
                "Interested" -> "By locking in this month's custom package, ${lead.company} gets priority integration support and a guaranteed 35% discount on outbound call minutes, optimizing your immediate ROI."
                else -> "Even if the timeline isn't immediate, our dynamic sync and auto-dialer can save your sales reps up to 2 hours of manual lead logging every single day."
            }
        }
        "Problem-Solver" -> {
            when (lead.status) {
                "New", "Contacted" -> "Our core value is architectural. We replace messy manual pipelines with a unified Room database that synchronizes with WhatsApp and Facebook Leads in under 5 seconds. Let's solve the data leakage issue at ${lead.company}."
                "Interested" -> "We support native webhook configuration, advanced call annotation playback, and custom rule-based follow-up reminders. It perfectly fits ${lead.company}'s custom workflow requirements."
                else -> "We've resolved past latency concerns with a brand-new local cache, ensuring your call recording annotations are fully synced offline-first."
            }
        }
        else -> { // Consultative
            when (lead.status) {
                "New", "Contacted" -> "We focus on helping your team have warmer, more meaningful conversations. We provide real-time caller dashboards and sentiment tracking so agents can build instant trust."
                "Interested" -> "Our primary goal is supporting ${lead.company}'s growth. We can customize the CRM layout to match your current sales playbooks so your team feels right at home."
                else -> "We want to be a helpful resource. We offer unlimited onboarding support for your representatives to guarantee a smooth transition."
            }
        }
    }

    val dragModifier = Modifier
        .align(Alignment.BottomEnd)
        .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
        .pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                offsetX += dragAmount.x
                offsetY += dragAmount.y
            }
        }

    if (!isExpanded) {
        // Minimized floating pill UI
        Card(
            modifier = dragModifier
                .width(190.dp)
                .height(60.dp)
                .padding(4.dp)
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(CosmicSecondary, CosmicPrimary)
                    ),
                    shape = RoundedCornerShape(30.dp)
                )
                .testTag("floating_call_timer_pill"),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface.copy(alpha = 0.95f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            shape = RoundedCornerShape(30.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Pulsating indicator
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .scale(pulseScale)
                        .background(CosmicSecondary.copy(alpha = pulseAlpha), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { isExpanded = true },
                        modifier = Modifier.size(28.dp).testTag("floating_timer_expand_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Expand Call Overlay",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = lead.name.take(10) + if (lead.name.length > 10) ".." else "",
                        color = CosmicTextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = formatTime(seconds),
                        color = CosmicSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Quick Red stop button
                IconButton(
                    onClick = { isExpanded = true }, // Go to expanded to allow logging notes
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Red, CircleShape)
                        .testTag("floating_timer_pill_end_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Open Logging Screen",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    } else {
        // Expanded floating card UI
        Card(
            modifier = dragModifier
                .width(320.dp)
                .wrapContentHeight()
                .padding(8.dp)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(CosmicPrimary, CosmicAccent)
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .testTag("floating_call_timer_card"),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface.copy(alpha = 0.98f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Header: Drag handle, minimize, status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Drag indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(30.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(CosmicTextSecondary.copy(alpha = 0.4f))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Outbound",
                            color = CosmicSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    // Minimize button
                    IconButton(
                        onClick = { isExpanded = false },
                        modifier = Modifier.size(24.dp).testTag("floating_timer_minimize_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Minimize to Pill",
                            tint = CosmicTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Lead Name and Info
                Text(
                    text = lead.name,
                    color = CosmicTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lead.phone,
                        color = CosmicTextSecondary,
                        fontSize = 11.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CosmicAccent.copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = lead.status,
                            color = CosmicAccent,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Timer & Speaking simulation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CosmicBackground)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "TALK TIME",
                            color = CosmicTextSecondary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatTime(seconds),
                            color = CosmicTextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("floating_timer_clock")
                        )
                    }

                    // Pulse Wave visualizer
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(0.4f, 1.2f, 0.7f, 1.5f, 0.5f, 1.0f).forEachIndexed { i, factor ->
                            val heightFactor = if (seconds % 2 == 0) factor else (2f - factor)
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height((15 * heightFactor).dp)
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(if (seconds % 2 == 0) CosmicSecondary else CosmicPrimary)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // In-Call Quick Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Mute Button
                    Button(
                        onClick = { isMuted = !isMuted },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMuted) Color(0xFFEF4444).copy(alpha = 0.2f) else CosmicSurfaceVariant
                        ),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .testTag("floating_timer_mute_btn")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.Close else Icons.Default.Phone,
                            contentDescription = "Mute",
                            tint = if (isMuted) Color.Red else CosmicTextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isMuted) "Muted" else "Mute",
                            color = CosmicTextPrimary,
                            fontSize = 10.sp
                        )
                    }

                    // Speaker Button
                    Button(
                        onClick = { isSpeakerOn = !isSpeakerOn },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSpeakerOn) CosmicPrimary.copy(alpha = 0.2f) else CosmicSurfaceVariant
                        ),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .testTag("floating_timer_speaker_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Speaker",
                            tint = if (isSpeakerOn) CosmicPrimary else CosmicTextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isSpeakerOn) "Speaker On" else "Speaker",
                            color = CosmicTextPrimary,
                            fontSize = 10.sp
                        )
                    }

                    // Script Prompts Toggle
                    Button(
                        onClick = { showScripts = !showScripts },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showScripts) CosmicAccent.copy(alpha = 0.2f) else CosmicSurfaceVariant
                        ),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .testTag("floating_timer_scripts_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Script",
                            tint = if (showScripts) CosmicAccent else CosmicTextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Scripts",
                            color = CosmicTextPrimary,
                            fontSize = 10.sp
                        )
                    }
                }

                // Scrollable scripts if toggled
                if (showScripts) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CosmicBackground),
                        border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Tone Selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("Consultative", "Value-Driven", "Problem-Solver").forEach { tone ->
                                    val isToneSelected = selectedTone == tone
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(
                                                color = if (isToneSelected) CosmicPrimary else CosmicSurface,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .clickable { selectedTone = tone }
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = tone,
                                            color = if (isToneSelected) Color.White else CosmicTextSecondary,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = CosmicSurfaceVariant, thickness = 0.5.dp)

                            // Scripts text
                            Text(
                                text = "GREETING:",
                                color = CosmicSecondary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = greeting,
                                color = CosmicTextPrimary,
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "OPENING STATEMENT:",
                                color = CosmicSecondary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = openingPoint,
                                color = CosmicTextPrimary,
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "VALUE PITCH:",
                                color = CosmicSecondary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = valuePitch,
                                color = CosmicTextPrimary,
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live Notes & Annotation input right on the card!
                OutlinedTextField(
                    value = liveNotes,
                    onValueChange = { liveNotes = it },
                    placeholder = { Text("Take active notes / annotations...", color = CosmicTextSecondary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .testTag("floating_timer_notes_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CosmicTextPrimary,
                        unfocusedTextColor = CosmicTextPrimary,
                        focusedContainerColor = CosmicBackground,
                        unfocusedContainerColor = CosmicBackground,
                        focusedBorderColor = CosmicSecondary,
                        unfocusedBorderColor = CosmicSurface
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick tags selector
                Text(
                    text = "QUICK LABELS (APPENDS TO NOTES)",
                    color = CosmicTextSecondary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val quickTags = listOf(
                        "Interested 👍",
                        "Busy / Callback 🕒",
                        "Not Interested 👎",
                        "No Answer 📱",
                        "Demo Scheduled 🗓️",
                        "Wrong Number ❌"
                    )
                    quickTags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CosmicSurfaceVariant)
                                .clickable {
                                    liveNotes = if (liveNotes.isEmpty()) tag else "$liveNotes | $tag"
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("quick_tag_$tag")
                        ) {
                            Text(
                                text = tag,
                                color = CosmicTextPrimary,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Save or Cancel Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onCancelCall,
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicSurfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("floating_timer_cancel_btn")
                    ) {
                        Text("Cancel", color = CosmicTextPrimary, fontSize = 11.sp)
                    }

                    Button(
                        onClick = { onEndCall(liveNotes) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("floating_timer_end_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Stop Timer",
                            modifier = Modifier.size(12.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Stop & Log Call",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FacebookDiagnosticConsole(viewModel: CRMViewModel) {
    val logs by viewModel.fbDiagnosticLogs.collectAsStateWithLifecycle()
    var expandedLogId by remember { mutableStateOf<Int?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("fb_diagnostic_console_card"),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color(0xFFEF4444).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "Meta Graph API Diagnostic Console",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CosmicTextPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { viewModel.verifyFacebookTokenHealth() },
                        modifier = Modifier.size(24.dp).testTag("refresh_diagnostics_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Health Check",
                            tint = CosmicSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearFacebookDiagnosticLogs() },
                        modifier = Modifier.size(24.dp).testTag("clear_diagnostics_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Console",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Text(
                text = "Logs HTTP status codes, request endpoints, and raw response JSON from local and remote API connections in real-time.",
                fontSize = 9.sp,
                color = CosmicTextSecondary
            )

            // Diagnostic Log simulation presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.logFacebookDiagnostic(
                            endpoint = "https://graph.facebook.com/v18.0/me/accounts",
                            method = "GET",
                            category = "Fetch Pages",
                            httpStatus = 401,
                            responseMessage = """{"error":{"message":"Session has expired or the access token is invalid.","type":"OAuthException","code":190,"error_subcode":463,"fbtrace_id":"Ab8Ysd61xPQ"}}""",
                            isSuccess = false,
                            tokenSample = "EAAGzB4ZCSD..."
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicSurfaceVariant),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f).height(24.dp).testTag("simulate_401_btn")
                ) {
                    Text("Simulate 401 Error", fontSize = 8.sp, color = CosmicTextPrimary)
                }

                Button(
                    onClick = {
                        viewModel.logFacebookDiagnostic(
                            endpoint = "https://graph.facebook.com/v18.0/me/accounts",
                            method = "GET",
                            category = "Fetch Pages",
                            httpStatus = 400,
                            responseMessage = """{"error":{"message":"(#100) The parameter access_token is required.","type":"OAuthException","code":100,"fbtrace_id":"G23bAs77dWe"}}""",
                            isSuccess = false,
                            tokenSample = "None Provided"
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicSurfaceVariant),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f).height(24.dp).testTag("simulate_400_btn")
                ) {
                    Text("Simulate 400 Error", fontSize = 8.sp, color = CosmicTextPrimary)
                }

                Button(
                    onClick = {
                        viewModel.logFacebookDiagnostic(
                            endpoint = "https://graph.facebook.com/v18.0/me/subscribed_apps",
                            method = "POST",
                            category = "Webhook Registration",
                            httpStatus = 503,
                            responseMessage = "Graph API connection timeout. Server failed to establish handshake within 6000ms.",
                            isSuccess = false,
                            tokenSample = "EAAGzB4ZCSD..."
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicSurfaceVariant),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f).height(24.dp).testTag("simulate_503_btn")
                ) {
                    Text("Simulate Timeout", fontSize = 8.sp, color = CosmicTextPrimary)
                }
            }

            HorizontalDivider(color = CosmicSurfaceVariant, thickness = 0.5.dp)

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = CosmicSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                        Text("No errors or diagnostic events logged", fontSize = 9.5.sp, color = CosmicTextSecondary)
                        Text("Trigger connection tests or fetch pages to log events", fontSize = 8.sp, color = CosmicTextSecondary.copy(alpha = 0.7f))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    logs.take(15).forEach { log ->
                        val isExpanded = expandedLogId == log.id
                        DiagnosticLogItem(
                            log = log,
                            isExpanded = isExpanded,
                            onToggleExpand = { expandedLogId = if (isExpanded) null else log.id }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticLogItem(
    log: FacebookDiagnosticLogEntity,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val formatter = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
    val formattedTime = formatter.format(java.util.Date(log.timestamp))

    val statusColor = when {
        log.isSuccess -> Color(0xFF10B981) // Green
        log.httpStatus == 401 -> Color(0xFFF59E0B) // Amber
        log.httpStatus == 400 || log.httpStatus == 403 -> Color(0xFFEF4444) // Bright Red
        else -> Color(0xFFEF4444)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        colors = CardDefaults.cardColors(containerColor = CosmicBackground),
        border = BorderStroke(0.5.dp, if (isExpanded) statusColor.copy(alpha = 0.5f) else CosmicSurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Success/Fail Icon
                    Icon(
                        imageVector = if (log.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(12.dp)
                    )

                    // Method Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (log.method == "POST") CosmicSecondary.copy(alpha = 0.15f) else CosmicPrimary.copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = log.method,
                            color = if (log.method == "POST") CosmicSecondary else CosmicPrimary,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // HTTP Status Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(statusColor.copy(alpha = 0.1f))
                            .padding(horizontal = 4.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = if (log.httpStatus == -1) "ERR" else "HTTP ${log.httpStatus}",
                            color = statusColor,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Category
                    Text(
                        text = log.category,
                        color = CosmicTextPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = formattedTime,
                    color = CosmicTextSecondary,
                    fontSize = 8.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Endpoint path
            Text(
                text = log.endpoint,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace,
                color = CosmicTextSecondary,
                modifier = Modifier.fillMaxWidth()
            )

            // If expanded, show the full response body / message in JSON styling
            if (isExpanded) {
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = CosmicSurfaceVariant, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Token Signature:", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = CosmicTextSecondary)
                        Text(log.tokenSample, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = CosmicTextPrimary)
                    }

                    Text("Server Response Details:", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = CosmicTextSecondary)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(CosmicSurfaceVariant.copy(alpha = 0.3f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = log.responseMessage,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = if (log.isSuccess) CosmicSecondary else Color(0xFFEF4444)
                        )
                    }
                }
            }
        }
    }
}

fun getRemainingTimeText(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = timestamp - now
    val absDiff = kotlin.math.abs(diff)
    val mins = absDiff / 60000
    val hrs = mins / 60
    val days = hrs / 24

    return when {
        diff < 0 -> {
            // Overdue
            when {
                days > 0 -> "Overdue by $days d"
                hrs > 0 -> "Overdue by $hrs h"
                mins > 0 -> "Overdue by $mins m"
                else -> "Overdue just now"
            }
        }
        else -> {
            // Upcoming
            when {
                days > 0 -> "In $days days"
                hrs > 0 -> "In $hrs hours"
                mins > 0 -> "In $mins mins"
                else -> "Starting now"
            }
        }
    }
}

@Composable
fun ScheduledReminderDashboardCard(
    reminder: LeadReminder,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onAction: () -> Unit
) {
    val now = System.currentTimeMillis()
    val isOverdue = now > reminder.timestamp && !reminder.isCompleted
    val remainingText = remember(reminder.timestamp, reminder.isCompleted) {
        if (reminder.isCompleted) "Completed" else getRemainingTimeText(reminder.timestamp)
    }

    val priorityColor = when (reminder.priority) {
        "High" -> Color(0xFFEF4444)
        "Medium" -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    val formattedDateTime = remember(reminder.timestamp) {
        val date = java.util.Date(reminder.timestamp)
        java.text.SimpleDateFormat("MMM dd, yyyy - HH:mm", java.util.Locale.getDefault()).format(date)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scheduled_reminder_card_${reminder.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (reminder.isCompleted) CosmicSurface.copy(alpha = 0.5f) else CosmicSurface
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isOverdue) Color(0xFFEF4444).copy(alpha = 0.6f) else if (reminder.isCompleted) CosmicSurfaceVariant.copy(alpha = 0.5f) else CosmicAccent.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Priority tag
                    Box(
                        modifier = Modifier
                            .background(priorityColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .border(1.dp, priorityColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${reminder.priority} Priority",
                            color = priorityColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Call type icon
                    val icon = when (reminder.type) {
                        "Product Demo" -> Icons.Default.PlayArrow
                        "Contract Review" -> Icons.Default.Assignment
                        "Pricing Discussion" -> Icons.Default.Info
                        else -> Icons.Default.Phone
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = reminder.type,
                        tint = CosmicPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = reminder.type,
                        color = CosmicTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Completion Checkbox
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Checkbox(
                        checked = reminder.isCompleted,
                        onCheckedChange = { onToggle() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = CosmicSecondary,
                            uncheckedColor = CosmicTextSecondary
                        ),
                        modifier = Modifier.size(24.dp).testTag("reminder_checkbox_${reminder.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body: Lead Name & Description
            Text(
                text = "Lead: ${reminder.leadName}",
                color = CosmicTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            if (reminder.customNotes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = reminder.customNotes,
                    color = CosmicTextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = CosmicSurfaceVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Footer row: remaining time & actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = formattedDateTime,
                        color = CosmicTextSecondary,
                        fontSize = 9.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = if (reminder.isCompleted) CosmicSecondary else if (isOverdue) Color(0xFFEF4444) else Color(0xFFF59E0B),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = remainingText,
                            color = if (reminder.isCompleted) CosmicSecondary else if (isOverdue) Color(0xFFEF4444) else Color(0xFFF59E0B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { onDelete() },
                        modifier = Modifier.size(28.dp).testTag("delete_reminder_${reminder.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Reminder",
                            tint = Color.Red.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    if (!reminder.isCompleted) {
                        Button(
                            onClick = { onAction() },
                            colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.height(28.dp).testTag("action_reminder_${reminder.id}"),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Call", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LeadReminderSchedulerView(
    viewModel: CRMViewModel,
    lead: LeadEntity
) {
    val leadReminders by viewModel.leadReminders.collectAsStateWithLifecycle()
    val thisLeadReminders = remember(leadReminders, lead.id) {
        leadReminders.filter { it.leadId == lead.id }.sortedBy { it.timestamp }
    }

    var selectedType by remember { mutableStateOf("Follow-up Call") }
    var selectedPriority by remember { mutableStateOf("Medium") }
    var customNotes by remember { mutableStateOf("") }
    
    // Simple custom date/time presets
    var selectedPresetMinutes by remember { mutableStateOf(15) } // default 15 minutes in future

    val reminderTypes = listOf("Follow-up Call", "Product Demo", "Contract Review", "Pricing Discussion")
    val priorities = listOf("High", "Medium", "Low")
    val presets = listOf(
        15 to "15m",
        60 to "1h",
        240 to "4h",
        1440 to "1d",
        4320 to "3d"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Scheduler form card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CosmicPrimary.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Schedule Follow-up Reminder",
                        color = CosmicTextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // 1. Time Presets
                    Text(
                        text = "Follow-up In:",
                        color = CosmicTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { (mins, label) ->
                            val isSel = selectedPresetMinutes == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (isSel) CosmicPrimary.copy(alpha = 0.2f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSel) CosmicPrimary else CosmicSurfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedPresetMinutes = mins }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) CosmicPrimary else CosmicTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Type Chips
                    Text(
                        text = "Remind For:",
                        color = CosmicTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        reminderTypes.take(2).forEach { type ->
                            val isSel = selectedType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (isSel) CosmicPrimary.copy(alpha = 0.15f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(1.dp, if (isSel) CosmicPrimary else CosmicSurfaceVariant, RoundedCornerShape(8.dp))
                                    .clickable { selectedType = type }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(type, color = if (isSel) CosmicPrimary else CosmicTextSecondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        reminderTypes.drop(2).forEach { type ->
                            val isSel = selectedType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (isSel) CosmicPrimary.copy(alpha = 0.15f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(1.dp, if (isSel) CosmicPrimary else CosmicSurfaceVariant, RoundedCornerShape(8.dp))
                                    .clickable { selectedType = type }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(type, color = if (isSel) CosmicPrimary else CosmicTextSecondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. Priority Chips
                    Text(
                        text = "Priority Level:",
                        color = CosmicTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        priorities.forEach { priority ->
                            val isSel = selectedPriority == priority
                            val pColor = when (priority) {
                                "High" -> Color(0xFFEF4444)
                                "Medium" -> Color(0xFFF59E0B)
                                else -> Color(0xFF10B981)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (isSel) pColor.copy(alpha = 0.15f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(1.dp, if (isSel) pColor else CosmicSurfaceVariant, RoundedCornerShape(8.dp))
                                    .clickable { selectedPriority = priority }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(priority, color = if (isSel) pColor else CosmicTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 4. Custom Notes TextField
                    Text(
                        text = "Agenda / Reminders Notes:",
                        color = CosmicTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customNotes,
                        onValueChange = { customNotes = it },
                        placeholder = { Text("Enter specifics about the call outcome expectation...", color = CosmicTextSecondary.copy(alpha = 0.6f), fontSize = 11.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reminder_notes_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CosmicPrimary,
                            unfocusedBorderColor = CosmicSurfaceVariant,
                            focusedContainerColor = CosmicSurfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = CosmicSurfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = false,
                        maxLines = 3,
                        textStyle = androidx.compose.ui.text.TextStyle(color = CosmicTextPrimary, fontSize = 11.5.sp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Button
                    Button(
                        onClick = {
                            val scheduledTimestamp = System.currentTimeMillis() + selectedPresetMinutes * 60 * 1000L
                            viewModel.addLeadReminder(
                                leadId = lead.id,
                                leadName = lead.name,
                                timestamp = scheduledTimestamp,
                                type = selectedType,
                                priority = selectedPriority,
                                customNotes = customNotes
                            )
                            // Clear states
                            customNotes = ""
                            selectedPresetMinutes = 15
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("schedule_reminder_submit_button")
                    ) {
                        Icon(Icons.Default.Alarm, contentDescription = "Add Reminder", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add to Local State Schedule", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // List of currently scheduled items for this lead
        item {
            Text(
                text = "Scheduled Reminders for ${lead.name} (${thisLeadReminders.size})",
                color = CosmicTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (thisLeadReminders.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .background(CosmicSurface.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No custom reminders scheduled for this lead.", color = CosmicTextSecondary.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
        } else {
            items(thisLeadReminders) { reminder ->
                ScheduledReminderDashboardCard(
                    reminder = reminder,
                    onToggle = { viewModel.toggleLeadReminderCompleted(reminder.id) },
                    onDelete = { viewModel.deleteLeadReminder(reminder.id) },
                    onAction = {
                        // Action callback - triggers direct call to lead
                        viewModel.showCallPromptForLead(lead)
                    }
                )
            }
        }
    }
}

@Composable
fun GlobalToastNotification(
    notification: ToastNotification,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(notification) {
        visible = true
        kotlinx.coroutines.delay(notification.durationMs)
        visible = false
        kotlinx.coroutines.delay(300)
        onDismiss()
    }

    val (accentColor, icon) = when (notification.type) {
        ToastType.SUCCESS -> Pair(CosmicSecondary, Icons.Default.CheckCircle)
        ToastType.ERROR -> Pair(Color(0xFFEF4444), Icons.Default.Error)
        ToastType.WARNING -> Pair(Color(0xFFF59E0B), Icons.Default.Warning)
        ToastType.INFO -> Pair(CosmicPrimary, Icons.Default.Info)
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(durationMillis = 300)
        ) + fadeOut(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
                .testTag("global_toast_card"),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(accentColor.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = notification.type.name,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (notification.type) {
                            ToastType.SUCCESS -> "Success"
                            ToastType.ERROR -> "Error Occurred"
                            ToastType.WARNING -> "Warning"
                            ToastType.INFO -> "Notification"
                        },
                        color = CosmicTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = notification.message,
                        color = CosmicTextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }

                IconButton(
                    onClick = {
                        visible = false
                        onDismiss()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = CosmicTextSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
