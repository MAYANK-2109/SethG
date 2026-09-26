package com.sethg.app.data.repository

import com.google.gson.Gson
import com.sethg.app.data.local.SecureTokenStore
import com.sethg.app.data.local.db.EarningsDao
import com.sethg.app.data.local.db.EarningsEntity
import com.sethg.app.data.local.db.UserDao
import com.sethg.app.data.local.db.UserEntity
import com.sethg.app.data.remote.SethGApiService
import com.sethg.app.data.remote.model.LoginRequest
import com.sethg.app.data.remote.model.RegisterRequest
import com.sethg.app.data.remote.model.UpdateProfileRequest
import com.sethg.app.domain.model.EarningsSummary
import com.sethg.app.domain.model.Result
import com.sethg.app.domain.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: SethGApiService,
    private val tokenStore: SecureTokenStore,
    private val userDao: UserDao
) {
    val isLoggedIn: Boolean get() = tokenStore.accessToken != null

    suspend fun register(
        name: String, phone: String?, email: String?,
        password: String, language: String, role: String
    ): Result<User> = withContext(Dispatchers.IO) {
        try {
            val response = api.register(
                RegisterRequest(name, phone, email, password, password, language, role)
            )
            if (response.isSuccessful) {
                val body = response.body()!!
                tokenStore.accessToken  = body.accessToken
                tokenStore.refreshToken = body.refreshToken
                val user = body.user.toDomain()
                userDao.upsertUser(body.user.toEntity())
                Result.Success(user)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Registration failed", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun login(
        phone: String?, email: String?, password: String
    ): Result<User> = withContext(Dispatchers.IO) {
        try {
            val response = api.login(LoginRequest(phone, email, password))
            if (response.isSuccessful) {
                val body = response.body()!!
                tokenStore.accessToken  = body.accessToken
                tokenStore.refreshToken = body.refreshToken
                val user = body.user.toDomain()
                userDao.upsertUser(body.user.toEntity())
                Result.Success(user)
            } else {
                Result.Error("Invalid credentials", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        try {
            val rt = tokenStore.refreshToken
            if (rt != null) {
                api.logout(com.sethg.app.data.remote.model.RefreshRequest(rt))
            }
        } finally {
            tokenStore.clearAll()
            userDao.clearUser()
        }
    }
}

@Singleton
class UserRepository @Inject constructor(
    private val api: SethGApiService,
    private val userDao: UserDao
) {
    fun observeUser(): Flow<User?> = userDao.observeUser().map { it?.toDomain() }

    suspend fun getProfile(): Result<User> = withContext(Dispatchers.IO) {
        try {
            val response = api.getProfile()
            if (response.isSuccessful) {
                val user = response.body()!!.user
                userDao.upsertUser(user.toEntity())
                Result.Success(user.toDomain())
            } else {
                // Fall back to cache
                val cached = userDao.getUser()?.toDomain()
                if (cached != null) Result.Success(cached)
                else Result.Error("Failed to load profile", response.code())
            }
        } catch (e: Exception) {
            val cached = userDao.getUser()?.toDomain()
            if (cached != null) Result.Success(cached)
            else Result.Error(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun updateProfile(
        name: String? = null, phone: String? = null, email: String? = null,
        language: String? = null, currentPassword: String? = null, newPassword: String? = null,
        certificateUrl: String? = null, role: String? = null
    ): Result<User> = withContext(Dispatchers.IO) {
        try {
            // Optimistic local update for role so the dashboard shows immediately
            if (role != null) {
                val cached = userDao.getUser()
                if (cached != null) userDao.upsertUser(cached.copy(role = role))
            }
            val response = api.updateProfile(
                UpdateProfileRequest(name, phone, email, language,
                    currentPassword = currentPassword, newPassword = newPassword,
                    certificateUrl = certificateUrl, role = role)
            )
            if (response.isSuccessful) {
                val user = response.body()!!.user
                userDao.upsertUser(user.toEntity())
                Result.Success(user.toDomain())
            } else {
                Result.Error(response.errorBody()?.string() ?: "Update failed", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.localizedMessage ?: "Network error")
        }
    }
}

@Singleton
class EarningsRepository @Inject constructor(
    private val api: SethGApiService,
    private val earningsDao: EarningsDao,
    private val gson: Gson
) {
    fun observeEarnings(period: String): Flow<EarningsSummary?> =
        earningsDao.observeEarnings(period).map { it?.toDomain() }

    suspend fun fetchToday(): Result<EarningsSummary> = fetch("today") { api.getEarningsToday() }
    suspend fun fetchWeekly(): Result<EarningsSummary> = fetch("weekly") { api.getEarningsWeekly() }
    suspend fun fetchMonthly(): Result<EarningsSummary> = fetch("monthly") { api.getEarningsMonthly() }

    private suspend fun fetch(
        period: String,
        call: suspend () -> retrofit2.Response<com.sethg.app.data.remote.model.EarningsSummary>
    ): Result<EarningsSummary> = withContext(Dispatchers.IO) {
        try {
            val response = call()
            if (response.isSuccessful) {
                val body = response.body()!!
                earningsDao.upsertEarnings(
                    EarningsEntity(
                        period           = period,
                        total            = body.total,
                        transactionCount = body.transactionCount,
                        rawJson          = gson.toJson(body)
                    )
                )
                Result.Success(body.toDomain())
            } else {
                val cached = earningsDao.getEarnings(period)?.toDomain()
                if (cached != null) Result.Success(cached)
                else Result.Error("Failed to load earnings", response.code())
            }
        } catch (e: Exception) {
            val cached = earningsDao.getEarnings(period)?.toDomain()
            if (cached != null) Result.Success(cached)
            else Result.Error(e.localizedMessage ?: "Network error")
        }
    }
}

// ── Mapper extensions ─────────────────────────────────────────────────────────

private fun com.sethg.app.data.remote.model.RemoteUser.toDomain() = User(
    id = id, name = name, phone = phone, email = email,
    photoUrl = photoUrl, language = language,
    role = role ?: "user",
    certificateUrl = certificateUrl,
    isVerified = isVerified ?: false
)

private fun com.sethg.app.data.remote.model.RemoteUser.toEntity() = UserEntity(
    id = id, name = name, phone = phone, email = email,
    photoUrl = photoUrl, language = language,
    role = role ?: "user",
    certificateUrl = certificateUrl,
    isVerified = isVerified ?: false
)

private fun UserEntity.toDomain() = User(
    id = id, name = name, phone = phone, email = email,
    photoUrl = photoUrl, language = language,
    role = role,
    certificateUrl = certificateUrl,
    isVerified = isVerified
)

private fun com.sethg.app.data.remote.model.EarningsSummary.toDomain() = EarningsSummary(
    period = period, total = total, transactionCount = transactionCount
)

private fun EarningsEntity.toDomain() = EarningsSummary(
    period = period, total = total, transactionCount = transactionCount
)
