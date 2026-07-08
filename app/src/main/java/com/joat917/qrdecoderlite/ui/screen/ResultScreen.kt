package com.joat917.qrdecoderlite.ui.screen

import android.graphics.PointF
import android.webkit.URLUtil
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import com.joat917.qrdecoderlite.model.DecodeUiState
import com.joat917.qrdecoderlite.model.DecodedBarcode

// ── 调色板 ────────────────────────────────────────────────────────────

internal data class BarcodeColorSet(
    val fill: Color,
    val stroke: Color,
    val cardBackground: Color
)

internal val barcodePalette = listOf(
    BarcodeColorSet(
        fill = Color(0x40673AB7),
        stroke = Color(0xFF673AB7),
        cardBackground = Color(0x40673AB7)
    ),
    BarcodeColorSet(
        fill = Color(0x40FF5722),
        stroke = Color(0xFFFF5722),
        cardBackground = Color(0x40FF5722)
    ),
    BarcodeColorSet(
        fill = Color(0x40009688),
        stroke = Color(0xFF009688),
        cardBackground = Color(0x40009688)
    ),
    BarcodeColorSet(
        fill = Color(0x40E91E63),
        stroke = Color(0xFFE91E63),
        cardBackground = Color(0x40E91E63)
    ),
    BarcodeColorSet(
        fill = Color(0x403F51B5),
        stroke = Color(0xFF3F51B5),
        cardBackground = Color(0x403F51B5)
    ),
    BarcodeColorSet(
        fill = Color(0x40FFA000),
        stroke = Color(0xFFFFA000),
        cardBackground = Color(0x40FFA000)
    )
)

// ── 工具函数 ──────────────────────────────────────────────────────────

internal fun isPointInPolygon(point: PointF, polygon: List<PointF>): Boolean {
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        if ((polygon[i].y > point.y) != (polygon[j].y > point.y) &&
            point.x < (polygon[j].x - polygon[i].x) * (point.y - polygon[i].y) /
                      (polygon[j].y - polygon[i].y) + polygon[i].x
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

internal fun String.isUrl(): Boolean =
    URLUtil.isNetworkUrl(this)

// ── 结果页主组件 ──────────────────────────────────────────────────────

@Composable
fun ResultScreen(
    decodeUiState: DecodeUiState,
    onBackToMain: () -> Unit,
    onCopy: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuIndex by remember { mutableIntStateOf(-1) }
    val dismissMenu = { menuIndex = -1 }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                dismissMenu()
            }
    ) {
        // 返回箭头
        IconButton(onClick = onBackToMain) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回"
            )
        }

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            // 图片 + 多边形覆盖层
            ImageWithOverlay(
                imageUri = decodeUiState.imageUri,
                barcodes = decodeUiState.barcodes,
                onBarcodeClick = { index ->
                    menuIndex = index
                }
            )

            // 状态区域
            when {
                decodeUiState.decodeError != null -> ErrorDisplay(decodeUiState.decodeError)
                decodeUiState.barcodes == null -> DecodingIndicator()
                decodeUiState.barcodes.isEmpty() -> NoResultIndicator()
                else -> BarcodeCardList(
                    barcodes = decodeUiState.barcodes,
                    menuIndex = menuIndex,
                    onBarcodeClick = { index ->
                        menuIndex = index
                    },
                    onDismissMenu = dismissMenu,
                    onCopy = onCopy,
                    onOpenLink = onOpenLink
                )
            }
        }
    }
}

// ── 图片与多边形覆盖层 ────────────────────────────────────────────────

