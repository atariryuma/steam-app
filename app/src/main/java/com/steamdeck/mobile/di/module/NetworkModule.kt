package com.steamdeck.mobile.di.module

import android.content.Context
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.steamdeck.mobile.core.network.DataResultCallAdapterFactory
import com.steamdeck.mobile.data.remote.steam.SteamApiService
import com.steamdeck.mobile.data.remote.steam.SteamRepository
import com.steamdeck.mobile.data.remote.steam.SteamRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Network-related dependency injection module
 *
 * Best Practice (2025):
 * - DataResultCallAdapterFactory for automatic error handling
 * - Unified error handling across all API calls
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

 @Provides
 @Singleton
 fun provideGson(): Gson {
  return GsonBuilder()
   .setLenient()
   .create()
 }

 /**
  * DataResult CallAdapter Factory
  *
  * Automatically wraps all Retrofit API calls with DataResult<T>
  */
 @Provides
 @Singleton
 fun provideDataResultCallAdapterFactory(): DataResultCallAdapterFactory {
  return DataResultCallAdapterFactory()
 }

 /**
  * OkHttp Client with GitHub research-backed optimizations (2025)
  *
  * Optimizations:
  * - Connection pooling (5 connections, 5min keep-alive) for reduced latency
  * - Optimized timeouts for large file downloads
  * - Automatic retry on connection failure
  */
 @Provides
 @Singleton
 fun provideOkHttpClient(): OkHttpClient {
  return OkHttpClient.Builder()
   .apply {
    // User-Agent header for Steam API
    addInterceptor { chain ->
     val request = chain.request().newBuilder()
      .header("User-Agent", "SteamDeckMobile/0.1.0 (Android)")
      .build()
     chain.proceed(request)
    }

    // Enable logging only for debug builds (disabled in production)
    // Security best practice: Prevent API keys/tokens from appearing in logs
    // NOTE: Using HEADERS instead of BODY to prevent OOM on large file downloads (130MB+)
    if (com.steamdeck.mobile.BuildConfig.DEBUG) {
     val loggingInterceptor = HttpLoggingInterceptor().apply {
      level = HttpLoggingInterceptor.Level.HEADERS
     }
     addInterceptor(loggingInterceptor)
    }
   }
   // NEW: Connection pooling optimization (GitHub research)
   // Max 5 idle connections, 5 minute keep-alive
   // Reduces latency by reusing connections to same host
   .connectionPool(
    ConnectionPool(
     maxIdleConnections = 5,
     keepAliveDuration = 5,
     timeUnit = TimeUnit.MINUTES
    )
   )
   // Timeout settings (optimized for large downloads)
   .connectTimeout(30, TimeUnit.SECONDS)  // Connection timeout
   .readTimeout(90, TimeUnit.SECONDS)   // Read timeout (increased for large files)
   .writeTimeout(90, TimeUnit.SECONDS)  // Write timeout (increased for large files)
   // Retry settings
   .retryOnConnectionFailure(true)   // Auto-retry on connection failure
   .build()
 }

 @Provides
 @Singleton
 fun provideSteamApiService(
  okHttpClient: OkHttpClient,
  gson: Gson,
  callAdapterFactory: DataResultCallAdapterFactory
 ): SteamApiService {
  return Retrofit.Builder()
   .baseUrl(SteamApiService.BASE_URL)
   .client(okHttpClient)
   .addCallAdapterFactory(callAdapterFactory)
   .addConverterFactory(GsonConverterFactory.create(gson))
   .build()
   .create(SteamApiService::class.java)
 }

 @Provides
 @Singleton
 fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
  return WorkManager.getInstance(context)
 }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SteamModule {
 @Binds
 @Singleton
 abstract fun bindSteamRepository(
  steamRepositoryImpl: SteamRepositoryImpl
 ): SteamRepository
}
