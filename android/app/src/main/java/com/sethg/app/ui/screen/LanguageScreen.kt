package com.sethg.app.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sethg.app.R
import com.sethg.app.ui.theme.*
import com.sethg.app.ui.viewmodel.LanguageViewModel

data class LanguageOption(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val icon: ImageVector
)

val languageOptions = listOf(
    LanguageOption("en", "English",  "English",  Icons.Filled.Language),
    LanguageOption("hi", "हिंदी",    "Hindi",    Icons.Filled.Translate),
    LanguageOption("mr", "मराठी",   "Marathi",  Icons.Filled.Public),
    LanguageOption("te", "తెలుగు",  "Telugu",   Icons.Filled.Language)
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
            .background(LightBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(50.dp))

            // App icon / logo (Popped up executive badge)
            Surface(
                modifier = Modifier
                    .size(96.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(26.dp),
                        ambientColor = Color(0x1F047857),
                        spotColor = Color(0x3D047857)
                    ),
                shape = RoundedCornerShape(26.dp),
                color = GreenPrimary,
                tonalElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Autorenew,
                        contentDescription = "Seth G Logo",
                        tint = Color.White,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text       = stringResource(R.string.app_name),
                style      = MaterialTheme.typography.headlineLarge,
                color      = GreenPrimary,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text  = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )

            Spacer(Modifier.height(40.dp))

            Text(
                text      = stringResource(R.string.select_language),
                style     = MaterialTheme.typography.titleLarge,
                color     = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            languageOptions.forEach { lang ->
                LanguageCard(
                    option     = lang,
                    isSelected = state.selectedLanguage == lang.code,
                    onClick    = { viewModel.selectLanguage(lang.code) }
                )
                Spacer(Modifier.height(14.dp))
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick  = { viewModel.confirmLanguage() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), spotColor = GreenPrimary.copy(alpha = 0.4f)),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                Text(
                    text       = stringResource(R.string.continue_btn),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
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
    val borderColor = if (isSelected) GreenPrimary else LightBorder
    val bgColor     = if (isSelected) GreenContainer.copy(alpha = 0.4f) else LightSurface

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isSelected) 8.dp else 4.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color(0x0F000000),
                spotColor = if (isSelected) Color(0x26047857) else Color(0x14000000)
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            ),
        color = bgColor,
        tonalElevation = if (isSelected) 4.dp else 1.dp
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) GreenPrimary else LightSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = option.icon,
                    contentDescription = option.englishName,
                    tint = if (isSelected) Color.White else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text       = option.nativeName,
                    style      = MaterialTheme.typography.titleMedium,
                    color      = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text  = option.englishName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = GreenPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
