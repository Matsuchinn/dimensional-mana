"""把石碑 bbmodel 转成 Minecraft Java 方块模型 JSON。"""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = root / "model_sources/ancient_magic_stele.bbmodel"
target = root / "src/main/resources/assets/dimensional_mana/models/block/ancient_magic_stele.json"
model = json.loads(source.read_text(encoding="utf-8"))
resolution = model.get("resolution", {"width": 16, "height": 16})
uv_scale_x = 16.0 / resolution["width"]
uv_scale_y = 16.0 / resolution["height"]
faces_out = {}
elements = []
for cube in model["elements"]:
    faces_out = {}
    for face_name, face in cube.get("faces", {}).items():
        # Blockbench 按贴图实际像素保存 UV，而 Java 方块模型固定使用 0～16 的归一化 UV。
        raw_uv = face["uv"]
        faces_out[face_name] = {
            "uv": [
                round(raw_uv[0] * uv_scale_x, 4),
                round(raw_uv[1] * uv_scale_y, 4),
                round(raw_uv[2] * uv_scale_x, 4),
                round(raw_uv[3] * uv_scale_y, 4)
            ],
            "texture": "#stele"
        }
    element = {
        "name": cube["name"],
        "from": [round(v, 4) for v in cube["from"]],
        "to": [round(v, 4) for v in cube["to"]],
        "faces": faces_out
    }
    rotation = cube.get("rotation", [0, 0, 0])
    nonzero = [(axis, angle) for axis, angle in zip(("x", "y", "z"), rotation) if abs(angle) > 0.001]
    if nonzero:
        axis, angle = nonzero[0]
        element["rotation"] = {
            "origin": [round(v, 4) for v in cube.get("origin", [8, 8, 8])],
            "axis": axis,
            "angle": round(angle, 4),
            "rescale": bool(cube.get("rescale", False))
        }
    elements.append(element)

output = {
    "ambientocclusion": True,
    "textures": {
        "particle": "dimensional_mana:block/ancient_magic_stele",
        "stele": "dimensional_mana:block/ancient_magic_stele"
    },
    "elements": elements,
    "display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, -2, 0], "scale": [0.52, 0.52, 0.52]},
        "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"translation": [0, -4, 0], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.32, 0.32, 0.32]}
    }
}
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(target)
