package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.CloudApiClient
import com.example.api.LoginResult
import kotlinx.coroutines.launch

@Composable
fun CloudLoginScreen(api: CloudApiClient, onLoggedIn: (LoginResult) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF070B24), Color(0xFF11183D), Color(0xFF1E1640)))
        ).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151B3D)),
            elevation = CardDefaults.cardElevation(defaultElevation = 18.dp)
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF6D5DFB)) {
                    Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.padding(16.dp).size(30.dp))
                }
                Spacer(Modifier.height(18.dp))
                Text("Quick CRM", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Secure cloud access", color = Color(0xFFB7BED8), fontSize = 14.sp)
                Spacer(Modifier.height(28.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; error = null },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF8B7CFF), unfocusedBorderColor = Color(0xFF4A5279),
                        focusedLabelColor = Color(0xFFB8AEFF), unfocusedLabelColor = Color(0xFF9BA3C5)
                    )
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show password", tint = Color(0xFFB7BED8))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF8B7CFF), unfocusedBorderColor = Color(0xFF4A5279),
                        focusedLabelColor = Color(0xFFB8AEFF), unfocusedLabelColor = Color(0xFF9BA3C5)
                    )
                )
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color(0xFFFF8A9A), fontSize = 13.sp)
                }
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) { error = "Enter your CRM email and password."; return@Button }
                        scope.launch {
                            loading = true; error = null
                            try { onLoggedIn(api.login(email, password)) }
                            catch (e: Exception) { error = e.message ?: "Login failed." }
                            finally { loading = false }
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D5DFB))
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text("Sign in", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(14.dp))
                Text("Connected to crm.rscc.in", color = Color(0xFF7E88B3), fontSize = 12.sp)
            }
        }
    }
}
