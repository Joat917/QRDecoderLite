package com.joat917.qrdecoderlite.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import com.joat917.qrdecoderlite.model.DecodeResult
import com.joat917.qrdecoderlite.model.DecodedBarcode
import com.king.wechat.qrcode.WeChatQRCodeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.OpenCV
import org.opencv.core.Point
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CancellationException

/**
 * WeChat QRCode 实现的解码器，仅识别二维码（QR Code）。
 *
 * 封装 [com.king.wechat.qrcode.WeChatQRCodeDetector]，
 * 首次 scan 时惰性初始化 OpenCV 与检测器（线程安全、幂等）。
 */
class WeChatQrScanner(private val context: Context) : BarcodeScanner {

    // ── 公开接口 ────────────────────────────────────────────────────────

    override suspend fun scan(uri: Uri): DecodeResult = withContext(Dispatchers.IO) {
        val bitmap = try {
            loadBitmap(uri)
        } catch (e: FileNotFoundException) {
            return@withContext DecodeResult.Failure(
                "[FileNotFoundException] 未找到图片文件，文件可能已被移动或删除"
            )
        } catch (e: IOException) {
            return@withContext DecodeResult.Failure(
                "[IOException] 无法读取图片，请检查文件权限"
            )
        } catch (e: Exception) {
            return@withContext DecodeResult.Failure(
                "[${e::class.simpleName}] ${e.message}"
            )
        }
        if (bitmap == null) {
            return@withContext DecodeResult.Failure(
                "[IllegalArgumentException] 图片数据为空"
            )
        }
        try {
            decode(bitmap)
        } finally {
            // 这张 Bitmap 由本类解码产生，用完立即交给 GC；不释放会在大图上堆内存
            bitmap.recycle()
        }
    }

    override suspend fun scan(bitmap: Bitmap): DecodeResult = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled) {
            return@withContext DecodeResult.Failure(
                "[IllegalStateException] 图片数据已失效，请重新选择"
            )
        }
        decode(bitmap)   // Bitmap 由调用方持有，不在此释放
    }

    override fun close() {
        // WeChatQRCodeDetector 由 OpenCV 全局管理，无独立可释放资源。
    }

    // ── 内部处理 ────────────────────────────────────────────────────────

    /** 执行识别。Bitmap 的释放责任在调用方。 */
    private fun decode(bitmap: Bitmap): DecodeResult {
        try {
            ensureInitialized(context)
        } catch (e: Exception) {
            return DecodeResult.Failure(
                "[WeChatQRCodeInitException] 二维码引擎初始化失败，请重试"
            )
        }
        return try {
            val result = WeChatQRCodeDetector.detectAndDecodeResult(bitmap)
            DecodeResult.Success(mapToDecoded(result.texts, result.getVertexPoints()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DecodeResult.Failure(
                "[${e::class.simpleName}] ${e.message}"
            )
        }
    }

    /**
     * 将 uri 读取为 Bitmap。
     *
     * 先只读尺寸，再按长边不超过 [MAX_DECODE_SIDE] 降采样解码。
     * 直接按原图解码时，一张 4000×3000 的照片就要 48 MB，识别过程还会再翻倍。
     */
    private fun loadBitmap(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    /** 返回 2 的幂的采样率，使解码后的长边不超过 [MAX_DECODE_SIDE]。 */
    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sample = 1
        var longestSide = maxOf(width, height)
        while (longestSide > MAX_DECODE_SIDE) {
            longestSide /= 2
            sample *= 2
        }
        return sample
    }

    /**
     * 将库返回的文本与顶点映射为 [DecodedBarcode]。
     *
     * 每个二维码对应 4 个连续顶点；顶点不足的条目丢弃（保证输出字段非空）。
     */
    private fun mapToDecoded(
        texts: List<String>,
        vertices: List<Point>
    ): List<DecodedBarcode> = texts.mapIndexedNotNull { index, text ->
        val start = index * 4
        val end = start + 4
        if (end > vertices.size) {
            null
        } else {
            val points = (start until end).map {
                PointF(vertices[it].x.toFloat(), vertices[it].y.toFloat())
            }
            DecodedBarcode(content = text, format = "QR_CODE", cornerPoints = points)
        }
    }

    companion object {
        /** 送进引擎的图片长边上限，单位像素。 */
        private const val MAX_DECODE_SIDE = 2000

        /** 只在 [ensureInitialized] 的同步块内读写，不需要 @Volatile。 */
        private var initialized = false

        /** 线程安全、幂等的初始化；首次调用加载 OpenCV 与检测器。 */
        @Synchronized
        private fun ensureInitialized(context: Context) {
            if (initialized) return
            OpenCV.initOpenCV()
            WeChatQRCodeDetector.init(context)
            initialized = true
        }
    }
}
