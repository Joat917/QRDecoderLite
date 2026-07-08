# 结果展示模块 — ui-result

## 职责

接收解码结果，以"图片标注 + 卡片列表"的形式展示图片中所有二维码的位置和内容，并提供每个码的复制/打开操作。

## 触发方式

sealed interface `Sheet.Result` 被设为当前页面时，以全屏页面的形式展示。

## 关闭方式

仅通过：
- 系统返回手势（左滑/返回键）
- 左上角浮动返回箭头（Material Icon: `arrow_back`）

无其他关闭按钮。关闭即销毁 Sheet，返回主界面。

## 布局

整个页面内容可垂直滚动，从顶部到底部顺序排列。

### 解码中

```
┌──────────────────────────────┐
│  ← (arrow_back)              │  左上角浮动返回箭头
│                              │
│  ┌────────────────────────┐  │
│  │        [原图]            │  │  图片已显示，无多边形标注
│  └────────────────────────┘  │
│                              │
│         正在识别…              │  灰色小字，居中
│                              │
└──────────────────────────────┘
```

### 解码完成（有条码）

```
┌──────────────────────────────┐
│  ← (arrow_back)              │
│                              │
│  ┌────────────────────────┐  │
│  │        [原图]            │  │  多边形以半透明填色、不透明边框
│  │    ┌──────┐             │  │  绘制在图片上方覆盖层
│  │    │ QR1  │             │  │
│  │    └──────┘             │  │
│  │           ┌──────┐      │  │
│  │           │ QR2  │      │  │
│  │           └──────┘      │  │
│  └────────────────────────┘  │
│                              │
│  ┌────────────────────────┐  │
│  │ ██ 条码 #1              │  │  卡片：顶部色条 + 内容
│  │ https://example.com    │  │  背景色为同色系极淡色
│  └────────────────────────┘  │
│  ┌────────────────────────┐  │
│  │ ██ 条码 #2              │  │
│  │ 1234567890             │  │
│  └────────────────────────┘  │
│           ...                │
│                              │
└──────────────────────────────┘
```

### 解码完成（无条码）

```
┌──────────────────────────────┐
│  ← (arrow_back)              │
│                              │
│  ┌────────────────────────┐  │
│  │        [原图]            │  │  无多边形标注
│  └────────────────────────┘  │
│                              │
│      未识别到二维码            │  灰色小字，居中
│                              │
└──────────────────────────────┘
```

### 解码出错

```
┌──────────────────────────────┐
│  ← (arrow_back)              │
│                              │
│  ┌────────────────────────┐  │
│  │        [原图]            │  │  无多边形标注
│  └────────────────────────┘  │
│                              │
│  ┌────────────────────────┐  │
│  │ ✕ 图片解析失败           │  │  灰色卡片，左侧错误图标
│  │   图片格式不支持或已损坏   │  │  第二行为详细信息（更小字号）
│  └────────────────────────┘  │
│                              │
└──────────────────────────────┘
```

### 图片标注层

- 原图显示：宽度填满容器，高度按图片宽高比自适应
- 每个二维码用四个角的坐标点（cornerPoints）绘制多边形覆盖层
- 多边形填充色为半透明，边框色为同色不透明，边框宽度 2-3dp
- 每个码从调色板中按序取色（第一个码取 color0，第二个取 color1……）
- 点击多边形区域 → 触发该码的操作栏弹出（见"交互"节）

### 卡片列表

- 每张卡片对应一个二维码
- 卡片顶部有 4dp 高的色条，颜色与该码的多边形一致
- 卡片背景为同色系的极淡色（约 10% 透明度）
- 卡片内容：条码内容文本，过长自动换行
- 点击卡片 → 触发该码的操作栏弹出
- 操作栏显示两个 Material Icon 按钮，浮在卡片右下角或内容区下方
- 点击其他任意区域 → 操作栏消失

### 调色板

用于区分不同二维码的一组高对比颜色。按序分配：

