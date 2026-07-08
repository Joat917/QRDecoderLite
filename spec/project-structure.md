# 项目目录结构

```
QRDecoderLite/
├── CLAUDE.md                    # AI 辅助开发指引（本文件）
├── build.gradle.kts             # 根级构建配置，声明全局插件但不 apply
├── settings.gradle.kts          # 项目设置：仓库、模块注册、rootProject.name
├── gradle.properties            # Gradle 全局属性（JVM 参数、Kotlin 编码风格）
├── gradlew / gradlew.bat        # Gradle Wrapper 脚本（Linux / Windows）
├── gradle/
│   ├── libs.versions.toml       # 版本目录：统一管理所有依赖版本和插件版本
│   ├── wrapper/
│   │   ├── gradle-wrapper.jar
│   │   └── gradle-wrapper.properties
│   └── gradle-daemon-jvm.properties
├── spec/                        # 项目规格文档（架构、设计、接口定义）
│   └── project-structure.md     # 本文件
└── app/                         # 唯一 Android 应用模块
    ├── build.gradle.kts         # 模块构建配置：SDK 版本、依赖、Compose 启用
    ├── proguard-rules.pro       # 混淆规则（发布构建用）
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/joat917/qrdecoderlite/
        │   │   ├── MainActivity.kt            # 唯一 Activity，Compose 入口
        │   │   └── ui/theme/
        │   │       ├── Color.kt               # 调色板颜色常量
        │   │       ├── Theme.kt               # 主题组合函数（亮/暗 + Dynamic Color）
        │   │       └── Type.kt                # Material3 Typography 定义
        │   └── res/                           # Android 资源文件
        │       ├── drawable/                  # 启动图标矢量
        │       ├── mipmap-*/                  # 启动图标位图（多密度）
        │       ├── values/
        │       │   ├── colors.xml             # XML 颜色资源
        │       │   ├── strings.xml            # 字符串资源（app_name = QRDecoderLite）
        │       │   └── themes.xml             # XML 主题（与 Compose 主题配合，用于启动页）
        │       └── xml/
        │           ├── backup_rules.xml
        │           └── data_extraction_rules.xml
        ├── test/                              # 本地单元测试
        │   └── java/com/joat917/qrdecoderlite/
        │       └── ExampleUnitTest.kt         # 模板测试（JUnit 4）
        └── androidTest/                       # 插桩测试（需设备/模拟器）
            └── java/com/joat917/qrdecoderlite/
                └── ExampleInstrumentedTest.kt # 模板测试（Compose UI + Espresso）
```

## 关键文件说明

### 构建系统（需重点关注）

| 文件 | 作用 | 说明 |
|------|------|------|
| `build.gradle.kts`（根级） | 声明全局插件 | 所有插件设置 `apply false`，仅声明不应用 |
| `app/build.gradle.kts` | 模块构建配置 | SDK 版本、Compose 功能、依赖项均在此配置 |
| `settings.gradle.kts` | 项目结构和仓库 | 注册 `:app` 模块，配置 Google/Maven 仓库 |
| `gradle/libs.versions.toml` | 版本目录 | 集中管理所有依赖版本，构建文件通过 `libs.*` 引用 |
| `gradle.properties` | Gradle JVM 参数 | `-Xmx2048m`，UTF-8 编码 |

### 源代码（实现状态）

| 文件 | 当前内容 | 说明 |
|------|----------|------|
| `MainActivity.kt` | `when(sheet)` 切换 MainScreen / ResultScreen | 单 Activity + Sheet 导航已实现 |
| `model/Models.kt` | `AppUiState`、`Sheet`、`DecodeUiState`、`DecodedBarcode`、`DecodeResult` | 全部数据模型已定义 |
| `viewmodel/AppViewModel.kt` | 图片选择 → 解码 → 结果展示的完整流程 | ViewModel + StateFlow 已实现 |
| `scanner/Scanner.kt` | ML Kit 解码封装，含异常映射、排序、空值过滤 | 核心解码逻辑已实现 |
| `ui/screen/MainScreen.kt` | Logo + "从相册"/"从文件"/"从相机"/"从历史"按钮 | PhotoPicker + OpenDocument 集成完成 |
| `ui/screen/ResultScreen.kt` | 图片 + 多边形标注 + 卡片列表 + 操作栏 + 状态指示 | 结果页完整实现 |
| `ui/theme/` | Dynamic Color + 亮/暗模式回退 | 主题系统已配置 |

### 技术栈现状

| 项目 | 版本 |
|------|------|
| Gradle | 9.4.1 |
| AGP | 9.2.1 |
| Kotlin | 2.4.0 |
| Compose BOM | 2026.05.00 |
| ML Kit Barcode Scanning | 17.3.0 |
| Coil | 3.5.0 |
| compileSdk / targetSdk | 36 |
| minSdk | 24 |
