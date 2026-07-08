# 架构总览

## 系统架构图

```
┌───────────────────────────────────────────────────────────────┐
│  UI Layer                                                     │
│  ┌──────────┐    ┌──────────────┐                             │
│  │ 主界面    │    │ 结果页       │    AppViewModel              │
│  │ Logo+按钮 │◄──►│ 图片+卡片    │◄──► AppUiState              │
│  │           │    │              │    ┌─ activeSheet: Sheet?  │
│  └──────────┘    └──────────────┘    └─ DecodeUiState         │
│       │                │                │                      │
│       │ 选择图片       │ 复制/打开/返回  │ BarcodeScanner 接口  │
│       ▼                ▼                ▼                      │
│  系统 PhotoPicker    Clipboard / Browser  MlKitScanner (ML Kit)│
├───────────────────────────────────────────────────────────────┤
│  Domain / Data Layer (简化)                                    │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ 扫码流程                                                 │  │
│  │  URI → (解码Bitmap) → ML Kit BarcodeScanner → 结果列表  │  │
│  └─────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────┘
```

## 页面与导航

应用由两个页面组成，通过 `Sheet` 模式切换：

```
主界面 (MainScreen)
  └─ activeSheet == null
     显示: Logo + "从相册" / "从相机" / "从历史" 按钮
     交互: 点击"从相册" → 启动系统图片选择器

        用户选择图片后:
        AppViewModel.onImageSelected(uri)
          → activeSheet = Sheet.Result(uri)
          → 立即切换到结果页

结果页 (ResultScreen)
  └─ activeSheet == Sheet.Result(uri)
     显示: 原图 + 多边形标注 + 卡片列表 (或解码中/无结果/错误提示)
     交互: 复制 / 打开URL / 点击多边形跳转卡片 / 返回

        用户点返回:
        AppViewModel.onBackToMain()
          → activeSheet = null
          → 回到主界面
```

## 数据流

```
用户点击"从相册"
  → 系统 PhotoPicker (PickVisualMedia)
  → URI 回调 → AppViewModel.onImageSelected(uri)
      ├─ activeSheet = Sheet.Result(uri)  → 页面切换到结果页
      │                                      (此时 DecodeUiState =
      │                                       imageUri, barcodes=null)
      └─ 启动扫描协程 (viewModelScope.launch)
            ├─ scanner.scan(uri)
            │     → InputImage.fromFilePath
            │     → ML Kit → 排序/补全 → List<DecodedBarcode>
            └─ 更新 DecodeUiState:
                  ├─ 成功有条码 → barcodes = [...]
                  ├─ 无条码     → barcodes = emptyList()
                  └─ 出错       → decodeError = "错误信息"
```

## 数据模型

### AppUiState — 应用级状态

```kotlin
data class AppUiState(
    val activeSheet: Sheet? = null
)

sealed interface Sheet {
    data class Result(val imageUri: Uri) : Sheet
}
```

`activeSheet` 控制页面切换：`null` 为主界面，`Sheet.Result` 为结果页。

### DecodeUiState — 结果页状态

```kotlin
data class DecodeUiState(
    val imageUri: Uri,
    val barcodes: List<DecodedBarcode>? = null,
    val decodeError: String? = null
)
```

状态机的三个取值：

| barcodes | decodeError | UI 表现 |
|----------|-------------|---------|
| `null` | `null` | 解码中 → 显示原图 + "正在识别…" |
| `[]` | `null` | 无条码 → 显示原图 + "未识别到二维码" |
| `[...]` | `null` | 成功 → 多边形标注 + 卡片列表 |
| 任意 | `"..."` | 出错 → 错误卡片 |

### DecodedBarcode — 单条码结果

```kotlin
data class DecodedBarcode(
    val content: String,               // 解码文本
    val format: String,                // 格式: "QR_CODE", "CODE_128"…
    val cornerPoints: List<PointF>     // 四个角点
)
```

### DecodeResult — 解码模块输出

```kotlin
sealed interface DecodeResult {
    data class Success(val barcodes: List<DecodedBarcode>) : DecodeResult
    data class Failure(val reason: String) : DecodeResult
}
```

## ViewModel 接口

