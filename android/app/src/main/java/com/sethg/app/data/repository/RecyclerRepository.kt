package com.sethg.app.data.repository

import com.sethg.app.data.remote.SethGApiService
import com.sethg.app.data.remote.model.AcceptedLot
import com.sethg.app.data.remote.model.HandoverRequest
import com.sethg.app.data.remote.model.NearbyLotsResponse
import com.sethg.app.data.remote.model.OfferRequest
import com.sethg.app.data.remote.model.RecyclerProfileRequest
import com.sethg.app.data.remote.model.HandoverResponse
import com.sethg.app.data.remote.model.RemoteTrip
import com.sethg.app.domain.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Recycler side of stages 2–4: facility setup, nearby lots, offers, trips, handover. */
@Singleton
class RecyclerRepository @Inject constructor(
    private val api: SethGApiService
) {
    suspend fun saveFacility(lat: Double, lon: Double, materials: List<String>?, vehicleMinKg: Double?) =
        call { api.updateRecyclerProfile(RecyclerProfileRequest(lat, lon, materials, vehicleMinKg)) }

    /** Lots within 3–5 km this recycler may bid on; `since` limits to new ones (for alerts). */
    suspend fun nearbyLots(since: String? = null): Result<NearbyLotsResponse> = call { api.nearbyLots(since) }

    suspend fun makeOffer(lotId: String, ratePerKg: Double, pickupDate: String, note: String?) =
        call { api.makeOffer(lotId, OfferRequest(ratePerKg, pickupDate, note)) }

    suspend fun acceptedLots(): Result<List<AcceptedLot>> =
        when (val r = call { api.acceptedLots() }) {
            is Result.Success -> Result.Success(r.data.lots)
            is Result.Error   -> r
            Result.Loading    -> Result.Loading
        }

    /** Also plans pending pooled / hub lots on the server before listing. */
    suspend fun trips(): Result<List<RemoteTrip>> =
        when (val r = call { api.trips() }) {
            is Result.Success -> Result.Success(r.data.trips)
            is Result.Error   -> r
            Result.Loading    -> Result.Loading
        }

    /** Records weight + photos + GPS; the response carries the code the vendor must type in. */
    suspend fun handover(lotId: String, request: HandoverRequest): Result<HandoverResponse> =
        call { api.handover(lotId, request) }

    private suspend fun <T> call(block: suspend () -> Response<T>): Result<T> = withContext(Dispatchers.IO) {
        try {
            val response = block()
            val body = response.body()
            when {
                response.isSuccessful && body != null -> Result.Success(body)
                response.isSuccessful -> @Suppress("UNCHECKED_CAST") (Result.Success(Unit as T))
                else -> Result.Error(errorMessage(response.errorBody()?.string()) ?: "Request failed", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.localizedMessage ?: "Network error")
        }
    }
}
