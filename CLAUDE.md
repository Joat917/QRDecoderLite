# 协作约定

给协作者与自动工具的说明。项目事实见 `README.md` 与 `spec/`，这里只写怎么做。

## 命令

```
./gradlew assembleDebug          构建调试包
./gradlew installDebug           安装到已连接设备
./gradlew test                   单元测试
./gradlew connectedAndroidTest   设备测试
./gradlew lint                   静态检查
```

## 写代码时的约定

依赖版本统一由 `gradle/libs.versions.toml` 管理，构建文件通过 `libs.*` 引用，不写死版本号。

界面文字全部放 `res/values/strings.xml`，组合期用 `stringResource` 解析，回调里只用已解析的变量。

日志与用户可见的错误文字按 `spec/error-handling.md` 处理，捕获异常时必须带上异常对象。

单 Activity 结构，页面切换靠 `AppUiState.activeSheet`，不引入导航库。

模块之间通过接口通信。解码模块只暴露 `BarcodeScanner`，流程层不依赖具体引擎。

抽象只在被多处使用或明显冗长时引入。

## 写文档时的约定

`spec/` 放设计与约定，不写代码实现细节。

数据模型与接口定义只在 `spec/architecture.md` 出现一次，其它文档引用而不复制。

决策写进 `spec/decisions.md`，每条包含决定、理由、代价。

已完成任务的计划移到 `spec/history/`，并在开头标注状态。

文字风格：短句、少修饰、不用加粗和括号堆叠、不写没有信息量的套话。一份文档开头用一句话说明它管什么。

避开译文腔：动作直接用动词，不写成「对…进行…」；少用「由…决定」「由…记录」这类被动句；不硬译术语，API 名字保留原文；「的」字连用三次以上的长定语要拆句。

## 工作方式

动手前先确认路线。架构、模块划分、接口定义没有定下来之前，不写实现。

环境配置、构建、运行由项目所有者执行，自动工具不代为执行。

实现进展与验证结果记录在 `spec/testing/manual-checklist.md` 每轮追加的小节里，不混入设计文档。
