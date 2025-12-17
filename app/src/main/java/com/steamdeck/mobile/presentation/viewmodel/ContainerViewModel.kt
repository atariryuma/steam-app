package com.steamdeck.mobile.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.core.winlator.WinlatorEmulator
import com.steamdeck.mobile.domain.emulator.EmulatorContainer
import com.steamdeck.mobile.domain.emulator.EmulatorContainerConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * コンテナ管理のViewModel
 */
@HiltViewModel
class ContainerViewModel @Inject constructor(
    private val winlatorEmulator: WinlatorEmulator
) : ViewModel() {

    private val _uiState = MutableStateFlow<ContainerUiState>(ContainerUiState.Loading)
    val uiState: StateFlow<ContainerUiState> = _uiState.asStateFlow()

    /**
     * コンテナ一覧を読み込み
     */
    fun loadContainers() {
        viewModelScope.launch {
            _uiState.value = ContainerUiState.Loading

            val result = winlatorEmulator.listContainers()

            _uiState.value = if (result.isSuccess) {
                ContainerUiState.Success(result.getOrThrow())
            } else {
                ContainerUiState.Error(
                    result.exceptionOrNull()?.message ?: "コンテナの読み込みに失敗しました"
                )
            }
        }
    }

    /**
     * 新しいコンテナを作成
     */
    fun createContainer(name: String) {
        viewModelScope.launch {
            val config = EmulatorContainerConfig(name = name)
            val result = winlatorEmulator.createContainer(config)

            if (result.isSuccess) {
                // Reload containers after creation
                loadContainers()
            } else {
                _uiState.value = ContainerUiState.Error(
                    result.exceptionOrNull()?.message ?: "コンテナの作成に失敗しました"
                )
            }
        }
    }

    /**
     * コンテナを削除
     */
    fun deleteContainer(containerId: String) {
        viewModelScope.launch {
            val result = winlatorEmulator.deleteContainer(containerId)

            if (result.isSuccess) {
                // Reload containers after deletion
                loadContainers()
            } else {
                _uiState.value = ContainerUiState.Error(
                    result.exceptionOrNull()?.message ?: "コンテナの削除に失敗しました"
                )
            }
        }
    }
}

/**
 * コンテナ管理のUI状態
 */
sealed class ContainerUiState {
    /** 読み込み中 */
    object Loading : ContainerUiState()

    /** 成功 */
    data class Success(val containers: List<EmulatorContainer>) : ContainerUiState()

    /** エラー */
    data class Error(val message: String) : ContainerUiState()
}