@Composable
private fun ImageWithOverlay(
    imageUri: android.net.Uri,
    barcodes: List<DecodedBarcode>?,
    onBarcodeClick: (Int) -> Unit
) {
    val painter = rememberAsyncImagePainter(model = imageUri)
    val imageSize = painter.intrinsicSize
    var displaySize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Box {
        Image(
            painter = painter,
            contentDescription = "选中图片",
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { displaySize = it },
            contentScale = ContentScale.FillWidth
        )

        // 多边形覆盖层（仅在有条码结果时绘制）
        if (!barcodes.isNullOrEmpty()
            && imageSize.width > 0f && imageSize.height > 0f
            && displaySize.width > 0 && displaySize.height > 0
        ) {
            val scaleX = displaySize.width / imageSize.width
            val scaleY = displaySize.height / imageSize.height

            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(barcodes) {
                        detectTapGestures { offset ->
                            // 将屏幕坐标还原为图片坐标
                            val tapX = offset.x / scaleX
                            val tapY = offset.y / scaleY
                            val hitIndex = barcodes.indexOfFirst {
                                isPointInPolygon(PointF(tapX, tapY), it.cornerPoints)
                            }
                            if (hitIndex >= 0) onBarcodeClick(hitIndex)
                        }
                    }
            ) {
                val strokePx = with(density) { 3.dp.toPx() }
                barcodes.forEachIndexed { index, barcode ->
                    val color = barcodePalette[index % barcodePalette.size]
                    val path = Path().apply {
                        val first = barcode.cornerPoints.firstOrNull() ?: return@forEachIndexed
                        moveTo(first.x * scaleX, first.y * scaleY)
                        for (i in 1 until barcode.cornerPoints.size) {
                            val pt = barcode.cornerPoints[i]
                            lineTo(pt.x * scaleX, pt.y * scaleY)
                        }
                        close()
                    }
                    drawPath(path, color = color.fill)
                    drawPath(path, color = color.stroke, style = Stroke(width = strokePx))
                }
            }
        }
    }
}

// ── 状态指示器 ────────────────────────────────────────────────────────

@Composable
private fun DecodingIndicator() {
    Text(
        text = "正在识别…",
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        fontSize = 14.sp
    )
}

@Composable
private fun NoResultIndicator() {
    Text(
        text = "未识别到二维码",
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        fontSize = 14.sp
    )
}

@Composable
private fun ErrorDisplay(error: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "✕",
                color = MaterialTheme.colorScheme.error,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "图片解析失败",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ── 条码卡片列表 ──────────────────────────────────────────────────────

@Composable
private fun BarcodeCardList(
    barcodes: List<DecodedBarcode>,
    menuIndex: Int,
    onBarcodeClick: (Int) -> Unit,
    onDismissMenu: () -> Unit,
    onCopy: (String) -> Unit,
    onOpenLink: (String) -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        barcodes.forEachIndexed { index, barcode ->
            BarcodeCard(
                barcode = barcode,
                color = barcodePalette[index % barcodePalette.size],
                showMenu = menuIndex == index,
                onClick = { onBarcodeClick(index) },
                onDismissMenu = onDismissMenu,
                onCopy = { onCopy(barcode.content) },
                onOpenLink = { onOpenLink(barcode.content) }
            )
        }
    }
}

@Composable
private fun BarcodeCard(
    barcode: DecodedBarcode,
    color: BarcodeColorSet,
    showMenu: Boolean,
    onClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onCopy: () -> Unit,
    onOpenLink: () -> Unit
) {
    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clickable { onClick() },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(2.dp, color.stroke),
            colors = CardDefaults.cardColors(
                containerColor = color.cardBackground
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = barcode.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = barcode.format,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 弹出式操作菜单（仅在被选中时显示）
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = onDismissMenu
        ) {
            DropdownMenuItem(
                text = { Text("复制") },
                onClick = {
                    onCopy()
                    onDismissMenu()
                },
                leadingIcon = {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                }
            )
            if (barcode.content.isUrl()) {
                DropdownMenuItem(
                    text = { Text("打开") },
                    onClick = {
                        onOpenLink()
                        onDismissMenu()
                    },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    }
                )
            }
        }
    }
}
