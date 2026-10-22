package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 奥术系追加法术：乱魔、魔素感知与魔素潮汐。
 *
 * <p>乱魔（中级·削弱）朝身前 30° 扇形喷出乱魔粒子，被扫到的生物奥术沉默 60 秒，
 * 期间无法释放技能、无法打出法术暴击，身上除奥术沉默外的增幅效果会被随机削掉一半。</p>
 *
 * <p>魔素感知（中级·预言）扫描自己所在区块：魔素超过 1000 的生物与各级魔晶矿都会被标成
 * 隔着方块也能看见的穿墙轮廓（原版发光只在实体露脸时描边，这里由客户端补一层线框）。</p>
 *
 * <p>魔素潮汐（中级·混沌）在脚下放下混乱魔力锚点：半径 16 格，每 1～40 刻不定时波动一次，
 * 造成 10 点随机元素伤害并附着；除奥术以外的元素结算完之后，下一次必定是奥术伤害。</p>
 */
public final class ArcaneSpells {
    /** 奥术没有元素媒介，统一用法术界面里的紫色。 */
    private static final int ARCANE_COLOR = 0xAE69FF;

    // ───────────────────────── 乱魔 ─────────────────────────

    private static final double CHAOS_RANGE = 48.0;
    /** 扇形总张角 30°，因此半角 15°。 */
    private static final double CHAOS_HALF_ANGLE = Math.toRadians(15.0);
    private static final int CHAOS_SILENCE_TICKS = 1200;
    private static final int CHAOS_MAX_TARGETS = 32;

    // ───────────────────────── 魔素感知 ─────────────────────────

    private static final int SENSE_TICKS = 600;
    private static final double SENSE_MANA_THRESHOLD = 1000.0;
    private static final int SENSE_MAX_ORES = 96;
    private static final int SENSE_PARTICLE_INTERVAL = 10;
    private static final int SENSE_PARTICLE_BUDGET = 24;
    private static final double SENSE_PARTICLE_RANGE = 48.0;
    /**
     * 每隔一秒把剩余时长补发一次。客户端只认这一份“还剩多少刻”，万一它把列表清掉了
     * （换维度、重进世界、丢包），下一秒就能重新画出来，30 秒的窗口不会被提前掐断。
     */
    private static final int SENSE_RESYNC_INTERVAL = 20;

    // ───────────────────────── 魔素潮汐 ─────────────────────────

    private static final double TIDE_RADIUS = 16.0;
    private static final int TIDE_TICKS = 320;
    private static final int TIDE_MIN_PULSE = 1;
    private static final int TIDE_MAX_PULSE = 40;
    private static final float TIDE_DAMAGE = 10.0F;
    private static final double TIDE_ANCHOR_HEIGHT = 0.6;

    private static final Map<UUID, Sense> SENSES = new HashMap<>();
    private static final Map<UUID, Tide> TIDES = new HashMap<>();

    /** 一次施法扫描到的结果：生物与矿物都交给客户端画穿墙轮廓，粒子只作点缀。 */
    private static final class Sense {
        private final ServerLevel level;
        private final UUID owner;
        private final long until;
        private final List<BlockPos> ores;
        /**
         * 被标记的生物 UUID：客户端按这份列表画穿墙外框，不再依赖只有实体露出画面才描边的
         * 原版发光效果；用 UUID 而不是实体 id，区块重新加载后也能找回同一只生物。
         */
        private final List<UUID> mobs;
        private int cursor;

        private Sense(ServerLevel level, UUID owner, long until, List<BlockPos> ores, List<UUID> mobs) {
            this.level = level;
            this.owner = owner;
            this.until = until;
            this.ores = ores;
            this.mobs = mobs;
        }
    }

    /** 混乱魔力锚点：固定位置，脉冲间隔与“下一发必须是奥术”都记在这里。 */
    private static final class Tide {
        private final ServerLevel level;
        private final UUID owner;
        private final Vec3 center;
        private final long born;
        private final long until;
        private long nextPulse;
        private boolean forceArcane;
        private int pulses;

        private Tide(ServerLevel level, UUID owner, Vec3 center, long born) {
            this.level = level;
            this.owner = owner;
            this.center = center;
            this.born = born;
            this.until = born + TIDE_TICKS;
            this.nextPulse = born + TIDE_MIN_PULSE;
        }
    }

