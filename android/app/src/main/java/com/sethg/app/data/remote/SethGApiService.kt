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

    // ── Lots (collector) ──────────────────────────────────────────────────────
    @POST("lots")
    suspend fun syncLot(@Body request: SyncLotRequest): Response<SyncLotResponse>

    @GET("lots/mine")
    suspend fun myLots(): Response<MyLotsResponse>

    @POST("lots/{id}/offers/{offerId}/accept")
    suspend fun acceptOffer(@Path("id") lotId: String, @Path("offerId") offerId: String): Response<AcceptOfferResponse>

    @POST("lots/{id}/transport")
    suspend fun chooseTransport(@Path("id") lotId: String, @Body request: TransportRequest): Response<LotResponse>

    @POST("lots/{id}/confirm")
    suspend fun confirmHandover(@Path("id") lotId: String, @Body request: ConfirmHandoverRequest): Response<LotResponse>

    // ── Recycler ──────────────────────────────────────────────────────────────
    @PUT("recycler/profile")
    suspend fun updateRecyclerProfile(@Body request: RecyclerProfileRequest): Response<Unit>

    @GET("recycler/lots/nearby")
    suspend fun nearbyLots(@Query("since") since: String? = null): Response<NearbyLotsResponse>

    @POST("recycler/lots/{id}/offers")
    suspend fun makeOffer(@Path("id") lotId: String, @Body request: OfferRequest): Response<Unit>

    @GET("recycler/lots/accepted")
    suspend fun acceptedLots(): Response<AcceptedLotsResponse>

    @GET("recycler/trips")
    suspend fun trips(): Response<TripsResponse>

    @POST("recycler/lots/{id}/handover")
    suspend fun handover(@Path("id") lotId: String, @Body request: HandoverRequest): Response<HandoverResponse>
}
