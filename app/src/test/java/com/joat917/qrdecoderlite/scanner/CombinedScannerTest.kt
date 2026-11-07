package com.joat917.qrdecoderlite.scanner

import com.joat917.qrdecoderlite.TestUriFactory
import com.joat917.qrdecoderlite.model.DecodeResult
import com.joat917.qrdecoderlite.model.DecodedBarcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 组合解码器的合并逻辑测试（纯函数 `combine`，不依赖 ML Kit / OpenCV 运行时）。
 */
class CombinedScannerTest {

    // ── 都成功：并集去重 ───────────────────────────────────────────────

    @Test
    fun combine_bothSuccess_mergesAndDeduplicates() {
        val mlKit = DecodeResult.Success(listOf(barcode("A"), barcode("B")))
        val wechat = DecodeResult.Success(listOf(barcode("B"), barcode("C")))

        val result = combine(mlKit, wechat)

        assertEquals(listOf("A", "B", "C"), (result as DecodeResult.Success).barcodes.map { it.content })
    }

    @Test
    fun combine_bothSuccess_distinctContents_keepsAll() {
        val mlKit = DecodeResult.Success(listOf(barcode("A")))
        val wechat = DecodeResult.Success(listOf(barcode("B")))

        val result = combine(mlKit, wechat)

        assertEquals(listOf("A", "B"), (result as DecodeResult.Success).barcodes.map { it.content })
    }

    @Test
    fun combine_emptyAndNonEmpty_returnsNonEmpty() {
        val mlKit = DecodeResult.Success(emptyList())
        val wechat = DecodeResult.Success(listOf(barcode("A")))

        val result = combine(mlKit, wechat)

        assertEquals(listOf("A"), (result as DecodeResult.Success).barcodes.map { it.content })
    }

    @Test
    fun combine_bothEmpty_returnsEmpty() {
        val result = combine(
            DecodeResult.Success(emptyList()),
            DecodeResult.Success(emptyList())
        )
        assertTrue((result as DecodeResult.Success).barcodes.isEmpty())
    }

    // ── 单侧报错：用成功的一侧 ─────────────────────────────────────────

    @Test
    fun combine_mlKitFailure_fallsBackToWeChatResults() {
        val result = combine(
            DecodeResult.Failure("[MlKitException] 识别失败"),
            DecodeResult.Success(listOf(barcode("A")))
        )
        assertEquals(listOf("A"), (result as DecodeResult.Success).barcodes.map { it.content })
    }

    @Test
    fun combine_wechatFailure_keepsMlKitResults() {
        val result = combine(
            DecodeResult.Success(listOf(barcode("A"))),
            DecodeResult.Failure("[WeChatQRCodeInitException] 引擎初始化失败")
        )
        assertEquals(listOf("A"), (result as DecodeResult.Success).barcodes.map { it.content })
    }

    @Test
    fun combine_failureWithEmptySuccess_returnsEmptySuccess() {
        val result = combine(
            DecodeResult.Success(emptyList()),
            DecodeResult.Failure("[IOException] 读取失败")
        )
        assertTrue((result as DecodeResult.Success).barcodes.isEmpty())
    }

    @Test
    fun combine_bothFailure_mergesReasons() {
        val result = combine(
            DecodeResult.Failure("[MlKitException] 识别失败"),
            DecodeResult.Failure("[WeChatQRCodeInitException] 引擎初始化失败")
        )
        val reason = (result as DecodeResult.Failure).reason
        assertTrue(reason.contains("[MlKitException] 识别失败"))
        assertTrue(reason.contains("[WeChatQRCodeInitException] 引擎初始化失败"))
        assertTrue(reason.contains("\n"))
    }

    // ── 角点 ───────────────────────────────────────────────────────────

    @Test
    fun combine_duplicateContent_keepsMlKitEntryWithItsCorners() {
        val result = combine(
            DecodeResult.Success(listOf(barcodeWithCorners("X"))),
            DecodeResult.Success(listOf(barcode("X")))
        )

        val barcodes = (result as DecodeResult.Success).barcodes
        assertEquals(1, barcodes.size)
        assertEquals(1, barcodes[0].cornerPoints.size)
    }

    @Test
    fun combine_keepsResultsWithoutCorners() {
        val result = combine(
            DecodeResult.Success(listOf(barcode("X"))),
            DecodeResult.Success(emptyList())
        )

        val barcodes = (result as DecodeResult.Success).barcodes
        assertEquals(1, barcodes.size)
        assertTrue(barcodes[0].cornerPoints.isEmpty())
    }

    // ── Helper ─────────────────────────────────────────────────────────

    private fun barcode(content: String): DecodedBarcode =
        DecodedBarcode(content = content, format = "QR_CODE", cornerPoints = emptyList())

    private fun barcodeWithCorners(content: String): DecodedBarcode =
        DecodedBarcode(
            content = content,
            format = "QR_CODE",
            cornerPoints = listOf(TestUriFactory.pointF(0f, 0f))
        )
}
