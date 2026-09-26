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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sethg.app.R
import com.sethg.app.ui.theme.*
import com.sethg.app.ui.viewmodel.AuthViewModel
import com.sethg.app.ui.viewmodel.LanguageViewModel

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
    languageVm: LanguageViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val langState by languageVm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var identifier by remember { mutableStateOf("") }
    var password   by remember { mutableStateOf("") }
    var showPass   by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthViewModel.UiEvent.NavigateToDashboard -> onLoginSuccess()
                is AuthViewModel.UiEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                else -> Unit
            }
        }
    }

    Scaffold(
        containerColor = LightBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Language switcher button
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 20.dp)
                    .clickable { showLanguagePicker = true },
                shape = RoundedCornerShape(20.dp),
                color = LightSurface,
                border = BorderStroke(1.dp, LightBorder),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = null,
                        tint = GreenPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = languageOptions.find { it.code == langState.selectedLanguage }?.nativeName ?: "Language",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(Modifier.height(50.dp))

                // Logo (Popped Card)
                Surface(
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(
                            elevation = 10.dp,
                            shape = RoundedCornerShape(22.dp),
                            ambientColor = Color(0x1F047857),
                            spotColor = Color(0x3D047857)
                        ),
                    shape = RoundedCornerShape(22.dp),
                    color = GreenPrimary,
                    tonalElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Autorenew,
                            contentDescription = "Seth G Logo",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = GreenPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    stringResource(R.string.login_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(36.dp))

                // Input fields inside a popped card container
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(24.dp),
                            ambientColor = Color(0x0A000000),
                            spotColor = Color(0x14000000)
                        )
                        .border(1.dp, LightBorder, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    color = LightSurface
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        SethGTextField(
                            value         = identifier,
                            onValueChange = {
                                identifier = it
                                viewModel.loginIdentifier = it
                            },
                            label         = stringResource(R.string.phone_or_email),
                            leadingIcon   = Icons.Filled.Phone,
                            keyboardType  = KeyboardType.Text
                        )

                        Spacer(Modifier.height(16.dp))

                        SethGTextField(
                            value         = password,
                            onValueChange = {
                                password = it
                                viewModel.loginPassword = it
                            },
                            label         = stringResource(R.string.password),
                            leadingIcon   = Icons.Filled.Lock,
                            isPassword    = true,
                            showPassword  = showPass,
                            onTogglePass  = { showPass = !showPass }
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                Button(
                    onClick   = { viewModel.login() },
                    enabled   = !state.isLoading,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), spotColor = GreenPrimary.copy(alpha = 0.4f)),
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
                            stringResource(R.string.login),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                TextButton(onClick = onNavigateToRegister) {
                    Text(
                        stringResource(R.string.create_account),
                        color = GreenPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (showLanguagePicker) {
        LanguagePickerDialog(
            currentCode = langState.selectedLanguage,
            onDismiss   = { showLanguagePicker = false },
            onConfirm   = { code ->
                languageVm.selectLanguage(code)
                languageVm.confirmLanguage()
                showLanguagePicker = false
            }
        )
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
fun SethGTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector? = null,
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
        leadingIcon   = leadingIcon?.let {
            { Icon(it, contentDescription = null, tint = GreenPrimary) }
        },
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
                    tint = TextSecondary
                )
            }
        }) else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor   = LightSurfaceVariant,
            unfocusedContainerColor = LightSurfaceVariant.copy(alpha = 0.5f),
            focusedBorderColor      = GreenPrimary,
            unfocusedBorderColor    = LightBorder,
            focusedLabelColor       = GreenPrimary,
            unfocusedLabelColor     = TextSecondary,
            cursorColor             = GreenPrimary,
            focusedTextColor        = TextPrimary,
            unfocusedTextColor      = TextPrimary
        )
    )
}
