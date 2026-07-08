package com.joat917.qrdecoderlite.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joat917.qrdecoderlite.model.AppUiState
import com.joat917.qrdecoderlite.model.DecodeUiState
import com.joat917.qrdecoderlite.model.Sheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _decodeUiState = MutableStateFlow<DecodeUiState?>(null)
    val decodeUiState: StateFlow<DecodeUiState?> = _decodeUiState.asStateFlow()

    /**
     * 系统图片选择器返回结果。
     * uri 非 null → 切换到结果页；
     * uri 为 null → 用户取消选择，不做任何事。
     */
    fun onImageSelected(uri: Uri?) {
        if (uri != null) {
            _uiState.value = _uiState.value.copy(activeSheet = Sheet.Result(uri))
        }
    }

    /** 关闭结果页，返回主界面 */
    fun onBackToMain() {
        _uiState.value = AppUiState()
        _decodeUiState.value = null
    }
}
