package com.sethg.app.domain.model

import kotlin.math.roundToInt

// ── Material categories (PS 26229 list + common kabadi items) ─────────────────
// Order = display order in the picker (most common first).
// Rates (₹/kg) are zone-wise: see PriceRateRepository / assets/ewaste_rates.json.

enum class MaterialCategory {
    CABLE,
    CHARGER,      // chargers, adapters, SMPS
    PCB,
    MOBILE,       // phones, tablets
    BATTERY,
    MOTOR,
    SWITCH,       // switches, MCBs, sockets
    LCD,
    CRT,
    PLASTIC,
    OTHER         // any other e-waste
}

data class PriceEstimate(val low: Int, val high: Int)

object PriceEstimator {
    // Always a range, never a single number — a photo tells category, not grade
    fun estimate(rateLowPerKg: Double, rateHighPerKg: Double, weightKg: Double): PriceEstimate =
        PriceEstimate(
            low  = roundTo10(weightKg * rateLowPerKg),
            high = roundTo10(weightKg * rateHighPerKg)
        )

    private fun roundTo10(value: Double): Int = ((value / 10).roundToInt()) * 10
}

// ── Lot ───────────────────────────────────────────────────────────────────────

data class CapturedPhoto(
    val filePath: String,
    val sha256: String,
    val capturedAt: Long,
    val nonce: String,
    val payload: String,
    val signature: String,
    val aiVerdict: String,
    val aiLabel: String?
)

data class Lot(
    val lotId: String,
    val category: MaterialCategory,
    val weightKg: Double,
    val estimate: PriceEstimate,
    val priceRegion: String?,     // whose rates priced it: "Raipur", "CENTRAL" or "India"
    val status: String,
    val syncStatus: String,
    val createdAt: Long,
    val photos: List<CapturedPhoto>
)
