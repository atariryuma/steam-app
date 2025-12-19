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
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * ネットワーク関連の依存性注入モジュール
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
     * 全てのRetrofit APIコールで自動的にDataResult<T>でラップ
     */
    @Provides
    @Singleton
    fun provideDataResultCallAdapterFactory(): DataResultCallAdapterFactory {
        return DataResultCallAdapterFactory()
    }

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

                // デバッグビルドのみログ出力を有効化（本番環境では無効）
                // セキュリティベストプラクティス: API KeyやTokenがログに露出しないようにする
                if (com.steamdeck.mobile.BuildConfig.DEBUG) {
                    val loggingInterceptor = HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    }
                    addInterceptor(loggingInterceptor)
                }
            }
            // タイムアウト設定
            .connectTimeout(30, TimeUnit.SECONDS)      // 接続タイムアウト
            .readTimeout(60, TimeUnit.SECONDS)         // 読み取りタイムアウト
            .writeTimeout(60, TimeUnit.SECONDS)        // 書き込みタイムアウト
            // リトライ設定
            .retryOnConnectionFailure(true)            // 接続失敗時の自動リトライ
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
