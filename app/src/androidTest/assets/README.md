# 设备测试样图

`ScannerDeviceTest` 的四个识别用例依赖本目录 `qr/` 下的图片。

| 文件 | 内容 | 用途 |
|---|---|---|
| `qr/url.png` | 二维码，内容为 `https://example.com/a?b=1` | 验证识别结果带文本与角点 |
| `qr/ean13.png` | EAN-13 一维码，编码为 `5901234123457` | 验证 ML Kit 的一维码分支 |
| `qr/multi_3.png` | 三个不同二维码拼在一张白底图上 | 验证多码识别与去重 |
| `qr/no_code.jpg` | 一张没有任何码的图片 | 验证无结果时返回空列表 |

缺哪张图，用到它的用例就记为跳过，不会让整批测试失败。

## 生成

```
pip install "qrcode[pil]" pillow python-barcode
python tools/generate_test_images.py
```

脚本把四个文件写到本目录的 `qr/` 子目录，并在终端打印每个文件的大小。
