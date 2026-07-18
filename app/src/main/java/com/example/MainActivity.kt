package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.room.Room
import com.example.api.CloudApiClient
import com.example.database.CRMDatabase
import com.example.service.FacebookLeadPollingService
import com.example.ui.*

class MainActivity : ComponentActivity() {
    private var viewModel: CRMViewModel? = null
    private val apiUrl = "https://crm.rscc.in/api/index.php"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = Room.databaseBuilder(
            applicationContext,
            CRMDatabase::class.java,
            "crm_telecall_db"
        ).fallbackToDestructiveMigration().build()

        // Preserve the original Facebook lead polling/background feature.
        try {
            startService(Intent(this, FacebookLeadPollingService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val prefs = getSharedPreferences("quick_crm_auth", MODE_PRIVATE)
        val api = CloudApiClient(apiUrl, prefs.getString("token", null))

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = CosmicPrimary,
                    background = CosmicBackground,
                    surface = CosmicSurface
                )
            ) {
                var token by remember { mutableStateOf(prefs.getString("token", null)) }
                var sessionChecked by remember { mutableStateOf(false) }

                LaunchedEffect(token) {
                    if (token != null) {
                        api.setToken(token)
                        sessionChecked = try {
                            api.snapshot()
                            true
                        } catch (_: Exception) {
                            prefs.edit().clear().apply()
                            token = null
                            false
                        }
                    } else {
                        sessionChecked = true
                    }
                }

                if (token == null) {
                    CloudLoginScreen(api) { result ->
                        api.setToken(result.token)
                        prefs.edit()
                            .putString("token", result.token)
                            .putString("name", result.name)
                            .putString("role", result.role)
                            .apply()
                        token = result.token
                    }
                } else if (sessionChecked) {
                    val vm = remember(token) {
                        CRMViewModel(database.crmDao(), api).also {
                            viewModel = it
                            handleDeepLink(intent)
                        }
                    }
                    CRMScreen(viewModel = vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        intent?.data?.let { uri ->
            if (uri.scheme == "crmtelefb" && uri.host == "authorize") {
                val token = uri.getQueryParameter("access_token")
                val pageId = uri.getQueryParameter("page_id")
                val pageName = uri.getQueryParameter("page_name")
                if (!token.isNullOrEmpty()) {
                    viewModel?.handleFacebookOAuthCallback(token, pageId, pageName)
                }
            }
        }
    }
}
