package com.steamdeck.mobile.core.download

import com.steamdeck.mobile.data.repository.SteamAuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Steamゲームダウンロード管理（MVP版スタブ）
 *
 * 現段階では実装なし。将来的にSteam CDNからの
 * 直接ダウンロード機能を実装予定。
 *
 * 実装予定機能:
 * - Steam CDN認証
 * - Depot manifest取得
 * - Chunk-based download with resume support
 * - DownloadManager統合
 *
 * 参考:
 * - https://github.com/SteamRE/DepotDownloader
 * - https://github.com/SteamRE/SteamKit
 */
@Singleton
class SteamDownloadManager @Inject constructor(
    private val downloadManager: DownloadManager,
    private val steamAuthRepository: SteamAuthRepository
) {

    /**
     * Steamゲームをダウンロード（未実装）
     *
     * @param appId Steam App ID
     * @param installPath インストール先パス
     * @return ダウンロードID or エラー
     */
    suspend fun downloadSteamGame(
        appId: Long,
        installPath: String
    ): Result<Long> {
        return Result.failure(
            NotImplementedError(
                """
                Steam CDN download not yet implemented.

                Use file import feature instead:
                1. Download game on PC via Steam
                2. Transfer files to Android (USB/Network)
                3. Use "Import from File" feature

                Future implementation will support direct Steam CDN downloads.
                """.trimIndent()
            )
        )
    }

    /**
     * ダウンロード進捗を監視（未実装）
     *
     * @param appId Steam App ID
     * @return 進捗状態のFlow
     */
    fun observeDownloadProgress(appId: Long): Flow<DownloadProgress> {
        // MVP: Empty flow
        return flowOf()
    }

    /**
     * ダウンロードをキャンセル（未実装）
     *
     * @param appId Steam App ID
     */
    suspend fun cancelDownload(appId: Long): Result<Unit> {
        return Result.failure(
            NotImplementedError("Cancel download not yet implemented")
        )
    }

    /**
     * ダウンロードを一時停止（未実装）
     *
     * @param appId Steam App ID
     */
    suspend fun pauseDownload(appId: Long): Result<Unit> {
        return Result.failure(
            NotImplementedError("Pause download not yet implemented")
        )
    }

    /**
     * ダウンロードを再開（未実装）
     *
     * @param appId Steam App ID
     */
    suspend fun resumeDownload(appId: Long): Result<Unit> {
        return Result.failure(
            NotImplementedError("Resume download not yet implemented")
        )
    }
}

// TODO: Phase 5B実装時に以下を追加
// - data class CDNAuthToken
// - data class DepotManifest
// - data class DepotFileEntry
// - suspend fun authenticateSteamCDN(): Result<CDNAuthToken>
// - suspend fun getDepotManifest(...): Result<DepotManifest>
// - suspend fun downloadDepotFile(...): Result<Unit>
