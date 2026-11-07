package com.joat917.qrdecoderlite.scanner

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.joat917.qrdecoderlite.model.DecodeResult
import com.joat917.qrdecoderlite.model.DecodedBarcode
import com.joat917.qrdecoderlite.util.AppLog
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private const val COMPONENT = "Combined"

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
        awaitAndLog("uri=$uri", mlKit, wechat)
    }

    override suspend fun scan(bitmap: Bitmap): DecodeResult = coroutineScope {
        val mlKit = async { mlKitScanner.scan(bitmap) }
        val wechat = async { weChatScanner.scan(bitmap) }
        awaitAndLog("image=${bitmap.width}x${bitmap.height}", mlKit, wechat)
    }

    override fun close() {
        mlKitScanner.close()
        weChatScanner.close()
    }

    /** 汇总两个引擎的结果再合并，同时留下一条能还原「谁给了什么」的日志。 */
    private suspend fun awaitAndLog(
        input: String,
        mlKit: Deferred<DecodeResult>,
        wechat: Deferred<DecodeResult>
    ): DecodeResult {
        val mlKitResult = mlKit.await()
        val wechatResult = wechat.await()
        val merged = combine(mlKitResult, wechatResult)
        AppLog.d(
            COMPONENT,
            "scan $input mlkit=${mlKitResult.describe()} " +
                "wechat=${wechatResult.describe()} merged=${merged.describe()}"
        )
        return merged
    }
}

private fun DecodeResult.describe(): String = when (this) {
    is DecodeResult.Success -> "success(${barcodes.size})"
    is DecodeResult.Failure -> "failure"
}

/**
 * 合并两个引擎的解码结果。
 *
 * - 只要有引擎成功，就用成功的一侧：失败的引擎只体现在日志里，不再让整体失败，
 *   否则一个引擎读图失败就会把另一个引擎已经认出的结果丢掉。
 * - ML Kit 的结果排在前，按 `content` 去重时保留 ML Kit 的条目（及其角点）。
 * - 两个引擎都失败才返回 `Failure`，reason 以换行拼接。
 *
 * 角点可能为空：引擎只负责原样交出文本与位置点，是否画多边形由渲染层决定。
 */
internal fun combine(mlKit: DecodeResult, wechat: DecodeResult): DecodeResult {
    val mlKitBarcodes = (mlKit as? DecodeResult.Success)?.barcodes
    val wechatBarcodes = (wechat as? DecodeResult.Success)?.barcodes

    if (mlKitBarcodes == null && wechatBarcodes == null) {
        val reasons = listOfNotNull(
            (mlKit as? DecodeResult.Failure)?.reason,
            (wechat as? DecodeResult.Failure)?.reason
        )
        return DecodeResult.Failure(reasons.joinToString("\n"))
    }

    val seen = LinkedHashSet<String>()
    val merged = mutableListOf<DecodedBarcode>()
    for (barcode in (mlKitBarcodes.orEmpty() + wechatBarcodes.orEmpty())) {
        if (seen.add(barcode.content)) {
            merged.add(barcode)
        }
    }
    return DecodeResult.Success(merged)
}
