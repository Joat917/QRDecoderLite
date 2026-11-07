package com.joat917.qrdecoderlite.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joat917.qrdecoderlite.R
import com.joat917.qrdecoderlite.model.AppUiState
import com.joat917.qrdecoderlite.model.DecodeResult
import com.joat917.qrdecoderlite.model.DecodeUiState
import com.joat917.qrdecoderlite.model.Sheet
import com.joat917.qrdecoderlite.scanner.BarcodeScanner
import com.joat917.qrdecoderlite.scanner.CombinedScanner
import com.joat917.qrdecoderlite.util.toErrorReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "AppViewModel"

class AppViewModel(
    application: Application,
    private val scanner: BarcodeScanner = CombinedScanner(application)
) : AndroidViewModel(application) {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                AppViewModel(application)
            }
        }
    }

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _decodeUiState = MutableStateFlow(DecodeUiState())
    val decodeUiState: StateFlow<DecodeUiState> = _decodeUiState.asStateFlow()

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
            _decodeUiState.value = DecodeUiState()

            scanningJob = viewModelScope.launch {
                val result = try {
                    scanner.scan(uri)
                } catch (e: CancellationException) {
                    Log.d(TAG, "scan cancelled: $uri")
                    throw e
                } catch (e: OutOfMemoryError) {
                    Log.w(TAG, "scan out of memory: $uri", e)
                    DecodeResult.Failure(
                        e.toErrorReason(getApplication<Application>().getString(R.string.error_out_of_memory))
                    )
                } catch (e: Throwable) {
                    Log.w(TAG, "scan failed: $uri", e)
                    DecodeResult.Failure(e.toErrorReason())
                }

                when (result) {
                    is DecodeResult.Success -> {
                        _decodeUiState.value = DecodeUiState(barcodes = result.barcodes)
                    }
                    is DecodeResult.Failure -> {
                        _decodeUiState.value = DecodeUiState(decodeError = result.reason)
                    }
                }
            }
        }
    }

    /** 关闭结果页，返回主界面 */
    fun onBackToMain() {
        scanningJob?.cancel()
        _uiState.value = AppUiState()
        _decodeUiState.value = DecodeUiState()
    }

    /** 复制条码内容到系统剪贴板 */
    fun onCopy(content: String, context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("barcode", content))
        Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
    }

    /** 用系统浏览器打开 URL */
    fun onOpenLink(url: String, context: Context) {
        val uri = url.toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.toast_no_app_for_link), Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, context.getString(R.string.toast_no_permission_for_link), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_unknown_error_for_link), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanner.close()
    }
}
