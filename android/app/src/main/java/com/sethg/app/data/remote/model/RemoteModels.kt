package com.sethg.app.data.remote.model

import com.google.gson.annotations.SerializedName

// ── Auth ────────────────────────────────────────────────────────────────────

data class RegisterRequest(
    val name: String,
    val phone: String?,
    val email: String?,
    val password: String,
    @SerializedName("confirmPassword") val confirmPassword: String,
    val language: String = "en",
    val role: String = "user"
)

data class LoginRequest(
    val phone: String?,
    val email: String?,
    val password: String
)

data class RefreshRequest(
    val refreshToken: String
)

data class AuthResponse(
    val user: RemoteUser,
    val accessToken: String,
    val refreshToken: String
)

data class RefreshResponse(
    val accessToken: String,
    val refreshToken: String
)

// ── User ─────────────────────────────────────────────────────────────────────

data class RemoteUser(
    val id: String,
    val name: String,
    val phone: String?,
    val email: String?,
    @SerializedName("photo_url") val photoUrl: String?,
    val language: String,
    val role: String?,
    @SerializedName("certificate_url") val certificateUrl: String?,
    @SerializedName("is_verified") val isVerified: Boolean?,
    @SerializedName("created_at") val createdAt: String?
)

data class UpdateProfileRequest(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val language: String? = null,
    @SerializedName("photo_url") val photoUrl: String? = null,
    val currentPassword: String? = null,
    val newPassword: String? = null,
    @SerializedName("certificate_url") val certificateUrl: String? = null
)

data class UserProfileResponse(
    val user: RemoteUser
)

// ── Earnings ─────────────────────────────────────────────────────────────────

data class EarningsSummary(
    val period: String,
    val total: Double,
    val transactionCount: Int,
    val transactions: List<RemoteEarning>? = null,
    val dailyBreakdown: List<DailyBreakdown>? = null,
    val weeklyBreakdown: List<WeeklyBreakdown>? = null
)

data class RemoteEarning(
    val id: String,
    val amount: Double,
    val material: String?,
    @SerializedName("weight_kg") val weightKg: Double?,
    val note: String?,
    @SerializedName("earned_at") val earnedAt: String
)

data class DailyBreakdown(
    val date: String,
    val total: Double
)

data class WeeklyBreakdown(
    @SerializedName("week_start") val weekStart: String,
    val total: Double
)

data class AddEarningRequest(
    val amount: Double,
    val material: String? = null,
    @SerializedName("weight_kg") val weightKg: Double? = null,
    val note: String? = null
)