| 索引 | 颜色 | HEX | 用途 |
|------|------|-----|------|
| 0 | 紫色 | `#673AB7` | 第一个二维码 |
| 1 | 橙色 | `#FF5722` | 第二个二维码 |
| 2 | 青色 | `#009688` | 第三个二维码 |
| 3 | 粉色 | `#E91E63` | 第四个二维码 |
| 4 | 靛蓝 | `#3F51B5` | 第五个二维码 |
| 5 | 琥珀 | `#FFA000` | 第六个二维码 |

每个颜色的三个用途层（以紫色为例）：

| 层 | 颜色值 | 说明 |
|-----|---------|------|
| 多边形填充 | `#40673AB7` | 主色 + 25% 透明度（~40 hex） |
| 多边形边框 | `#FF673AB7` | 主色，不透明，2-3dp |
| 卡片背景 | `#1A673AB7` | 主色 + 10% 透明度（~1A hex） |
| 卡片色条 | `#FF673AB7` | 主色，不透明，4dp 高 |

超过 6 个条码则从索引 0 循环使用。

### URL 判定规则

`open_in_new` 按钮是否显示的判定条件：

| 内容前缀 | 判定结果 | 行为 |
|---------|---------|------|
| `http://` 或 `https://` 开头 | 是 URL | 显示 `open_in_new`，点击用系统浏览器打开 |
| 其他（纯文本、数字、邮箱等） | 非 URL | 隐藏 `open_in_new` |

仅检查前缀，**不**做额外格式校验。`www.example.com`（无协议头）不算 URL。

### 图片加载方案

**选型：Coil** (coil-compose)

理由：Kotlin 协程原生、Compose 一等支持、自动采样加载防 OOM、内建缓存。

用法：

```kotlin
// 显示用户选中的原图
AsyncImage(
    model = imageUri,               // 系统图片选择器返回的 Uri
    contentDescription = "选中图片",
    modifier = Modifier.fillMaxWidth(),
    contentScale = ContentScale.Fit
)
```

坐标映射：通过 `rememberAsyncImagePainter` 获取 `Painter`，其 `intrinsicSize` 为图片原始尺寸，与 Image 实际显示尺寸（`onSizeChanged` 获取）做比例换算，计算出角点在屏幕上的坐标。

```kotlin
val painter = rememberAsyncImagePainter(model = imageUri)
val imageSize = painter.intrinsicSize     // 原始尺寸
var displaySize by remember { mutableIntSizeStateOf(IntSize.Zero) }

Box {
    Image(
        painter = painter,
        contentDescription = "选中图片",
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { displaySize = it },
        contentScale = ContentScale.Fit
    )
    Canvas(modifier = Modifier.matchParentSize()) {
        val scaleX = displaySize.width / imageSize.width
        val scaleY = displaySize.height / imageSize.height
        // 用 scaleX/scaleY 将 cornerPoints 映射到屏幕坐标后绘制多边形
    }
}
```

### 多边形点击检测

**方案：射线法（Ray Casting）**

原理：从点击点向右发出一条射线，统计与多边形所有边的交叉次数——**奇数为内部，偶数为外部**。

```kotlin
/**
 * 射线法判断点击点是否在多边形内部。
 * 从点 (x,y) 向右水平发射射线，统计与各边的交点数：
 * 交点数为奇数 → 点在多边形内部
 * 交点数为偶数 → 点在多边形外部
 */
fun isPointInPolygon(point: PointF, polygon: List<PointF>): Boolean {
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        // 检查边 (polygon[i] → polygon[j]) 是否与水平射线相交
        if ((polygon[i].y > point.y) != (polygon[j].y > point.y) &&
            point.x < (polygon[j].x - polygon[i].x) * (point.y - polygon[i].y) /
                      (polygon[j].y - polygon[i].y) + polygon[i].x
        ) {
            inside = !inside   // 交叉一次，翻转结果
        }
        j = i
    }
    return inside
}
```

在 Compose 中的集成：

