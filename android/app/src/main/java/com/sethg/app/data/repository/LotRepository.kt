package com.sethg.app.data.repository

import android.content.Context
import com.sethg.app.data.local.CaptureSigner
import com.sethg.app.data.local.EWasteDetector
import com.sethg.app.data.local.LastLocation
import com.sethg.app.data.remote.SethGApiService
import com.sethg.app.data.remote.model.AcceptOfferResponse
import com.sethg.app.data.remote.model.ConfirmHandoverRequest
import com.sethg.app.data.remote.model.RemoteLot
import com.sethg.app.data.remote.model.SyncLotRequest
import com.sethg.app.data.remote.model.TransportRequest
import com.sethg.app.domain.model.Result
import com.sethg.app.work.LotAlertsWorker
import com.sethg.app.data.local.db.LotDao
import com.sethg.app.data.local.db.LotEntity
import com.sethg.app.data.local.db.LotPhotoEntity
import com.sethg.app.data.local.db.LotWithPhotos
import com.sethg.app.domain.model.CapturedPhoto
import com.sethg.app.domain.model.Lot
import com.sethg.app.domain.model.MaterialCategory
import com.sethg.app.domain.model.PriceEstimate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LotRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lotDao: LotDao,
    private val signer: CaptureSigner,
    private val eWasteDetector: EWasteDetector,
    private val api: SethGApiService,
    private val lastLocation: LastLocation
) {
    companion object {
        const val STATUS_LISTED = "LISTED"
        const val SYNC_PENDING  = "PENDING"
        const val SYNC_DONE     = "SYNCED"
        private const val ID_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O, 1/I
    }

    fun observeLots(): Flow<List<Lot>> = lotDao.observeLots().map { list -> list.map { it.toDomain() } }

    fun observeLot(lotId: String): Flow<Lot?> = lotDao.observeLot(lotId).map { it?.toDomain() }

    /** Offline-safe ID: date + random suffix, so two phones never collide. */
    fun newLotId(): String {
        val date   = SimpleDateFormat("yyMMdd", Locale.US).format(Date())
        val random = SecureRandom()
        val suffix = (1..4).map { ID_CHARS[random.nextInt(ID_CHARS.length)] }.joinToString("")
        return "SG-$date-$suffix"
    }

    /** Photos live in app-private storage — invisible to the gallery and other apps. */
    fun newPhotoFile(): File = File(photoDir(), "${UUID.randomUUID()}.jpg")

    private fun photoDir(): File = File(context.filesDir, "lot_photos").apply { mkdirs() }

    /**
     * Deletes photo files no lot refers to (drafts abandoned by a crash or force-stop).
     * Only files older than an hour, so a draft being captured right now is never touched.
     */
    suspend fun deleteOrphanPhotos(): Int = withContext(Dispatchers.IO) {
        val referenced = lotDao.allPhotoPaths().toSet()
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
        photoDir().listFiles().orEmpty()
            .filter { it.absolutePath !in referenced && it.lastModified() < cutoff }
            .count { it.delete() }
    }

    sealed class PhotoCheck {
        data class Accepted(val photo: CapturedPhoto) : PhotoCheck()
        data class NotEWaste(val detectedLabel: String?) : PhotoCheck()
    }

    /** Checks the photo shows e-waste, then signs it. Rejected photos are deleted. */
    suspend fun checkAndSealPhoto(lotId: String, file: File): PhotoCheck = withContext(Dispatchers.IO) {
        val check = eWasteDetector.check(file)
        if (check.verdict == EWasteDetector.Verdict.NOT_EWASTE) {
            file.delete()
            // Only name what was seen when ML Kit made the call; our model has no object names
            return@withContext PhotoCheck.NotEWaste(check.topLabel.takeIf { check.eWasteScore == null })
        }
        val proof = signer.seal(file, lotId)
        PhotoCheck.Accepted(
            CapturedPhoto(
                filePath   = file.absolutePath,
                sha256     = proof.sha256,
                capturedAt = proof.capturedAt,
                nonce      = proof.nonce,
                payload    = proof.payload,
                signature  = proof.signature,
                aiVerdict  = check.verdict.name,
                aiLabel    = listOfNotNull(
                    check.topLabel,
                    check.eWasteScore?.let { "e-waste ${(it * 100).roundToInt()}%" }
                ).joinToString(" · ").ifEmpty { null }
            )
        )
    }

    suspend fun deletePhotoFiles(photos: List<CapturedPhoto>) = withContext(Dispatchers.IO) {
        photos.forEach { File(it.filePath).delete() }
    }

    suspend fun createLot(
        lotId: String,
        category: MaterialCategory,
        weightKg: Double,
        estimate: PriceEstimate,
        priceRegion: String?,
        photos: List<CapturedPhoto>
    ) = withContext(Dispatchers.IO) {
        val here = lastLocation.fresh()
        lotDao.insertLotWithPhotos(
            LotEntity(
                lotId        = lotId,
                category     = category.name,
                weightKg     = weightKg,
                estimateLow  = estimate.low,
                estimateHigh = estimate.high,
                priceRegion  = priceRegion,
                lat          = here?.latitude,
                lon          = here?.longitude,
                status       = STATUS_LISTED,
                syncStatus   = SYNC_PENDING
            ),
            photos.map {
                LotPhotoEntity(
                    lotId      = lotId,
                    filePath   = it.filePath,
                    sha256     = it.sha256,
                    capturedAt = it.capturedAt,
                    nonce      = it.nonce,
                    payload    = it.payload,
                    signature  = it.signature,
                    aiVerdict  = it.aiVerdict,
                    aiLabel    = it.aiLabel
                )
            }
        )
        LotAlertsWorker.runNow(context)   // upload now, or as soon as there is network
    }

    // ── Server sync: lots → recyclers within 3–5 km ──────────────────────────

    /** Uploads lots saved offline. Lots without a location get one now if possible. Returns how many synced. */
    suspend fun syncPending(): Int = withContext(Dispatchers.IO) {
        var synced = 0
        for (pending in lotDao.pendingSync()) {
            val lot = pending.lot
            var lat = lot.lat
            var lon = lot.lon
            if (lat == null || lon == null) {
                val here = lastLocation.fresh() ?: continue          // can't match without a location
                lat = here.latitude; lon = here.longitude
                lotDao.setLocation(lot.lotId, lat, lon)
            }
            val photoUrl = pending.photos.firstOrNull()?.filePath?.let { encodePhotoFile(it) }
            val response = runCatching {
                api.syncLot(
                    SyncLotRequest(
                        id = lot.lotId, category = lot.category, weightKg = lot.weightKg,
                        estimateLow = lot.estimateLow, estimateHigh = lot.estimateHigh,
                        priceRegion = lot.priceRegion, lat = lat, lon = lon,
                        photoHashes = pending.photos.map { it.sha256 },
                        photoUrl = photoUrl
                    )
                )
            }.getOrNull() ?: break                                     // offline: try again later
            if (response.isSuccessful) {
                lotDao.setSyncStatus(lot.lotId, SYNC_DONE)
                synced++
            }
        }
        syncConfirmations()
        synced
    }

    private fun encodePhotoFile(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val maxDim = 800
            val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val (w, h) = if (ratio > 1) Pair(maxDim, (maxDim / ratio).toInt()) else Pair((maxDim * ratio).toInt(), maxDim)
                android.graphics.Bitmap.createScaledBitmap(bitmap, w, h, true)
            } else bitmap
            val stream = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, stream)
            val bytes = stream.toByteArray()
            "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    // ── Stage 4: vendor confirms the handover with the recycler's code ──────

    enum class CodeCheck { OK, WRONG, NOT_AVAILABLE }

    /**
     * Checks the code shown on the recycler's phone against the hash kept on this
     * phone — no network needed. A right code is saved and sent to the server at sync.
     */
    suspend fun confirmHandover(lotId: String, code: String): CodeCheck = withContext(Dispatchers.IO) {
        val hash = lotDao.observeLot(lotId).first()?.lot?.otpHash ?: return@withContext CodeCheck.NOT_AVAILABLE
        if (sha256("$lotId:$code") != hash) return@withContext CodeCheck.WRONG
        lotDao.setConfirmation(lotId, code, System.currentTimeMillis())
        LotAlertsWorker.runNow(context)   // send now, or as soon as there is network
        CodeCheck.OK
    }

    /** Sends confirmations entered offline; the server checks the code again. */
    private suspend fun syncConfirmations() {
        val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        for (lot in lotDao.pendingConfirmations()) {
            val otp = lot.confirmOtp ?: continue
            val at = utc.format(Date(lot.confirmedAt ?: System.currentTimeMillis()))
            val response = runCatching { api.confirmHandover(lot.lotId, ConfirmHandoverRequest(otp, at)) }.getOrNull()
                ?: return                                              // offline: try again later
            when {
                response.isSuccessful -> {
                    lotDao.setConfirmation(lot.lotId, null, lot.confirmedAt)   // sent; keep the time
                    response.body()?.lot?.let { lotDao.setStatus(it.id, it.status) }
                }
                response.code() == 403 || response.code() == 404 ->
                    lotDao.setConfirmation(lot.lotId, null, null)      // server refused: ask for the code again
            }
        }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Pulls offers / schedule / handover for this collector's lots and mirrors the status locally. */
    suspend fun refreshFromServer(): Result<List<RemoteLot>> = withContext(Dispatchers.IO) { try {
        val response = api.myLots()
        val lots = response.body()?.lots
        if (response.isSuccessful && lots != null) {
            lots.forEach { lot ->
                lotDao.setStatus(lot.id, lot.status)
                lot.handoverOtpHash?.let { lotDao.setOtpHash(lot.id, it) }
            }
            Result.Success(lots)
        } else Result.Error("Could not load offers", response.code())
    } catch (e: Exception) {
        Result.Error(e.localizedMessage ?: "Network error")
    } }

    /** Accepting locks the price; keeps the handover code's hash so the code can be checked offline. */
    suspend fun acceptOffer(lotId: String, offerId: String): Result<AcceptOfferResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.acceptOffer(lotId, offerId)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                body.lot.handoverOtpHash?.let { lotDao.setOtpHash(lotId, it) }
                lotDao.setStatus(lotId, body.lot.status)
                Result.Success(body)
            } else Result.Error(errorMessage(response.errorBody()?.string()) ?: "Could not accept offer", response.code())
        } catch (e: Exception) {
            Result.Error(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun chooseTransport(lotId: String, mode: String, hubId: String?): Result<RemoteLot> = withContext(Dispatchers.IO) {
        try {
            val response = api.chooseTransport(lotId, TransportRequest(mode, hubId))
            val lot = response.body()?.lot
            if (response.isSuccessful && lot != null) {
                lotDao.setStatus(lotId, lot.status)
                Result.Success(lot)
            } else Result.Error(errorMessage(response.errorBody()?.string()) ?: "Could not save choice", response.code())
        } catch (e: Exception) {
            Result.Error(e.localizedMessage ?: "Network error")
        }
    }
}

// ── Mapper extensions ─────────────────────────────────────────────────────────

private fun LotWithPhotos.toDomain() = Lot(
    lotId      = lot.lotId,
    // Unknown names (e.g. a lot saved by a newer app version) fall back to OTHER instead of crashing
    category   = MaterialCategory.entries.firstOrNull { it.name == lot.category } ?: MaterialCategory.OTHER,
    weightKg   = lot.weightKg,
    estimate   = PriceEstimate(lot.estimateLow, lot.estimateHigh),
    priceRegion = lot.priceRegion,
    status     = lot.status,
    syncStatus = lot.syncStatus,
    canCheckCode = lot.otpHash != null,
    confirmedAt = lot.confirmedAt,
    confirmPending = lot.confirmOtp != null,
    createdAt  = lot.createdAt,
    photos     = photos.map {
        CapturedPhoto(
            it.filePath, it.sha256, it.capturedAt, it.nonce, it.payload, it.signature,
            it.aiVerdict, it.aiLabel
        )
    }
)

/** Pulls {"error": "..."} out of an API error body. */
internal fun errorMessage(body: String?): String? =
    body?.let { Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
