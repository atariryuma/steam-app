package com.steamdeck.mobile.di.module

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Application-level dependency injection module.
 * Provides named and global application dependencies.
 *
 * Best Practice (2025):
 * - Users provide their own Steam Web API Key
 * - No embedded API keys (security & compliance)
 * - Get API key at: https://steamcommunity.com/dev/apikey
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // No providers currently needed
    // This module is reserved for future application-level dependencies
}
