package com.steamdeck.mobile.di.module

import com.steamdeck.mobile.data.local.preferences.SecurePreferencesImpl
import com.steamdeck.mobile.data.remote.steam.SteamRepository
import com.steamdeck.mobile.data.remote.steam.SteamRepositoryImpl
import com.steamdeck.mobile.data.repository.ControllerRepositoryImpl
import com.steamdeck.mobile.data.repository.DownloadRepositoryImpl
import com.steamdeck.mobile.data.repository.FileImportRepositoryImpl
import com.steamdeck.mobile.data.repository.GameRepositoryImpl
import com.steamdeck.mobile.data.repository.SteamRepositoryAdapter
import com.steamdeck.mobile.data.repository.WinlatorContainerRepositoryImpl
import com.steamdeck.mobile.domain.repository.ControllerRepository
import com.steamdeck.mobile.domain.repository.DownloadRepository
import com.steamdeck.mobile.domain.repository.FileImportRepository
import com.steamdeck.mobile.domain.repository.GameRepository
import com.steamdeck.mobile.domain.repository.ISecurePreferences
import com.steamdeck.mobile.domain.repository.ISteamRepository
import com.steamdeck.mobile.domain.repository.WinlatorContainerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * リポジトリ 依存性注入module
 * Clean Architecture: Domain interfaces bound to data layer implementations
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
 /**
  * GameRepository implementationbind
  */
 @Binds
 @Singleton
 abstract fun bindGameRepository(
  gameRepositoryImpl: GameRepositoryImpl
 ): GameRepository

 /**
  * WinlatorContainerRepository implementationbind
  */
 @Binds
 @Singleton
 abstract fun bindWinlatorContainerRepository(
  winlatorContainerRepositoryImpl: WinlatorContainerRepositoryImpl
 ): WinlatorContainerRepository

 /**
  * DownloadRepository implementationbind
  */
 @Binds
 @Singleton
 abstract fun bindDownloadRepository(
  downloadRepositoryImpl: DownloadRepositoryImpl
 ): DownloadRepository

 /**
  * FileImportRepository implementationbind
  */
 @Binds
 @Singleton
 abstract fun bindFileImportRepository(
  fileImportRepositoryImpl: FileImportRepositoryImpl
 ): FileImportRepository

 // ControllerRepository binding removed - provided by ControllerModule

 /**
  * ISecurePreferences implementationbind
  * Clean Architecture: Domain interface for secure storage
  */
 @Binds
 @Singleton
 abstract fun bindSecurePreferences(
  securePreferencesImpl: SecurePreferencesImpl
 ): ISecurePreferences

 // SteamRepository binding removed - provided by SteamModule in NetworkModule

 /**
  * ISteamRepository implementationbind
  * Clean Architecture: Domain interface adapter for Steam operations
  */
 @Binds
 @Singleton
 abstract fun bindISteamRepository(
  steamRepositoryAdapter: SteamRepositoryAdapter
 ): ISteamRepository
}
