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
    private static final ResourceLocation AURA_COATING = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "aura_coating");
    private static final Set<UUID> RESTED_THIS_SLEEP = new HashSet<>();
    private static final Set<UUID> CONVERTING_MAGIC_XP = new HashSet<>();
    private static final ResourceLocation AURA_BREAKER = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "aura_breaker");

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
        if (event.getEntity() instanceof ServerPlayer player) SpellCasting.clearTransientState(player);
    }

    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SpellCasting.clearTransientState(player);
    }

    public void onClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer player) SpellCasting.clearTransientState(player);
        ManaData.copy(event.getOriginal(), event.getEntity());
        MagicProgression.init(event.getEntity());
        if (event.isWasDeath()) {
            // 死亡复活保留成长数据，但立即清除超出当前上限的魔素。
            ManaData.Snapshot data = ManaData.get(event.getEntity());
            ManaData.setMana(event.getEntity(), Math.min(data.mana(), data.maxMana()), false);
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
        // 刻蚀真伤交给伤害类型标签直达生命值，不再经过斗气、法术状态或外壳减免。
        if (MagicEtchingSystem.isTrueDamage(event.getSource())) {
            event.setInvulnerabilityTicks(0);
            return;
        }
        // 极性紊乱期间没有受击无敌帧：这里是在原版冷却判定之前，清零后任何来源的伤害都会按全额结算。
        if (target.hasEffect(NewSpellContent.POLARITY_DISORDER)) target.invulnerableTime = 0;
        if (event.getAmount() <= 0) return;
        float incoming = NewSpellSystem.incoming(target, event.getSource(), event.getAmount());
        if (incoming < 0) { event.setCanceled(true); return; }
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
        event.setAmount(ElementReactionSystem.modifyPhysicalDamage(target, event.getSource(), incoming));
        // 攻击已经命中就打断施法；后续即使被斗气或临时生命完全吸收，也不恢复施法。
        SpellCasting.interruptByAttack(target, event.getSource());
        ManaData.Snapshot d = ManaData.get(target);
        int temporaryBonus = armorAuraBonus(target, d.aura());
        int effectiveAura = Math.min(ManaConfig.GLOBAL_AURA_CAP.get(), d.aura() + temporaryBonus);
        Entity attackerEntity = event.getSource().getEntity();
        int ignored = 0;
        if (attackerEntity instanceof LivingEntity attacker) {
            // 金钟罩期间斗气破除也无法穿透斗气，等同于免疫破甲。
            int breaker = MetalSpells.immuneToArmorBreak(target) ? 0
                    : enchantmentLevel(attacker.getMainHandItem(), AURA_BREAKER);
            ignored = (int)Math.ceil(effectiveAura * 0.08 * breaker);
        }
        int protectiveAura = Math.max(0, effectiveAura - ignored);
        double reduction = Math.min(ManaConfig.AURA_REDUCTION_CAP.get(), protectiveAura * ManaConfig.AURA_REDUCTION_PER_LAYER.get());
        float reducedAmount = (float)(event.getAmount() * (1.0 - reduction));
        event.setAmount(reducedAmount);

        int cost = (int)Math.ceil(reducedAmount / ManaConfig.DAMAGE_PER_AURA_COST.get()
                * ManaConfig.BASE_AURA_COST.get());
        cost = Math.max(ManaConfig.MIN_AURA_COST.get(), cost);
        ManaData.setAura(target, d.aura() - Math.min(d.aura(), cost + ignored));
    }

    public void onDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (MagicEtchingSystem.isTrueDamage(event.getSource())) return;
        if (victim.hasEffect(GeneratedMod.MANA_EXHAUSTION)) event.setNewDamage(event.getNewDamage() * 1.30F);
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker.hasEffect(GeneratedMod.MANA_EXHAUSTION)) {
            event.setNewDamage(event.getNewDamage() * 0.40F);
        }
        // 高频声波穿透金钟罩与磐石外壳，只吃斗气减伤。
        if (SonicSpells.isSonic(event.getSource())) return;
        // 金钟罩先接住伤害，其次才是磐石外壳；两者都只吸收经过护甲/斗气减免后的实际伤害。
        event.setNewDamage(MetalSpells.absorbBell(victim, event.getNewDamage(), event.getSource()));
        // 磐石外壳在护甲/韧性/药水减伤之后接住实际伤害；外壳破裂后溢出的部分才落到生命值。
        event.setNewDamage(EarthSpells.absorbShell(victim, event.getNewDamage(), event.getSource()));
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

    /** 伤害真正落地后再结算披风反伤，避免在受击结算中途嵌套新的伤害。 */
    public void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.getNewDamage() <= 0.0F) return;
        // 共鸣核心：充能 ≥50 时受到任何伤害都会掉充能，环境与 DOT 伤害同样算数。
        ResonanceCore.onDamaged(victim);
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        MagicEquipment.tryReflect(victim, attacker, event.getNewDamage());
    }

    public void onTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LightningBolt bolt) {
            // 原版闪电只对“会挨打”的实体走 thunderHit，掉落物不一定收到被雷劈的事件，
            // 所以落下后的前两刻再自己扫一遍附近的特级魔晶掉落物，保证一定转化。
            if (!bolt.level().isClientSide && bolt.tickCount <= 2) convertCrystalDropsNear(bolt);
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity living) || living.level().isClientSide) return;
        // 法杖不是原版装备栏属性，必须主动跟随主手/副手变化同步；这样切换后状态页和本刻施法都会立刻生效。
        MagicStaffItem.syncSpellPowerAttribute(living);
        NaturalElementSystem.tick(living);
        MagicEtchingSystem.tickLiving(living);
        SpellCasting.tickLiving(living);
        NewSpellSystem.tickLiving(living);
        SpellEnvironmentSystem.tickLiving(living);
        if (living instanceof ServerPlayer serverPlayer) SpellCasting.tick(serverPlayer);
        if (living instanceof ServerPlayer corePlayer) ResonanceCore.tick(corePlayer);
        if (living instanceof Player player) {
            if (player.isSleeping() && player.getSleepTimer() >= 100) restoreAfterSleep(player);
            else if (!player.isSleeping()) RESTED_THIS_SLEEP.remove(player.getUUID());
        }
        if (living.tickCount % 10 == 0 && living.isInFluidType(GeneratedMod.PURE_LIQUID_MANA_TYPE.get())) {
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
        }
        if (living.tickCount % 20 != 0) return;
        ManaData.Snapshot d = ManaData.get(living);
        int exhaustion = Math.max(0, d.exhaustionTicks() - 20);
        ManaData.setExhaustion(living, exhaustion);

        if (exhaustion == 0 && d.mana() < d.maxMana()) {
            double regen = living instanceof WitherBoss ? 15
                    : living instanceof EnderDragon ? 10
                    : ManaConfig.PLAYER_MANA_REGEN.get() + d.affinity() * MagicEquipment.affinityMultiplier(living);
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
            ManaData.restoreMana(living, regen, false);
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
        int aura = d.aura();
        if (aura > d.maxAura()) {
            int decay = Math.max(1, (int)Math.ceil(d.maxAura() * ManaConfig.OVERCAP_AURA_DECAY.get()));
            ManaData.setAura(living, aura - decay);
        } else if (living instanceof WitherBoss) {
            ManaData.setAura(living, Math.min(d.maxAura(), aura + 20));
        } else if (living instanceof EnderDragon) {
            ManaData.setAura(living, Math.min(d.maxAura(), aura + 50));
        }

        if (living instanceof ServerPlayer player) {
            MagicAchievements.tick(player);
            ManaData.Snapshot synced = ManaData.get(player);
            PacketDistributor.sendToPlayer(player, new ManaSyncPayload(synced.mana(), synced.maxMana(), synced.aura(), synced.maxAura(),
                    synced.exhaustionTicks() > 0, ManaConfig.OVERCHARGE_SAFE_RATIO.get()));
            ProgressSyncPayload.send(player);
            syncAppraisal(player);
        }
    }

    private static void syncAppraisal(ServerPlayer player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        boolean active = AppraisalGlassesItem.isEquipped(player) && ManaData.get(player).mana() >= 0.4;
        double concentration = ManaConcentration.at(level, player.blockPosition());
        int entityId = -1;
        String targetName = "";
        double health = 0, maxHealth = 0, mana = 0, maxMana = 0;
        int aura = 0, maxAura = 0;
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
                aura = targetData.aura();
                maxAura = targetData.maxAura();
            }
        }
        PacketDistributor.sendToPlayer(player, new AppraisalSyncPayload(active, concentration, entityId, targetName,
                health, maxHealth, mana, maxMana, aura, maxAura));
    }

    private static void restoreAfterSleep(Player player) {
        if (!RESTED_THIS_SLEEP.add(player.getUUID())) return;
        ManaData.Snapshot data = ManaData.get(player);
        ManaData.setMana(player, Math.min(data.maxMana(),
                data.mana() + data.maxMana() * ManaConfig.SLEEP_MANA_RESTORE_RATIO.get()), false);
    }

    private static int armorAuraBonus(LivingEntity entity, int aura) {
        int level = 0;
        for (ItemStack armor : entity.getArmorSlots()) level += enchantmentLevel(armor, AURA_COATING);
        return (int)Math.floor(aura * 0.04 * level);
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
