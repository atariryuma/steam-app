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
 * Input (controller) related DI module
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
