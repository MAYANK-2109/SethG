package com.sethg.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sethg.app.ui.viewmodel.ProfileViewModel

@Composable
fun RecyclerDashboardScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val user = state.user

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Recycler Dashboard", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (user?.isVerified == true) {
            Text("✅ Verified Recycler", color = MaterialTheme.colorScheme.primary)
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
