# 解码模块

本文说明解码模块的设计，界面与流程不在范围内。

## 职责

接收图片，用两个识别引擎解码，把结果合并成一份列表交给 `AppViewModel`。

两个引擎能力不同：ML Kit 识别全部条码格式，WeChatQRCode 只识别二维码，但对难认的二维码识别率更高。两边的结果合并后按文本去重。

## 接口

```kotlin
interface BarcodeScanner {
    suspend fun scan(uri: Uri): DecodeResult
    suspend fun scan(bitmap: Bitmap): DecodeResult
    fun close()
}
```

生产实现是 `CombinedScanner`，由 `AppViewModel` 的构造参数默认注入。测试注入替代实现，不加载 ML Kit 与 OpenCV。

`scan(bitmap)` 传入的 Bitmap 归调用方所有，模块不回收。`scan(uri)` 由模块自己解码，用完立即回收。

## 实现

`MlKitScanner` 封装 ML Kit 的条码识别。每条原始结果先确定文本，再确定位置点。

确定文本的顺序是：有 `rawValue` 用 `rawValue`；没有则用 `rawBytes` 转成十六进制字符串；两者都没有就丢掉这条结果，因为没有内容的结果没有意义。

确定位置点的顺序是：有 `cornerPoints` 直接用；没有则用 `boundingBox` 的四个角推算；两者都没有则保留文本，位置点留空。

结果的排序按四个优先级：可读文本且有位置点、可读文本且位置点由外框推算、十六进制文本且有位置点、十六进制文本且位置点由外框推算。同一优先级内保持 ML Kit 返回的顺序。没有位置点的结果排在最后。

`WeChatQrScanner` 封装 WeChatQRCode。引擎在首次识别时才初始化，初始化包括加载 OpenCV 与检测器；初始化失败会转成解码失败，不会抛到上层。

读图时只打开文件一次，把内容读进内存后解码两次，一次读尺寸、一次按缩小后的尺寸解码。长边超过 2000 像素时先缩小再解码，缩小的比例取 2 的幂。设这个上限是为了控制内存：一张 4000×3000 的照片按原尺寸要占 48 MB，识别过程还要再翻一倍。

库把位置点放在一个列表里，按码的顺序排列。只有位置点个数等于识别结果数乘 4 时，才能确定每个码的四个角，此时按顺序挂到对应结果上；其它情况说明位置不可用或排列方式未知，所有结果的位置点都留空。

`WeChatQrScanner` 的格式名固定为 `QR_CODE`。

`CombinedScanner` 接收两个引擎，并行执行，再把两份结果合并。合并规则放在下一节。

## 合并规则

| ML Kit 结果 | WeChat 结果 | 合并结果 |
|---|---|---|
| 失败 | 成功 | 用 WeChat 的结果 |
| 成功 | 失败 | 用 ML Kit 的结果 |
| 成功 | 成功 | 两份结果按文本去重后合并，ML Kit 的结果在前 |
| 失败 | 失败 | 失败，两条原因换行拼接 |

单个引擎失败不会让整体失败。失败信息只记录在日志里，不影响另一侧已经识别出的内容。

两份结果内容相同时保留 ML Kit 的结果，位置点与格式名也取 ML Kit 的。

空列表算成功，表示这一侧没有识别到码。一侧成功为空、另一侧失败时，结果是成功且为空。

## 输出保证

`content` 一定非空。拿不到文本的结果直接丢掉。

`cornerPoints` 可以为空。引擎不因为缺位置就丢掉文本，是否画多边形由结果页决定。

## 生命周期

`CombinedScanner` 由 `AppViewModel` 创建，在 `onCleared` 时调用 `close` 释放 ML Kit 客户端。OpenCV 的初始化发生在首次识别，生命周期覆盖整个进程，`close` 不回收。

## 错误处理

所有失败转成 `DecodeResult.Failure`，原因文字按 `spec/error-handling.md` 的规则构造。模块内部不做重试。

## 已知限制

微信引擎直接解码 Bitmap，不读 EXIF 方向，旋转过的照片可能识别不到。ML Kit 经由 `InputImage` 读取文件，会校正方向。

微信引擎对整张图直接解码时可能只得到文本、没有位置点，这类结果的位置点为空。

混淆会破坏 OpenCV 的 JNI 调用，`app/proguard-rules.pro` 为 OpenCV 的字段名保留原名，理由见 `spec/decisions.md`。

## 依赖

- `com.google.mlkit:barcode-scanning`
- `com.github.jenly1314.WeChatQRCode:opencv` 与 `opencv-*`，含各架构的原生库文件
- `com.github.jenly1314.WeChatQRCode:wechat-qrcode`

## 消费方

`AppViewModel` 通过接口调用。拿到的 `DecodeResult` 被映射为 `DecodeUiState`，由结果页展示。
