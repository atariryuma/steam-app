package com.steamdeck.mobile.domain.error

import org.junit.Assert.*
import org.junit.Test

/**
 * SteamSyncError unit tests
 *
 * Tests:
 * - Error type creation
 * - Message generation
 * - Type hierarchy
 */
class SteamSyncErrorTest {

    @Test
    fun `PrivateProfile has correct message`() {
        val error = SteamSyncError.PrivateProfile

        assertEquals("Steam profile is private", error.message)
    }

    @Test
    fun `AuthFailed has correct message`() {
        val error = SteamSyncError.AuthFailed

        assertEquals("Steam API authentication failed", error.message)
    }

    @Test
    fun `NetworkTimeout has correct message`() {
        val error = SteamSyncError.NetworkTimeout

        assertEquals("Network timeout", error.message)
    }

    @Test
    fun `ApiError has correct message with custom error`() {
        val error = SteamSyncError.ApiError("Rate limited")

        assertEquals("Steam API error: Rate limited", error.message)
    }

    @Test
    fun `ApiKeyNotConfigured has correct message`() {
        val error = SteamSyncError.ApiKeyNotConfigured

        assertEquals("Steam API Key not configured", error.message)
    }

    @Test
    fun `SteamSyncError is AppError`() {
        val error: SteamSyncError = SteamSyncError.PrivateProfile

        assertTrue(error is com.steamdeck.mobile.core.error.AppError)
    }

    @Test
    fun `PrivateProfile is data object singleton`() {
        val error1 = SteamSyncError.PrivateProfile
        val error2 = SteamSyncError.PrivateProfile

        assertSame(error1, error2)
    }

    @Test
    fun `ApiError with same message are equal`() {
        val error1 = SteamSyncError.ApiError("Test")
        val error2 = SteamSyncError.ApiError("Test")

        assertEquals(error1, error2)
    }
}
