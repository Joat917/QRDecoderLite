# 测试方案

## 测试分类与运行命令

| 类型 | 目录 | 运行命令 | 运行环境 |
|------|------|---------|---------|
| Unit Test | `app/src/test/` | `./gradlew test` | 本地 JVM |
| Instrumented Test | `app/src/androidTest/` | `./gradlew connectedAndroidTest` | 设备/模拟器 |

## 测试基础设施

### 依赖

| 依赖 | 用途 |
|------|------|
| `kotlinx-coroutines-test` | `runTest` / `TestDispatcher` / `Dispatchers.setMain` |
| `androidx.test:core` | `ApplicationProvider.getApplicationContext` (JVM 兼容) |
| `gradle: unitTests.isReturnDefaultValues = true` | Android SDK 方法在 JVM 上返回默认值而非抛异常 |

### 测试辅助类

| 类 | 文件 | 用途 |
|----|------|------|
| `FakeBarcodeScanner` | `viewmodel/FakeBarcodeScanner.kt` | 实现 `BarcodeScanner` 接口，测试用 fake |
| `MainDispatcherRule` | `TestSupport.kt` | JUnit Rule：将主协程调度器替换为 `TestDispatcher` |
| `TestUriFactory` | `TestSupport.kt` | 反射创建 `android.net.Uri` 实例（JVM 上 `Uri.parse` 返回 null） |

---

## 模块测试覆盖方案

### 1. Scanner 解码模块

#### Unit Test ✅ 已实现

| 测试目标 | 测试内容 | 状态 |
|---------|---------|------|
| bytesToHex 格式 | 单字节/多字节/空/全字节范围 | ✅ |
| formatToString | 13 种已知格式 + UNKNOWN | ✅ |
| SortableBarcode.priority | 4 级优先级各自正确 | ✅ |
| sortToDecoded | 4 级混合排序、同优先级稳定、空输入、单元素 | ✅ |
| sortToDecoded | 输出字段保留 (content/format/cornerPoints) | ✅ |

#### Instrumented Test（待实现）

| 测试目标 | 测试内容 | 说明 |
|---------|---------|------|
| 真实二维码（URL） | 传含二维码的 asset 图片 → `Success`，`barcodes` 列表非空 | 端到端验证 ML Kit 集成有效 |
| 真实二维码（纯文本） | 同上，验证文本内容解码正确 | |
| 无码图片 | 传不含二维码的图片 → `Success(emptyList())` | 区分"无结果"和"失败" |
| 无效 URI | 传不存在的文件 URI → `Failure` | 验证异常被正确捕获并映射 |
| 多码图片 | 传包含多个二维码的图片 → 结果列表数量 >= 2 | 多码检测能力 |

### 2. ViewModel

#### Unit Test ✅ 已实现

| 测试目标 | 测试内容 | 状态 |
|---------|---------|------|
| 初始状态 | `uiState.activeSheet == null` | ✅ |
| 初始状态 | `decodeUiState` 未初始化 | ✅ |
| onImageSelected | 传入 null → `activeSheet` 不变 | ✅ |
| onImageSelected | 传入有效 URI → `Sheet.Result` + decodeUiState 创建 | ✅ |
| onBackToMain | 调用后 → `activeSheet = null` | ✅ |
| onBackToMain | 旧扫描 job 被取消 | ✅ |
| 解码完成（成功） | scanner 返回 `Success(barcodes)` → `decodeUiState.barcodes` 非空 | ✅ |
| 解码完成（空） | scanner 返回 `Success(emptyList())` → `decodeUiState.barcodes == emptyList()` | ✅ |
| 解码失败 | scanner 返回 `Failure(reason)` → `decodeUiState.decodeError == reason` | ✅ |
| 解码失败 | FileNotFoundException / IOException 等异常映射 | ✅ |
| 连续选图 | 选图 A 解码中 → 选图 B → B 的结果覆盖 A | ✅ |
| 连续选图 | 第二次失败覆盖第一次成功 | ✅ |
| 异常流程 | 已在主界面时调用 onBackToMain 不报错 | ✅ |

### 3. 主界面（ui-main）

#### Unit Test（依赖 Compose 环境，待实现）

