package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.repository.GameRepository
import javax.inject.Inject

/**
 * ゲームを追加するUseCase
 *
 * 2025 Best Practice: DataResult<T> for type-safe error handling
 */
class AddGameUseCase @Inject constructor(
    private val gameRepository: GameRepository
) {
    /**
     * ゲームを追加
     * @return 追加されたゲームのID
     */
    suspend operator fun invoke(game: Game): DataResult<Long> {
        return try {
            // バリデーション
            if (game.name.isBlank()) {
                return DataResult.Error(
                    AppError.DatabaseError("ゲーム名を入力してください", null)
                )
            }
            if (game.executablePath.isBlank()) {
                return DataResult.Error(
                    AppError.FileError("実行ファイルパスを入力してください", null)
                )
            }
            if (game.installPath.isBlank()) {
                return DataResult.Error(
                    AppError.FileError("インストールパスを入力してください", null)
                )
            }

            val gameId = gameRepository.insertGame(game)
            DataResult.Success(gameId)
        } catch (e: Exception) {
            DataResult.Error(AppError.from(e))
        }
    }
}
