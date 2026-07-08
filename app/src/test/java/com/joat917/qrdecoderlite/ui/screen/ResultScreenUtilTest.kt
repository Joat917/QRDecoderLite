package com.joat917.qrdecoderlite.ui.screen

import android.graphics.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1 测试：结果页工具函数。
 *
 * 测试不依赖 Compose 运行时，仅测试纯函数：
 * - URL 判定规则
 * - 射线法多边形点击检测
 * - 调色板循环分配
 */
class ResultScreenUtilTest {

    // ── isUrl ──────────────────────────────────────────────────────────

    @Test
    fun isUrl_httpScheme_returnsTrue() {
        assertTrue("http://example.com".isUrl())
    }

    @Test
    fun isUrl_httpsScheme_returnsTrue() {
        assertTrue("https://example.com".isUrl())
    }

    @Test
    fun isUrl_httpsWithComplexPath_returnsTrue() {
        assertTrue("https://example.com/path/to/page?query=1&param=2#fragment".isUrl())
    }

    @Test
    fun isUrl_httpsWithPort_returnsTrue() {
        assertTrue("https://example.com:443/api".isUrl())
    }

    @Test
    fun isUrl_wwwWithoutScheme_returnsFalse() {
        assertFalse("www.example.com".isUrl())
    }

    @Test
    fun isUrl_justNumbers_returnsFalse() {
        assertFalse("1234567890".isUrl())
    }

    @Test
    fun isUrl_plainText_returnsFalse() {
        assertFalse("Hello World".isUrl())
    }

    @Test
    fun isUrl_emptyString_returnsFalse() {
        assertFalse("".isUrl())
    }

    @Test
    fun isUrl_singleSlash_returnsFalse() {
        assertFalse("http://".isUrl())
    }

    @Test
    fun isUrl_onlyHttpWithoutColon_returnsFalse() {
        assertFalse("http".isUrl())
    }

    @Test
    fun isUrl_ftpScheme_returnsFalse() {
        assertFalse("ftp://example.com".isUrl())
    }

    @Test
    fun isUrl_caseSensitiveHttp() {
        assertFalse("HTTP://EXAMPLE.COM".isUrl())   // 仅检查小写前缀
        assertTrue("http://EXAMPLE.COM".isUrl())
    }

    // ── isPointInPolygon (射线法) ──────────────────────────────────────

    // 标准正方形
    private val square = listOf(
        PointF(0f, 0f),
        PointF(10f, 0f),
        PointF(10f, 10f),
        PointF(0f, 10f)
    )

    // 三角形
    private val triangle = listOf(
        PointF(0f, 0f),
        PointF(10f, 0f),
        PointF(5f, 10f)
    )

    // 不规则四边形
    private val quad = listOf(
        PointF(2f, 1f),
        PointF(8f, 2f),
        PointF(9f, 9f),
        PointF(1f, 8f)
    )

    @Test
    fun isPointInPolygon_pointInCenter_returnsTrue() {
        assertTrue(isPointInPolygon(PointF(5f, 5f), square))
    }

    @Test
    fun isPointInPolygon_pointOutsideLeft_returnsFalse() {
        assertFalse(isPointInPolygon(PointF(-1f, 5f), square))
    }

    @Test
    fun isPointInPolygon_pointOutsideRight_returnsFalse() {
        assertFalse(isPointInPolygon(PointF(11f, 5f), square))
    }

    @Test
    fun isPointInPolygon_pointOutsideTop_returnsFalse() {
        assertFalse(isPointInPolygon(PointF(5f, -1f), square))
    }

    @Test
    fun isPointInPolygon_pointOutsideBottom_returnsFalse() {
        assertFalse(isPointInPolygon(PointF(5f, 11f), square))
    }

    @Test
    fun isPointInPolygon_pointOnVertex_returnsTrue() {
        assertTrue(isPointInPolygon(PointF(0f, 0f), square))
    }

    @Test
    fun isPointInPolygon_pointOnEdge_returnsTrue() {
        assertTrue(isPointInPolygon(PointF(5f, 0f), square))
    }

    @Test
    fun isPointInPolygon_trianglePointInside_returnsTrue() {
        assertTrue(isPointInPolygon(PointF(5f, 3f), triangle))
    }

    @Test
    fun isPointInPolygon_trianglePointOutside_returnsFalse() {
        assertFalse(isPointInPolygon(PointF(0f, 0f), triangle))
    }

    @Test
    fun isPointInPolygon_triangleBelowBase_returnsFalse() {
        assertFalse(isPointInPolygon(PointF(5f, 11f), triangle))
    }

    @Test
    fun isPointInPolygon_irregularQuad_pointInside() {
        assertTrue(isPointInPolygon(PointF(5f, 5f), quad))
    }

    @Test
    fun isPointInPolygon_irregularQuad_pointOutside() {
        assertFalse(isPointInPolygon(PointF(0f, 0f), quad))
    }

    @Test
    fun isPointInPolygon_emptyPolygon_returnsFalse() {
        assertFalse(isPointInPolygon(PointF(0f, 0f), emptyList()))
    }

    @Test
    fun isPointInPolygon_singlePointPolygon_returnsFalse() {
        assertFalse(isPointInPolygon(PointF(5f, 5f), listOf(PointF(5f, 5f))))
    }

    @Test
    fun isPointInPolygon_twoPointPolygon_returnsFalse() {
        assertFalse(isPointInPolygon(PointF(5f, 5f), listOf(PointF(0f, 0f), PointF(10f, 10f))))
    }

    @Test
    fun isPointInPolygon_diagonalCorner_returnsTrue() {
        // 点在多边形对角线位置
        val diamond = listOf(
            PointF(5f, 0f),
            PointF(10f, 5f),
            PointF(5f, 10f),
            PointF(0f, 5f)
        )
        assertTrue(isPointInPolygon(PointF(5f, 5f), diamond))
        assertFalse(isPointInPolygon(PointF(0f, 0f), diamond))
    }

    // ── 调色板循环 ─────────────────────────────────────────────────────

    @Test
    fun palette_hasSixColors() {
        assertEquals(6, barcodePalette.size)
    }

    @Test
    fun palette_index0IsPurple() {
        assertEquals(0xFF673AB7.toInt(), barcodePalette[0].stroke.value.toInt())
    }

    @Test
    fun palette_index1IsOrange() {
        assertEquals(0xFFFF5722.toInt(), barcodePalette[1].stroke.value.toInt())
    }

    @Test
    fun palette_index5IsAmber() {
        assertEquals(0xFFFFA000.toInt(), barcodePalette[5].stroke.value.toInt())
    }

    @Test
    fun palette_cycle_index6EqualsIndex0() {
        assertEquals(barcodePalette[0], barcodePalette[6 % barcodePalette.size])
    }

    @Test
    fun palette_cycle_index7EqualsIndex1() {
        assertEquals(barcodePalette[1], barcodePalette[7 % barcodePalette.size])
    }

    @Test
    fun palette_cycle_index12EqualsIndex0() {
        assertEquals(barcodePalette[0], barcodePalette[12 % barcodePalette.size])
    }

    @Test
    fun palette_eachColorHasAllLayers() {
        for (color in barcodePalette) {
            assertTrue("fill should have alpha", color.fill.alpha > 0f)
            assertTrue("stroke should be opaque", color.stroke.alpha == 1f)
            assertTrue("cardBackground should have alpha", color.cardBackground.alpha > 0f)
            assertTrue("cardBar should be opaque", color.cardBar.alpha == 1f)
        }
    }
}
