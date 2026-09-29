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
    val role: String = "user",
    @SerializedName("certificate_url") val certificateUrl: String? = null
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
    @SerializedName("certificate_url") val certificateUrl: String? = null,
    val role: String? = null
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

// ── Lots, offers, trips, handover (stages 2–4) ───────────────────────────────

data class SyncLotRequest(
    val id: String,
    val category: String,
    @SerializedName("weight_kg") val weightKg: Double,
    @SerializedName("estimate_low") val estimateLow: Int,
    @SerializedName("estimate_high") val estimateHigh: Int,
    @SerializedName("price_region") val priceRegion: String?,
    val lat: Double,
    val lon: Double,
    @SerializedName("photo_hashes") val photoHashes: List<String>,
    @SerializedName("photo_url") val photoUrl: String? = null
)

data class RemoteOffer(
    val id: String,
    @SerializedName("rate_per_kg") val ratePerKg: Double,
    @SerializedName("pickup_date") val pickupDate: String,
    val note: String?,
    @SerializedName("distance_km") val distanceKm: Double,
    val status: String,
    @SerializedName("recycler_name") val recyclerName: String,
    @SerializedName("offer_total") val offerTotal: Double
)

data class RemoteHandover(
    val id: String,
    @SerializedName("actual_weight_kg") val actualWeightKg: Double,
    @SerializedName("final_amount") val finalAmount: Double,
    @SerializedName("weight_flagged") val weightFlagged: Boolean,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("confirmed_at") val confirmedAt: String? = null,
    @SerializedName("blockchain_proof_hash") val blockchainProofHash: String? = null,
    @SerializedName("blockchain_tx_hash") val blockchainTxHash: String? = null
)

data class RemoteLot(
    val id: String,
    val category: String,
    @SerializedName("weight_kg") val weightKg: Double,
    val status: String,
    @SerializedName("transport_mode") val transportMode: String?,
    @SerializedName("trip_mode") val tripMode: String?,
    @SerializedName("hub_name") val hubName: String?,
    @SerializedName("slot_start") val slotStart: String?,
    @SerializedName("slot_end") val slotEnd: String?,
    val offers: List<RemoteOffer> = emptyList(),
    val handover: RemoteHandover?,
    @SerializedName("handover_otp_hash") val handoverOtpHash: String? = null,
    @SerializedName("escrow_status") val escrowStatus: String? = null,
    @SerializedName("escrow_tx_id") val escrowTxId: String? = null,
    @SerializedName("escrow_amount") val escrowAmount: Double? = null,
    @SerializedName("blockchain_tx_hash") val blockchainTxHash: String? = null,
    @SerializedName("blockchain_proof_hash") val blockchainProofHash: String? = null,
    @SerializedName("epr_token_id") val eprTokenId: String? = null,
    @SerializedName("photo_url") val photoUrl: String? = null
)

data class SyncLotResponse(
    val lot: RemoteLot,
    @SerializedName("matched_recyclers") val matchedRecyclers: Int,
    @SerializedName("match_radius_km") val matchRadiusKm: Int
)

data class MyLotsResponse(val lots: List<RemoteLot>)

data class RemoteHub(val id: String, val name: String, @SerializedName("distance_km") val distanceKm: Double)

data class TransportOptions(
    @SerializedName("pickup_allowed") val pickupAllowed: Boolean,
    @SerializedName("vehicle_min_kg") val vehicleMinKg: Double,
    val hubs: List<RemoteHub>
)

data class AcceptOfferResponse(
    val lot: RemoteLot,
    @SerializedName("transport_options") val transportOptions: TransportOptions
)

data class TransportRequest(val mode: String, @SerializedName("hub_id") val hubId: String? = null)

data class LotResponse(val lot: RemoteLot)

data class ConfirmHandoverRequest(val otp: String, @SerializedName("confirmed_at") val confirmedAt: String)

// Recycler side
data class RecyclerProfileRequest(
    val lat: Double,
    val lon: Double,
    @SerializedName("accepted_materials") val acceptedMaterials: List<String>?,
    @SerializedName("vehicle_min_kg") val vehicleMinKg: Double?
)

data class NearbyLot(
    val id: String,
    val category: String,
    @SerializedName("weight_kg") val weightKg: Double,
    @SerializedName("estimate_low") val estimateLow: Int,
    @SerializedName("estimate_high") val estimateHigh: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("collector_first_name") val collectorFirstName: String,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("offer_count") val offerCount: Int,
    @SerializedName("my_rate_per_kg") val myRatePerKg: Double?,
    @SerializedName("photo_url") val photoUrl: String? = null
)

data class NearbyLotsResponse(val lots: List<NearbyLot>, @SerializedName("server_time") val serverTime: String)

data class OfferRequest(
    @SerializedName("rate_per_kg") val ratePerKg: Double,
    @SerializedName("pickup_date") val pickupDate: String,
    val note: String?
)

data class TripStop(
    @SerializedName("lot_id") val lotId: String,
    @SerializedName("stop_seq") val stopSeq: Int,
    @SerializedName("slot_start") val slotStart: String,
    @SerializedName("slot_end") val slotEnd: String,
    val category: String,
    @SerializedName("weight_kg") val weightKg: Double,
    val status: String,
    val lat: Double,
    val lon: Double,
    @SerializedName("collector_name") val collectorName: String,
    @SerializedName("collector_phone") val collectorPhone: String?,
    @SerializedName("handover_otp") val handoverOtp: String      // the vendor types this in to confirm
)

