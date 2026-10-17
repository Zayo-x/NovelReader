# -*- coding: utf-8 -*-
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W = H = 1080
BG = (245, 241, 232, 255)        # 米白（宣纸）
INK = (62, 92, 118, 255)         # 黛青 #3E5C76
CINNABAR = (176, 91, 59, 255)    # 朱砂 #B05B3B

img = Image.new("RGBA", (W, H), BG)

# 水墨晕染层
halo = Image.new("RGBA", (W, H), (0, 0, 0, 0))
hd = ImageDraw.Draw(halo)
hd.ellipse([60, 690, 460, 1080], fill=(62, 92, 118, 26))     # 左下黛青晕
hd.ellipse([700, 30, 1080, 430], fill=(176, 91, 59, 20))     # 右上朱砂晕
hd.ellipse([540, 900, 1060, 1080], fill=(62, 92, 118, 16))   # 右下淡晕
halo = halo.filter(ImageFilter.GaussianBlur(70))
img = Image.alpha_composite(img, halo)

draw = ImageDraw.Draw(img)

# 外框细线（宣纸装裱感）
draw.rectangle([40, 40, 1040, 1040], outline=INK, width=3)

# 左下：黛青月亮（实心圆）
draw.ellipse([110, 810, 250, 950], fill=(62, 92, 118, 205))

# 中央：竖排标题「翰墨阅读器」
font_title = ImageFont.truetype("C:/Windows/Fonts/simhei.ttf", 110)
title = "翰墨阅读器"
x = 540
ys = [290, 420, 550, 680, 810]
for ch, y in zip(title, ys):
    draw.text((x, y), ch, font=font_title, fill=INK, anchor="mm")

# 底部：宣传语
font_slogan = ImageFont.truetype("C:/Windows/Fonts/simsun.ttc", 46, index=0)
draw.text((540, 942), "执卷翰墨 · 阅尽山河", font=font_slogan, fill=CINNABAR, anchor="mm")

# 右上：朱砂印章「翰墨」
draw.rounded_rectangle([830, 110, 990, 270], radius=22, fill=CINNABAR)
font_seal = ImageFont.truetype("C:/Windows/Fonts/simhei.ttf", 52)
draw.text((910, 165), "翰", font=font_seal, fill=(255, 255, 255, 255), anchor="mm")
draw.text((910, 220), "墨", font=font_seal, fill=(255, 255, 255, 255), anchor="mm")

out = r"D:\deepseek-harness\杂七杂八\NovelReader\宣传图.png"
img.convert("RGB").save(out)
print("saved:", out)
