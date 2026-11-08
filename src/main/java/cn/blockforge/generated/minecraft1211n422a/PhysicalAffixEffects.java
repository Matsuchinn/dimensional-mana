package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * r332 物理词条扩充的实战结算。
 *
 * <p>这里只处理玩家打出的<b>物理</b>伤害（贴脸近战、弓弩箭矢、斗气技能那几刀），法术伤害一概不碰：</p>
 *
 * <table border="1">
 *   <caption>新增物理词条</caption>
 *   <tr><th>词条</th><th>结算位置</th><th>怎么算</th></tr>
 *   <tr><td>破甲</td><td>受击事件</td><td>把目标护甲当成「护甲 - 破甲」重算一次减伤，
 *       多出来的那部分伤害在护甲结算之后补上，等价于这几刀真的无视了对面的护甲</td></tr>
 *   <tr><td>连击伤害</td><td>受击事件 + 伤害结算</td><td>40 刻内连续打同一目标，每击 +1 层（最多 4 层），
 *       每层按词条百分比增伤；换目标或脱战就重来</td></tr>
 *   <tr><td>处决伤害</td><td>受击事件 + 伤害结算</td><td>目标进入「处决」线（生命值低于处决阈值）时的额外增伤</td></tr>
 *   <tr><td>见切伤害</td><td>受击事件 + 伤害结算</td><td>目标正处在完美格挡打出的「招架破绽」里时的额外增伤</td></tr>
 *   <tr><td>物理吸血</td><td>伤害落地后</td><td>造成的物理伤害 × 百分比，直接补给自己</td></tr>
 *   <tr><td>元素附加</td><td>伤害落地后</td><td>额外结算一段对应元素的法术伤害，会挂上元素附着、也会走元素反应</td></tr>
 *   <tr><td>箭矢伤害</td><td>受击事件（箭矢）</td><td>见 {@link EquipmentAffixSystem#onRangedIncomingDamage}</td></tr>
 *   <tr><td>招架特化</td><td>完美格挡判定</td><td>见 {@link BattleQiParry#perfectWindowTicks}</td></tr>
 *   <tr><td>斗气恢复 / 斗气技能强度</td><td>斗气系统</td><td>见 {@link QiSkills#tickRegen} 与拔刀斩 / 剑气伤害</td></tr>
 * </table>
 *
 * <p>「护甲穿透后多出来的伤害」「连击 / 处决 / 见切增伤」都在护甲结算之后统一乘上去，
 * 和本模组「招架破绽 +75%」的写法保持一致（{@link ManaEvents#onDamagePre}）。</p>
 */
public final class PhysicalAffixEffects {
    /** 连击层数保留多久：脱战 40 刻（2 秒）刷掉。 */
    private static final int COMBO_WINDOW_TICKS = 40;
    /** 连击最多叠 4 层。 */
    private static final int COMBO_MAX_STACKS = 4;

    /**
     * 一次命中在「受击阶段」算好、留到「伤害结算阶段」用的加成。
     *
     * @param tick     记录时的游戏刻：结算时对不上就丢，避免 cancelled 的伤害留下脏数据
     * @param attacker 攻击者 UUID
     * @param armorPen 破甲换算出来的额外伤害（已经是护甲结算之后的数值）
     * @param combo    连击倍率（1.0 表示没有）
     * @param mikiri   见切倍率
     * @param execute  处决倍率
     */
    private record Pending(long tick, UUID attacker, float armorPen, double combo, double mikiri, double execute) {
        boolean untouched() {
            return armorPen <= 0.0F && combo == 1.0D && mikiri == 1.0D && execute == 1.0D;
        }
    }

    private record ComboState(long tick, int stacks, UUID target) { }

    /** 受害者 UUID → 这一下留到结算阶段用的加成。 */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** 攻击者 UUID → 连击状态。 */
    private static final Map<UUID, ComboState> COMBO = new HashMap<>();

    /**
     * 受击阶段：算出这一下的破甲、连击、处决、见切加成并记下来。
     *
     * <p>放在 {@code LivingIncomingDamageEvent} 而不是伤害结算阶段，是因为破甲要用「护甲结算之前」
     * 的伤害值才能反推出「无视这些护甲会多打多少」；连击层数也要在每一击都记一次。</p>
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.isCanceled()) return;
        float amount = event.getAmount();
        if (amount <= 0.0F) return;
        DamageSource source = event.getSource();
        // 必杀伤害（/kill、斩杀这类）不参与加成：它已经是「无论如何都会死」的定值伤害。
        if (LethalDamage.isLethal(source, amount)) return;
        if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker == victim) return;
        boolean melee = isPhysicalHit(source, attacker);
        boolean arrow = source.getDirectEntity() instanceof AbstractArrow;
        if (!melee && !arrow) return;

        long now = victim.level().getGameTime();
        float armorPen = armorPenBonus(victim, source, amount, EquipmentAffixSystem.armorPenetration(attacker));
        double combo = 1.0D + EquipmentAffixSystem.comboDamagePercent(attacker) / 100.0 * comboStacks(attacker, victim, now);
        double mikiri = 1.0D;
        if (victim.hasEffect(NewSpellContent.PARRY_VULNERABLE)) {
            mikiri += EquipmentAffixSystem.mikiriDamagePercent(attacker) / 100.0;
        }
        double execute = 1.0D;
        double threshold = QiProgression.executeThreshold(attacker);
        if (threshold > 0.0D && victim.getHealth() < victim.getMaxHealth() * threshold) {
            execute += EquipmentAffixSystem.executeDamagePercent(attacker) / 100.0;
        }
        Pending pending = new Pending(now, attacker.getUUID(), armorPen, combo, mikiri, execute);
        if (pending.untouched()) return;
        PENDING.put(victim.getUUID(), pending);
        prune(now);
    }

    /**
     * 伤害结算阶段（护甲、附魔、斗气减伤都算完之后）：把上面记下的加成乘上去。
     *
     * <p>顺序：先补破甲多出来的那一段，再依次乘连击 / 见切 / 处决三个独立乘区。</p>
     */
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        Pending pending = PENDING.remove(victim.getUUID());
        if (pending == null) return;
        long now = victim.level().getGameTime();
        // 记录和结算必须是同一刻：中间这一段可能被别的模组取消掉了。
        if (now - pending.tick() > 1L || now < pending.tick()) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer attacker)
                || !attacker.getUUID().equals(pending.attacker())) return;
        // 必杀伤害同样不再叠倍率（和 ManaEvents.onDamagePre 的处理保持一致）。
        if (LethalDamage.isLethal(source, event.getNewDamage())) return;
        float damage = event.getNewDamage() + pending.armorPen();
        damage = (float)(damage * pending.combo() * pending.mikiri() * pending.execute());
        event.setNewDamage(Math.max(0.0F, damage));
    }

    /**
     * 伤害落地之后：物理吸血与元素附加。
     *
     * <p>这两个都是「额外效果」，放在落地之后结算，才不会干扰这一下本身的伤害数字
     * （附加的元素伤害自己也走法术伤害，所以不会被这里再处理一次）。</p>
     */
    public static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        float damage = event.getNewDamage();
        if (damage <= 0.0F) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == victim) return;
        DamageSource source = event.getSource();
        boolean melee = isPhysicalHit(source, attacker);
        boolean arrow = source.getDirectEntity() instanceof AbstractArrow;
        if (!melee && !arrow) return;

        // 物理吸血：将造成的物理伤害按百分比转化为自身生命（不会超过生命上限）。
        double lifesteal = EquipmentAffixSystem.lifestealPercent(attacker);
        if (lifesteal > 0.0D) {
            float heal = (float)(damage * lifesteal / 100.0D);
            if (heal > 0.0F && attacker.isAlive()) attacker.heal(heal);
        }
        // 元素附加：物理命中额外附带一段对应元素的法术伤害，挂附着、走反应。
        applyElementalAddition(attacker, victim);
    }

    /**
     * 元素附加：按「元素 → 点数」逐系结算一段额外元素伤害。
     *
     * <p>走 {@link ElementReactionSystem#elementalDamage} 再走
     * {@link SpellDamage#hitResolvedElement}，和本模组其它「附伤」完全同一条通道：
     * 会正常挂上元素附着、也会和目标身上已有的元素触发反应，但这段伤害本身不再暴击、不再吃刻蚀。</p>
     */
    private static void applyElementalAddition(ServerPlayer attacker, LivingEntity victim) {
        Map<String, Double> additions = EquipmentAffixSystem.elementalAddition(attacker);
        if (additions.isEmpty() || !victim.isAlive()) return;
        Level level = victim.level();
        for (Map.Entry<String, Double> entry : additions.entrySet()) {
            float extra = entry.getValue().floatValue();
            if (extra <= 0.0F) continue;
            ElementType element = ElementType.byId(entry.getKey());
            if (element == null) {
                // 奥术不属于 11 种初级元素，没有附着与反应，只算一段奥术法术伤害。
                // 武备元素附加也能给共鸣核心充能，但不视为暴击。
                if (SpellDamage.hitResolvedElement(level, attacker, victim, MagicEquipment.ARCANE, extra)) {
                    ResonanceCore.onElementDamage(attacker, MagicEquipment.ARCANE, false);
                }
            } else {
                float resolved = ElementReactionSystem.elementalDamage(attacker, victim, element, extra);
                if (resolved > 0.0F
                        && SpellDamage.hitResolvedElement(level, attacker, victim, element.id(), resolved)) {
                    // 附加元素伤害没有独立暴击判定，因此按普通元素命中充能。
                    ResonanceCore.onElementDamage(attacker, element.id(), false);
                }
            }
            if (level instanceof ServerLevel serverLevel) {
                var at = victim.getBoundingBox().getCenter();
                serverLevel.sendParticles(particleFor(element), at.x, at.y, at.z, 6, 0.3, 0.35, 0.3, 0.12);
            }
        }
    }

    /** 元素附加的小粒子：按元素挑一种气质相符的原版粒子，纯表现，不影响结算。 */
    private static net.minecraft.core.particles.ParticleOptions particleFor(ElementType element) {
        if (element == null) return net.minecraft.core.particles.ParticleTypes.PORTAL;
        return switch (element) {
            case FIRE -> net.minecraft.core.particles.ParticleTypes.FLAME;
            case WATER -> net.minecraft.core.particles.ParticleTypes.SPLASH;
            case ICE -> net.minecraft.core.particles.ParticleTypes.SNOWFLAKE;
            case LIGHTNING -> net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK;
            case WIND -> net.minecraft.core.particles.ParticleTypes.GUST;
            case EARTH -> net.minecraft.core.particles.ParticleTypes.POOF;
            case NATURE -> net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER;
            case METAL -> net.minecraft.core.particles.ParticleTypes.CRIT;
            case LIGHT -> net.minecraft.core.particles.ParticleTypes.END_ROD;
            case POISON -> net.minecraft.core.particles.ParticleTypes.ITEM_SLIME;
            case SOUND -> net.minecraft.core.particles.ParticleTypes.NOTE;
        };
    }

    /**
     * 是不是玩家打出的「物理」伤害：贴脸近战、拔刀斩这类斗气技能都算（它们的伤害源就是玩家本体），
     * 箭矢在上面的调用点单独判定，法术（含本模组自己的附伤）一律不算，避免递归。
     */
    private static boolean isPhysicalHit(DamageSource source, ServerPlayer attacker) {
        if (source == null || SpellDamage.isSpell(source) || MagicEtchingSystem.isTrueDamage(source)) return false;
        // 盾牌弹反打出的伤害不是"攻击"：它不受吸血、连击、处决、见切这些攻击者收益影响
        // （伤害源本身也不带实体，这里再显式挡一道，换成别的伤害类型也不会被误算）。
        if (ShieldTridentEffects.isReflect(source)) return false;
        Entity direct = source.getDirectEntity();
        return direct == attacker;
    }

    /**
     * 破甲换算：把目标护甲当成「护甲 - 破甲」重新算一次原版减伤，多出来的部分就是这几刀少扣的血。
     *
     * <p>只在护甲结算之前（{@code LivingIncomingDamageEvent}）算得准，所以调用点只有一个。
     * 目标本来就没护甲、或者这一下本来就无视护甲时，破甲不会二次生效。</p>
     */
    private static float armorPenBonus(LivingEntity victim, DamageSource source, float amount, double pen) {
        if (pen <= 0.0D || amount <= 0.0F) return 0.0F;
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) return 0.0F;
        AttributeInstance armorAttribute = victim.getAttribute(Attributes.ARMOR);
        if (armorAttribute == null) return 0.0F;
        float armor = (float)armorAttribute.getValue();
        if (armor <= 0.0F) return 0.0F;
        float toughness = (float)victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float before = CombatRules.getDamageAfterAbsorb(victim, amount, source, armor, toughness);
        float after = CombatRules.getDamageAfterAbsorb(victim, amount, source,
                (float)Math.max(0.0D, armor - pen), toughness);
        return Math.max(0.0F, after - before);
    }

    /** 连击层数：同一目标 40 刻内的连续命中逐次 +1 层，最多 4 层；换目标或超时就重新从 1 层算。 */
    private static int comboStacks(ServerPlayer attacker, LivingEntity victim, long now) {
        ComboState state = COMBO.get(attacker.getUUID());
        int stacks;
        if (state == null || !state.target().equals(victim.getUUID()) || now - state.tick() > COMBO_WINDOW_TICKS) {
            stacks = 1;
        } else {
            stacks = Math.min(COMBO_MAX_STACKS, state.stacks() + 1);
        }
        COMBO.put(attacker.getUUID(), new ComboState(now, stacks, victim.getUUID()));
        return stacks;
    }

    /** 玩家退出、换维度时清掉只存在内存里的连击与待结算加成。 */
    public static void clear(ServerPlayer player) {
        if (player == null) return;
        COMBO.remove(player.getUUID());
        PENDING.remove(player.getUUID());
    }

    private static void prune(long now) {
        if (PENDING.size() < 128 && COMBO.size() < 128) return;
        Iterator<Map.Entry<UUID, Pending>> pendings = PENDING.entrySet().iterator();
        while (pendings.hasNext()) {
            if (now - pendings.next().getValue().tick() > 4L) pendings.remove();
        }
        Iterator<Map.Entry<UUID, ComboState>> combos = COMBO.entrySet().iterator();
        while (combos.hasNext()) {
            if (now - combos.next().getValue().tick() > COMBO_WINDOW_TICKS) combos.remove();
        }
    }

    /** 当前连击层数（HUD / 调试用）：没有在连击就是 0。 */
    public static int comboStacks(LivingEntity attacker) {
        if (attacker == null) return 0;
        ComboState state = COMBO.get(attacker.getUUID());
        if (state == null) return 0;
        return attacker.level().getGameTime() - state.tick() > COMBO_WINDOW_TICKS ? 0 : state.stacks();
    }

    /** 这件装备是不是能出专属词条的那类装备（给图鉴 / 调试查询用）。 */
    public static boolean accepts(ItemStack stack) {
        return EquipmentAffixSystem.isSwordLike(stack) || EquipmentAffixSystem.isBowLike(stack)
                || EquipmentAffixSystem.isShieldLike(stack) || EquipmentAffixSystem.isTridentLike(stack);
    }

    private PhysicalAffixEffects() { }
}
