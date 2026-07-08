package com.joat917.qrdecoderlite.scanner

import android.graphics.Bitmap
import android.net.Uri
import com.joat917.qrdecoderlite.model.DecodeResult

/**
 * 解码器接口，抽象二维码/条码解码能力。
 *
 * 生产环境使用 [MlKitScanner]（ML Kit 实现），
 * 测试环境可传入 mock/fake 实现以验证 ViewModel 状态流转。
 */
interface BarcodeScanner {

    /** 从图片文件 URI 解码，自动处理文件读取和 EXIF 校正。 */
    suspend fun scan(uri: Uri): DecodeResult

    /** 从 Bitmap 解码（为未来相机帧预留）。 */
    suspend fun scan(bitmap: Bitmap): DecodeResult

    /** 释放底层资源。调用后不应再执行 scan。 */
    fun close()
}
