package com.steamdeck.mobile.di.module

import android.content.Context
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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
                // デバッグビルドのみログ出力を有効化（本番環境では無効）
                // セキュリティベストプラクティス: API KeyやTokenがログに露出しないようにする
                if (com.steamdeck.mobile.BuildConfig.DEBUG) {
                    val loggingInterceptor = HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    }
                    addInterceptor(loggingInterceptor)
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
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
