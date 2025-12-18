package com.steamdeck.mobile.di.module

import android.content.Context
import com.steamdeck.mobile.core.input.GameControllerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 入力（コントローラー）関連のDIモジュール
 */
@Module
@InstallIn(SingletonComponent::class)
object InputModule {

    @Provides
    @Singleton
    fun provideGameControllerManager(
        @ApplicationContext context: Context
    ): GameControllerManager {
        return GameControllerManager(context)
    }
}
