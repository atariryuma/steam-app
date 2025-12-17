package com.steamdeck.mobile.di.module

import android.content.Context
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.steamdeck.mobile.data.remote.steam.SteamApiService
import com.steamdeck.mobile.data.remote.steam.SteamCdnService
import com.steamdeck.mobile.data.remote.steam.SteamCmdApiService
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
            // ベストプラクティス: 大容量ダウンロード（Manifest、Chunk）を考慮
            .connectTimeout(30, TimeUnit.SECONDS)      // 接続タイムアウト（変更なし）
            .readTimeout(5, TimeUnit.MINUTES)          // 読み取りタイムアウト（30秒→5分）
            .writeTimeout(5, TimeUnit.MINUTES)         // 書き込みタイムアウト（30秒→5分）
            .callTimeout(10, TimeUnit.MINUTES)         // コール全体のタイムアウト（新規追加）
            // リトライ設定
            .retryOnConnectionFailure(true)            // 接続失敗時の自動リトライ
            // 接続プール設定
            // 同時に最大5接続を維持、アイドル接続は5分後にクローズ
            .connectionPool(
                okhttp3.ConnectionPool(
                    maxIdleConnections = 5,
                    keepAliveDuration = 5,
                    timeUnit = TimeUnit.MINUTES
                )
            )
            // キャッシュ設定
            // Note: Manifestやゲームチャンクは頻繁に変わらないためキャッシュ有効
            .cache(null)  // キャッシュは無効（Steam CDNはHTTPキャッシュヘッダーを持たないため）
            .build()
    }

    @Provides
    @Singleton
    fun provideSteamApiService(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): SteamApiService {
        return Retrofit.Builder()
            .baseUrl(SteamApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SteamApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideSteamCdnService(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): SteamCdnService {
        return Retrofit.Builder()
            .baseUrl(SteamCdnService.CDN_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SteamCdnService::class.java)
    }

    @Provides
    @Singleton
    fun provideSteamCmdApiService(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): SteamCmdApiService {
        return Retrofit.Builder()
            .baseUrl(SteamCmdApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SteamCmdApiService::class.java)
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
