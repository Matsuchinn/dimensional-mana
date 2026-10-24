package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ManaEvents {
    // 附魔的资源 ID 保留旧名，老存档里已经附上的装备不会失效；英文显示名与代码标识都改为 Battle Qi。
    private static final ResourceLocation BATTLE_QI_COATING = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "aura_coating");
    private static final Set<UUID> RESTED_THIS_SLEEP = new HashSet<>();
    private static final Set<UUID> CONVERTING_MAGIC_XP = new HashSet<>();
    private static final ResourceLocation BATTLE_QI_BREAKER = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "aura_breaker");
    /** 两种「杀手」附魔：主手武器每级 +20% 对哥布林 / 史莱姆的伤害。 */
    private static final ResourceLocation GOBLIN_SLAYER = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "goblin_slayer");
    private static final ResourceLocation SLIME_SLAYER = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "slime_slayer");

    public void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof LivingEntity living) {
            ManaData.init(living);
            if (living instanceof Player player) MagicProgression.init(player);
        }
    }

    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MagicProgression.init(player);
        SpellData.migrateSpellSkillRewards(player);
        NewSpellSystem.clearDragonSlow(player);
        var root = player.getPersistentData().getCompound("DimensionalMana");
        if (!root.getBoolean("receivedArcaneCodex")) {
            root.putBoolean("receivedArcaneCodex", true);
            ItemStack book = new ItemStack(GeneratedMod.MOD_ITEMS.get("arcane_codex").get());
            if (!player.getInventory().add(book)) player.drop(book, false);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.dimensional_mana.arcane_codex_received",
                    net.minecraft.network.chat.Component.keybind("key.dimensional_mana.open_codex")), false);
        }
        // 战斗手册和万法知识之书一起发：第一次进世界两本都进背包，以后不再重复发。
        if (!root.getBoolean("receivedCombatCodex")) {
            root.putBoolean("receivedCombatCodex", true);
            ItemStack codex = new ItemStack(GeneratedMod.MOD_ITEMS.get(CombatCodexItem.ITEM_ID).get());
            if (!player.getInventory().add(codex)) player.drop(codex, false);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.dimensional_mana.combat_codex_received"), false);
        }
        QiSyncPayload.send(player);
        MagicProgression.awardAdvancement(player, "root");
        // 元素反应使用独立进度页；登录即解锁根节点，避免整个反应树藏在主进度深处。
        MagicProgression.awardAdvancement(player, "reaction_root");
        ProgressSyncPayload.send(player);
        SpellSyncPayload.send(player);
        ElementTalentSyncPayload.send(player);
        // 大师施法手套的施法计数、披风的凝聚球开关：登录时补一次，物品描述立刻就是正确的状态。
        MagicToggleSyncPayload.send(player);
    }

    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpellCasting.clearTransientState(player);
            BattleQiParry.clear(player);
            QiSkills.clear(player);
        }
    }

    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpellCasting.clearTransientState(player);
            BattleQiParry.clear(player);
            QiSkills.clear(player);
        }
    }

    public void onClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer player) {
            SpellCasting.clearTransientState(player);
            BattleQiParry.clear(player);
            QiSkills.clear(player);
        }
        ManaData.copy(event.getOriginal(), event.getEntity());
        MagicProgression.init(event.getEntity());
        if (event.isWasDeath()) {
            // 死亡复活保留成长数据，但立即清除超出当前上限的魔素。
            ManaData.Snapshot data = ManaData.get(event.getEntity());
            ManaData.setMana(event.getEntity(), Math.min(data.mana(), data.maxMana()), false);
            // 斗气只恢复一半上限：睡一觉才是回满，死一次要付点代价。
            ManaData.setBattleQi(event.getEntity(), Math.max(1, data.maxBattleQi() / 2));
        }
    }

    public void onVanillaExperience(PlayerXpEvent.XpChange event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0
                || CONVERTING_MAGIC_XP.contains(player.getUUID())) return;
        MagicProgression.recordVanillaExperience(player, event.getAmount());
    }

    public static void addConvertedVanillaExperience(ServerPlayer player, int amount) {
        if (amount <= 0) return;
        CONVERTING_MAGIC_XP.add(player.getUUID());
        try {
            player.giveExperiencePoints(amount);
        } finally {
            CONVERTING_MAGIC_XP.remove(player.getUUID());
        }
    }

    public void onWake(PlayerWakeUpEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide && (event.updateLevel() || player.getSleepTimer() >= 100)) {
            restoreAfterSleep(player);
        }
        RESTED_THIS_SLEEP.remove(player.getUUID());
    }

    public void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        // /kill 这类必杀伤害（Float.MAX_VALUE）不参与任何修正：先把它收窄成安全值再直接放行。
        // 不这么做的话，下面任何一个大于 1 的乘区都会让它溢出成 Infinity，再吃到 0 倍率就变 NaN，
        // 血量写成 NaN 之后原版判不出死亡，生物就会卡成「血条空、打不死」的幽灵状态。
        if (LethalDamage.isLethal(event.getSource(), event.getAmount())) {
            event.setAmount(LethalDamage.SAFE_LETHAL);
            event.setInvulnerabilityTicks(0);
            return;
        }
        // 末影龙复活破绽：这段窗口里伤害按原始值结算，不套斗气、抗性和其它受击修正，也不吃受击无敌帧。
        // 「复活期间 100 血」因此就是实打实的 100 点伤害，高爆发才有机会抢下第一次那 1 秒。
        if (DragonRevival.isExposed(target)) {
            target.invulnerableTime = 0;
            event.setInvulnerabilityTicks(0);
            return;
        }
        // 刻蚀真伤交给伤害类型标签直达生命值，不再经过斗气、法术状态或外壳减免。
        if (MagicEtchingSystem.isTrueDamage(event.getSource())) {
            event.setInvulnerabilityTicks(0);
            return;
        }
        // 极性紊乱期间没有受击无敌帧：这里是在原版冷却判定之前，清零后任何来源的伤害都会按全额结算。
        if (target.hasEffect(NewSpellContent.POLARITY_DISORDER)) target.invulnerableTime = 0;
        if (event.getAmount() <= 0) return;
        // 斗气招架：姿态下受到的所有伤害 -10%；近战伤害还会先走一遍弹反结算。
        if (target instanceof ServerPlayer parryPlayer) {
            BattleQiParry.Outcome parry = BattleQiParry.onIncomingDamage(parryPlayer,
                    event.getSource(), event.getAmount());
            if (parry == BattleQiParry.Outcome.PARRIED) {
                event.setCanceled(true);
                return;
            }
            if (BattleQiParry.inStance(parryPlayer)) event.setAmount(BattleQiParry.reduceDamage(parryPlayer, event.getAmount()));
        }
        // 斗气永远是整条伤害链的第一步：先按这一下的原始伤害算出斗气减伤，
        // 溃散易伤、零下领域、披风、怪物技能、护甲、元素抗性全部在剩下的数字上继续算。
        // 这里只算「减伤多少」，斗气本身留到真正掉血的 Post 阶段才扣（见 consumeBattleQi），
        // 这样被无敌帧丢掉、或举盾完全挡下的那一下不会白扣斗气。
        applyBattleQiReduction(target, event);
        float incoming = NewSpellSystem.incoming(target, event.getSource(), event.getAmount());
        if (incoming < 0) { event.setCanceled(true); return; }
        // 斗气溃散：4 秒内全种类易伤 25%。
        incoming *= BattleQiCollapseEffect.DAMAGE_TAKEN_MULTIPLIER;
        // 零下领域：深层冻结期间受到的所有伤害提高；真正命中的直接攻击才消耗解冻次数。
        incoming *= ElementReactionSystem.deepFreezeDamageMultiplier(target);
        // 伤害偏转披风：受到的魔法伤害 -25%（每件叠加）。
        if (event.getSource().is(net.neoforged.neoforge.common.Tags.DamageTypes.IS_MAGIC)) {
            incoming *= (float)MagicEquipment.magicDamageMultiplier(target);
        }
        // 高频声波无视眩晕落空、荆棘反制这类受击修正，直接进入斗气结算。
        if (!SonicSpells.isSonic(event.getSource())) {
            float spellAdjustedDamage = SpellCasting.modifyIncomingAttack(target, event.getSource(), incoming);
            if (spellAdjustedDamage < 0) {
                event.setCanceled(true);
                return;
            }
            incoming = spellAdjustedDamage;
        }
        ElementReactionSystem.deepFreezeHit(target, event.getSource());
        incoming = ElementReactionSystem.modifyPhysicalDamage(target, event.getSource(), incoming);
        // 生物专属技能的伤害修正（女巫护盾、破甲重劈、精准打击、蛮力冲锋等）。
        incoming = MobSkills.modifyAttack(event.getSource().getEntity() instanceof LivingEntity livingAttacker
                ? livingAttacker : null, target, event.getSource(), incoming);
        event.setAmount(incoming);
        // 元素镀层：举盾时按层数掷完美格挡，成功就直接取消这次伤害；护甲抗性在 onDamagePre 里结算。
        // 放在扣斗气之前，避免完美格挡了还白扣斗气。
        ElementPlating.onIncomingDamage(event);
        if (event.isCanceled()) return;
        // 攻击已经命中就打断施法；后续即使被斗气或临时生命完全吸收，也不恢复施法。
        SpellCasting.interruptByAttack(target, event.getSource());
    }

    /**
     * 斗气减伤：伤害链的第一步，只负责按当前斗气把这一下的伤害压下来。
     *
     * <p>减伤比例 = min(上限, 可用斗气 × 每层减伤)，默认每 1 点斗气 0.01%、封顶 80%。
     * 「斗气缠绕」附魔额外提供斗气层数，「斗气破除」则先扣掉目标一部分斗气再算比例。
     * 斗气本身不在这里扣，由 {@link #consumeBattleQi} 在伤害真正落地后结算。</p>
     */
    private static void applyBattleQiReduction(LivingEntity target, net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        int qi = effectiveBattleQi(target);
        if (qi <= 0) return;
        int protectiveBattleQi = Math.max(0, qi - battleQiBreakerIgnore(target, event.getSource(), qi));
        double reduction = Math.min(ManaConfig.BATTLE_QI_REDUCTION_CAP.get(),
                protectiveBattleQi * ManaConfig.BATTLE_QI_REDUCTION_PER_LAYER.get());
        if (reduction <= 0.0) return;
        event.setAmount((float)(event.getAmount() * (1.0 - reduction)));
    }

    /**
     * 伤害真正落下之后扣斗气。
     *
     * <p>只按「打中的伤害」扣：吸收血量算，被无敌帧丢掉或盾牌完全挡下的部分不算，
     * 所以高抗性生物挨了没掉血的一下不会白白清空斗气池。</p>
     */
    private static void consumeBattleQi(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source, float taken) {
        if (taken <= 0.0F) return;
        ManaData.Snapshot d = ManaData.get(victim);
        if (d.battleQi() <= 0) return;
        int cost = (int)Math.ceil(taken / ManaConfig.DAMAGE_PER_BATTLE_QI_COST.get()
                * ManaConfig.BASE_BATTLE_QI_COST.get());
        cost = Math.max(ManaConfig.MIN_BATTLE_QI_COST.get(), cost);
        // 斗气破除在减伤阶段已经削掉一部分斗气，这里再额外打掉同样的层数。
        int ignored = battleQiBreakerIgnore(victim, source, effectiveBattleQi(victim));
        ManaData.setBattleQi(victim, d.battleQi() - Math.min(d.battleQi(), cost + ignored));
    }

    /** 计入「斗气缠绕」附魔后的当前斗气层数。 */
    private static int effectiveBattleQi(LivingEntity entity) {
        ManaData.Snapshot d = ManaData.get(entity);
        return Math.min(ManaConfig.GLOBAL_BATTLE_QI_CAP.get(), d.battleQi() + armorBattleQiBonus(entity, d.battleQi()));
    }

    /** 供指令 / 调试查询：这只生物当前斗气能提供的减伤比例（0 ~ 配置上限）。 */
    public static double battleQiReduction(LivingEntity entity) {
        return Math.min(ManaConfig.BATTLE_QI_REDUCTION_CAP.get(),
                Math.max(0, effectiveBattleQi(entity)) * ManaConfig.BATTLE_QI_REDUCTION_PER_LAYER.get());
    }

    /** 「斗气破除」按等级无视目标的一份斗气；金钟罩期间完全免疫。 */
    private static int battleQiBreakerIgnore(LivingEntity target, net.minecraft.world.damagesource.DamageSource source,
                                             int effectiveBattleQi) {
        Entity attackerEntity = source == null ? null : source.getEntity();
        if (!(attackerEntity instanceof LivingEntity attacker)) return 0;
        int breaker = MetalSpells.immuneToArmorBreak(target) ? 0
                : enchantmentLevel(attacker.getMainHandItem(), BATTLE_QI_BREAKER);
        if (breaker <= 0) return 0;
        return (int)Math.ceil(Math.max(0, effectiveBattleQi) * 0.08 * breaker);
    }

    public void onDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        // 必杀伤害不叠任何倍率；如果上游已经把它放成了无穷大，这里收窄回有限值，
        // 免得原版的 100% 减伤（×0）把它算成 NaN。
        float lethalAmount = event.getNewDamage();
        if (LethalDamage.isLethal(event.getSource(), lethalAmount)) {
            event.setNewDamage(LethalDamage.finite(lethalAmount));
            return;
        }
        // 破绽期的末影龙已经按原始伤害放行，这里不再叠抗性、镀层与吸收。
        if (DragonRevival.isExposed(victim)) return;
        // 招架破绽：被完美招架眩晕的 1 秒内，受到的所有伤害 +75%（不叠加，重复完美招架只刷新时间）。
        // 放在最前面统一乘，近战、法术、弓箭、真伤（刻蚀）都吃得到这份增伤。
        event.setNewDamage(event.getNewDamage() * BattleQiParry.vulnerabilityMultiplier(victim));
        // 刻蚀真伤绕过其他减免，但「全种类易伤」仍然算数。
        if (MagicEtchingSystem.isTrueDamage(event.getSource())) {
            if (victim.hasEffect(GeneratedMod.BATTLE_QI_COLLAPSE)) {
                event.setNewDamage(event.getNewDamage() * BattleQiCollapseEffect.DAMAGE_TAKEN_MULTIPLIER);
            }
            return;
        }
        // 生物元素/物理/爆炸抗性，以及监守者标记、远古诅咒的增伤。
        float resisted = event.getNewDamage() * (float)MobCombatSystem.damageMultiplier(victim, event.getSource());
        resisted = MobCombatSystem.markSonicBonus(victim, event.getSource(), resisted);
        resisted *= MobCombatSystem.curseMultiplier(victim, event.getSource());
        event.setNewDamage(resisted);
        if (victim.hasEffect(GeneratedMod.MANA_EXHAUSTION)) event.setNewDamage(event.getNewDamage() * 1.30F);
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            if (attacker.hasEffect(GeneratedMod.MANA_EXHAUSTION)) {
                event.setNewDamage(event.getNewDamage() * 0.40F);
            }
            // 斗气溃散期间自己打出的近战伤害 -45%（法术、弓箭不受影响）。
            if (attacker.hasEffect(GeneratedMod.BATTLE_QI_COLLAPSE) && isMeleeAttack(event.getSource())) {
                event.setNewDamage(event.getNewDamage() * BattleQiCollapseEffect.MELEE_DAMAGE_MULTIPLIER);
            }
            // 哥布林杀手 / 史莱姆杀手：主手武器每级 +20% 对对应生物的伤害，只算武器近战，
            // 避免拿着剑放法术也吃到这份加成。
            if (isMeleeAttack(event.getSource())) {
                float slayer = slayerMultiplier(attacker, victim);
                if (slayer != 1.0F) event.setNewDamage(event.getNewDamage() * slayer);
                // 斗气手套：近战伤害 +15%，独立乘区，在其它增伤之后单独再乘一次。
                if (attacker instanceof ServerPlayer glovePlayer && BattleQiGloveItem.worn(glovePlayer)) {
                    event.setNewDamage(event.getNewDamage() * BattleQiGloveItem.MELEE_DAMAGE_MULTIPLIER);
                }
            }
        }
        // 高频声波穿透金钟罩与磐石外壳，只吃斗气减伤。
        if (SonicSpells.isSonic(event.getSource())) return;
        // 元素镀层：盔甲、附魔与药水减伤都已算完，这里乘上的抗性就是最终扣血的数字。
        ElementPlating.onDamagePre(event);
        // 金钟罩先接住伤害，其次才是磐石外壳；两者都只吸收经过护甲/斗气减免后的实际伤害。
        event.setNewDamage(MetalSpells.absorbBell(victim, event.getNewDamage(), event.getSource()));
        // 磐石外壳在护甲/韧性/药水减伤之后接住实际伤害；外壳破裂后溢出的部分才落到生命值。
        event.setNewDamage(EarthSpells.absorbShell(victim, event.getNewDamage(), event.getSource()));
    }

    /** 玩家挖到铁矿或高级以上魔晶矿时，叫醒附近的守矿史莱姆。 */
    public void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (event.isCanceled() || event.getPlayer().level().isClientSide) return;
        ElementalSlime.onBlockMined(event.getPlayer(), event.getPos(), event.getState());
    }

    public void onStruckByLightning(EntityStruckByLightningEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            NaturalElementSystem.struckByLightning(living);
        } else if (event.getEntity() instanceof ItemEntity drop && !drop.level().isClientSide) {
            // 原版雷电会给落点内每个实体结算 5 点伤害，而掉落物一共只有 5 点生命值：
            // 不拦下这一下，特级魔晶会先变成超导魔晶粉末、紧接着整堆被打没，看起来就像“粉末凭空消失”。
            // 防火物品（特级魔晶、超导魔晶、超导魔晶粉末）本身就不该被雷电带走，直接取消这次结算。
            if (drop.getItem().has(net.minecraft.core.component.DataComponents.FIRE_RESISTANT)) {
                event.setCanceled(true);
            }
            convertSpecialCrystalDrop(drop);
        }
    }

    /** 闪电落点附近三格内的特级魔晶掉落物都算被劈中。 */
    private static void convertCrystalDropsNear(LightningBolt bolt) {
        if (!(bolt.level() instanceof ServerLevel level)) return;
        AABB area = new AABB(bolt.getX() - 3.0, bolt.getY() - 3.0, bolt.getZ() - 3.0,
                bolt.getX() + 3.0, bolt.getY() + 9.0, bolt.getZ() + 3.0);
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, area)) {
            convertSpecialCrystalDrop(drop);
        }
    }

    /** 被闪电劈中的特级魔晶掉落物会转化成超导魔晶粉末，数量原样保留。 */
    private static void convertSpecialCrystalDrop(ItemEntity drop) {
        ItemStack stack = drop.getItem();
        if (stack.isEmpty() || !stack.is(GeneratedMod.MOD_ITEMS.get("special_mana_crystal").get())) return;
        if (!(drop.level() instanceof ServerLevel level)) return;
        ItemStack converted = new ItemStack(GeneratedMod.MOD_ITEMS.get("superconducting_crystal_dust").get(),
                stack.getCount());
        // 直接替换堆里的物品：保留原来的位置、速度和拾取延迟，也不会凭空多出一份。
        drop.setItem(converted);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                drop.getX(), drop.getY() + 0.3, drop.getZ(), 24, 0.35, 0.35, 0.35, 0.12);
        level.playSound(null, drop.blockPosition(), net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.4F);
    }

    /** 伤害真正落地后再结算斗气消耗与披风反伤，避免在受击结算中途嵌套新的伤害。 */
    public void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        // 必杀伤害不结算斗气、经验、突破与反伤：它的数值故意放大到必死，
        // 拿去算斗气经验或突破会被强转成整数上限，反而刷出离谱数字。
        if (LethalDamage.isLethal(event.getSource(), event.getNewDamage())) return;
        // 斗气按这一下「真正打中」的伤害扣：吸收血量算，盾牌挡下的部分不算。
        // 刻蚀真伤完全绕过斗气，既不减速也不扣层数。
        float taken = event.getNewDamage() + event.getReduction(
                net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ABSORPTION);
        if (victim instanceof ServerPlayer parryPlayer && event.getNewDamage() > 0.0F) {
            // 招架姿态：每挨一下真实伤害，主手剑掉 1 点耐久。
            BattleQiParry.wearSword(parryPlayer);
        }
        if (taken > 0.0F && !MagicEtchingSystem.isTrueDamage(event.getSource())) {
            consumeBattleQi(victim, event.getSource(), taken);
        }
        if (event.getNewDamage() <= 0.0F) return;
        // 近战命中：攒斗气经验、手套回气、剑气附加的额外一段伤害。
        if (event.getSource().getEntity() instanceof ServerPlayer meleeAttacker
                && isMeleeAttack(event.getSource()) && !SpellDamage.isSpell(event.getSource())
                && !(victim instanceof Player)) {
            QiProgression.addExperience(meleeAttacker, Math.max(1,
                    (int)Math.round(event.getNewDamage() * QiProgression.MELEE_EXPERIENCE_PER_DAMAGE)));
            if (BattleQiGloveItem.worn(meleeAttacker)) {
                // 击杀那一下翻倍：2 点变 4 点。
                int gain = victim.isDeadOrDying() || victim.getHealth() <= 0.0F
                        ? BattleQiGloveItem.QI_ON_KILL : BattleQiGloveItem.QI_ON_HIT;
                ManaData.Snapshot gloveQi = ManaData.get(meleeAttacker);
                if (gloveQi.battleQi() < gloveQi.maxBattleQi()) {
                    ManaData.setBattleQi(meleeAttacker,
                            Math.min(gloveQi.maxBattleQi(), gloveQi.battleQi() + gain));
                }
            }
            QiSkills.onMeleeHit(meleeAttacker, victim, event.getNewDamage());
        }
        // 僵尸猪灵的反击怒火：受击时消耗斗气提升攻速。
        MobSkills.onHurt(victim, event.getSource(), event.getNewDamage());
        // 共鸣核心：充能 ≥50 时受到任何伤害都会掉充能，环境与 DOT 伤害同样算数。
        ResonanceCore.onDamaged(victim);
        // 一次掉血超过最大生命 80%：咬牙凝聚斗气，有概率把斗气上限永久提高一点。
        if (victim instanceof ServerPlayer player) rollBattleQiBreakthrough(player, event.getNewDamage());
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        MagicEquipment.tryReflect(victim, attacker, event.getNewDamage());
    }

    /**
     * 斗气上限的成长渠道：单次伤害超过自身最大生命 80% 时，25% 概率提升上限。
     * 提升量 1/2/4/8 点的权重分别是 65% / 25% / 9% / 1%，每个游戏日最多两次。
     */
    private static void rollBattleQiBreakthrough(ServerPlayer player, float damage) {
        if (damage < player.getMaxHealth() * 0.80F) return;
        var root = player.getPersistentData().getCompound(ManaData.ROOT_TAG);
        long day = player.level().getDayTime() / 24000L;
        if (root.getLong("battleQiBreakthroughDay") != day) {
            root.putLong("battleQiBreakthroughDay", day);
            root.putInt("battleQiBreakthroughs", 0);
        }
        int used = root.getInt("battleQiBreakthroughs");
        if (used >= 2 || player.getRandom().nextFloat() >= 0.25F) return;
        int roll = player.getRandom().nextInt(100);
        int gain = roll < 65 ? 1 : roll < 90 ? 2 : roll < 99 ? 4 : 8;
        ManaData.increaseMaxBattleQi(player, gain);
        root.putInt("battleQiBreakthroughs", used + 1);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.dimensional_mana.battle_qi_breakthrough", gain, 2 - used - 1), false);
    }

    public void onTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LightningBolt bolt) {
            // 原版闪电只对“会挨打”的实体走 thunderHit，掉落物不一定收到被雷劈的事件，
            // 所以落下后的前两刻再自己扫一遍附近的特级魔晶掉落物，保证一定转化。
            if (!bolt.level().isClientSide && bolt.tickCount <= 2) convertCrystalDropsNear(bolt);
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity living) || living.level().isClientSide) return;
        // 血量安全网：NaN 和任何数比较都是 false，原版永远判不出死亡、也不会播死亡动画，
        // 实体就会卡成「站着不动、血条空、打不死」的幽灵。这里修正成 0 并补一次正常死亡。
        // 正常游戏不会走到这里，只有别的模组或极端数值把血量写成 NaN 时才会触发。
        if (Float.isNaN(living.getHealth())) {
            living.setHealth(0.0F);
            // 已经在死亡动画里的只要把血量修正回 0，让它照常消失；还没死的才补一次正常死亡，
            // 避免同一次死亡结算两遍掉落和经验。
            if (living.deathTime == 0) living.die(living.damageSources().genericKill());
            return;
        }
        // 法杖不是原版装备栏属性，必须主动跟随主手/副手变化同步；这样切换后状态页和本刻施法都会立刻生效。
        MagicStaffItem.syncSpellPowerAttribute(living);
        NaturalElementSystem.tick(living);
        MagicEtchingSystem.tickLiving(living);
        SpellCasting.tickLiving(living);
        NewSpellSystem.tickLiving(living);
        SpellEnvironmentSystem.tickLiving(living);
        MobSkills.tickLiving(living);
        if (living instanceof ServerPlayer serverPlayer) {
            SpellCasting.tick(serverPlayer);
            // 专业鉴定眼镜：每刻维护（按秒扣魔素、按同步间隔推解析结果）。
            ProfessionalAppraisalSystem.tick(serverPlayer);
            // 饰品的定时效果：万解手镯清负面、法师之喜每分钟回魔、炽阳恩赐的高空日照计数。
            RelicEffects.onPlayerTick(serverPlayer);
            // 招架姿态的前置复核：换手、副手被占、被定身都会自动收刀。
            BattleQiParry.tick(serverPlayer);
            // 斗气技能：拔刀斩蓄力、剑士护符加速到期、自然斗气恢复。
            QiSkills.tick(serverPlayer);
        }
        if (living instanceof ServerPlayer corePlayer) ResonanceCore.tick(corePlayer);
        if (living instanceof Player player) {
            if (player.isSleeping() && player.getSleepTimer() >= 100) restoreAfterSleep(player);
            else if (!player.isSleeping()) RESTED_THIS_SLEEP.remove(player.getUUID());
        }
        if (living.tickCount % 10 == 0 && living.isInFluidType(GeneratedMod.PURE_LIQUID_MANA_TYPE.get())) {
            // 魔素史莱姆把纯态液态魔晶当作滋养液：免疫其毒性，每秒恢复 2.5% 最大生命。
            if (living instanceof ElementalSlime slime && slime.variant() == ElementalSlimeVariant.MANA) {
                if (living.tickCount % 20 == 0) living.heal(living.getMaxHealth() * 0.025F);
            } else {
                living.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
            }
        }
        if (living.tickCount % 20 != 0) return;
        ManaData.Snapshot d = ManaData.get(living);
        int exhaustion = Math.max(0, d.exhaustionTicks() - 20);
        ManaData.setExhaustion(living, exhaustion);

        if (MobCombatSystem.enabled() && MobCombatSystem.isManagedMob(living)) {
            // 原版生物：按难度/专配恢复，魔素恢复受环境浓度影响。
            MobCombatSystem.tick(living, ManaData.get(living));
        } else if (exhaustion == 0 && d.mana() < d.maxMana()) {
            double regen = ManaConfig.PLAYER_MANA_REGEN.get() + d.affinity() * MagicEquipment.affinityMultiplier(living);
            int wisdomRings = 0;
            if (living instanceof Player) {
                MagicStaffItem.StaffStats staff = MagicStaffItem.heldStats(living);
                wisdomRings = MagicAccessoryItem.equippedCount(living, "wisdom_ring", "ring");
                regen *= 1.0 + staff.manaRegeneration();
            }
            if (living.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                regen *= ManaConcentration.regenerationMultiplier(ManaConcentration.at(serverLevel, living.blockPosition()));
            }
            // 戒指写明的是固定“每秒”回复，不受环境魔素浓度二次放大。
            regen += wisdomRings * 0.2;
            // 魔素增强护符同样是固定每秒回复。
            regen += MagicEquipment.manaRegenPerSecond(living);
            // 法师之喜：固定每秒 1.0 点，同样不吃浓度加成。
            regen += cn.blockforge.generated.minecraft1211n422a.RelicEffects.manaRegenPerSecond(living);
            // 剧毒射击：沼骸的箭让目标魔素恢复速度降低 30%。
            regen *= MobSkills.manaRegenMultiplier(living);
            if (MobCombatSystem.inAntiMagicZone(living)) regen = 0.0;
            if (regen > 0.0) ManaData.restoreMana(living, regen, false);
        }

        // 被驯服的魔素史莱姆：待在 4 格内，每只每秒额外恢复 1 点魔素，最多叠 4 层。
        if (living instanceof ServerPlayer player) {
            int manaSlimes = tamedManaSlimeStacks(player);
            if (manaSlimes > 0) ManaData.restoreMana(player, manaSlimes, false);
        }

        d = ManaData.get(living);
        if (d.mana() > d.maxMana() * ManaConfig.OVERCHARGE_SAFE_RATIO.get()) {
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 2));
        }
        if (d.mana() > d.maxMana()) {
            double decayedMana = Math.max(d.maxMana(),
                    d.mana() - d.maxMana() * ManaConfig.OVERCHARGE_DECAY.get());
            ManaData.setMana(living, decayedMana, true);
        }

        d = ManaData.get(living);
        int battleQi = d.battleQi();
        if (battleQi > d.maxBattleQi()) {
            int decay = Math.max(1, (int)Math.ceil(d.maxBattleQi() * ManaConfig.OVERCAP_BATTLE_QI_DECAY.get()));
            ManaData.setBattleQi(living, battleQi - decay);
        }

        if (living instanceof ServerPlayer player) {
            MagicAchievements.tick(player);
            ManaData.Snapshot synced = ManaData.get(player);
            PacketDistributor.sendToPlayer(player, new ManaSyncPayload(synced.mana(), synced.maxMana(), synced.battleQi(), synced.maxBattleQi(),
                    synced.exhaustionTicks() > 0, ManaConfig.OVERCHARGE_SAFE_RATIO.get()));
            ProgressSyncPayload.send(player);
            QiSyncPayload.send(player);
            syncAppraisal(player);
        }
    }

    /** 4 格内已驯服的魔素史莱姆数量，作为魔素恢复层数（上限 4）。 */
    private static int tamedManaSlimeStacks(ServerPlayer player) {
        int stacks = 0;
        for (ElementalSlime ignored : player.level().getEntitiesOfClass(ElementalSlime.class,
                player.getBoundingBox().inflate(4.0),
                slime -> slime.variant() == ElementalSlimeVariant.MANA && slime.isTamed())) {
            if (++stacks >= 4) break;
        }
        return stacks;
    }

    private static void syncAppraisal(ServerPlayer player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        boolean active = AppraisalGlassesItem.isEquipped(player) && ManaData.get(player).mana() >= 0.4;
        double concentration = ManaConcentration.at(level, player.blockPosition());
        int entityId = -1;
        String targetName = "";
        double health = 0, maxHealth = 0, mana = 0, maxMana = 0;
        int battleQi = 0, maxBattleQi = 0;
        if (active) {
            HitResult hit = ProjectileUtil.getHitResultOnViewVector(player, entity -> entity instanceof LivingEntity, 12.0);
            if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target) {
                ManaData.Snapshot targetData = ManaData.get(target);
                entityId = target.getId();
                targetName = target.getDisplayName().getString();
                health = target.getHealth();
                maxHealth = target.getMaxHealth();
                mana = targetData.mana();
                maxMana = targetData.maxMana();
                battleQi = targetData.battleQi();
                maxBattleQi = targetData.maxBattleQi();
            }
        }
        PacketDistributor.sendToPlayer(player, new AppraisalSyncPayload(active, concentration, entityId, targetName,
                health, maxHealth, mana, maxMana, battleQi, maxBattleQi));
    }

    private static void restoreAfterSleep(Player player) {
        if (!RESTED_THIS_SLEEP.add(player.getUUID())) return;
        ManaData.Snapshot data = ManaData.get(player);
        ManaData.setMana(player, Math.min(data.maxMana(),
                data.mana() + data.maxMana() * ManaConfig.SLEEP_MANA_RESTORE_RATIO.get()), false);
        // 睡一觉斗气回满，刚醒来就能接着打。
        ManaData.setBattleQi(player, data.maxBattleQi());
    }

    /** 只有攻击者自己贴脸打出、直接实体就是攻击者的伤害才算近战。 */
    private static boolean isMeleeAttack(net.minecraft.world.damagesource.DamageSource source) {
        Entity direct = source.getDirectEntity();
        return direct instanceof LivingEntity && direct == source.getEntity();
    }

    private static int armorBattleQiBonus(LivingEntity entity, int battleQi) {
        int level = 0;
        for (ItemStack armor : entity.getArmorSlots()) level += enchantmentLevel(armor, BATTLE_QI_COATING);
        return (int)Math.floor(battleQi * 0.04 * level);
    }

    /**
     * 哥布林杀手 / 史莱姆杀手：主手武器每级让对应生物多挨 20% 伤害。
     *
     * <p>史莱姆按原版的 {@code Slime} 算：元素史莱姆、原版史莱姆与熔岩史莱姆都继承它，
     * 所以一个 {@code instanceof} 就把三种都覆盖了。</p>
     */
    private static float slayerMultiplier(LivingEntity attacker, LivingEntity victim) {
        ItemStack weapon = attacker.getMainHandItem();
        float multiplier = 1.0F;
        if (victim instanceof Goblin) {
            int level = enchantmentLevel(weapon, GOBLIN_SLAYER);
            if (level > 0) multiplier += 0.20F * level;
        }
        if (victim instanceof net.minecraft.world.entity.monster.Slime) {
            int level = enchantmentLevel(weapon, SLIME_SLAYER);
            if (level > 0) multiplier += 0.20F * level;
        }
        return multiplier;
    }

    public void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        // 末影龙复活会取消死亡事件；取消的那一次不能算击杀，否则斗气经验会白送。
        if (event.isCanceled()) return;
        if (event.getEntity() instanceof LivingEntity living) {
            MobSkills.onDeath(living, event.getSource());
            if (!(living instanceof Player) && event.getSource().getEntity() instanceof ServerPlayer killer) {
                QiSkills.onKill(killer, living);
            }
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            BattleQiParry.clear(player);
            QiSkills.clear(player);
        }
    }

    public void onHeal(net.neoforged.neoforge.event.entity.living.LivingHealEvent event) {
        MobSkills.onHeal(event);
    }

    public void onEffectAdded(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added event) {
        MobSkills.onEffectAdded(event);
    }

    public void onMobEntityJoin(EntityJoinLevelEvent event) {
        MobSkills.onEntityJoin(event);
    }

    public void onExplosion(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        MobSkills.onExplosion(event);
    }

    public void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) MobSkills.tickLevel(level);
    }

    private static int enchantmentLevel(ItemStack stack, ResourceLocation id) {
        var registry = stack.getItem().builtInRegistryHolder().unwrapKey();
        if (stack.isEmpty() || registry.isEmpty()) return 0;
        final int[] found = {0};
        EnchantmentHelper.runIterationOnItem(stack, (holder, level) -> holder.unwrapKey().ifPresent(key -> {
            if (key.location().equals(id)) found[0] = Math.max(found[0], level);
        }));
        return found[0];
    }
}
