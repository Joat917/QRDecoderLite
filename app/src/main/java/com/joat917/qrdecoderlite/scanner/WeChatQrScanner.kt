package com.joat917.qrdecoderlite.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import android.util.Log
import com.joat917.qrdecoderlite.R
import com.joat917.qrdecoderlite.model.DecodeResult
import com.joat917.qrdecoderlite.model.DecodedBarcode
import com.joat917.qrdecoderlite.util.toErrorReason
import com.king.wechat.qrcode.WeChatQRCodeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.OpenCV
import org.opencv.core.Point
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CancellationException

private const val TAG = "WeChatQrScanner"

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
        } catch (e: ImageLoadException) {
            // 具体原因已由 loadBitmap 记入日志，这里只把文字交给用户
            return@withContext DecodeResult.Failure(e.message.orEmpty())
        } catch (e: FileNotFoundException) {
            return@withContext fail("load $uri", e, R.string.error_file_not_found)
        } catch (e: IOException) {
            return@withContext fail("load $uri", e, R.string.error_io_read)
        } catch (e: Exception) {
            return@withContext fail("load $uri", e, null)
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
            Log.w(TAG, "scan rejected: bitmap is recycled")
            return@withContext DecodeResult.Failure(
                "[IllegalStateException] ${context.getString(R.string.error_image_recycled)}"
            )
        }
        decode(bitmap)   // Bitmap 由调用方持有，不在此释放
    }

    override fun close() {
        // WeChatQRCodeDetector 由 OpenCV 全局管理，无独立可释放资源。
    }

    // ── 内部处理 ────────────────────────────────────────────────────────

    /**
     * 记录异常并转成用户可见的失败原因。
     *
     * 日志带完整异常对象，用户看到的是异常自带的 message；只有 message 为空时
     * 才用 [fallbackRes] 的文字，避免用套话盖掉真实原因。
     */
    private fun fail(stage: String, e: Throwable, fallbackRes: Int?): DecodeResult.Failure {
        Log.w(TAG, stage, e)
        val fallback = fallbackRes?.let { context.getString(it) }
        return DecodeResult.Failure(e.toErrorReason(fallback))
    }

    /** 执行识别。Bitmap 的释放责任在调用方。 */
    private fun decode(bitmap: Bitmap): DecodeResult {
        try {
            ensureInitialized(context)
        } catch (e: Exception) {
            return fail("init OpenCV / WeChatQRCodeDetector", e, R.string.error_engine_init_failed)
        }
        return try {
            val result = WeChatQRCodeDetector.detectAndDecodeResult(bitmap)
            val decoded = mapToDecoded(result.texts, result.getVertexPoints())
            if (decoded.size < result.texts.size) {
                Log.d(
                    TAG,
                    "dropped ${result.texts.size - decoded.size} of ${result.texts.size} codes " +
                        "with incomplete vertex points"
                )
            }
            DecodeResult.Success(decoded)
        } catch (e: CancellationException) {
            Log.d(TAG, "scan cancelled")
            throw e
        } catch (e: Exception) {
            fail("detect and decode", e, null)
        }
    }

    /**
     * 将 uri 读取为 Bitmap。
     *
     * 只打开一次流、把字节读进内存后再解码两次（一次读尺寸、一次按采样率解码）。
     * 有些 ContentProvider 给的是一次性流，重复打开会失败，因此不能分两次 open。
     */
    private fun loadBitmap(uri: Uri): Bitmap {
        val stream = context.contentResolver.openInputStream(uri)
        if (stream == null) {
            Log.w(TAG, "openInputStream returned null: $uri")
            throw ImageLoadException(context.getString(R.string.error_image_unreadable))
        }
        val bytes = stream.use { it.readBytes() }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(
                TAG,
                "cannot read bounds: $uri (${bytes.size} bytes, " +
                    "${bounds.outWidth}x${bounds.outHeight})"
            )
            throw ImageLoadException(context.getString(R.string.error_image_undecodable))
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: run {
                Log.w(TAG, "decode returned null: $uri (${bytes.size} bytes)")
                throw ImageLoadException(context.getString(R.string.error_image_undecodable))
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

/** 读图失败：message 直接作为用户可见原因，详细原因由 [WeChatQrScanner] 记入日志。 */
private class ImageLoadException(message: String) : Exception(message)
