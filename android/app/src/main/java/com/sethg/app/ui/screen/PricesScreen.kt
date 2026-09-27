package com.sethg.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sethg.app.R
import com.sethg.app.domain.model.MaterialCategory
import com.sethg.app.ui.theme.*

data class PriceGuideItem(
    val category: MaterialCategory,
    val name: String,
    val pricePerKg: String,
    val icon: ImageVector
)

@Composable
fun PricesScreen(
    onProfileClick: () -> Unit = {},
    onNotificationClick: (() -> Unit)? = null
) {
    val items = remember {
        listOf(
            PriceGuideItem(MaterialCategory.MOBILE, "Mobile phone", "₹140.00", Icons.Outlined.Smartphone),
            PriceGuideItem(MaterialCategory.LCD, "Laptop", "₹140.00", Icons.Outlined.Laptop),
            PriceGuideItem(MaterialCategory.MOBILE, "Tablet", "₹140.00", Icons.Outlined.TabletAndroid),
            PriceGuideItem(MaterialCategory.CHARGER, "Charger / adapter", "₹140.00", Icons.Outlined.ElectricalServices),
            PriceGuideItem(MaterialCategory.BATTERY, "Battery", "₹140.00", Icons.Outlined.BatteryChargingFull),
            PriceGuideItem(MaterialCategory.PCB, "Motherboard / PCB", "₹140.00", Icons.Outlined.Memory),
            PriceGuideItem(MaterialCategory.CABLE, "Cable & Wire", "₹140.00", Icons.Outlined.Cable),
            PriceGuideItem(MaterialCategory.SWITCH, "Switch / Relays", "₹140.00", Icons.Outlined.ToggleOn),
            PriceGuideItem(MaterialCategory.MOTOR, "Electric Motor", "₹140.00", Icons.Outlined.Settings),
            PriceGuideItem(MaterialCategory.PLASTIC, "Plastic scrap", "₹140.00", Icons.Outlined.Recycling)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        SethGTopHeader(
            onProfileClick = onProfileClick,
            onNotificationClick = onNotificationClick
        )
        SethGStatusBanner()

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                Column {
                    Text(
                        text = "Local price guide",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = TextPrimary
                        ),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    SethGHeroCard(
                        tag = "PROVISIONAL PRICE GUIDE",
                        title = "Pune",
                        description = "Provisional recycler rate cards for Pune. Final offers may differ after inspection."
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            items(items) { item ->
                PriceCard(item = item)
            }

            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun PriceCard(item: PriceGuideItem) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, LightBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = GreenPrimary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = item.pricePerKg,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "per kg",
                fontSize = 13.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = item.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = GreenPrimary
            )
        }
    }
}
