package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.repository.GameRepository
import javax.inject.Inject

/**
 * ゲームを追加するUseCase
 */
class AddGameUseCase @Inject constructor(
    private val gameRepository: GameRepository
) {
    /**
     * ゲームを追加
     * @return 追加されたゲームのID
     */
    suspend operator fun invoke(game: Game): Result<Long> {
        return try {
            // バリデーション
            if (game.name.isBlank()) {
                return Result.failure(IllegalArgumentException("ゲーム名を入力してください"))
            }
            if (game.executablePath.isBlank()) {
                return Result.failure(IllegalArgumentException("実行ファイルパスを入力してください"))
            }
            if (game.installPath.isBlank()) {
                return Result.failure(IllegalArgumentException("インストールパスを入力してください"))
            }

            val gameId = gameRepository.insertGame(game)
            Result.success(gameId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
