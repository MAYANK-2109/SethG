package com.sethg.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sethg.app.ui.viewmodel.ProfileViewModel

@Composable
fun RecyclerDashboardScreen(
    onHandover: (lotId: String, declaredKg: Double) -> Unit = { _, _ -> },
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val user = state.user

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Recycler Dashboard", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (user?.isVerified == true) {
            Text("✅ Verified Recycler", color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            // Stages 2–4: lots within 3–5 km, offers, pooled trips, handover
            RecyclerMarketSection(onHandover = onHandover)
        } else {
            Text("Not Verified", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { 
                // Upload certificate mock
                viewModel.updateProfile(certificateUrl = "mock_cert_url_123") 
            }) {
                Text("Upload Government Certificate")
            }
        }
    }
}
