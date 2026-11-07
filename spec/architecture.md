# 架构

本文说明分层、数据模型、数据流与对外接口，各模块的细节见对应文档。

## 分层与边界

应用分三层：界面层、流程层、解码层。依赖方向只有一条，界面层调用流程层，流程层调用解码层。

| 层 | 主要内容 | 依赖 |
|---|---|---|
| 界面 | `MainActivity`、`ui/screen`、`ui/theme` | 流程层 |
| 流程 | `viewmodel/AppViewModel` | 解码层接口 `BarcodeScanner` |
| 解码 | `scanner`，含两个引擎与结果合并 | ML Kit、WeChatQRCode、OpenCV |

界面层不直接调用解码层。流程层只依赖 `BarcodeScanner` 接口，测试时注入替代实现。

## 页面与状态

应用只有一个 Activity，两个页面靠状态切换，没有导航栈。

`AppUiState` 只保存当前页面：

```kotlin
data class AppUiState(val activeSheet: Sheet? = null)

sealed interface Sheet {
    data class Result(val imageUri: Uri) : Sheet
}
```

`activeSheet` 为 null 显示主界面，`Sheet.Result` 显示结果页。结果页关闭即回到主界面。

## 数据模型

以下定义只在这里维护，其它文档引用而不复制。

```kotlin
data class DecodeUiState(
    val barcodes: List<DecodedBarcode>? = null,
    val decodeError: String? = null
)

data class DecodedBarcode(
    val content: String,
    val format: String,
    val cornerPoints: List<PointF>
)

sealed interface DecodeResult {
    data class Success(val barcodes: List<DecodedBarcode>) : DecodeResult
    data class Failure(val reason: String) : DecodeResult
}
```

`barcodes` 有三种取值：为 null 表示解码中，空列表表示没有识别到码，非空表示成功。`decodeError` 非 null 时表示解码失败，此时 `barcodes` 无意义。

`DecodedBarcode` 的 `content` 一定非空。`cornerPoints` 是这条结果在图片上的四个角坐标；它可能为空，表示拿不到位置，界面就不画这条结果的多边形。

图片归属由 `Sheet.Result.imageUri` 记录，`DecodeUiState` 不重复保存。

## 数据流

选图后进入结果页并启动解码：

1. 主界面得到图片 URI，调用 `AppViewModel.onImageSelected`。
2. `AppViewModel` 把 `activeSheet` 设为 `Sheet.Result`，同时把 `DecodeUiState` 置为解码中，界面切到结果页。
3. `AppViewModel` 在协程里调用 `BarcodeScanner.scan`。`CombinedScanner` 并行调用两个引擎，各自返回 `DecodeResult`。
4. 合并结果写回 `DecodeUiState`。成功写 `barcodes`，失败写 `decodeError`。
5. 用户点返回箭头或按返回键时调用 `AppViewModel.onBackToMain`，取消未完成的解码任务并清空状态。

相机入口和分享入口的差别只在取 URI 的方式，之后走的流程相同。

## ViewModel 接口

```kotlin
class AppViewModel(
    application: Application,
    private val scanner: BarcodeScanner = CombinedScanner(application)
) : AndroidViewModel(application) {

    val uiState: StateFlow<AppUiState>
    val decodeUiState: StateFlow<DecodeUiState>

    fun onImageSelected(uri: Uri?)
    fun onBackToMain()
    fun onCopy(content: String, context: Context)
    fun onOpenLink(url: String, context: Context)
}
```

`onImageSelected` 收到 null 表示用户取消选图，此时不改状态。解码协程捕获未预期异常并写入 `decodeError`，异常本身记录到日志。
