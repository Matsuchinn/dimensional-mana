"""生成古老魔法石碑的可编辑 Blockbench 模型与贴图。"""
import random
from bbmodel import Model

STONE = (57, 54, 68)
STONE_D = (35, 33, 45)
STONE_L = (85, 79, 98)
RUNE = (143, 79, 219)
RUNE_L = (211, 161, 255)
METAL = (105, 81, 126)

m = Model("ancient_magic_stele", tex_size=64)
rnd = random.Random(9421)

m.region("stone", 0, 0, 16, 24)
m.region("stone_dark", 16, 0, 32, 24)
m.region("rune_face", 32, 0, 48, 24)
m.region("base", 0, 24, 20, 40)
m.region("crystal", 20, 24, 32, 36)
m.region("trim", 32, 24, 48, 36)

m.fill("stone", STONE)
m.paint("stone", lambda u, v, w, h: rnd.choice([STONE_D, STONE_L]) if rnd.random() < 0.10 else None)
m.fill("stone_dark", STONE_D)
m.paint("stone_dark", lambda u, v, w, h: STONE if rnd.random() < 0.08 else None)
m.fill("base", STONE_D)
m.paint("base", lambda u, v, w, h: STONE if ((u + v) % 7 == 0 or rnd.random() < 0.06) else None)
m.fill("trim", METAL)
m.fill("crystal", RUNE)
m.paint("crystal", lambda u, v, w, h: RUNE_L if u == v or u + v == w - 1 or rnd.random() < 0.10 else None)
m.fill("rune_face", STONE)

def rune_paint(u, v, w, h):
    cx, cy = w // 2, h // 2
    dx, dy = u - cx, v - cy
    ring = 20 <= dx * dx + dy * dy <= 32
    spokes = (abs(dx) <= 0 and abs(dy) <= 6) or (abs(dy) <= 0 and abs(dx) <= 6)
    diamond = abs(abs(dx) + abs(dy) - 5) <= 0
    if ring or spokes or diamond:
        return RUNE_L if (u + v) % 3 == 0 else RUNE
    if rnd.random() < 0.07:
        return STONE_D
    return None
m.paint("rune_face", rune_paint)

# 底座与台阶
m.cube("base_foot", (0.5, 0, 2.0), (15.5, 2.5, 14.0), tex={"up": "base", "*": "stone_dark"})
m.cube("base_step", (2.0, 2.5, 3.0), (14.0, 5.0, 13.0), tex={"up": "base", "*": "stone"})
m.cube("pedestal", (3.2, 5.0, 4.0), (12.8, 7.5, 12.0), tex="stone_dark")

# 墓碑主体：中段宽，肩部逐级内收，形成厚重墓碑轮廓
m.cube("body", (3.0, 7.5, 5.0), (13.0, 25.0, 11.0), tex={"north": "rune_face", "*": "stone"})
m.cube("shoulder_l", (3.0, 24.0, 5.0), (6.0, 28.0, 11.0), tex="stone")
m.cube("shoulder_r", (10.0, 24.0, 5.0), (13.0, 28.0, 11.0), tex="stone")
m.cube("crown", (5.0, 25.0, 5.0), (11.0, 30.0, 11.0), tex={"north": "rune_face", "*": "stone_dark"})

# 正面边框与浮雕，略微凸出避免闪烁
m.cube("trim_left", (3.35, 9.0, 4.72), (4.15, 24.0, 5.05), tex="trim")
m.cube("trim_right", (11.85, 9.0, 4.72), (12.65, 24.0, 5.05), tex="trim")
m.cube("trim_top", (4.0, 23.2, 4.72), (12.0, 24.0, 5.05), tex="trim")
m.cube("rune_gem", (6.0, 16.0, 4.45), (10.0, 20.0, 5.0), tex="crystal", rotation=(0, 0, 45), origin=(8, 18, 4.75))

# 顶部魔晶，像石碑上仍在发光的知识核心
m.cube("top_gem_a", (6.2, 29.0, 6.2), (9.8, 32.0, 9.8), tex="crystal")
m.cube("top_gem_b", (6.2, 29.0, 6.2), (9.8, 32.0, 9.8), tex="crystal", rotation=(0, 45, 0), origin=(8, 30.5, 8))

m.save("model_sources/ancient_magic_stele.bbmodel", model_format="java_block")
lo, hi = m.bounds()
print(f"已生成石碑，包围盒 {tuple(lo)} -> {tuple(hi)}")
