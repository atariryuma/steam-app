package com.steamdeck.mobile.di.module

import com.steamdeck.mobile.data.repository.FileImportRepositoryImpl
import com.steamdeck.mobile.domain.repository.FileImportRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * fileインポート関連 依存性注入module
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FileImportModule {

 @Binds
 @Singleton
 abstract fun bindFileImportRepository(
  impl: FileImportRepositoryImpl
 ): FileImportRepository
}
