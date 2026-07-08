package com.joat917.qrdecoderlite.viewmodel

import android.app.Application
import com.joat917.qrdecoderlite.MainDispatcherRule
import com.joat917.qrdecoderlite.TestUriFactory
import com.joat917.qrdecoderlite.model.DecodeResult
import com.joat917.qrdecoderlite.model.DecodedBarcode
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
 * - 解码失败 → decodeError 设置
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
    fun initial_decodeUiState_isNull() {
        assertNull(viewModel.decodeUiState.value)
    }

    // ── onImageSelected ────────────────────────────────────────────────

    @Test
    fun onImageSelected_null_doesNotChangeState() {
        viewModel.onImageSelected(null)

        assertNull(viewModel.uiState.value.activeSheet)
        assertNull(viewModel.decodeUiState.value)
    }

    @Test
    fun onImageSelected_validUri_setsActiveSheet() {
        val uri = TestUriFactory.create()

        viewModel.onImageSelected(uri)

        assertNotNull(viewModel.uiState.value.activeSheet)
        assertTrue(viewModel.uiState.value.activeSheet.toString().contains("Result"))
    }

    @Test
    fun onImageSelected_validUri_createsDecodeUiState() {
        val uri = TestUriFactory.create()

        viewModel.onImageSelected(uri)

        assertNotNull(viewModel.decodeUiState.value)
        assertEquals(uri, viewModel.decodeUiState.value?.imageUri)
        assertNull(viewModel.decodeUiState.value?.barcodes)
        assertNull(viewModel.decodeUiState.value?.decodeError)
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
        assertNull(viewModel.decodeUiState.value)
    }

    @Test
    fun onBackToMain_cancelsScanningJob() {
        val uri = TestUriFactory.create()
        viewModel.onImageSelected(uri)

        viewModel.onBackToMain()
        // 扫描 job 被取消，不应再有状态更新
        assertNull(viewModel.decodeUiState.value?.barcodes)
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

        assertNotNull(viewModel.decodeUiState.value?.barcodes)
        assertEquals(1, viewModel.decodeUiState.value?.barcodes?.size)
        assertEquals("https://example.com", viewModel.decodeUiState.value?.barcodes?.get(0)?.content)
        assertNull(viewModel.decodeUiState.value?.decodeError)
    }

    @Test
    fun decodeSuccess_emptyBarcodesList() {
        val uri = TestUriFactory.create()
        fakeScanner.result = DecodeResult.Success(emptyList())

        viewModel.onImageSelected(uri)

        assertNotNull(viewModel.decodeUiState.value?.barcodes)
        assertTrue(viewModel.decodeUiState.value?.barcodes?.isEmpty() ?: false)
        assertNull(viewModel.decodeUiState.value?.decodeError)
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

        assertEquals(2, viewModel.decodeUiState.value?.barcodes?.size)
        assertEquals("second", viewModel.decodeUiState.value?.barcodes?.get(1)?.content)
    }

    // ── 解码失败 ───────────────────────────────────────────────────────

    @Test
    fun decodeFailure_setsError() {
        val uri = TestUriFactory.create()
        fakeScanner.result = DecodeResult.Failure("[MlKitException] 二维码识别失败，请重试")

        viewModel.onImageSelected(uri)

        assertNotNull(viewModel.decodeUiState.value?.decodeError)
        assertTrue(viewModel.decodeUiState.value?.decodeError?.contains("MlKitException") ?: false)
        assertNull(viewModel.decodeUiState.value?.barcodes)
    }

    @Test
    fun decodeFailure_fileNotFound() {
        val uri = TestUriFactory.create()
        fakeScanner.result = DecodeResult.Failure("[FileNotFoundException] 未找到图片文件")

        viewModel.onImageSelected(uri)

        assertNotNull(viewModel.decodeUiState.value?.decodeError)
        assertTrue(viewModel.decodeUiState.value?.decodeError?.contains("FileNotFoundException") ?: false)
    }

    @Test
    fun decodeFailure_ioError() {
        val uri = TestUriFactory.create()
        fakeScanner.result = DecodeResult.Failure("[IOException] 无法读取图片")

        viewModel.onImageSelected(uri)

        assertNotNull(viewModel.decodeUiState.value?.decodeError)
        assertTrue(viewModel.decodeUiState.value?.decodeError?.contains("IOException") ?: false)
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
        assertEquals(uri2, viewModel.decodeUiState.value?.imageUri)
        assertEquals("second_result", viewModel.decodeUiState.value?.barcodes?.get(0)?.content)
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

        assertNull(viewModel.decodeUiState.value?.barcodes)
        assertNotNull(viewModel.decodeUiState.value?.decodeError)
    }

    // ── 异常流程 ───────────────────────────────────────────────────────

    @Test
    fun onBackToMain_whenAlreadyOnMain_noError() {
        viewModel.onBackToMain()
        assertNull(viewModel.uiState.value.activeSheet)
    }
}
