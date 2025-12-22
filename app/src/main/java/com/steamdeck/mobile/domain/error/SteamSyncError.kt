package com.steamdeck.mobile.domain.error

/**
 * Steam sync specific errors
 *
 * Best Practice (2025):
 * - Domain layer sealed class for type-safe error representation
 * - UI layer converts to strings.xml
 * - Clean Architecture compliant (domain layer doesn't use Context)
 *
 * Note: Extends Exception instead of AppError to avoid package restriction
 */
sealed class SteamSyncError : Exception() {
 /**
  * Steam profile is private
  */
 data object PrivateProfile : SteamSyncError() {
  override val message: String
   get() = "Steam profile is private"
 }

 /**
  * Steam API authentication error
  */
 data object AuthFailed : SteamSyncError() {
  override val message: String
   get() = "Steam API authentication failed"
 }

 /**
  * Network timeout
  */
 data object NetworkTimeout : SteamSyncError() {
  override val message: String
   get() = "Network timeout"
 }

 /**
  * General API error
  */
 data class ApiError(val errorMessage: String) : SteamSyncError() {
  override val message: String
   get() = "Steam API error: $errorMessage"
 }
}
