package com.sethg.app.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sethg.app.ui.theme.*
import com.sethg.app.ui.viewmodel.LanguageViewModel

data class LanguageOption(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val flag: String
)

val languageOptions = listOf(
    LanguageOption("en", "English",  "English",  "🇬🇧"),
    LanguageOption("hi", "हिंदी",    "Hindi",    "🇮🇳"),
    LanguageOption("mr", "मराठी",   "Marathi",  "🏵️"),
    LanguageOption("te", "తెలుగు",  "Telugu",   "🌺")
)

@Composable
fun LanguageScreen(
    onLanguageConfirmed: () -> Unit,
    viewModel: LanguageViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isLanguageAlreadySelected) {
        if (state.isLanguageAlreadySelected) onLanguageConfirmed()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A1F0A), BackgroundDark)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(60.dp))

            // App icon / logo
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(GreenPrimary),
                contentAlignment = Alignment.Center
            ) {
                Text("♻️", fontSize = 48.sp)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text       = "Seth G",
                style      = MaterialTheme.typography.headlineLarge,
                color      = GreenPrimary,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text  = "कबाड़ी वाला App",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurfaceDark.copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(48.dp))

            Text(
                text      = "अपनी भाषा चुनें\nSelect your language",
                style     = MaterialTheme.typography.titleLarge,
                color     = OnSurfaceDark,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            languageOptions.forEach { lang ->
                LanguageCard(
                    option     = lang,
                    isSelected = state.selectedLanguage == lang.code,
                    onClick    = { viewModel.selectLanguage(lang.code) }
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick  = { viewModel.confirmLanguage() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                Text(
                    text      = "जारी रखें  ·  Continue",
                    style     = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun LanguageCard(
    option: LanguageOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) GreenPrimary else SurfaceVariant
    val bgColor     = if (isSelected) GreenPrimary.copy(alpha = 0.15f) else SurfaceDark

    Surface(
        modifier  = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .border(
                width  = if (isSelected) 2.dp else 1.dp,
                color  = borderColor,
                shape  = RoundedCornerShape(16.dp)
            ),
        color     = bgColor,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier            = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Text(option.flag, fontSize = 32.sp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text       = option.nativeName,
                    style      = MaterialTheme.typography.titleMedium,
                    color      = OnSurfaceDark,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text  = option.englishName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SubText
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = GreenPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
