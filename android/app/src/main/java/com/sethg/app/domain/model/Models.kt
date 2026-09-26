package com.sethg.app.domain.model

// ── Domain models (UI-facing, decoupled from network/DB) ──────────────────────

data class User(
    val id: String,
    val name: String,
    val phone: String?,
    val email: String?,
    val photoUrl: String?,
    val language: String
)

data class EarningsSummary(
    val period: String,
    val total: Double,
    val transactionCount: Int
)

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val code: Int? = null) : Result<Nothing>()
    object Loading : Result<Nothing>()
}
