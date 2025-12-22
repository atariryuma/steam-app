package com.steamdeck.mobile.core.winlator

import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.WinlatorContainer

/**
 * Winlator engine interface
 * Handles Windows game execution and container management
 */
interface WinlatorEngine {
 /**
  * Launch game
  * @param game Game to launch
  * @param container Winlator container to use
  * @return Launch result
  */
 suspend fun launchGame(game: Game, container: WinlatorContainer?): LaunchResult

 /**
  * Check if game is currently running
  *
  * @return true if game process is active, false otherwise
  */
 suspend fun isGameRunning(): Boolean

 /**
  * Stop running game
  */
 suspend fun stopGame(): Result<Unit>

 /**
  * Create container
  * @param container Container configuration
  * @return Creation result
  */
 suspend fun createContainer(container: WinlatorContainer): Result<WinlatorContainer>

 /**
  * Delete container
  * @param containerId Container ID
  */
 suspend fun deleteContainer(containerId: Long): Result<Unit>

 /**
  * Auto-detect game engine (Unity, Unreal, etc.)
  * @param executablePath Executable file path
  * @return Detected engine
  */
 suspend fun detectGameEngine(executablePath: String): GameEngine

 /**
  * Get engine-optimized container configuration
  */
 fun getOptimizedContainerSettings(engine: GameEngine): WinlatorContainer
}

/**
 * Game launch result
 */
sealed class LaunchResult {
 /** Launch success */
 data class Success(val processId: Int) : LaunchResult()

 /** Launch failure */
 data class Error(val message: String, val cause: Throwable? = null) : LaunchResult()
}

/**
 * Game engine type
 */
enum class GameEngine {
 /** Unity Engine */
 UNITY,

 /** Unreal Engine */
 UNREAL,

 /** .NET Framework */
 DOTNET,

 /** Other/Unknown */
 UNKNOWN;

 /**
  * Engine display name
  */
 val displayName: String
  get() = when (this) {
   UNITY -> "Unity Engine"
   UNREAL -> "Unreal Engine"
   DOTNET -> ".NET Framework"
   UNKNOWN -> "Unknown"
  }
}
