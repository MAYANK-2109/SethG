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
    version = 2,
    exportSchema = true
)
abstract class SethGDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun earningsDao(): EarningsDao
    abstract fun lotDao(): LotDao
}
