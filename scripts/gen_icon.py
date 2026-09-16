#!/usr/bin/env python3
import os
import struct
import zlib

SIZE = 128
BG = (0x1E, 0x2A, 0x44, 0xFF)
PAPER = (0xF4, 0xF1, 0xE8, 0xFF)
LINE = (0xB0, 0xAD, 0xA4, 0xFF)
ACCENT = (0x94, 0xE4, 0xD3, 0xFF)


def pixel(x, y):
    # paper sheet 80x96 centred, with three ruled lines and a teal tick on the second line
    px, py = x - 24, y - 16
    if 0 <= px < 80 and 0 <= py < 96:
        for row in (28, 48, 68):
            if row <= py < row + 4 and 12 <= px < 68:
                if row == 48 and 12 <= px < 24:
                    return ACCENT
                return LINE
        if row_tick(px, py):
            return ACCENT
        return PAPER
    return BG


def row_tick(px, py):
    # a 12x12 tick mark to the left of the first rule
    tx, ty = px - 12, py - 20
    if not (0 <= tx < 12 and 0 <= ty < 12):
        return False
    return (2 <= tx < 6 and ty == tx + 4) or (6 <= tx < 12 and ty == 14 - tx)


def write_png(path):
    raw = bytearray()
    for y in range(SIZE):
        raw.append(0)
        for x in range(SIZE):
            raw.extend(pixel(x, y))

    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack('>IIBBBBB', SIZE, SIZE, 8, 6, 0, 0, 0)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(chunk(b'IHDR', ihdr))
        f.write(chunk(b'IDAT', zlib.compress(bytes(raw), 9)))
        f.write(chunk(b'IEND', b''))


if __name__ == '__main__':
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    write_png(os.path.join(root, 'src', 'client', 'resources', 'assets', 'notedown', 'icon.png'))
