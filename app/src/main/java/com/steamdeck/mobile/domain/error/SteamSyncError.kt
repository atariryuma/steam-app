package com.steamdeck.mobile.domain.error

/**
 * Steam同期専用error
 *
 * Best Practice (2025):
 * - ドメイン層 Sealed class errortype安全 表現
 * - UI層 strings.xml conversion
 * - Clean Architecture準拠（ドメイン層 Contextuseしない）
 *
 * Note: Extends Exception instead of AppError to avoid package restriction
 */
sealed class SteamSyncError : Exception() {
 /**
  * Steamプロフィール 非公開
  */
 data object PrivateProfile : SteamSyncError() {
  override val message: String
   get() = "Steam profile is private"
 }

 /**
  * Steam APIAuthentication error
  */
 data object AuthFailed : SteamSyncError() {
  override val message: String
   get() = "Steam API authentication failed"
 }

 /**
  * networkTimeout
  */
 data object NetworkTimeout : SteamSyncError() {
  override val message: String
   get() = "Network timeout"
 }

 /**
  * 一般的なAPIerror
  */
 data class ApiError(val errorMessage: String) : SteamSyncError() {
  override val message: String
   get() = "Steam API error: $errorMessage"
 }
}
