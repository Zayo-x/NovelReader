# -*- coding: utf-8 -*-
from PIL import Image, ImageDraw

S = 2  # 超采样倍数（抗锯齿）
W = 1024
INK = (62, 92, 118, 255)          # 黛青 #3E5C76
PAPER = (245, 241, 232, 255)      # 米白 #F5F1E8
PAPER_DIM = (232, 227, 216, 255)  # 米白略暗（左页）
CINNABAR = (176, 91, 59, 255)     # 朱砂 #B05B3B
INK_DARK = (43, 43, 43, 255)      # 深墨

canvas = Image.new("RGBA", (W * S, W * S), (0, 0, 0, 0))

# 黛青底（先方形，再裁圆角）
base = Image.new("RGBA", (W * S, W * S), INK)
d = ImageDraw.Draw(base)
# 水墨晕染层次
d.ellipse([-120 * S, -120 * S, 460 * S, 460 * S], fill=(18, 28, 42, 52))
d.ellipse([620 * S, 660 * S, 1160 * S, 1160 * S], fill=(18, 28, 42, 52))
d.ellipse([200 * S, 200 * S, 824 * S, 824 * S], fill=(255, 255, 255, 12))

# 圆角遮罩
mask = Image.new("L", (W * S, W * S), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, W * S, W * S], radius=180 * S, fill=255)
canvas.paste(base, (0, 0), mask)

draw = ImageDraw.Draw(canvas)


def pt(x, y):
    return (x * S, y * S)


def poly(pts):
    return [pt(x, y) for x, y in pts]


# 打开的书（俯视，中间书脊下凹、两页向两侧展开）
draw.polygon(poly([(512, 660), (300, 610), (280, 730), (512, 780)]), fill=PAPER_DIM)
draw.polygon(poly([(512, 660), (724, 610), (744, 730), (512, 780)]), fill=PAPER)
draw.line([pt(512, 660), pt(512, 780)], fill=(198, 188, 172, 255), width=3 * S)

# 毛笔（斜放，笔杆米白，笔头深墨，笔尖蘸向书页）
draw.polygon(poly([(719.7, 309.8), (680.3, 290.2), (570.3, 510.2), (609.7, 529.8)]), fill=PAPER)
draw.polygon(poly([(609.7, 529.8), (570.3, 510.2), (575, 615)]), fill=INK_DARK)

# 笔杆红圈（朱砂点缀）
draw.ellipse([pt(662 - 16, 377 - 16), pt(662 + 16, 377 + 16)], fill=CINNABAR)

# 笔尖墨点（落在右页上）
draw.ellipse([pt(580 - 15, 635 - 15), pt(580 + 15, 635 + 15)], fill=INK_DARK)

out = r"D:\deepseek-harness\杂七杂八\NovelReader\logo.png"
canvas.resize((W, W), Image.LANCZOS).save(out)
print("saved:", out)
