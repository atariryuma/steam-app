package com.steamdeck.mobile.core.result

import com.steamdeck.mobile.core.error.AppError

/**
 * Unified data result type
 *
 * Best Practice (2025):
 * - Type-safe error handling with sealed interface
 * - Loading state support
 * - Support migration from existing Result<T>
 *
 * Reference:
 * - https://proandroiddev.com/kotlin-sealed-classes-for-better-handling-of-api-response-6aa1fce0f28a
 * - https://medium.com/@douglas.iacovelli/the-beauty-of-sealed-classes-in-kotlin-e793bb3962a2
 */
sealed interface DataResult<out T> {
 /**
  * Success state
  *
  * @param data Data on success
  */
 data class Success<T>(val data: T) : DataResult<T>

 /**
  * Error state
  *
  * @param error Application error
  */
 data class Error(val error: AppError) : DataResult<Nothing>

 /**
  * Loading state (optional)
  *
  * @param progress Progress rate (0.0 ~ 1.0), null if indeterminate
  */
 data class Loading(val progress: Float? = null) : DataResult<Nothing>

 companion object {
  /**
   * Create success result
   */
  fun <T> success(data: T): DataResult<T> = Success(data)

  /**
   * Create error result
   */
  fun error(error: AppError): DataResult<Nothing> = Error(error)

  /**
   * Create error result (from Throwable)
   */
  fun error(throwable: Throwable): DataResult<Nothing> =
   Error(AppError.from(throwable))

  /**
   * Create error result (from message)
   */
  fun error(message: String): DataResult<Nothing> =
   Error(AppError.Unknown(Exception(message)))

  /**
   * Create loading result
   */
  fun loading(progress: Float? = null): DataResult<Nothing> = Loading(progress)

  /**
   * Convert from Kotlin Result<T>
   *
   * Support gradual migration of existing code
   */
  fun <T> fromResult(result: Result<T>): DataResult<T> {
   return result.fold(
    onSuccess = { Success(it) },
    onFailure = { Error(AppError.from(it)) }
   )
  }
 }
}

/**
 * DataResult extension functions
 */

/**
 * Get success data (only on success)
 */
fun <T> DataResult<T>.getOrNull(): T? = when (this) {
 is DataResult.Success -> data
 else -> null
}

/**
 * Get error (only on error)
 */
fun <T> DataResult<T>.errorOrNull(): AppError? = when (this) {
 is DataResult.Error -> error
 else -> null
}

/**
 * Transform on success
 */
inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> = when (this) {
 is DataResult.Success -> DataResult.Success(transform(data))
 is DataResult.Error -> this
 is DataResult.Loading -> this
}

/**
 * Async transform on success
 */
inline fun <T, R> DataResult<T>.flatMap(transform: (T) -> DataResult<R>): DataResult<R> = when (this) {
 is DataResult.Success -> transform(data)
 is DataResult.Error -> this
 is DataResult.Loading -> this
}

/**
 * Transform on error
 */
inline fun <T> DataResult<T>.mapError(transform: (AppError) -> AppError): DataResult<T> = when (this) {
 is DataResult.Error -> DataResult.Error(transform(error))
 else -> this
}

/**
 * Determine if success
 */
fun <T> DataResult<T>.isSuccess(): Boolean = this is DataResult.Success

/**
 * Determine if error
 */
fun <T> DataResult<T>.isError(): Boolean = this is DataResult.Error

/**
 * Determine if loading
 */
fun <T> DataResult<T>.isLoading(): Boolean = this is DataResult.Loading

/**
 * Execute action on success
 */
inline fun <T> DataResult<T>.onSuccess(action: (T) -> Unit): DataResult<T> {
 if (this is DataResult.Success) {
  action(data)
 }
 return this
}

/**
 * Execute action on error
 */
inline fun <T> DataResult<T>.onError(action: (AppError) -> Unit): DataResult<T> {
 if (this is DataResult.Error) {
  action(error)
 }
 return this
}

/**
 * Execute action on loading
 */
inline fun <T> DataResult<T>.onLoading(action: (Float?) -> Unit): DataResult<T> {
 if (this is DataResult.Loading) {
  action(progress)
 }
 return this
}

/**
 * Convert to Kotlin Result<T>
 *
 * For compatibility with APIs that expect existing Result<T>
 */
fun <T> DataResult<T>.toResult(): Result<T> = when (this) {
 is DataResult.Success -> Result.success(data)
 is DataResult.Error -> Result.failure(Exception(error.message))
 is DataResult.Loading -> Result.failure(Exception("Still loading"))
}
