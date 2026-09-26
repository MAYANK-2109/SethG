package com.sethg.app.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sethg.app.R
import com.sethg.app.ui.theme.*
import com.sethg.app.ui.viewmodel.AuthViewModel
import com.sethg.app.ui.viewmodel.LanguageViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
    languageVm: LanguageViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val langState by languageVm.uiState.collectAsState()
    @Suppress("SpellCheckingInspection")
    val snackbarHostState = remember { SnackbarHostState() }

    var name            by remember { mutableStateOf("") }
    var phone           by remember { mutableStateOf("") }
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPass        by remember { mutableStateOf(false) }
    var selectedRole    by remember { mutableStateOf("recycler") }
    var showLanguagePicker by remember { mutableStateOf(false) }
    val roles = listOf("recycler" to "Recycler", "vendor" to "Vendor")

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
        modifier = modifier,
        containerColor = LightBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.register_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateToLogin) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    Surface(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clickable { showLanguagePicker = true },
                        shape = RoundedCornerShape(20.dp),
                        color = LightSurface,
                        border = BorderStroke(1.dp, LightBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Language, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = languageOptions.find { it.code == langState.selectedLanguage }?.nativeName ?: "Language",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LightBackground,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))

                // Input fields inside a popped surface card
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
                            value = name,
                            onValueChange = { name = it; viewModel.regName = it },
                            label = stringResource(R.string.full_name),
                            leadingIcon = Icons.Filled.Person
                        )
                        Spacer(Modifier.height(14.dp))

                        SethGTextField(
                            value = phone,
                            onValueChange = { phone = it; viewModel.regPhone = it },
                            label = stringResource(R.string.phone),
                            leadingIcon = Icons.Filled.Phone,
                            keyboardType = KeyboardType.Phone
                        )
                        Spacer(Modifier.height(14.dp))

                        SethGTextField(
                            value = email,
                            onValueChange = { email = it; viewModel.regEmail = it },
                            label = stringResource(R.string.email),
                            leadingIcon = Icons.Filled.Email,
                            keyboardType = KeyboardType.Email
                        )
                        Spacer(Modifier.height(14.dp))

                        SethGTextField(
                            value = password,
                            onValueChange = { password = it; viewModel.regPassword = it },
                            label = stringResource(R.string.password),
                            leadingIcon = Icons.Filled.Lock,
                            isPassword = true,
                            showPassword = showPass,
                            onTogglePass = { showPass = !showPass }
                        )
                        Spacer(Modifier.height(14.dp))

                        SethGTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it; viewModel.regConfirmPassword = it },
                            label = stringResource(R.string.confirm_password),
                            leadingIcon = Icons.Filled.Lock,
                            isPassword = true,
                            showPassword = showPass,
                            onTogglePass = { showPass = !showPass }
                        )
                        Spacer(Modifier.height(20.dp))
                        Text("Select Role", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            roles.forEach { (roleValue, roleLabel) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        selectedRole = roleValue
                                        viewModel.regRole = roleValue
                                    }
                                ) {
                                    RadioButton(
                                        selected = (selectedRole == roleValue),
                                        onClick = {
                                            selectedRole = roleValue
                                            viewModel.regRole = roleValue
                                        }
                                    )
                                    Text(text = roleLabel, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = { viewModel.register() },
                    enabled = !state.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), spotColor = GreenPrimary.copy(alpha = 0.4f)),
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
                            stringResource(R.string.register),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                TextButton(onClick = onNavigateToLogin) {
                    Text(
                        stringResource(R.string.already_have_account),
                        color = GreenPrimary,
                        style = MaterialTheme.typography.bodyMedium,
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
