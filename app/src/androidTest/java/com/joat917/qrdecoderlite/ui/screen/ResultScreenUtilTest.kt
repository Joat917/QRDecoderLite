package com.joat917.qrdecoderlite.ui.screen

import android.graphics.PointF
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 结果页纯函数的设备端测试。
 *
 * 这些函数依赖 Android 的真实实现（`PointF` 的坐标、`Color` 的取值），
 * 在 JVM 单元测试里只能拿到默认值（坐标 0、颜色 0），因此放在 androidTest。
 *
 * 覆盖：URL 判定、调色板循环、多边形命中（含边与顶点）。
 */
@RunWith(AndroidJUnit4::class)
class ResultScreenUtilTest {

    private val square = polygon(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f)
    private val triangle = polygon(0f, 0f, 10f, 0f, 5f, 10f)
    private val quad = polygon(2f, 1f, 8f, 2f, 9f, 9f, 1f, 8f)
    private val diamond = polygon(5f, 0f, 10f, 5f, 5f, 10f, 0f, 5f)

    // ── isUrl ──────────────────────────────────────────────────────────

    @Test
    fun isUrl_httpScheme() {
        assertTrue("http://example.com".isUrl())
    }

    @Test
    fun isUrl_httpsScheme() {
        assertTrue("https://example.com".isUrl())
    }

    @Test
    fun isUrl_httpsWithPathQueryAndFragment() {
        assertTrue("https://example.com/path/to/page?query=1&param=2#fragment".isUrl())
    }

    @Test
    fun isUrl_httpsWithPort() {
        assertTrue("https://example.com:443/api".isUrl())
    }

    @Test
    fun isUrl_schemeOnly_countsAsUrl() {
        // 只做前缀判断，不校验域名
        assertTrue("http://".isUrl())
        assertTrue("https://".isUrl())
    }

    @Test
    fun isUrl_wwwWithoutScheme() {
        assertFalse("www.example.com".isUrl())
    }

    @Test
    fun isUrl_plainText() {
        assertFalse("Hello World".isUrl())
        assertFalse("1234567890".isUrl())
    }

    @Test
    fun isUrl_emptyString() {
        assertFalse("".isUrl())
    }

    @Test
    fun isUrl_missingColon() {
        assertFalse("http".isUrl())
        assertFalse("https".isUrl())
    }

    @Test
    fun isUrl_otherSchemes() {
        assertFalse("ftp://example.com".isUrl())
        assertFalse("file:///sdcard/a.png".isUrl())
    }

    @Test
    fun isUrl_schemeMustBeLowerCase() {
        assertFalse("HTTP://EXAMPLE.COM".isUrl())
        assertTrue("http://EXAMPLE.COM".isUrl())
    }

    // ── 调色板 ─────────────────────────────────────────────────────────

    @Test
    fun palette_hasSixColors() {
        assertEquals(6, barcodePalette.size)
    }

    @Test
    fun palette_strokeColors() {
        assertEquals(0xFF673AB7.toInt(), barcodePalette[0].stroke.toArgb())
        assertEquals(0xFFFF5722.toInt(), barcodePalette[1].stroke.toArgb())
        assertEquals(0xFF009688.toInt(), barcodePalette[2].stroke.toArgb())
        assertEquals(0xFFE91E63.toInt(), barcodePalette[3].stroke.toArgb())
        assertEquals(0xFF3F51B5.toInt(), barcodePalette[4].stroke.toArgb())
        assertEquals(0xFFFFA000.toInt(), barcodePalette[5].stroke.toArgb())
    }

    @Test
    fun palette_cyclesEverySixEntries() {
        assertEquals(barcodePalette[0], barcodePalette[6 % barcodePalette.size])
        assertEquals(barcodePalette[1], barcodePalette[7 % barcodePalette.size])
        assertEquals(barcodePalette[0], barcodePalette[12 % barcodePalette.size])
    }

    @Test
    fun palette_fillIsTranslucentAndFrameIsOpaque() {
        for (color in barcodePalette) {
            assertTrue("填充色应半透明", color.fill.alpha > 0f && color.fill.alpha < 1f)
            assertEquals(1f, color.stroke.alpha, 0.001f)
            assertEquals(1f, color.cardBackground.alpha, 0.001f)
        }
    }

    // ── 多边形命中：内部与外部 ─────────────────────────────────────────

