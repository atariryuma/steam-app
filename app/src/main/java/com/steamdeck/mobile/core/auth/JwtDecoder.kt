package com.steamdeck.mobile.core.auth

import android.util.Base64
import org.json.JSONObject

/**
 * JWT Token Decoder for Steam Authentication
 *
 * Decodes Steam access_token (JWT format) to extract Steam ID from "sub" claim.
 *
 * JWT Format:
 * - header.payload.signature (Base64URL encoded)
 * - payload contains: {"sub": "steamid64", "iss": "...", "aud": [...], ...}
 *
 * Reference: https://github.com/DoctorMcKay/node-steam-session
 * Best Practice: Use built-in Base64 decoder with URL_SAFE flag
 */
object JwtDecoder {

    /**
     * Extract Steam ID (steamid64) from JWT access token
     *
     * @param accessToken JWT access token from Steam Authentication Service
     * @return Steam ID (17-digit numeric string) or null if decoding fails
     *
     * Example:
     * ```
     * val steamId = JwtDecoder.extractSteamId(accessToken)
     * // steamId = "76561198012345678"
     * ```
     */
    fun extractSteamId(accessToken: String): String? {
        return try {
            // JWT format: header.payload.signature
            val parts = accessToken.split(".")
            if (parts.size != 3) {
                android.util.Log.w("JwtDecoder", "Invalid JWT format: expected 3 parts, got ${parts.size}")
                return null
            }

            // Decode Base64URL payload (middle part)
            val payloadBase64 = parts[1]
            val payloadJson = String(
                Base64.decode(
                    payloadBase64,
                    Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                ),
                Charsets.UTF_8
            )

            // Parse JSON and extract "sub" claim
            val json = JSONObject(payloadJson)
            val steamId = json.optString("sub", "")

            if (steamId.isBlank()) {
                android.util.Log.w("JwtDecoder", "Steam ID not found in JWT payload")
                return null
            }

            // Validate Steam ID format (17-digit numeric string starting with "7656119")
            if (!steamId.matches(Regex("^7656119\\d{10}$"))) {
                android.util.Log.w("JwtDecoder", "Invalid Steam ID format: $steamId")
                return null
            }

            steamId
        } catch (e: IllegalArgumentException) {
            android.util.Log.e("JwtDecoder", "Base64 decoding failed", e)
            null
        } catch (e: org.json.JSONException) {
            android.util.Log.e("JwtDecoder", "JSON parsing failed", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("JwtDecoder", "Unexpected error during JWT decoding", e)
            null
        }
    }

    /**
     * Extract all claims from JWT payload for debugging
     *
     * @param accessToken JWT access token
     * @return Map of all claims or null if decoding fails
     */
    fun extractAllClaims(accessToken: String): Map<String, Any>? {
        return try {
            val parts = accessToken.split(".")
            if (parts.size != 3) return null

            val payloadJson = String(
                Base64.decode(
                    parts[1],
                    Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                ),
                Charsets.UTF_8
            )

            val json = JSONObject(payloadJson)
            val claims = mutableMapOf<String, Any>()

            json.keys().forEach { key ->
                claims[key] = json.get(key)
            }

            claims
        } catch (e: Exception) {
            android.util.Log.e("JwtDecoder", "Failed to extract all claims", e)
            null
        }
    }
}
