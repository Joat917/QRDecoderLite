# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

QRDecoderLite — 轻量级 Android 二维码解码应用。使用 Jetpack Compose + Material3 构建，单模块项目。

- **包名**: `com.joat917.qrdecoderlite`
- **compileSdk / targetSdk**: 36 (Android 15)
- **minSdk**: 24 (Android 7.0)
- **语言**: Kotlin
- **构建系统**: Gradle 9.4.1 + AGP 9.2.1 + Kotlin 2.2.10
- **依赖管理**: Gradle Version Catalog (`gradle/libs.versions.toml`)

## 构建与运行命令

```bash
# 构建 debug APK
./gradlew assembleDebug

# 安装到连接的设备/模拟器
./gradlew installDebug

# 运行所有单元测试
./gradlew test

# 运行所有插桩测试 (需连接设备/模拟器)
./gradlew connectedAndroidTest

# 检查依赖更新
./gradlew libsUpdateCheck

# Lint 检查
./gradlew lint
```

## 项目架构

### 模块结构

单模块 (`:app`)，详细目录结构见 [spec/project-structure.md](spec/project-structure.md)。

### 架构要点

- **UI 层**: Jetpack Compose + Material3
- **主题**: Android 12+ 支持 Dynamic Color，回退到紫色系静态调色板
- **Activity**: 单 Activity 架构（`ComponentActivity`），Edge-to-Edge 显示

### 依赖体系 (Version Catalog)

| 类别 | 依赖 |
|------|------|
| Compose | compose-bom 2026.02.01, Material3, UI, UI Graphics |
| Activity | activity-compose 1.13.0 |
| Lifecycle | lifecycle-runtime-ktx 2.10.0 |
| Core | core-ktx 1.18.0 |
| 测试 | JUnit 4.13.2, Espresso 3.7.0, Compose UI Test |

### 设计约定

- 遵循 Android 官方推荐的单 Activity + Compose 导航模式
- 使用版本目录统一管理依赖，禁止直接硬编码版本号
- Gradle 使用 Kotlin DSL，模块级 `build.gradle.kts` 通过 `libs.*` 引用目录
- 主题支持亮/暗模式切换

## 工作流程约定

- **架构先行**: 在完全确认项目路线（架构设计、模块划分、接口定义）之前，不要开始编写实现代码。
- **需求与设计文档**: 项目规格、架构设计、接口定义等写入 `spec/` 目录下的文档。实现进展（测试结果）和项目要求（架构、设计）尽量写在不同的文件中。
- **规划文件只包含高层信息**: 架构、设计和接口定义等，不包含代码实现细节。
- **环境搭建由用户完成**: 环境配置（如安装 SDK、配置 Gradle）、代码构建和运行由用户手动执行，AI 不代为执行。
- **模块化原则**: 一个模块只处理一类数据或只和一个外部系统交互。模块之间通过明确的接口通信，避免全局状态和单例。
- **适度抽象**: 只有多次使用的或者较为冗长的代码块才需要封装成函数/类。过度抽象会增加理解和维护成本。
