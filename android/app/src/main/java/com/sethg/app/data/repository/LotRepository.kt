package com.sethg.app.data.repository

import android.content.Context
import com.sethg.app.data.local.CaptureSigner
import com.sethg.app.data.local.EWasteDetector
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LotRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lotDao: LotDao,
    private val signer: CaptureSigner,
    private val eWasteDetector: EWasteDetector
) {
    companion object {
        const val STATUS_LISTED = "LISTED"
        const val SYNC_PENDING  = "PENDING"
        private const val ID_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O, 1/I
    }

    fun observeLots(): Flow<List<Lot>> = lotDao.observeLots().map { list -> list.map { it.toDomain() } }

    /** Offline-safe ID: date + random suffix, so two phones never collide. */
    fun newLotId(): String {
        val date   = SimpleDateFormat("yyMMdd", Locale.US).format(Date())
        val random = SecureRandom()
        val suffix = (1..4).map { ID_CHARS[random.nextInt(ID_CHARS.length)] }.joinToString("")
        return "SG-$date-$suffix"
    }

    /** Photos live in app-private storage — invisible to the gallery and other apps. */
    fun newPhotoFile(): File {
        val dir = File(context.filesDir, "lot_photos").apply { mkdirs() }
        return File(dir, "${UUID.randomUUID()}.jpg")
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
            return@withContext PhotoCheck.NotEWaste(check.topLabel)
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
                aiLabel    = check.topLabel
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
        photos: List<CapturedPhoto>
    ) = withContext(Dispatchers.IO) {
        lotDao.insertLotWithPhotos(
            LotEntity(
                lotId        = lotId,
                category     = category.name,
                weightKg     = weightKg,
                estimateLow  = estimate.low,
                estimateHigh = estimate.high,
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
    }
}

// ── Mapper extensions ─────────────────────────────────────────────────────────

private fun LotWithPhotos.toDomain() = Lot(
    lotId      = lot.lotId,
    category   = MaterialCategory.valueOf(lot.category),
    weightKg   = lot.weightKg,
    estimate   = PriceEstimate(lot.estimateLow, lot.estimateHigh),
    status     = lot.status,
    syncStatus = lot.syncStatus,
    createdAt  = lot.createdAt,
    photos     = photos.map {
        CapturedPhoto(
            it.filePath, it.sha256, it.capturedAt, it.nonce, it.payload, it.signature,
            it.aiVerdict, it.aiLabel
        )
    }
)
