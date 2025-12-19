package com.steamdeck.mobile.core.result

import com.steamdeck.mobile.core.error.AppError

/**
 * 統一データ結果型
 *
 * Best Practice (2025):
 * - Sealed interfaceで型安全なエラーハンドリング
 * - Loading状態のサポート
 * - 既存のResult<T>からの移行をサポート
 *
 * Reference:
 * - https://proandroiddev.com/kotlin-sealed-classes-for-better-handling-of-api-response-6aa1fce0f28a
 * - https://medium.com/@douglas.iacovelli/the-beauty-of-sealed-classes-in-kotlin-e793bb3962a2
 */
sealed interface DataResult<out T> {
    /**
     * 成功状態
     *
     * @param data 成功時のデータ
     */
    data class Success<T>(val data: T) : DataResult<T>

    /**
     * エラー状態
     *
     * @param error アプリケーションエラー
     */
    data class Error(val error: AppError) : DataResult<Nothing>

    /**
     * ローディング状態（オプショナル）
     *
     * @param progress 進捗率（0.0 ~ 1.0）、nullの場合は不定
     */
    data class Loading(val progress: Float? = null) : DataResult<Nothing>

    companion object {
        /**
         * 成功結果を作成
         */
        fun <T> success(data: T): DataResult<T> = Success(data)

        /**
         * エラー結果を作成
         */
        fun error(error: AppError): DataResult<Nothing> = Error(error)

        /**
         * エラー結果を作成（Throwableから）
         */
        fun error(throwable: Throwable): DataResult<Nothing> =
            Error(AppError.from(throwable))

        /**
         * エラー結果を作成（メッセージから）
         */
        fun error(message: String): DataResult<Nothing> =
            Error(AppError.Unknown(Exception(message)))

        /**
         * ローディング結果を作成
         */
        fun loading(progress: Float? = null): DataResult<Nothing> = Loading(progress)

        /**
         * Kotlin Result<T>から変換
         *
         * 既存コードの段階的移行をサポート
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
 * DataResultの拡張関数
 */

/**
 * 成功データを取得（成功時のみ）
 */
fun <T> DataResult<T>.getOrNull(): T? = when (this) {
    is DataResult.Success -> data
    else -> null
}

/**
 * エラーを取得（エラー時のみ）
 */
fun <T> DataResult<T>.errorOrNull(): AppError? = when (this) {
    is DataResult.Error -> error
    else -> null
}

/**
 * 成功時の変換
 */
inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> = when (this) {
    is DataResult.Success -> DataResult.Success(transform(data))
    is DataResult.Error -> this
    is DataResult.Loading -> this
}

/**
 * 成功時の非同期変換
 */
inline fun <T, R> DataResult<T>.flatMap(transform: (T) -> DataResult<R>): DataResult<R> = when (this) {
    is DataResult.Success -> transform(data)
    is DataResult.Error -> this
    is DataResult.Loading -> this
}

/**
 * エラー時の変換
 */
inline fun <T> DataResult<T>.mapError(transform: (AppError) -> AppError): DataResult<T> = when (this) {
    is DataResult.Error -> DataResult.Error(transform(error))
    else -> this
}

/**
 * 成功かどうかを判定
 */
fun <T> DataResult<T>.isSuccess(): Boolean = this is DataResult.Success

/**
 * エラーかどうかを判定
 */
fun <T> DataResult<T>.isError(): Boolean = this is DataResult.Error

/**
 * ローディング中かどうかを判定
 */
fun <T> DataResult<T>.isLoading(): Boolean = this is DataResult.Loading

/**
 * 成功時の処理を実行
 */
inline fun <T> DataResult<T>.onSuccess(action: (T) -> Unit): DataResult<T> {
    if (this is DataResult.Success) {
        action(data)
    }
    return this
}

/**
 * エラー時の処理を実行
 */
inline fun <T> DataResult<T>.onError(action: (AppError) -> Unit): DataResult<T> {
    if (this is DataResult.Error) {
        action(error)
    }
    return this
}

/**
 * ローディング時の処理を実行
 */
inline fun <T> DataResult<T>.onLoading(action: (Float?) -> Unit): DataResult<T> {
    if (this is DataResult.Loading) {
        action(progress)
    }
    return this
}

/**
 * Kotlin Result<T>に変換
 *
 * 既存のResult<T>を期待するAPIとの互換性のため
 */
fun <T> DataResult<T>.toResult(): Result<T> = when (this) {
    is DataResult.Success -> Result.success(data)
    is DataResult.Error -> Result.failure(Exception(error.message))
    is DataResult.Loading -> Result.failure(Exception("Still loading"))
}