```kotlin
class AppViewModel(
    application: Application,
    private val scanner: BarcodeScanner = MlKitScanner(application)
) : AndroidViewModel(application) {

    val uiState: StateFlow<AppUiState>
    val decodeUiState: StateFlow<DecodeUiState>

    fun onImageSelected(uri: Uri?)       // 图片选择器返回
    fun onBackToMain()                   // 返回主界面
    fun onCopy(content: String, ...)     // 复制到剪贴板
    fun onOpenLink(url: String, ...)     // 系统浏览器打开 URL
}
```

> 注：`scanner` 参数提供默认值 `MlKitScanner(application)`，生产环境无需显式传入；测试时传入 mock/fake 实现。`onCopy`/`onOpenLink` 额外接收 `context: Context` 参数。

## 结果页 UI 构成

### 布局

```
┌──────────────────────────────┐
│  ←  (返回箭头)                │
├──────────────────────────────┤
│  ┌────────────────────────┐  │
│  │        [原图]            │  │  Coil AsyncImage
│  │    ┌──────┐             │  │  多边形绘制在 Canvas 覆盖层
│  │    │ QR1  │             │  │  射线法检测多边形点击
│  │    └──────┘             │  │
│  │           ┌──────┐      │  │
│  │           │ QR2  │      │  │
│  │           └──────┘      │  │
│  └────────────────────────┘  │
│                              │
│  ┌────────────────────────┐  │
│  │ ██ 条码 #1              │  │  色条 + 内容 + 操作按钮
│  │ https://example.com    │  │  复制 / open_in_new (仅URL)
│  └────────────────────────┘  │
│  ┌────────────────────────┐  │
│  │ ██ 条码 #2              │  │
│  │ 1234567890             │  │  open_in_new 隐藏
│  └────────────────────────┘  │
└──────────────────────────────┘
```

### 多边形调色板

| 索引 | 颜色 | 填充色 | 边框色 | 卡片背景 | 卡片色条 |
|------|------|--------|--------|----------|----------|
| 0 | 紫 `#673AB7` | `#40673AB7` | `#FF673AB7` | `#1A673AB7` | `#FF673AB7` |
| 1 | 橙 `#FF5722` | `#40FF5722` | `#FFFF5722` | `#1AFF5722` | `#FFFF5722` |
| 2 | 青 `#009688` | `#40009688` | `#FF009688` | `#1A009688` | `#FF009688` |
| 3 | 粉 `#E91E63` | `#40E91E63` | `#FFE91E63` | `#1AE91E63` | `#FFE91E63` |
| 4 | 靛 `#3F51B5` | `#403F51B5` | `#FF3F51B5` | `#1A3F51B5` | `#FF3F51B5` |
| 5 | 琥 `#FFA000` | `#40FFA000` | `#FFFFA000` | `#1AFFA000` | `#FFFFA000` |

超过 6 个条码则从索引 0 循环。

### URL 判定规则

内容以 `http://` 或 `https://` 开头 → 显示 `open_in_new` 按钮，点击用系统浏览器打开。
否则隐藏 `open_in_new`。

## 图片加载

使用 **Coil** (`coil-compose`)，`AsyncImage` 直接加载 URI：

- 缩放模式: `ContentScale.Fit`（宽度填满，高度等比）
- 坐标映射: 通过 `Painter.intrinsicSize` 取得原图尺寸，与控件 `onSizeChanged` 的显示尺寸做比例换算
- 多边形层绘制在 `Canvas` 覆盖层中

## 依赖关系

| 组件 | 依赖 |
|------|------|
| ViewModel | `BarcodeScanner` 接口（通过构造器注入） |
| 结果页 | `DecodeResult`、`DecodedBarcode` 模型 |
| 结果页 | Coil (`io.coil-kt:coil-compose`) |
| 结果页 | 不依赖相机模块、历史记录模块 |
| 主界面 | 不依赖解码模块（仅触发导航） |

## 边界情况

| 场景 | 行为 |
|------|------|
| 用户取消图片选择 | `onImageSelected(null)` → 不切换 Sheet，留在主界面 |
| 解码中 | 显示原图 + "正在识别…" |
| 解码成功无条码 | 显示原图 + "未识别到二维码" |
| 解码出错（格式不支持/图片损坏） | 显示原图 + 错误卡片，含图标和详细信息 |
| 内容为 URL | `open_in_new` 按钮可见 |
| 内容非 URL | `open_in_new` 隐藏 |
| 多条码 > 6 个 | 调色板颜色循环使用 |
| 点击多边形或卡片 | 弹出该码的操作栏，点击其他区域关闭 |