    @Test
    fun hit_centerOfEachShape() {
        val center = point(5f, 5f)
        assertTrue(isPointInPolygon(center, square))
        assertTrue(isPointInPolygon(center, triangle))
        assertTrue(isPointInPolygon(center, quad))
        assertTrue(isPointInPolygon(center, diamond))
    }

    @Test
    fun hit_pointLeftOfShape() {
        val p = point(-1f, 5f)
        assertFalse(isPointInPolygon(p, square))
        assertFalse(isPointInPolygon(p, triangle))
        assertFalse(isPointInPolygon(p, quad))
    }

    @Test
    fun hit_pointRightOfShape() {
        val p = point(11f, 5f)
        assertFalse(isPointInPolygon(p, square))
        assertFalse(isPointInPolygon(p, triangle))
        assertFalse(isPointInPolygon(p, quad))
    }

    @Test
    fun hit_pointAboveShape() {
        val p = point(5f, -1f)
        assertFalse(isPointInPolygon(p, square))
        assertFalse(isPointInPolygon(p, triangle))
        assertFalse(isPointInPolygon(p, quad))
    }

    @Test
    fun hit_pointBelowShape() {
        val p = point(5f, 11f)
        assertFalse(isPointInPolygon(p, square))
        assertFalse(isPointInPolygon(p, triangle))
        assertFalse(isPointInPolygon(p, quad))
    }

    @Test
    fun hit_insideNotOnAxis() {
        val p = point(5f, 3f)
        assertTrue(isPointInPolygon(p, square))
        assertTrue(isPointInPolygon(p, triangle))
        assertTrue(isPointInPolygon(p, quad))
    }

    @Test
    fun hit_irregularQuadConcaveCornerIsOutside() {
        // (5, 0.5) 落在 quad 上方、边 (2,1)-(8,2) 之外
        assertFalse(isPointInPolygon(point(5f, 0.5f), quad))
    }

    // ── 多边形命中：顶点与边算命中 ─────────────────────────────────────

    @Test
    fun hit_vertexOfSquare() {
        assertTrue(isPointInPolygon(point(0f, 0f), square))
        assertTrue(isPointInPolygon(point(10f, 0f), square))
        assertTrue(isPointInPolygon(point(10f, 10f), square))
        assertTrue(isPointInPolygon(point(0f, 10f), square))
    }

    @Test
    fun hit_vertexOfTriangle() {
        assertTrue(isPointInPolygon(point(0f, 0f), triangle))
        assertTrue(isPointInPolygon(point(5f, 10f), triangle))
    }

    @Test
    fun hit_vertexOfDiamond() {
        assertTrue(isPointInPolygon(point(5f, 0f), diamond))
        assertTrue(isPointInPolygon(point(0f, 5f), diamond))
    }

    @Test
    fun hit_pointOnEdge() {
        assertTrue(isPointInPolygon(point(5f, 0f), square))    // 上边
        assertTrue(isPointInPolygon(point(0f, 5f), square))    // 左边
        assertTrue(isPointInPolygon(point(5f, 0f), triangle))  // 底边
        assertTrue(isPointInPolygon(point(2.5f, 5f), triangle)) // 左边斜边
    }

    @Test
    fun miss_pointOnEdgeExtensionOnly() {
        // 与某条边共线但在线段之外
        assertFalse(isPointInPolygon(point(15f, 0f), square))
        assertFalse(isPointInPolygon(point(-5f, 0f), square))
    }

    // ── 多边形命中：退化输入 ───────────────────────────────────────────

    @Test
    fun miss_emptyPolygon() {
        assertFalse(isPointInPolygon(point(0f, 0f), emptyList()))
    }

    @Test
    fun miss_singlePointPolygon() {
        assertFalse(isPointInPolygon(point(1f, 1f), listOf(point(1f, 1f))))
    }

    @Test
    fun miss_twoPointPolygon() {
        assertFalse(isPointInPolygon(point(1f, 1f), listOf(point(0f, 0f), point(10f, 10f))))
    }

    // ── 辅助 ───────────────────────────────────────────────────────────

    private fun point(x: Float, y: Float) = PointF(x, y)

    private fun polygon(vararg xy: Float): List<PointF> =
        xy.toList().chunked(2).map { (x, y) -> PointF(x, y) }
}
