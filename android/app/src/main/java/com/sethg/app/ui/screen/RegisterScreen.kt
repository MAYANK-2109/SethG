package com.sethg.app.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sethg.app.ui.theme.*
import com.sethg.app.ui.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var name            by remember { mutableStateOf("") }
    var phone           by remember { mutableStateOf("") }
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPass        by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthViewModel.UiEvent.NavigateToDashboard -> onRegisterSuccess()
                is AuthViewModel.UiEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                else -> Unit
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("नया खाता  ·  Register", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateToLogin) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = OnSurfaceDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = OnSurfaceDark
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(colors = listOf(Color(0xFF0A1F0A), BackgroundDark))
                )
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))

                SethGTextField(
                    value = name,
                    onValueChange = { name = it; viewModel.regName = it },
                    label = "👤 पूरा नाम / Full Name"
                )
                Spacer(Modifier.height(14.dp))

                SethGTextField(
                    value = phone,
                    onValueChange = { phone = it; viewModel.regPhone = it },
                    label = "📱 फोन नंबर / Phone",
                    keyboardType = KeyboardType.Phone
                )
                Spacer(Modifier.height(14.dp))

                SethGTextField(
                    value = email,
                    onValueChange = { email = it; viewModel.regEmail = it },
                    label = "📧 ईमेल / Email (optional)",
                    keyboardType = KeyboardType.Email
                )
                Spacer(Modifier.height(14.dp))

                SethGTextField(
                    value = password,
                    onValueChange = { password = it; viewModel.regPassword = it },
                    label = "🔒 पासवर्ड / Password",
                    isPassword = true,
                    showPassword = showPass,
                    onTogglePass = { showPass = !showPass }
                )
                Spacer(Modifier.height(14.dp))

                SethGTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; viewModel.regConfirmPassword = it },
                    label = "🔒 पासवर्ड दोबारा / Confirm Password",
                    isPassword = true,
                    showPassword = showPass,
                    onTogglePass = { showPass = !showPass }
                )

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = { viewModel.register() },
                    enabled = !state.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "खाता बनाएं  ·  Create Account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                TextButton(onClick = onNavigateToLogin) {
                    Text(
                        "पहले से खाता है? लॉगिन करें  ·  Already have account? Login",
                        color = GreenPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
