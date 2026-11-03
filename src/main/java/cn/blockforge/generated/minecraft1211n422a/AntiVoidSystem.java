package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/** 反虚空领域的维度钥匙、仪式流程、门、封印巡检和膜波纹。 */
public final class AntiVoidSystem {
    public static final ResourceKey<Level> ANTI_VOID = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "anti_void"));

    public static final int MEMBRANE_Y = 96;
    public static final int FLOOR_Y = 97;
    public static final int PORTAL_Z = 100;
    private static final int GATE_Z = 24;
    private static final int RED_GATE_X = -6;
    private static final int BLUE_GATE_X = 6;

    private static final String INTRO_DONE = "AntiVoidIntroDone";
    private static final String INTRO_PENDING = "AntiVoidIntroPending";
    private static final String GATE_PENDING = "AntiVoidGatePending";
    private static final String GATE_ARM_TICK = "AntiVoidGateArmTick";
    private static final String DEATH_POS = "AntiVoidDeath";
    private static final String LAST_RIPPLE = "AntiVoidLastRipple";
    private static final String GATE_INSIDE = "AntiVoidGateInside";
    private static final String WAS_GROUNDED = "AntiVoidWasGrounded";
    private static final String AIR_FALL = "AntiVoidAirFall";
    /** 音效排队：换维度那一刻客户端还在读图，直接发包会被丢掉，所以等落地再放。 */
    private static final String SOUND_QUEUE = "AntiVoidSoundQueue";
    private static final String SOUND_DELAY = "AntiVoidSoundDelay";
    private static final String SOUND_WAIT = "AntiVoidSoundWait";
    /** 玩家此刻是否身处反虚空（用来判定「离开」这个边沿）。 */
    private static final String PRESENT = "AntiVoidPresent";
    private static final int SOUND_ARRIVE = 1;
    private static final int SOUND_RETURN = 2;
    private static final int SOUND_LEAVE = 3;
    /** 排队音效最多等多久（刻）：15 秒内场景还没加载完就放弃，免得下次进维度才响。 */
    private static final int SOUND_MAX_WAIT = 300;

    /**
     * r291：膜面水波粒子的登记挪到了 {@link GeneratedMod#MEMBRANE_RIPPLE}。
     * 这里（以及本类任何静态字段）都不能再调 DeferredRegister.register——
     * 本类是游戏运行期才首次加载的，那时注册窗口早已关闭，会直接崩溃。
     * 发送粒子时统一引用 GeneratedMod.MEMBRANE_RIPPLE。
     */

    private AntiVoidSystem() {}

    public static boolean introQueued(ServerPlayer player) {
        var data = player.getPersistentData();
        return !data.getBoolean(INTRO_DONE) && data.getInt(INTRO_PENDING) > 0;
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var data = player.getPersistentData();
        if (!data.getBoolean(INTRO_DONE)) data.putInt(INTRO_PENDING, 1);
    }

    /** 登录后延迟一刻再跨维度，避免刚建立连接时客户端还没准备好接收换维度包。 */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var data = player.getPersistentData();
            if (!data.getBoolean(INTRO_DONE) && data.getInt(INTRO_PENDING) == 1) {
                ServerLevel anti = server.getLevel(ANTI_VOID);
                if (anti == null) {
                    // 数据包被禁用时不能把新玩家困在“待传送”状态，保底发书。
                    giveMissingBooks(player);
                    data.putBoolean(INTRO_DONE, true);
                    data.putInt(INTRO_PENDING, 0);
                } else {
                    player.teleportTo(anti, 0.5D, FLOOR_Y, 0.5D, 0.0F, 0.0F);
                    data.putInt(INTRO_PENDING, 2);
                    setupIntroArrival(player, anti);
                }
            }
            updatePresenceSound(player, data);
            tickQueuedSound(player);
        }
    }

    /**
     * 进出反虚空的音效：进入的两个入口自己排队（初次降临 / 死后回到门厅），
     * 离开则靠「上一刻还在反虚空、这一刻不在了」这个边沿统一处理——
     * 不管玩家是从引导门、归乡之门、回溯之门还是指令走的，都会响一声。
     */
    private static void updatePresenceSound(ServerPlayer player, net.minecraft.nbt.CompoundTag data) {
        boolean inside = player.level().dimension().equals(ANTI_VOID);
        if (inside == data.getBoolean(PRESENT)) return;
        data.putBoolean(PRESENT, inside);
        if (!inside) queueSound(player, SOUND_LEAVE, 4);
    }

    /** 排一个音效：延迟 delayTicks 刻后开始尝试，直到玩家真的站在目标维度里再放。 */
    private static void queueSound(ServerPlayer player, int which, int delayTicks) {
        var data = player.getPersistentData();
        data.putInt(SOUND_QUEUE, which);
        data.putInt(SOUND_DELAY, Math.max(0, delayTicks));
        data.putInt(SOUND_WAIT, 0);
    }

    private static void tickQueuedSound(ServerPlayer player) {
        var data = player.getPersistentData();
        int which = data.getInt(SOUND_QUEUE);
        if (which <= 0) return;
        int delay = data.getInt(SOUND_DELAY);
        if (delay > 0) {
            data.putInt(SOUND_DELAY, delay - 1);
            return;
        }
        boolean inside = player.level().dimension().equals(ANTI_VOID);
        boolean ready = which == SOUND_LEAVE ? !inside : inside;
        if (!ready) {
            int waited = data.getInt(SOUND_WAIT);
            if (waited >= SOUND_MAX_WAIT) {
                data.remove(SOUND_QUEUE);
                data.remove(SOUND_WAIT);
                return;
            }
            data.putInt(SOUND_WAIT, waited + 1);
            return;
        }
        data.remove(SOUND_QUEUE);
        data.remove(SOUND_WAIT);
        data.remove(SOUND_DELAY);
        SoundEvent sound = switch (which) {
            case SOUND_ARRIVE -> GeneratedMod.ANTI_VOID_ARRIVE.get();
            case SOUND_RETURN -> GeneratedMod.ANTI_VOID_RETURN.get();
            case SOUND_LEAVE -> GeneratedMod.ANTI_VOID_LEAVE.get();
            default -> null;
        };
        if (sound != null) {
            player.playNotifySound(sound, SoundSource.AMBIENT, 1.0F, 1.0F);
        }
    }

    public static void onRespawnPosition(PlayerRespawnPositionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var data = player.getPersistentData();
        if (data.getInt(GATE_PENDING) <= 0 || event.isFromEndFight()) return;
        ServerLevel anti = player.server.getLevel(ANTI_VOID);
        if (anti == null) return;
        event.setDimensionTransition(new DimensionTransition(anti,
                new Vec3(0.5D, FLOOR_Y, 0.5D), Vec3.ZERO, 0.0F, 0.0F,
                DimensionTransition.DO_NOTHING));
        // 死后回到门厅：等落地再放「重返反虚空」的史诗音效。
        queueSound(player, SOUND_RETURN, 20);
        // 保留原来的床/主城绑定点，归乡之门稍后仍能把玩家送回去。
        event.setCopyOriginalSpawnPosition(true);
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().dimension().equals(ANTI_VOID)) return;

        DamageSource source = event.getSource();
        var death = new net.minecraft.nbt.CompoundTag();
        death.putString("Dimension", player.level().dimension().location().toString());
        death.putDouble("X", player.getX());
        death.putDouble("Y", player.getY());
        death.putDouble("Z", player.getZ());
        death.putBoolean("Hazard", source.is(DamageTypes.FELL_OUT_OF_WORLD)
                || source.is(DamageTypes.LAVA) || source.is(DamageTypes.HOT_FLOOR));
        player.getPersistentData().put(DEATH_POS, death);
        player.getPersistentData().putInt(GATE_PENDING, 1);
        player.getPersistentData().remove(GATE_ARM_TICK);
    }

    /** PlayerList.respawn 会换一个玩家实体，补拷贝反虚空自己的顶层标签。 */
    public static void onClone(PlayerEvent.Clone event) {
        var oldData = event.getOriginal().getPersistentData();
        var newData = event.getEntity().getPersistentData();
        for (String key : oldData.getAllKeys()) {
            if (key.startsWith("AntiVoid")) newData.put(key, oldData.get(key).copy());
        }
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level) || !level.dimension().equals(ANTI_VOID)) return;

        var data = player.getPersistentData();
        if (data.getInt(INTRO_PENDING) == 2) {
            ensureIntroPortal(level);
            if (insideIntroPortal(player)) completeIntro(player, level);
        }
        if (data.getInt(GATE_PENDING) > 0) {
            // 初次引导还没走完时不清星之间隙（引导每刻都要重建它），其余情况门一出现就撤掉远处的旧引导门。
            if (data.getInt(INTRO_PENDING) != 2) clearDistantAstralRift(level);
            ensureDeathGates(level);
            if (level.getGameTime() % 30L == Math.floorMod(player.getId(), 30)) {
                gateParticles(level);
            }
            // 走出门幕后清掉"在门里"标记，这样再走进去能重新触发一次。
            if (data.getBoolean(GATE_INSIDE) && !insideDeathGate(player)) {
                data.putBoolean(GATE_INSIDE, false);
            }
        }

        boolean grounded = player.onGround();
        boolean wasGrounded = data.getBoolean(WAS_GROUNDED);
        float airFall = data.getFloat(AIR_FALL);
        if (!grounded) {
            data.putFloat(AIR_FALL, Math.max(airFall, player.fallDistance));
        } else {
            if (isMembraneBelow(level, player.blockPosition())) {
                if (!wasGrounded && airFall >= 0.75F) {
                    membraneLanding(level, player);
                } else {
                    double horizontalSpeed = player.getDeltaMovement().horizontalDistance();
                    long interval = horizontalSpeed > 0.12D ? 5L : 8L;
                    if (horizontalSpeed > 0.035D
                            && level.getGameTime() - data.getLong(LAST_RIPPLE) >= interval) {
                        data.putLong(LAST_RIPPLE, level.getGameTime());
                        membraneStep(level, player);
                    }
                }
            }
            data.putFloat(AIR_FALL, 0.0F);
        }
        data.putBoolean(WAS_GROUNDED, grounded);

        if (level.getGameTime() % 60L == Math.floorMod(player.getId(), 60)) {
            repairNearbySeals(level, player);
        }
    }

    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        if (mob.level().dimension().equals(ANTI_VOID)) event.setSpawnCancelled(true);
    }

    /**
     * r290：反空虚无生物的最后一道闸。{@link #onFinalizeSpawn} 只管得住走正常生成流程的生物，
     * 而 MOD 自己 {@code addFreshEntity} 的旁路（比如按区块兜底刷元素史莱姆）会绕过它——
     * 所以在“实体进入世界”这一刻把反虚空里的一切生物直接拦下。
     */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof Mob)) return;
        if (event.getLevel().dimension().equals(ANTI_VOID)) event.setCanceled(true);
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        BlockState state = event.getState();
        Block anti = block("anti_void_block");
        Block membrane = block("membrane");
        Block frame = block("astral_rift_frame");
        Player player = event.getPlayer();
        if (state.is(anti)) {
            event.setCanceled(true);
            return;
        }
        if (state.is(membrane) && !player.isCreative()) {
            event.setCanceled(true);
            return;
        }
        if (state.is(frame) && !player.isCreative()) {
            ItemStack stack = player.getMainHandItem();
            boolean netheritePick = stack.getItem() instanceof PickaxeItem
                    && stack.getItem() instanceof TieredItem tiered
                    && tiered.getTier() == Tiers.NETHERITE;
            if (!netheritePick) event.setCanceled(true);
        }
        if (state.is(block("rift_gate_crimson")) || state.is(block("rift_gate_azure"))) {
            event.setCanceled(true);
        }
    }

    /**
     * r294：身体走进门幕即触发。用"进出边沿"而不是每刻重复触发——
     * 站在门里只会算一次，要再触发必须先退出来再走进去。
     */
    public static void onGateContact(ServerPlayer player, BlockPos pos, RiftGateBlock.Kind kind) {
        if (!(player.level() instanceof ServerLevel level)) return;
        var data = player.getPersistentData();
        if (data.getBoolean(GATE_INSIDE)) return;
        data.putBoolean(GATE_INSIDE, true);
        if (data.getInt(GATE_PENDING) <= 0) return;
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.9F,
                kind == RiftGateBlock.Kind.CRIMSON ? 0.85F : 1.25F);
        onGateUse(player, pos, kind);
    }

    /** 玩家碰撞盒是否还压着任何一扇门板（与 entityInside 的判定范围一致）。 */
    private static boolean insideDeathGate(ServerPlayer player) {
        AABB box = player.getBoundingBox();
        for (int centerX : new int[]{RED_GATE_X, BLUE_GATE_X}) {
            AABB gate = new AABB(centerX - 2.0D, FLOOR_Y, GATE_Z,
                    centerX + 3.0D, FLOOR_Y + 6.0D, GATE_Z + 1.0D);
            if (box.intersects(gate)) return true;
        }
        return false;
    }

    public static void onGateUse(ServerPlayer player, BlockPos pos, RiftGateBlock.Kind kind) {
        if (!(player.level() instanceof ServerLevel level)) return;
        var data = player.getPersistentData();
        if (data.getInt(GATE_PENDING) <= 0) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.gate_silent"), true);
            return;
        }
        if (kind == RiftGateBlock.Kind.CRIMSON) {
            long now = level.getGameTime();
            long armed = data.getLong(GATE_ARM_TICK);
            if (armed == 0L || now - armed > 200L) {
                data.putLong(GATE_ARM_TICK, now);
                player.displayClientMessage(Component.translatable("message.dimensional_mana.gate_confirm"), false);
                return;
            }
            useRetrospectionGate(player, level, pos);
        } else {
            useHomecomingGate(player, level, pos);
        }
    }

    private static void useRetrospectionGate(ServerPlayer player, ServerLevel level, BlockPos gatePos) {
        var death = player.getPersistentData().getCompound(DEATH_POS);
        ServerLevel target = levelFor(player.server, death.getString("Dimension"));
        if (target == null) target = player.server.getLevel(Level.OVERWORLD);
        if (target == null) return;

        double x = death.contains("X") ? death.getDouble("X") : target.getSharedSpawnPos().getX() + 0.5D;
        double y = death.contains("Y") ? death.getDouble("Y") : target.getSharedSpawnPos().getY();
        double z = death.contains("Z") ? death.getDouble("Z") : target.getSharedSpawnPos().getZ() + 0.5D;
        boolean hazard = death.getBoolean("Hazard");
        int safeY = findSafeY(target, (int) Math.floor(x), (int) Math.floor(z),
                (int) Math.floor(y) + (hazard ? 5 : 0), hazard);
        if (safeY == Integer.MIN_VALUE) {
            target = player.server.getLevel(Level.OVERWORLD);
            BlockPos spawn = target == null ? BlockPos.ZERO : target.getSharedSpawnPos();
            if (target != null) player.teleportTo(target, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
            player.displayClientMessage(Component.translatable("message.dimensional_mana.gate_no_foothold"), false);
        } else {
            player.teleportTo(target, x, safeY, z, player.getYRot(), player.getXRot());
        }
        DeathLawPenalty.applyLawDebt(player);
        finishGate(player, level, gatePos);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.gate_retrospect"), false);
    }

    private static void useHomecomingGate(ServerPlayer player, ServerLevel level, BlockPos gatePos) {
        ResourceKey<Level> respawnDimension = player.getRespawnDimension();
        ServerLevel target = respawnDimension == null ? player.server.getLevel(Level.OVERWORLD)
                : player.server.getLevel(respawnDimension);
        if (target == null) target = player.server.getLevel(Level.OVERWORLD);
        if (target == null) return;
        BlockPos spawn = player.getRespawnPosition();
        if (spawn == null || !respawnDimension.equals(target.dimension())) spawn = target.getSharedSpawnPos();
        player.teleportTo(target, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                player.getRespawnAngle(), 0.0F);
        finishGate(player, level, gatePos);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.gate_return_home"), false);
    }

    private static void finishGate(ServerPlayer player, ServerLevel level, BlockPos gatePos) {
        var data = player.getPersistentData();
        data.putInt(GATE_PENDING, 0);
        data.remove(GATE_ARM_TICK);
        data.putBoolean(GATE_INSIDE, false);
        removeDeathGates(level);
        streamEffect(level, gatePos.getX() + 0.5D, gatePos.getY() + 1.0D, gatePos.getZ() + 0.5D);
    }

    /** 门选完就关：把两扇重生门全部拆掉，下次死亡时 ensureDeathGates 会重新铺。 */
    private static void removeDeathGates(ServerLevel level) {
        Block crimson = block("rift_gate_crimson");
        Block azure = block("rift_gate_azure");
        for (int centerX : new int[]{RED_GATE_X, BLUE_GATE_X}) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = 0; dy <= 7; dy++) {
                    BlockPos pos = new BlockPos(centerX + dx, FLOOR_Y + dy, GATE_Z);
                    BlockState cur = level.getBlockState(pos);
                    if (cur.is(crimson) || cur.is(azure)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    private static void completeIntro(ServerPlayer player, ServerLevel level) {
        var data = player.getPersistentData();
        data.putBoolean(INTRO_DONE, true);
        data.putInt(INTRO_PENDING, 0);
        giveMissingBooks(player);
        streamEffect(level, player.getX(), player.getY() + 1.0D, player.getZ());
        ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 0.0F, 0.0F);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.anti_void_departed"), false);
    }

    private static void setupIntroArrival(ServerPlayer player, ServerLevel level) {
        ensureIntroPortal(level);
        if (!ArcaneCodexItem.hasAccess(player)) spawnBook(level, player, "arcane_codex", -0.65D);
        if (!CombatCodexItem.hasAccess(player)) spawnBook(level, player, CombatCodexItem.ITEM_ID, 0.65D);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.dimensional_mana.anti_void_title")));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.dimensional_mana.anti_void_subtitle")));
        player.displayClientMessage(Component.translatable("message.dimensional_mana.anti_void_hint"), false);
        // 第一次降临：等标题和场景都就位，再放最长的那段史诗音效。
        queueSound(player, SOUND_ARRIVE, 20);
    }

    private static void spawnBook(ServerLevel level, ServerPlayer player, String id, double xOffset) {
        var holder = GeneratedMod.MOD_ITEMS.get(id);
        if (holder == null) return;
        ItemEntity item = new ItemEntity(level, player.getX() + xOffset, FLOOR_Y + 0.7D,
                player.getZ() + 2.5D, new ItemStack(holder.get()));
        item.setNoGravity(true);
        item.setPickUpDelay(0);
        item.lifespan = 24000;
        item.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(item);
    }

    private static void giveMissingBooks(ServerPlayer player) {
        if (!ArcaneCodexItem.hasAccess(player)) {
            ItemStack book = new ItemStack(GeneratedMod.MOD_ITEMS.get("arcane_codex").get());
            if (!player.getInventory().add(book)) player.drop(book, false);
        }
        if (!CombatCodexItem.hasAccess(player)) {
            ItemStack book = new ItemStack(GeneratedMod.MOD_ITEMS.get(CombatCodexItem.ITEM_ID).get());
            if (!player.getInventory().add(book)) player.drop(book, false);
        }
    }

    private static boolean insideIntroPortal(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        return pos.getZ() == PORTAL_Z && pos.getX() >= -1 && pos.getX() <= 1
                && pos.getY() >= FLOOR_Y && pos.getY() <= FLOOR_Y + 3;
    }

    private static void ensureIntroPortal(ServerLevel level) {
        // r290：星之间隙不再摆空心门框——它本身就是门。3×4 的发光门板直接立在 z=100 上，
        // 方块实体无碰撞，人走进去（insideIntroPortal 判定）即完成仪式。
        Block door = block("astral_rift_frame");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 3; dy++) {
                level.setBlock(new BlockPos(dx, FLOOR_Y + dy, PORTAL_Z), door.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static void ensureDeathGates(ServerLevel level) {
        // 门板已经铺好时不再每刻重放 setBlock（会连锁触发光照/封顶更新）。
        if (!isGateBuilt(level, RED_GATE_X, RiftGateBlock.Kind.CRIMSON)) {
            buildGate(level, RED_GATE_X, RiftGateBlock.Kind.CRIMSON);
        }
        if (!isGateBuilt(level, BLUE_GATE_X, RiftGateBlock.Kind.AZURE)) {
            buildGate(level, BLUE_GATE_X, RiftGateBlock.Kind.AZURE);
        }
    }

    private static boolean isGateBuilt(ServerLevel level, int centerX, RiftGateBlock.Kind kind) {
        Block door = block(kind == RiftGateBlock.Kind.CRIMSON ? "rift_gate_crimson" : "rift_gate_azure");
        return level.getBlockState(new BlockPos(centerX, FLOOR_Y, GATE_Z)).is(door);
    }

    private static void clearDistantAstralRift(ServerLevel level) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 3; dy++) {
                BlockPos pos = new BlockPos(dx, FLOOR_Y + dy, PORTAL_Z);
                if (level.getBlockState(pos).is(block("astral_rift_frame"))) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static void buildGate(ServerLevel level, int centerX, RiftGateBlock.Kind kind) {
        Block door = block(kind == RiftGateBlock.Kind.CRIMSON ? "rift_gate_crimson" : "rift_gate_azure");
        // r294：门板贴地（从 FLOOR_Y 起 5×6），走进去就能碰上门幕；
        // 旧版悬浮在 FLOOR_Y+2 以上，玩家从底下穿过去什么都碰不到。先清掉整段门位区间再铺新板。
        Block other = block(kind == RiftGateBlock.Kind.CRIMSON ? "rift_gate_azure" : "rift_gate_crimson");
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy <= 7; dy++) {
                BlockPos pos = new BlockPos(centerX + dx, FLOOR_Y + dy, GATE_Z);
                BlockState cur = level.getBlockState(pos);
                if (cur.is(door) || cur.is(other)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy <= 5; dy++) {
                level.setBlock(new BlockPos(centerX + dx, FLOOR_Y + dy, GATE_Z),
                        door.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static void gateParticles(ServerLevel level) {
        level.sendParticles(ParticleTypes.FLAME, RED_GATE_X, FLOOR_Y + 4.5D, GATE_Z - 0.56D,
                10, 1.2D, 1.8D, 0.03D, 0.01D);
        level.sendParticles(ParticleTypes.END_ROD, BLUE_GATE_X, FLOOR_Y + 4.5D, GATE_Z - 0.56D,
                10, 1.2D, 1.8D, 0.03D, 0.01D);
    }

    private static void membraneStep(ServerLevel level, ServerPlayer player) {
        membraneRipple(level, player, false);
        level.playSound(null, player.blockPosition(), SoundEvents.POINTED_DRIPSTONE_DRIP_WATER,
                SoundSource.PLAYERS, 0.42F, 1.15F + level.random.nextFloat() * 0.45F);
    }

    private static void membraneLanding(ServerLevel level, ServerPlayer player) {
        membraneRipple(level, player, true);
        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(),
                SoundSource.PLAYERS, 0.72F, 1.18F + level.random.nextFloat() * 0.5F);
    }

    private static void membraneRipple(ServerLevel level, ServerPlayer player, boolean landing) {
        double x = player.getX();
        double y = MEMBRANE_Y + 1.03D;
        double z = player.getZ();
        level.sendParticles(GeneratedMod.MEMBRANE_RIPPLE.get(), x, y, z,
                landing ? 2 : 1, 0.04D, 0.0D, 0.04D, 0.0D);
        double ox = x + (level.random.nextDouble() - 0.5D) * 0.7D;
        double oz = z + (level.random.nextDouble() - 0.5D) * 0.7D;
        level.sendParticles(GeneratedMod.MEMBRANE_RIPPLE.get(), ox, y, oz, 1,
                0.0D, 0.0D, 0.0D, 0.0D);
        for (int i = 0; i < (landing ? 12 : 8); i++) {
            double angle = Math.PI * 2.0D * i / (landing ? 12 : 8);
            level.sendParticles(ParticleTypes.SPLASH, x + Math.cos(angle) * 0.55D, y + 0.05D,
                    z + Math.sin(angle) * 0.55D, 1, 0.0D, 0.02D, 0.0D, 0.02D);
        }
    }

    private static boolean isMembraneBelow(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet.below()).is(block("membrane"));
    }

    private static void repairNearbySeals(ServerLevel level, ServerPlayer player) {
        Block seal = block("anti_void_block");
        int cx = player.blockPosition().getX();
        int cz = player.blockPosition().getZ();
        int radius = 32;
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                repairSealAt(level, new BlockPos(x, -64, z), seal);
                repairSealAt(level, new BlockPos(x, 256, z), seal);
            }
        }
    }

    private static void repairSealAt(ServerLevel level, BlockPos pos, Block seal) {
        int chunkX = Math.floorDiv(pos.getX(), 16);
        int chunkZ = Math.floorDiv(pos.getZ(), 16);
        if (level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) return;
        if (!level.getBlockState(pos).is(seal)) level.setBlock(pos, seal.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void streamEffect(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 45, 0.7D, 1.0D, 0.7D, 0.06D);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 35, 0.6D, 0.9D, 0.6D, 0.04D);
        level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.AMETHYST_BLOCK_BREAK,
                SoundSource.PLAYERS, 0.65F, 0.8F);
        level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.BELL_RESONATE,
                SoundSource.PLAYERS, 0.45F, 1.15F);
    }

    private static int findSafeY(ServerLevel level, int x, int z, int startY, boolean hazard) {
        int min = level.getMinBuildHeight() + 1;
        int max = level.getMaxBuildHeight() - 2;
        int from = Math.max(min, startY);
        for (int y = from; y <= max; y++) {
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos head = feet.above();
            if (!clear(level, feet) || !clear(level, head)) continue;
            if (!hazard) return y;
            BlockState below = feet.below().getY() < min ? level.getBlockState(feet.below()) : level.getBlockState(feet.below());
            if (!below.isAir() && !below.liquid()) return y;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean clear(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() && !state.liquid() && state.getCollisionShape(level, pos).isEmpty();
    }

    private static ServerLevel levelFor(MinecraftServer server, String id) {
        if (id == null || id.isBlank()) return null;
        try {
            ResourceLocation location = ResourceLocation.parse(id);
            return server.getLevel(ResourceKey.create(Registries.DIMENSION, location));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Block block(String id) {
        return GeneratedMod.MOD_BLOCKS.get(id).get();
    }
}
