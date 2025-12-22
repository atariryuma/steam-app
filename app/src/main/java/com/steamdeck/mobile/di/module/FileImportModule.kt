package com.steamdeck.mobile.di.module

import com.steamdeck.mobile.data.repository.FileImportRepositoryImpl
import com.steamdeck.mobile.domain.repository.FileImportRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * File import related dependency injection module
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
