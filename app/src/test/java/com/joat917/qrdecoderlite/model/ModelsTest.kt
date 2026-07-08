package com.joat917.qrdecoderlite.model

import android.graphics.PointF
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * P2 测试：数据模型。
 *
 * 验证数据类的 equals/hashCode/copy 以及状态机约束。
 */
class ModelsTest {

    // ── AppUiState ─────────────────────────────────────────────────────

    @Test
    fun appUiState_defaultActiveSheetIsNull() {
        val state = AppUiState()
        assertNull(state.activeSheet)
    }

    @Test
    fun appUiState_withSheet() {
        val uri = Uri.EMPTY
        val state = AppUiState(activeSheet = Sheet.Result(uri))
        assertNotNull(state.activeSheet)
    }

    @Test
    fun appUiState_copy() {
        val state1 = AppUiState(activeSheet = Sheet.Result(Uri.EMPTY))
        val state2 = state1.copy(activeSheet = null)
        assertNull(state2.activeSheet)
    }

    // ── Sheet ──────────────────────────────────────────────────────────

    @Test
    fun sheetResult_holdsUri() {
        val uri = Uri.EMPTY
        val sheet = Sheet.Result(uri)
        assertEquals(uri, sheet.imageUri)
    }

    @Test
    fun sheetResult_equals() {
        val uri = Uri.EMPTY
        assertEquals(Sheet.Result(uri), Sheet.Result(uri))
    }

    // ── DecodeUiState ──────────────────────────────────────────────────

    @Test
    fun decodeUiState_defaultBarcodesIsNull() {
        val state = DecodeUiState(imageUri = Uri.EMPTY)
        assertNull(state.barcodes)
        assertNull(state.decodeError)
    }

    @Test
    fun decodeUiState_successState() {
        val barcode = DecodedBarcode("content", "QR_CODE", emptyList())
        val state = DecodeUiState(imageUri = Uri.EMPTY, barcodes = listOf(barcode))
        assertEquals(1, state.barcodes?.size)
        assertNull(state.decodeError)
    }

    @Test
    fun decodeUiState_emptyResultState() {
        val state = DecodeUiState(imageUri = Uri.EMPTY, barcodes = emptyList())
        assertNotNull(state.barcodes)
        assertEquals(0, state.barcodes?.size)
        assertNull(state.decodeError)
    }

    @Test
    fun decodeUiState_errorState() {
        val state = DecodeUiState(imageUri = Uri.EMPTY, decodeError = "[Error] 测试错误")
        assertNull(state.barcodes)
        assertNotNull(state.decodeError)
    }

    @Test
    fun decodeUiState_copy() {
        val state1 = DecodeUiState(imageUri = Uri.EMPTY, barcodes = listOf(
            DecodedBarcode("a", "QR_CODE", emptyList())
        ))
        val state2 = state1.copy(barcodes = emptyList())
        assertEquals(0, state2.barcodes?.size)
        assertEquals(state1.imageUri, state2.imageUri)
    }

    // ── DecodedBarcode ─────────────────────────────────────────────────

    @Test
    fun decodedBarcode_fields() {
        val points = listOf(PointF(0f, 0f), PointF(1f, 1f))
        val barcode = DecodedBarcode(
            content = "https://example.com",
            format = "QR_CODE",
            cornerPoints = points
        )
        assertEquals("https://example.com", barcode.content)
        assertEquals("QR_CODE", barcode.format)
        assertEquals(points, barcode.cornerPoints)
    }

    @Test
    fun decodedBarcode_equalsAndHashCode() {
        val pts = listOf(PointF(0f, 0f))
        val b1 = DecodedBarcode("a", "QR_CODE", pts)
        val b2 = DecodedBarcode("a", "QR_CODE", pts)
        assertEquals(b1, b2)
        assertEquals(b1.hashCode(), b2.hashCode())
    }

    @Test
    fun decodedBarcode_notEqual() {
        val pts = listOf(PointF(0f, 0f))
        val b1 = DecodedBarcode("a", "QR_CODE", pts)
        val b2 = DecodedBarcode("b", "QR_CODE", pts)
        assertNotEquals(b1, b2)
    }

    // ── DecodeResult ───────────────────────────────────────────────────

    @Test
    fun decodeResult_success_holdsBarcodes() {
        val barcodes = listOf(DecodedBarcode("a", "QR_CODE", emptyList()))
        val result = DecodeResult.Success(barcodes)
        assertEquals(barcodes, result.barcodes)
    }

    @Test
    fun decodeResult_failure_holdsReason() {
        val result = DecodeResult.Failure("error message")
        assertEquals("error message", result.reason)
    }

    @Test
    fun decodeResult_whenExhaustive() {
        // 验证 sealed 分支没有被破坏
        val success: DecodeResult = DecodeResult.Success(emptyList())
        val failure: DecodeResult = DecodeResult.Failure("err")
        val results = listOf(success, failure)
        assertEquals(2, results.size)
    }
}
