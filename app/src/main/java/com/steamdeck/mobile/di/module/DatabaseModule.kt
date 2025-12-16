package com.steamdeck.mobile.di.module

import android.content.Context
import androidx.room.Room
import com.steamdeck.mobile.data.local.database.SteamDeckDatabase
import com.steamdeck.mobile.data.local.database.dao.DownloadDao
import com.steamdeck.mobile.data.local.database.dao.GameDao
import com.steamdeck.mobile.data.local.database.dao.WinlatorContainerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * データベース関連の依存性注入モジュール
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * SteamDeckDatabaseインスタンスを提供
     */
    @Provides
    @Singleton
    fun provideSteamDeckDatabase(
        @ApplicationContext context: Context
    ): SteamDeckDatabase {
        return Room.databaseBuilder(
            context,
            SteamDeckDatabase::class.java,
            SteamDeckDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration() // MVP段階では破壊的マイグレーション許可
            .build()
    }

    /**
     * GameDaoを提供
     */
    @Provides
    @Singleton
    fun provideGameDao(database: SteamDeckDatabase): GameDao {
        return database.gameDao()
    }

    /**
     * WinlatorContainerDaoを提供
     */
    @Provides
    @Singleton
    fun provideWinlatorContainerDao(database: SteamDeckDatabase): WinlatorContainerDao {
        return database.winlatorContainerDao()
    }

    /**
     * DownloadDaoを提供
     */
    @Provides
    @Singleton
    fun provideDownloadDao(database: SteamDeckDatabase): DownloadDao {
        return database.downloadDao()
    }
}
