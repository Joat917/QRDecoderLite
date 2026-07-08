# 解码模块 — scanner

## 所属层

Data

## 职责

接收图片来源，调用 ML Kit 条码识别库执行解码。对原始结果做空值处理和排序后返回，**输出数据中的字段均为非空**。

## 架构

采用**接口 + 实现分离**，便于 ViewModel 单元测试：

```
BarcodeScanner (接口)           ← 定义解码契约
    ↑
MlKitScanner (ML Kit 实现)      ← 生产环境使用
    ↑
Mock/FakeScanner (测试实现)     ← 单元测试注入
```

生产环境通过 ViewModel 构造器默认参数注入，测试时传入 fake 实现。

## 接口定义

```kotlin
interface BarcodeScanner {
    suspend fun scan(uri: Uri): DecodeResult
    suspend fun scan(bitmap: Bitmap): DecodeResult
    fun close()
}
```

ViewModel 构造器接收 `BarcodeScanner` 并设默认值：

```kotlin
class AppViewModel(
    application: Application,
    private val scanner: BarcodeScanner = MlKitScanner(application)
) : AndroidViewModel(application)
```

## 输入

| 参数 | 类型 | 说明 |
|------|------|------|
| uri | android.net.Uri | 系统图片选择器返回的 URI，由 `InputImage.fromFilePath` 内部处理文件读取和 EXIF 校正 |
| bitmap | android.graphics.Bitmap | 已加载的 Bitmap（为未来相机帧预留） |

## 输出

```
sealed interface DecodeResult
  ── Success(val barcodes: List<DecodedBarcode>)
  ── Failure(val reason: String)

DecodedBarcode
  ├─ content: String           — 解码文本（rawValue 或 rawBytes 的 hex 表示）
  ├─ format: String            — 条码格式名称
  └─ cornerPoints: List<PointF> — 四个角点
```

输出保证：`Success` 列表中的每条 `DecodedBarcode`，其 `content` 和 `cornerPoints` 均非空。无法满足的条目已丢弃。

## 处理流程

### 原始 Barcode 到 DecodedBarcode 的映射

对 ML Kit 返回的每个 Barcode 依次做：

1. **确定 content**
   - `rawValue` 非 null → 直接使用
   - `rawValue` 为 null 但 `rawBytes` 非空 → 转为 hex 格式（`"41 42 43 …"` 每字节两位十六进制、空格分隔）
   - 二者皆 null → 丢弃该条目

2. **确定 cornerPoints**
   - `cornerPoints` 非 null → 直接使用
   - `cornerPoints` 为 null 但 `boundingBox` 非 null → 由 boundingBox 四角推算
   - 二者皆 null → 丢弃该条目

### 排序规则

最终列表按以下优先级排列：

```
① rawValue 来源 + cornerPoints 非 null  （可读文本、有坐标）
② rawValue 来源 + cornerPoints 推算     （可读文本、坐标由 boundingBox 推算）
③ rawBytes 来源 + cornerPoints 非 null  （hex 文本、有坐标）
④ rawBytes 来源 + cornerPoints 推算     （hex 文本、坐标由 boundingBox 推算）
```

同一优先级内保持 ML Kit 返回的原始顺序。

## 特性

- 检测全部条码格式，不限制
- 多码检测，单次返回所有识别到的条码
- URI 入口不干预图片加载，完全交由 ML Kit 处理
- 结果已排序：可读内容在前、hex 在后
- suspend 接口，调用方以协程方式调用

## 生命周期

`MlKitScanner` 实例作为 `AppViewModel` 的属性创建，ViewModel 初始化时创建，`onCleared()` 时调用 `close()` 释放。ViewModel 通过 `BarcodeScanner` 接口持有实例，不依赖具体实现。

## 边界

| 场景 | 行为 |
|------|------|
| 成功但无条码 | Success(emptyList()) |
| 扫描过程被取消 | 静默终止，不返回 |
| rawValue 和 rawBytes 皆 null | 丢弃该条目 |
| cornerPoints 和 boundingBox 皆 null | 丢弃该条目 |

## 异常与错误映射

`scan(uri)` 和 `scan(bitmap)` 内部在两个明确的异常边界处 try-catch，将异常映射为 `Failure(reason)`。reason 值的格式约定：

```
[异常类名] 用户友好的错误描述
```

### 异常边界一：InputImage.fromFilePath

```kotlin
InputImage.fromFilePath(context, uri)
```

可能抛出的异常：

| 条件 | reason |
|------|--------|
| `FileNotFoundException` | `[FileNotFoundException] 未找到图片文件，文件可能已被移动或删除` |
| `IOException`（非 FileNotFound） | `[IOException] 无法读取图片，请检查文件权限` |
| `IllegalArgumentException`（如无效 URI scheme） | `[IllegalArgumentException] 不支持的图片格式或无效的文件路径` |
| 其他非预期 `Exception` | `[${e::class.simpleName}] ${e.message}`（忠实返回类名和详细信息） |

### 异常边界二：barcodeScanner.process

```kotlin
barcodeScanner.process(image)
```

可能抛出的异常：

| 条件 | reason |
|------|--------|
| `MlKitException` | `[MlKitException] 二维码识别失败，请重试` |
| `CancellationException` | 静默忽略，不返回 Failure（协程被取消时不消费异常） |
| 其他非预期 `Exception` | `[${e::class.simpleName}] ${e.message}`（忠实返回类名和详细信息） |

### 前置检查（scan(bitmap) 入口）

| 条件 | reason |
|------|--------|
| bitmap 参数为 null | `[IllegalArgumentException] 图片数据为空` |
| bitmap.isRecycled 为 true | `[IllegalStateException] 图片数据已失效，请重新选择` |

### 处理流程说明

```
scan(uri) / scan(bitmap)
  │
  ├─ 前置检查 ────────────────── 失败 → Failure(reason)
  │
  ├─ try ①: InputImage.fromFilePath ── 异常 → Failure(reason)
  │
  ├─ try ②: barcodeScanner.process ─── 异常 → Failure(reason)
  │                                       CancellationException → 静默忽略
  │
  └─ 空值过滤 + 排序 ─────────── 纯逻辑，不抛异常
       │
       ├─ 结果列表为空 → Success(emptyList())
       └─ 结果列表非空 → Success(sortedBarcodes)
```

"未识别到二维码"（Success 空列表）与"识别失败"（Failure）在语义上有明确区分——前者图片能正常读取但没有条码，后者是读取或识别过程中出现了异常。

## 依赖

- `com.google.mlkit:barcode-scanning`（仅 MlKitScanner 依赖）
- `BarcodeScanner` 接口本身无外部依赖

## 消费方

`AppViewModel` 通过 `BarcodeScanner` 接口调用解码，`DecodeResult` 映射为 `DecodeUiState(barcodes, decodeError)`，由 ui-result 模块处理。消费方拿到的 `cornerPoints` 均为非空，可直接用于绘制。

测试时 ViewModel 可注入 fake `BarcodeScanner` 实现，无需 ML Kit 依赖。
