package com.steamdeck.mobile.core.auth

import android.net.Uri
import java.security.SecureRandom
import java.util.Base64

/**
 * Steam OpenID認証ヘルパー
 *
 * Valve公式のSteam OpenID 2.0認証を使用（規約準拠）
 *
 * 公式ドキュメント:
 * - https://partner.steamgames.com/doc/features/auth
 * - https://steamcommunity.com/dev
 * - https://openid.net/specs/openid-authentication-2_0.html
 *
 * ⚠️ Steam Web API (非OpenID) との違い:
 * - OpenID: Valve公式推奨、サードパーティアプリでの使用許可
 * - Web API: Steam公式クライアント専用（QRログイン等）
 *
 * Best Practice:
 * - ステート検証でCSRF攻撃を防止
 * - HTTPS必須（ローカル開発時はlocalhost例外）
 * - Claimed IDからSteamID64を抽出
 */
object SteamOpenIdAuthenticator {

    private const val STEAM_OPENID_URL = "https://steamcommunity.com/openid/login"

    /**
     * Steam OpenID認証URLを生成
     *
     * @param returnUrl 認証後のリダイレクト先（例: myapp://auth/callback）
     * @param realm アプリケーションルート（例: myapp://）
     * @return Pair(認証URL, ステート値)
     */
    fun generateAuthUrl(
        returnUrl: String,
        realm: String = returnUrl.substringBefore("//") + "//"
    ): Pair<String, String> {
        // CSRF対策用のステート生成
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
     * コールバックURLからSteamID64を抽出
     *
     * Steam OpenIDのclaimed_id形式:
     * https://steamcommunity.com/openid/id/76561197960435530
     *                                    ^^^^^^^^^^^^^^^^^^^
     *                                    SteamID64
     *
     * @param callbackUrl Steam認証後のコールバックURL
     * @param expectedState 事前に生成したステート値
     * @return SteamID64（認証失敗時はnull）
     */
    fun extractSteamId(callbackUrl: String, expectedState: String): String? {
        val uri = Uri.parse(callbackUrl)

        // ステート検証（CSRF対策）
        val returnedState = uri.getQueryParameter("state")
        if (returnedState != expectedState) {
            android.util.Log.w("SteamOpenIdAuth", "State mismatch: expected=$expectedState, got=$returnedState")
            return null
        }

        // OpenID検証
        val mode = uri.getQueryParameter("openid.mode")
        if (mode != "id_res") {
            android.util.Log.w("SteamOpenIdAuth", "Invalid mode: $mode")
            return null
        }

        // Claimed IDからSteamID64を抽出
        val claimedId = uri.getQueryParameter("openid.claimed_id") ?: return null
        val steamIdMatch = Regex("""https://steamcommunity\.com/openid/id/(\d+)""")
            .find(claimedId)

        return steamIdMatch?.groupValues?.getOrNull(1)
    }

    /**
     * セキュアなランダムステート生成（32バイト = 256bit）
     */
    private fun generateSecureState(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * SteamID64の妥当性検証
     *
     * 有効なSteamID64の範囲:
     * 76561197960265728 ~ 76561202255233023 (32bit accountID)
     */
    fun isValidSteamId64(steamId: String): Boolean {
        val id = steamId.toLongOrNull() ?: return false
        return id >= 76561197960265728L && id <= 76561202255233023L
    }
}
