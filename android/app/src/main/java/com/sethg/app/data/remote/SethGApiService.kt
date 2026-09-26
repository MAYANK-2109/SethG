package com.sethg.app.data.remote

import com.sethg.app.data.remote.model.*
import retrofit2.Response
import retrofit2.http.*

interface SethGApiService {

    // ── Auth ────────────────────────────────────────────────────────────────
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<RefreshResponse>

    @POST("auth/logout")
    suspend fun logout(@Body request: RefreshRequest): Response<Unit>

    // ── User ─────────────────────────────────────────────────────────────────
    @GET("user/profile")
    suspend fun getProfile(): Response<UserProfileResponse>

    @PUT("user/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<UserProfileResponse>

    // ── Earnings ──────────────────────────────────────────────────────────────
    @GET("earnings/today")
    suspend fun getEarningsToday(): Response<EarningsSummary>

    @GET("earnings/weekly")
    suspend fun getEarningsWeekly(): Response<EarningsSummary>

    @GET("earnings/monthly")
    suspend fun getEarningsMonthly(): Response<EarningsSummary>

    @POST("earnings")
    suspend fun addEarning(@Body request: AddEarningRequest): Response<Unit>
}
