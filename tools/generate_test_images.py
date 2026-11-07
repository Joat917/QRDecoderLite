"""生成 androidTest 需要的样图。

用法：
    pip install "qrcode[pil]" pillow python-barcode
    python tools/generate_test_images.py

生成到 app/src/androidTest/assets/qr/：
    url.png        单个二维码，内容为 URL
    ean13.png      EAN-13 一维码
    multi_3.png    三个不同二维码拼在一张白底图上
    no_code.jpg    无任何码的照片样图（渐变 + 圆形轮廓 + JPEG 压缩痕迹）
"""

from pathlib import Path
import random

import qrcode
from barcode import EAN13
from barcode.writer import ImageWriter
from PIL import Image, ImageDraw

OUT_DIR = Path(__file__).resolve().parents[1] / "app" / "src" / "androidTest" / "assets" / "qr"


def qr_image(text: str, box_size: int = 8) -> Image.Image:
    """生成一个白底黑码的二维码图片（含 4 模块静默区）。"""
    qr = qrcode.QRCode(
        box_size=box_size,
        border=4,
        error_correction=qrcode.constants.ERROR_CORRECT_M,
    )
    qr.add_data(text)
    qr.make(fit=True)
    return qr.make_image(fill_color="black", back_color="white").get_image().convert("RGB")


def make_url_qr() -> None:
    qr_image("https://example.com/a?b=1").save(OUT_DIR / "url.png")


def make_ean13() -> None:
    # 5901234123457 的校验位合法；不写字，避免一维码下方文字干扰
    EAN13("5901234123457", writer=ImageWriter()).save(
        str(OUT_DIR / "ean13"),
        options={"write_text": False, "module_height": 12.0},
    )


def make_multi_code() -> None:
    contents = [
        "https://example.com/first",
        "第二个二维码，纯文本内容",
        "WIFI:T:WPA;S:test;P:12345678;;",
    ]
    tiles = [qr_image(text) for text in contents]
    gap, margin = 40, 40
    width = sum(t.width for t in tiles) + gap * (len(tiles) - 1) + margin * 2
    height = max(t.height for t in tiles) + margin * 2

    canvas = Image.new("RGB", (width, height), "white")
    x = margin
    for tile in tiles:
        canvas.paste(tile, (x, margin))
        x += tile.width + gap
    canvas.save(OUT_DIR / "multi_3.png")


def make_no_code_photo() -> None:
    random.seed(7)
    width, height = 1200, 900
    image = Image.new("RGB", (width, height))
    draw = ImageDraw.Draw(image)

    for y in range(height):
        ratio = y / height
        draw.line(
            [(0, y), (width, y)],
            fill=(int(40 + 120 * ratio), int(90 + 60 * ratio), int(150 - 40 * ratio)),
        )

    for _ in range(60):
        x0 = random.randrange(-100, width)
        y0 = random.randrange(-100, height)
        x1 = x0 + random.randrange(20, 260)
        y1 = y0 + random.randrange(20, 260)
        draw.ellipse([x0, y0, x1, y1], outline=(250, 250, 250), width=3)

    image.save(OUT_DIR / "no_code.jpg", quality=85)


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    make_url_qr()
    make_ean13()
    make_multi_code()
    make_no_code_photo()

    for path in sorted(OUT_DIR.iterdir()):
        print(f"{path.name}\t{path.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
