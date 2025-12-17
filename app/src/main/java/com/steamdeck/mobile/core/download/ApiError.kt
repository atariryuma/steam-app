package com.steamdeck.mobile.core.download

/**
 * API エラー型
 *
 * ベストプラクティス: Sealed classでエラーを型安全に表現
 * Reference: https://canopas.com/retrofit-effective-error-handling-with-kotlin-coroutine-and-result-api-405217e9a73d
 */
sealed class ApiError : Exception() {
    /**
     * ネットワークエラー（接続失敗、タイムアウト等）
     * リトライ可能
     */
    data class NetworkError(
        override val message: String,
        override val cause: Throwable?
    ) : ApiError()

    /**
     * HTTPエラー（4xx, 5xx）
     */
    data class HttpError(
        val code: Int,
        override val message: String,
        val isRetryable: Boolean = false
    ) : ApiError() {
        companion object {
            fun from(code: Int, message: String): HttpError {
                return when (code) {
                    // クライアントエラー（4xx） - 一般的にリトライ不可
                    400 -> HttpError(code, "Bad Request: $message", isRetryable = false)
                    401 -> HttpError(code, "Unauthorized: $message", isRetryable = false)
                    403 -> HttpError(code, "Forbidden: $message", isRetryable = false)
                    404 -> HttpError(code, "Not Found: $message", isRetryable = false)

                    // Rate Limiting - リトライ可能（遅延後）
                    429 -> HttpError(code, "Rate Limited: $message", isRetryable = true)

                    // サーバーエラー（5xx） - リトライ可能
                    in 500..599 -> HttpError(code, "Server Error: $message", isRetryable = true)

                    // その他
                    else -> HttpError(code, "HTTP Error $code: $message", isRetryable = false)
                }
            }
        }
    }

    /**
     * パースエラー（JSONデコード失敗等）
     */
    data class ParseError(
        override val message: String,
        override val cause: Throwable?
    ) : ApiError()

    /**
     * 不明なエラー
     */
    data class UnknownError(
        override val message: String,
        override val cause: Throwable?
    ) : ApiError()
}
