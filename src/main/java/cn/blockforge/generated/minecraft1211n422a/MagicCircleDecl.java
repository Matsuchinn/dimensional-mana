package cn.blockforge.generated.minecraft1211n422a;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 条件节点「声明模式」的唯一数据源（《输出节点与条件声明模式规范》v2.2 第二部分）。
 *
 * <p>四槽位语义（规范 12）：语义槽锁边界组大类、元素槽定作用域、染料槽选 0~15 档、
 * 功率槽决定可声明组数 / 参数精度 / 局部效率。玩家在绘制界面里勾好边界组并微调参数，
 * 结果压成一串紧凑文本存进功能块的 {@code declare} 字段（{@link #encode}/{@link #parse}）；
 * 法阵码与 NBT 存的都是这一串，档位不存颜色名（规范 12.3 总则第 4 条）。</p>
 *
 * <p>染料档位固定表：白 0 → 品红 15（粉红 14）。同一个染料在不同边界组含义不同，
 * 档位只提供<b>主参数的基准值</b>，参数面板在功率精度允许的区间内微调（规范 12.3 四）。</p>
 */
public final class MagicCircleDecl {

    /** 元素槽在声明模式里的含义：作用域 / 目标域（规范 12.2）。 */
    public enum Scope {
        TERMINAL("terminal"), GLOBAL("global"), HOSTILE("hostile"), FRIENDLY("friendly"),
        BLOCK("block"), MOVE("move"), CONTROL("control"), SIGNAL("signal");

        private final String id;

        Scope(String id) { this.id = id; }

        public String id() { return id; }

        public String translationKey() { return "gui.dimensional_mana.magiccircle.decl.scope." + id; }
    }

    /** 功率档在声明模式里的精度形态（规范 12.4）。 */
    public enum Precision {
        COARSE("coarse"),      // 下级 / 中级：只能取本档基准值
        MEDIUM("medium"),      // 高级 / 极品：本档 ~ 下一档（不含）
        FINE("fine"),          // 特级：上一档 ~ 下一档（不含）
        FINE_JUMP("fineJump"); // 超导：细 + 可越 1 档，消耗按越档后的档算并 +1 复杂度

        private final String id;

        Precision(String id) { this.id = id; }

        public String id() { return id; }

        public String translationKey() { return "gui.dimensional_mana.magiccircle.decl.precision." + id; }
    }

    /**
     * 一个边界组。{@link #id} 是 NBT / 法阵码里的短码；{@link #semantics} 是哪几种语义粉
     * 能声明它（规范 12.1，跨大类必须拆成多个条件节点）；{@link #tiers} 给主参数提供
     * 0~15 档基准值，没有表的组主参数就是枚举下标或固定小整数（协议指定、权限这类）。
     */
    public enum Group {
        SUMMON("su", "summon", new String[]{"minecraft:sugar"}, Scope.FRIENDLY,
                table(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16), 3,
                new String[]{"gui.dimensional_mana.magiccircle.decl.ai.follow",
                        "gui.dimensional_mana.magiccircle.decl.ai.stay",
                        "gui.dimensional_mana.magiccircle.decl.ai.guard",
                        "gui.dimensional_mana.magiccircle.decl.ai.aggressive",
                        "gui.dimensional_mana.magiccircle.decl.ai.passive"},
                new String[]{"gui.dimensional_mana.magiccircle.decl.owner.party",
                        "gui.dimensional_mana.magiccircle.decl.owner.public",
                        "gui.dimensional_mana.magiccircle.decl.owner.self"},
                new String[]{"gui.dimensional_mana.magiccircle.decl.lost.despawn",
                        "gui.dimensional_mana.magiccircle.decl.lost.stop_ai",
                        "gui.dimensional_mana.magiccircle.decl.lost.keep"},
                new String[]{"gui.dimensional_mana.magiccircle.decl.flag.boss"}),
        HEAL("he", "heal", new String[]{"minecraft:sugar"}, Scope.FRIENDLY,
                table(10, 15, 20, 25, 30, 40, 50, 60, 75, 90, 110, 130, 160, 200, 250, 300), 6,
                new String[]{"gui.dimensional_mana.magiccircle.decl.flag.healHostile",
                        "gui.dimensional_mana.magiccircle.decl.flag.overflowShield"}),
        PERMISSION("pe", "permission",
                new String[]{"minecraft:sugar", "dimensional_mana:amethyst_dust"}, Scope.GLOBAL,
                null, -1,
                new String[]{"gui.dimensional_mana.magiccircle.decl.perm.owner",
                        "gui.dimensional_mana.magiccircle.decl.perm.party",
                        "gui.dimensional_mana.magiccircle.decl.perm.public"},
                new String[0]),
        SIGNAL("si", "signal", new String[]{"minecraft:redstone"}, Scope.SIGNAL,
                table(4, 6, 8, 10, 12, 14, 16, 20, 24, 28, 32, 36, 40, 48, 56, 64), 6,
                new String[]{"gui.dimensional_mana.magiccircle.decl.freq.5",
                        "gui.dimensional_mana.magiccircle.decl.freq.10",
                        "gui.dimensional_mana.magiccircle.decl.freq.20"},
                new String[]{"gui.dimensional_mana.magiccircle.decl.flag.crossDim"}),
        PROTOCOL("pr", "protocol", new String[]{"minecraft:redstone"}, Scope.TERMINAL,
                null, -1),
        PRIORITY("pv", "priority", new String[]{"minecraft:redstone"}, Scope.TERMINAL,
                null, -1,
                new String[]{"gui.dimensional_mana.magiccircle.decl.prio.highest",
                        "gui.dimensional_mana.magiccircle.decl.prio.forbid",
                        "gui.dimensional_mana.magiccircle.decl.prio.single"},
                new String[0]),
        COMPAT("cf", "compat", new String[]{"minecraft:redstone"}, Scope.TERMINAL,
                null, -1,
                new String[]{"gui.dimensional_mana.magiccircle.decl.flag.forceCompat"}),
        DAMAGE("da", "damage", new String[]{"minecraft:gunpowder"}, Scope.HOSTILE,
                table(0, 2, 4, 6, 8, 10, 12, 15, 18, 21, 24, 28, 32, 36, 42, 50), 0,
                new String[]{"gui.dimensional_mana.magiccircle.decl.flag.pvp",
                        "gui.dimensional_mana.magiccircle.decl.flag.friendFire",
                        "gui.dimensional_mana.magiccircle.decl.flag.blockBreak"}),
        INTERACT("in", "interact",
                new String[]{"minecraft:gunpowder", "dimensional_mana:lapis_dust"}, Scope.BLOCK,
                table(32, 48, 64, 80, 96, 112, 128, 160, 192, 224, 256, 288, 320, 384, 448, 512), 6,
                new String[]{"gui.dimensional_mana.magiccircle.decl.flag.requireChunk",
                        "gui.dimensional_mana.magiccircle.decl.flag.silk",
                        "gui.dimensional_mana.magiccircle.decl.flag.toInventory"}),
        PURIFY("pu", "purify", new String[]{"minecraft:blaze_powder"}, Scope.FRIENDLY,
                table(1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8), 4,
                new String[]{"gui.dimensional_mana.magiccircle.decl.cd.2",
                        "gui.dimensional_mana.magiccircle.decl.cd.4",
                        "gui.dimensional_mana.magiccircle.decl.cd.8"},
                new String[]{"gui.dimensional_mana.magiccircle.decl.flag.bossNegative"}),
        DISORDER("di", "disorder", new String[]{"minecraft:blaze_powder"}, Scope.GLOBAL,
                table(4, 4, 5, 5, 6, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16), 4,
                new String[]{"gui.dimensional_mana.magiccircle.decl.backlash.none",
                        "gui.dimensional_mana.magiccircle.decl.backlash.random",
                        "gui.dimensional_mana.magiccircle.decl.backlash.minor"},
                new String[]{"gui.dimensional_mana.magiccircle.decl.flag.allowBacklash"}),
        STATUS("st", "status", new String[]{"minecraft:blaze_powder"}, Scope.FRIENDLY,
                table(1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8), 4,
                new String[]{"gui.dimensional_mana.magiccircle.decl.flag.keepAttachments"}),
        INFO("nf", "info",
                new String[]{"minecraft:glowstone_dust", "dimensional_mana:lapis_dust"}, Scope.GLOBAL,
                table(4, 6, 8, 10, 12, 14, 16, 20, 24, 28, 32, 36, 40, 48, 56, 64), 6,
                new String[]{"gui.dimensional_mana.magiccircle.decl.selfScan.self",
                        "gui.dimensional_mana.magiccircle.decl.selfScan.party",
                        "gui.dimensional_mana.magiccircle.decl.selfScan.all"},
                new String[]{"gui.dimensional_mana.magiccircle.decl.flag.wallMark",
                        "gui.dimensional_mana.magiccircle.decl.flag.pvpReveal"}),
        VISIBILITY("vs", "visibility", new String[]{"minecraft:glowstone_dust"}, Scope.GLOBAL,
                null, -1,
                new String[]{"gui.dimensional_mana.magiccircle.decl.flag.showOutline"}),
        COUNT("nu", "count", new String[]{"minecraft:glowstone_dust"}, Scope.GLOBAL,
                table(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16), 4),
        TELEPORT("tp", "teleport", new String[]{"dimensional_mana:amethyst_dust"}, Scope.MOVE,
                table(4, 6, 8, 10, 12, 14, 16, 20, 24, 28, 32, 36, 40, 48, 56, 64), 6,
                new String[]{"gui.dimensional_mana.magiccircle.decl.flag.crossDim",
                        "gui.dimensional_mana.magiccircle.decl.flag.pvpTeleport",
                        "gui.dimensional_mana.magiccircle.decl.flag.hostileOnly"}),
        ENERGY("en", "energy", new String[]{"dimensional_mana:amethyst_dust"}, Scope.GLOBAL,
                table(10, 20, 30, 40, 50, 60, 75, 90, 110, 130, 150, 180, 220, 260, 320, 500), 4,
                new String[]{"gui.dimensional_mana.magiccircle.decl.chargeTarget.player",
                        "gui.dimensional_mana.magiccircle.decl.chargeTarget.circle",
                        "gui.dimensional_mana.magiccircle.decl.chargeTarget.table"},
                new String[0]),
        LINK("lk", "link", new String[]{"dimensional_mana:amethyst_dust"}, Scope.GLOBAL,
                null, -1,
                new String[]{"gui.dimensional_mana.magiccircle.decl.flag.relay"}),
        PRIVACY("pc", "privacy", new String[]{"dimensional_mana:lapis_dust"}, Scope.GLOBAL,
                null, -1,
                new String[]{"gui.dimensional_mana.magiccircle.decl.flag.readMana",
                        "gui.dimensional_mana.magiccircle.decl.flag.readFight",
                        "gui.dimensional_mana.magiccircle.decl.flag.readBag"});

        public final String id;
        public final String name;
        public final String[] semantics;
        public final Scope defaultScope;
        /** 主参数的 0~15 档基准值；null = 主参数不走染料档。 */
        public final int[] tiers;
        /** 染料槽空着时的推荐档（规范 12.3 六）；-1 = 无档位概念。 */
        public final int defaultTier;
        public final String[] flags;
        /** 第二参数上限（寿命秒 / 单次方块数），0 = 没有第二参数。 */
        public final int secondaryMax;

        private final String[][] enumOptions;

        Group(String id, String name, String[] semantics, Scope defaultScope, int[] tiers, int defaultTier,
              String[]... enumsAndFlags) {
            this.id = id;
            this.name = name;
            this.semantics = semantics;
            this.defaultScope = defaultScope;
            this.tiers = tiers;
            this.defaultTier = defaultTier;
            // 变参的最后一段恒为 flags，前面每段是一个枚举的选项列表。
            List<String[]> enums = new ArrayList<>();
            for (int i = 0; i < enumsAndFlags.length - 1; i++) enums.add(enumsAndFlags[i]);
            this.enumOptions = enums.toArray(new String[0][]);
            this.flags = enumsAndFlags.length == 0 ? new String[0] : enumsAndFlags[enumsAndFlags.length - 1];
            this.secondaryMax = switch (this) {
                case SUMMON -> 600;   // 寿命秒
                case INTERACT -> 64;  // 单次方块数
                default -> 0;
            };
        }

        public String translationKey() { return "group.dimensional_mana.declare." + name; }

        /** 主参数标签的语言键。 */
        public String primaryKey() {
            return switch (this) {
                case SUMMON -> "gui.dimensional_mana.magiccircle.decl.primary.maxCount";
                case HEAL -> "gui.dimensional_mana.magiccircle.decl.primary.maxHeal";
                case PERMISSION -> "gui.dimensional_mana.magiccircle.decl.primary.perm";
                case SIGNAL -> "gui.dimensional_mana.magiccircle.decl.primary.wireless";
                case PROTOCOL -> "gui.dimensional_mana.magiccircle.decl.primary.protocol";
                case PRIORITY -> "gui.dimensional_mana.magiccircle.decl.primary.prio";
                case COMPAT -> "gui.dimensional_mana.magiccircle.decl.primary.forced";
                case DAMAGE -> "gui.dimensional_mana.magiccircle.decl.primary.realPct";
                case INTERACT -> "gui.dimensional_mana.magiccircle.decl.primary.blocksMinute";
                case PURIFY -> "gui.dimensional_mana.magiccircle.decl.primary.clears";
                case DISORDER -> "gui.dimensional_mana.magiccircle.decl.primary.threshold";
                case STATUS -> "gui.dimensional_mana.magiccircle.decl.primary.statusPurify";
                case INFO -> "gui.dimensional_mana.magiccircle.decl.primary.scanRadius";
                case VISIBILITY -> "gui.dimensional_mana.magiccircle.decl.primary.visible";
                case COUNT -> "gui.dimensional_mana.magiccircle.decl.primary.count";
                case TELEPORT -> "gui.dimensional_mana.magiccircle.decl.primary.maxDistance";
                case ENERGY -> "gui.dimensional_mana.magiccircle.decl.primary.charge";
                case LINK -> "gui.dimensional_mana.magiccircle.decl.primary.linkHops";
                case PRIVACY -> "gui.dimensional_mana.magiccircle.decl.primary.privacy";
            };
        }

        /** 主参数单位后缀（试运行预览与报错文案直接拼数字用；平台文案本来就是中文短词）。 */
        public String primaryUnit() {
            return switch (this) {
                case SUMMON, COUNT -> " 只";
                case HEAL -> " 点";
                case DAMAGE, DISORDER -> "%";
                case INTERACT -> " 块/分";
                case PURIFY, STATUS -> " 个";
                case INFO, SIGNAL, TELEPORT -> " 格";
                case ENERGY -> " 魔素";
                default -> "";
            };
        }

        public int enumCount() { return enumOptions.length; }

        public String[] enumOptions(int index) {
            return index >= 0 && index < enumOptions.length ? enumOptions[index] : new String[0];
        }

        public int enumSize(int index) { return Math.max(1, enumOptions(index).length); }

        /** 枚举值下标的显示名语言键。 */
        public String enumKey(int index, int value) {
            String[] options = enumOptions(index);
            if (options.length == 0) return "";
            return options[Math.max(0, Math.min(options.length - 1, value))];
        }

        /** 没有档位表的组，主参数的固定范围（协议指定 = 前置序号、权限 = 三档……）。 */
        public int[] fallbackRange() {
            return switch (this) {
                case PROTOCOL -> new int[]{0, 31};
                case PERMISSION, PRIORITY, LINK -> new int[]{0, 2};
                case VISIBILITY, COMPAT, PRIVACY -> new int[]{0, 1};
                default -> new int[]{0, 0};
            };
        }

        public boolean hasTier() { return tiers != null; }

        /** 该语义粉能不能声明这个边界组（规范 12.1）。 */
        public boolean allows(String semanticItemId) {
            for (String s : semantics) if (s.equals(semanticItemId)) return true;
            return false;
        }

        public static Group byShortId(String id) {
            for (Group group : values()) if (group.id.equals(id)) return group;
            return null;
        }

        /** 某个语义粉能声明的全部边界组（面板「添加边界组」的候选列表）。 */
        public static List<Group> familyOf(String semanticItemId) {
            List<Group> list = new ArrayList<>();
            if (semanticItemId == null || semanticItemId.isEmpty()) return list;
            for (Group group : values()) if (group.allows(semanticItemId)) list.add(group);
            return list;
        }

        private static int[] table(int... values) { return values; }
    }

    // ---------------------------------------------------------------- 染料 → 档位

    private static final String[] DYE_BY_TIER = {"white_dye", "light_gray_dye", "gray_dye", "black_dye",
            "brown_dye", "red_dye", "orange_dye", "yellow_dye", "lime_dye", "green_dye", "cyan_dye",
            "light_blue_dye", "blue_dye", "purple_dye", "pink_dye", "magenta_dye"};
    private static final Map<String, Integer> DYE_TIER = new LinkedHashMap<>();

    static {
        // 规范 12.3 二：白色 0 档最低，品红 15 档最高，粉红 14 次高。
        for (int i = 0; i < DYE_BY_TIER.length; i++) DYE_TIER.put("minecraft:" + DYE_BY_TIER[i], i);
    }

    /** 染料物品 id → 档位 0~15；不是染料（含空槽）返回 -1。 */
    public static int dyeTier(String dyeItemId) {
        Integer tier = dyeItemId == null ? null : DYE_TIER.get(dyeItemId);
        return tier == null ? -1 : tier;
    }

    /** 档位的显示短名（白 0 …品红 15）。 */
    public static String tierName(int tier) {
        if (tier < 0) return "-";
        return MagicCirclePowders.shortName("minecraft:" + DYE_BY_TIER[Math.max(0, Math.min(15, tier))]);
    }

    /** 元素槽 → 作用域（规范 12.2）；元素槽不参与紊乱，只做作用域标签。 */
    public static Scope scopeOf(String elementItemId) {
        if (MagicCirclePowders.isArcane(elementItemId)) return Scope.GLOBAL;
        ElementType element = MagicCirclePowders.elementOf(elementItemId);
        if (element == null) return Scope.TERMINAL;
        return switch (element) {
            case FIRE, LIGHTNING, POISON -> Scope.HOSTILE;
            case WATER, NATURE, LIGHT -> Scope.FRIENDLY;
            case METAL, EARTH -> Scope.BLOCK;
            case WIND -> Scope.MOVE;
            case ICE -> Scope.CONTROL;
            case SOUND -> Scope.SIGNAL;
        };
    }

    // ---------------------------------------------------------------- 功率 → 组数 / 精度

    /** 功率粉能声明的边界组数：下 1 / 中 2 / 高 3 / 极 4 / 特 5 / 超导 6（规范 12.4）。 */
    public static int groupCapacity(String powerItemId) {
        if (powerItemId == null || powerItemId.isEmpty()) return 1;
        return switch (powerItemId) {
            case "dimensional_mana:lesser_mana_crystal_dust" -> 1;
            case "dimensional_mana:medium_mana_crystal_dust" -> 2;
            case "dimensional_mana:advanced_mana_crystal_dust" -> 3;
            case "dimensional_mana:supreme_mana_crystal_dust" -> 4;
            case "dimensional_mana:special_mana_crystal_dust" -> 5;
            case "dimensional_mana:superconducting_crystal_dust" -> 6;
            default -> 1;
        };
    }

    public static Precision precisionOf(String powerItemId) {
        if (powerItemId == null || powerItemId.isEmpty()) return Precision.COARSE;
        return switch (powerItemId) {
            case "dimensional_mana:advanced_mana_crystal_dust", "dimensional_mana:supreme_mana_crystal_dust" ->
                    Precision.MEDIUM;
            case "dimensional_mana:special_mana_crystal_dust" -> Precision.FINE;
            case "dimensional_mana:superconducting_crystal_dust" -> Precision.FINE_JUMP;
            default -> Precision.COARSE;
        };
    }

    // ---------------------------------------------------------------- 一条声明

    /**
     * 一个边界组的声明。主参数 / 第二参数存的是「玩家定下来的最终值」；染料档只决定基准值，
     * 实际填得比染料档基准高（超导越档）时 {@link Entry#effectiveTier} 自动升档并按它计费
     * （规范 12.3 四第 4 条）。
     */
    public record Entry(Group group, int primary, int secondary, int[] enums, int flags) {

        public int enumValue(int index) {
            return enums != null && index >= 0 && index < enums.length ? enums[index] : 0;
        }

        public boolean flag(int bit) {
            return (flags & (1 << bit)) != 0;
        }

        /** 指定染料档下本组主参数的基准值；空染料用推荐档。 */
        public int baseValue(int dyeTier) {
            int tier = dyeTier < 0 ? Math.max(0, group.defaultTier) : Math.min(15, dyeTier);
            if (!group.hasTier()) return group.fallbackRange()[0];
            return group.tiers[tier];
        }

        /** 精度允许的主参数范围 [min, max]（规范 12.3 四）。 */
        public int[] allowedRange(int dyeTier, Precision precision) {
            if (!group.hasTier()) {
                return group.fallbackRange().clone();
            }
            int tier = Math.min(15, Math.max(0, dyeTier < 0 ? Math.max(0, group.defaultTier) : dyeTier));
            int[] t = group.tiers;
            int base = t[tier];
            int next = tier < 15 ? t[tier + 1] : base;
            int prev = tier > 0 ? t[tier - 1] : base;
            return switch (precision) {
                case COARSE -> new int[]{base, base};
                case MEDIUM -> new int[]{base, Math.max(base, next - 1)};
                case FINE -> new int[]{prev, Math.max(prev, next - 1)};
                case FINE_JUMP -> new int[]{prev, next};
            };
        }

        /**
         * 计费档位：从染料档起算，主参数填到多少就至少是「基准值 ≥ 填值」的那一档；
         * 超导越 1 档也自然落在这里。消耗与复杂度按越档后的档算。
         */
        public int effectiveTier(int dyeTier) {
            if (!group.hasTier()) return Math.max(0, dyeTier);
            int tier = dyeTier < 0 ? Math.max(0, group.defaultTier) : Math.min(15, Math.max(0, dyeTier));
            int[] t = group.tiers;
            if (primary <= t[tier]) return tier;
            for (int i = tier + 1; i < t.length; i++) {
                if (t[i] >= primary) return i;
            }
            return 15;
        }

        /** 越档附加的复杂度（规范：超导越档每条 +1）。 */
        public int extraComplexity(int dyeTier) {
            int base = dyeTier < 0 ? Math.max(0, group.defaultTier) : Math.max(0, dyeTier);
            return Math.max(0, effectiveTier(dyeTier) - base);
        }

        /** 夹到本节点当前染料档 + 精度允许的范围内（换粉、导入之后都要重夹）。 */
        public Entry clampTo(int dyeTier, Precision precision) {
            int[] range = allowedRange(dyeTier, precision);
            int p = Math.max(range[0], Math.min(range[1], primary));
            int s = secondaryMax() == 0 ? 0 : Math.max(secondaryMin(), Math.min(secondaryMax(), secondary));
            int[] fixedEnums = new int[group.enumCount()];
            for (int i = 0; i < fixedEnums.length; i++) {
                fixedEnums[i] = Math.max(0, Math.min(group.enumSize(i) - 1, enumValue(i)));
            }
            int flagMask = group.flags.length == 0 ? 0 : (1 << group.flags.length) - 1;
            return new Entry(group, p, s, fixedEnums, flags & flagMask);
        }

        public int secondaryMin() {
            return switch (group) {
                case SUMMON -> 60;
                case INTERACT -> 1;
                default -> 0;
            };
        }

        public int secondaryMax() {
            return group.secondaryMax;
        }

        /** 第二参数的步进（寿命 20 秒、单次方块 4 块）。 */
        public int secondaryStep() {
            return switch (group) {
                case SUMMON -> 20;
                case INTERACT -> 4;
                default -> 1;
            };
        }

        /** 主参数步进（规范 12.3 四第 5 条：距离按 2 格、魔素按 5 点、其余按 1）。 */
        public int primaryStep() {
            return switch (group) {
                case SIGNAL, INFO, TELEPORT -> 2;
                case ENERGY -> 5;
                case HEAL -> 5;
                case INTERACT -> 16;
                default -> 1;
            };
        }
    }

    // ---------------------------------------------------------------- 编码 / 解析

    /**
     * 一串声明的存储格式：{@code c1|组短码;主;次;枚举0;…;标志|…}。
     * 全 ASCII、只依赖短码与整数，法阵码压缩与跨版本都稳（规范 19）。
     */
    public static String encode(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) return "";
        StringBuilder builder = new StringBuilder("c1");
        for (Entry entry : entries) {
            builder.append('|').append(entry.group().id)
                    .append(';').append(entry.primary())
                    .append(';').append(entry.secondary());
            for (int i = 0; i < entry.group().enumCount(); i++) builder.append(';').append(entry.enumValue(i));
            builder.append(';').append(entry.flags());
        }
        String out = builder.toString();
        return out.length() > 160 ? "" : out;
    }

    /** 解析；认不出的组、越界的数字就地夹掉，坏串当「没有声明」。 */
    public static List<Entry> parse(String raw) {
        List<Entry> entries = new ArrayList<>();
        if (raw == null || raw.isEmpty() || !raw.startsWith("c1")) return entries;
        String[] parts = raw.split("\\|");
        for (int i = 1; i < parts.length && entries.size() < 8; i++) {
            String[] fields = parts[i].split(";");
            // 「组;主;次;标志」是无枚举组的最短形态；有枚举的每组多一段。
            if (fields.length < 4) continue;
            Group group = Group.byShortId(fields[0]);
            if (group == null) continue;
            int primary = readInt(fields[1], 0);
            int secondary = readInt(fields[2], 0);
            int[] enums = new int[group.enumCount()];
            for (int e = 0; e < enums.length; e++) {
                String cell = fields.length > 3 + e ? fields[3 + e] : "0";
                enums[e] = Math.max(0, Math.min(group.enumSize(e) - 1, readInt(cell, 0)));
            }
            int flags = fields.length > 3 + enums.length ? readInt(fields[3 + enums.length], 0) : 0;
            entries.add(new Entry(group, primary, secondary, enums, flags & 7));
        }
        return dedup(entries);
    }

    private static List<Entry> dedup(List<Entry> entries) {
        Map<Group, Entry> kept = new LinkedHashMap<>();
        for (Entry entry : entries) kept.putIfAbsent(entry.group(), entry);
        return new ArrayList<>(kept.values());
    }

    private static int readInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (RuntimeException error) {
            return fallback;
        }
    }

    /** 按染料档 + 精度把所有条目重夹一遍（换粉、导入后调用），返回可直接存回节点的新串。 */
    public static String reconcile(String raw, String dye, String power) {
        List<Entry> entries = parse(raw);
        if (entries.isEmpty()) return "";
        int tier = dyeTier(dye);
        Precision precision = precisionOf(power);
        List<Entry> clamped = new ArrayList<>(entries.size());
        for (Entry entry : entries) clamped.add(entry.clampTo(tier, precision));
        int capacity = groupCapacity(power);
        while (clamped.size() > capacity) clamped.remove(clamped.size() - 1);
        return encode(clamped);
    }

    // ---------------------------------------------------------------- 消耗与复杂度

    /** 档位消耗系数 = 1 + 0.03 × 档位，四舍五入到 0.01（规范 12.3 五，只乘条件节点自身）。 */
    public static double tierCostFactor(int tier) {
        return Math.round((1.0 + 0.03 * Math.max(0, Math.min(15, tier))) * 100.0) / 100.0;
    }

    /**
     * 条件节点（声明模式）基础值 = (基础 4 + 每组加价 2 × (组数 − 1)) × 最贵一条的档位系数。
     * 「档位消耗系数只乘条件节点自身，不乘终端输出消耗」（规范 12.3 五）。
     */
    public static double conditionBase(String raw, int dyeTier) {
        List<Entry> entries = parse(raw);
        if (entries.isEmpty()) return MagicCircleConfig.CONDITION_BASE_COST.get();
        double base = MagicCircleConfig.CONDITION_BASE_COST.get()
                + (double) MagicCircleConfig.CONDITION_EXTRA_GROUP_COST.get() * (entries.size() - 1);
        double factor = 1.0;
        for (Entry entry : entries) {
            factor = Math.max(factor, tierCostFactor(entry.effectiveTier(dyeTier)));
        }
        return base * factor;
    }

    /** 声明模式的条件节点复杂度 = 边界组数（+ 超导越档每条再 +1）。 */
    public static int complexity(String raw, int dyeTier) {
        int total = 0;
        for (Entry entry : parse(raw)) total += 1 + entry.extraComplexity(dyeTier);
        return total;
    }

    // ---------------------------------------------------------------- 必需边界组表（规范 11）

    /** 词缀标签 → 必须声明的边界组。 */
    private static final Map<String, Group> TAG_REQUIREMENT = new LinkedHashMap<>();

    static {
        for (String tag : List.of("summon_friendly", "summon_hostile", "summon_special")) {
            TAG_REQUIREMENT.put(tag, Group.SUMMON);
        }
        for (String tag : List.of("mine", "silk_mine", "plant", "melt", "burn", "harvest")) {
            TAG_REQUIREMENT.put(tag, Group.INTERACT);
        }
        for (String tag : List.of("teleport_random", "teleport_directed", "link")) {
            TAG_REQUIREMENT.put(tag, Group.TELEPORT);
        }
        for (String tag : List.of("damage", "explosion", "spread", "chain", "pierce",
                "execute", "lifesteal", "ignite")) {
            TAG_REQUIREMENT.put(tag, Group.DAMAGE);
        }
        for (String tag : List.of("heal", "regen", "shield", "buff")) {
            TAG_REQUIREMENT.put(tag, Group.HEAL);
        }
        for (String tag : List.of("purify", "disinfect", "defrost", "smoke")) {
            TAG_REQUIREMENT.put(tag, Group.PURIFY);
        }
        for (String tag : List.of("light", "reveal", "mark", "hologram", "scan", "identify", "exp")) {
            TAG_REQUIREMENT.put(tag, Group.INFO);
        }
        for (String tag : List.of("signal", "wireless", "logic", "lock", "clock")) {
            TAG_REQUIREMENT.put(tag, Group.SIGNAL);
        }
        for (String tag : List.of("charge", "detect", "stabilize", "resonance")) {
            TAG_REQUIREMENT.put(tag, Group.ENERGY);
        }
    }

    /** 七个终端协议各自的最低必需组（哪怕没有任何词缀标签也要声明的东西，规范 11）。 */
    public static Group protocolRequirement(MagicCircleAffixes.Protocol protocol) {
        return switch (protocol) {
            case SIGNAL -> Group.SIGNAL;
            case LIFE -> Group.HEAL;
            case DAMAGE -> Group.DAMAGE;
            case PURIFY -> Group.PURIFY;
            case LIGHT, SCAN -> Group.INFO;
            case CHARGE -> Group.ENERGY;
            case NONE -> null;
        };
    }

    public static Group requiredGroupForTag(String tag) {
        return TAG_REQUIREMENT.get(tag);
    }

    /** 「总紊乱超阈值仍要运行」的线（规范 11 末行）。 */
    public static boolean disorderNeedsDeclaration(int disorder) {
        return disorder > MagicCircleConfig.DISORDER_DECLARE_THRESHOLD.get();
    }

    /** 声明串里的组集合（缺组检查用）。 */
    public static Set<Group> declaredGroups(String raw) {
        Set<Group> set = EnumSet.noneOf(Group.class);
        for (Entry entry : parse(raw)) set.add(entry.group());
        return set;
    }

    /** 找一条声明里某个组的参数；没有返回 null。 */
    public static Entry find(String raw, Group group) {
        for (Entry entry : parse(raw)) if (entry.group() == group) return entry;
        return null;
    }

    /** 面板「推荐值」按钮与运行兜底共用的默认一条（规范 12.3 六 + 13 各表默认值）。 */
    public static Entry recommended(Group group, int dyeTier, Precision precision) {
        Entry probe = new Entry(group, 0, 0, new int[group.enumCount()], defaultFlags(group));
        int tier = dyeTier < 0 ? Math.max(0, group.defaultTier) : dyeTier;
        int[] range = probe.allowedRange(tier, precision);
        int primary = group.hasTier() ? group.tiers[Math.max(0, Math.min(15, tier))] : group.fallbackRange()[0];
        primary = Math.max(range[0], Math.min(range[1], primary));
        int secondary = switch (group) {
            case SUMMON -> MagicCircleConfig.SUMMON_LIFETIME_SECONDS.get();
            case INTERACT -> MagicCircleConfig.MAX_BLOCKS_PER_TRIGGER.get();
            default -> 0;
        };
        secondary = Math.max(probe.secondaryMin(), Math.min(probe.secondaryMax(), secondary));
        int[] enums = new int[group.enumCount()];
        if (group == Group.SUMMON) enums[0] = 2; // AI：守卫锚点
        return new Entry(group, primary, secondary, enums, defaultFlags(group)).clampTo(tier, precision);
    }

    /** 各组的默认标志位（「默认安全值仍保留」，规范 11 末注）。 */
    public static int defaultFlags(Group group) {
        return switch (group) {
            // r211：伤害边界默认全开（PvP / 友伤 / 方块破坏）。上一版默认全关，
            // 新手拿推荐值一发打在猪 / 自己身上完全不掉血，以为整套伤害链路坏了。
            case DAMAGE -> 0b111;
            case INTERACT -> 0b001;   // 默认要求区块已加载
            case TELEPORT -> 0b100;   // 默认只传敌对生物
            case INFO -> 0b001;       // 默认允许隔墙标记（受距离限制）
            case COMPAT -> 0b001;     // 声明了「强制兼容」就是开了它
            default -> 0;
        };
    }

    private MagicCircleDecl() {}
}
