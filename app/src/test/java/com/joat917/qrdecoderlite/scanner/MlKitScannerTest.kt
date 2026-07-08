package com.joat917.qrdecoderlite.scanner

import com.joat917.qrdecoderlite.TestUriFactory
import com.joat917.qrdecoderlite.model.DecodedBarcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0 测试：解码器核心逻辑。
 *
 * 测试不依赖 ML Kit 运行时，仅测试纯 Kotlin 函数：
 * - bytesToHex 格式正确性
 * - formatToString 格式映射
 * - SortableBarcode 优先级计算
 * - sortToDecoded 排序正确性
 */
class MlKitScannerTest {

    // ── bytesToHex ─────────────────────────────────────────────────────

    @Test
    fun bytesToHex_singleByte() {
        assertEquals("41", MlKitScanner.bytesToHex(byteArrayOf(0x41)))
    }

    @Test
    fun bytesToHex_multipleBytes() {
        assertEquals("41 42 43", MlKitScanner.bytesToHex(byteArrayOf(0x41, 0x42, 0x43)))
    }

    @Test
    fun bytesToHex_emptyArray() {
        assertEquals("", MlKitScanner.bytesToHex(byteArrayOf()))
    }

    @Test
    fun bytesToHex_upperCaseUsesTwoDigitsPerByte() {
        val result = MlKitScanner.bytesToHex(byteArrayOf(0x00, 0x0A, 0xFF.toByte()))
        assertEquals("00 0A FF", result)
    }

    @Test
    fun bytesToHex_allByteValues() {
        val input = byteArrayOf(0x00, 0x10, 0x80.toByte(), 0xFF.toByte())
        val result = MlKitScanner.bytesToHex(input)
        assertEquals("00 10 80 FF", result)
    }

    // ── formatToString ─────────────────────────────────────────────────

    @Test
    fun formatToString_qrCode() {
        assertEquals("QR_CODE", MlKitScanner.formatToString(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE))
    }

    @Test
    fun formatToString_code128() {
        assertEquals("CODE_128", MlKitScanner.formatToString(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128))
    }

    @Test
    fun formatToString_aztec() {
        assertEquals("AZTEC", MlKitScanner.formatToString(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_AZTEC))
    }

    @Test
    fun formatToString_pdf417() {
        assertEquals("PDF417", MlKitScanner.formatToString(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_PDF417))
    }

    @Test
    fun formatToString_dataMatrix() {
        assertEquals("DATA_MATRIX", MlKitScanner.formatToString(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_DATA_MATRIX))
    }

    @Test
    fun formatToString_upcA() {
        assertEquals("UPC_A", MlKitScanner.formatToString(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_A))
    }

    @Test
    fun formatToString_upcE() {
        assertEquals("UPC_E", MlKitScanner.formatToString(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_E))
    }

    @Test
    fun formatToString_ean8() {
        assertEquals("EAN_8", MlKitScanner.formatToString(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8))
    }

    @Test
    fun formatToString_ean13() {
        assertEquals("EAN_13", MlKitScanner.formatToString(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13))
    }

    @Test
    fun formatToString_code39() {
        assertEquals("CODE_39", MlKitScanner.formatToString(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_39))
    }

    @Test
    fun formatToString_code93() {
        assertEquals("CODE_93", MlKitScanner.formatToString(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_93))
    }

    @Test
    fun formatToString_codabar() {
        assertEquals("CODA_BAR", MlKitScanner.formatToString(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODABAR))
    }

    @Test
    fun formatToString_itf() {
        assertEquals("ITF", MlKitScanner.formatToString(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ITF))
    }

    @Test
    fun formatToString_unknownFormat_returnsUnknown() {
        assertEquals("UNKNOWN", MlKitScanner.formatToString(-1))
    }

    // ── SortableBarcode.priority ───────────────────────────────────────

    @Test
    fun priority_rawValueAndOriginalPoints_is0() {
        val sb = sortableBarcode("a", isRawValue = true, isOriginalPoints = true)
        assertEquals(0, sb.priority)
    }

    @Test
    fun priority_rawValueAndDerivedPoints_is1() {
        val sb = sortableBarcode("a", isRawValue = true, isOriginalPoints = false)
        assertEquals(1, sb.priority)
    }

    @Test
    fun priority_hexAndOriginalPoints_is2() {
        val sb = sortableBarcode("a", isRawValue = false, isOriginalPoints = true)
        assertEquals(2, sb.priority)
    }

