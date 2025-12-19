package com.steamdeck.mobile.core.error

/**
 * Application-wide error types.
 * Provides type-safe error handling across all layers.
 *
 * Best Practice (2025):
 * - Unified error type system (AppError + ApiError integration)
 * - Retryability determination
 * - User-friendly message generation
 */
sealed class AppError : Exception() {
 /**
  * Network-related errors.
  *
  * @param code HTTP status code or error code
  * @param originalException The original exception that caused this error
  * @param retryable Whether this error is retryable
  */
 data class NetworkError(
  val code: Int,
  val originalException: Throwable? = null,
  val retryable: Boolean = true
 ) : AppError() {
  override val message: String
   get() = when (code) {
    401 -> "Authentication error (code: $code)"
    403 -> "Access denied (code: $code)"
    404 -> "Resource not found (code: $code)"
    429 -> "Rate limited (code: $code)"
    in 500..599 -> "Server error (code: $code)"
    else -> "Network error (code: $code)"
   }
 }

 /**
  * Authentication/Authorization errors.
  *
  * @param reason Description of why authentication failed
  * @param originalException The original exception that caused this error
  */
 data class AuthError(
  val reason: String,
  val originalException: Throwable? = null
 ) : AppError() {
  override val message: String
   get() = "Authentication error: $reason"

  companion object {
   val ApiKeyNotConfigured = AuthError("Steam API Key not configured")
  }
 }

 /**
  * Database-related errors.
  *
  * @param operation The database operation that failed
  * @param originalException The original exception that caused this error
  */
 data class DatabaseError(
  val operation: String,
  val originalException: Throwable? = null
 ) : AppError() {
  override val message: String
   get() = "Database error: $operation"
 }

 /**
  * File system errors.
  *
  * @param path The file path that caused the error
  * @param originalException The original exception that caused this error
  */
 data class FileError(
  val path: String,
  val originalException: Throwable? = null
 ) : AppError() {
  override val message: String
   get() = "File error: $path"
 }

 /**
  * Timeout errors.
  *
  * @param operation The operation that timed out
  * @param originalException The original exception that caused this error
  */
 data class TimeoutError(
  val operation: String,
  val originalException: Throwable? = null
 ) : AppError() {
  override val message: String
   get() = "Timeout: $operation"
 }

 /**
  * Parse/Deserialization errors.
  *
  * @param reason Reason for parse error
  * @param originalException The original exception
  */
 data class ParseError(
  val reason: String,
  val originalException: Throwable? = null
 ) : AppError() {
  override val message: String
   get() = "Data parsing error: $reason"
 }

 /**
  * Unknown/Generic errors.
  *
  * @param originalException The original exception
  */
 data class Unknown(
  val originalException: Throwable? = null
 ) : AppError() {
  override val message: String
   get() = originalException?.message ?: "Unknown error"
 }

 /**
  * Determine whether this error is retryable
  */
 open fun isRetryable(): Boolean = when (this) {
  is NetworkError -> retryable
  is TimeoutError -> true
  is DatabaseError -> false
  is AuthError -> false
  is FileError -> false
  is ParseError -> false
  is Unknown -> false
 }

 companion object {
  /**
   * Converts a generic Throwable to an AppError.
   *
   * @param throwable The exception to convert
   * @return An appropriate AppError subtype
   */
  fun from(throwable: Throwable): AppError {
   return when (throwable) {
    is AppError -> throwable
    is java.net.UnknownHostException -> NetworkError(
     code = 0,
     originalException = throwable,
     retryable = true
    )
    is java.net.SocketTimeoutException -> TimeoutError(
     operation = "Network request",
     originalException = throwable
    )
    is java.io.FileNotFoundException -> FileError(
     path = throwable.message ?: "",
     originalException = throwable
    )
    is kotlinx.serialization.SerializationException -> ParseError(
     reason = "JSON deserialization failed",
     originalException = throwable
    )
    else -> Unknown(throwable)
   }
  }

  /**
   * Generate AppError from HTTP status code
   *
   * Used for Retrofit error handling
   */
  fun fromHttpCode(code: Int, message: String = ""): AppError {
   return when (code) {
    // 4xx Client Errors
    400 -> NetworkError(code, Exception("Bad Request: $message"), retryable = false)
    401 -> AuthError("Unauthorized: $message")
    403 -> AuthError("Forbidden: $message")
    404 -> NetworkError(code, Exception("Not Found: $message"), retryable = false)
    429 -> NetworkError(code, Exception("Rate Limited: $message"), retryable = true)

    // 5xx Server Errors (retryable)
    in 500..599 -> NetworkError(code, Exception("Server Error: $message"), retryable = true)

    // Other
    else -> NetworkError(code, Exception("HTTP Error $code: $message"), retryable = false)
   }
  }
 }
}
