package com.sethg.app.data.remote

import com.sethg.app.data.local.SecureTokenStore
import com.sethg.app.data.remote.model.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

/**
 * OkHttp Authenticator that intercepts 401 responses, attempts to refresh the
 * access token using the stored refresh token, and retries the original request.
 * If refresh fails, tokens are cleared (user must log in again).
 */
class TokenAuthenticator @Inject constructor(
    private val tokenStore: SecureTokenStore,
    // Use a lazy provider to avoid circular dependency with OkHttpClient
    private val apiServiceProvider: dagger.Lazy<SethGApiService>
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Prevent infinite retry loops
        if (response.request.header("X-Token-Refreshed") != null) {
            tokenStore.clearAll()
            return null
        }

        val refreshToken = tokenStore.refreshToken ?: run {
            tokenStore.clearAll()
            return null
        }

        return runBlocking {
            try {
                val refreshResponse = apiServiceProvider.get().refresh(
                    RefreshRequest(refreshToken)
                )
                if (refreshResponse.isSuccessful) {
                    val body = refreshResponse.body()!!
                    tokenStore.accessToken  = body.accessToken
                    tokenStore.refreshToken = body.refreshToken
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${body.accessToken}")
                        .header("X-Token-Refreshed", "true")
                        .build()
                } else {
                    tokenStore.clearAll()
                    null
                }
            } catch (e: Exception) {
                tokenStore.clearAll()
                null
            }
        }
    }
}
