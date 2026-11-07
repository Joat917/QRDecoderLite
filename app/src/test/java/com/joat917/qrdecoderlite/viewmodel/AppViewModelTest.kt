package com.joat917.qrdecoderlite.viewmodel

import android.app.Application
import com.joat917.qrdecoderlite.MainDispatcherRule
import com.joat917.qrdecoderlite.TestUriFactory
import com.joat917.qrdecoderlite.model.DecodeResult
import com.joat917.qrdecoderlite.model.DecodedBarcode
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

/**
 * P0 测试：ViewModel 状态转换。
 *
 * 用 [FakeBarcodeScanner] 替换真实解码器，验证：
 * - 初始状态
 * - 取消选择（null URI）
 * - 返回主界面
 * - 解码成功 → barcodes 更新
 * - 解码失败、解码抛异常 → decodeError 设置
 */
class AppViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var application: Application
    private lateinit var fakeScanner: FakeBarcodeScanner
    private lateinit var viewModel: AppViewModel

    @Before
    fun setUp() {
        application = Mockito.mock(Application::class.java)
        fakeScanner = FakeBarcodeScanner()
        viewModel = AppViewModel(application, fakeScanner)
    }

    @After
    fun tearDown() {
        fakeScanner.close()
    }

    // ── 初始状态 ───────────────────────────────────────────────────────

    @Test
    fun initial_uiState_activeSheetIsNull() {
        assertNull(viewModel.uiState.value.activeSheet)
    }

    @Test
    fun initial_decodeUiState_hasNoBarcodes() {
        assertNull(viewModel.decodeUiState.value.barcodes)
        assertNull(viewModel.decodeUiState.value.decodeError)
    }

    // ── onImageSelected ────────────────────────────────────────────────

    @Test
    fun onImageSelected_null_doesNotChangeState() {
        viewModel.onImageSelected(null)

        assertNull(viewModel.uiState.value.activeSheet)
        assertNull(viewModel.decodeUiState.value.barcodes)
    }

    @Test
    fun onImageSelected_validUri_setsActiveSheet() {
        val uri = TestUriFactory.create()

        viewModel.onImageSelected(uri)

        assertNotNull(viewModel.uiState.value.activeSheet)
        assertTrue(viewModel.uiState.value.activeSheet.toString().contains("Result"))
    }

    @Test
    fun onImageSelected_validUri_showsDecodingStateUntilScanFinishes() {
        val uri = TestUriFactory.create()
        val gate = CompletableDeferred<Unit>()
        fakeScanner.gate = gate
        fakeScanner.result = DecodeResult.Success(
            listOf(DecodedBarcode("late", "QR_CODE", emptyList()))
        )

        viewModel.onImageSelected(uri)

        // 扫描还没返回 → 结果页停在解码中
        assertNull(viewModel.decodeUiState.value.barcodes)
        assertNull(viewModel.decodeUiState.value.decodeError)

        gate.complete(Unit)

        // 扫描返回 → 结果写入
        assertEquals("late", viewModel.decodeUiState.value.barcodes?.get(0)?.content)
    }

    @Test
    fun onImageSelected_validUri_triggersScan() {
        val uri = TestUriFactory.create()
        fakeScanner.result = DecodeResult.Success(
            listOf(DecodedBarcode(content = "hello", format = "QR_CODE", cornerPoints = emptyList()))
        )

        viewModel.onImageSelected(uri)

        assertEquals(uri, fakeScanner.lastScannedUri)
    }

    // ── onBackToMain ───────────────────────────────────────────────────

    @Test
    fun onBackToMain_resetsState() {
        // 先进入结果页
        val uri = TestUriFactory.create()
        viewModel.onImageSelected(uri)
        assertNotNull(viewModel.uiState.value.activeSheet)

        // 返回主界面
        viewModel.onBackToMain()

        assertNull(viewModel.uiState.value.activeSheet)
        assertNull(viewModel.decodeUiState.value.barcodes)
    }

    @Test
    fun onBackToMain_cancelsScanningJob() {
        val uri = TestUriFactory.create()
        viewModel.onImageSelected(uri)

        viewModel.onBackToMain()
        // 扫描 job 被取消，不应再有状态更新
        assertNull(viewModel.decodeUiState.value.barcodes)
    }

    // ── 解码完成（成功） ────────────────────────────────────────────────

    @Test
    fun decodeSuccess_updatesBarcodes() {
        val uri = TestUriFactory.create()
        val expectedBarcode = DecodedBarcode(
            content = "https://example.com",
            format = "QR_CODE",
            cornerPoints = listOf(android.graphics.PointF(0f, 0f))
        )
        fakeScanner.result = DecodeResult.Success(listOf(expectedBarcode))

        viewModel.onImageSelected(uri)

        assertNotNull(viewModel.decodeUiState.value.barcodes)
        assertEquals(1, viewModel.decodeUiState.value.barcodes?.size)
        assertEquals("https://example.com", viewModel.decodeUiState.value.barcodes?.get(0)?.content)
        assertNull(viewModel.decodeUiState.value.decodeError)
    }

    @Test
    fun decodeSuccess_emptyBarcodesList() {
        val uri = TestUriFactory.create()
        fakeScanner.result = DecodeResult.Success(emptyList())

        viewModel.onImageSelected(uri)

        assertNotNull(viewModel.decodeUiState.value.barcodes)
        assertTrue(viewModel.decodeUiState.value.barcodes?.isEmpty() ?: false)
        assertNull(viewModel.decodeUiState.value.decodeError)
    }

    @Test
    fun decodeSuccess_multipleBarcodes() {
        val uri = TestUriFactory.create()
        val barcodes = listOf(
            DecodedBarcode("first", "QR_CODE", listOf(android.graphics.PointF(0f, 0f))),
            DecodedBarcode("second", "CODE_128", listOf(android.graphics.PointF(1f, 1f)))
        )
        fakeScanner.result = DecodeResult.Success(barcodes)

        viewModel.onImageSelected(uri)

        assertEquals(2, viewModel.decodeUiState.value.barcodes?.size)
        assertEquals("second", viewModel.decodeUiState.value.barcodes?.get(1)?.content)
    }

    // ── 解码失败 ───────────────────────────────────────────────────────

    @Test
    fun decodeFailure_setsError() {
        val uri = TestUriFactory.create()
        fakeScanner.result = DecodeResult.Failure("[MlKitException] 二维码识别失败，请重试")

        viewModel.onImageSelected(uri)

        assertNotNull(viewModel.decodeUiState.value.decodeError)
        assertTrue(viewModel.decodeUiState.value.decodeError?.contains("MlKitException") ?: false)
        assertNull(viewModel.decodeUiState.value.barcodes)
    }

    @Test
    fun decodeFailure_fileNotFound() {
        val uri = TestUriFactory.create()
        fakeScanner.result = DecodeResult.Failure("[FileNotFoundException] 未找到图片文件")

        viewModel.onImageSelected(uri)

        assertNotNull(viewModel.decodeUiState.value.decodeError)
        assertTrue(viewModel.decodeUiState.value.decodeError?.contains("FileNotFoundException") ?: false)
    }

    @Test
    fun decodeFailure_ioError() {
        val uri = TestUriFactory.create()
        fakeScanner.result = DecodeResult.Failure("[IOException] 无法读取图片")

        viewModel.onImageSelected(uri)

        assertNotNull(viewModel.decodeUiState.value.decodeError)
        assertTrue(viewModel.decodeUiState.value.decodeError?.contains("IOException") ?: false)
    }

    @Test
    fun scanThrows_setsDecodeErrorInsteadOfCrashing() {
        val uri = TestUriFactory.create()
        fakeScanner.error = SecurityException("读取被拒绝")

        viewModel.onImageSelected(uri)

        assertNull(viewModel.decodeUiState.value.barcodes)
        assertNotNull(viewModel.decodeUiState.value.decodeError)
        // 用户看到的是异常自带的 message，不是包装后的套话
        assertEquals("[SecurityException] 读取被拒绝", viewModel.decodeUiState.value.decodeError)
    }

    // ── 连续选图 ───────────────────────────────────────────────────────

    @Test
    fun consecutiveImageSelection_secondResultOverridesFirst() {
        val uri1 = TestUriFactory.create()
        val uri2 = TestUriFactory.create()
        fakeScanner.result = DecodeResult.Success(
            listOf(DecodedBarcode("first_result", "QR_CODE", emptyList()))
        )

        viewModel.onImageSelected(uri1)

        fakeScanner.result = DecodeResult.Success(
            listOf(DecodedBarcode("second_result", "QR_CODE", emptyList()))
        )

        viewModel.onImageSelected(uri2)

        // 最终状态的 barcodes 应来自第二次选图
        assertEquals("second_result", viewModel.decodeUiState.value.barcodes?.get(0)?.content)
    }

    @Test
    fun consecutiveImageSelection_secondFailureOverridesFirstSuccess() {
        val uri1 = TestUriFactory.create()
        val uri2 = TestUriFactory.create()
        fakeScanner.result = DecodeResult.Success(
            listOf(DecodedBarcode("first", "QR_CODE", emptyList()))
        )

        viewModel.onImageSelected(uri1)

        fakeScanner.result = DecodeResult.Failure("[IOException] 读取错误")
        viewModel.onImageSelected(uri2)

        assertNull(viewModel.decodeUiState.value.barcodes)
        assertNotNull(viewModel.decodeUiState.value.decodeError)
    }

    // ── 异常流程 ───────────────────────────────────────────────────────

    @Test
    fun onBackToMain_whenAlreadyOnMain_noError() {
        viewModel.onBackToMain()
        assertNull(viewModel.uiState.value.activeSheet)
    }
}