    // ───────────────────────── 乱魔 ─────────────────────────

    /** 前摇结束：朝身前 30° 扇形扫出乱魔粒子，命中的生物挂上奥术沉默并削掉一半增幅。 */
    public static void castChaosMagic(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 flatLook = new Vec3(look.x, 0, look.z);
        if (flatLook.lengthSqr() < 1.0E-4) flatLook = new Vec3(0, 0, 1);
        flatLook = flatLook.normalize();
        double limit = Math.cos(CHAOS_HALF_ANGLE);

        List<LivingEntity> hits = new ArrayList<>();
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(eye, eye).inflate(CHAOS_RANGE),
                candidate -> NewSpellSystem.enemy(player, candidate));
        nearby.sort((first, second) -> Double.compare(first.distanceToSqr(eye), second.distanceToSqr(eye)));
        for (LivingEntity candidate : nearby) {
            if (hits.size() >= CHAOS_MAX_TARGETS) break;
            Vec3 center = candidate.getBoundingBox().getCenter();
            Vec3 delta = center.subtract(eye);
            if (delta.length() > CHAOS_RANGE) continue;
            Vec3 flat = new Vec3(delta.x, 0, delta.z);
            // 扇形只按水平方位判定：贴在脚边的小体型生物不会因为高度差被漏掉。
            if (flat.lengthSqr() > 1.0E-4 && flat.normalize().dot(flatLook) < limit) continue;
            if (!NewSpellSystem.clear(player, eye, center)) continue;
            hits.add(candidate);
        }

