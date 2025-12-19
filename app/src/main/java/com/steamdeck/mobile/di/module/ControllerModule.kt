package com.steamdeck.mobile.di.module

import android.content.Context
import com.steamdeck.mobile.data.local.database.SteamDeckDatabase
import com.steamdeck.mobile.data.local.database.dao.ControllerProfileDao
import com.steamdeck.mobile.data.repository.ControllerRepositoryImpl
import com.steamdeck.mobile.domain.repository.ControllerRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for controller-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object ControllerModule {

 @Provides
 @Singleton
 fun provideControllerProfileDao(
  database: SteamDeckDatabase
 ): ControllerProfileDao {
  return database.controllerProfileDao()
 }

 @Provides
 @Singleton
 fun provideControllerRepository(
  @ApplicationContext context: Context,
  controllerProfileDao: ControllerProfileDao
 ): ControllerRepository {
  return ControllerRepositoryImpl(context, controllerProfileDao)
 }
}
