package com.steamdeck.mobile.domain.error

import com.steamdeck.mobile.core.error.AppError

/**
 * Steam同期専用エラー
 *
 * Best Practice (2025):
 * - ドメイン層でSealed classでエラーを型安全に表現
 * - UI層でstrings.xmlに変換
 * - Clean Architecture準拠（ドメイン層でContextを使用しない）
 */
sealed class SteamSyncError : AppError() {
    /**
     * Steamプロフィールが非公開
     */
    data object PrivateProfile : SteamSyncError() {
        override val message: String
            get() = "Steam profile is private"
    }

    /**
     * Steam API認証エラー
     */
    data object AuthFailed : SteamSyncError() {
        override val message: String
            get() = "Steam API authentication failed"
    }

    /**
     * ネットワークタイムアウト
     */
    data object NetworkTimeout : SteamSyncError() {
        override val message: String
            get() = "Network timeout"
    }

    /**
     * 一般的なAPIエラー
     */
    data class ApiError(val errorMessage: String) : SteamSyncError() {
        override val message: String
            get() = "Steam API error: $errorMessage"
    }

    /**
     * API Key未設定
     */
    data object ApiKeyNotConfigured : SteamSyncError() {
        override val message: String
            get() = "Steam API Key not configured"
    }
}
