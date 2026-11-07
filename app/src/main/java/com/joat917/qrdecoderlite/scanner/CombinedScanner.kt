package com.joat917.qrdecoderlite.scanner

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.joat917.qrdecoderlite.model.DecodeResult
import com.joat917.qrdecoderlite.model.DecodedBarcode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 组合解码器：并行调用 ML Kit 与 WeChat QRCode 两个引擎，结果取并集去重。
 *
 * 主构造器接收两个引擎实例（便于测试注入 fake），次构造器接收 [Context]
 * 创建生产用的真实引擎。
 */
class CombinedScanner(
    private val mlKitScanner: BarcodeScanner,
    private val weChatScanner: BarcodeScanner
) : BarcodeScanner {

    constructor(context: Context) : this(
        MlKitScanner(context),
        WeChatQrScanner(context)
    )

    // ── 公开接口 ────────────────────────────────────────────────────────

    override suspend fun scan(uri: Uri): DecodeResult = coroutineScope {
        val mlKit = async { mlKitScanner.scan(uri) }
        val wechat = async { weChatScanner.scan(uri) }
        combine(mlKit.await(), wechat.await())
    }

    override suspend fun scan(bitmap: Bitmap): DecodeResult = coroutineScope {
        val mlKit = async { mlKitScanner.scan(bitmap) }
        val wechat = async { weChatScanner.scan(bitmap) }
        combine(mlKit.await(), wechat.await())
    }

    override fun close() {
        mlKitScanner.close()
        weChatScanner.close()
    }
}

/**
 * 合并两个引擎的解码结果。
 *
 * - 任一引擎 `Failure` → 返回 `Failure`，合并所有 reason（换行分隔）。
 * - 都 `Success` → 非空结果按 `content` 去重，ML Kit 结果在前。
 */
internal fun combine(mlKit: DecodeResult, wechat: DecodeResult): DecodeResult {
    val failures = listOfNotNull(
        (mlKit as? DecodeResult.Failure)?.reason,
        (wechat as? DecodeResult.Failure)?.reason
    )
    if (failures.isNotEmpty()) {
        return DecodeResult.Failure(failures.joinToString("\n"))
    }

    val mlKitBarcodes = (mlKit as DecodeResult.Success).barcodes
    val wechatBarcodes = (wechat as DecodeResult.Success).barcodes

    val seen = LinkedHashSet<String>()
    val merged = mutableListOf<DecodedBarcode>()
    for (barcode in mlKitBarcodes + wechatBarcodes) {
        if (seen.add(barcode.content)) {
            merged.add(barcode)
        }
    }
    return DecodeResult.Success(merged)
}
