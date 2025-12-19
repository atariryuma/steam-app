package com.steamdeck.mobile.core.result

import com.steamdeck.mobile.core.error.AppError
import org.junit.Assert.*
import org.junit.Test

/**
 * DataResult unit tests
 *
 * Tests:
 * - Success/Error/Loading state creation
 * - Extension functions (map, flatMap, onSuccess, onError)
 * - Type conversions (Result<T> interop)
 */
class DataResultTest {

    @Test
    fun `success creates Success state`() {
        val result = DataResult.success("test")

        assertTrue(result is DataResult.Success)
        assertTrue(result.isSuccess())
        assertFalse(result.isError())
        assertFalse(result.isLoading())
        assertEquals("test", result.getOrNull())
    }

    @Test
    fun `error creates Error state`() {
        val error = AppError.Unknown(Exception("test error"))
        val result = DataResult.error(error)

        assertTrue(result is DataResult.Error)
        assertFalse(result.isSuccess())
        assertTrue(result.isError())
        assertFalse(result.isLoading())
        assertEquals(error, result.errorOrNull())
    }

    @Test
    fun `loading creates Loading state`() {
        val result = DataResult.loading(0.5f)

        assertTrue(result is DataResult.Loading)
        assertFalse(result.isSuccess())
        assertFalse(result.isError())
        assertTrue(result.isLoading())
        assertEquals(0.5f, (result as DataResult.Loading).progress)
    }

    @Test
    fun `map transforms success value`() {
        val result = DataResult.success(5)
        val mapped = result.map { it * 2 }

        assertTrue(mapped is DataResult.Success)
        assertEquals(10, mapped.getOrNull())
    }

    @Test
    fun `map preserves error state`() {
        val error = AppError.Unknown()
        val result: DataResult<Int> = DataResult.error(error)
        val mapped = result.map { it * 2 }

        assertTrue(mapped is DataResult.Error)
        assertEquals(error, mapped.errorOrNull())
    }

    @Test
    fun `flatMap chains operations`() {
        val result = DataResult.success(5)
        val mapped = result.flatMap { value ->
            if (value > 0) {
                DataResult.success(value * 2)
            } else {
                DataResult.error("Invalid value")
            }
        }

        assertTrue(mapped is DataResult.Success)
        assertEquals(10, mapped.getOrNull())
    }

    @Test
    fun `onSuccess executes only for success`() {
        var executed = false
        val result = DataResult.success("test")

        result.onSuccess { executed = true }

        assertTrue(executed)
    }

    @Test
    fun `onSuccess does not execute for error`() {
        var executed = false
        val result: DataResult<String> = DataResult.error(AppError.Unknown())

        result.onSuccess { executed = true }

        assertFalse(executed)
    }

    @Test
    fun `onError executes only for error`() {
        var executed = false
        val result: DataResult<String> = DataResult.error(AppError.Unknown())

        result.onError { executed = true }

        assertTrue(executed)
    }

    @Test
    fun `onError does not execute for success`() {
        var executed = false
        val result = DataResult.success("test")

        result.onError { executed = true }

        assertFalse(executed)
    }

    @Test
    fun `fromResult converts Kotlin Result success`() {
        val kotlinResult = Result.success("test")
        val dataResult = DataResult.fromResult(kotlinResult)

        assertTrue(dataResult is DataResult.Success)
        assertEquals("test", dataResult.getOrNull())
    }

    @Test
    fun `fromResult converts Kotlin Result failure`() {
        val exception = Exception("test error")
        val kotlinResult = Result.failure<String>(exception)
        val dataResult = DataResult.fromResult(kotlinResult)

        assertTrue(dataResult is DataResult.Error)
        assertNotNull(dataResult.errorOrNull())
    }

    @Test
    fun `toResult converts Success to Kotlin Result`() {
        val dataResult = DataResult.success("test")
        val kotlinResult = dataResult.toResult()

        assertTrue(kotlinResult.isSuccess)
        assertEquals("test", kotlinResult.getOrNull())
    }

    @Test
    fun `toResult converts Error to Kotlin Result failure`() {
        val dataResult: DataResult<String> = DataResult.error(AppError.Unknown())
        val kotlinResult = dataResult.toResult()

        assertTrue(kotlinResult.isFailure)
    }

    @Test
    fun `mapError transforms error type`() {
        val originalError = AppError.Unknown(Exception("original"))
        val result: DataResult<String> = DataResult.error(originalError)

        val mapped = result.mapError {
            AppError.NetworkError(500, it.originalException)
        }

        assertTrue(mapped is DataResult.Error)
        val error = mapped.errorOrNull()
        assertTrue(error is AppError.NetworkError)
        assertEquals(500, (error as AppError.NetworkError).code)
    }
}
