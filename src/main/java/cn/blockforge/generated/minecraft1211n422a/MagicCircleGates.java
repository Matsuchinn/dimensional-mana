package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 条件节点「门控模式」的判定引擎（《条件节点设计规范》v2.4 + 《触发与条件节点流转规范》v3.0）。
 *
 * <p><b>底层防坑三条</b>（三份文档第六节）：</p>
 * <ol>
 *   <li>数据层<b>绝不存「是否反转」布尔</b>——只存 {@link CompareOperator} 枚举；
 *       UI 的 ⇌ 按钮直接把枚举翻转后落盘，底层永远只有一套比较逻辑；</li>
 *   <li>串联条件链<b>必须短路</b>：任意一个 false 立刻收工，绝不再查后面条件的数据
 *       （{@link #evaluateBranch}）——1 tick 处理全服法阵时省下的都是真 CPU；</li>
 *   <li>区域扫描只走原版 {@code level.getEntitiesOfClass(LivingEntity.class, AABB, predicate)}，
 *       谓词里排除旁观者（{@code NO_SPECTATORS} 口径）；流体 / 着火一律用
 *       {@code isInWater / isInLava / isInPowderSnow / isOnFire} 属性方法；
 *       原版状态与模组附着统一映射到 {@link #matchesFilter} 一族（文档说的 StatusManager 口径）。</li>
 * </ol>
 *
 * <p><b>gate 串存储</b>（{@code Node.gate()}）：第 1 位模式（{@code G}=门控 / {@code D}=声明），
 * 第 2 位比较符（{@code L}=≤ / {@code R}=≥）。空串 = r208 旧存档：模式按「放了原版语义粉 = 声明」
 * 推断，比较符落到语义域默认（表二）。91 种联动（7 语义 × 13 元素列）在
 * {@link #matchesFilter}（元素维）与七个 {@code evaluateXxx}（语义维）两处落地。</p>
 */
public final class MagicCircleGates {

    // ---------------------------------------------------------------- 比较符枚举

    /** 比较符。数据层只存这个枚举，绝不存布尔反转（文档六 2 的硬要求）。 */
    public enum CompareOperator {
        LESS_THAN("L", "≤"),
        GREATER_THAN("R", "≥");

        private final String code;
        private final String symbol;

        CompareOperator(String code, String symbol) {
            this.code = code;
            this.symbol = symbol;
        }

        public String code() { return code; }
        public String symbol() { return symbol; }

        /** ⇌ 按钮：直接翻转枚举。 */
        public CompareOperator toggled() {
            return this == LESS_THAN ? GREATER_THAN : LESS_THAN;
        }

        public static CompareOperator fromCode(String code, CompareOperator fallback) {
            if ("L".equals(code)) return LESS_THAN;
            if ("R".equals(code)) return GREATER_THAN;
            return fallback;
        }

        public boolean test(double measured, double threshold) {
            return this == LESS_THAN ? measured <= threshold : measured >= threshold;
        }
    }

    // ---------------------------------------------------------------- 语义域

    /** 七种原版语义粉对应的判定主域（表二）。 */
    public enum Domain {
        LIFE("minecraft:sugar", CompareOperator.LESS_THAN, "生命"),
        RESOURCE("dimensional_mana:amethyst_dust", CompareOperator.LESS_THAN, "资源"),
        STATUS("dimensional_mana:lapis_dust", CompareOperator.GREATER_THAN, "状态"),
        ENVIRONMENT("minecraft:glowstone_dust", CompareOperator.LESS_THAN, "环境"),
        ACTION("minecraft:redstone", CompareOperator.LESS_THAN, "动作/空间"),
        COUNT("minecraft:gunpowder", CompareOperator.GREATER_THAN, "数量"),
        ATTACH("minecraft:blaze_powder", CompareOperator.GREATER_THAN, "附着");

        private final String semantic;
        private final CompareOperator defaultOperator;
        private final String label;

        Domain(String semantic, CompareOperator defaultOperator, String label) {
            this.semantic = semantic;
            this.defaultOperator = defaultOperator;
            this.label = label;
        }

        public String semantic() { return semantic; }
        public CompareOperator defaultOperator() { return defaultOperator; }
        public String label() { return label; }
    }

    /** 语义粉 → 判定主域；不是七种原版粉返回 null。 */
    public static Domain domainOf(String semanticItemId) {
        if (semanticItemId == null || semanticItemId.isEmpty()) return null;
        for (Domain domain : Domain.values()) {
            if (domain.semantic().equals(semanticItemId)) return domain;
        }
        return null;
    }

    /** 检测半径（格）：下 3 / 中 5 / 高 8 / 极 12 / 特 16 / 超导 32；空功率槽按下级。 */
    public static int scanRadius(String powerItemId) {
        if (powerItemId == null) return 3;
        return switch (powerItemId) {
            case "dimensional_mana:medium_mana_crystal_dust" -> 5;
            case "dimensional_mana:advanced_mana_crystal_dust" -> 8;
            case "dimensional_mana:supreme_mana_crystal_dust" -> 12;
            case "dimensional_mana:special_mana_crystal_dust" -> 16;
            case "dimensional_mana:superconducting_crystal_dust" -> 32;
            default -> 3;
        };
    }

    /** 染料档位 → 阈值（白 0 档 → 品红 15 档；不放染料按白档 0 算）。 */
    public static double threshold(Domain domain, int dyeTier) {
        int tier = Math.max(0, Math.min(15, dyeTier));
        return switch (domain) {
            case LIFE, RESOURCE -> (10.0 + 6.0 * tier) / 100.0;   // 10% → 100%
            case STATUS -> 1.0 + tier * 29.0 / 15.0;              // 1 秒 → 30 秒
            case ENVIRONMENT -> tier;                             // 0 → 15 刻度
            case ACTION -> 2.0 + 2.0 * tier;                      // 2 格 → 32 格
            case COUNT -> 1.0 + tier;                             // 1 个 → 16 个
            case ATTACH -> 1.0 + Math.round(tier * 3.0 / 15.0);   // 1 层 → 4 层
        };
    }

    /** 阈值的大白话。 */
    public static String thresholdText(Domain domain, double value) {
        return switch (domain) {
            case LIFE, RESOURCE -> Math.round(value * 100.0) + "%";
            case STATUS -> String.format(Locale.ROOT, "%.1f秒", value);
            case ENVIRONMENT -> String.valueOf((int) value);
            case ACTION -> (int) value + "格";
            case COUNT -> (int) value + "个";
            case ATTACH -> (int) value + "层";
        };
    }

    // ---------------------------------------------------------------- 模式与比较符的存取

    public static final String MODE_GATE = "G";
    public static final String MODE_DECLARE = "D";

    /** 条件节点的有效模式：显式 G/D 优先；空串按 r208 旧规则推断（有原版语义粉 = 声明）。 */
    public static boolean isGateMode(String semantic, String gate) {
        if (gate != null && !gate.isEmpty()) {
            return gate.startsWith(MODE_GATE);
        }
        return MagicCircleAffixes.Protocol.ofSemantic(semantic) == MagicCircleAffixes.Protocol.NONE;
    }

    /** 有效比较符：gate 串第 2 位显式值优先；缺省落语义域默认（未知域按 ≤，兼容文档五）。 */
    public static CompareOperator operatorOf(Domain domain, String gate) {
        CompareOperator fallback = domain == null ? CompareOperator.LESS_THAN : domain.defaultOperator();
        if (gate == null || gate.length() < 2) return fallback;
        return CompareOperator.fromCode(String.valueOf(gate.charAt(1)), fallback);
    }

    /** ⇌ 按钮的落盘值：门控模式 + 翻转后的枚举。 */
    public static String toggleOperator(String gate, String semantic) {
        CompareOperator current = operatorOf(domainOf(semantic), gate);
        return MODE_GATE + current.toggled().code();
    }

    /** 门控 ⇄ 声明切换按钮；比较符保留（切回门控时还是原来的 ≤/≥）。 */
    public static String toggleMode(String gate, String semantic) {
        if (isGateMode(semantic, gate)) {
            return MODE_DECLARE; // 声明模式不读比较符位
        }
        return MODE_GATE + operatorOf(domainOf(semantic), gate).code();
    }

    // ---------------------------------------------------------------- 动作保持帧

    /** 动作保持帧（刻）：跳跃这种 1 tick 动作锁定 0.5 秒，1 秒轮询也抓得住（文档四 1）。 */
    public static final int GRACE_TICKS = 10;

    private static final Map<String, Long> ACTION_STAMPS = new ConcurrentHashMap<>();

    /** 记一笔瞬时动作（事件总线调用）：action ∈ jump / attack / hurt。 */
    public static void recordAction(String action, int entityId, long gameTick) {
        ACTION_STAMPS.put(action + ':' + entityId, gameTick);
        if (ACTION_STAMPS.size() > 4096) {
            List<String> keys = new ArrayList<>(ACTION_STAMPS.keySet());
            for (int i = 0; i < keys.size() / 2; i++) ACTION_STAMPS.remove(keys.get(i));
        }
    }

    /** 最近 {@value #GRACE_TICKS} 刻内做过该动作？ */
    public static boolean recentlyDid(String action, int entityId, long gameTick) {
        Long at = ACTION_STAMPS.get(action + ':' + entityId);
        return at != null && gameTick - at <= GRACE_TICKS && gameTick >= at;
    }

    /** 停服 / 换世界时清缓存，防止旧刻号误判。 */
    public static void clearActions() {
        ACTION_STAMPS.clear();
    }

    /** 事件挂接：跳跃保持帧（动作列「风 / 音」用 0.5 秒宽限抓 1 刻动作）。 */
    public static void onJump(net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        LivingEntity living = event.getEntity();
        if (!living.level().isClientSide()) {
            recordAction("jump", living.getId(), living.level().getGameTime());
        }
    }

    /** 事件挂接：受击 / 攻击保持帧（动作列「火」看挥击起手，「毒」看挨打）。 */
    public static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        long now = target.level().getGameTime();
        recordAction("hurt", target.getId(), now);
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            recordAction("attack", attacker.getId(), now);
        }
    }

    // ---------------------------------------------------------------- 判定入口

    /** 一次门控判定的完整结果；T/F 悬浮文字与试运行预览直接读它。 */
    public record Verdict(boolean pass, int matched, String detail) {
        public String mark() { return pass ? "T" : "F"; }
    }

    /** 存档功能块版判定（运行时与轮询用）。 */
    public static Verdict evaluate(MagicCircleNetwork.Node node, ServerLevel level, Vec3 anchor, Player caster) {
        Domain domain = domainOf(node.semantic());
        if (domain == null) {
            return new Verdict(true, 0, "没装语义粉：门控直接放行（装一种原版粉才有判定）");
        }
        return evaluate(domain, node.element(), MagicCircleDecl.dyeTier(node.dye()),
                operatorOf(domain, node.gate()), node.power(), level, anchor, caster);
    }

    /** 同步包视图版判定（界面与试运行用）。 */
    public static Verdict evaluate(MagicCircleSyncPayload.NodeView node, ServerLevel level, Vec3 anchor,
                                   Player caster) {
        Domain domain = domainOf(node.semantic());
        if (domain == null) {
            return new Verdict(true, 0, "没装语义粉：门控直接放行（装一种原版粉才有判定）");
        }
        return evaluate(domain, node.element(), MagicCircleDecl.dyeTier(node.dye()),
                operatorOf(domain, node.gate()), node.power(), level, anchor, caster);
    }

    /** 七域分发。 */
    public static Verdict evaluate(Domain domain, String element, int dyeTier, CompareOperator op,
                                   String power, ServerLevel level, Vec3 anchor, Player caster) {
        double threshold = threshold(domain, Math.max(0, dyeTier));
        int radius = scanRadius(power);
        return switch (domain) {
            case LIFE -> evaluateRatio(level, anchor, radius, element, op, threshold, false);
            case RESOURCE -> evaluateRatio(level, anchor, radius, element, op, threshold, true);
            case STATUS -> evaluateStatus(level, anchor, radius, element, op, threshold);
            case ENVIRONMENT -> evaluateEnvironment(level, anchor, element, op, threshold);
            case ACTION -> evaluateAction(level, anchor, radius, element, op, threshold, caster);
            case COUNT -> evaluateCount(level, anchor, radius, element, op, threshold);
            case ATTACH -> evaluateAttach(level, anchor, radius, element, op, threshold);
        };
    }

    /**
     * 一条「分支」的串联门控链，<b>短路执行</b>（文档五 5 / 四 1）：
     * 任意一块返回 false 就立刻结束，后面的条件一个数据都不查。
     *
     * @param verdicts 出参：链上每块的判定结果（调试棒 T/F 展示用），全过 = 整条分支通
     * @return 整条分支是否放行
     */
    public static boolean evaluateBranch(List<MagicCircleNetwork.Node> chain, ServerLevel level, Vec3 anchor,
                                         Player caster, List<Verdict> verdicts) {
        for (MagicCircleNetwork.Node gate : chain) {
            Verdict verdict = evaluate(gate, level, anchor, caster);
            if (verdicts != null) verdicts.add(verdict);
            if (!verdict.pass()) {
                return false; // 短路：不再计算链上后面的条件
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- 各语义域

    /** 糖（生命）/ 紫水晶（资源）：找「占比满足比较」的目标；resource=true 走魔素/斗气。 */
    private static Verdict evaluateRatio(ServerLevel level, Vec3 anchor, int radius, String element,
                                         CompareOperator op, double threshold, boolean resource) {
        List<LivingEntity> nearby = scanLiving(level, anchor, radius, element);
        int matched = 0;
        for (LivingEntity living : nearby) {
            double ratio = resource ? resourceRatio(living) : healthRatio(living);
            if (Double.isNaN(ratio)) continue;
            if (op.test(ratio, threshold)) matched++;
        }
        String what = resource ? "魔素/斗气" : "生命值";
        return new Verdict(matched > 0, matched,
                "当前检测到 " + matched + " 个" + what + "满足条件的目标 " + op.symbol() + " "
                        + thresholdText(resource ? Domain.RESOURCE : Domain.LIFE, threshold));
    }

    private static double healthRatio(LivingEntity living) {
        float max = living.getMaxHealth();
        return max <= 0.0F ? Double.NaN : living.getHealth() / max;
    }

    /** 魔素（含玩家与友好生物）或斗气（敌对优先）占比；两边都没数据返回 NaN。 */
    private static double resourceRatio(LivingEntity living) {
        ManaData.Snapshot data = ManaData.get(living);
        boolean hostile = living instanceof Monster || living instanceof Goblin;
        if (hostile && data.maxBattleQi() > 0) {
            return (double) data.battleQi() / data.maxBattleQi();
        }
        if (data.maxMana() > 0) {
            return data.mana() / data.maxMana();
        }
        return data.maxBattleQi() > 0 ? (double) data.battleQi() / data.maxBattleQi() : Double.NaN;
    }

    /** 青金石（状态）：状态剩余秒数比较；身份列（空除外）按「在场 = 满足」处理。 */
    private static Verdict evaluateStatus(ServerLevel level, Vec3 anchor, int radius, String element,
                                          CompareOperator op, double threshold) {
        List<LivingEntity> nearby = scanLiving(level, anchor, radius, null);
        int matched = 0;
        for (LivingEntity living : nearby) {
            if (!identityPass(living, element)) continue; // 状态列在 identityPass 恒过，条件落在状态时长上
            double seconds = statusSeconds(living, element);
            if (seconds <= 0.0) continue;
            if (op.test(seconds, threshold)) matched++;
        }
        return new Verdict(matched > 0, matched, "当前检测到 " + matched + " 个带该状态的目标");
    }

    /** 元素列是不是「查某种状态」的列（火水冰雷毒光风土音）；身份 / 空列走身份口径。 */
    private static boolean statusColumn(String element) {
        String name = elementShort(element);
        return switch (name) {
            case "火", "水", "冰", "雷", "毒", "光", "风", "土", "音" -> true;
            default -> false;
        };
    }

    /** 目标身上「元素列对应的状态」剩余秒数（原版状态 + 模组附着统一映射，StatusManager 口径）。 */
    private static double statusSeconds(LivingEntity living, String element) {
        String name = elementShort(element);
        if (element == null || element.isEmpty()) {
            double best = 0.0; // [空]：任意负面状态
            for (MobEffectInstance effect : living.getActiveEffects()) {
                if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                    best = Math.max(best, effect.getDuration() / 20.0);
                }
            }
            return best;
        }
        if (!statusColumn(element)) {
            return matchesFilter(living, element) ? 31.0 : 0.0; // 身份列：在场即算满足
        }
        Holder<MobEffect> vanilla = switch (name) {
            case "火" -> null;                       // 燃烧看着火
            case "水" -> null;                       // 溺水看气泡，火水两列先靠附着兜底
            case "冰" -> MobEffects.MOVEMENT_SLOWDOWN;
            case "雷" -> null;                       // 感电 = 雷附着
            case "毒" -> MobEffects.POISON;
            case "光" -> MobEffects.GLOWING;
            case "风" -> MobEffects.LEVITATION;
            case "土" -> MobEffects.DIG_SLOWDOWN;
            case "音" -> MobEffects.CONFUSION;
            default -> null;
        };
        double best = vanilla == null ? 0.0 : secondsOf(living, vanilla);
        if (name.equals("火") && living.isOnFire()) best = Math.max(best, living.getRemainingFireTicks() / 20.0);
        if (name.equals("水") && living.isInWater()) best = Math.max(best, 31.0);
        ElementType attached = MagicCirclePowders.elementOf(element);
        if (attached != null) {
            MobEffectInstance instance = living.getEffect(GeneratedMod.elementEffect(attached));
            if (instance != null) best = Math.max(best, instance.getDuration() / 20.0);
        }
        return best;
    }

    private static double secondsOf(LivingEntity living, Holder<MobEffect> effect) {
        MobEffectInstance instance = living.getEffect(effect);
        return instance == null ? 0.0 : instance.getDuration() / 20.0;
    }

    /**
     * 萤石（环境）。数值列：[空] 光照、[金] / [木] Y 轴刻度，按表二比较；
     * 在场列（火水冰雷毒光风土音）：满足 = 放行，⇌ 切成反面（「没有 X 环境才放行」）。
     */
    private static Verdict evaluateEnvironment(ServerLevel level, Vec3 anchor, String element,
                                               CompareOperator op, double threshold) {
        BlockPos pos = BlockPos.containing(anchor);
        String name = elementShort(element);
        if (element == null || element.isEmpty()) {
            double light = level.getMaxLocalRawBrightness(pos);
            boolean pass = op.test(light, threshold);
            return new Verdict(pass, pass ? 1 : 0, "当前光照 " + (int) light + "（" + op.symbol() + " "
                    + (int) threshold + "）");
        }
        if (name.equals("金") || name.equals("木")) {
            double y = (pos.getY() + 64) * 15.0 / 224.0; // -64 → 0 档，约 160 → 15 档
            if (name.equals("木")) y = 15.0 - y;          // 地表列反向：越接近地表刻度越小
            boolean pass = op.test(y, threshold);
            return new Verdict(pass, pass ? 1 : 0, "当前 Y=" + pos.getY() + "（刻度 " + (int) y
                    + "，" + op.symbol() + " " + (int) threshold + "）");
        }
        boolean present = environmentPresence(level, pos, name);
        boolean pass = op == CompareOperator.GREATER_THAN ? present : !present;
        return new Verdict(pass, pass ? 1 : 0, (present ? "有" : "没有") + name + "环境");
    }

    private static boolean environmentPresence(ServerLevel level, BlockPos pos, String name) {
        return switch (name) {
            case "火" -> level.dimension() == Level.NETHER
                    || level.getFluidState(pos).is(Fluids.LAVA) || level.getFluidState(pos.below()).is(Fluids.LAVA);
            case "水" -> level.isRaining() || level.getFluidState(pos).is(Fluids.WATER)
                    || level.getFluidState(pos.below()).is(Fluids.WATER);
            case "冰" -> level.getBlockState(pos.below()).is(Blocks.SNOW)
                    || level.getBlockState(pos.below()).is(Blocks.SNOW_BLOCK)
                    || level.getBlockState(pos.below()).is(Blocks.ICE)
                    || biomePathContains(level, pos, "snowy", "frozen", "ice", "cold");
            case "雷" -> level.isThundering();
            case "毒" -> biomePathContains(level, pos, "swamp", "nether_wastes", "soul_sand_valley");
            case "光" -> level.isDay() && !level.isRaining();
            case "风" -> pos.getY() >= 120;
            case "土" -> level.getMaxLocalRawBrightness(pos) <= 0 && pos.getY() < 48;
            case "音" -> level.getBlockState(pos.below()).is(Blocks.NOTE_BLOCK)
                    || level.getBlockState(pos.below()).is(Blocks.SCULK_SENSOR)
                    || level.getBlockState(pos.below()).is(Blocks.SCULK_SHRIEKER);
            // 奥术列 = 区块魔素浓度：模组没有浓度场，按「锚点在法阵基座/祭坛附近」近似，⇌ 可反切。
            case "奥术" -> level.getBlockState(pos.below()).getBlock() instanceof MagicCircleBaseBlock
                    || level.getBlockState(pos).getBlock() instanceof MagicCircleBaseBlock;
            default -> false;
        };
    }

    /** 群系按 id 路径片段判定（与 SwampBiomeCondition 同口径，不依赖 BiomeKeys / 标签）。 */
    private static boolean biomePathContains(ServerLevel level, BlockPos pos, String... parts) {
        String path = level.getBiome(pos).unwrapKey().map(key -> key.location().getPath()).orElse("");
        for (String part : parts) {
            if (path.contains(part)) return true;
        }
        return false;
    }

    /** 红石（动作/空间）。[空] 红石信号、[奥术] 与施法者距离是数值；其余是动作布尔（阈值忽略）。 */
    private static Verdict evaluateAction(ServerLevel level, Vec3 anchor, int radius, String element,
                                          CompareOperator op, double threshold, Player caster) {
        String name = elementShort(element);
        if (element == null || element.isEmpty()) {
            int signal = level.getBestNeighborSignal(BlockPos.containing(anchor));
            boolean pass = op.test(signal, Math.min(threshold, 15.0));
            return new Verdict(pass, pass ? 1 : 0, "红石信号 " + signal + "（" + op.symbol() + " "
                    + (int) threshold + "）");
        }
        if (name.equals("奥术")) {
            int matched = 0;
            double nearest = Double.MAX_VALUE;
            for (Player player : level.getEntitiesOfClass(Player.class,
                    AABB.ofSize(anchor, threshold * 2 + 2, threshold * 2 + 2, threshold * 2 + 2))) {
                if (player.isSpectator()) continue;
                nearest = Math.min(nearest, anchor.distanceTo(player.position()));
                if (op.test(anchor.distanceTo(player.position()), threshold)) matched++;
            }
            if (caster != null && !caster.isSpectator()) {
                nearest = Math.min(nearest, anchor.distanceTo(caster.position()));
            }
            return new Verdict(matched > 0, matched, "最近玩家 " + (nearest == Double.MAX_VALUE ? "-"
                    : String.format(Locale.ROOT, "%.1f格", nearest)) + "（" + op.symbol() + " "
                    + (int) threshold + "格）");
        }
        List<LivingEntity> nearby = scanLiving(level, anchor, Math.max(radius, 6), null);
        int matched = 0;
        for (LivingEntity living : nearby) {
            if (!identityPass(living, element)) continue;
            if (actionMatches(living, name, anchor, level.getGameTime())) matched++;
        }
        return new Verdict(matched > 0, matched,
                "当前检测到 " + matched + " 个" + actionName(name) + "的目标");
    }

    private static String actionName(String name) {
        return switch (name) {
            case "金" -> "举盾格挡";
            case "木" -> "进食 / 喝药";
            case "火" -> "攻击前摇";
            case "水" -> "游泳 / 水中移动";
            case "冰" -> "潜行";
            case "雷" -> "疾跑";
            case "毒" -> "受伤硬直";
            case "光" -> "视线正对锚点";
            case "风" -> "下落 / 飞行 / 跳跃";
            case "土" -> "站在实心方块上";
            case "音" -> "正在跳跃";
            default -> "对应动作";
        };
    }

    /** 单目标是否在做该列动作；跳跃 / 攻击 / 受击这类瞬时动作走保持帧缓存。 */
    private static boolean actionMatches(LivingEntity living, String name, Vec3 anchor, long now) {
        return switch (name) {
            case "金" -> living.isUsingItem() && living.getUseItem().is(Items.SHIELD);
            case "木" -> living.isUsingItem() && (living.getUseItem().has(net.minecraft.core.component.DataComponents.FOOD)
                    || living.getUseItem().is(Items.POTION));
            case "火" -> recentlyDid("attack", living.getId(), now);
            case "水" -> living.isInWater();
            case "冰" -> living.isShiftKeyDown() || living.isCrouching();
            case "雷" -> living.isSprinting();
            case "毒" -> living.hurtMarked || recentlyDid("hurt", living.getId(), now);
            case "光" -> facingToward(living, anchor);
            case "风" -> living.isFallFlying() || living.hasEffect(MobEffects.LEVITATION)
                    || living.getDeltaMovement().y < -0.08 || recentlyDid("jump", living.getId(), now);
            case "土" -> living.onGround() && !living.isInWater();
            case "音" -> recentlyDid("jump", living.getId(), now);
            default -> false;
        };
    }

    private static boolean facingToward(LivingEntity living, Vec3 anchor) {
        Vec3 to = anchor.subtract(living.getEyePosition()).normalize();
        Vec3 look = living.getLookAngle();
        return look != null && look.dot(to) > 0.85;
    }

    /** 火药（数量）：范围内满足过滤的实体数比较；够数即 break。 */
    private static Verdict evaluateCount(ServerLevel level, Vec3 anchor, int radius, String element,
                                         CompareOperator op, double threshold) {
        List<LivingEntity> nearby = scanLiving(level, anchor, radius, element);
        int total = nearby.size();
        boolean pass = op.test(total, threshold);
        return new Verdict(pass, total, "当前检测到 " + total + " 个满足条件的生物（" + op.symbol() + " "
                + (int) threshold + "）");
    }

    /** 烈焰（附着）：目标身上元素附着层数比较；空列 = 身上几种不同元素附着。 */
    private static Verdict evaluateAttach(ServerLevel level, Vec3 anchor, int radius, String element,
                                          CompareOperator op, double threshold) {
        // 空列数「几种元素附着」，带列只数那一列：一律先扫全部活体，层数在 attachLayers 里区分。
        List<LivingEntity> nearby = scanLiving(level, anchor, radius, null);
        int matched = 0;
        for (LivingEntity living : nearby) {
            int layers = attachLayers(living, element);
            if (layers > 0 && op.test(layers, threshold)) matched++;
        }
        return new Verdict(matched > 0, matched, "当前检测到 " + matched + " 个附着达标的目标");
    }

    /** 附着层数 = 效果等级 + 1；空元素列数「几种不同元素附着」。 */
    private static int attachLayers(LivingEntity living, String elementItem) {
        if (elementItem != null && !elementItem.isEmpty() && !MagicCirclePowders.isArcane(elementItem)) {
            ElementType element = MagicCirclePowders.elementOf(elementItem);
            if (element == null) return 0;
            MobEffectInstance instance = living.getEffect(GeneratedMod.elementEffect(element));
            return instance == null ? 0 : instance.getAmplifier() + 1;
        }
        if (MagicCirclePowders.isArcane(elementItem)) {
            return living.getActiveEffects().stream()
                    .anyMatch(e -> e.getEffect().value() == NewSpellContent.ARCANE_SILENCE.get()) ? 1 : 0;
        }
        int kinds = 0;
        for (ElementType element : ElementType.values()) {
            if (living.getEffect(GeneratedMod.elementEffect(element)) != null) kinds++;
        }
        return kinds;
    }

    // ---------------------------------------------------------------- 扫描与过滤

    /**
     * 区域扫描（文档三 1）：只用原版 {@code getEntitiesOfClass}；谓词排除旁观者
     * （{@code EntitySelector.NO_SPECTATORS} 口径）与死亡。绝不手写物理距离循环。
     */
    public static List<LivingEntity> scanLiving(ServerLevel level, Vec3 anchor, double radius, String element) {
        AABB box = AABB.ofSize(anchor, radius * 2, radius * 2, radius * 2);
        return level.getEntitiesOfClass(LivingEntity.class, box, entity ->
                !entity.isSpectator() && !entity.isDeadOrDying() && matchesFilter(entity, element));
    }

    /** 身份列是否通过（奥术 / 金 / 木）；状态列与空列恒过（它们的条件在各自数值判定里查）。 */
    private static boolean identityPass(LivingEntity living, String element) {
        String name = elementShort(element);
        return switch (name) {
            case "奥术" -> living instanceof Player || MagicCircleRites.isCircleSummon(living);
            case "金" -> living instanceof Monster || living instanceof Goblin;
            case "木" -> living instanceof Animal
                    || (living instanceof Mob && !(living instanceof Monster) && !(living instanceof Goblin));
            default -> true;
        };
    }

    /** 元素列过滤（91 矩阵的元素维）：身份列认阵营；状态列认原版状态或模组附着。 */
    public static boolean matchesFilter(LivingEntity living, String elementItem) {
        if (elementItem == null || elementItem.isEmpty()) return true;
        String name = elementShort(elementItem);
        return switch (name) {
            case "奥术", "金", "木" -> identityPass(living, elementItem);
            case "火" -> living.isOnFire() || attachLayers(living, "dimensional_mana:fire_element_crystal_dust") > 0;
            case "水" -> living.isInWater() || attachLayers(living, "dimensional_mana:water_element_crystal_dust") > 0;
            case "冰" -> living.isInPowderSnow || living.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                    || attachLayers(living, "dimensional_mana:ice_element_crystal_dust") > 0;
            case "雷" -> attachLayers(living, "dimensional_mana:lightning_element_crystal_dust") > 0;
            case "毒" -> living.hasEffect(MobEffects.POISON) || living.hasEffect(MobEffects.WITHER)
                    || living.hasEffect(GeneratedMod.ANCIENT_CURSE)
                    || attachLayers(living, "dimensional_mana:poison_element_crystal_dust") > 0;
            case "光" -> living.hasEffect(MobEffects.GLOWING) || hasBeneficial(living)
                    || attachLayers(living, "dimensional_mana:light_element_crystal_dust") > 0;
            case "风" -> living.isFallFlying() || living.hasEffect(MobEffects.LEVITATION)
                    || attachLayers(living, "dimensional_mana:wind_element_crystal_dust") > 0;
            case "土" -> living.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || living.hasEffect(MobEffects.DIG_SLOWDOWN)
                    || attachLayers(living, "dimensional_mana:earth_element_crystal_dust") > 0;
            case "音" -> attachLayers(living, "dimensional_mana:sound_element_crystal_dust") > 0
                    || living.hasEffect(MobEffects.CONFUSION) || living.hasEffect(MobEffects.DARKNESS)
                    || living.getEffect(NewSpellContent.ARCANE_SILENCE) != null;
            default -> true;
        };
    }

    private static boolean hasBeneficial(LivingEntity living) {
        for (MobEffectInstance effect : living.getActiveEffects()) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) return true;
        }
        return false;
    }

    /** 元素粉 id → 单字短名（火 / 水 / 冰 / 雷 / 毒 / 光 / 风 / 土 / 音 / 金 / 木 / 奥术）。 */
    public static String elementShort(String itemId) {
        if (itemId == null || itemId.isEmpty()) return "";
        if (MagicCirclePowders.isArcane(itemId)) return "奥术";
        return MagicCirclePowders.shortName(itemId);
    }

    // ---------------------------------------------------------------- 大白话解析

    /**
     * 「当周围 8 格内 (燃烧) 的 (敌对生物) 数量 ≥ 3 时放行。」
     * 文档四 2 的格式；粉末一装上立刻发这句。
     */
    public static String describe(MagicCircleNetwork.Node node) {
        return describe(node.semantic(), node.element(), node.power(), node.dye(), node.gate());
    }

    public static String describe(MagicCircleSyncPayload.NodeView node) {
        return describe(node.semantic(), node.element(), node.power(), node.dye(), node.gate());
    }

    public static String describe(String semantic, String element, String power, String dye, String gate) {
        Domain domain = domainOf(semantic);
        if (domain == null) {
            return "还没装语义粉：门控暂时直接放行。装一种原版粉（糖 / 紫晶 / 青金 / 萤石 / 红石 / 火药 / 烈焰）才有判定。";
        }
        CompareOperator op = operatorOf(domain, gate);
        double value = threshold(domain, Math.max(0, MagicCircleDecl.dyeTier(dye)));
        String radius = String.valueOf(scanRadius(power));
        String target = element == null || element.isEmpty() ? "" : "(" + elementPhrase(element) + ") 的 ";
        String body = switch (domain) {
            case LIFE -> "目标生命值 " + op.symbol() + " " + thresholdText(domain, value);
            case RESOURCE -> "目标魔素/斗气 " + op.symbol() + " " + thresholdText(domain, value);
            case STATUS -> "状态剩余时长 " + op.symbol() + " " + thresholdText(domain, value);
            case ENVIRONMENT -> environmentPhrase(element, op, value);
            case ACTION -> actionPhrase(element, op, value);
            case COUNT -> "范围内目标数量 " + op.symbol() + " " + thresholdText(domain, value);
            case ATTACH -> "元素附着层数 " + op.symbol() + " " + thresholdText(domain, value);
        };
        return "当周围 " + radius + " 格内 " + target + body + " 时放行。";
    }

    private static String environmentPhrase(String element, CompareOperator op, double value) {
        String name = elementShort(element);
        if (element == null || element.isEmpty() || name.equals("金") || name.equals("木")) {
            return "环境数值 " + op.symbol() + " " + (int) value;
        }
        return (op == CompareOperator.LESS_THAN ? "没有" : "存在") + name + "环境";
    }

    private static String actionPhrase(String element, CompareOperator op, double value) {
        String name = elementShort(element);
        if (element == null || element.isEmpty()) return "红石信号强度 " + op.symbol() + " " + (int) value;
        if (name.equals("奥术")) return "与施法者距离 " + op.symbol() + " " + (int) value + "格";
        return "检测到" + actionName(name);
    }

    /** 元素列的判定含义（解析句里的括号内容）。 */
    public static String elementPhrase(String elementItem) {
        String name = elementShort(elementItem);
        return switch (name) {
            case "奥术" -> "玩家/魔法生物";
            case "金" -> "敌对/亡灵/哥布林";
            case "木" -> "动物/友好";
            case "火" -> "燃烧";
            case "水" -> "入水/溺水";
            case "冰" -> "冻结/迟缓";
            case "雷" -> "感电";
            case "毒" -> "中毒/腐化";
            case "光" -> "发光/增益";
            case "风" -> "滞空/漂浮";
            case "土" -> "迟缓/定身";
            case "音" -> "震荡/沉默";
            default -> name;
        };
    }

    /** 触发节点（底座模式）的解析句后半：上游门控条件的阈值摘要。 */
    public static String gateShort(MagicCircleNetwork.Node gateNode) {
        Domain domain = domainOf(gateNode.semantic());
        if (domain == null) return "（该门控还没装语义粉）";
        CompareOperator op = operatorOf(domain, gateNode.gate());
        double value = threshold(domain, Math.max(0, MagicCircleDecl.dyeTier(gateNode.dye())));
        String target = gateNode.element().isEmpty() ? "" : elementPhrase(gateNode.element()) + "的";
        return target + domain.label() + " " + op.symbol() + " " + thresholdText(domain, value);
    }

    private MagicCircleGates() {}
}
