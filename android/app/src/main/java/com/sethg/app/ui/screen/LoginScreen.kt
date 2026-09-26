package com.sethg.app.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sethg.app.ui.theme.*
import com.sethg.app.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.flow.collect

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var identifier by remember { mutableStateOf("") }
    var password   by remember { mutableStateOf("") }
    var showPass   by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthViewModel.UiEvent.NavigateToDashboard -> onLoginSuccess()
                is AuthViewModel.UiEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                else -> Unit
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0A1F0A), BackgroundDark)
                    )
                )
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(Modifier.height(60.dp))

                // Logo
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(GreenPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("♻️", fontSize = 40.sp)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Seth G",
                    style = MaterialTheme.typography.headlineLarge,
                    color = GreenPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "लॉगिन करें",
                    style = MaterialTheme.typography.titleMedium,
                    color = SubText
                )

                Spacer(Modifier.height(40.dp))

                // Phone / Email field
                SethGTextField(
                    value         = identifier,
                    onValueChange = {
                        identifier = it
                        viewModel.loginIdentifier = it
                    },
                    label         = "📱 फोन नंबर / Phone or Email",
                    keyboardType  = KeyboardType.Text
                )

                Spacer(Modifier.height(16.dp))

                // Password field
                SethGTextField(
                    value         = password,
                    onValueChange = {
                        password = it
                        viewModel.loginPassword = it
                    },
                    label         = "🔒 पासवर्ड / Password",
                    isPassword    = true,
                    showPassword  = showPass,
                    onTogglePass  = { showPass = !showPass }
                )

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick   = { viewModel.login() },
                    enabled   = !state.isLoading,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape     = RoundedCornerShape(16.dp),
                    colors    = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color    = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "लॉगिन  ·  Login",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                TextButton(onClick = onNavigateToRegister) {
                    Text(
                        "नया खाता बनाएं  ·  Create Account",
                        color = GreenPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
fun SethGTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onTogglePass: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        singleLine    = true,
        modifier      = modifier.fillMaxWidth(),
        shape         = RoundedCornerShape(14.dp),
        visualTransformation = if (isPassword && !showPassword)
            PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon  = if (isPassword) ({
            IconButton(onClick = { onTogglePass?.invoke() }) {
                Icon(
                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = "Toggle password",
                    tint = SubText
                )
            }
        }) else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = GreenPrimary,
            unfocusedBorderColor = SurfaceVariant,
            focusedLabelColor    = GreenPrimary,
            cursorColor          = GreenPrimary,
            focusedTextColor     = OnSurfaceDark,
            unfocusedTextColor   = OnSurfaceDark
        )
    )
}
