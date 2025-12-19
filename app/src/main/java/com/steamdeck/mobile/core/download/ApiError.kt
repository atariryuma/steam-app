package com.steamdeck.mobile.core.download

/**
 * API error types
 *
 * Best practice: Type-safe error representation with sealed class
 * Reference: https://canopas.com/retrofit-effective-error-handling-with-kotlin-coroutine-and-result-api-405217e9a73d
 */
sealed class ApiError : Exception() {
 /**
  * Network error (connection failure, timeout, etc.)
  * Retryable
  */
 data class NetworkError(
  override val message: String,
  override val cause: Throwable?
 ) : ApiError()

 /**
  * HTTP error (4xx, 5xx)
  */
 data class HttpError(
  val code: Int,
  override val message: String,
  val isRetryable: Boolean = false
 ) : ApiError() {
  companion object {
   fun from(code: Int, message: String): HttpError {
    return when (code) {
     // Client errors (4xx) - generally not retryable
     400 -> HttpError(code, "Bad Request: $message", isRetryable = false)
     401 -> HttpError(code, "Unauthorized: $message", isRetryable = false)
     403 -> HttpError(code, "Forbidden: $message", isRetryable = false)
     404 -> HttpError(code, "Not Found: $message", isRetryable = false)

     // Rate Limiting - retryable (after delay)
     429 -> HttpError(code, "Rate Limited: $message", isRetryable = true)

     // Server errors (5xx) - retryable
     in 500..599 -> HttpError(code, "Server Error: $message", isRetryable = true)

     // Other
     else -> HttpError(code, "HTTP Error $code: $message", isRetryable = false)
    }
   }
  }
 }

 /**
  * Parse error (JSON decoding failure, etc.)
  */
 data class ParseError(
  override val message: String,
  override val cause: Throwable?
 ) : ApiError()

 /**
  * Unknown error
  */
 data class UnknownError(
  override val message: String,
  override val cause: Throwable?
 ) : ApiError()
}
