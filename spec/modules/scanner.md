# 解码模块 — scanner

## 所属层

Data

## 职责

接收图片来源，调用 ML Kit 条码识别库执行解码。对原始结果做空值处理和排序后返回，**输出数据中的字段均为非空**。

## 接口

```
suspend scan(uri: Uri) → DecodeResult
suspend scan(bitmap: Bitmap) → DecodeResult
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
  └─ cornerPoints: List<Point> — 四个角点
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

Scanner 实例作为 AppViewModel 的属性创建，ViewModel 初始化时创建，`onCleared()` 时调用 `close()` 释放。

## 边界

| 场景 | 行为 |
|------|------|
| URI 不可读或格式不受支持 | Failure |
| 成功但无条码 | Success(emptyList()) |
| 扫描过程被取消 | 静默终止，不返回 |
| Bitmap 为 null 或已回收 | Failure |
| rawValue 和 rawBytes 皆 null | 丢弃该条目 |
| cornerPoints 和 boundingBox 皆 null | 丢弃该条目 |

## 依赖

- `com.google.mlkit:barcode-scanning`

## 消费方

`DecodeResult` 映射为 `DecodeUiState(barcodes, decodeError)`，由 ui-result 模块处理。消费方拿到的 `cornerPoints` 均为非空，可直接用于绘制。
