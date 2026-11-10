package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.EffectCure;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.Set;

/**
 * 魔蚀（MagicErosion）：魔蚀织网者的蛛网、毒牙与毒液留下的持续侵蚀。
 *
 * <p>发作节奏按等级走：基础每 30 刻咬一口，每升 1 级间隔少 4 刻，最快 2 刻一次。
 * 每次发作造成真实伤害（{@code minecraft:magic}，绕过护甲），并把受击无敌帧
 * 直接清零——所以这一口永远不会被上一口吞掉；同时从目标的 {@link ManaData} 里扣 20 点魔素。</p>
 *
 * <p><b>【深渊侵蚀阶层】</b>（r370 起）：每挨一次魔蚀就多「一层」，层数写在实体自己的持久数据里，
 * 上限 {@value #MAX_LAYERS} 层。基础跳伤 {@value #TICK_DAMAGE} 点，每累计
 * {@value #LAYERS_PER_TIER} 层再 +1 点真实伤害，所以越泡越深、越深越疼
 * （{@value #MAX_LAYERS} 层封顶时为 {@value #MAX_TIER} 阶层）。
 * 玩家和生物一视同仁，生物身上同样会叠层、同样按阶层加伤。</p>
 *
 * <p><b>层数从哪来</b>（r371 修正）：<u>任何</u>把魔蚀挂到实体身上的途径都算一层——
 * 毒牙、蛛网、毒液射线、侵蚀魔药（普通 / 喷溅 / 滞留）、{@code /effect give}，
 * 甚至别的模组直接 {@code addEffect}，统一在 {@link #onEffectApplied} 里记账。
 * r370 只在 {@link #apply(LivingEntity, int)} 里加层，药水这种走原版施加流程的途径
 * 一层都记不上，于是「给生物叠了一身魔蚀，还是每次只掉 2 点」。</p>
 *
 * <p><b>层数什么时候退</b>：状态到期<u>不再</u>把层数清零（那样谁都叠不到 16 层，
 * 阶层永远不涨）；层数留在实体身上，只有在身上没有魔蚀时才会慢慢退，
 * 每 {@value #DECAY_TICKS} 刻退 1 层。想一次洗干净仍然只能靠 {@link #cleanse(LivingEntity)}。</p>
 *
 * <p>为什么层数不直接塞进状态等级：原版的 {@code MobEffectInstance} 等级上限只有 255，
 * 装不下 {@value #MAX_LAYERS} 层，所以状态等级只负责「发作节奏 + 图标上的罗马数字」，
 * 真正的层数单独记，并通过 {@link ErosionDepthPayload} 同步给带着状态的玩家自己
 * （只用来画悬停说明，不参与任何伤害结算）。</p>
 *
 * <p>叠加规则：重复施加刷新持续时间、层数 +1；持续时间传 {@code <= 0} 表示无限时长，
 * 一旦变成无限就不会再被有限的重复施加改回有限。</p>
 *
 * <p>净化限制：本效果是「强制状态」——{@code fillEffectCures} 留空，牛奶、蜂蜜、
 * 不死图腾、{@code /effect clear} 都解不掉（{@link CorruptedWeaverEvents#onEffectRemove}
 * 里还拦了一道）。只有 {@link #cleanse(LivingEntity)} 这条白名单通道能清：
 * 补魔剂、光系净化法术与死亡复活走的就是它。</p>
 */
public final class MagicErosionEffect extends MobEffect {
    /** 基础发作间隔（刻）。 */
    public static final int BASE_INTERVAL = 30;
    /** 每提升 1 级减少的间隔（刻）。 */
    public static final int INTERVAL_STEP = 4;
    /** 最短间隔（刻）。 */
    public static final int MIN_INTERVAL = 2;
    /** 每次发作的基础真实伤害。 */
    public static final float TICK_DAMAGE = 2.0F;
    /** 每累计多少层魔蚀，跳伤额外 +1。 */
    public static final int LAYERS_PER_TIER = 16;
    /** 每提升一个深渊阶层额外的真实伤害。 */
    public static final float DAMAGE_PER_TIER = 1.0F;
    /** 侵蚀层数上限：1048576 层（2 的 20 次方）。 */
    public static final int MAX_LAYERS = 1_048_576;
    /** 阶层上限：{@value #MAX_LAYERS} / {@value #LAYERS_PER_TIER} = 65536 阶层。 */
    public static final int MAX_TIER = MAX_LAYERS / LAYERS_PER_TIER;
    /** 每次发作扣掉的魔素。 */
    public static final double TICK_MANA_DRAIN = 20.0D;
    /** 默认持续时间：10 秒。 */
    public static final int DEFAULT_DURATION = 200;

