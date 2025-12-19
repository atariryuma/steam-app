package com.steamdeck.mobile.core.error

/**
 * Application-wide error types.
 * Provides type-safe error handling across all layers.
 *
 * Best Practice (2025):
 * - Unified error type system (AppError + ApiError統合)
 * - リトライ可能性の判定
 * - ユーザーフレンドリーなメッセージ生成
 */
sealed class AppError : Exception() {
    /**
     * Network-related errors.
     *
     * @param code HTTP status code or error code
     * @param originalException The original exception that caused this error
     * @param isRetryable リトライ可能かどうか
     */
    data class NetworkError(
        val code: Int,
        val originalException: Throwable? = null,
        val isRetryable: Boolean = true
    ) : AppError() {
        override val message: String
            get() = when (code) {
                401 -> "認証エラー (code: $code)"
                403 -> "アクセス拒否 (code: $code)"
                404 -> "リソースが見つかりません (code: $code)"
                429 -> "レート制限 (code: $code)"
                in 500..599 -> "サーバーエラー (code: $code)"
                else -> "ネットワークエラー (code: $code)"
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
            get() = "認証エラー: $reason"
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
            get() = "データベースエラー: $operation"
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
            get() = "ファイルエラー: $path"
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
            get() = "タイムアウト: $operation"
    }

    /**
     * Parse/Deserialization errors.
     *
     * @param reason パースエラーの理由
     * @param originalException The original exception
     */
    data class ParseError(
        val reason: String,
        val originalException: Throwable? = null
    ) : AppError() {
        override val message: String
            get() = "データ解析エラー: $reason"
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
            get() = originalException?.message ?: "不明なエラー"
    }

    /**
     * リトライ可能かどうかを判定
     */
    open fun isRetryable(): Boolean = when (this) {
        is NetworkError -> isRetryable
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
                    isRetryable = true
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
         * HTTP status codeからAppErrorを生成
         *
         * Retrofit error handlingで使用
         */
        fun fromHttpCode(code: Int, message: String = ""): AppError {
            return when (code) {
                // 4xx Client Errors
                400 -> NetworkError(code, Exception("Bad Request: $message"), isRetryable = false)
                401 -> AuthError("Unauthorized: $message")
                403 -> AuthError("Forbidden: $message")
                404 -> NetworkError(code, Exception("Not Found: $message"), isRetryable = false)
                429 -> NetworkError(code, Exception("Rate Limited: $message"), isRetryable = true)

                // 5xx Server Errors (retryable)
                in 500..599 -> NetworkError(code, Exception("Server Error: $message"), isRetryable = true)

                // Other
                else -> NetworkError(code, Exception("HTTP Error $code: $message"), isRetryable = false)
            }
        }
    }
}
