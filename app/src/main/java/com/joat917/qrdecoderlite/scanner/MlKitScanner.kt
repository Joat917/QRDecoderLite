package com.joat917.qrdecoderlite.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.joat917.qrdecoderlite.model.DecodeResult
import com.joat917.qrdecoderlite.model.DecodedBarcode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CancellationException

/**
 * ML Kit 实现的解码器。
 *
 * 封装 [com.google.mlkit.vision.barcode.BarcodeScanner]，
 * 处理异常映射、空值过滤、排序。
 */
class MlKitScanner(private val context: Context) : BarcodeScanner {

    private val barcodeScanner = BarcodeScanning.getClient()

    // ── 公开接口 ────────────────────────────────────────────────────────

    override suspend fun scan(uri: Uri): DecodeResult = withContext(Dispatchers.IO) {
        val image = try {
            InputImage.fromFilePath(context, uri)
        } catch (e: FileNotFoundException) {
            return@withContext DecodeResult.Failure(
                "[FileNotFoundException] 未找到图片文件，文件可能已被移动或删除"
            )
        } catch (e: IOException) {
            return@withContext DecodeResult.Failure(
                "[IOException] 无法读取图片，请检查文件权限"
            )
        } catch (e: IllegalArgumentException) {
            return@withContext DecodeResult.Failure(
                "[IllegalArgumentException] 不支持的图片格式或无效的文件路径"
            )
        } catch (e: Exception) {
            return@withContext DecodeResult.Failure(
                "[${e::class.simpleName}] ${e.message}"
            )
        }
        processImage(image)
    }

    override suspend fun scan(bitmap: Bitmap): DecodeResult = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled) {
            return@withContext DecodeResult.Failure(
                "[IllegalStateException] 图片数据已失效，请重新选择"
            )
        }
        val image = InputImage.fromBitmap(bitmap, 0)
        processImage(image)
    }

    override fun close() {
        barcodeScanner.close()
    }

    // ── 内部处理 ────────────────────────────────────────────────────────

    private suspend fun processImage(image: InputImage): DecodeResult {
        return try {
            val rawBarcodes = barcodeScanner.process(image).await()
            val decoded = rawBarcodes.mapNotNull { it.toSortable() }
            val sorted = decoded.sortToDecoded()
            DecodeResult.Success(sorted)
        } catch (e: CancellationException) {
            throw e   // 静默终止，由调用方协程框架处理
        } catch (e: com.google.mlkit.common.MlKitException) {
            DecodeResult.Failure(
                "[MlKitException] 二维码识别失败，请重试"
            )
        } catch (e: Exception) {
            DecodeResult.Failure(
                "[${e::class.simpleName}] ${e.message}"
            )
        }
    }

    // ── Barcode → 中间排序对象 ─────────────────────────────────────────

    private fun Barcode.toSortable(): SortableBarcode? {
        // Barcode 是 Java 对象，属性不能智能转换，先取到本地变量
        val rawValue = rawValue
        val rawBytes = rawBytes

        // ── 确定 content ──
        val content: String
        val isRawValue: Boolean
        when {
            rawValue != null -> {
                content = rawValue
                isRawValue = true
            }
            rawBytes != null -> {
                content = bytesToHex(rawBytes)
                isRawValue = false
            }
            else -> return null   // 二者皆 null → 丢弃
        }

        // ── 确定 cornerPoints ──
        val cornerPoints = cornerPoints
        val boundingBox = boundingBox
        val points: List<PointF>
        val isOriginalPoints: Boolean
        when {
            cornerPoints != null -> {
                points = cornerPoints.map { PointF(it.x.toFloat(), it.y.toFloat()) }
                isOriginalPoints = true
            }
            boundingBox != null -> {
                points = listOf(
                    PointF(boundingBox.left.toFloat(), boundingBox.top.toFloat()),
                    PointF(boundingBox.right.toFloat(), boundingBox.top.toFloat()),
                    PointF(boundingBox.right.toFloat(), boundingBox.bottom.toFloat()),
                    PointF(boundingBox.left.toFloat(), boundingBox.bottom.toFloat())
                )
                isOriginalPoints = false
            }
            else -> return null   // 二者皆 null → 丢弃
        }

        return SortableBarcode(
            decoded = DecodedBarcode(
                content = content,
                format = formatToString(format),
                cornerPoints = points
            ),
            isRawValue = isRawValue,
            isOriginalPoints = isOriginalPoints
        )
    }

    // ── 工具函数 ────────────────────────────────────────────────────────

    companion object {
        internal fun bytesToHex(bytes: ByteArray): String =
            bytes.joinToString(" ") { "%02X".format(it) }

        internal fun formatToString(format: Int): String = when (format) {
            Barcode.FORMAT_QR_CODE -> "QR_CODE"
            Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
            Barcode.FORMAT_UPC_A -> "UPC_A"
            Barcode.FORMAT_UPC_E -> "UPC_E"
            Barcode.FORMAT_EAN_8 -> "EAN_8"
            Barcode.FORMAT_EAN_13 -> "EAN_13"
            Barcode.FORMAT_CODE_39 -> "CODE_39"
            Barcode.FORMAT_CODE_93 -> "CODE_93"
            Barcode.FORMAT_CODE_128 -> "CODE_128"
            Barcode.FORMAT_CODABAR -> "CODA_BAR"
            Barcode.FORMAT_ITF -> "ITF"
            Barcode.FORMAT_AZTEC -> "AZTEC"
            Barcode.FORMAT_PDF417 -> "PDF417"
            else -> "UNKNOWN"
        }
    }
}

// ── 测试暴露：排序对象与排序函数 ─────────────────────────────────────

/**
 * 中间排序对象。由 [MlKitScanner] 内部构建，此处为 internal
 * 以便单元测试直接构造并验证排序逻辑。
 */
internal data class SortableBarcode(
    val decoded: DecodedBarcode,
    val isRawValue: Boolean,
    val isOriginalPoints: Boolean
) {
    /**
     * 优先级（值越小优先级越高）：
     * 0 = rawValue + 原始角点
     * 1 = rawValue + 推算角点
     * 2 = rawBytes  + 原始角点
     * 3 = rawBytes  + 推算角点
     */
    val priority: Int get() = when {
        isRawValue && isOriginalPoints -> 0
        isRawValue && !isOriginalPoints -> 1
        !isRawValue && isOriginalPoints -> 2
        else -> 3
    }
}

/** 按优先级排序后提取最终解码列表。 */
internal fun List<SortableBarcode>.sortToDecoded(): List<DecodedBarcode> =
    sortedBy { it.priority }.map { it.decoded }
