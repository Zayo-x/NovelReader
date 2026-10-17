# -*- coding: utf-8 -*-
"""生成测试用漫画文件：CBZ 压缩包 + PDF"""
import os
import shutil
import zipfile
from PIL import Image, ImageDraw, ImageFont

OUT = r"D:\deepseek-harness\杂七杂八\NovelReader\test_comics"
os.makedirs(OUT, exist_ok=True)

FONT = ImageFont.truetype("C:/Windows/Fonts/msyhbd.ttc", 90)
FONT_S = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 44)

# 两话，每话若干页；第一话 12 页用于验证自然排序（2 排在 10 前面）
CHAPTERS = [("第01话", 12), ("第02话", 6)]
COLORS = [
    (214, 226, 235), (235, 226, 214), (222, 235, 222),
    (235, 214, 226), (226, 222, 235), (235, 235, 214),
]

frames = []  # 供 PDF 使用


def make_page(chapter, index, total, size=(800, 1200)):
    img = Image.new("RGB", size, COLORS[index % len(COLORS)])
    d = ImageDraw.Draw(img)
    d.rectangle([20, 20, size[0] - 20, size[1] - 20], outline=(90, 100, 110), width=4)
    d.text((size[0] // 2, 140), chapter, font=FONT_S, fill=(70, 80, 90), anchor="mm")
    d.text((size[0] // 2, size[1] // 2), str(index), font=FONT, fill=(40, 50, 60), anchor="mm")
    d.text((size[0] // 2, size[1] - 120), f"{index} / {total}", font=FONT_S,
           fill=(90, 100, 110), anchor="mm")
    return img


# ---- CBZ ----
cbz_path = os.path.join(OUT, "测试漫画.cbz")
with zipfile.ZipFile(cbz_path, "w", zipfile.ZIP_DEFLATED) as zf:
    for chapter, total in CHAPTERS:
        for i in range(1, total + 1):
            page = make_page(chapter, i, total)
            name = f"{chapter}/{i:03d}.jpg"
            tmp = os.path.join(OUT, "_tmp.jpg")
            page.save(tmp, "JPEG", quality=85)
            zf.write(tmp, name)
            frames.append(page)
            os.remove(tmp)

# ---- PDF（单章 6 页）----
pdf_path = os.path.join(OUT, "测试PDF.pdf")
pdf_pages = []
for i in range(1, 7):
    pdf_pages.append(make_page("PDF 测试", i, 6, size=(1240, 1754)))
pdf_pages[0].save(pdf_path, "PDF", save_all=True, append_images=pdf_pages[1:], resolution=150)

print("CBZ:", cbz_path, round(os.path.getsize(cbz_path) / 1024, 1), "KB")
print("PDF:", pdf_path, round(os.path.getsize(pdf_path) / 1024, 1), "KB")
