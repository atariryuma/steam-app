package com.steamdeck.mobile.presentation.util

import android.content.Context
import com.steamdeck.mobile.R
import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.domain.error.SteamSyncError

/**
 * UI層用エラー拡張関数
 *
 * Best Practice (2025):
 * - ドメインエラーuser-friendlyなメッセージ conversion
 * - strings.xml 多言語対応
 * - Contextuse（UI層 みuse）
 */

/**
 * AppError文字列リソースID conversion
 */
fun AppError.toStringRes(): Int {
 return when (this) {
  is AppError.NetworkError -> when (code) {
   401 -> R.string.error_auth
   403 -> R.string.error_permission_denied
   404 -> R.string.error_game_not_found
   429 -> R.string.error_network_with_code
   in 500..599 -> R.string.error_network_with_code
   else -> R.string.error_network
  }
  is AppError.AuthError -> R.string.error_auth
  is AppError.DatabaseError -> R.string.error_database
  is AppError.FileError -> R.string.error_file_not_found
  is AppError.TimeoutError -> R.string.error_timeout
  is AppError.ParseError -> R.string.error_unknown
  is AppError.Unknown -> R.string.error_unknown
 }
}

/**
 * AppErrorユーザー向けメッセージ conversion
 *
 * @param context Android Context
 * @return ローカライズされたエラーメッセージ
 */
fun AppError.toUserMessage(context: Context): String {
 return when (this) {
  is AppError.NetworkError -> {
   when (code) {
    401, 403, 404, 429, in 500..599 -> {
     context.getString(toStringRes(), code)
    }
    else -> context.getString(R.string.error_network)
   }
  }
  is AppError.AuthError -> context.getString(R.string.error_auth, reason)
  is AppError.DatabaseError -> context.getString(R.string.error_database)
  is AppError.FileError -> context.getString(R.string.error_file_not_found)
  is AppError.TimeoutError -> context.getString(R.string.error_timeout)
  is AppError.ParseError -> context.getString(R.string.error_unknown)
  is AppError.Unknown -> originalException?.message ?: context.getString(R.string.error_unknown)
 }
}

/**
 * SteamSyncError文字列リソースID conversion
 */
fun SteamSyncError.toStringRes(): Int {
 return when (this) {
  is SteamSyncError.PrivateProfile -> R.string.error_steam_profile_private
  is SteamSyncError.AuthFailed -> R.string.error_steam_auth_failed
  is SteamSyncError.NetworkTimeout -> R.string.error_steam_network_timeout
  is SteamSyncError.ApiError -> R.string.error_steam_api
 }
}

/**
 * SteamSyncErrorユーザー向けメッセージ conversion
 *
 * @param context Android Context
 * @return ローカライズされたエラーメッセージ
 */
fun SteamSyncError.toUserMessage(context: Context): String {
 return when (this) {
  is SteamSyncError.PrivateProfile -> context.getString(R.string.error_steam_profile_private)
  is SteamSyncError.AuthFailed -> context.getString(R.string.error_steam_auth_failed)
  is SteamSyncError.NetworkTimeout -> context.getString(R.string.error_steam_network_timeout)
  is SteamSyncError.ApiError -> context.getString(R.string.error_steam_api, errorMessage)
 }
}

/**
 * Throwableユーザー向けメッセージ conversion
 *
 * 汎用的なエラーハンドリング用
 */
fun Throwable.toUserMessage(context: Context): String {
 return when (this) {
  is SteamSyncError -> this.toUserMessage(context)
  is AppError -> this.toUserMessage(context)
  else -> this.message ?: context.getString(R.string.error_unknown)
 }
}
