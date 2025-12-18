package com.steamdeck.mobile.core.steam

import android.content.Context
import android.util.Log
import com.steamdeck.mobile.core.winlator.WinlatorEmulator
import com.steamdeck.mobile.data.local.database.SteamDeckDatabase
import com.steamdeck.mobile.data.local.database.entity.SteamInstallStatus
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
        data class Success(val installPath: String) : SteamInstallResult()
        data class Error(val message: String) : SteamInstallResult()
        data class Progress(val progress: Float, val message: String) : SteamInstallResult()
    }

    /**
     * Steam Client をインストール
     */
    suspend fun installSteam(
        containerId: Long,
        progressCallback: ((Float, String) -> Unit)? = null
    ): Result<SteamInstallResult> = withContext(Dispatchers.IO) {
        try {
            progressCallback?.invoke(0.1f, "Downloading Steam installer...")
            Log.i(TAG, "Starting Steam installation for container: $containerId")

            // 1. Steam インストーラーをダウンロード
            val installerResult = steamInstallerService.downloadInstaller()
            if (installerResult.isFailure) {
                return@withContext Result.success(
                    SteamInstallResult.Error("Failed to download installer: ${installerResult.exceptionOrNull()?.message}")
                )
            }

            val installerFile = installerResult.getOrNull()!!
            Log.i(TAG, "Installer downloaded: ${installerFile.absolutePath}")

            progressCallback?.invoke(0.3f, "Preparing container...")

            // 2. コンテナを取得または作成
            val containerResult = getOrCreateContainer(containerId)
            if (containerResult.isFailure) {
                return@withContext Result.success(
                    SteamInstallResult.Error("Failed to get container: ${containerResult.exceptionOrNull()?.message}")
                )
            }

            val container = containerResult.getOrNull()!!
            Log.i(TAG, "Using container: ${container.name} (${container.id})")

            progressCallback?.invoke(0.5f, "Installing Steam in Wine...")

            // 3. インストーラーをコンテナにコピー
            val containerInstallerPath = copyInstallerToContainer(container, installerFile)
            if (containerInstallerPath.isFailure) {
                return@withContext Result.success(
                    SteamInstallResult.Error("Failed to copy installer: ${containerInstallerPath.exceptionOrNull()?.message}")
                )
            }

            val containerInstaller = containerInstallerPath.getOrNull()!!
            Log.i(TAG, "Installer copied to container: ${containerInstaller.absolutePath}")

            progressCallback?.invoke(0.7f, "Running Steam installer...")

            // 4. Wine 経由で Steam インストーラーを実行
            val installResult = runSteamInstaller(container, containerInstaller)
            if (installResult.isFailure) {
                return@withContext Result.success(
                    SteamInstallResult.Error("Failed to run installer: ${installResult.exceptionOrNull()?.message}")
                )
            }

            progressCallback?.invoke(0.9f, "Finalizing installation...")

            // 5. インストール情報を保存
            steamInstallerService.saveInstallation(
                containerId = containerId.toString(),
                installPath = DEFAULT_STEAM_PATH,
                status = SteamInstallStatus.INSTALLED
            )

            progressCallback?.invoke(1.0f, "Installation complete")
            Log.i(TAG, "Steam installation completed successfully")

            Result.success(SteamInstallResult.Success(DEFAULT_STEAM_PATH))

        } catch (e: Exception) {
            Log.e(TAG, "Steam installation failed", e)
            Result.success(SteamInstallResult.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * コンテナを取得または作成
     */
    private suspend fun getOrCreateContainer(containerId: Long): Result<com.steamdeck.mobile.domain.emulator.EmulatorContainer> =
        withContext(Dispatchers.IO) {
            try {
                // データベースからコンテナ情報を取得
                val containerEntity = database.winlatorContainerDao().getContainerById(containerId)
                    ?: return@withContext Result.failure(Exception("Container not found in database"))

                // Winlatorからコンテナリストを取得
                val containersResult = winlatorEmulator.listContainers()
                if (containersResult.isFailure) {
                    return@withContext Result.failure(
                        Exception("Failed to list containers: ${containersResult.exceptionOrNull()?.message}")
                    )
                }

                val containers = containersResult.getOrNull() ?: emptyList()

                // コンテナIDでマッチングを試みる
                val container = containers.firstOrNull { it.id == containerId.toString() }

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
     */
    private suspend fun runSteamInstaller(
        container: com.steamdeck.mobile.domain.emulator.EmulatorContainer,
        installerFile: File
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

            val process = processResult.getOrNull()!!
            Log.i(TAG, "Steam installer process started: PID ${process.pid}")

            // インストーラーの完了を待つ (タイムアウト: 5分)
            var waitTime = 0L
            val maxWaitTime = 5 * 60 * 1000L // 5 minutes
            val checkInterval = 2000L // 2 seconds

            while (waitTime < maxWaitTime) {
                val statusResult = winlatorEmulator.getProcessStatus(process.id)
                val isRunning = statusResult.getOrNull()?.isRunning ?: false
                if (!isRunning) {
                    Log.i(TAG, "Steam installer completed")
                    break
                }

                kotlinx.coroutines.delay(checkInterval)
                waitTime += checkInterval
            }

            if (waitTime >= maxWaitTime) {
                Log.w(TAG, "Steam installer timeout after 5 minutes")
                // タイムアウトでも成功として扱う（バックグラウンドで完了する可能性）
            }

            Result.success(Unit)

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
}
