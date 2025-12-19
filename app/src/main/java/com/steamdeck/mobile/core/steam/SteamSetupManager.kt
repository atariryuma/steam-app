package com.steamdeck.mobile.core.steam

import android.content.Context
import android.util.Log
import com.steamdeck.mobile.core.winlator.WinlatorEmulator
import com.steamdeck.mobile.data.local.database.SteamDeckDatabase
import com.steamdeck.mobile.data.local.database.entity.Box64Preset
import com.steamdeck.mobile.data.local.database.entity.SteamInstallStatus
import com.steamdeck.mobile.data.local.database.entity.WinlatorContainerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Steam Client セットアップマネージャー
 *
 * Winlatorコンテナ内でのSteamインストールを管理します
 */
@Singleton
class SteamSetupManager @Inject constructor(
    private val context: Context,
    private val winlatorEmulator: WinlatorEmulator,
    private val steamInstallerService: SteamInstallerService,
    private val database: SteamDeckDatabase
) {
    companion object {
        private const val TAG = "SteamSetupManager"
        private const val DEFAULT_STEAM_PATH = "C:\\Program Files (x86)\\Steam"
    }

    /**
     * Steam インストール結果
     */
    sealed class SteamInstallResult {
        data class Success(
            val installPath: String,
            val containerId: String
        ) : SteamInstallResult()
        data class Error(val message: String) : SteamInstallResult()
        data class Progress(val progress: Float, val message: String) : SteamInstallResult()
    }

    /**
     * Steam Client をインストール
     *
     * @param containerId Winlatorコンテナ ID (String型)
     * @param progressCallback インストール進捗コールバック
     */
    suspend fun installSteam(
        containerId: String,
        progressCallback: ((Float, String) -> Unit)? = null
    ): Result<SteamInstallResult> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting Steam installation for container: $containerId")

            // 0. Winlatorエミュレータを初期化（Box64/Wine展開）
            progressCallback?.invoke(0.0f, "Checking Winlator initialization...")
            val available = winlatorEmulator.isAvailable().getOrNull() ?: false

            if (!available) {
                Log.w(TAG, "Winlator not initialized - starting initialization (this may take 2-3 minutes)...")

                // Winlatorを初期化（Box64/Wineバイナリを展開）
                // 進捗: 0.0 ~ 0.4 (40%)
                val initResult = winlatorEmulator.initialize { progress, message ->
                    // Map 0.0-1.0 progress to 0.0-0.4 range
                    progressCallback?.invoke(progress * 0.4f, message)
                }

                if (initResult.isFailure) {
                    val error = initResult.exceptionOrNull()
                    Log.e(TAG, "Winlator initialization failed", error)
                    return@withContext Result.success(
                        SteamInstallResult.Error(
                            "Winlator環境の初期化に失敗しました。\n\n" +
                            "エラー: ${error?.message}\n\n" +
                            "解決方法:\n" +
                            "• ストレージ空き容量を確認（最低500MB必要）\n" +
                            "• アプリを再起動してください\n" +
                            "• 端末を再起動してください"
                        )
                    )
                }

                Log.i(TAG, "Winlator initialization completed successfully")
            } else {
                Log.i(TAG, "Winlator already initialized, skipping initialization")
            }

            // 進捗: 0.4 ~ 0.5 (10%)
            progressCallback?.invoke(0.4f, "Downloading Steam installer...")

            // 1. Steam インストーラーをダウンロード
            // 進捗: 0.4 ~ 0.5 (10%)
            val installerResult = steamInstallerService.downloadInstaller()
            if (installerResult.isFailure) {
                return@withContext Result.success(
                    SteamInstallResult.Error("Failed to download installer: ${installerResult.exceptionOrNull()?.message}")
                )
            }

            val installerFile = installerResult.getOrElse {
                return@withContext Result.success(
                    SteamInstallResult.Error("Installer file not found after download: ${it.message}")
                )
            }
            Log.i(TAG, "Installer downloaded: ${installerFile.absolutePath}")

            // 進捗: 0.5 ~ 0.6 (10%)
            progressCallback?.invoke(0.5f, "Preparing container...")

            // 2. コンテナを取得または作成
            val containerResult = getOrCreateContainer(containerId)
            if (containerResult.isFailure) {
                return@withContext Result.success(
                    SteamInstallResult.Error("Failed to get container: ${containerResult.exceptionOrNull()?.message}")
                )
            }

            val container = containerResult.getOrElse {
                return@withContext Result.success(
                    SteamInstallResult.Error("Container not available: ${it.message}")
                )
            }
            Log.i(TAG, "Using container: ${container.name} (${container.id})")

            // 進捗: 0.6 ~ 0.65 (5%)
            progressCallback?.invoke(0.6f, "Copying installer to container...")

            // 3. インストーラーをコンテナにコピー
            val containerInstallerPath = copyInstallerToContainer(container, installerFile)
            if (containerInstallerPath.isFailure) {
                return@withContext Result.success(
                    SteamInstallResult.Error("Failed to copy installer: ${containerInstallerPath.exceptionOrNull()?.message}")
                )
            }

            val containerInstaller = containerInstallerPath.getOrElse {
                return@withContext Result.success(
                    SteamInstallResult.Error("Failed to get installer path in container: ${it.message}")
                )
            }
            Log.i(TAG, "Installer copied to container: ${containerInstaller.absolutePath}")

            // 進捗: 0.65 ~ 0.95 (30%)
            progressCallback?.invoke(0.65f, "Running Steam installer (this may take a few minutes)...")

            // 4. Wine 経由で Steam インストーラーを実行
            val installResult = runSteamInstaller(
                container = container,
                installerFile = containerInstaller,
                progressCallback = { installProgress ->
                    // Map installer progress (0.0-1.0) to 0.65-0.95 range
                    progressCallback?.invoke(0.65f + installProgress * 0.3f, "Installing Steam Client...")
                }
            )
            if (installResult.isFailure) {
                return@withContext Result.success(
                    SteamInstallResult.Error("Failed to run installer: ${installResult.exceptionOrNull()?.message}")
                )
            }

            // 進捗: 0.95 ~ 1.0 (5%)
            progressCallback?.invoke(0.95f, "Finalizing installation...")

            // 5. インストール情報を保存 (container.id は既にString型)
            steamInstallerService.saveInstallation(
                containerId = container.id,
                installPath = DEFAULT_STEAM_PATH,
                status = SteamInstallStatus.INSTALLED
            )

            progressCallback?.invoke(1.0f, "Installation complete")
            Log.i(TAG, "Steam installation completed successfully")

            Result.success(SteamInstallResult.Success(
                installPath = DEFAULT_STEAM_PATH,
                containerId = container.id
            ))

        } catch (e: Exception) {
            Log.e(TAG, "Steam installation failed", e)
            Result.success(SteamInstallResult.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * コンテナを取得または作成
     *
     * @param containerId Winlatorコンテナ ID (String型)
     */
    private suspend fun getOrCreateContainer(containerId: String): Result<com.steamdeck.mobile.domain.emulator.EmulatorContainer> =
        withContext(Dispatchers.IO) {
            try {
                // コンテナIDをLongに変換してデータベース検索（データベースは既存のLong型を維持）
                val containerIdLong = containerId.toLongOrNull()
                var containerEntity = if (containerIdLong != null) {
                    database.winlatorContainerDao().getContainerById(containerIdLong)
                } else {
                    null
                }

                // コンテナが存在しない場合はデータベースに新規作成
                if (containerEntity == null) {
                    Log.w(TAG, "Container $containerId not found in database, creating default container...")
                    val newEntity = WinlatorContainerEntity(
                        id = containerIdLong ?: 0, // Long型で保存、0の場合はauto-generate
                        name = "Steam Client",
                        box64Preset = Box64Preset.STABILITY, // Steamには安定性重視
                        wineVersion = "9.0+"
                    )

                    try {
                        val newId = database.winlatorContainerDao().insertContainer(newEntity)
                        Log.i(TAG, "Created default container entity with ID: $newId")
                        containerEntity = newEntity.copy(id = newId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to create default container entity", e)
                        return@withContext Result.failure(Exception("Failed to create container in database: ${e.message}"))
                    }
                }

                // Winlatorからコンテナリストを取得
                val containersResult = winlatorEmulator.listContainers()
                if (containersResult.isFailure) {
                    return@withContext Result.failure(
                        Exception("Failed to list containers: ${containersResult.exceptionOrNull()?.message}")
                    )
                }

                val containers = containersResult.getOrNull() ?: emptyList()

                // コンテナIDでマッチングを試みる (String型で比較)
                val container = containers.firstOrNull { it.id == containerId }

                if (container != null) {
                    return@withContext Result.success(container)
                }

                // コンテナが存在しない場合は新規作成
                Log.i(TAG, "Container not found, creating new container for ID: $containerId")
                val config = com.steamdeck.mobile.domain.emulator.EmulatorContainerConfig(
                    name = containerEntity.name,
                    performancePreset = when (containerEntity.box64Preset) {
                        com.steamdeck.mobile.data.local.database.entity.Box64Preset.PERFORMANCE ->
                            com.steamdeck.mobile.domain.emulator.PerformancePreset.MAXIMUM_PERFORMANCE
                        com.steamdeck.mobile.data.local.database.entity.Box64Preset.STABILITY ->
                            com.steamdeck.mobile.domain.emulator.PerformancePreset.MAXIMUM_STABILITY
                        com.steamdeck.mobile.data.local.database.entity.Box64Preset.CUSTOM ->
                            com.steamdeck.mobile.domain.emulator.PerformancePreset.BALANCED
                    }
                )

                winlatorEmulator.createContainer(config)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to get or create container", e)
                Result.failure(e)
            }
        }

    /**
     * インストーラーをコンテナにコピー
     */
    private suspend fun copyInstallerToContainer(
        container: com.steamdeck.mobile.domain.emulator.EmulatorContainer,
        installerFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // コンテナの drive_c/users/Public/Downloads にコピー
            val downloadsDir = File(container.rootPath, "drive_c/users/Public/Downloads")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val containerInstaller = File(downloadsDir, "SteamSetup.exe")
            installerFile.copyTo(containerInstaller, overwrite = true)

            Log.i(TAG, "Copied installer to: ${containerInstaller.absolutePath}")
            Result.success(containerInstaller)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy installer to container", e)
            Result.failure(e)
        }
    }

    /**
     * Wine 経由で Steam インストーラーを実行
     *
     * インストール完了検証を強化:
     * 1. プロセス終了を待機
     * 2. steam.exe の存在確認 (最大3回リトライ)
     * 3. タイムアウト時はエラーを返す
     */
    private suspend fun runSteamInstaller(
        container: com.steamdeck.mobile.domain.emulator.EmulatorContainer,
        installerFile: File,
        progressCallback: ((Float) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Steam インストーラーをサイレントモードで実行
            // /S = サイレントインストール
            // /D = インストールディレクトリ (スペースを含む場合でも引用符なし)
            val arguments = listOf(
                "/S",
                "/D=C:\\Program Files (x86)\\Steam"
            )

            Log.i(TAG, "Launching Steam installer with arguments: $arguments")

            val processResult = winlatorEmulator.launchExecutable(
                container = container,
                executable = installerFile,
                arguments = arguments
            )

            if (processResult.isFailure) {
                return@withContext Result.failure(
                    Exception("Failed to launch installer: ${processResult.exceptionOrNull()?.message}")
                )
            }

            val process = processResult.getOrElse {
                return@withContext Result.failure(
                    Exception("Failed to get process handle: ${it.message}")
                )
            }
            Log.i(TAG, "Steam installer process started: PID ${process.pid}")

            // インストーラーの完了を待つ (タイムアウト: 5分)
            var waitTime = 0L
            val maxWaitTime = 5 * 60 * 1000L // 5 minutes
            val checkInterval = 2000L // 2 seconds
            var processCompleted = false

            while (waitTime < maxWaitTime) {
                val statusResult = winlatorEmulator.getProcessStatus(process.id)
                val isRunning = statusResult.getOrNull()?.isRunning ?: false
                if (!isRunning) {
                    Log.i(TAG, "Steam installer process completed")
                    processCompleted = true
                    progressCallback?.invoke(0.9f)
                    break
                }

                // Update progress based on elapsed time
                val progress = (waitTime.toFloat() / maxWaitTime.toFloat()).coerceIn(0f, 0.85f)
                progressCallback?.invoke(progress)

                kotlinx.coroutines.delay(checkInterval)
                waitTime += checkInterval
            }

            if (!processCompleted) {
                Log.e(TAG, "Steam installer timeout after 5 minutes")
                return@withContext Result.failure(
                    Exception(
                        "Steam インストーラーがタイムアウトしました (5分)。\n" +
                        "端末のストレージ容量を確認してください。"
                    )
                )
            }

            // インストール完了を検証: steam.exe の存在確認 (最大3回リトライ)
            val steamExe = File(container.rootPath, "drive_c/Program Files (x86)/Steam/steam.exe")
            var retryCount = 0
            val maxRetries = 3
            val retryDelay = 2000L // 2 seconds

            while (retryCount < maxRetries) {
                if (steamExe.exists()) {
                    Log.i(TAG, "Steam installation verified: ${steamExe.absolutePath}")
                    progressCallback?.invoke(1.0f)
                    return@withContext Result.success(Unit)
                }

                retryCount++
                Log.w(TAG, "steam.exe not found, retry $retryCount/$maxRetries")
                progressCallback?.invoke(0.9f + (retryCount.toFloat() / maxRetries.toFloat()) * 0.1f)
                kotlinx.coroutines.delay(retryDelay)
            }

            // 検証失敗
            Log.e(TAG, "Steam installation verification failed: steam.exe not found at ${steamExe.absolutePath}")
            return@withContext Result.failure(
                Exception(
                    "Steam インストールの検証に失敗しました。\n" +
                    "steam.exe が見つかりません: ${steamExe.absolutePath}\n\n" +
                    "再試行してください。"
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to run Steam installer", e)
            Result.failure(e)
        }
    }

    /**
     * Steam が既にインストールされているかチェック
     */
    suspend fun isSteamInstalled(): Boolean = withContext(Dispatchers.IO) {
        val installation = steamInstallerService.getInstallation()
        installation?.status == SteamInstallStatus.INSTALLED
    }

    /**
     * Steam インストール情報を取得
     */
    suspend fun getSteamInstallation() = withContext(Dispatchers.IO) {
        steamInstallerService.getInstallation()
    }
}