        int stripped = 0;
        for (LivingEntity victim : hits) {
            NewSpellSystem.effect(victim, NewSpellContent.ARCANE_SILENCE, CHAOS_SILENCE_TICKS);
            stripped += stripAmplifiers(victim);
            Vec3 at = victim.getBoundingBox().getCenter();
            level.sendParticles(dust(ARCANE_COLOR), at.x, at.y, at.z, 16, 0.45, 0.6, 0.45, 0.03);
            level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 10, 0.5, 0.6, 0.5, 0.6);
            level.sendParticles(ParticleTypes.WITCH, at.x, at.y, at.z, 8, 0.45, 0.55, 0.45, 0.02);
            level.playSound(null, victim.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS,
                    0.85F, 0.75F);
        }

        // 扇形本身的气浪：沿准心分几段铺开，前端窄、远端宽。
        for (double step = 3.0; step <= CHAOS_RANGE; step += CHAOS_RANGE / 6.0) {
            double spread = Math.tan(CHAOS_HALF_ANGLE) * step * 1.35 + 0.35;
            Vec3 point = eye.add(flatLook.scale(step));
            level.sendParticles(dust(ARCANE_COLOR), point.x, point.y - 0.25, point.z,
                    10, spread, 0.55, spread, 0.02);
            level.sendParticles(ParticleTypes.WITCH, point.x, point.y - 0.25, point.z,
                    4, spread * 0.8, 0.45, spread * 0.8, 0.01);
        }
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
                eye.x + flatLook.x * 8.0, eye.y - 0.2, eye.z + flatLook.z * 8.0, 40, 2.6, 0.8, 2.6, 0.04);
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.95F, 0.72F);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                0.9F, 0.85F);
        NewSpellSystem.visual(player, SpellDefinition.CHAOS_MAGIC, eye,
                eye.add(look.normalize().scale(CHAOS_RANGE)), -1, 26);

        player.displayClientMessage(Component.translatable("message.dimensional_mana.chaos_magic_cast",
                hits.size(), CHAOS_SILENCE_TICKS / 20, stripped), true);
        SpellSyncPayload.send(player);
    }

    /**
     * 削掉目标一半的增幅效果：每个正面状态各掷一次五成概率，奥术沉默本身不会被削掉。
     *
     * @return 实际被削掉的状态数量
     */
    private static int stripAmplifiers(LivingEntity victim) {
        List<MobEffectInstance> amplifiers = new ArrayList<>();
        for (MobEffectInstance instance : victim.getActiveEffects()) {
            if (instance.getEffect().value() == NewSpellContent.ARCANE_SILENCE.get()) continue;
            if (!instance.getEffect().value().isBeneficial()) continue;
            amplifiers.add(instance);
        }
        int removed = 0;
        for (MobEffectInstance instance : amplifiers) {
            if (victim.getRandom().nextFloat() >= 0.5F) continue;
            victim.removeEffect(instance.getEffect());
            removed++;
        }
        return removed;
    }

    // ───────────────────────── 魔素感知 ─────────────────────────

    /** 前摇结束：扫描自己所在区块，把高魔素生物与各级魔晶矿标成穿墙轮廓 30 秒。 */
    public static void castManaSense(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        ChunkPos chunk = player.chunkPosition();
        List<BlockPos> ores = scanOres(level, chunk);

        AABB column = new AABB(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1.0, level.getMaxBuildHeight(), chunk.getMaxBlockZ() + 1.0);
        List<UUID> mobIds = new ArrayList<>();
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, column)) {
            if (living == player || !living.isAlive() || living.isSpectator()) continue;
            if (ManaData.get(living).mana() < SENSE_MANA_THRESHOLD) continue;
            // 原版发光保留：实体露在画面里时多一层贴脸描边，穿墙那层由客户端自己画。
            living.addEffect(new MobEffectInstance(MobEffects.GLOWING, SENSE_TICKS, 0, false, false, true));
            mobIds.add(living.getUUID());
        }

        Sense sense = new Sense(level, player.getUUID(), now + SENSE_TICKS, ores, mobIds);
        SENSES.put(player.getUUID(), sense);
        // 先把扫描结果发给施法者：客户端马上就能画出穿墙轮廓，不必等粒子逐个点亮。
        ManaSensePayload.send(player, ores, mobIds, SENSE_TICKS);
        // 粒子只作为点缀，继续轮流点亮附近的一部分矿。
        markOres(level, player, sense, true);

        Vec3 at = player.position().add(0, 1.0, 0);
        level.sendParticles(dust(ARCANE_COLOR), at.x, at.y, at.z, 42, 3.2, 1.4, 3.2, 0.05);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 12, 2.4, 1.0, 2.4, 0.03);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                1.0F, 1.45F);
        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS,
                0.85F, 0.9F);
        NewSpellSystem.visual(player, SpellDefinition.MANA_SENSE, at, at.add(0, 1.2, 0), -1, 30);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.mana_sense_cast",
                ores.size(), mobIds.size(), SENSE_TICKS / 20), true);
        SpellSyncPayload.send(player);
    }

    /** 扫描一个区块里的各级魔晶矿与特级魔晶簇；空区块靠调色板预筛，不会真的逐格遍历。 */
    private static List<BlockPos> scanOres(ServerLevel level, ChunkPos chunk) {
        List<BlockPos> found = new ArrayList<>();
        LevelChunk column = level.getChunk(chunk.x, chunk.z);
        LevelChunkSection[] sections = column.getSections();
        for (int index = 0; index < sections.length && found.size() < SENSE_MAX_ORES; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()) continue;
            if (!section.maybeHas(ArcaneSpells::highManaBlock)) continue;
            int baseY = level.getSectionYFromSectionIndex(index) * 16;
            for (int y = 0; y < 16 && found.size() < SENSE_MAX_ORES; y++) {
                for (int x = 0; x < 16 && found.size() < SENSE_MAX_ORES; x++) {
                    for (int z = 0; z < 16 && found.size() < SENSE_MAX_ORES; z++) {
                        if (!highManaBlock(section.getBlockState(x, y, z))) continue;
                        found.add(new BlockPos(chunk.getMinBlockX() + x, baseY + y, chunk.getMinBlockZ() + z));
                    }
                }
            }
        }
        return found;
    }

    /** 高浓度魔素方块：下级～极品的魔晶矿，以及特级魔晶簇。 */
    private static boolean highManaBlock(BlockState state) {
        String id = state.getBlock().builtInRegistryHolder().key().location().toString();
        if (!id.startsWith(GeneratedMod.MOD_ID + ":")) return false;
        return id.endsWith("_mana_crystal_ore")
                || id.equals(GeneratedMod.MOD_ID + ":special_mana_crystal_cluster");
    }

    /**
     * 给施法者标注矿物：只在看得见的一圈里轮流点亮，避免一次把九十多个方块全发一遍。
     */
    private static void markOres(ServerLevel level, ServerPlayer owner, Sense sense, boolean all) {
        DustParticleOptions dust = dust(0xFFF0C0);
        int budget = all ? SENSE_MAX_ORES : SENSE_PARTICLE_BUDGET;
        int sent = 0;
        Vec3 eye = owner.position();
        for (int step = 0; step < sense.ores.size() && sent < budget; step++) {
            BlockPos pos = sense.ores.get(sense.cursor % sense.ores.size());
            sense.cursor++;
            Vec3 at = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (!all && at.distanceToSqr(eye) > SENSE_PARTICLE_RANGE * SENSE_PARTICLE_RANGE) continue;
            level.sendParticles(owner, dust, true, at.x, at.y, at.z, 2, 0.06, 0.06, 0.06, 0);
            level.sendParticles(owner, ParticleTypes.END_ROD, true, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            sent++;
        }
    }

    // ───────────────────────── 魔素潮汐 ─────────────────────────

    /** 前摇结束：在脚下放下混乱魔力锚点，16 秒内不定时朝四周发射魔素脉冲。 */
    public static void castManaTide(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        TIDES.remove(player.getUUID());
        Vec3 center = NewSpellSystem.ground(level, player.position()).add(0, TIDE_ANCHOR_HEIGHT, 0);
        TIDES.put(player.getUUID(), new Tide(level, player.getUUID(), center, now));

        level.sendParticles(dust(ARCANE_COLOR), center.x, center.y + 0.2, center.z, 48, 1.2, 0.35, 1.2, 0.06);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y + 0.9, center.z,
                30, 0.7, 0.9, 0.7, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                1.15F, 0.62F);
        level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.55F, 1.4F);
        NewSpellSystem.visual(player, SpellDefinition.MANA_TIDE, center, center.add(0, 1.4, 0), -1, 40);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.mana_tide_cast",
                (int)TIDE_RADIUS, TIDE_TICKS / 20), true);
        SpellSyncPayload.send(player);
    }

    /** 锚点每刻维护：环境特效、脉冲计时与失效回收都在这里。 */
    private static void tickTide(ServerLevel level, long now) {
        Iterator<Map.Entry<UUID, Tide>> iterator = TIDES.entrySet().iterator();
        while (iterator.hasNext()) {
            Tide tide = iterator.next().getValue();
            if (tide.level != level) continue;
            if (now >= tide.until) {
                iterator.remove();
                endTide(level, tide, true);
                continue;
            }
            ServerLevel server = tide.level;
            if (now % 4 == 0) {
                double angle = now * 0.16;
                level.sendParticles(dust(ARCANE_COLOR), tide.center.x + Math.cos(angle) * 1.15,
                        tide.center.y + 0.15, tide.center.z + Math.sin(angle) * 1.15, 3, 0.12, 0.12, 0.12, 0.01);
                level.sendParticles(dust(0x6B3FA8), tide.center.x - Math.cos(angle) * 0.8,
                        tide.center.y + 0.35, tide.center.z - Math.sin(angle) * 0.8, 2, 0.1, 0.1, 0.1, 0.01);
                level.sendParticles(ParticleTypes.PORTAL, tide.center.x, tide.center.y + 0.5, tide.center.z,
                        2, 0.35, 0.35, 0.35, 0.02);
            }
            if (now < tide.nextPulse) continue;
            tide.nextPulse = now + TIDE_MIN_PULSE + server.getRandom().nextInt(TIDE_MAX_PULSE - TIDE_MIN_PULSE + 1);
            pulse(level, tide, now);
        }
    }

    /** 一次波动：定下这一次的元素，结算半径内所有敌人，再放对应元素的光效与音效。 */
    private static void pulse(ServerLevel level, Tide tide, long now) {
        ElementType element;
        if (tide.forceArcane) {
            // 上一发是非奥术元素，这一发必定是奥术，打完重新掷骰。
            element = null;
            tide.forceArcane = false;
        } else {
            ElementType[] pool = ElementType.values();
            int roll = level.getRandom().nextInt(pool.length + 1);
            element = roll == 0 ? null : pool[roll - 1];
            // 除奥术以外的元素结算完之后，下一次必定是奥术伤害。
            tide.forceArcane = element != null;
        }
        tide.pulses++;

        ServerPlayer owner = level.getServer() == null ? null
                : level.getServer().getPlayerList().getPlayer(tide.owner);
        if (owner != null) {
            for (LivingEntity victim : NewSpellSystem.enemies(owner, tide.center, TIDE_RADIUS)) {
                NewSpellSystem.damage(owner, victim, TIDE_DAMAGE, element);
                Vec3 at = victim.getBoundingBox().getCenter();
                splash(level, at, element);
            }
        }
        wave(level, tide.center, element);
        playElementSound(level, tide.center, element, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.25F);
    }

    /** 脉冲波纹：三圈由近到远，颜色跟着这一次的元素走，奥术用紫色。 */
    private static void wave(ServerLevel level, Vec3 center, ElementType element) {
        int color = element == null ? ARCANE_COLOR : element.color() & 0xFFFFFF;
        DustParticleOptions dust = dust(color);
        double[] radii = {5.0, 10.0, TIDE_RADIUS};
        for (double radius : radii) {
            level.sendParticles(dust, center.x, center.y + 0.2, center.z, (int)Math.round(radius * 2.2),
                    radius, 0.22, radius, 0.0);
        }
        if (element == null) {
            level.sendParticles(ParticleTypes.ENCHANT, center.x, center.y + 0.9, center.z,
                    26, TIDE_RADIUS * 0.5, 0.5, TIDE_RADIUS * 0.5, 0.35);
            level.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 1.2, center.z,
                    14, TIDE_RADIUS * 0.45, 0.4, TIDE_RADIUS * 0.45, 0.12);
        }
    }

    /** 命中单个生物的元素光效：每种元素换一套粒子，看起来才不是同一发法术。 */
    private static void splash(ServerLevel level, Vec3 at, ElementType element) {
        if (element == null) {
            level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 12, 0.45, 0.55, 0.45, 0.5);
            level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, 8, 0.4, 0.5, 0.4, 0.1);
            return;
        }
        switch (element) {
            case FIRE -> {
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 10, 0.4, 0.5, 0.4, 0.03);
                level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 3, 0.25, 0.25, 0.25, 0.0);
            }
            case WATER -> level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 16, 0.45, 0.55, 0.45, 0.12);
            case ICE -> {
                level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 14, 0.45, 0.6, 0.45, 0.02);
                level.sendParticles(ParticleTypes.ITEM_SNOWBALL, at.x, at.y, at.z, 6, 0.35, 0.45, 0.35, 0.05);
            }
            case LIGHTNING -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z,
                    16, 0.45, 0.6, 0.45, 0.12);
            case WIND -> {
                level.sendParticles(ParticleTypes.GUST, at.x, at.y, at.z, 3, 0.35, 0.45, 0.35, 0.05);
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.3, 0.35, 0.3, 0.0);
            }
            case EARTH -> level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 12, 0.45, 0.45, 0.45, 0.03);
            case NATURE -> level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z,
                    12, 0.5, 0.55, 0.5, 0.0);
            case METAL -> level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.45, 0.55, 0.45, 0.18);
            case LIGHT -> level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 12, 0.45, 0.55, 0.45, 0.06);
            case POISON -> level.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z,
                    10, 0.4, 0.5, 0.4, 0.06);
            case SOUND -> level.sendParticles(ParticleTypes.NOTE, at.x, at.y, at.z, 12, 0.4, 0.5, 0.4, 1.0);
            default -> level.sendParticles(dust(element.color() & 0xFFFFFF), at.x, at.y, at.z,
                    14, 0.45, 0.55, 0.45, 0.03);
        }
    }

    /** 每种元素一句不同的音效，混沌感来自“同一发法术每次听起来都不一样”。 */
    public static void playElementSound(ServerLevel level, Vec3 at, ElementType element, float volume, float pitch) {
        if (element == null) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                    volume * 0.9F, pitch * 1.25F);
            return;
        }
        switch (element) {
            case FIRE -> level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT,
                    SoundSource.PLAYERS, volume * 0.8F, pitch);
            case WATER -> level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH,
                    SoundSource.PLAYERS, volume * 0.8F, pitch);
            case ICE -> level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK,
                    SoundSource.PLAYERS, volume * 0.7F, pitch * 1.35F);
            case LIGHTNING -> level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT,
                    SoundSource.PLAYERS, volume * 0.45F, pitch * 1.3F);
            case WIND -> level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_SHOOT,
                    SoundSource.PLAYERS, volume * 0.9F, pitch);
            case EARTH -> level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_HIT,
                    SoundSource.PLAYERS, volume * 0.95F, pitch * 0.85F);
            case NATURE -> level.playSound(null, at.x, at.y, at.z, SoundEvents.GRASS_BREAK,
                    SoundSource.PLAYERS, volume * 0.9F, pitch * 1.1F);
            case METAL -> level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND,
                    SoundSource.PLAYERS, volume * 0.5F, pitch * 1.55F);
            case LIGHT -> level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, volume * 0.85F, pitch * 1.4F);
            case POISON -> level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH,
                    SoundSource.PLAYERS, volume * 0.9F, pitch * 0.95F);
            case SOUND -> level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_HARP.value(),
                    SoundSource.PLAYERS, volume, pitch * 1.2F);
            default -> level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, volume * 0.8F, pitch);
        }
    }

    private static void endTide(ServerLevel level, Tide tide, boolean notify) {
        level.sendParticles(dust(ARCANE_COLOR), tide.center.x, tide.center.y + 0.4, tide.center.z,
                40, 1.0, 0.5, 1.0, 0.05);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, tide.center.x, tide.center.y + 0.6, tide.center.z,
                20, 0.8, 0.6, 0.8, 0.04);
        level.playSound(null, tide.center.x, tide.center.y, tide.center.z, SoundEvents.AMETHYST_BLOCK_FALL,
                SoundSource.PLAYERS, 0.85F, 1.1F);
        if (!notify || level.getServer() == null) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(tide.owner);
        if (owner == null) return;
        owner.displayClientMessage(Component.translatable("message.dimensional_mana.mana_tide_end",
                tide.pulses), true);
        SpellSyncPayload.send(owner);
    }

    // ───────────────────────── 调度 ─────────────────────────

    /** 每刻由 {@link NewSpellSystem#tickLevel} 调用，按世界统一推进感知与锚点。 */
    public static void tickLevel(ServerLevel level, long now) {
        tickSenses(level, now);
        tickTide(level, now);
    }

    private static void tickSenses(ServerLevel level, long now) {
        Iterator<Map.Entry<UUID, Sense>> iterator = SENSES.entrySet().iterator();
        while (iterator.hasNext()) {
            Sense sense = iterator.next().getValue();
            if (sense.level != level) continue;
            if (now >= sense.until) {
                iterator.remove();
                continue;
            }
            ServerPlayer owner = level.getServer() == null ? null
                    : level.getServer().getPlayerList().getPlayer(sense.owner);
            if (owner == null || !owner.isAlive() || owner.level() != level) continue;
            // 每隔一秒补发一次剩余时长，客户端那边就算清过状态也会马上重新画出轮廓。
            if (now % SENSE_RESYNC_INTERVAL == 0) {
                ManaSensePayload.send(owner, sense.ores, sense.mobs, (int)Math.max(1L, sense.until - now));
            }
            if (now % SENSE_PARTICLE_INTERVAL != 0) continue;
            markOres(level, owner, sense, false);
        }
    }

    /** 魔素感知剩余时长（刻），供 HUD 显示。 */
    public static int senseTicks(ServerPlayer player) {
        Sense sense = SENSES.get(player.getUUID());
        if (sense == null || sense.level != player.serverLevel()) return 0;
        return (int)Math.max(0L, sense.until - player.level().getGameTime());
    }

    /** 魔素潮汐锚点剩余时长（刻），供 HUD 显示。 */
    public static int tideTicks(ServerPlayer player) {
        Tide tide = TIDES.get(player.getUUID());
        if (tide == null || tide.level != player.serverLevel()) return 0;
        return (int)Math.max(0L, tide.until - player.level().getGameTime());
    }

    /** 下线、死亡或换维度：锚点与标注一起收掉，不在世界里留下孤儿状态。 */
    public static void clear(ServerPlayer player) {
        Tide tide = TIDES.remove(player.getUUID());
        // 下线、死亡与换维度时只收掉锚点，不再往已经断开的连接里发提示。
        if (tide != null && tide.level == player.serverLevel()) endTide(tide.level, tide, false);
        SENSES.remove(player.getUUID());
    }

    public static void clearAll() {
        SENSES.clear();
        TIDES.clear();
    }

    private static DustParticleOptions dust(int rgb) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F,
                ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), 1.0F);
    }

    private ArcaneSpells() { }
}
