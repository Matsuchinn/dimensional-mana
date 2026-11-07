package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 二十三件新饰品的全部效果结算。
 *
 * <p>装备与否统一问 Curios（{@link #worn}），临时状态（冷却、日照计数、抉择形态）
 * 存在玩家自己的持久数据或物品组件里，所以退出重进、换维度都不会丢。</p>
 *
 * <p>分成几条通道，和游戏里的事件一一对应：</p>
 * <ul>
 *   <li>{@link #onIncomingDamage}：伤害落地前的受击侧（减伤、暴击抗性、致命伤抵挡）；</li>
 *   <li>{@link #onDamagePre}：造成伤害的增伤乘区；</li>
 *   <li>{@link #onDamagePost}：命中之后才发生的事（毒、吸血、附加元素伤害、击杀回血）；</li>
 *   <li>{@link #onPlayerTick}/{@link #onLevelTick}：定时效果与延迟 1 刻的追加伤害。</li>
 * </ul>
 */
public final class RelicEffects {
    public static final String CHARM_SLOT = "charm";
    public static final String UNIVERSAL_SLOT = "universal";

    public static final String GREEN_AMBER_RING = "green_amber_ring";
    public static final String SMUGGLER_RING = "smuggler_ring";
    public static final String VENOM_RING = "venom_ring";
    public static final String STRENGTH_CHARM = "strength_charm";
    public static final String MOONLIGHT_BLESSING = "moonlight_blessing";
    public static final String BLAZING_BLESSING = "blazing_blessing";
    public static final String RISK_BRACELET = "risk_bracelet";
    public static final String CHOICE_RING = "choice_ring";
    public static final String NAMELESS_CHARM = "nameless_charm";
    public static final String LOST_CHARM = "lost_charm";
    public static final String BLOODTHIRST_BROOCH = "bloodthirst_brooch";
    public static final String SERENE_BRACELET = "serene_bracelet";
    public static final String SERAPHIS_CHARM = "seraphis_charm";
    public static final String COSETAS_CHARM = "cosetas_charm";
    public static final String AURORA_CHARM = "aurora_charm";
    public static final String SEAL_BLINDFOLD = "seal_blindfold";
    public static final String MAGE_DELIGHT = "mage_delight";
    public static final String ELEMENT_ABSORPTION_CHARM = "element_absorption_charm";
    public static final String DETECTIVE_CHARM = "detective_charm";
    public static final String UNIVERSAL_BELT = "universal_belt";
    public static final String SCARLET_SPIRIT_CHARM = "scarlet_spirit_charm";
    public static final String ANGEL_CROWN = "angel_crown";
    public static final String OMNISCIENT_SHARD = "omniscient_shard";
    public static final String WORLD_BLESSING = "world_blessing";
    public static final String ADVANCED_BATTLE_QI_ETCHING = "advanced_battle_qi_etching";
    public static final String AMULET_CASKET = "amulet_casket";

    /** 塞拉菲丝：附加随机元素伤害的冷却（5 刻）。 */
    private static final int SERAPHIS_ATTACK_COOLDOWN = 5;
    /** 剧毒指环：中毒Ⅲ 持续 4 刻，整体冷却 10 刻。 */
    private static final int VENOM_COOLDOWN = 10;
    private static final int VENOM_POISON_TICKS = 4;
    /** 万解手镯：每 15 秒清一个负面效果。 */
    private static final int SERENE_CLEANSE_TICKS = 300;
    /** 法师之喜：每分钟额外回复 1% 最大魔素。 */
    private static final int MAGE_MINUTE_TICKS = 1200;
    /** 炽阳恩赐：高海拔连续日照 144000 刻（2 游戏小时）换一件。 */
    public static final int BLAZING_SUNLIGHT_TICKS = 144_000;
    /** 全知全能残片的初始层数。 */
    public static final int OMNISCIENT_CHARGES = 2;
    /** 天使王冠：每吃一次正面效果最大生命 +10%，总加成最多 +100%（生命翻倍）。 */
    public static final double ANGEL_MAX_HEALTH_STEP = 0.10;
    public static final double ANGEL_MAX_HEALTH_CAP = 1.00;
    /** 生命上限已经吃满时，再来正面效果改为回复这么多血。 */
    public static final float ANGEL_CAPPED_HEAL = 2.0F;
    /** 天使庇护：致命伤保留 1 滴血，随后 200 刻（10 秒）无敌；冷却 200 秒。 */
    public static final int ANGEL_SHELTER_INVULNERABLE_TICKS = 200;
    public static final int ANGEL_SHELTER_COOLDOWN_TICKS = 200 * 20;

    private static final String CHOICE_MODE_TAG = "dimensional_mana_aggressive";
    private static final String RISK_DEALT_KEY = "DimensionalManaRiskDealt";
    private static final String RISK_TAKEN_KEY = "DimensionalManaRiskTaken";
    private static final String VENOM_KEY = "DimensionalManaVenom";
    private static final String SERAPHIS_ATTACK_KEY = "DimensionalManaSeraphisAttack";
    private static final String SERAPHIS_DEFEND_KEY = "DimensionalManaSeraphisDefend";
    private static final String SERENE_TICK_KEY = "DimensionalManaSereneTick";
    private static final String MAGE_MINUTE_KEY = "DimensionalManaMageMinute";
    private static final String SUNLIGHT_KEY = "DimensionalManaSunlightTicks";
    private static final String SUNLIGHT_DONE_KEY = "DimensionalManaSunlightReward";
    private static final String LOST_AGGRO_KEY = "DimensionalManaLostCharmAggro";
    private static final String ANGEL_BONUS_KEY = "DimensionalManaAngelBonus";
    private static final String ANGEL_SHELTER_READY_KEY = "DimensionalManaAngelShelterReady";
    private static final String ANGEL_SHELTER_UNTIL_KEY = "DimensionalManaAngelShelterUntil";
    private static final ResourceLocation ANGEL_BONUS_ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "angel_crown_bonus");
    private static final String OMNISCIENT_KEY = "dimensional_mana_charges";
    private static final String SMUGGLER_APPLIED_KEY = "DimensionalManaSmugglerApplied";
    private static final String SMUGGLER_DIFFS_KEY = "DimensionalManaSmugglerDiffs";
    private static final String SMUGGLER_BARTER_KEY = "DimensionalManaSmugglerBarter";
    /** 右键猪灵到物品扔出的窗口；原版欣赏金锭大约等待 8 刻，留足余量。 */
    private static final int SMUGGLER_BARTER_WINDOW = 60;

    /** 延迟结算的追加伤害：毒属性伤害要等 1 刻，避免和本次命中挤在同一刻被无敌帧吞掉。 */
    private record PendingHit(ServerLevel level, UUID caster, UUID target, String element, float amount,
                              int poisonTicks, long at) { }
    private static final List<PendingHit> PENDING = new ArrayList<>();

    /** 防止「附加伤害又触发了附加伤害」的递归标记。 */
    private static boolean resolvingExtra;

    // ── 查询 ────────────────────────────────────────────────────────────────

    public static int count(LivingEntity entity, String id) {
        if (entity == null || id == null) return 0;
        var holder = GeneratedMod.MOD_ITEMS.get(id);
        if (holder == null) return 0;
        Item item = holder.get();
        return CuriosApi.getCuriosInventory(entity)
                .map(handler -> (int)handler.findCurios(stack -> stack.is(item)).stream().count())
                .orElse(0);
    }

    public static boolean worn(LivingEntity entity, String id) {
        return count(entity, id) > 0;
    }

    /** 已装备的第一件该饰品；没有就返回空栈（抉择戒指靠它读形态）。 */
    public static ItemStack firstEquipped(LivingEntity entity, String id) {
        if (entity == null || id == null) return ItemStack.EMPTY;
        var holder = GeneratedMod.MOD_ITEMS.get(id);
        if (holder == null) return ItemStack.EMPTY;
        Item item = holder.get();
        return CuriosApi.getCuriosInventory(entity)
                .flatMap(handler -> handler.findCurios(stack -> stack.is(item)).stream().findFirst())
                .map(result -> result.stack())
                .orElse(ItemStack.EMPTY);
    }

    // ── 抉择戒指 ────────────────────────────────────────────────────────────

    /** 当前形态：true 为①（造成伤害 ×1.25、受伤 ×1.35），false 为②。 */
    public static boolean choiceAggressive(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getBoolean(CHOICE_MODE_TAG);
    }

    public static boolean choiceAggressive(LivingEntity entity) {
        return choiceAggressive(firstEquipped(entity, CHOICE_RING));
    }

    /** 切换手里这枚戒指的形态，返回切换后的「是否①」。 */
    public static boolean toggleChoiceMode(ItemStack stack) {
        boolean next = !choiceAggressive(stack);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(CHOICE_MODE_TAG, next));
        return next;
    }

    // ── 暴击 ────────────────────────────────────────────────────────────────

    /** 风险手镯的重掷：没暴击才重来一次，打人 7 刻、挨打 14 刻冷却。 */
    public static boolean riskReroll(LivingEntity entity, boolean taken) {
        if (!(entity instanceof Player player) || !worn(player, RISK_BRACELET)) return false;
        if (!CriticalConfig.ENABLED.get()) return false;
        long now = player.level().getGameTime();
        String key = taken ? RISK_TAKEN_KEY : RISK_DEALT_KEY;
        CompoundTag data = player.getPersistentData();
        if (data.getLong(key) > now) return false;
        data.putLong(key, now + (taken ? 14 : 7));
        return true;
    }

    /** 近战暴击：力量护符换 4 秒力量Ⅰ。 */
    public static void onMeleeCritical(LivingEntity attacker, LivingEntity victim, float damage) {
        if (!(attacker instanceof Player player) || victim == null || damage <= 0.0F) return;
        if (worn(player, STRENGTH_CHARM)) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 80, 0, false, true, true));
        }
    }

    // ── 受击侧 ──────────────────────────────────────────────────────────────

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        // /kill 这类必杀伤害不归饰品管：天使庇护「留 1 滴血」、全知残片「整下免掉」
        // 都会让 /kill 直接失效，看起来就像杀不死。
        if (LethalDamage.isLethal(event.getSource(), event.getAmount())) return;
        // 已经被弹反、完美格挡等前置逻辑取消的伤害，不该再消耗天使庇护这类一次性保险。
        if (event.isCanceled()) return;
        float amount = event.getAmount();
        if (amount <= 0.0F) return;
        if (!(victim instanceof Player player)) return;
        // 天使庇护：先看无敌窗口，再看这一次会不会致死。
        if (tryAngelShelter(player, event)) return;
        // 全知全能残片：再替玩家挡下致命一击。
        if (tryOmniscientShield(player, event)) return;
        if (MagicEtchingSystem.isTrueDamage(event.getSource())) return;

        float result = amount;
        boolean melee = event.getSource().getDirectEntity() == event.getSource().getEntity()
                && event.getSource().getEntity() != null;
        if (melee && worn(player, NAMELESS_CHARM)) result *= 0.75F;
        if (worn(player, SERENE_BRACELET)) result *= 0.95F;
        if (worn(player, CHOICE_RING)) result *= choiceAggressive(player) ? 1.35F : 0.5F;
        if (worn(player, WORLD_BLESSING) && player.level().dimension() == Level.OVERWORLD) {
            String element = ElementPlating.damageElementOf(event.getSource());
            if (element != null) result *= 0.85F;
        }
        if (worn(player, SERAPHIS_CHARM) && event.getSource().getEntity() != null) {
            long now = player.level().getGameTime();
            CompoundTag data = player.getPersistentData();
            if (data.getLong(SERAPHIS_DEFEND_KEY) <= now) {
                data.putLong(SERAPHIS_DEFEND_KEY, now + 1);
                float roll = 0.01F + player.getRandom().nextFloat() * 0.14F;
                // 一半概率多挨一点，一半概率少挨一点，数值都是 1%~15%。
                result *= player.getRandom().nextBoolean() ? 1.0F + roll : 1.0F - roll;
            }
        }
        if (result != amount) event.setAmount(result);
    }

    /**
     * 天使庇护的受击侧：无敌窗口里这一下直接作废。
     *
     * <p>真正的「保命」在 {@link #onAngelShelterFinalGuard}——那里的伤害已经过完护甲、
     * 抗性和所有乘区，压下去的数字才是玩家真正会掉的血。</p>
     */
    private static boolean tryAngelShelter(Player player, LivingIncomingDamageEvent event) {
        if (!worn(player, ANGEL_CROWN)) return false;
        if (player.getPersistentData().getLong(ANGEL_SHELTER_UNTIL_KEY) <= player.level().getGameTime()) return false;
        event.setAmount(0.0F);
        event.setCanceled(true);
        return true;
    }

    /**
     * 天使庇护的最后一环：所有伤害乘区结算完之后再兜一次底。
     *
     * <p>受击那一层拿到的是「护甲前」的原始数值，之后还会被招架破绽、诅咒、元素增伤
     * 这类乘区乘回去。只在那层压到剩 1 滴血的话，玩家会在庇护刚触发的那一刻被真打死：
     * 消息、粒子、冷却都播了，人却进了死亡界面——也就是玩家说的「免死触发后假死」。
     * 这里排在最低优先级，拿到的已经是最终伤害，按「吸收盾 + 生命」
     * 重算一遍，保证这一下最多只扣到剩 1 点。</p>
     */
    public static void onAngelShelterFinalGuard(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        if (!worn(player, ANGEL_CROWN)) return;
        float damage = event.getNewDamage();
        if (damage <= 0.0F) return;
        // /kill 这类必杀伤害不归饰品管，和受击侧保持一致。
        if (LethalDamage.isLethal(event.getSource(), damage)) return;
        long now = player.level().getGameTime();
        if (player.getPersistentData().getLong(ANGEL_SHELTER_UNTIL_KEY) > now) {
            event.setNewDamage(0.0F);
            return;
        }
        float total = player.getHealth() + player.getAbsorptionAmount();
        if (damage < total) return;
        if (!startAngelShelter(player, now, true)) return;
        event.setNewDamage(Math.max(0.0F, total - 1.0F));
    }

    /**
     * 天使庇护的死亡兜底：无敌窗口里不该有真正的死亡。
     *
     * <p>万一还有别的结算直接把血写到 0（其它模组直接 setHealth、绕过伤害乘区的真伤等），
     * 就在死亡事件上把这一下驳回、把血补回 1 点。否则服务端会先发死亡界面、玩家再被
     * 某个治疗补回血量，客户端就停在「死亡界面 + 人还活着」的假死状态：点重生被服务端
     * 当成「还没死」忽略，人卡在原地。</p>
     */
    public static void onAngelShelterDeathGuard(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        if (!worn(player, ANGEL_CROWN)) return;
        if (player.getPersistentData().getLong(ANGEL_SHELTER_UNTIL_KEY) <= player.level().getGameTime()) return;
        event.setCanceled(true);
        player.setHealth(1.0F);
        // 死亡计时拨回 0：活人身上挂着死亡动画，20 刻后原版会把实体直接 remove 掉。
        player.deathTime = 0;
    }

    /**
     * 开启天使庇护：给 200 刻无敌并进入 200 秒冷却。
     *
     * @param lethal 这一下是致命伤（会在动作条上写“保留 1 滴血”）
     * @return 冷却已经好、成功开启时返回 {@code true}
     */
    private static boolean startAngelShelter(Player player, long now, boolean lethal) {
        CompoundTag data = player.getPersistentData();
        if (data.getLong(ANGEL_SHELTER_READY_KEY) > now) return false;
        data.putLong(ANGEL_SHELTER_READY_KEY, now + ANGEL_SHELTER_COOLDOWN_TICKS);
        data.putLong(ANGEL_SHELTER_UNTIL_KEY, now + ANGEL_SHELTER_INVULNERABLE_TICKS);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                    player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
                    40, 0.45, 0.6, 0.45, 0.25);
            level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.3F);
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                lethal ? "message.dimensional_mana.relic.angel_crown.shelter"
                        : "message.dimensional_mana.relic.angel_crown.ward"), true);
        return true;
    }

    /**
     * 全知全能残片：伤害足以致死时整下免掉，并消耗一层；层数用完后残片消失，
     * 同时给玩家 1000 点原版经验。
     */
    private static boolean tryOmniscientShield(Player player, LivingIncomingDamageEvent event) {
        ItemStack shard = firstEquipped(player, OMNISCIENT_SHARD);
        if (shard.isEmpty()) return false;
        float health = player.getHealth() + player.getAbsorptionAmount();
        if (event.getAmount() < health) return false;
        int charges = charges(shard);
        event.setAmount(0.0F);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
                    28, 0.4, 0.5, 0.4, 0.06);
            level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.15F);
        }
        if (charges <= 1) {
            shard.shrink(1);
            player.giveExperiencePoints(1000);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.dimensional_mana.relic.omniscient_shard.spent"), true);
        } else {
            setCharges(shard, charges - 1);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.dimensional_mana.relic.omniscient_shard.saved", charges - 1), true);
        }
        return true;
    }

    public static int charges(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(OMNISCIENT_KEY) ? tag.getInt(OMNISCIENT_KEY) : OMNISCIENT_CHARGES;
    }

    private static void setCharges(ItemStack stack, int charges) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(OMNISCIENT_KEY, charges));
    }

    // ── 造成伤害的乘区 ──────────────────────────────────────────────────────

    public static void onDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof Player attacker)) return;
        float damage = event.getNewDamage();
        if (damage <= 0.0F) return;
        boolean physical = isPhysicalDamage(event.getSource(), attacker);
        float result = damage;
        if (physical && worn(attacker, ADVANCED_BATTLE_QI_ETCHING)) result *= 1.35F;
        boolean melee = event.getSource().getDirectEntity() == attacker;
        if (melee && worn(attacker, STRENGTH_CHARM)) result *= 1.15F;
        if (worn(attacker, CHOICE_RING)) result *= choiceAggressive(attacker) ? 1.25F : 0.75F;
        if (worn(attacker, MOONLIGHT_BLESSING) && isNight(attacker.level())) result *= 1.15F;
        if (worn(attacker, BLAZING_BLESSING) && isDay(attacker.level())) result *= 1.15F;
        if (worn(attacker, WORLD_BLESSING) && attacker.level().dimension() == Level.OVERWORLD) result *= 1.20F;
        if (result != damage) event.setNewDamage(result);
    }

    /**
     * 绝对防御：LivingDamageEvent.Pre 已经拿到护甲、抗性、斗气减伤和自定义护盾后的实际伤害。
     * 斗气超过一半且这一下不超过最大生命的 25% 时，直接免疫这一击。
     */
    public static void onAdvancedBattleQiFinalGuard(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof Player player) || player.level().isClientSide) return;
        if (!worn(player, ADVANCED_BATTLE_QI_ETCHING)) return;
        float damage = event.getNewDamage();
        if (damage <= 0.0F || LethalDamage.isLethal(event.getSource(), damage)) return;
        ManaData.Snapshot qi = ManaData.get(player);
        if (qi.maxBattleQi() > 0 && qi.battleQi() > qi.maxBattleQi() * 0.5
                && damage <= player.getMaxHealth() * 0.25F) {
            event.setNewDamage(0.0F);
        }
    }

    /** 高阶斗气铭蚀的物理伤害判定：近战和玩家射出的箭矢 / 三叉戟都算，法术与刻蚀真伤不算。 */
    private static boolean isPhysicalDamage(net.minecraft.world.damagesource.DamageSource source, Player attacker) {
        if (source == null || attacker == null || SpellDamage.isSpell(source)
                || MagicEtchingSystem.isTrueDamage(source)) return false;
        Entity direct = source.getDirectEntity();
        return direct == attacker
                || (direct instanceof net.minecraft.world.entity.projectile.AbstractArrow
                && source.getEntity() == attacker);
    }

    /** 极限壁垒：默认斗气减伤上限 80%，佩戴高阶斗气铭蚀时提高到 90%。 */
    public static double battleQiReductionCap(LivingEntity entity) {
        return worn(entity, ADVANCED_BATTLE_QI_ETCHING)
                ? Math.max(0.90, ManaConfig.BATTLE_QI_REDUCTION_CAP.get())
                : ManaConfig.BATTLE_QI_REDUCTION_CAP.get();
    }

    // ── 命中之后 ────────────────────────────────────────────────────────────

    public static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || resolvingExtra) return;
        float damage = event.getNewDamage();
        if (damage <= 0.0F) return;
        if (!(event.getSource().getEntity() instanceof Player player) || player == victim) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        CompoundTag data = player.getPersistentData();

        // 迷失护符：打过这只亡灵之后，它有一小段时间能看见穿戴者。
        if (victim.getType().is(EntityTypeTags.UNDEAD)) {
            victim.getPersistentData().putLong(LOST_AGGRO_KEY, now + 200);
        }

        // 剧毒指环：1 刻后追加 2% 毒属性伤害，并挂中毒Ⅲ 4 刻。
        if (worn(player, VENOM_RING) && data.getLong(VENOM_KEY) <= now) {
            data.putLong(VENOM_KEY, now + VENOM_COOLDOWN);
            PENDING.add(new PendingHit(level, player.getUUID(), victim.getUUID(), ElementType.POISON.id(),
                    damage * 0.02F, VENOM_POISON_TICKS, now + 1));
        }

        // 塞拉菲丝：额外附加 1%~15% 的随机元素伤害，冷却 5 刻。
        // 这段附伤按真正的元素伤害结算：先给目标挂上对应元素的附着，并和目标身上已有的
        // 元素走同一套反应判定，反应后的伤害再用“已结算元素伤害”通道落地（不再暴击、
        // 不再吃刻蚀）。整段都处于 resolvingExtra 中，所以附伤与它触发的即时反应伤害
        // 不会再回头触发饰品链，一次命中只结算一次。
        if (worn(player, SERAPHIS_CHARM) && data.getLong(SERAPHIS_ATTACK_KEY) <= now) {
            data.putLong(SERAPHIS_ATTACK_KEY, now + SERAPHIS_ATTACK_COOLDOWN);
            ElementType[] elements = ElementType.values();
            ElementType element = elements[player.getRandom().nextInt(elements.length)];
            float extra = damage * (0.01F + player.getRandom().nextFloat() * 0.14F);
            resolvingExtra = true;
            try {
                float resolved = ElementReactionSystem.elementalDamage(player, victim, element, extra);
                if (resolved > 0.0F) {
                    SpellDamage.hitResolvedElement(level, player, victim, element.id(), resolved);
                }
            } finally {
                resolvingExtra = false;
            }
        }

        // 嗜血胸针：全伤害吸血 15%（直接补血，不吃自己「生命恢复 -50%」的惩罚）。
        if (worn(player, BLOODTHIRST_BROOCH)) grantHealth(player, damage * 0.15F);
        // 柯赛塔斯：血量低于 25% 时每次造成伤害回 8 点（同样是直接补血，无冷却）。
        if (worn(player, COSETAS_CHARM) && player.getHealth() < player.getMaxHealth() * 0.25F) {
            grantHealth(player, 8.0F);
        }
        // 赤灵护符：这一下把人打死就按这次击杀伤害回等量生命（100%，不再被自身 +15% 放大）。
        if (worn(player, SCARLET_SPIRIT_CHARM) && victim.isDeadOrDying()) {
            grantHealth(player, damage);
        }
        // 鲜血回响：物理暴击按本次实际造成的伤害回复斗气，并受当前斗气上限限制。
        if (worn(player, ADVANCED_BATTLE_QI_ETCHING)
                && isPhysicalDamage(event.getSource(), player)) {
            CriticalSystem.Hit hit = CriticalSystem.hit(victim);
            if (hit != null && hit.critical() && hit.attacker().equals(player.getUUID())) {
                ManaData.Snapshot qi = ManaData.get(player);
                ManaData.setBattleQi(player, Math.min(qi.maxBattleQi(),
                        qi.battleQi() + Math.max(0, Math.round(damage))));
            }
        }
    }

    /**
     * 昼夜加成只在主世界生效：下界和末地没有昼夜循环，固定时间会
     * 让「夜晚增伤」永久常驻、而「白天增伤」永远拿不到。
     */
    private static boolean isDay(Level level) {
        return level.dimension() == Level.OVERWORLD && level.isDay();
    }

    private static boolean isNight(Level level) {
        return level.dimension() == Level.OVERWORLD && !level.isDay();
    }

    /** 每刻推进延迟伤害：毒属性追加伤害在命中后 1 刻落地。 */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || PENDING.isEmpty()) return;
        long now = level.getGameTime();
        Iterator<PendingHit> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            PendingHit pending = iterator.next();
            if (pending.level() != level || pending.at() > now) continue;
            iterator.remove();
            LivingEntity target = pending.target() == null ? null
                    : level.getEntity(pending.target()) instanceof LivingEntity living ? living : null;
            LivingEntity caster = level.getEntity(pending.caster()) instanceof LivingEntity living ? living : null;
            if (target == null || !target.isAlive() || pending.amount() <= 0.0F) continue;
            resolvingExtra = true;
            try {
                SpellDamage.hitResolvedElement(level, caster, target, pending.element(), pending.amount());
            } finally {
                resolvingExtra = false;
            }
            if (pending.poisonTicks() > 0) {
                target.addEffect(new MobEffectInstance(MobEffects.POISON, pending.poisonTicks(), 2,
                        false, true, true));
            }
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ITEM_SLIME,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    8, 0.3, 0.3, 0.3, 0.02);
        }
    }

    /** 直接补血：绕过 {@code LivingHealEvent}，所以不会被「生命恢复速率」类效果削减。 */
    private static void grantHealth(LivingEntity entity, float amount) {
        // 已经死了就别再补血：把血量从 0 抬起来只会制造出「死亡界面 + 人还活着」的假死，
        // 客户端点重生会被服务端当成「还没死」忽略，人卡在原地动不了。
        if (amount <= 0.0F || entity.isDeadOrDying()) return;
        entity.setHealth(Math.min(entity.getMaxHealth(), entity.getHealth() + amount));
    }

    // ── 治疗 ────────────────────────────────────────────────────────────────

    public static void onHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player player) || entity.level().isClientSide) return;
        float amount = event.getAmount();
        if (amount <= 0.0F) return;
        if (worn(player, BLOODTHIRST_BROOCH)) amount *= 0.5F;
        if (worn(player, SCARLET_SPIRIT_CHARM)) amount *= 1.15F;
        if (amount != event.getAmount()) event.setAmount(amount);
    }

    // ── 效果增减 ────────────────────────────────────────────────────────────

    /** 奥萝菈免疫负面效果；天使王冠把正面效果换成最大生命。 */
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player player) || entity.level().isClientSide) return;
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null) return;
        MobEffectCategory category = instance.getEffect().value().getCategory();
        if (category == MobEffectCategory.HARMFUL && worn(player, AURORA_CHARM)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }
        if (category == MobEffectCategory.BENEFICIAL && worn(player, ANGEL_CROWN)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            addAngelMaxHealth(player);
        }
    }

    /**
     * 天使王冠：每次正面效果 +10% 最大生命，总加成最多 +100%；
     * 已经吃满上限后，再获得正面效果改为回复 2 点生命。
     */
    private static void addAngelMaxHealth(Player player) {
        // 死亡动画期间不结算：把血量从 0 抬回正数会让玩家停在假死状态。
        if (player.isDeadOrDying()) return;
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;
        double bonus = player.getPersistentData().getDouble(ANGEL_BONUS_KEY);
        // 用「不含王冠加成」的当前最大生命做基数，这样同时减最大生命的饰品也不会算错。
        double base = Math.max(1.0, health.getValue() - bonus);
        double cap = base * ANGEL_MAX_HEALTH_CAP;
        // 已经到顶：正面效果不再加血上限，改成回 2 点血。
        if (bonus >= cap - 1.0E-4) {
            grantHealth(player, ANGEL_CAPPED_HEAL);
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                        player.getX(), player.getY() + player.getBbHeight() * 0.8, player.getZ(),
                        3, 0.3, 0.3, 0.3, 0.02);
            }
            return;
        }
        double next = Math.min(cap, bonus + base * ANGEL_MAX_HEALTH_STEP);
        if (next <= bonus + 1.0E-4) return;
        player.getPersistentData().putDouble(ANGEL_BONUS_KEY, next);
        applyAngelBonus(player, next);
        // 补上这次新增的那部分，看上去和以前的临时生命一样立刻生效。
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + (float)(next - bonus)));
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                    player.getX(), player.getY() + player.getBbHeight() * 0.8, player.getZ(),
                    4, 0.3, 0.3, 0.3, 0.02);
        }
    }

    /** 把累计的最大生命加成写回属性；amount 为 0 就是收回。 */
    public static void applyAngelBonus(Player player, double amount) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;
        health.removeModifier(ANGEL_BONUS_ID);
        if (amount > 0.0) health.addPermanentModifier(new AttributeModifier(ANGEL_BONUS_ID, amount,
                AttributeModifier.Operation.ADD_VALUE));
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    /** 脱下天使王冠：收回它累计的最大生命，避免靠反复穿脱白嫖。 */
    public static void clearAngelBonus(Player player) {
        player.getPersistentData().putDouble(ANGEL_BONUS_KEY, 0.0);
        applyAngelBonus(player, 0.0);
    }

    /** 万解手镯：负面效果时长 -15%，用「卸掉再按 85% 时长重挂」实现。 */
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        if (!worn(player, SERENE_BRACELET)) return;
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null || instance.isInfiniteDuration()) return;
        if (instance.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) return;
        int duration = instance.getDuration();
        if (duration <= 1) return;
        String key = player.getUUID() + "|" + instance.getEffect().unwrapKey()
                .map(k -> k.location().toString()).orElse("?");
        if (!DURATION_GUARD.add(key)) return;
        try {
            int shortened = Math.max(1, duration - Math.max(1, Math.round(duration * 0.15F)));
            player.removeEffect(instance.getEffect());
            player.addEffect(new MobEffectInstance(instance.getEffect(), shortened, instance.getAmplifier(),
                    instance.isAmbient(), instance.isVisible(), instance.showIcon()));
        } finally {
            DURATION_GUARD.remove(key);
        }
    }

    private static final Set<String> DURATION_GUARD = new HashSet<>();

    // ── 亡灵不认人 ──────────────────────────────────────────────────────────

    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity mob = event.getEntity();
        if (mob.level().isClientSide) return;
        if (!(event.getNewAboutToBeSetTarget() instanceof Player player)) return;
        if (!worn(player, LOST_CHARM)) return;
        if (!mob.getType().is(EntityTypeTags.UNDEAD)) return;
        // 刚被这个玩家打过：护符不生效，亡灵照常追。
        if (mob.getPersistentData().getLong(LOST_AGGRO_KEY) > mob.level().getGameTime()) return;
        event.setCanceled(true);
    }

    // ── 走私之戒 ────────────────────────────────────────────────────────────

    /** 打开村民交易界面时按是否佩戴走私之戒刷新五折标记；右键猪灵用金锭交易时给它打个短标记。 */
    public static void onVillagerInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        Player player = event.getEntity();
        if (event.getTarget() instanceof Villager villager) {
            applySmugglerDiscount(villager, worn(player, SMUGGLER_RING));
            return;
        }
        // 猪灵以物易物是在右键后过几刻才把物品扔出来，而且扔出前副手金锭已经清空，
        // 不能在掉落物出现时靠「猪灵手里有没有金锭」判断。这里在交易发起时打时间戳，
        // 掉落物出现时看附近有没有刚交易过的猪灵。
        if (event.getTarget() instanceof Piglin piglin && worn(player, SMUGGLER_RING)
                && player.getMainHandItem().is(Items.GOLD_INGOT)) {
            piglin.getPersistentData().putLong(SMUGGLER_BARTER_KEY,
                    event.getLevel().getGameTime() + SMUGGLER_BARTER_WINDOW);
        }
    }

    /**
     * 半价：每笔交易把最终价格压到「原价一半向上取整、最少 1」。
     * 实际写进 {@code specialPriceDiff}，并把这次改了多少记在村民自己身上，摘掉戒指时原样退回。
     */
    private static void applySmugglerDiscount(Villager villager, boolean discount) {
        CompoundTag tag = villager.getPersistentData();
        boolean applied = tag.getBoolean(SMUGGLER_APPLIED_KEY);
        if (applied == discount) return;
        List<MerchantOffer> offers = villager.getOffers();
        ListTag diffs = new ListTag();
        for (int index = 0; index < offers.size(); index++) {
            MerchantOffer offer = offers.get(index);
            int base = offer.getBaseCostA().getCount();
            if (discount) {
                int target = Math.max(1, (int)Math.ceil(base * 0.5));
                int delta = target - base;
                offer.setSpecialPriceDiff(offer.getSpecialPriceDiff() + delta);
                CompoundTag entry = new CompoundTag();
                entry.putInt("i", index);
                entry.putInt("d", delta);
                diffs.add(entry);
            } else {
                int delta = 0;
                ListTag saved = tag.getList(SMUGGLER_DIFFS_KEY, 10);
                for (int entry = 0; entry < saved.size(); entry++) {
                    CompoundTag item = saved.getCompound(entry);
                    if (item.getInt("i") == index) {
                        delta = item.getInt("d");
                        break;
                    }
                }
                if (delta != 0) offer.setSpecialPriceDiff(offer.getSpecialPriceDiff() - delta);
            }
        }
        tag.putBoolean(SMUGGLER_APPLIED_KEY, discount);
        if (discount) tag.put(SMUGGLER_DIFFS_KEY, diffs);
        else tag.remove(SMUGGLER_DIFFS_KEY);
    }

    /** 猪灵交易：投出的物品落在刚交易过的猪灵附近、且玩家戴着走私之戒时，整堆翻倍。 */
    public static void onItemEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity drop)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ItemStack stack = drop.getItem();
        if (stack.isEmpty() || stack.getCount() >= stack.getMaxStackSize()) return;
        long now = level.getGameTime();
        var barterers = level.getEntitiesOfClass(Piglin.class, drop.getBoundingBox().inflate(3.0),
                piglin -> !piglin.isBaby()
                        && piglin.getPersistentData().getLong(SMUGGLER_BARTER_KEY) >= now);
        if (barterers.isEmpty()) return;
        Player player = level.getNearestPlayer(drop, 8.0);
        if (player == null || !worn(player, SMUGGLER_RING)) return;
        // 一次以物易物可能扔出好几堆；只认同一刻的这一批，避免把附近无关的掉落物也翻倍。
        for (Piglin piglin : barterers) piglin.getPersistentData().putLong(SMUGGLER_BARTER_KEY, now);
        ItemStack doubled = stack.copy();
        doubled.setCount(Math.min(stack.getMaxStackSize(), stack.getCount() * 2));
        drop.setItem(doubled);
    }

    // ── 定时效果 ────────────────────────────────────────────────────────────

    /** 每刻：万解手镯清负面、法师之喜回魔、炽阳恩赐的日照计数。 */
    public static void onPlayerTick(Player player) {
        if (player.level().isClientSide || !(player.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        CompoundTag data = player.getPersistentData();

        // 老存档里可能留下「血已经回上来了、死亡计时还挂着」的假死：再过 20 刻原版会把实体
        // 直接 remove 掉，玩家就成了看不见、动不了、点重生也没反应的幽灵。活着的人
        // deathTime 一定该是 0，这里顺手拨回去。
        if (player.deathTime > 0 && player.getHealth() > 0.0F) player.deathTime = 0;

        // 天使王冠：复活或被其它系统清掉属性后，把累计的最大生命加成补回去；
        // 没戴时则确保加成已经收回。
        if (worn(player, ANGEL_CROWN)) {
            double bonus = data.getDouble(ANGEL_BONUS_KEY);
            if (bonus > 0.0 && player.getAttribute(Attributes.MAX_HEALTH) != null
                    && player.getAttribute(Attributes.MAX_HEALTH).getModifier(ANGEL_BONUS_ID) == null) {
                applyAngelBonus(player, bonus);
            }
            // 天使庇护：血量掉到一半以下就开 200 刻无敌（冷却 200 秒由 startAngelShelter 管）。
            if (player.getHealth() > 0.0F && player.getHealth() < player.getMaxHealth() * 0.5F) {
                startAngelShelter(player, now, false);
            }
        } else if (data.getDouble(ANGEL_BONUS_KEY) > 0.0) {
            clearAngelBonus(player);
        }

        if (worn(player, SERENE_BRACELET)) {
            if (data.getLong(SERENE_TICK_KEY) == 0L) data.putLong(SERENE_TICK_KEY, now + SERENE_CLEANSE_TICKS);
            if (now >= data.getLong(SERENE_TICK_KEY)) {
                data.putLong(SERENE_TICK_KEY, now + SERENE_CLEANSE_TICKS);
                cleanseOne(player);
            }
        } else {
            data.putLong(SERENE_TICK_KEY, 0L);
        }

        // 封魔眼罩与高阶斗气铭蚀都会降低魔素上限，换装后立即压回当前值，避免界面长期显示超上限。
        if (worn(player, SEAL_BLINDFOLD) || worn(player, ADVANCED_BATTLE_QI_ETCHING)) {
            ManaData.Snapshot snapshot = ManaData.get(player);
            if (snapshot.mana() > snapshot.maxMana()) ManaData.setMana(player, snapshot.maxMana(), false);
        }

        if (worn(player, MAGE_DELIGHT)) {
            if (data.getLong(MAGE_MINUTE_KEY) == 0L) data.putLong(MAGE_MINUTE_KEY, now + MAGE_MINUTE_TICKS);
            if (now >= data.getLong(MAGE_MINUTE_KEY)) {
                data.putLong(MAGE_MINUTE_KEY, now + MAGE_MINUTE_TICKS);
                ManaData.restoreMana(player, ManaData.get(player).maxMana() * 0.01, false);
            }
        } else {
            data.putLong(MAGE_MINUTE_KEY, 0L);
        }

        // 炽阳恩赐：y>200、露天、白天，连续 144000 刻送一件。
        if (!data.getBoolean(SUNLIGHT_DONE_KEY) && level.isDay() && player.getY() > 200.0
                && level.canSeeSky(player.blockPosition())) {
            int ticks = data.getInt(SUNLIGHT_KEY) + 1;
            if (ticks >= BLAZING_SUNLIGHT_TICKS) {
                data.putInt(SUNLIGHT_KEY, BLAZING_SUNLIGHT_TICKS);
                data.putBoolean(SUNLIGHT_DONE_KEY, true);
                ItemStack reward = new ItemStack(GeneratedMod.MOD_ITEMS.get(BLAZING_BLESSING).get());
                if (!player.getInventory().add(reward)) player.drop(reward, false);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.dimensional_mana.relic.blazing_blessing.forged"), false);
                level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                        SoundSource.PLAYERS, 1.0F, 1.2F);
            } else {
                data.putInt(SUNLIGHT_KEY, ticks);
            }
        }
    }

    /** 清掉一个时长最长的负面效果。 */
    private static void cleanseOne(Player player) {
        MobEffectInstance worst = null;
        for (MobEffectInstance instance : player.getActiveEffects()) {
            if (instance.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) continue;
            if (worst == null || instance.getDuration() > worst.getDuration()) worst = instance;
        }
        if (worst == null) return;
        player.removeEffect(worst.getEffect());
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + player.getBbHeight() * 0.7, player.getZ(),
                    8, 0.3, 0.3, 0.3, 0.02);
        }
    }

    // ── 给其它系统读取的数值 ────────────────────────────────────────────────

    /** 元素伤害乘区：月光（冰）、炽阳（光/火）、法师之喜（奥术）、元素吸收（自身附着的元素）。 */
    public static double elementDamageMultiplier(LivingEntity caster, String elementId) {
        if (!(caster instanceof Player player) || elementId == null) return 1.0;
        double multiplier = 1.0;
        if ("ice".equals(elementId) && worn(player, MOONLIGHT_BLESSING)) multiplier *= 1.10;
        if ("light".equals(elementId) && worn(player, BLAZING_BLESSING)) multiplier *= 1.15;
        if ("fire".equals(elementId) && worn(player, BLAZING_BLESSING)) multiplier *= 1.10;
        if (worn(player, WORLD_BLESSING) && player.level().dimension() == Level.OVERWORLD) multiplier *= 1.15;
        if (MagicEquipment.ARCANE.equals(elementId) && worn(player, MAGE_DELIGHT)) multiplier *= 1.15;
        if (worn(player, ELEMENT_ABSORPTION_CHARM)) {
            ElementType element = ElementType.byId(elementId);
            if (element != null && player.hasEffect(GeneratedMod.elementEffect(element))) multiplier *= 1.25;
        }
        return multiplier;
    }

    /** 封魔眼罩：魔素上限 -50%（独立乘区）。 */
    public static double maxManaMultiplier(Player player) {
        return worn(player, SEAL_BLINDFOLD) ? 0.5 : 1.0;
    }

    /** 法师之喜：固定每秒 1.0 点魔素恢复。 */
    public static double manaRegenPerSecond(LivingEntity entity) {
        return worn(entity, MAGE_DELIGHT) ? 1.0 : 0.0;
    }

    /** 侦探护符：预言学派的冷却 -50%。 */
    public static double divinationCooldownMultiplier(LivingEntity entity, SpellDefinition spell) {
        if (spell == null || !"divination".equals(spell.school())) return 1.0;
        return worn(entity, DETECTIVE_CHARM) ? 0.5 : 1.0;
    }

    /** 侦探护符：预言学派的前摇 -50%。 */
    public static double divinationCastMultiplier(LivingEntity entity, SpellDefinition spell) {
        return divinationCooldownMultiplier(entity, spell);
    }

    private RelicEffects() { }
}
