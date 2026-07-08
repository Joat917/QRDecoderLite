# 主界面模块 — ui-main

## 职责

展示应用 Logo 和功能入口按钮，处理用户点击事件（触发图片选择、预留相机/历史入口）。

## 布局

内容垂直居中，从上到下排列：

```
┌──────────────────────────────┐
│                              │
│                              │
│          (Logo)              │  自定义 vector drawable
│                              │  居中，大小约 96x96 dp
│                              │
│                              │
│                              │
│  ┌────────────────────────┐  │
│  │ icon  从相册             │  │  主按钮：正常色（Primary）
│  └────────────────────────┘  │  圆角、高度约 56dp
│                              │  左侧 Material Icon：image
│  ┌────────────────────────┐  │
│  │ icon  从相机             │  │  灰色按钮，不可点击
│  └────────────────────────┘  │  预留，当前版本不实现
│                              │  左侧 Material Icon：camera_alt
│  ┌────────────────────────┐  │
│  │ icon  从历史             │  │  灰色按钮，不可点击
│  └────────────────────────┘  │  预留，当前版本不实现
│                              │  左侧 Material Icon：history
│                              │
│                              │
└──────────────────────────────┘
```

### Logo

- 使用自定义 vector drawable 资源文件（`res/drawable/ic_logo.xml`）
- 内容为二维码风格的简约图形（待设计）
- 大小：约 96 x 96 dp
- 允许应用主题色的 tint（亮/暗模式自适应）

### 按钮样式

| 属性 | 值 |
|------|----|
| 宽度 | 占满内容区，左右留 32dp margin |
| 高度 | 56dp |
| 圆角 | 12dp |
| 文字 | 左侧 Material Icon 16dp + 间距 12dp + 文字 |
| 文字大小 | 16sp |
| 间距 | 按钮之间 12dp |

### 按钮状态

| 按钮 | 当前版本 | 状态 |
|------|---------|------|
| 从相册 | ✅ 可点击 | Primary 色填充，点击触发系统图片选择器 |
| 从相机 | ❌ 未实现 | 灰色不可点击，右下角标注"即将推出"（可选） |
| 从历史 | ❌ 未实现 | 灰色不可点击，右下角标注"即将推出"（可选） |

三个按钮纵向居中排列，与 Logo 间距约 48dp。

## 交互

### 点击"从相册"

1. 调用系统图片选择器（`PickVisualMedia` / `GetContent`）
2. 用户选择图片 → 将 URI 传给 ViewModel
3. ViewModel 立即将 `activeSheet` 设为 `Sheet.Result(uri)`
4. 用户取消选择 → 无操作，留在主界面

### 点击"从相机" / "从历史"

无响应（灰色禁用状态）。

## ViewModel 接口

应用采用单 ViewModel 架构。以下为对外暴露的接口：

```kotlin
class AppViewModel : ViewModel() {

    // ── 对外状态 ──

    /** 主界面状态（当前显示哪个页面） */
    val uiState: StateFlow<AppUiState>

    /** 结果页状态（解码中/成功/出错） */
    val decodeUiState: StateFlow<DecodeUiState>

    // ── 操作方法 ──

    /** 用户点击了"从相册"按钮 */
    fun onPickFromAlbum()

    /** 系统图片选择器返回了结果 */
    fun onImageSelected(uri: Uri?)

    /** 关闭结果页，返回主界面 */
    fun onBackToMain()

    /** 复制条码内容到剪贴板 */
    fun onCopy(content: String)

    /** 用系统浏览器打开 URL */
    fun onOpenLink(url: String)
}
```

## 数据模型

```kotlin
data class AppUiState(
    val activeSheet: Sheet? = null
)

sealed interface Sheet {
    data class Result(val imageUri: Uri) : Sheet
}
```

## 边界情况

| 场景 | 行为 |
|------|------|
| 用户取消图片选择 | `onImageSelected(null)` → `activeSheet` 不变，留在主界面 |
| 快速重复点击"从相册" | Compose 层 300ms 内忽略重复点击 |