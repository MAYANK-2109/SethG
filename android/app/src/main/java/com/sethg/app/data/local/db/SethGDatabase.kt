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

// ── Database ──────────────────────────────────────────────────────────────────

@Database(
    entities = [UserEntity::class, EarningsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SethGDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun earningsDao(): EarningsDao
}