    /** 层数存在实体持久数据的这个键下（跟着实体存档走，重进世界不会白赚也不会白丢）。 */
    static final String LAYERS_TAG = "DmMagicErosionLayers";
    /** 身上没有魔蚀时，下一次退层的时间点（游戏刻）；有魔蚀时会清掉。 */
    static final String DECAY_AT_TAG = "DmMagicErosionDecayAt";
    /** 没有魔蚀时每多少刻退 1 层：5 秒。 */
    public static final int DECAY_TICKS = 100;

    private static final ParticleOptions EROSION_DUST =
            new DustParticleOptions(new Vector3f(0.55F, 0.16F, 0.78F), 1.1F);
    /** 深渊阶层加深时那一撮「化不开的黑」。 */
    private static final ParticleOptions DEPTH_DUST =
            new DustParticleOptions(new Vector3f(0.12F, 0.02F, 0.18F), 1.35F);

    /** 「这次移除是净化白名单发起的」：只在服务端主线程同步触发，用 ThreadLocal 最省事。 */
    private static final ThreadLocal<Boolean> CLEANSING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public MagicErosionEffect() {
        super(MobEffectCategory.HARMFUL, 0x7B2FBE);
    }

    /** 某一等级下的发作间隔（刻）。 */
    public static int interval(int amplifier) {
        return Math.max(MIN_INTERVAL, BASE_INTERVAL - Math.max(0, amplifier) * INTERVAL_STEP);
    }

    // ---------------------------------------------------------------- 深渊侵蚀阶层
    /**
     * 某一层数对应的深渊侵蚀阶层。
     *
     * <p>1～15 层是 0 阶层（基础 2 点），16～31 层是 1 阶层（3 点），依此类推；
     * {@value #MAX_LAYERS} 层就是 {@value #MAX_TIER} 阶层。</p>
     */
    public static int tierOf(int layers) {
        return Math.min(MAX_TIER, Math.max(0, layers) / LAYERS_PER_TIER);
    }

    /** 某一层数下每次发作的真实伤害。 */
    public static float damageOf(int layers) {
        return TICK_DAMAGE + tierOf(layers) * DAMAGE_PER_TIER;
    }

    /** 距离下一阶层还差几层；已经封顶时返回 0。 */
    public static int layersToNextTier(int layers) {
        int clamped = Math.min(MAX_LAYERS, Math.max(0, layers));
        if (clamped >= MAX_LAYERS) return 0;
        return LAYERS_PER_TIER - clamped % LAYERS_PER_TIER;
    }

    /** 只凭状态等级能推出来的层数：给没有同步值（例如旁观别人的状态）的场合兜底。 */
    public static int layersFromAmplifier(int amplifier) {
        return Math.min(MAX_LAYERS, Math.max(1, amplifier + 1));
    }

    /**
     * 这个实体身上现在叠了多少层魔蚀。
     *
     * <p>返回 0 表示既没中招、也没残留的侵蚀。层数在状态到期后<b>不会</b>立刻消失
     * （只会慢慢退），所以这里优先读实体存档里的值；有状态但还没记过账
     * （{@code /effect give}、药水直接给）时按状态等级折算，至少 1 层。</p>
     */
    public static int layersOf(LivingEntity entity) {
        if (entity == null) return 0;
        int stored = Math.max(0, entity.getPersistentData().getInt(LAYERS_TAG));
        MobEffectInstance instance = entity.getEffect(GeneratedMod.MAGIC_EROSION);
        int fromEffect = instance == null ? 0 : Math.max(1, instance.getAmplifier() + 1);
        return Math.min(MAX_LAYERS, Math.max(stored, fromEffect));
    }

    /** 把层数写回实体存档，并把 0（清空）当成删除。 */
    static void storeLayers(LivingEntity entity, int layers) {
        int clamped = Math.min(MAX_LAYERS, Math.max(0, layers));
        if (clamped == 0) {
            entity.getPersistentData().remove(LAYERS_TAG);
            entity.getPersistentData().remove(DECAY_AT_TAG);
        } else {
            entity.getPersistentData().putInt(LAYERS_TAG, clamped);
        }
    }

