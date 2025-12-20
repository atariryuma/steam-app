package com.steamdeck.mobile.core.steam

import android.util.Log
import com.steamdeck.mobile.core.winlator.WinlatorEmulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Steam インストール済みゲームスキャナー
 *
 * Winlator コンテナ内の Steam フォルダをスキャンして、
 * インストール済みゲームの情報を取得する
 */
@Singleton
class SteamGameScanner @Inject constructor(
 private val winlatorEmulator: WinlatorEmulator
) {
 companion object {
  private const val TAG = "SteamGameScanner"
  private const val STEAM_APPS_PATH = "drive_c/Program Files (x86)/Steam/steamapps"
  private const val COMMON_PATH = "common"
 }

 /**
  * Steam App ID に対応するゲームの実行ファイルを検索
  *
  * @param containerId Winlator コンテナ ID
  * @param steamAppId Steam App ID
  * @return 見つかった実行ファイルのパス、見つからない場合は null
  */
 suspend fun findGameExecutable(
  containerId: String,
  steamAppId: Long
 ): Result<String?> = withContext(Dispatchers.IO) {
  try {
   Log.i(TAG, "Scanning for game: appId=$steamAppId, containerId=$containerId")

   // 1. コンテナを取得
   val containersResult = winlatorEmulator.listContainers()
   if (containersResult.isFailure) {
    return@withContext Result.failure(
     Exception("Failed to list containers: ${containersResult.exceptionOrNull()?.message}")
    )
   }

   val containers = containersResult.getOrNull() ?: emptyList()
   val container = containers.firstOrNull { it.id == containerId }
    ?: return@withContext Result.failure(
     Exception("Container not found: $containerId")
    )

   // 2. steamapps フォルダをチェック
   val steamAppsDir = File(container.rootPath, STEAM_APPS_PATH)
   if (!steamAppsDir.exists() || !steamAppsDir.isDirectory) {
    Log.w(TAG, "Steam apps directory not found: ${steamAppsDir.absolutePath}")
    return@withContext Result.success(null)
   }

   // 3. appmanifest_<appId>.acf ファイルを確認
   val manifestFile = File(steamAppsDir, "appmanifest_$steamAppId.acf")
   if (!manifestFile.exists()) {
    Log.i(TAG, "Game not installed (no manifest): appId=$steamAppId")
    return@withContext Result.success(null)
   }

   // 4. マニフェストファイルからインストールディレクトリ名を取得
   val installDir = parseInstallDirFromManifest(manifestFile)
   if (installDir == null) {
    Log.w(TAG, "Failed to parse install directory from manifest")
    return@withContext Result.success(null)
   }

   // 5. common/<InstallDir> フォルダを確認
   val gameDir = File(steamAppsDir, "$COMMON_PATH/$installDir")
   if (!gameDir.exists() || !gameDir.isDirectory) {
    Log.w(TAG, "Game directory not found: ${gameDir.absolutePath}")
    return@withContext Result.success(null)
   }

   // 6. .exe ファイルを検索（最も可能性の高いものを選択）
   val exeFile = findMainExecutable(gameDir)
   if (exeFile == null) {
    Log.w(TAG, "No executable found in: ${gameDir.absolutePath}")
    return@withContext Result.success(null)
   }

   // 7. Windows パス形式に変換 (例: C:\Program Files (x86)\Steam\steamapps\common\Game\game.exe)
   // Bug fix: Handle cases where "drive_c/" might not be in the path
   val absolutePath = exeFile.absolutePath
   val relativePath = if (absolutePath.contains("drive_c/")) {
    absolutePath.substringAfter("drive_c/")
   } else if (absolutePath.contains("drive_c\\")) {
    absolutePath.substringAfter("drive_c\\")
   } else {
    Log.w(TAG, "Unexpected path format (no drive_c): $absolutePath")
    return@withContext Result.success(null)
   }

   val windowsPath = "C:\\$relativePath".replace("/", "\\")

   Log.i(TAG, "Found executable: $windowsPath")
   Result.success(windowsPath)

  } catch (e: Exception) {
   Log.e(TAG, "Failed to scan for game executable", e)
   Result.failure(e)
  }
 }

 /**
  * マニフェストファイルから installdir を取得
  *
  * appmanifest_*.acf ファイルの形式:
  * "AppState"
  * {
  *   "appid" "730"
  *   "installdir" "Counter-Strike Global Offensive"
  *   ...
  * }
  */
 private fun parseInstallDirFromManifest(manifestFile: File): String? {
  return try {
   // Use useLines to properly close file resources
   val line = manifestFile.useLines { lines ->
    lines.find { it.contains("\"installdir\"", ignoreCase = true) }
   } ?: return null

   // Bug fix: More robust parsing
   // Expected format: "\t\"installdir\"\t\t\"GameFolder\""
   val afterKey = line.substringAfter("\"installdir\"", "")
   if (afterKey.isEmpty()) {
    Log.w(TAG, "Invalid manifest format: $line")
    return null
   }

   // Extract value between quotes
   val firstQuote = afterKey.indexOf('"')
   if (firstQuote == -1) {
    Log.w(TAG, "No opening quote found: $afterKey")
    return null
   }

   val secondQuote = afterKey.indexOf('"', firstQuote + 1)
   if (secondQuote == -1) {
    Log.w(TAG, "No closing quote found: $afterKey")
    return null
   }

   val installDir = afterKey.substring(firstQuote + 1, secondQuote).trim()
   if (installDir.isEmpty()) {
    Log.w(TAG, "Empty install directory")
    return null
   }

   installDir

  } catch (e: Exception) {
   Log.e(TAG, "Failed to parse manifest file", e)
   null
  }
 }

 /**
  * ゲームフォルダから最も可能性の高いメイン実行ファイルを検索
  *
  * 優先順位:
  * 1. フォルダ名と同じ名前の .exe
  * 2. 最初に見つかった .exe ファイル
  */
 private fun findMainExecutable(gameDir: File): File? {
  return try {
   // walkTopDown() returns a FileTreeWalk which is closeable
   // Use toList() within try-catch to handle potential errors during directory traversal
   val exeFiles = gameDir.walkTopDown()
    .maxDepth(2) // サブフォルダも1階層まで検索
    .filter { it.isFile && it.extension.equals("exe", ignoreCase = true) }
    .toList()

   if (exeFiles.isEmpty()) {
    return null
   }

   // フォルダ名と同じ名前の .exe を優先
   val gameName = gameDir.name
   val mainExe = exeFiles.find {
    it.nameWithoutExtension.equals(gameName, ignoreCase = true)
   }

   mainExe ?: exeFiles.firstOrNull()
  } catch (e: Exception) {
   Log.w(TAG, "Error finding executable in ${gameDir.name}: ${e.message}")
   null
  }
 }
}
