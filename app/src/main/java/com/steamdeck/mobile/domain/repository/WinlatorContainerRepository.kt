package com.steamdeck.mobile.domain.repository

import com.steamdeck.mobile.domain.model.WinlatorContainer
import kotlinx.coroutines.flow.Flow

/**
 * Winlatorコンテナ管理リポジトリのインターフェース
 */
interface WinlatorContainerRepository {
    /**
     * すべてのコンテナを取得
     */
    fun getAllContainers(): Flow<List<WinlatorContainer>>

    /**
     * コンテナIDでコンテナを取得
     */
    suspend fun getContainerById(containerId: Long): WinlatorContainer?

    /**
     * コンテナ名でコンテナを検索
     */
    fun searchContainers(query: String): Flow<List<WinlatorContainer>>

    /**
     * コンテナを追加
     */
    suspend fun insertContainer(container: WinlatorContainer): Long

    /**
     * コンテナを更新
     */
    suspend fun updateContainer(container: WinlatorContainer)

    /**
     * コンテナを削除
     */
    suspend fun deleteContainer(container: WinlatorContainer)

    /**
     * すべてのコンテナを削除
     */
    suspend fun deleteAllContainers()
}
