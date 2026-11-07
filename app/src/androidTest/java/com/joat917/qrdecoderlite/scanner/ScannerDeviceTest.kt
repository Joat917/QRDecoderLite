package com.joat917.qrdecoderlite.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.joat917.qrdecoderlite.model.DecodeResult
import com.joat917.qrdecoderlite.model.DecodedBarcode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/**
 * 解码器的设备端测试：需要真实的 `Bitmap`、真实的 OpenCV so、真实的 ML Kit 模型。
 *
 * 分两类：
 * 1. 合并逻辑（纯函数 `combine`，但这里用真实 `PointF`，因此不能放在 JVM 单元测试）；
 * 2. 端到端识别，依赖 `app/src/androidTest/assets/qr/` 下的样图，缺图时用例记为跳过。
 */
@RunWith(AndroidJUnit4::class)
class ScannerDeviceTest {

    private lateinit var scanner: CombinedScanner

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        scanner = CombinedScanner(context)
    }

    @After
    fun tearDown() {
        scanner.close()
    }

    // ── 合并逻辑 ───────────────────────────────────────────────────────

    @Test
    fun combine_duplicateContent_keepsMlKitEntryWithItsPoints() {
        val mlKit = DecodeResult.Success(listOf(barcode("X", 10f)))
        val wechat = DecodeResult.Success(listOf(barcode("X", 20f)))

        val result = combine(mlKit, wechat)

        val barcodes = (result as DecodeResult.Success).barcodes
        assertEquals(1, barcodes.size)
        assertEquals(10f, barcodes[0].cornerPoints[0].x, 0.001f)
    }

    // ── 前置检查与错误映射 ─────────────────────────────────────────────

    @Test
    fun scan_recycledBitmap_returnsFailure() {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.recycle()

        val result = runBlocking { scanner.scan(bitmap) }

        assertTrue(result is DecodeResult.Failure)
        assertTrue((result as DecodeResult.Failure).reason.contains("IllegalStateException"))
    }

    @Test
    fun scan_missingFileUri_returnsFileNotFoundFailure() {
        val missing = Uri.fromFile(File(context.cacheDir, "not_there_${System.nanoTime()}.png"))

        val result = runBlocking { scanner.scan(missing) }

        assertTrue(result is DecodeResult.Failure)
        assertTrue((result as DecodeResult.Failure).reason.contains("FileNotFoundException"))
    }

    @Test
    fun scan_blankImage_returnsEmptySuccess() {
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        val result = runBlocking { scanner.scan(bitmap) }

        assertTrue(result is DecodeResult.Success)
        assertTrue((result as DecodeResult.Success).barcodes.isEmpty())
    }

    // ── 真实图片识别 ───────────────────────────────────────────────────

    @Test
    fun scan_qrUrlImage_returnsContentAndPoints() {
        val result = runBlocking { scanner.scan(loadAsset("qr/url.png")) }

        val barcodes = (result as DecodeResult.Success).barcodes
        assertTrue("应至少识别出一个码", barcodes.isNotEmpty())
        assertTrue(
            "识别内容应含 example.com，实际为 ${barcodes.map { it.content }}",
            barcodes.any { it.content.contains("example.com") }
        )
        assertTrue("每个结果都应带四个角点", barcodes.all { it.cornerPoints.size >= 3 })
    }

    @Test
    fun scan_ean13Image_recognizedByMlKit() {
        val result = runBlocking { scanner.scan(loadAsset("qr/ean13.png")) }

        val barcodes = (result as DecodeResult.Success).barcodes
        assertTrue("一维码应由 ML Kit 识别", barcodes.any { it.format == "EAN_13" })
    }

    @Test
    fun scan_imageWithoutCode_returnsEmptySuccess() {
        val result = runBlocking { scanner.scan(loadAsset("qr/no_code.jpg")) }

        assertTrue(result is DecodeResult.Success)
        assertTrue((result as DecodeResult.Success).barcodes.isEmpty())
    }

    @Test
    fun scan_multiCodeImage_returnsAllCodes() {
        val result = runBlocking { scanner.scan(loadAsset("qr/multi_3.png")) }

        val barcodes = (result as DecodeResult.Success).barcodes
        assertTrue("应识别出至少 3 个码，实际 ${barcodes.size}", barcodes.size >= 3)
        assertEquals("去重后内容不应重复", barcodes.size, barcodes.map { it.content }.distinct().size)
    }

    // ── 辅助 ───────────────────────────────────────────────────────────

    private fun barcode(content: String, x: Float): DecodedBarcode =
        DecodedBarcode(
            content = content,
            format = "QR_CODE",
            cornerPoints = listOf(PointF(x, 0f), PointF(x, 10f), PointF(x + 10f, 10f))
        )

    /**
     * 从 androidTest 的 assets 读取样图。
     * 缺少文件时把用例标记为跳过，而不是失败，避免样图未准备时整批报红。
     */
    private fun loadAsset(name: String): Bitmap {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val exists = try {
            assets.open(name).close()
            true
        } catch (_: IOException) {
            false
        }
        assumeTrue("缺少样图 $name，见 app/src/androidTest/assets/README.md", exists)
        return assets.open(name).use { BitmapFactory.decodeStream(it) }
    }
}
