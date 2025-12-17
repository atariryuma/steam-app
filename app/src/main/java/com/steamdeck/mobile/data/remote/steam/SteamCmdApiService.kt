package com.steamdeck.mobile.data.remote.steam

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * SteamCMD API Service
 *
 * SteamCMD APIを使用してApp情報とDepot/Manifest情報を取得
 *
 * API Documentation: https://www.steamcmd.net/
 * Base URL: https://api.steamcmd.net/v1/
 *
 * Note: これは非公式APIですが、2025年現在も安定して動作しています
 */
interface SteamCmdApiService {

    /**
     * App情報を取得
     *
     * Depot ID、Manifest ID、Branch情報などを含む詳細情報を取得
     *
     * @param appId Steam App ID
     * @return App詳細情報
     */
    @GET("info/{appId}")
    suspend fun getAppInfo(
        @Path("appId") appId: Long
    ): Response<SteamCmdAppInfoResponse>

    companion object {
        const val BASE_URL = "https://api.steamcmd.net/v1/"
    }
}

/**
 * SteamCMD API レスポンス
 */
data class SteamCmdAppInfoResponse(
    @SerializedName("status")
    val status: String, // "success" or "error"

    @SerializedName("data")
    val data: SteamCmdAppData?
)

/**
 * App データ
 */
data class SteamCmdAppData(
    @SerializedName("appid")
    val appId: String,

    @SerializedName("name")
    val name: String?,

    @SerializedName("depots")
    val depots: Map<String, SteamCmdDepotData>?
)

/**
 * Depot データ
 */
data class SteamCmdDepotData(
    @SerializedName("manifests")
    val manifests: Map<String, SteamCmdManifestData>?,

    @SerializedName("name")
    val name: String?,

    @SerializedName("config")
    val config: SteamCmdDepotConfig?
)

/**
 * Manifest データ
 */
data class SteamCmdManifestData(
    @SerializedName("gid")
    val manifestId: String, // Manifest ID (String形式)

    @SerializedName("size")
    val size: String?,

    @SerializedName("download")
    val downloadSize: String?
)

/**
 * Depot Config
 */
data class SteamCmdDepotConfig(
    @SerializedName("oslist")
    val osList: String?,

    @SerializedName("osarch")
    val osArch: String?,

    @SerializedName("language")
    val language: String?
)
