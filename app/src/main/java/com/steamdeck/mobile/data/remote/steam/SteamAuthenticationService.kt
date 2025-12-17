package com.steamdeck.mobile.data.remote.steam

import com.steamdeck.mobile.data.remote.steam.model.BeginAuthSessionViaQRResponse
import com.steamdeck.mobile.data.remote.steam.model.GetAuthSessionInfoRequest
import com.steamdeck.mobile.data.remote.steam.model.GetAuthSessionInfoResponse
import com.steamdeck.mobile.data.remote.steam.model.PollAuthSessionStatusResponse
import com.steamdeck.mobile.data.remote.steam.model.UpdateAuthSessionWithMobileConfirmationRequest
import com.steamdeck.mobile.data.remote.steam.model.UpdateAuthSessionWithMobileConfirmationResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Steam IAuthenticationService Retrofit API
 *
 * CRITICAL: Steam API expects form-encoded parameters, NOT JSON body
 * Reference: https://github.com/DoctorMcKay/node-steam-session
 *
 * API Documentation: https://steamapi.xpaw.me/
 * Base URL: https://api.steampowered.com/
 */
interface SteamAuthenticationService {

    /**
     * QR認証セッションを開始
     *
     * エンドポイント: IAuthenticationService/BeginAuthSessionViaQR/v1
     *
     * CRITICAL FIX: Use @FormUrlEncoded instead of @Body
     * Steam API expects application/x-www-form-urlencoded, not application/json
     *
     * @param deviceFriendlyName デバイス名
     * @param platformType 3 = MobileApp
     * @param deviceDetails デバイス詳細
     * @param websiteId "Mobile"
     * @return client_id, request_id, challenge_url
     */
    @FormUrlEncoded
    @POST("IAuthenticationService/BeginAuthSessionViaQR/v1/")
    suspend fun beginAuthSessionViaQR(
        @Field("device_friendly_name") deviceFriendlyName: String,
        @Field("platform_type") platformType: Int = 3,
        @Field("website_id") websiteId: String = "Mobile"
    ): Response<BeginAuthSessionViaQRResponse>

    /**
     * 認証セッションのステータスをポーリング
     *
     * エンドポイント: IAuthenticationService/PollAuthSessionStatus/v1
     *
     * CRITICAL FIX: Use @FormUrlEncoded instead of JSON body
     * Steam API expects application/x-www-form-urlencoded
     *
     * @param clientId client_id (form field for routing)
     * @param requestId request_id (form field)
     * @return 認証状態、トークン（承認後）
     */
    @FormUrlEncoded
    @POST("IAuthenticationService/PollAuthSessionStatus/v1/")
    suspend fun pollAuthSessionStatus(
        @Field("client_id") clientId: String,
        @Field("request_id") requestId: String
    ): Response<PollAuthSessionStatusResponse>

    /**
     * モバイル承認で認証セッションを更新
     *
     * エンドポイント: IAuthenticationService/UpdateAuthSessionWithMobileConfirmation/v1
     *
     * @param request client_id, steamid, signature, confirm
     * @return 成功/失敗
     */
    @POST("IAuthenticationService/UpdateAuthSessionWithMobileConfirmation/v1/")
    suspend fun updateAuthSessionWithMobileConfirmation(
        @Body request: UpdateAuthSessionWithMobileConfirmationRequest
    ): Response<UpdateAuthSessionWithMobileConfirmationResponse>

    /**
     * 認証セッション情報を取得（位置情報含む）
     *
     * エンドポイント: IAuthenticationService/GetAuthSessionInfo/v1
     *
     * @param request client_id
     * @return IP、位置情報、デバイス名
     */
    @POST("IAuthenticationService/GetAuthSessionInfo/v1/")
    suspend fun getAuthSessionInfo(
        @Body request: GetAuthSessionInfoRequest
    ): Response<GetAuthSessionInfoResponse>

    companion object {
        const val BASE_URL = "https://api.steampowered.com/"
    }
}