```kotlin
Canvas(modifier = Modifier
    .matchParentSize()
    .pointerInput(barcodes) {
        detectTapGestures { offset ->
            // 将屏幕坐标还原为图片坐标
            val tapX = offset.x / scaleX
            val tapY = offset.y / scaleY
            // 遍历所有条码，找到第一个命中的
            val hitIndex = barcodes.indexOfFirst {
                isPointInPolygon(PointF(tapX, tapY), it.cornerPoints)
            }
            if (hitIndex >= 0) onBarcodeClick(hitIndex)
        }
    }
)
```

射线法相比 Android `Path.contains()`：避免系统 API 对四边形的误判，代码仅 10 行，可靠性验证充分。

## 交互

### 操作栏点击

- 点击卡片或图片上的多边形区域 → 弹出该码对应的操作栏
- 操作栏包含两个 Material Icon 按钮：

| 图标 (Material Icon name) | 功能 |
|--------------------------|------|
| `content_copy` | 将该条码内容复制到系统剪贴板 |
| `open_in_new` | 若内容为 URL，用系统浏览器打开；若非 URL，隐藏此按钮 |

- 操作栏浮在卡片内容的右下角（或紧挨卡片底部），背景为轻微浮起阴影
- 点击操作栏以外的任意区域 → 操作栏立即消失（包括点击其他卡片、图片、空白区域）

### 关闭

- 左上角浮动箭头（`arrow_back`）：始终可见，点击即关闭页面
- 系统返回键/手势：同等效果

## 输入数据模型

```kotlin
data class DecodedBarcode(
    val content: String,                    // 解码文本（URL、文本、数字等）
    val format: String,                     // 条码格式，如 "QR_CODE"、"CODE_128"、"AZTEC"…
    val cornerPoints: List<PointF>          // 四个角点 [左上, 右上, 右下, 左下]
)

列表已按优先级排序：可读文本在前、hex 在后。

/**
 * 结果页的完整状态。
 */
data class DecodeUiState(
    val imageUri: Uri,
    val barcodes: List<DecodedBarcode>? = null,
    val decodeError: String? = null
)
```

状态机：

| barcodes | decodeError | UI 表现 |
|----------|-------------|---------|
| null | null | 解码中 → 显示原图 + "正在识别…" |
| [] | null | 无条码 → 显示原图 + "未识别到二维码" |
| [...] | null | 成功 → 多边形标注 + 卡片列表（按 scanner 返回顺序渲染） |
| 任意 | "..." | 出错 → 错误卡片 |

页面通过 `Sheet.Result(uri: Uri)` 触发，ViewModel 内部持有一个 `DecodeUiState`，解码完成后更新 `barcodes` 字段触发 UI 重组。

## 边界情况

| 场景 | 行为 |
|------|------|
| 选中图片后解码中 | 显示原图 + 灰色小字"正在识别…"，无多边形和卡片 |
| 解码成功（有条码） | 显示多边形标注 + 卡片列表 |
| 解码成功（无条码） | 显示原图 + 灰色小字"未识别到二维码"，无卡片 |
| 解码出错 | 显示原图 + 灰色卡片，卡片包含错误图标和详细信息（如"图片解析失败，图片格式不支持或已损坏"） |
| 单条码 | 一个多边形 + 一张卡片 |
| 多条码（> 调色板颜色数） | 颜色循环使用 |
| 内容过长 | 卡片文本自动换行，不截断 |
| 内容为 URL | `open_in_new` 按钮可点击；非 URL 则隐藏 |
| 大图 | 图片宽度自适应容器，高度等比缩放，不裁剪 |
| 小图 | 居中显示，不拉伸 |

## 依赖关系

- **依赖**: 解码模块输出 `DecodeResult`
- **依赖**: 数据模型 `DecodedBarcode`
- **不依赖**: 相机模块、历史记录模块
- **输出**: 用户操作（复制、打开、关闭），通过回调上传给 ViewModel