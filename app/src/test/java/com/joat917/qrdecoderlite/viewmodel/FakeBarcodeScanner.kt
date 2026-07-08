package com.joat917.qrdecoderlite.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import com.joat917.qrdecoderlite.model.DecodeResult
import com.joat917.qrdecoderlite.scanner.BarcodeScanner

/**
 * 测试用的 fake 解码器，不依赖 ML Kit。
 *
 * 通过 [result] 控制返回结果，测试 ViewModel 对不同解码结果的响应。
 */
class FakeBarcodeScanner : BarcodeScanner {

    /** 每次 scan 调用时返回的结果。测试中可随时修改。 */
    var result: DecodeResult = DecodeResult.Success(emptyList())

    /** 记录最近一次 scan 收到的 URI，用于验证调用参数。 */
    var lastScannedUri: Uri? = null

    override suspend fun scan(uri: Uri): DecodeResult {
        lastScannedUri = uri
        return result
    }

    override suspend fun scan(bitmap: Bitmap): DecodeResult {
        return result
    }

    override fun close() {
        // No-op for tests
    }
}
