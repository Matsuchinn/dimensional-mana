package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/**
 * 元素结晶戒指、三件披风与魔素增强护符的已装备加成汇总。
 *
 * <p>Curios 的真实属性修正负责“魔素强度 / 施法速度 / 减耗 / 暴击率 / 暴击伤害”这些
 * 已经存在对应属性的项目；本类只处理属性系统装不下的部分：按元素区分的增伤、
 * 附着时长、元素反应增伤、魔素上限乘区、魔素恢复，以及暴击回魔等触发型效果。</p>
 */
public final class MagicEquipment {
    /** 奥术不属于 {@link ElementType}，这里用独立字符串参与戒指匹配。 */
    public static final String ARCANE = "arcane";
    public static final String RING_SLOT = "ring";
    public static final String AMULET_SLOT = "charm";
    /** 手套戴在 Curios 的手饰栏位上。 */
    public static final String HANDS_SLOT = "hands";
    /** 本模组自带的“魔法饰品”栏位（两个格，只收标签里登记过的饰品）。 */
    public static final String MAGIC_ACCESSORY_SLOT = "magic_accessory";
    /** 披风既能放背饰槽，也能放身体（胸甲位）槽。 */
    public static final List<String> CLOAK_SLOTS = List.of("back", "body");

    public static final String MAGIC_CLOTH = "magic_cloth";
    public static final String MAGIC_SILK = "magic_silk";
    public static final String UNIVERSAL_COMPONENT = "universal_magic_component";
    public static final String FOCUS_CLOAK = "magic_focus_cloak";
    public static final String DEFLECT_CLOAK = "damage_deflect_cloak";
    public static final String MASTER_CLOAK = "magic_master_cloak";
    public static final String MANA_AMULET = "mana_enhance_amulet";
    /** 元素强化戒指：由奥术元素结晶戒指在锻造台上升级而来。 */
    public static final String ENHANCE_RING = "element_enhance_ring";

    /** 元素结晶戒指：对应元素法术伤害 +14%、附着时长 +14%、元素反应伤害 +7%，可堆叠。 */
    public static final double RING_SPELL_DAMAGE = 0.14;
    public static final double RING_ATTACHMENT = 0.14;
    public static final double RING_REACTION = 0.07;

    /** 元素强化戒指：全元素法术伤害 +18%、附着时长 +18%、元素反应伤害 +18%。 */
    public static final double ENHANCE_SPELL_DAMAGE = 0.18;
    public static final double ENHANCE_ATTACHMENT = 0.18;
    public static final double ENHANCE_REACTION = 0.18;

    /** 魔法大师披风：施法时生成魔素凝聚球的概率。 */
    public static final double MASTER_ORB_CHANCE = 0.45;
    /**
     * 凝聚球开关存在玩家自己的持久数据里（默认开启，所以记的是“关掉了没有”）。
     * 主手拿着魔法大师披风按 Shift+O 就能切，开关跟着玩家走、不跟着某一件披风走。
     */
    private static final String ORB_DISABLED_KEY = "DimensionalManaMasterOrbDisabled";

    /** 护符的暴击回魔：最大魔素的 1.28% + 22 点，冷却 2.2 秒。 */
    private static final double AMULET_CRIT_HEAL_PERCENT = 0.0128;
    private static final double AMULET_CRIT_HEAL_FLAT = 22.0;
    private static final int AMULET_CRIT_HEAL_COOLDOWN = 44;
    /** 伤害偏转披风的反射冷却 2.3 秒。 */
    private static final int REFLECT_COOLDOWN = 46;
    private static final String CRIT_HEAL_KEY = "DimensionalManaAmuletCritHeal";
    private static final String REFLECT_KEY = "DimensionalManaCloakReflect";

    public static String ringId(String elementId) { return elementId + "_element_ring"; }
    public static String crystalId(String elementId) { return elementId + "_element_crystal"; }

    /** 统计某个饰品位上指定物品的件数；槽位标识不匹配的不算。 */
    public static int equippedCount(LivingEntity entity, String itemId, String... slots) {
        if (entity == null || itemId == null) return 0;
        var holder = GeneratedMod.MOD_ITEMS.get(itemId);
        if (holder == null) return 0;
        Item item = holder.get();
        List<String> allowed = List.of(slots);
        return CuriosApi.getCuriosInventory(entity)
                .map(handler -> (int)handler.findCurios(stack -> stack.is(item)).stream()
                        .filter(result -> allowed.contains(result.slotContext().identifier()))
                        .count())
                .orElse(0);
    }

    /** 玩家的凝聚球开关；没有写过数据时默认开启。 */
    public static boolean orbEnabled(LivingEntity entity) {
        return entity == null || !entity.getPersistentData().getBoolean(ORB_DISABLED_KEY);
    }

    /** 设置凝聚球开关，返回值只用于提示文案。 */
    public static boolean setOrbEnabled(LivingEntity entity, boolean enabled) {
        if (entity == null) return enabled;
        entity.getPersistentData().putBoolean(ORB_DISABLED_KEY, !enabled);
        return enabled;
    }

    /** 元素强化戒指的件数（戒指栏位）。 */
    public static int enhanceRingCount(LivingEntity entity) {
        return equippedCount(entity, ENHANCE_RING, RING_SLOT);
    }

    public static int ringCount(LivingEntity entity, String elementId) {
        return equippedCount(entity, ringId(elementId), RING_SLOT);
    }

    public static int cloakCount(LivingEntity entity, String itemId) {
        return equippedCount(entity, itemId, CLOAK_SLOTS.toArray(new String[0]));
    }

