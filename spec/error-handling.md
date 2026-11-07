# 日志与错误文案

适用于所有模块。三条规则。

## 捕获异常必须记录

捕获到异常就必须写一条日志，并带上异常对象，否则堆栈丢失。成功路径也要留一条摘要，否则事后无法判断是否执行过、得到多少结果。

日志统一走 `util/AppLog`，日志标签即 TAG，值为 `QRLite/` 加组件名，与系统日志和第三方库日志区分开。

| 组件 | TAG | 负责内容 |
|---|---|---|
| MlKit | `QRLite/MlKit` | ML Kit 引擎 |
| WeChat | `QRLite/WeChat` | WeChatQRCode 引擎 |
| Combined | `QRLite/Combined` | 两引擎结果合并 |
| VM | `QRLite/VM` | `AppViewModel` 对未预期异常的捕获 |
| UI | `QRLite/UI` | 界面代码，如图片加载失败、拉起系统界面失败 |

筛选方式：

```
adb logcat | grep "QRLite/"
adb logcat QRLite/WeChat:D '*:S'
adb logcat -s QRLite/MlKit QRLite/WeChat QRLite/Combined QRLite/VM
```

Android Studio 的日志面板可用 `tag~QRLite/.*` 过滤，并确保级别过滤包含 Debug。

## 消息写清楚

格式为阶段加若干键值对，例如 `detect image=1200x900 texts=1 vertices=0 withPoints=0`。键名用代码里的字段名，其中 `vertices` 指库返回的位置点个数，`withPoints` 指带位置点的结果条数。

一条消息要能回答三个问题：在哪一步、输入是什么、观察到什么。输入可以是 URI、图片尺寸、字节数；观察值可以是识别条数、丢弃条数、合并结果。

不要写 `decode failed`、`error occurred` 这类没有信息量的句子。

## 用户看到真实原因

用户可见的错误文字格式为方括号加异常类名，空格后接异常自带的描述，例如 `[IOException] read failed: EIO`。

异常描述为空或全空白时，才用 `strings.xml` 里的备用文字。备用文字只在拿不到描述时出现，不能反过来盖掉真实描述。

没有真实异常对象的情况，例如图片已被回收、读不到文件、解码返回空，直接陈述事实，不编造类名。

不使用「请重试」「请稍后再试」这类不含信息的话。

| 情况 | 用户看到 | 日志 |
|---|---|---|
| 异常自带描述 | 方括号加类名，加原始描述 | 警告级，带堆栈与输入 |
| 异常描述为空 | 方括号加类名，加备用文字 | 同上 |
| 图片已被回收 | 图片数据已失效，请重新选择 | 警告级，无堆栈 |
| 图片加载失败 | 按前两条构造，没有描述时显示图片加载失败 | 警告级，带堆栈与 URI |

## 只保证调试版本可读

发布版本的类名被混淆，堆栈要用 `app/build/outputs/mapping/release/mapping.txt` 还原。本约定只承诺调试版本能直接从日志定位问题。

## 覆盖范围

| 模块 | 位置 |
|---|---|
| 解码 | 两个引擎的读图、初始化、识别、识别结果过滤，以及合并阶段 |
| 流程 | 解码协程的捕获 |
| 界面 | 图片加载失败、拉起系统界面失败 |