| 测试目标 | 测试内容 | 说明 |
|---------|---------|------|
| 按钮启用状态 | "从相册"/"从文件"可点击 | primary 色填充 |
| 按钮启用状态 | "从相机"/"从历史"不可点击 | 灰色 disabled |

#### Instrumented Test（待实现）

| 测试目标 | 测试内容 | 说明 |
|---------|---------|------|
| UI 渲染 | 打开应用 → 显示 Logo + 四个按钮 | 页面元素完整 |
| 点击"从相册" | 点击按钮 → 系统图片选择器弹出 | 交互连通 |
| 点击"从文件" | 点击按钮 → 系统文件选择器弹出（过滤图片） | 交互连通 |
| 禁用按钮 | "从相机"/"从历史"点击无响应 | 确认点击事件被阻止 |

### 4. 结果页（ui-result）

#### Unit Test ✅ 已实现

| 测试目标 | 测试内容 | 状态 |
|---------|---------|------|
| URL 判定 | `"http://example.com"` → 是 URL | ✅ |
| URL 判定 | `"https://example.com"` → 是 URL | ✅ |
| URL 判定 | `"www.example.com"` → 非 URL | ✅ |
| URL 判定 | `"123456"` → 非 URL | ✅ |
| URL 判定 | `""` → 非 URL | ✅ |
| URL 判定 | `"http://"` → 是 URL（仅前缀检查） | ✅ |
| URL 判定 | `"ftp://"` / `"HTTP://"` → 仅小写 http/https | ✅ |
| 调色板循环 | 0-5 个条码 → 各自取 index 0-5 | ✅ |
| 调色板循环 | 第 7 个条码 → 取 index 0 | ✅ |
| 调色板循环 | 每个颜色的四层 (fill/stroke/background/bar) 均有透明度 | ✅ |
| 射线法 | 点在多边形内部 → true | ✅ |
| 射线法 | 点在多边形外部 → false | ✅ |
| 射线法 | 点在顶点 → true | ✅ |
| 射线法 | 点在边上 → true | ✅ |
| 射线法 | 空多边形 → false | ✅ |
| 射线法 | 单/双点多边形 → false | ✅ |
| 射线法 | 菱形对角线 → true/false | ✅ |
| 射线法 | 三角形/不规则四边形 | ✅ |

#### Instrumented Test（待实现）

| 测试目标 | 测试内容 | 说明 |
|---------|---------|------|
| 图片渲染 | 选中图片后 → 图片正常显示 | Coil 加载验证 |
| 解码中 | 解码完成前 → 显示"正在识别…" | |
| 有条码 | 解码完成 → 多边形标注可见 + 卡片列表可见 | |
| 无条码 | 解码完成空结果 → 显示"未识别到二维码" | |
| 出错 | 解码失败 → 错误卡片可见 | |
| 复制 | 点击 `content_copy` → 内容写入剪贴板 | |
| 打开 URL | 点击 `open_in_new` → 系统浏览器打开 | |
| 返回 | 点击返回箭头 → 回到主界面 | 页面导航正确 |

### 5. 数据模型

#### Unit Test ✅ 已实现

| 测试目标 | 测试内容 | 状态 |
|---------|---------|------|
| `AppUiState` | 默认值、copy、Sheet 持有 | ✅ |
| `Sheet.Result` | URI 持有、equals | ✅ |
| `DecodeUiState` | 默认值、成功/空/错误三状态、copy | ✅ |
| `DecodedBarcode` | 字段存取、equals/hashCode | ✅ |
| `DecodeResult` | Success/Failure sealed 分支 | ✅ |

---

## 测试优先级（更新）

| 优先级 | 内容 | 状态 |
|--------|------|------|
| P0 | Scanner 排序+格式映射（Unit） | ✅ 已实现 |
| P0 | ViewModel 状态转换（Unit） | ✅ 已实现 |
| P1 | URL 判定 + 调色板循环 + 射线法（Unit） | ✅ 已实现 |
| P1 | 真实二维码解码（Instrumented） | ⏳ 待实现 |
| P2 | UI 渲染（Instrumented） | ⏳ 待实现 |
| P2 | 连续选图、取消等边界（Unit） | ✅ 已实现 |
