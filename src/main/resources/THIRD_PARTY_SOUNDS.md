# r36 法术音效来源与许可

本文件与 `SOUND_SOURCES.json` 随模组 JAR 一起分发。JAR 可用普通 ZIP 解压工具打开。
本版以公开音效的剪辑和叠层成品实现“前摇、释放、生效/命中”三个声音时间点，并为风刃与落石补入专门素材。
“免费素材”不等于全部无版权；每个输出文件的具体许可见 `SOUND_SOURCES.json`。

## 作者与来源

### Impact Sounds
- 作者：**Kenney**。
- 官方页面：https://kenney.nl/assets/impact-sounds
- 原始下载：https://kenney.nl/media/pages/assets/impact-sounds/87b4ddecda-1677589768/kenney_impact-sounds.zip
- 许可：**CC0 1.0**，https://creativecommons.org/publicdomain/zero/1.0/
- 本版使用矿石、金属、木材、草地、玻璃、钟等拟音素材。感谢作者免费提供。

### Ice breaking/shattering
- 作者：**IgnasD**。
- 作者页面：https://opengameart.org/content/ice-breakingshattering
- 原始下载：https://opengameart.org/sites/default/files/IceShatters_0.zip
- 许可：**CC0 1.0**，https://creativecommons.org/publicdomain/zero/1.0/
- 本版用于冰锥、冰盾与施法破碎声。

### Spell Sounds Starter Pack
- 作者：**p0ss**。
- 作者页面：https://opengameart.org/content/spell-sounds-starter-pack
- 原始下载：https://opengameart.org/sites/default/files/spells.zip
- 发布页提供多重许可，本版选择 **Creative Commons Attribution-ShareAlike 3.0 Unported（CC BY-SA 3.0，署名—相同方式共享）**。
- 许可链接：https://creativecommons.org/licenses/by-sa/3.0/
- 法律文本：https://creativecommons.org/licenses/by-sa/3.0/legalcode
- 该作者不是本模组的合作方或推荐人。

### Short wind sound
- 作者：**remaxim**。
- 作者页面：https://opengameart.org/content/short-wind-sound
- 原始文件：https://opengameart.org/sites/default/files/short%20wind%20sound.wav
- 许可：**CC0 1.0**，https://creativecommons.org/publicdomain/zero/1.0/
- 本版用于风刃的横向切割呼啸和疾风位移声。

### Various Sound Effects
- 作者：**Spring Spring**。
- 作者页面：https://opengameart.org/content/various-sound-effects-0
- 使用 `break_stone.wav`、`small_rock_impact.wav`、`crush.wav` 与 `spear.wav`。
- 许可：**CC0 1.0**，https://creativecommons.org/publicdomain/zero/1.0/
- 本版用于风刃切割层，以及落石落地时的重击、断裂和碎石层。

## 修改与再次使用

本版对声音进行了：按真实内容解码、转为单声道、裁去前导静音、剪辑、轻微变速、叠层混合、滤波、统一响度上限、淡入淡出，以及 OGG Vorbis 编码。
每个成品的原文件名、作者对应素材包、延迟、增益、变速、时长和 SHA-256 校验值均列在 `SOUND_SOURCES.json`。

含 p0ss 素材的改编音效继续按 **CC BY-SA 3.0** 发布。可以从 JAR 的 `assets/dimensional_mana/sounds/spell/` 提取、修改和再分发这些音效，请保留 p0ss 的署名、原始来源、许可链接以及修改说明，并遵守相同方式共享条款。
仅由 CC0 原料组成的本版音效也按 **CC0 1.0** 提供。
本模组的总体版权声明不对这些音效追加限制，也不将它们改为保留所有权利；以上许可仅适用于清单中的声音，不自动覆盖其余代码、美术或资源。

## 动画参考边界

仅参考 Iron’s Spells ’n Spellbooks 官方展示中“法术主体形态、材质差异和分阶段动作”的思路：
- https://iron.wiki/spells/
- https://iron.wiki/gallery/

本版未使用该模组的代码、模型、贴图、声音或动画文件，也不需要安装该模组作为依赖。
