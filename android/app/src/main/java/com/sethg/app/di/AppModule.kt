package com.sethg.app.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.sethg.app.BuildConfig
import com.sethg.app.data.local.SecureTokenStore
import com.sethg.app.data.local.db.EarningsDao
import com.sethg.app.data.local.db.SethGDatabase
import com.sethg.app.data.local.db.UserDao
import com.sethg.app.data.remote.SethGApiService
import com.sethg.app.data.remote.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Gson ──────────────────────────────────────────────────────────────────
    @Provides @Singleton
    fun provideGson(): Gson = Gson()

    // ── OkHttp ────────────────────────────────────────────────────────────────
    @Provides @Singleton
    fun provideOkHttpClient(
        tokenStore: SecureTokenStore,
        authenticator: TokenAuthenticator
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }

        val authInterceptor = Interceptor { chain ->
            val token = tokenStore.accessToken
            val request = if (token != null) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else chain.request()
            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .authenticator(authenticator)
            // Conservative timeouts for low-bandwidth environments
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ── Retrofit ──────────────────────────────────────────────────────────────
    @Provides @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides @Singleton
    fun provideApiService(retrofit: Retrofit): SethGApiService =
        retrofit.create(SethGApiService::class.java)

    // ── Room ──────────────────────────────────────────────────────────────────
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SethGDatabase =
        Room.databaseBuilder(context, SethGDatabase::class.java, "sethg.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideUserDao(db: SethGDatabase): UserDao = db.userDao()
    @Provides fun provideEarningsDao(db: SethGDatabase): EarningsDao = db.earningsDao()
}
