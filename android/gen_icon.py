#!/usr/bin/env python3
# 生成视觉象棋助手启动图标（纯 stdlib，无需 PIL）。
import zlib, struct, os

def png_chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)

def make_icon(size):
    # RGB 像素
    buf = bytearray()
    cx = cy = size / 2.0
    r_out = size * 0.46
    r_in = size * 0.34
    for y in range(size):
        buf.append(0)  # filter byte
        for x in range(size):
            dx = x - cx + 0.5
            dy = y - cy + 0.5
            d = (dx * dx + dy * dy) ** 0.5
            if d <= r_out:
                if d <= r_in:
                    # 白色棋子
                    r, g, b = 250, 250, 250
                else:
                    # 红环
                    r, g, b = 198, 40, 40
            else:
                r, g, b = 16, 20, 24  # 背景
            buf += bytes((r, g, b))
    raw = bytes(buf)
    idat = zlib.compress(raw, 9)
    png = b"\x89PNG\r\n\x1a\n"
    png += png_chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
    png += png_chunk(b"IDAT", idat)
    png += png_chunk(b"IEND", b"")
    return png

base = os.path.join(os.path.dirname(__file__), "app", "src", "main", "res")
sizes = {"mipmap-mdpi": 48, "mipmap-hdpi": 72, "mipmap-xhdpi": 96, "mipmap-xxhdpi": 144, "mipmap-xxxhdpi": 192}
for d, s in sizes.items():
    p = os.path.join(base, d, "ic_launcher.png")
    with open(p, "wb") as f:
        f.write(make_icon(s))
    print("wrote", p, s)
