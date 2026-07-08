package com.joat917.qrdecoderlite.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joat917.qrdecoderlite.model.AppUiState
import com.joat917.qrdecoderlite.model.DecodeResult
import com.joat917.qrdecoderlite.model.DecodeUiState
import com.joat917.qrdecoderlite.model.Sheet
import com.joat917.qrdecoderlite.scanner.BarcodeScanner
import com.joat917.qrdecoderlite.scanner.MlKitScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(
    application: Application,
    private val scanner: BarcodeScanner = MlKitScanner(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _decodeUiState = MutableStateFlow<DecodeUiState?>(null)
    val decodeUiState: StateFlow<DecodeUiState?> = _decodeUiState.asStateFlow()

    private var scanningJob: Job? = null

    /**
     * 系统图片选择器返回结果。
     * uri 非 null → 切换到结果页并启动解码；
     * uri 为 null → 用户取消选择，不做任何事。
     */
    fun onImageSelected(uri: Uri?) {
        if (uri != null) {
            scanningJob?.cancel()
            _uiState.value = _uiState.value.copy(activeSheet = Sheet.Result(uri))
            _decodeUiState.value = DecodeUiState(imageUri = uri)

            scanningJob = viewModelScope.launch {
                when (val result = scanner.scan(uri)) {
                    is DecodeResult.Success -> {
                        _decodeUiState.value = DecodeUiState(
                            imageUri = uri,
                            barcodes = result.barcodes
                        )
                    }
                    is DecodeResult.Failure -> {
                        _decodeUiState.value = DecodeUiState(
                            imageUri = uri,
                            decodeError = result.reason
                        )
                    }
                }
            }
        }
    }

    /** 关闭结果页，返回主界面 */
    fun onBackToMain() {
        scanningJob?.cancel()
        _uiState.value = AppUiState()
        _decodeUiState.value = null
    }

    /** 复制条码内容到系统剪贴板 */
    fun onCopy(content: String, context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("barcode", content))
    }

    /** 用系统浏览器打开 URL */
    fun onOpenLink(url: String, context: Context) {
        val uri = url.toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "没有应用可打开此链接", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, "没有权限打开此链接", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "未知的异常导致无法打开此链接", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanner.close()
    }
}
