package com.steamdeck.mobile.core.winlator

import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.WinlatorContainer

/**
 * Winlatorエンジン interface
 * Windowsgame execution、containermanagement行う
 */
interface WinlatorEngine {
 /**
  * gamelaunch
  * @param game launchdogame
  * @param container usedoWinlatorcontainer
  * @return launchresult
  */
 suspend fun launchGame(game: Game, container: WinlatorContainer?): LaunchResult

 /**
  * Check if game is currently running
  *
  * @return true if game process is active, false otherwise
  */
 suspend fun isGameRunning(): Boolean

 /**
  * executionin gamestop
  */
 suspend fun stopGame(): Result<Unit>

 /**
  * containercreate
  * @param container containerconfiguration
  * @return createresult
  */
 suspend fun createContainer(container: WinlatorContainer): Result<WinlatorContainer>

 /**
  * containerdelete
  * @param containerId containerID
  */
 suspend fun deleteContainer(containerId: Long): Result<Unit>

 /**
  * gameエンジン自動detection（Unity, Unrealetc）
  * @param executablePath executionfilepath
  * @return detectionされたエンジン
  */
 suspend fun detectGameEngine(executablePath: String): GameEngine

 /**
  * エンジン optimizationされたcontainerconfigurationretrieve
  */
 fun getOptimizedContainerSettings(engine: GameEngine): WinlatorContainer
}

/**
 * gamelaunchresult
 */
sealed class LaunchResult {
 /** launchsuccess */
 data class Success(val processId: Int) : LaunchResult()

 /** launchfailure */
 data class Error(val message: String, val cause: Throwable? = null) : LaunchResult()
}

/**
 * gameエンジン種別
 */
enum class GameEngine {
 /** Unity Engine */
 UNITY,

 /** Unreal Engine */
 UNREAL,

 /** .NET Framework */
 DOTNET,

 /** そ 他/不明 */
 UNKNOWN;

 /**
  * エンジン名
  */
 val displayName: String
  get() = when (this) {
   UNITY -> "Unity Engine"
   UNREAL -> "Unreal Engine"
   DOTNET -> ".NET Framework"
   UNKNOWN -> "不明"
  }
}