    @Test
    fun priority_hexAndDerivedPoints_is3() {
        val sb = sortableBarcode("a", isRawValue = false, isOriginalPoints = false)
        assertEquals(3, sb.priority)
    }

    // ── sortToDecoded ──────────────────────────────────────────────────

    @Test
    fun sortToDecoded_ordersByPriority() {
        val p3 = sortableBarcode("p3", isRawValue = false, isOriginalPoints = false)
        val p0 = sortableBarcode("p0", isRawValue = true, isOriginalPoints = true)
        val p1 = sortableBarcode("p1", isRawValue = true, isOriginalPoints = false)

        val sorted = listOf(p3, p0, p1).sortToDecoded()
        assertEquals(listOf("p0", "p1", "p3"), sorted.map { it.content })
    }

    @Test
    fun sortToDecoded_fourPrioritiesAllPresent() {
        val p0 = sortableBarcode("p0", isRawValue = true, isOriginalPoints = true)
        val p1 = sortableBarcode("p1", isRawValue = true, isOriginalPoints = false)
        val p2 = sortableBarcode("p2", isRawValue = false, isOriginalPoints = true)
        val p3 = sortableBarcode("p3", isRawValue = false, isOriginalPoints = false)

        val sorted = listOf(p3, p2, p1, p0).sortToDecoded()
        assertEquals(listOf("p0", "p1", "p2", "p3"), sorted.map { it.content })
    }

    @Test
    fun sortToDecoded_samePriority_keepsOriginalOrder() {
        val a = sortableBarcode("a", isRawValue = true, isOriginalPoints = true)
        val b = sortableBarcode("b", isRawValue = true, isOriginalPoints = true)
        val c = sortableBarcode("c", isRawValue = true, isOriginalPoints = true)

        val sorted = listOf(a, b, c).sortToDecoded()
        assertEquals(listOf("a", "b", "c"), sorted.map { it.content })
    }

    @Test
    fun sortToDecoded_samePriority_reverseInput() {
        val x = sortableBarcode("x", isRawValue = true, isOriginalPoints = true)
        val y = sortableBarcode("y", isRawValue = true, isOriginalPoints = true)

        val sortedForward = listOf(x, y).sortToDecoded()
        val sortedReverse = listOf(y, x).sortToDecoded()
        // 同优先级保持原始顺序
        assertEquals(listOf("x", "y"), sortedForward.map { it.content })
        assertEquals(listOf("y", "x"), sortedReverse.map { it.content })
    }

    @Test
    fun sortToDecoded_emptyInput_returnsEmpty() {
        assertTrue(emptyList<SortableBarcode>().sortToDecoded().isEmpty())
    }

    @Test
    fun sortToDecoded_singleItem() {
        val sb = sortableBarcode("only", isRawValue = true, isOriginalPoints = true)
        val sorted = listOf(sb).sortToDecoded()
        assertEquals(1, sorted.size)
        assertEquals("only", sorted[0].content)
    }

    @Test
    fun sortToDecoded_decodedFieldsPreserved() {
        val pts = listOf(TestUriFactory.pointF(1f, 2f), TestUriFactory.pointF(3f, 4f))
        val decoded = DecodedBarcode(content = "data", format = "QR_CODE", cornerPoints = pts)
        val sb = SortableBarcode(decoded = decoded, isRawValue = true, isOriginalPoints = false)

        val result = listOf(sb).sortToDecoded()
        assertEquals("data", result[0].content)
        assertEquals("QR_CODE", result[0].format)
        assertEquals(pts, result[0].cornerPoints)
    }

    @Test
    fun sortToDecoded_nullSafety_decodedNeverNull() {
        val items = listOf(
            sortableBarcode("a", isRawValue = true, isOriginalPoints = true),
            sortableBarcode("b", isRawValue = false, isOriginalPoints = true)
        )
        val result = items.sortToDecoded()
        assertNotNull(result[0])
        assertNotNull(result[1])
    }

    // ── Helper ─────────────────────────────────────────────────────────

    private fun sortableBarcode(
        content: String,
        isRawValue: Boolean,
        isOriginalPoints: Boolean
    ): SortableBarcode = SortableBarcode(
        decoded = DecodedBarcode(
            content = content,
            format = "QR_CODE",
            cornerPoints = listOf(TestUriFactory.pointF(0f, 0f))
        ),
        isRawValue = isRawValue,
        isOriginalPoints = isOriginalPoints
    )
}
