package com.steamdeck.mobile.data.remote.steam

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Steam CDN Service
 *
 * Steam CDNからDepotファイルをダウンロードするためのサービス
 *
 * Reference:
 * - DepotDownloader: https://github.com/SteamRE/DepotDownloader
 * - Steam CDN Servers: https://api.steampowered.com/ISteamApps/GetServersForSteamPipe/v1
 */
interface SteamCdnService {

    /**
     * Depotマニフェストをダウンロード
     *
     * URL形式: https://cdn.steamcontent.com/depot/{depotId}/manifest/{manifestId}/5
     *
     * @param depotId Depot ID
     * @param manifestId Manifest ID
     * @return Manifestバイナリデータ
     */
    @Streaming
    @GET("depot/{depotId}/manifest/{manifestId}/5")
    suspend fun downloadManifest(
        @Path("depotId") depotId: Long,
        @Path("manifestId") manifestId: Long
    ): Response<ResponseBody>

    /**
     * Depotチャンクをダウンロード
     *
     * URL形式: https://cdn.steamcontent.com/depot/{depotId}/chunk/{chunkId}
     *
     * @param depotId Depot ID
     * @param chunkId Chunk ID (SHA-1 hash)
     * @return チャンクバイナリデータ（LZ圧縮済み）
     */
    @Streaming
    @GET("depot/{depotId}/chunk/{chunkId}")
    suspend fun downloadChunk(
        @Path("depotId") depotId: Long,
        @Path("chunkId") chunkId: String
    ): Response<ResponseBody>

    /**
     * カスタムURLからダウンロード (CDN URL用)
     *
     * @param url 完全なCDN URL
     * @param authToken CDN認証トークン (オプション)
     * @return ダウンロードデータ
     */
    @Streaming
    @GET
    suspend fun downloadFromUrl(
        @Url url: String,
        @Header("Authorization") authToken: String? = null
    ): Response<ResponseBody>

    companion object {
        /**
         * Steam CDN Base URLs
         *
         * Steamは複数のCDNサーバーを持っているため、
         * 地域に応じて最適なサーバーを選択できます
         */
        const val CDN_BASE_URL = "https://cdn.cloudflare.steamstatic.com/"
        const val CDN_AKAMAI_URL = "https://cdn.akamai.steamstatic.com/"
        const val CDN_STEAM_CONTENT_URL = "https://cdn.steamcontent.com/"

        /**
         * 利用可能なCDNサーバーのリスト
         */
        val AVAILABLE_CDN_SERVERS = listOf(
            CDN_BASE_URL,
            CDN_AKAMAI_URL,
            CDN_STEAM_CONTENT_URL
        )
    }
}
