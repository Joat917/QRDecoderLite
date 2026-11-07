# QRDecoderLite

图片扫码应用。用户从相册、文件或相机选一张图片，应用用两个识别引擎同时解码，把识别结果列成卡片，并在图片上标出二维码位置。

本文是项目入口：说明这是什么、怎么跑、文档放在哪。

## 使用流程

1. 在主界面选择图片来源：相册、文件、相机，或由其它应用分享单张图片进来。
2. 选图后立即进入结果页，原图先显示，解码在后台进行。
3. 解码完成后显示二维码文本和位置。识别不到或识别失败各有对应提示。
4. 每张结果卡片可以复制文本；文本是网址时还能用浏览器打开。

## 技术构成

单模块 Android 项目，单 Activity，界面用 Jetpack Compose 与 Material3。

识别用两个引擎：ML Kit 覆盖全部条码格式，微信引擎只认二维码但更难认的图也能认。两者并行执行，结果去重后合并。

图片显示用 Coil。运行环境为 minSdk 24，compileSdk 与 targetSdk 36，Kotlin 2.4.0。

## 构建与测试

```
./gradlew assembleDebug
./gradlew test
./gradlew connectedAndroidTest
```

设备测试需要连接设备或模拟器。测试用样图由 `tools/generate_test_images.py` 生成，说明见 `app/src/androidTest/assets/README.md`。

## 文档

| 文件 | 内容 |
|---|---|
| `spec/architecture.md` | 分层、数据流、模块边界、数据模型与接口 |
| `spec/modules/scanner.md` | 解码模块 |
| `spec/modules/ui-main.md` | 主界面 |
| `spec/modules/ui-result.md` | 结果页 |
| `spec/error-handling.md` | 日志格式与错误文案规则 |
| `spec/testing/test-plan.md` | 测试策略与自动化覆盖 |
| `spec/testing/manual-checklist.md` | 发布前手工验证清单 |
| `spec/decisions.md` | 设计决策记录 |
| `CLAUDE.md` | 协作约定 |

## 目录

```
app/src/main/java/com/joat917/qrdecoderlite/
  MainActivity.kt              应用入口，按状态切换页面
  model/Models.kt              界面状态与解码结果模型
  scanner/                     解码模块，含两个引擎与结果合并
  viewmodel/AppViewModel.kt    选图到解码的流程与状态
  ui/screen/                   主界面与结果页
  ui/theme/                    主题
  util/                        日志与错误文案工具
app/src/test/                  单元测试
app/src/androidTest/           设备测试
tools/generate_test_images.py  生成设备测试样图
spec/                          项目文档
```
