package com.steamdeck.mobile.core.winlator

import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.WinlatorContainer

/**
 * Winlatorエンジンのインターフェース
 * Windowsゲームの実行、コンテナ管理を行う
 */
interface WinlatorEngine {
    /**
     * ゲームを起動
     * @param game 起動するゲーム
     * @param container 使用するWinlatorコンテナ
     * @return 起動結果
     */
    suspend fun launchGame(game: Game, container: WinlatorContainer?): LaunchResult

    /**
     * Check if game is currently running
     *
     * @return true if game process is active, false otherwise
     */
    suspend fun isGameRunning(): Boolean

    /**
     * 実行中のゲームを停止
     */
    suspend fun stopGame(): Result<Unit>

    /**
     * コンテナを作成
     * @param container コンテナ設定
     * @return 作成結果
     */
    suspend fun createContainer(container: WinlatorContainer): Result<WinlatorContainer>

    /**
     * コンテナを削除
     * @param containerId コンテナID
     */
    suspend fun deleteContainer(containerId: Long): Result<Unit>

    /**
     * ゲームエンジンを自動検出（Unity, Unreal等）
     * @param executablePath 実行ファイルパス
     * @return 検出されたエンジン
     */
    suspend fun detectGameEngine(executablePath: String): GameEngine

    /**
     * エンジンに最適化されたコンテナ設定を取得
     */
    fun getOptimizedContainerSettings(engine: GameEngine): WinlatorContainer
}

/**
 * ゲーム起動結果
 */
sealed class LaunchResult {
    /** 起動成功 */
    data class Success(val processId: Int) : LaunchResult()

    /** 起動失敗 */
    data class Error(val message: String, val cause: Throwable? = null) : LaunchResult()
}

/**
 * ゲームエンジン種別
 */
enum class GameEngine {
    /** Unity Engine */
    UNITY,

    /** Unreal Engine */
    UNREAL,

    /** .NET Framework */
    DOTNET,

    /** その他/不明 */
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