    public static int amuletCount(LivingEntity entity) {
        return equippedCount(entity, MANA_AMULET, AMULET_SLOT);
    }

    /** 共鸣核心的魔素亲和乘区：装备后亲和部分 ×2.0。 */
    public static double affinityMultiplier(LivingEntity entity) {
        return equippedCount(entity, ResonanceCore.ITEM_ID, ResonanceCore.SLOT) > 0 ? 2.0 : 1.0;
    }

    /** 对应元素法术伤害乘区（1.0 表示没有加成）。元素强化戒指是全元素通用乘区。 */
    public static double spellDamageMultiplier(LivingEntity caster, String elementId) {
        int rings = ringCount(caster, elementId);
        int enhance = enhanceRingCount(caster);
        if (rings == 0 && enhance == 0) return 1.0;
        return (1.0 + rings * RING_SPELL_DAMAGE) * (1.0 + enhance * ENHANCE_SPELL_DAMAGE);
    }

    /** 对应元素附着时长乘区。 */
    public static double attachmentMultiplier(LivingEntity caster, String elementId) {
        int rings = ringCount(caster, elementId);
        int enhance = enhanceRingCount(caster);
        if (rings == 0 && enhance == 0) return 1.0;
        return (1.0 + rings * RING_ATTACHMENT) * (1.0 + enhance * ENHANCE_ATTACHMENT);
    }

    /** 对应元素参与的元素反应伤害乘区。 */
    public static double reactionMultiplier(LivingEntity caster, String... elementIds) {
        int best = 0;
        for (String elementId : elementIds) {
            if (elementId == null) continue;
            best = Math.max(best, ringCount(caster, elementId));
        }
        int enhance = enhanceRingCount(caster);
        if (best == 0 && enhance == 0) return 1.0;
        return (1.0 + best * RING_REACTION) * (1.0 + enhance * ENHANCE_REACTION);
    }

    /** 魔素上限乘区：伤害偏转披风 +8%、魔法大师披风 +25%，各自独立乘算。 */
    public static double maxManaMultiplier(Player player) {
        double multiplier = 1.0;
        int deflect = cloakCount(player, DEFLECT_CLOAK);
        int master = cloakCount(player, MASTER_CLOAK);
        for (int i = 0; i < deflect; i++) multiplier *= 1.08;
        for (int i = 0; i < master; i++) multiplier *= 1.25;
        return multiplier;
    }

    /** 魔素增强护符提供的固定每秒回复。 */
    public static double manaRegenPerSecond(LivingEntity entity) {
        return amuletCount(entity) * 1.25;
    }

    /** 伤害偏转披风：受到的魔法伤害减免。 */
    public static double magicDamageMultiplier(LivingEntity entity) {
        int cloaks = cloakCount(entity, DEFLECT_CLOAK);
        if (cloaks <= 0) return 1.0;
        return Math.max(0.0, 1.0 - 0.25 * cloaks);
    }

    /**
     * 魔法大师披风：施法时生成魔素凝聚球的概率。
     * 开关是玩家级设置（主手持披风按 Shift+O 切换），关掉之后概率按 0 处理，多件也不叠加。
     */
    public static double manaOrbChance(LivingEntity entity) {
        return cloakCount(entity, MASTER_CLOAK) > 0 && orbEnabled(entity) ? MASTER_ORB_CHANCE : 0.0;
    }

    /** 迅捷施法手套的法术冷却乘区（1.0 表示没有减免）。 */
    public static double cooldownMultiplier(LivingEntity entity) {
        int gloves = equippedCount(entity, CastingGloveItem.Kind.SWIFT.itemId(), HANDS_SLOT);
        return gloves <= 0 ? 1.0 : Math.max(0.10, 1.0 - gloves * CastingGloveItem.SWIFT_COOLDOWN_REDUCTION);
    }

    /** 法术暴击触发时，护符按最大魔素回复；同一时间窗口内只触发一次。 */
    public static void onSpellCritical(LivingEntity caster) {
        if (caster == null || caster.level().isClientSide || amuletCount(caster) <= 0) return;
        long now = caster.level().getGameTime();
        var data = caster.getPersistentData();
        if (data.getLong(CRIT_HEAL_KEY) > now) return;
        data.putLong(CRIT_HEAL_KEY, now + AMULET_CRIT_HEAL_COOLDOWN);
        double maxMana = ManaData.get(caster).maxMana();
        ManaData.restoreMana(caster, maxMana * AMULET_CRIT_HEAL_PERCENT + AMULET_CRIT_HEAL_FLAT, false);
    }

    /** 伤害偏转披风的反伤：45% 伤害回到伤害者身上，2.3 秒冷却。 */
    public static void tryReflect(LivingEntity victim, LivingEntity attacker, float damage) {
        if (victim == null || attacker == null || damage <= 0.0F || victim.level().isClientSide) return;
        if (attacker == victim || cloakCount(victim, DEFLECT_CLOAK) <= 0) return;
        long now = victim.level().getGameTime();
        var data = victim.getPersistentData();
        if (data.getLong(REFLECT_KEY) > now) return;
        data.putLong(REFLECT_KEY, now + REFLECT_COOLDOWN);
        SpellDamage.hitResolved(victim.level(), victim, attacker, damage * 0.45F);
        if (victim.level() instanceof net.minecraft.server.level.ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT,
                    attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.5, attacker.getZ(),
                    10, 0.3, 0.4, 0.3, 0.06);
            level.playSound(null, attacker.blockPosition(), net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.45F);
        }
    }

    private MagicEquipment() { }
}
