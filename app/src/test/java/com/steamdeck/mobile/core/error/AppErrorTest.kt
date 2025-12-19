package com.steamdeck.mobile.core.error

import org.junit.Assert.*
import org.junit.Test
import java.io.FileNotFoundException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * AppError unit tests
 *
 * Tests:
 * - Error type creation
 * - HTTP code mapping
 * - Throwable conversion
 * - Retryability detection
 */
class AppErrorTest {

    @Test
    fun `NetworkError creates correct message for 401`() {
        val error = AppError.NetworkError(401)

        assertEquals("認証エラー (code: 401)", error.message)
        assertFalse(error.isRetryable())
    }

    @Test
    fun `NetworkError creates correct message for 403`() {
        val error = AppError.NetworkError(403)

        assertEquals("アクセス拒否 (code: 403)", error.message)
        assertFalse(error.isRetryable())
    }

    @Test
    fun `NetworkError creates correct message for 404`() {
        val error = AppError.NetworkError(404)

        assertEquals("リソースが見つかりません (code: 404)", error.message)
        assertFalse(error.isRetryable())
    }

    @Test
    fun `NetworkError creates correct message for 429`() {
        val error = AppError.NetworkError(429)

        assertEquals("レート制限 (code: 429)", error.message)
        assertTrue(error.isRetryable())
    }

    @Test
    fun `NetworkError creates correct message for 500`() {
        val error = AppError.NetworkError(500)

        assertEquals("サーバーエラー (code: 500)", error.message)
        assertTrue(error.isRetryable())
    }

    @Test
    fun `fromHttpCode creates correct error for 401`() {
        val error = AppError.fromHttpCode(401, "Unauthorized")

        assertTrue(error is AppError.AuthError)
        assertTrue(error.message.contains("Unauthorized"))
    }

    @Test
    fun `fromHttpCode creates correct error for 403`() {
        val error = AppError.fromHttpCode(403, "Forbidden")

        assertTrue(error is AppError.AuthError)
        assertTrue(error.message.contains("Forbidden"))
    }

    @Test
    fun `fromHttpCode creates retryable error for 500`() {
        val error = AppError.fromHttpCode(500, "Internal Server Error")

        assertTrue(error is AppError.NetworkError)
        assertTrue(error.isRetryable())
    }

    @Test
    fun `from converts UnknownHostException to NetworkError`() {
        val exception = UnknownHostException("api.example.com")
        val error = AppError.from(exception)

        assertTrue(error is AppError.NetworkError)
        assertTrue(error.isRetryable())
    }

    @Test
    fun `from converts SocketTimeoutException to TimeoutError`() {
        val exception = SocketTimeoutException("Read timed out")
        val error = AppError.from(exception)

        assertTrue(error is AppError.TimeoutError)
        assertTrue(error.isRetryable())
    }

    @Test
    fun `from converts FileNotFoundException to FileError`() {
        val exception = FileNotFoundException("/path/to/file")
        val error = AppError.from(exception)

        assertTrue(error is AppError.FileError)
        assertFalse(error.isRetryable())
    }

    @Test
    fun `from preserves AppError type`() {
        val originalError = AppError.AuthError("Test")
        val converted = AppError.from(originalError)

        assertSame(originalError, converted)
    }

    @Test
    fun `AuthError is not retryable`() {
        val error = AppError.AuthError("Test")

        assertFalse(error.isRetryable())
    }

    @Test
    fun `DatabaseError is not retryable`() {
        val error = AppError.DatabaseError("Insert failed")

        assertFalse(error.isRetryable())
    }

    @Test
    fun `TimeoutError is retryable`() {
        val error = AppError.TimeoutError("Network request")

        assertTrue(error.isRetryable())
    }

    @Test
    fun `ParseError is not retryable`() {
        val error = AppError.ParseError("Invalid JSON")

        assertFalse(error.isRetryable())
    }

    @Test
    fun `NetworkError with isRetryable false is not retryable`() {
        val error = AppError.NetworkError(400, isRetryable = false)

        assertFalse(error.isRetryable())
    }

    @Test
    fun `NetworkError with isRetryable true is retryable`() {
        val error = AppError.NetworkError(500, isRetryable = true)

        assertTrue(error.isRetryable())
    }
}
