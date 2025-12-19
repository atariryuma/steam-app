package com.steamdeck.mobile.core.auth

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.util.Base64

/**
 * Steam OpenID authentication helper
 *
 * Uses Valve's official Steam OpenID 2.0 authentication (ToS compliant)
 *
 * Official documentation:
 * - https://partner.steamgames.com/doc/features/auth
 * - https://steamcommunity.com/dev
 * - https://openid.net/specs/openid-authentication-2_0.html
 *
 * ⚠️ Steam Web API (non-OpenID) differences:
 * - OpenID: Valve officially recommended, allowed for third-party apps
 * - Web API: Steam official client only (QR login, etc.)
 *
 * Best Practice (2025):
 * - State verification to prevent CSRF attacks
 * - Signature verification to prevent MITM attacks (OpenID 2.0 spec compliant)
 * - HTTPS required (localhost exception for local development)
 * - Extract SteamID64 from claimed ID
 */
object SteamOpenIdAuthenticator {

 private const val STEAM_OPENID_URL = "https://steamcommunity.com/openid/login"

 // OkHttpClient for verification requests
 private val httpClient: OkHttpClient by lazy {
  OkHttpClient.Builder()
   .build()
 }

 /**
  * Generate Steam OpenID authentication URL
  *
  * @param returnUrl Redirect destination after authentication (Example: myapp://auth/callback)
  * @param realm Application root (Example: myapp://)
  * @return Pair(authentication URL, state value)
  */
 fun generateAuthUrl(
  returnUrl: String,
  realm: String = returnUrl.substringBefore("//") + "//"
 ): Pair<String, String> {
  // Generate state for CSRF protection
  val state = generateSecureState()

  val params = mapOf(
   "openid.ns" to "http://specs.openid.net/auth/2.0",
   "openid.mode" to "checkid_setup",
   "openid.return_to" to "$returnUrl?state=$state",
   "openid.realm" to realm,
   "openid.identity" to "http://specs.openid.net/auth/2.0/identifier_select",
   "openid.claimed_id" to "http://specs.openid.net/auth/2.0/identifier_select"
  )

  val queryString = params.entries.joinToString("&") { (key, value) ->
   "$key=${Uri.encode(value)}"
  }

  return Pair("$STEAM_OPENID_URL?$queryString", state)
 }

 /**
  * Extract SteamID64 from callback URL (with signature verification)
  *
  * Steam OpenID claimed_id format:
  * https://steamcommunity.com/openid/id/76561197960435530
  *         ^^^^^^^^^^^^^^^^^^^
  *         SteamID64
  *
  * Best Practice (2025):
  * - State verification (CSRF protection)
  * - Signature verification (MITM protection, OpenID 2.0 spec compliant)
  *
  * @param callbackUrl Callback URL after Steam authentication
  * @param expectedState Previously generated state value
  * @return SteamID64 (null on authentication failure)
  */
 suspend fun extractSteamId(callbackUrl: String, expectedState: String): String? {
  val uri = Uri.parse(callbackUrl)

  // Step 1: State verification (CSRF protection)
  val returnedState = uri.getQueryParameter("state")
  if (returnedState != expectedState) {
   // Security: Never log CSRF tokens - they can be extracted from logcat
   android.util.Log.w("SteamOpenIdAuth", "State mismatch detected - CSRF protection triggered")
   return null
  }

  // Step 2: OpenID mode verification
  val mode = uri.getQueryParameter("openid.mode")
  if (mode != "id_res") {
   android.util.Log.w("SteamOpenIdAuth", "Invalid mode: $mode")
   return null
  }

  // Step 3: Signature verification (MITM protection)
  val params = extractOpenIdParams(uri)
  val isSignatureValid = verifyOpenIdSignature(params)
  if (!isSignatureValid) {
   android.util.Log.e("SteamOpenIdAuth", "Signature verification failed - potential MITM attack")
   return null
  }

  // Step 4: Extract SteamID64 from claimed ID
  val claimedId = uri.getQueryParameter("openid.claimed_id") ?: return null
  val steamIdMatch = Regex("""https://steamcommunity\.com/openid/id/(\d+)""")
   .find(claimedId)

  return steamIdMatch?.groupValues?.getOrNull(1)
 }

 /**
  * OpenID signature verification (OpenID 2.0 spec compliant)
  *
  * Reference: https://openid.net/specs/openid-authentication-2_0.html#verifying_assertions
  *
  * Security:
  * - Send verification request directly to Steam OpenID Provider
  * - Reject authentication if signature is invalid
  *
  * @param params OpenID parameters
  * @return true if signature is valid
  */
 private suspend fun verifyOpenIdSignature(params: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
  try {
   // Build OpenID verification request parameters
   val formBodyBuilder = FormBody.Builder()

   // Change mode to check_authentication
   params.forEach { (key, value) ->
    if (key == "openid.mode") {
     formBodyBuilder.add(key, "check_authentication")
    } else if (key.startsWith("openid.")) {
     formBodyBuilder.add(key, value)
    }
   }

   val request = Request.Builder()
    .url(STEAM_OPENID_URL)
    .post(formBodyBuilder.build())
    .build()

   httpClient.newCall(request).execute().use { response ->
    if (!response.isSuccessful) {
     android.util.Log.e("SteamOpenIdAuth", "Verification request failed: ${response.code}")
     return@withContext false
    }

    val responseBody = response.body?.string() ?: ""
    android.util.Log.d("SteamOpenIdAuth", "Verification response: $responseBody")

    // Steam returns "is_valid:true" if signature is valid
    return@withContext responseBody.contains("is_valid:true")
   }
  } catch (e: Exception) {
   android.util.Log.e("SteamOpenIdAuth", "Signature verification failed", e)
   return@withContext false
  }
 }

 /**
  * Extract OpenID parameters from URI
  *
  * @param uri Callback URI
  * @return Map of OpenID parameters
  */
 private fun extractOpenIdParams(uri: Uri): Map<String, String> {
  val params = mutableMapOf<String, String>()

  uri.queryParameterNames.forEach { paramName ->
   if (paramName.startsWith("openid.")) {
    uri.getQueryParameter(paramName)?.let { value ->
     params[paramName] = value
    }
   }
  }

  return params
 }

 /**
  * Generate secure random state (32 bytes = 256 bits)
  */
 private fun generateSecureState(): String {
  val random = SecureRandom()
  val bytes = ByteArray(32)
  random.nextBytes(bytes)
  return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
 }

 /**
  * SteamID64 validity verification
  *
  * Valid SteamID64 range:
  * 76561197960265728 ~ 76561202255233023 (32-bit accountID)
  */
 fun isValidSteamId64(steamId: String): Boolean {
  val id = steamId.toLongOrNull() ?: return false
  return id >= 76561197960265728L && id <= 76561202255233023L
 }
}