data class RemoteTrip(
    val id: String,
    val mode: String,
    @SerializedName("scheduled_date") val scheduledDate: String,
    @SerializedName("total_kg") val totalKg: Double,
    @SerializedName("hub_name") val hubName: String?,
    val status: String? = null,
    val stops: List<TripStop>
)

data class TripsResponse(val trips: List<RemoteTrip>)

data class AcceptedLot(
    val id: String,
    val category: String,
    @SerializedName("weight_kg") val weightKg: Double,
    val status: String,
    @SerializedName("transport_mode") val transportMode: String?,
    @SerializedName("rate_per_kg") val ratePerKg: Double,
    @SerializedName("expected_amount") val expectedAmount: Double,
    @SerializedName("collector_name") val collectorName: String,
    @SerializedName("slot_start") val slotStart: String?,
    @SerializedName("handover_otp") val handoverOtp: String
)

data class AcceptedLotsResponse(val lots: List<AcceptedLot>)

data class HandoverRequest(
    @SerializedName("actual_weight_kg") val actualWeightKg: Double,
    val lat: Double,
    val lon: Double,
    @SerializedName("captured_at") val capturedAt: String,
    @SerializedName("photo_hashes") val photoHashes: List<String>
)

data class HandoverResponse(
    val handover: RemoteHandover,
    @SerializedName("handover_otp") val handoverOtp: String
)

// Chat
data class ChatMessageResponse(
    val id: String,
    @SerializedName("lot_id") val lotId: String,
    @SerializedName("sender_id") val senderId: String,
    val content: String,
    @SerializedName("created_at") val createdAt: String
)

data class PostMessageRequest(
    val content: String
)

// Chat Inbox
data class ChatConversation(
    @SerializedName("lot_id")         val lotId: String,
    @SerializedName("category")       val category: String,
    @SerializedName("status")         val status: String,
    @SerializedName("my_role")        val myRole: String,        // "vendor" or "recycler"
    @SerializedName("other_name")     val otherName: String?,
    @SerializedName("other_id")       val otherId: String?,
    @SerializedName("last_message")   val lastMessage: String?,  // bcrypt hash (display as "🔒 Encrypted")
    @SerializedName("last_message_at")val lastMessageAt: String?,
    @SerializedName("last_sender_id") val lastSenderId: String?,
    @SerializedName("message_count") val messageCount: Int
)

data class MyChatsResponse(
    val chats: List<ChatConversation>
)

// Dispute & Escrow
data class DisputeRequest(
    val reason: String
)

data class DisputeRemoteModel(
    val id: String,
    @SerializedName("lot_id") val lotId: String,
    @SerializedName("raised_by") val raisedBy: String,
    val reason: String,
    @SerializedName("declared_weight_kg") val declaredWeightKg: Double?,
    @SerializedName("actual_weight_kg") val actualWeightKg: Double?,
    val status: String,
    @SerializedName("created_at") val createdAt: String
)

data class DisputeResponse(
    val message: String,
    val dispute: DisputeRemoteModel?
)

data class BlockchainProofResponse(
    @SerializedName("lot_id") val lotId: String,
    val status: String,
    val category: String,
    @SerializedName("is_blockchain_verified") val isBlockchainVerified: Boolean,
    val network: String,
    @SerializedName("chain_id") val chainId: Int,
    @SerializedName("blockchain_tx_hash") val blockchainTxHash: String?,
    @SerializedName("blockchain_proof_hash") val blockchainProofHash: String?,
    @SerializedName("epr_token_id") val eprTokenId: String?,
    @SerializedName("explorer_url") val explorerUrl: String?,
    @SerializedName("statutory_compliance") val statutoryCompliance: String?
)

// ── Pools & Pool Chat ────────────────────────────────────────────────────────
data class PoolItem(
    val id: String,
    @SerializedName("admin_id") val adminId: String,
    val category: String,
    val status: String,
    @SerializedName("admin_name") val adminName: String,
    @SerializedName("simple_id") val simpleId: String,
    @SerializedName("is_admin") val isAdmin: Boolean,
    @SerializedName("total_weight") val totalWeight: Double,
    @SerializedName("lot_count") val lotCount: Int,
    @SerializedName("last_message") val lastMessage: String?,
    @SerializedName("last_message_at") val lastMessageAt: String?,
    @SerializedName("last_sender_id") val lastSenderId: String?,
    @SerializedName("message_count") val messageCount: Int,
    @SerializedName("created_at") val createdAt: String
)

data class MyPoolsResponse(
    val pools: List<PoolItem>
)

data class CreatePoolRequest(
    val category: String
)

data class PoolMessageItem(
    val id: String,
    @SerializedName("pool_id") val poolId: String,
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("sender_name") val senderName: String,
    val content: String,
    @SerializedName("created_at") val createdAt: String
)

// ── In-App Notifications ──────────────────────────────────────────────────────
data class RemoteNotification(
    val id: String,
    val title: String,
    val message: String,
    val type: String,
    @SerializedName("reference_id") val referenceId: String?,
    @SerializedName("is_read") val isRead: Boolean,
    @SerializedName("created_at") val createdAt: String
)

data class NotificationsResponse(
    val notifications: List<RemoteNotification>,
    @SerializedName("unread_count") val unreadCount: Int
)

