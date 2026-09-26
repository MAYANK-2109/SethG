package com.sethg.app.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ── Entities ──────────────────────────────────────────────────────────────────

@Entity(tableName = "cached_user")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String?,
    val email: String?,
    val photoUrl: String?,
    val language: String,
    val role: String,
    val certificateUrl: String?,
    val isVerified: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_earnings")
data class EarningsEntity(
    @PrimaryKey val period: String,   // "today" | "weekly" | "monthly"
    val total: Double,
    val transactionCount: Int,
    val rawJson: String,              // Full JSON for offline display
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "lots")
data class LotEntity(
    @PrimaryKey val lotId: String,    // e.g. "SG-260926-7KQ4"
    val category: String,             // MaterialCategory.name
    val weightKg: Double,
    val estimateLow: Int,
    val estimateHigh: Int,
    val priceRegion: String? = null,  // "Raipur" | zone name | "India" — whose rates priced it
    val lat: Double? = null,          // where the material is — used for 3–5 km recycler matching
    val lon: Double? = null,
    val handoverOtp: String? = null,  // 6-digit code from the server on acceptance; shown to the driver
    val status: String,               // "LISTED" → … → "PAID"
    val syncStatus: String,           // "PENDING" until uploaded
    val createdAt: Long = System.currentTimeMillis()
)

// One row per in-app camera photo, with the signed capture proof
@Entity(
    tableName = "lot_photos",
    foreignKeys = [ForeignKey(
        entity = LotEntity::class,
        parentColumns = ["lotId"],
        childColumns = ["lotId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("lotId")]
)
data class LotPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lotId: String,
    val filePath: String,
    val sha256: String,
    val capturedAt: Long,
    val nonce: String,
    val payload: String,              // "sha256|lotId|capturedAt|nonce|device"
    val signature: String,            // Base64 ECDSA signature from Android Keystore
    val aiVerdict: String,            // EWasteDetector.Verdict: EWASTE | UNCERTAIN
    val aiLabel: String?              // Top ML Kit label, e.g. "Computer"
)

data class LotWithPhotos(
    @Embedded val lot: LotEntity,
    @Relation(parentColumn = "lotId", entityColumn = "lotId")
    val photos: List<LotPhotoEntity>
)

// ── DAOs ──────────────────────────────────────────────────────────────────────

@Dao
interface UserDao {
    @Query("SELECT * FROM cached_user LIMIT 1")
    fun observeUser(): Flow<UserEntity?>

    @Query("SELECT * FROM cached_user LIMIT 1")
    suspend fun getUser(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: UserEntity)

    @Query("DELETE FROM cached_user")
    suspend fun clearUser()
}

@Dao
interface EarningsDao {
    @Query("SELECT * FROM cached_earnings WHERE period = :period")
    fun observeEarnings(period: String): Flow<EarningsEntity?>

    @Query("SELECT * FROM cached_earnings WHERE period = :period")
    suspend fun getEarnings(period: String): EarningsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEarnings(earnings: EarningsEntity)

    @Query("DELETE FROM cached_earnings")
    suspend fun clearAll()
}

@Dao
interface LotDao {
    @Transaction
    @Query("SELECT * FROM lots ORDER BY createdAt DESC")
    fun observeLots(): Flow<List<LotWithPhotos>>

    @Query("SELECT * FROM lots WHERE lotId = :lotId")
    fun observeLot(lotId: String): Flow<LotWithPhotos?>

    @Transaction
    @Query("SELECT * FROM lots WHERE syncStatus = 'PENDING'")
    suspend fun pendingSync(): List<LotWithPhotos>

    @Query("UPDATE lots SET syncStatus = :syncStatus WHERE lotId = :lotId")
    suspend fun setSyncStatus(lotId: String, syncStatus: String)

    @Query("UPDATE lots SET status = :status WHERE lotId = :lotId")
    suspend fun setStatus(lotId: String, status: String)

    @Query("UPDATE lots SET lat = :lat, lon = :lon WHERE lotId = :lotId")
    suspend fun setLocation(lotId: String, lat: Double, lon: Double)

    @Query("UPDATE lots SET handoverOtp = :otp WHERE lotId = :lotId")
    suspend fun setHandoverOtp(lotId: String, otp: String)

    @Query("SELECT filePath FROM lot_photos")
    suspend fun allPhotoPaths(): List<String>

    @Insert
    suspend fun insertLot(lot: LotEntity)

    @Insert
    suspend fun insertPhotos(photos: List<LotPhotoEntity>)

    @Transaction
    suspend fun insertLotWithPhotos(lot: LotEntity, photos: List<LotPhotoEntity>) {
        insertLot(lot)
        insertPhotos(photos)
    }
}

// ── Database ──────────────────────────────────────────────────────────────────

@Database(
    entities = [UserEntity::class, EarningsEntity::class, LotEntity::class, LotPhotoEntity::class],
    version = 5,
    exportSchema = true
)
abstract class SethGDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun earningsDao(): EarningsDao
    abstract fun lotDao(): LotDao
}