    // ---------------------------------------------------------------- 层数记账
    /**
     * 任何来源把魔蚀挂到实体身上，都算它多挨了一次侵蚀：织网者的毒牙 / 蛛网 / 毒液射线、
     * 侵蚀魔药（普通、喷溅、滞留）、{@code /effect give}，乃至别的模组直接 {@code addEffect}。
     *
     * <p>r370 只在 {@link #apply(LivingEntity, int)} 里加层，药水这类走原版施加流程的途径
     * 一层都记不上——给生物灌一背包侵蚀药水，每次发作还是只掉 2 点。现在记账统一挪到
     * 这里：{@code MobEffectEvent.Added} 只在服务端、且只在 {@code canBeAffected}
     * 放行之后才触发，所以「神圣庇护」这类拦下负面状态的情况依然不会白记一层。</p>
     *
     * <p>注意事件是在状态真正写进 {@code activeEffects} <b>之前</b>派发的，所以第一次中招时
     * {@link #layersOf(LivingEntity)} 还是 0，记完账正好 1 层。</p>
     */
    public static void onEffectApplied(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null || !instance.is(GeneratedMod.MAGIC_EROSION)) return;
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide) return;
        addLayers(entity, 1);
    }

    /** 统一记账：层数只增不减，到上限就停；跨过阶层放提示，玩家再同步给客户端画悬停说明。 */
    static void addLayers(LivingEntity entity, int amount) {
        int before = layersOf(entity);
        int after = Math.min(MAX_LAYERS, Math.max(0, before + amount));
        if (after == before) return;
        storeLayers(entity, after);
        if (tierOf(after) > tierOf(before)) onTierUp(entity, after);
        syncLayers(entity, after);
    }

    /**
     * 身上没有魔蚀的时候，侵蚀慢慢退：每 {@value #DECAY_TICKS} 刻退 1 层。
     *
     * <p>层数不再随状态到期清零（见 {@link #onExpired}），但也不能永远挂着，
     * 所以用「不挨魔蚀就慢慢退」的办法：打起来的时候一直有状态，层数照涨；
     * 收手之后一层层退掉。想一次洗干净仍然只能走 {@link #cleanse(LivingEntity)}。</p>
     */
    public static void tickLiving(LivingEntity entity) {
        if (entity.level().isClientSide || !entity.isAlive()) return;
        // 退层不需要逐刻精度，20 刻查一次就够，省掉一堆每刻都跑的哈希/标签读写。
        if (entity.tickCount % 20 != 0) return;
        if (entity.hasEffect(GeneratedMod.MAGIC_EROSION)) {
            if (entity.getPersistentData().contains(DECAY_AT_TAG)) entity.getPersistentData().remove(DECAY_AT_TAG);
            return;
        }
        int layers = entity.getPersistentData().getInt(LAYERS_TAG);
        if (layers <= 0) {
            if (entity.getPersistentData().contains(DECAY_AT_TAG)) entity.getPersistentData().remove(DECAY_AT_TAG);
            return;
        }
        long now = entity.level().getGameTime();
        long at = entity.getPersistentData().getLong(DECAY_AT_TAG);
        if (at <= 0L) {
            entity.getPersistentData().putLong(DECAY_AT_TAG, now + DECAY_TICKS);
            return;
        }
        if (now < at) return;
        int after = layers - 1;
        storeLayers(entity, after);
        if (after > 0) entity.getPersistentData().putLong(DECAY_AT_TAG, now + DECAY_TICKS);
        syncLayers(entity, after);
    }

    // ---------------------------------------------------------------- 状态本身
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = interval(amplifier);
        return duration % interval == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) return true;
        int layers = layersOf(entity);
        float damage = damageOf(layers);
        // 真实伤害：MAGIC 绕过护甲；受击前后都把无敌帧清零，保证每一下都实打实落下。
        entity.invulnerableTime = 0;
        try {
            entity.hurt(entity.damageSources().magic(), damage);
        } finally {
            entity.invulnerableTime = 0;
        }
        if (entity.isAlive()) ManaData.drainMana(entity, TICK_MANA_DRAIN);
        if (entity.level() instanceof ServerLevel level) {
            // 阶层越深，身上那层紫黑色粉尘越浓——生物没有状态说明看，这是它能给出的直观信号。
            int dust = 4 + Math.min(12, tierOf(layers));
            level.sendParticles(EROSION_DUST, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6D, entity.getZ(),
                    dust, 0.22D, 0.28D, 0.22D, 0.0D);
        }
        return true;
    }

    /**
     * 给目标挂上（或升一级）魔蚀。
     *
     * <p>层数不在这里记——{@link #onEffectApplied} 会替所有施加途径（包括这一条）统一记账，
     * 这里只负责「时长、发作节奏、图标等级」三件事，免得同一条路走两遍多记一层。</p>
     *
     * @param durationTicks 持续时间；{@code <= 0} 表示无限时长。
     */
    public static void apply(LivingEntity target, int durationTicks) {
        if (target == null || target.level().isClientSide) return;
        MobEffectInstance current = target.getEffect(GeneratedMod.MAGIC_EROSION);
        // 这一口之后会是第几层，只用来定状态等级（封顶 255）。
        int layers = Math.min(MAX_LAYERS, layersOf(target) + 1);
        int duration;
        if (durationTicks <= 0 || (current != null && current.isInfiniteDuration())) duration = -1;
        else duration = Math.max(20, durationTicks);
        // 原版状态等级封顶 255，装不下全部层数，只拿它表示「发作节奏 + 图标等级」。
        int amplifier = Math.min(MobEffectInstance.MAX_AMPLIFIER, layers - 1);
        target.addEffect(new MobEffectInstance(GeneratedMod.MAGIC_EROSION, duration, amplifier, false, true, true));
    }

    /** 深渊阶层加深的一瞬间：一声闷响、一撮黑紫粉尘，玩家自己还会收到一条动作栏提示。 */
    private static void onTierUp(LivingEntity entity, int layers) {
        int tier = tierOf(layers);
        if (entity.level() instanceof ServerLevel level) {
            Vec3 center = entity.getBoundingBox().getCenter();
            level.sendParticles(DEPTH_DUST, center.x, center.y, center.z,
                    26, 0.55D, 0.75D, 0.55D, 0.05D);
            level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z,
                    10, 0.4D, 0.6D, 0.4D, 0.02D);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.HOSTILE, 1.0F, 0.62F);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.8F, 0.5F);
        }
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable(
                    "effect.dimensional_mana.magic_erosion.tier_up",
                    tier, damageText(damageOf(layers))).withStyle(ChatFormatting.DARK_PURPLE), true);
        }
    }

    /** 伤害数字统一按整数写（2、3、66……），别给玩家看 2.0。 */
    public static String damageText(float damage) {
        return String.format(Locale.ROOT, "%.0f", damage);
    }

    private static void syncLayers(LivingEntity entity, int layers) {
        if (entity instanceof ServerPlayer player) ErosionDepthPayload.send(player, layers);
    }

    /** 强制状态不受任何常规 cure 影响：这里刻意不填任何 cure。 */
    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {
    }

    /**
     * 白名单净化：补魔剂、光系 / 净化法术、复活走这里。
     *
     * @return 目标身上确实有魔蚀并被清掉时返回 true。
     */
    public static boolean cleanse(LivingEntity target) {
        if (target == null || target.level().isClientSide) return false;
        if (!target.hasEffect(GeneratedMod.MAGIC_EROSION)) return false;
        CLEANSING.set(Boolean.TRUE);
        try {
            target.removeEffect(GeneratedMod.MAGIC_EROSION);
        } finally {
            CLEANSING.set(Boolean.FALSE);
        }
        // 洗净=层数归零，再挨一次从 1 层重新开始。
        storeLayers(target, 0);
        syncLayers(target, 0);
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6D, target.getZ(),
                    12, 0.3D, 0.35D, 0.3D, 0.02D);
        }
        return true;
    }

    /**
     * 状态自然到期：层数<b>保留</b>，只开始计时慢慢退。
     *
     * <p>以前这里直接清零，结果谁都不可能在同一轮里叠到 16 层——生物尤其叠不到
     * （咬一口才 +1 层，10 秒的状态里根本咬不了 16 口），阶层永远不涨，
     * 看起来就是「叠了一身魔蚀还是每次掉 2 点」。现在的规则是：
     * 状态没了，层数还在，靠 {@link #tickLiving} 每 {@value #DECAY_TICKS} 刻退 1 层。</p>
     */
    public static void onExpired(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Expired event) {
        if (!event.getEffectInstance().is(GeneratedMod.MAGIC_EROSION)) return;
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (entity.getPersistentData().getInt(LAYERS_TAG) <= 0) return;
        entity.getPersistentData().putLong(DECAY_AT_TAG, entity.level().getGameTime() + DECAY_TICKS);
        syncLayers(entity, layersOf(entity));
    }

    /** 当前线程是否正在走白名单净化。 */
    public static boolean cleansing() {
        return Boolean.TRUE.equals(CLEANSING.get());
    }
}
