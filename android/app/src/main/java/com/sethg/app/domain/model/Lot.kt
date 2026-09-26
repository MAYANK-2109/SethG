package com.sethg.app.domain.model

import kotlin.math.roundToInt

// ── Material categories (PS 26229 list + common kabadi items) ─────────────────
// Order = display order in the picker (most common first).
// rateLow / rateHigh are ₹ per kg.
// ⚠️ PLACEHOLDER RATES — replace with prices collected from field survey with
//    collectors/recyclers before any demo. Do not present these as real prices.

enum class MaterialCategory(val rateLow: Double, val rateHigh: Double) {
    CABLE(150.0, 350.0),
    CHARGER(20.0, 60.0),      // chargers, adapters, SMPS
    PCB(60.0, 250.0),
    MOBILE(100.0, 400.0),     // phones, tablets
    BATTERY(40.0, 120.0),
    MOTOR(25.0, 60.0),
    SWITCH(30.0, 80.0),       // switches, MCBs, sockets
    LCD(8.0, 30.0),
    CRT(0.0, 5.0),
    PLASTIC(8.0, 20.0),
    OTHER(10.0, 50.0)         // any other e-waste
}

data class PriceEstimate(val low: Int, val high: Int)

object PriceEstimator {
    // Always a range, never a single number — a photo tells category, not grade
    fun estimate(category: MaterialCategory, weightKg: Double): PriceEstimate =
        PriceEstimate(
            low  = roundTo10(weightKg * category.rateLow),
            high = roundTo10(weightKg * category.rateHigh)
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
    val status: String,
    val syncStatus: String,
    val createdAt: Long,
    val photos: List<CapturedPhoto>
)
