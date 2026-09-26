package com.sethg.app.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sethg.app.ui.theme.*

// ── Role Selection Screen ──────────────────────────────────────────────────────
// Shown once after login/register when the server role is still "user" (unset).
// The user picks Vendor or Recycler; the choice is immediately saved and the
// dashboard for that role is opened.

@Composable
fun RoleSelectionScreen(
    onSelectVendor: () -> Unit,
    onSelectRecycler: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Subtle entrance animation for the cards
    val infiniteTransition = rememberInfiniteTransition(label = "role_bg")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue  = 0.6f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF064E3B), Color(0xFF065F46), LightBackground)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))

            // Logo + title
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Autorenew,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Seth G",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "आप क्या करते हैं?",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium
            )
            Text(
                "Who are you?",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.65f)
            )

            Spacer(Modifier.height(48.dp))

            // Vendor Card
            RoleCard(
                icon = Icons.Filled.ShoppingCart,
                emojiLabel = "🛒",
                title = "Vendor",
                titleHindi = "विक्रेता (कबाड़ी)",
                description = "I collect and sell e-waste.\nमैं ई-कचरा इकट्ठा करके बेचता हूँ।",
                gradientColors = listOf(Color(0xFF047857), Color(0xFF065F46)),
                glowAlpha = glowAlpha,
                onClick = onSelectVendor
            )

            Spacer(Modifier.height(20.dp))

            // Recycler Card
            RoleCard(
                icon = Icons.Filled.Recycling,
                emojiLabel = "♻️",
                title = "Recycler",
                titleHindi = "रिसाइक्लर",
                description = "I buy e-waste and recycle it.\nमैं ई-कचरा खरीदता और रिसाइकल करता हूँ।",
                gradientColors = listOf(Color(0xFF1D4ED8), Color(0xFF1E3A8A)),
                glowAlpha = glowAlpha,
                onClick = onSelectRecycler
            )

            Spacer(Modifier.height(48.dp))

            Text(
                "You can change your role later from Profile settings.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RoleCard(
    icon: ImageVector,
    emojiLabel: String,
    title: String,
    titleHindi: String,
    description: String,
    gradientColors: List<Color>,
    glowAlpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "card_scale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(
                elevation = 16.dp,
                shape     = RoundedCornerShape(24.dp),
                ambientColor = gradientColors[0].copy(alpha = glowAlpha * 0.4f),
                spotColor    = gradientColors[0].copy(alpha = glowAlpha * 0.6f)
            )
            .clickable(
                onClick = onClick
            ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(gradientColors[0], gradientColors[1], gradientColors[0].copy(alpha = 0.85f))
                    )
                )
                .padding(28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon bubble
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.18f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector      = icon,
                            contentDescription = null,
                            tint             = Color.White,
                            modifier         = Modifier.size(42.dp)
                        )
                    }
                }

                Spacer(Modifier.width(20.dp))

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            style      = MaterialTheme.typography.headlineSmall,
                            color      = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(emojiLabel, fontSize = 22.sp)
                    }
                    Text(
                        titleHindi,
                        style  = MaterialTheme.typography.titleSmall,
                        color  = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        description,
                        style  = MaterialTheme.typography.bodyMedium,
                        color  = Color.White.copy(alpha = 0.75f),
                        lineHeight = 20.sp
                    )
                }

                Icon(
                    imageVector      = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint             = Color.White.copy(alpha = 0.7f),
                    modifier         = Modifier.size(28.dp)
                )
            }
        }
    }
}
