package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

/** 按媒介分流：材质实体交给 ClientSpellMaterials，奥术、雷电、光和声保留能量表现。 */
public final class ClientSpellVisuals {
    private static final Vec3 UP = new Vec3(0, 1, 0), X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1);
    private static final List<Visual> ACTIVE = new ArrayList<>();
    private static final List<SoundCue> SOUND_CUES = new ArrayList<>();
    private static ClientLevel currentLevel;
    /** 飞剑风声的节流时间戳，避免一轮十六把剑把音效叠成噪音。 */
    private static long swordSoundAt;
    /** 结界射线每波射击只响一发的节流时间戳。 */
    private static long barrierShotAt = Long.MIN_VALUE;
    private static final int MAX_EFFECTS = 96;
    /** 水柱每 2 刻收到一次新端点，客户端用同样长的时间插值过去，转向才顺滑。 */
    private static final int BEAM_SMOOTH_TICKS = 2;
    /** 曜光裂界爆裂的黑白闪光：屏幕层每 2 刻翻转一次，总共 16 刻。 */
    private static final int RADIANT_FLASH_TICKS = 16;
    private static long radiantFlashAt = Long.MIN_VALUE;
    /** 水柱靠每 2 刻的刷新包更新端点，因此数据与最近刷新时间都可变；其余视觉仍按出生时间播放。 */
    private static final class Visual {
        SpellVisualPayload data;
        final SpellDefinition spell;
        /** 边界类视觉每次刷新都会重新起算动画，因此出生时间不是 final。 */
        long born;
        final Vec3 floor;
        long updatedAt;
        /** 上一包与最新包的起止点；渲染时按时间在两包之间插值。 */
        Vec3 fromStart, fromEnd, toStart, toEnd;
        Visual(SpellVisualPayload data, SpellDefinition spell, long born, Vec3 floor) {
            this.data = data; this.spell = spell; this.born = born; this.floor = floor; this.updatedAt = born;
            this.fromStart = data.start(); this.fromEnd = data.end();
            this.toStart = data.start(); this.toEnd = data.end();
        }
        /** 收到水柱新端点时，把插值起点推进到“此刻实际显示的位置”，避免插值中断造成跳帧。 */
        void retarget(SpellVisualPayload next, long time) {
            double t = Math.clamp((double)(time - updatedAt) / BEAM_SMOOTH_TICKS, 0.0, 1.0);
            fromStart = fromStart.lerp(toStart, t);
            fromEnd = fromEnd.lerp(toEnd, t);
            toStart = next.start();
            toEnd = next.end();
            data = next;
            updatedAt = time;
        }
    }
    private record SoundCue(long playAt, String event, Vec3 position, float volume, float pitch) { }

    public static void accept(SpellVisualPayload data) {
        Minecraft mc = Minecraft.getInstance();
        resetLevel(mc.level);
        SpellDefinition spell = SpellDefinition.byId(data.spellId());
        if (mc.level == null || spell == null || data.duration() < 1 || data.duration() > 1200) return;
        // 飞剑从天空裂缝一路射到地面，起止点可能相距两三百格，单独放宽这个上限。
        double lengthLimit = data.phase() == SpellVisualPayload.FLYING_SWORD ? 512.0 * 512.0 : 4096.0;
        if (!finite(data.start()) || !finite(data.end()) || data.start().distanceToSqr(data.end()) > lengthLimit) return;
        if (data.phase() < SpellVisualPayload.CHARGE || data.phase() > SpellVisualPayload.MAX_PHASE) return;
        if (data.phase() == SpellVisualPayload.END_ATTACHED) {
            ACTIVE.removeIf(v -> v.spell == spell && v.data.anchor() == data.anchor() && v.data.phase() == SpellVisualPayload.RELEASE);
            return;
        }
        if (data.phase() == SpellVisualPayload.SKY_RIFT_END) {
            ACTIVE.removeIf(v -> v.spell == spell && v.data.owner() == data.owner()
                    && v.data.phase() == SpellVisualPayload.SKY_RIFT);
            return;
        }
        if (data.phase() == SpellVisualPayload.GOLDEN_BELL_END) {
            ACTIVE.removeIf(v -> v.spell == spell && v.data.owner() == data.owner()
                    && v.data.phase() == SpellVisualPayload.GOLDEN_BELL);
            return;
        }
        if (data.phase() == SpellVisualPayload.LIGHT_SHELTER_END) {
            ACTIVE.removeIf(v -> v.spell == spell && v.data.owner() == data.owner()
                    && v.data.phase() == SpellVisualPayload.LIGHT_SHELTER);
            return;
        }
        if (data.phase() == SpellVisualPayload.LIGHT_SHELTER) {
            // 庇护光环每 40 刻刷新一次，同一个被庇护的生物只保留一条视觉。
            for (Visual v : ACTIVE) {
                if (v.spell == spell && v.data.owner() == data.owner()
                        && v.data.phase() == SpellVisualPayload.LIGHT_SHELTER) {
                    v.data = data;
                    v.born = mc.level.getGameTime() - 10;
                    v.updatedAt = mc.level.getGameTime();
                    return;
                }
            }
            addVisual(data, spell, mc.level);
            return;
        }
        if (data.phase() == SpellVisualPayload.VENOM_MIST_END) {
            ACTIVE.removeIf(v -> v.spell == spell && v.data.owner() == data.owner()
                    && v.data.phase() == SpellVisualPayload.VENOM_MIST);
            return;
        }
        if (data.phase() == SpellVisualPayload.VENOM_MIST) {
            // 毒雾每秒原地刷新一次半径，同一个施法者只保留一条视觉。
            for (Visual v : ACTIVE) {
                if (v.spell == spell && v.data.owner() == data.owner()
                        && v.data.phase() == SpellVisualPayload.VENOM_MIST) {
                    v.data = data;
                    v.updatedAt = mc.level.getGameTime();
                    v.born = mc.level.getGameTime() - 2;
                    return;
                }
            }
            play(mc.level, "spell_impact_poison", data.start(), 0.55F, 0.62F);
            addVisual(data, spell, mc.level);
            return;
        }
        if (data.phase() == SpellVisualPayload.VENOM_NEEDLE) {
            ACTIVE.removeIf(v -> v.data.owner() == data.owner() && v.data.phase() == SpellVisualPayload.CHARGE);
            play(mc.level, "spell_poison_bolt", data.start(), 0.75F, 1.10F);
            addVisual(data, spell, mc.level);
            return;
        }
        if (data.phase() == SpellVisualPayload.RADIANT_CHARGE) {
            // 蓄力只刷新同一施法者的蓄力法阵，层数一多就向外多延一层，不重复叠视觉。
            for (Visual v : ACTIVE) {
                if (v.data.owner() == data.owner() && v.data.phase() == SpellVisualPayload.RADIANT_CHARGE) {
                    v.data = data;
                    v.updatedAt = mc.level.getGameTime();
                    return;
                }
            }
            ACTIVE.removeIf(v -> v.data.owner() == data.owner() && v.data.phase() == SpellVisualPayload.CHARGE);
            addVisual(data, spell, mc.level);
            return;
        }
        if (data.phase() == SpellVisualPayload.RADIANT_FLASH) {
            // 屏幕层的黑白闪光交给 HUD 层绘制，世界层不留几何。
            triggerRadiantFlash(mc.level.getGameTime());
            return;
        }
        if (data.phase() == SpellVisualPayload.RADIANT_BURST) {
            play(mc.level, "spell_impact_light", data.start(), 1.15F, 0.85F);
            SOUND_CUES.add(new SoundCue(mc.level.getGameTime() + 3, "spell_shatter", data.start(), 0.95F, 0.75F));
            ACTIVE.removeIf(v -> v.data.owner() == data.owner() && v.data.phase() == SpellVisualPayload.RADIANT_CHARGE);
            addVisual(data, spell, mc.level);
            return;
        }
        if (data.phase() == SpellVisualPayload.SKY_RIFT || data.phase() == SpellVisualPayload.GOLDEN_BELL) {
            // 读条已经结束，先收掉同一施法者残留的蓄力视觉。
            ACTIVE.removeIf(v -> v.data.owner() == data.owner() && v.data.phase() == SpellVisualPayload.CHARGE);
            // 天空裂缝与金钟罩每 40 刻刷新一次，同一施法者只保留一条视觉。
            for (Visual v : ACTIVE) {
                if (v.spell == spell && v.data.owner() == data.owner() && v.data.phase() == data.phase()) {
                    v.data = data;
                    // 提前 10 刻出生：刷新时淡入淡出已经走完，钟罩与裂缝不会每两秒闪一下。
                    v.born = mc.level.getGameTime() - 10;
                    v.updatedAt = mc.level.getGameTime();
                    return;
                }
            }
            if (data.phase() == SpellVisualPayload.SKY_RIFT) riftOpeningSound(mc.level, data);
            addVisual(data, spell, mc.level);
            return;
        }
        if (data.phase() == SpellVisualPayload.FLYING_SWORD) {
            ACTIVE.removeIf(v -> v.data.owner() == data.owner() && v.data.phase() == SpellVisualPayload.CHARGE);
            swordFallingSound(mc.level, data);
            addVisual(data, spell, mc.level);
            return;
        }
        if (data.phase() == SpellVisualPayload.VINE_END) {
            ACTIVE.removeIf(v -> v.spell == spell && v.data.phase() == SpellVisualPayload.VINE
                    && v.data.owner() == data.owner()
                    && v.data.start().distanceToSqr(data.start()) < 4.0);
            return;
        }
        if (data.phase() == SpellVisualPayload.BANKAI_END) {
            ACTIVE.removeIf(v -> v.spell == spell && v.data.owner() == data.owner()
                    && (v.data.phase() == SpellVisualPayload.BANKAI_FIELD
                    || v.data.phase() == SpellVisualPayload.BANKAI_WAVE));
            return;
        }
        if (data.phase() == SpellVisualPayload.BANKAI_FIELD) {
            // 领域边界每 40 刻刷新一次；同一棵树只保留一条视觉。
            for (Visual v : ACTIVE) {
                if (v.spell == spell && v.data.owner() == data.owner()
                        && v.data.phase() == SpellVisualPayload.BANKAI_FIELD) {
                    v.data = data;
                    v.updatedAt = mc.level.getGameTime();
                    v.born = mc.level.getGameTime();
                    return;
                }
            }
            addVisual(data, spell, mc.level);
            return;
        }
        if (data.phase() == SpellVisualPayload.VINE || data.phase() == SpellVisualPayload.BANKAI_WAVE
                || data.phase() == SpellVisualPayload.VINE_LASH) {
            addVisual(data, spell, mc.level);
            return;
        }
        if (data.phase() == SpellVisualPayload.CAGE_END) {
            ACTIVE.removeIf(v -> v.spell == spell && v.data.phase() == SpellVisualPayload.CAGE
                    && v.data.start().distanceToSqr(data.start()) < 4.0);
            return;
        }
        if (data.phase() == SpellVisualPayload.BEAM_END) {
            ACTIVE.removeIf(v -> v.spell == spell && v.data.owner() == data.owner()
                    && v.data.phase() == SpellVisualPayload.BEAM);
            return;
        }
        if (data.phase() == SpellVisualPayload.BEAM) {
            // 同一条水柱只在原地刷新端点，不重复入列，避免每 2 刻叠一层。
            for (Visual v : ACTIVE) {
                if (v.spell == spell && v.data.owner() == data.owner()
                        && v.data.phase() == SpellVisualPayload.BEAM) {
                    v.retarget(data, mc.level.getGameTime());
                    return;
                }
            }
            addVisual(data, spell, mc.level);
            return;
        }
        if (data.phase() == SpellVisualPayload.DOMAIN_END) {
            ACTIVE.removeIf(v -> v.spell == spell && v.data.owner() == data.owner()
                    && v.data.phase() == SpellVisualPayload.DOMAIN_FIELD);
            return;
        }
        if (data.phase() == SpellVisualPayload.DOMAIN_FIELD) {
            // 叠层时只刷新同一条法阵视觉，避免每按一次 Alt 就叠一层新的。
            for (Visual v : ACTIVE) {
                if (v.spell == spell && v.data.owner() == data.owner()
                        && v.data.phase() == SpellVisualPayload.DOMAIN_FIELD) {
                    v.data = data;
                    v.updatedAt = mc.level.getGameTime();
                    return;
                }
            }
            addVisual(data, spell, mc.level);
            return;
        }
        if (data.phase() == SpellVisualPayload.DOMAIN_BURST) {
            // 主爆发 + 两层延迟回声：冰层先炸开，再补一记闷响拖尾，整片领域听起来才有分量。
            play(mc.level, "spell_impact_ice", data.start(), 1.20F, 0.55F);
            long now = mc.level.getGameTime();
            SOUND_CUES.add(new SoundCue(now + 6, "spell_shatter", data.start(), 0.95F, 0.62F));
            SOUND_CUES.add(new SoundCue(now + 16, "spell_impact_ice", data.start(), 0.85F, 0.44F));
            addVisual(data, spell, mc.level);
            return;
        }
        if (data.phase() == SpellVisualPayload.METEOR_IMPACT) {
            // 万法千陨一轮可能同时落地几十块，客户端只补碎屑与尘环，声音交给服务端节流。
            addVisual(data, spell, mc.level);
            return;
        }
        if (data.phase() != SpellVisualPayload.CHARGE) ACTIVE.removeIf(v ->
                v.data.owner() == data.owner() && (v.data.phase() == SpellVisualPayload.CHARGE
                        || v.data.phase() == SpellVisualPayload.RADIANT_CHARGE));
        if (data.phase() == SpellVisualPayload.CANCEL) return;
        // 断魂鸣弦：鸣球绕施法者旋转，每 20 刻原地刷新一次；声波、反射波与触碰爆震各自成段。
        if (data.phase() == SpellVisualPayload.SONIC_ORB_END) {
            ACTIVE.removeIf(v -> v.data.owner() == data.owner()
                    && v.data.phase() == SpellVisualPayload.SONIC_ORB);
            return;
        }
        if (data.phase() == SpellVisualPayload.SONIC_ORB) {
            for (Visual v : ACTIVE) {
                if (v.data.owner() == data.owner() && v.data.phase() == SpellVisualPayload.SONIC_ORB) {
                    v.data = data;
                    v.updatedAt = mc.level.getGameTime();
                    return;
                }
            }
            addVisual(data, spell, mc.level);
            return;
        }
        if (data.phase() == SpellVisualPayload.SONIC_WAVE
                || data.phase() == SpellVisualPayload.SONIC_REFLECT
                || data.phase() == SpellVisualPayload.SONIC_TOUCH) {
            // 音效由服务端按位置播放，客户端只补几何，避免同一记声波响两遍。
            addVisual(data, spell, mc.level);
            return;
        }
        // 天雷滚滚：雷球跟随施法者，每秒刷新一次位置；收束漩涡与爆炸各自独立成段。
        if (data.phase() == SpellVisualPayload.TEMPEST_END) {
            ACTIVE.removeIf(v -> v.data.owner() == data.owner()
                    && (v.data.phase() == SpellVisualPayload.TEMPEST_BALL
                    || v.data.phase() == SpellVisualPayload.TEMPEST_PULL));
            return;
        }
        if (data.phase() == SpellVisualPayload.TEMPEST_BALL) {
            // 每 20 刻刷新一次位置：只更新数据，不重复叠加视觉与声音。
            for (Visual v : ACTIVE) {
                if (v.data.owner() == data.owner() && v.data.phase() == SpellVisualPayload.TEMPEST_BALL) {
                    v.data = data;
                    v.updatedAt = mc.level.getGameTime();
                    return;
                }
            }
            play(mc.level, "spell_impact_lightning", data.start(), 0.85F, 0.72F);
            addVisual(data, spell, mc.level);
            return;
        }
        if (data.phase() == SpellVisualPayload.TEMPEST_PULL) {
            for (Visual v : ACTIVE) {
                if (v.data.owner() == data.owner() && v.data.phase() == SpellVisualPayload.TEMPEST_PULL) {
                    v.data = data;
                    v.updatedAt = mc.level.getGameTime();
                    return;
                }
            }
            play(mc.level, "spell_impact_lightning", data.start(), 0.70F, 0.55F);
            addVisual(data, spell, mc.level);
            return;
        }
        if (data.phase() == SpellVisualPayload.TEMPEST_STRIKE) {
            // 落雷残影只画一束电弧，声音由服务端按位置播放，避免三次落雷叠成噪音。
            addVisual(data, spell, mc.level);
            return;
        }
        if (data.phase() == SpellVisualPayload.TEMPEST_BLAST) {
            play(mc.level, "spell_impact_lightning", data.start(), 1.25F, 0.55F);
            SOUND_CUES.add(new SoundCue(mc.level.getGameTime() + 4, "spell_shatter", data.start(), 0.95F, 0.60F));
            addVisual(data, spell, mc.level);
            return;
        }
        if (data.phase() == SpellVisualPayload.SHOT) {
            // 每一波射击都要有声音：九个法阵同一刻开火时只响一发，避免叠成噪音，
            // 但下一波（三秒后）必须还能听见，所以按游戏刻节流而不是永久留白。
            long shotAt = mc.level.getGameTime();
            if (shotAt != barrierShotAt) {
                barrierShotAt = shotAt;
                play(mc.level, "spell_arcane_ray", data.start(), 0.45F, 1.45F);
            }
            addVisual(data, spell, mc.level);
            return;
        }
        if (data.phase() == SpellVisualPayload.BARRIER) {
            play(mc.level, "spell_impact_arcane", data.start(), 0.24F, 1.42F);
            addVisual(data, spell, mc.level);
            return;
        }
        if (data.phase() == SpellVisualPayload.CAGE) {
            play(mc.level, "spell_impact_fire", data.start(), 0.55F, 0.85F);
            addVisual(data, spell, mc.level);
            return;
        }
        String sound = data.phase() == SpellVisualPayload.CHARGE ? "spell_charge"
                : data.phase() == SpellVisualPayload.SHATTER ? "spell_shatter" : "spell_" + spell.visualBase().id();
        // 发射声从施法者位置起音；命中声按画面抵达时间延迟到真实终点。
        play(mc.level, sound, data.start(), data.phase() == SpellVisualPayload.CHARGE ? 0.25F : 0.72F, 1.0F);
        if (data.phase() == SpellVisualPayload.RELEASE) {
            int delay = impactDelay(spell);
            SOUND_CUES.add(new SoundCue(mc.level.getGameTime() + delay, "spell_impact_" + spell.medium(),
                    data.end(), impactVolume(spell), impactPitch(spell)));
        }
        addVisual(data, spell, mc.level);
    }

    private static void addVisual(SpellVisualPayload data, SpellDefinition spell, ClientLevel level) {
        if (ACTIVE.size() >= MAX_EFFECTS) ACTIVE.remove(0);
        ACTIVE.add(new Visual(data, spell, level.getGameTime(), ClientSpellMaterials.ground(level, data.end())));
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        resetLevel(mc.level);
        if (mc.level == null) return;
        long time = mc.level.getGameTime();
        ACTIVE.removeIf(v -> time - v.born >= v.data.duration()
                || (v.data.phase() == SpellVisualPayload.CHARGE && !alive(mc.level.getEntity(v.data.owner())))
                // 水柱一旦超过 2 秒没有刷新（施法者断线、分包丢失），视觉自行收起。
                || (v.data.phase() == SpellVisualPayload.BEAM && time - v.updatedAt > 40)
                // 叠层法阵与雷球每 20 刻刷新一次，超过 5 秒没刷新说明玩家已离开或状态丢失。
                || (v.data.phase() == SpellVisualPayload.DOMAIN_FIELD && time - v.updatedAt > 100)
                || (v.data.phase() == SpellVisualPayload.BANKAI_FIELD && time - v.updatedAt > 100)
                || ((v.data.phase() == SpellVisualPayload.TEMPEST_BALL
                    || v.data.phase() == SpellVisualPayload.TEMPEST_PULL) && time - v.updatedAt > 100)
                // 天空裂缝与金钟罩每 40 刻刷新一次，5 秒没刷新说明施法者已离开或状态丢失。
                || ((v.data.phase() == SpellVisualPayload.SKY_RIFT
                    || v.data.phase() == SpellVisualPayload.GOLDEN_BELL) && time - v.updatedAt > 100)
                // 蓄力法阵与庇护光环同样每 40 刻刷新一次，5 秒没刷新说明施法者已经离开。
                || ((v.data.phase() == SpellVisualPayload.RADIANT_CHARGE
                    || v.data.phase() == SpellVisualPayload.LIGHT_SHELTER) && time - v.updatedAt > 100)
                // 毒雾每秒刷新一次，5 秒没刷新说明施法者已经离开或状态丢失。
                || (v.data.phase() == SpellVisualPayload.VENOM_MIST && time - v.updatedAt > 100)
                // 震荡鸣球每 20 刻刷新一次，5 秒没刷新说明施法者已经离开或状态丢失。
                || (v.data.phase() == SpellVisualPayload.SONIC_ORB && time - v.updatedAt > 100));
        Iterator<SoundCue> cues = SOUND_CUES.iterator();
        while (cues.hasNext()) {
            SoundCue cue = cues.next();
            if (cue.playAt() > time) continue;
            play(mc.level, cue.event(), cue.position(), cue.volume(), cue.pitch());
            cues.remove();
        }
    }
    private static void play(ClientLevel level, String event, Vec3 pos, float volume, float pitch) {
        level.playLocalSound(pos.x, pos.y, pos.z, SpellSounds.get(event), SoundSource.PLAYERS,
                volume, pitch, false);
    }
    /**
     * 开天缝：裂缝开在世界高度上限，离地面两百多格，按裂缝位置播放在地面上根本听不见，
     * 所以把声音放在施法者脚下，用较高的音量铺满整片战场；后面再补两记延迟的低频回响。
     */
    private static void riftOpeningSound(ClientLevel level, SpellVisualPayload data) {
        Entity caster = level.getEntity(data.owner());
        Vec3 at = caster != null ? caster.position() : data.start();
        play(level, "spell_sky_rift", at, 2.6F, 1.0F);
        long now = level.getGameTime();
        SOUND_CUES.add(new SoundCue(now + 12, "spell_impact_metal", at, 1.35F, 0.54F));
        SOUND_CUES.add(new SoundCue(now + 26, "spell_impact_sound", at, 1.05F, 0.46F));
    }
    /** 飞剑落下：一轮十六把剑，风声按 6 刻节流，位置跟着施法者，地面上才有“剑在头顶破空”的感觉。 */
    private static void swordFallingSound(ClientLevel level, SpellVisualPayload data) {
        long now = level.getGameTime();
        if (now < swordSoundAt) return;
        swordSoundAt = now + 6;
        Entity caster = level.getEntity(data.owner());
        Vec3 at = caster != null ? caster.position() : data.end();
        play(level, "spell_flying_sword", at, 1.35F, 0.90F + level.random.nextFloat() * 0.25F);
    }
    private static int impactDelay(SpellDefinition spell) {
        return spell == SpellDefinition.ROCKFALL ? 7 : switch (spell) {
            case FIREBALL, WATER_BOLT, ICE_SPIKE, WIND_BLADE, SHARP_EDGE, LIGHT_BOLT, POISON_BOLT -> 4;
            default -> 0;
        };
    }
    private static float impactVolume(SpellDefinition spell) {
        return spell == SpellDefinition.ROCKFALL ? 1.15F : spell == SpellDefinition.WIND_BLADE ? 0.90F : 0.68F;
    }
    private static float impactPitch(SpellDefinition spell) {
        return spell == SpellDefinition.ROCKFALL ? 0.78F : spell == SpellDefinition.ICE_SPIKE ? 1.12F : 1.0F;
    }
    private static boolean alive(Entity entity) { return entity != null && entity.isAlive(); }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static void resetLevel(ClientLevel level) {
        if (currentLevel != level) { ACTIVE.clear(); SOUND_CUES.clear(); swordSoundAt = 0;
            barrierShotAt = Long.MIN_VALUE; currentLevel = level; }
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Minecraft mc = Minecraft.getInstance();
        resetLevel(mc.level);
        if (mc.level == null) return;
        List<SpellProjectile> projectiles = new ArrayList<>();
        List<BlazingSun> suns = new ArrayList<>();
        List<LivingEntity> volts = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof SpellProjectile projectile) projectiles.add(projectile);
            else if (entity instanceof BlazingSun sun) suns.add(sun);
            // 伏特状态是原版效果，客户端直接读效果绘制缠绕闪电，双方都不用逐刻发包。
            else if (entity instanceof LivingEntity living && living.hasEffect(NewSpellContent.VOLT_STATE))
                volts.add(living);
        }
        if (ACTIVE.isEmpty() && projectiles.isEmpty() && suns.isEmpty() && volts.isEmpty()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f matrix = new Matrix4f(event.getPoseStack().last().pose());
        // 每种材质有独立固定缓冲，切换贴图不会让先前的 VertexConsumer 失效。
        LinkedHashMap<RenderType, ByteBufferBuilder> buffers = new LinkedHashMap<>();
        try (ByteBufferBuilder memory = new ByteBufferBuilder(65536)) {
            for (SpellRenderTypes.Material material : SpellRenderTypes.Material.values())
                buffers.put(material.type, new ByteBufferBuilder(65536));
            for (SpellRenderTypes.SpriteAtlas atlas : SpellRenderTypes.SpriteAtlas.values())
                buffers.put(atlas.type, new ByteBufferBuilder(65536));
            buffers.put(SpellRenderTypes.GLOW, new ByteBufferBuilder(262144));
            MultiBufferSource.BufferSource source = MultiBufferSource.immediateWithBuffers(buffers, memory);
            Mesh mesh = new Mesh(source.getBuffer(SpellRenderTypes.GLOW), matrix, camera);
            ClientSpellSprites sprites = new ClientSpellSprites(source, matrix, event.getCamera());
            ClientSpellMaterials materials = new ClientSpellMaterials(source, matrix, camera, sprites);
            List<Visual> ordered = new ArrayList<>(ACTIVE);
            ordered.sort((a, b) -> Double.compare(b.data.end().distanceToSqr(camera), a.data.end().distanceToSqr(camera)));
            for (Visual v : ordered) {
                // 天空裂缝开在世界高度上限、飞剑从天上落下，两者的可视距离要单独放宽。
                double cull = v.data.phase() == SpellVisualPayload.SKY_RIFT ? 768.0
                        : v.data.phase() == SpellVisualPayload.FLYING_SWORD ? 512.0
                        : v.data.phase() == SpellVisualPayload.RADIANT_BURST ? 220.0
                        : v.data.phase() == SpellVisualPayload.VENOM_MIST ? 160.0 : 72.0;
                if (v.data.start().distanceToSqr(camera) > cull * cull) continue;
                double age = mc.level.getGameTime() - v.born + partial;
                if (age < 0 || age >= v.data.duration()) continue;
                draw(mesh, materials, sprites, v, age, mc.level, partial);
            }
            for (SpellProjectile projectile : projectiles) {
                if (projectile.distanceToSqr(camera) > 72 * 72) continue;
                materials.lighting(mc.level, projectile.position());
                sprites.lighting(mc.level, projectile.position());
                materials.drawProjectile(projectile, partial);
            }
            for (BlazingSun sun : suns) {
                if (sun.distanceToSqr(camera) > 96 * 96) continue;
                materials.lighting(mc.level, sun.position());
                sprites.lighting(mc.level, sun.position());
                drawBlazingSun(mesh, materials, sprites, sun, partial, mc.level);
            }
            for (LivingEntity volt : volts) {
                if (volt.distanceToSqr(camera) > 48 * 48) continue;
                drawVoltBody(mesh, volt, mc.level, partial);
            }
            // 材质先画，光效最后合成；不触碰游戏其他渲染队列。
            source.endBatch();
        } finally {
            buffers.values().forEach(ByteBufferBuilder::close);
        }
    }

    /** 炽阳之怒：悬停时在身前画火系高级法阵，发射后只保留火球与拖尾。 */
    private static void drawBlazingSun(Mesh m, ClientSpellMaterials materials, ClientSpellSprites sprites,
                                       BlazingSun sun, float partial, ClientLevel level) {
        Vec3 center = sun.getPosition(partial);
        double age = sun.tickCount + partial;
        double radius = sun.radius();
        Vec3 dir = sun.direction();
        if (dir.lengthSqr() < 0.01) dir = Z;
        if (!sun.isReleased()) {
            Vec3 sigil = center.subtract(dir.scale(2.1));
            Vec3 axisU = UP.cross(dir);
            if (axisU.lengthSqr() < 0.0001) axisU = X;
            axisU = axisU.normalize();
            Vec3 axisV = dir.cross(axisU).normalize();
            m.ring(sigil, axisU, axisV, 1.55, 0.032, age * 0.020, 0xFF9A33, 0.78);
            m.ring(sigil, axisU, axisV, 1.22, 0.022, -age * 0.030, 0xFFDE7A, 0.82);
            m.ring(sigil, axisU, axisV, 0.78, 0.014, age * 0.045, 0xFFF2C0, 0.72);
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3.0 + age * 0.02;
                Vec3 p = sigil.add(axisU.scale(Math.cos(a) * 1.40)).add(axisV.scale(Math.sin(a) * 1.40));
                m.crystal(p, dir, 0.09 + radius * 0.02, 0.20 + radius * 0.03, 0xFFC65A, 0.85);
            }
            m.beam(sigil, center, 0.070, 0xFFB347, 0.55);
            m.beam(sigil, center, 0.022, 0xFFFFE0, 0.82);
            Vec3 floor = ClientSpellMaterials.ground(level, center);
            m.ring(floor, X, Z, 2.6, 0.030, age * 0.010, 0xFF7A2E, 0.45);
            m.ring(floor, X, Z, 1.9, 0.018, -age * 0.016, 0xFFD36A, 0.40);
        }
        materials.drawSun(sun, partial);
    }

    private static void draw(Mesh m, ClientSpellMaterials materials, ClientSpellSprites sprites,
                             Visual v, double age, ClientLevel level, float partial) {
        SpellVisualPayload d = v.data;
        int color = v.spell.isArcane() ? 0xAE69FF : v.spell.element().color() & 0xFFFFFF;
        double fade = Math.min(1, age / 2.0 + 0.2) * Math.max(0, Math.min(1, (d.duration() - age) / 8.0));
        Vec3 start = d.start(), end = d.end();
        if (d.phase() == SpellVisualPayload.CHARGE) {
            Entity owner = level.getEntity(d.owner());
            if (!alive(owner)) return;
            Vec3 feet = owner.getPosition(partial).add(0, 0.06, 0);
            Vec3 direction = owner.getViewVector(partial).normalize();
            // 用实体朝向的水平右轴定位手部，仰视和俯视时不突然翻到另一侧。
            double yaw = Math.toRadians(owner.getViewYRot(partial));
            Vec3 handRight = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
            Vec3 hand = owner.getEyePosition(partial).add(direction.scale(0.85))
                    .add(handRight.scale(0.30)).add(0, -0.23, 0);
            sprites.lighting(level, hand);
            double chargeFade = Math.min(1, (age + 1) / 3.0)
                    * Math.min(1, (d.duration() - age) / Math.min(3.0, d.duration()));
            // 天雷滚滚：头顶堆多重光圈与法阵，雷球随读条一点点长成型。
            if (v.spell == SpellDefinition.THUNDER_TEMPEST)
                tempestCharge(m, sprites, owner, partial, age, d.duration(), chargeFade);
            sprites.charge(v.spell, hand, feet, direction, age, d.duration(), chargeFade);
            // 曜光裂界：身前的光球与身上向外延展的多重法术阵。
            if (v.spell == SpellDefinition.RADIANT_RIFT)
                radiantCharge(m, sprites, owner, partial, age, chargeLayers(d), chargeFade);
            return;
        }
        if (d.phase() == SpellVisualPayload.RADIANT_CHARGE) {
            // 蓄力法阵单独成段：读条一开始就出现，蓄力时原地刷新层数。
            Entity owner = level.getEntity(d.owner());
            if (!alive(owner)) return;
            double alpha = Math.min(1.0, (age + 1) / 3.0) * Math.min(1.0, (d.duration() - age) / 8.0);
            sprites.lighting(level, owner.position());
            radiantCharge(m, sprites, owner, partial, age, chargeLayers(d), alpha);
            return;
        }
        if (d.phase() == SpellVisualPayload.SHATTER) {
            sprites.lighting(level, start);
            sprites.shatter(v.spell, start, age, d.duration(), fade);
            return;
        }
        if (d.phase() == SpellVisualPayload.BEAM) {
            // 两包之间线性插值端点；起点再按施法者当前眼睛位置重算，水柱就会跟着镜头平滑扫过去。
            double t = Math.clamp((level.getGameTime() - v.updatedAt + partial) / (double)BEAM_SMOOTH_TICKS,
                    0.0, 1.0);
            start = v.fromStart.lerp(v.toStart, t);
            end = v.fromEnd.lerp(v.toEnd, t);
            Entity owner = level.getEntity(d.owner());
            if (owner != null) {
                Vec3 direction = d.end().subtract(d.start());
                if (direction.lengthSqr() > 1.0E-6)
                    start = owner.getEyePosition(partial).add(direction.normalize().scale(1.35)).add(0, -0.15, 0);
            }
        }
        Vec3 anchor = end, floor = v.floor;
        double bodyAlpha = fade;
        if (d.anchor() >= 0) {
            Entity entity = level.getEntity(d.anchor());
            // 致死一击后实体可能先于动画从渲染列表消失；此时继续使用服务器同步的最后命中点。
            if (alive(entity)) {
                anchor = entity.getPosition(partial).add(0, entity.getBbHeight() * 0.5, 0);
                floor = entity.getPosition(partial).add(0, 0.025, 0);
                Minecraft mc = Minecraft.getInstance();
                // 第一人称自己的护甲只保留淡轮廓，不把屏幕封在石头/钢板里面。
                if (entity == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) bodyAlpha *= 0.16;
            }
        }
        // 藤条与卍解是纯几何视觉，不需要材质贴图与发射残影，直接按各自形状绘制。
        if (d.phase() == SpellVisualPayload.VINE) {
            thornVine(m, start, end, age, fade);
            return;
        }
        if (d.phase() == SpellVisualPayload.VINE_LASH) {
            // 目标还活着就用它当前的位置，鞭击会跟着人走。
            vineLash(m, start, anchor, age, d.duration(), Math.min(1.0, fade + 0.35));
            return;
        }
        if (d.phase() == SpellVisualPayload.BANKAI_WAVE) {
            bankaiWave(m, start, Math.max(1.0, end.y - start.y), age, d.duration(), fade);
            return;
        }
        if (d.phase() == SpellVisualPayload.BANKAI_FIELD) {
            bankaiField(m, start, Math.max(1.0, end.y - start.y), level.getGameTime(), fade);
            return;
        }
        // 毒系新术也是纯几何视觉：飞行中的毒刺与持续扩散的毒雾。
        if (d.phase() == SpellVisualPayload.VENOM_NEEDLE) {
            venomNeedle(m, start, end, age, d.duration(), fade);
            return;
        }
        if (d.phase() == SpellVisualPayload.VENOM_MIST) {
            venomMist(m, start, Math.max(1.0, end.y - start.y), level.getGameTime(), fade);
            return;
        }
        // 万剑归一与金钟罩同样是纯几何视觉：高空裂缝、落下的飞剑与罩住施法者的金钟。
        if (d.phase() == SpellVisualPayload.SKY_RIFT) {
            skyRift(m, sprites, start, end, level.getGameTime());
            return;
        }
        if (d.phase() == SpellVisualPayload.FLYING_SWORD) {
            flyingSword(m, start, end, age, d.duration(), Math.min(1.0, fade + 0.25));
            return;
        }
        if (d.phase() == SpellVisualPayload.GOLDEN_BELL) {
            goldenBell(m, anchor, level.getGameTime(), bodyAlpha);
            return;
        }
        // 光系新术同样是纯几何视觉：庇护光环与光球爆裂。
        if (d.phase() == SpellVisualPayload.LIGHT_SHELTER) {
            lightShelter(m, anchor, level.getGameTime(), bodyAlpha);
            return;
        }
        if (d.phase() == SpellVisualPayload.RADIANT_BURST) {
            radiantBurst(m, start, Math.max(1.0, end.y - start.y), age, d.duration(), fade);
            return;
        }
        // 音系新术同样是纯几何视觉：环绕的鸣球、穿透声波、反射波与触碰爆震。
        if (d.phase() == SpellVisualPayload.SONIC_ORB) {
            Entity caster = level.getEntity(d.owner());
            Vec3 center = caster != null
                    ? caster.getPosition(partial).add(0, caster.getBbHeight() * 0.65, 0)
                    : start;
            int orbs = (int)Math.round(Math.max(1.0, end.y - start.y));
            sonicOrbs(m, center, orbs, level.getGameTime(), Math.min(1.0, fade + 0.25));
            return;
        }
        if (d.phase() == SpellVisualPayload.SONIC_WAVE) {
            sonicWave(m, start, Math.max(1.0, end.y - start.y), age, d.duration(), fade);
            return;
        }
        if (d.phase() == SpellVisualPayload.SONIC_REFLECT) {
            sonicReflect(m, start, end, age, d.duration(), fade);
            return;
        }
        if (d.phase() == SpellVisualPayload.SONIC_TOUCH) {
            sonicTouch(m, start, age, d.duration(), fade);
            return;
        }
        materials.lighting(level, anchor);
        sprites.lighting(level, anchor);
        sprites.release(v.spell, start, end, anchor, floor, age, d.duration(), fade,
                d.anchor() >= 0, fade > 0 ? bodyAlpha / fade : 1);
        if (v.spell.isNewSpell() && materials.drawNew(v.spell, start, end, anchor, floor, age, d.duration(), bodyAlpha, d.anchor() >= 0, d.phase())) return;
        if (materials.draw(v.spell, start, end, anchor, floor, age, d.duration(), bodyAlpha)) return;
        Vec3 direction = end.subtract(start).normalize();
        if (direction.lengthSqr() < 0.01) direction = Z;
        Vec3 side = side(direction), vertical = direction.cross(side).normalize();
        switch (v.spell) {
            case ARCANE_RAY -> {
                m.beam(start, end, 0.12 * fade, color, fade * 0.7);
                m.beam(start, end, 0.026, 0xF9E8FF, fade);
                helix(m, start, end, 0.18, age * 0.3, color, fade, 2);
                impact(m, end, side, vertical, age, color, fade, 1.1);
            }
            case ARCANE_WARD -> {
                shield(m, anchor, 6, 0.85, age, color, bodyAlpha);
                m.ring(anchor, X, Z, 0.95, 0.035, age * 0.07, 0xF2CBFF, bodyAlpha);
            }
            case ARCANE_SURGE -> {
                sigil(m, floor, 1.0, -age * 0.05, color, fade);
                helix(m, anchor.add(0, -0.7, 0), anchor.add(0, 1.2, 0), 0.65, age * 0.12, color, bodyAlpha, 3);
                for (int i = 0; i < 3; i++) {
                    double a = age * 0.06 + i * Math.PI * 2 / 3;
                    m.crystal(anchor.add(Math.cos(a) * 0.75, 0.2 + Math.sin(age * 0.15) * 0.2, Math.sin(a) * 0.75),
                            UP, 0.12, 0.35, 0xDFC0FF, bodyAlpha);
                }
            }
            case LIGHTNING_BLINK, THUNDER_STRIKE -> {
                lightning(m, start, end, age, 0xFFF19A, fade);
                if (v.spell == SpellDefinition.THUNDER_STRIKE) {
                    lightning(m, start.add(0.8, 0, 0.8), end, age + 3, 0xBCAFFF, fade * 0.6);
                    impact(m, end.add(0, 0.05, 0), X, Z, age, color, fade, 3);
                }
            }
            case HOLY_BEAM -> {
                m.beam(start, end, 0.30 * fade, 0xFFECA5, fade * 0.35);
                m.beam(start, end, 0.11 * fade, 0xFFFFFF, fade);
                helix(m, start, end, 0.35, age * 0.1, 0xFFF2A0, fade, 3);
                impact(m, end, side, vertical, age, color, fade, 1.2);
            }
            case SHATTERING_SOUND -> {
                for (int j = 0; j < 4; j++) {
                    double r = age * 0.5 - j * 1.0;
                    if (r < 0 || r > 10) continue;
                    Vec3 last = null;
                    for (int i = 0; i <= 24; i++) {
                        double a = -Math.PI / 6 + i * Math.PI / 72;
                        Vec3 p = start.add(direction.scale(Math.cos(a) * r)).add(side.scale(Math.sin(a) * r));
                        if (last != null) m.beam(last, p, 0.045, 0xECC8FF, fade * 0.8);
                        last = p;
                    }
                }
            }
            case WIND_BURST_STEP -> {
                // 狂风缠身：脚下一圈圈外扩的风环，身体被三股高速气流卷住。
                for (int i = 0; i < 3; i++)
                    m.ring(floor.add(0, 0.06 + i * 0.02, 0), X, Z, 0.75 + i * 0.38, 0.022,
                            (i % 2 == 0 ? 1 : -1) * age * 0.06 + i, 0xC9FFE9, bodyAlpha * (0.44 - i * 0.09));
                helix(m, floor.add(0, 0.12, 0), floor.add(0, 2.05, 0), 0.78, age * 0.17, 0xE4FFF4,
                        bodyAlpha * 0.5, 3);
                for (int i = 0; i < 5; i++) {
                    double a = age * 0.24 + i * 1.2566;
                    Vec3 p = floor.add(Math.cos(a) * 0.9, 0.35 + (age * 0.06 + i * 0.4) % 1.5, Math.sin(a) * 0.9);
                    m.beam(p, p.add(Math.cos(a + 1.3) * 0.5, 0.34, Math.sin(a + 1.3) * 0.5),
                            0.02, 0xC9FFE9, fade * 0.5);
                }
            }
            case WIND_BURIAL_CORRIDOR -> {
                if (start.distanceToSqr(end) < 0.02) {
                    // 风暴场：三圈反向旋转的气流环叠成一根直立的风柱。
                    for (int i = 0; i < 3; i++)
                        m.ring(start.add(0, 0.15 + i * 0.55, 0), X, Z, 2.2 - i * 0.45, 0.02,
                                (i % 2 == 0 ? 1 : -1) * age * 0.10, 0xE4FFF4, fade * (0.5 - i * 0.09));
                    helix(m, start, start.add(0, 3.0, 0), 1.6, age * 0.13, 0xC9FFE9, fade * 0.38, 2);
                } else {
                    // 风十字斩：一横一竖两片薄刃沿射程展开，末端收窄。
                    for (int i = 0; i <= 14; i++) {
                        double t = i / 14.0;
                        Vec3 p = start.lerp(end, t);
                        double w = 2.6 * Math.sin(Math.PI * Math.min(1.0, t * 1.15));
                        m.beam(p.subtract(side.scale(w)), p.add(side.scale(w)), 0.035, 0xE4FFF4, fade * 0.62);
                        m.beam(p.subtract(vertical.scale(w * 0.6)), p.add(vertical.scale(w * 0.6)),
                                0.030, 0xC9FFE9, fade * 0.52);
                    }
                    m.beam(start, end, 0.06, 0xFFFFFF, fade * 0.32);
                }
            }
            case LIGHTNESS, LIFE_BLESSING, GLIMMER, MUSICAL_INSPIRATION -> {
                sigil(m, floor, 0.8, age * 0.025, color, fade);
                helix(m, floor, floor.add(0, 2.1, 0), 0.6, age * 0.10, color, bodyAlpha * 0.7, v.spell.isIntermediate() ? 4 : 2);
                m.ring(anchor.add(0, 1.1, 0), X, Z, 0.55, 0.035, age * 0.02, color, bodyAlpha);
            }
            case METAL_DASH -> {
                for (int i = 0; i < 4; i++) m.beam(start.add(side.scale((i - 1.5) * 0.2)),
                        end.add(side.scale((i - 1.5) * 0.2)), 0.025, 0xDBE7F2, fade * 0.7);
            }
            case ELECTRIC_SHOCK -> {
                lightning(m, start, end, age, 0xFFE873, fade);
                impact(m, end, side, vertical, age, 0xFFF4B8, fade, 0.65);
            }
            case STATIC_FIELD -> {
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 2 + age * 0.04;
                    Vec3 p = anchor.add(Math.cos(a) * 0.7, -0.7, Math.sin(a) * 0.7);
                    lightning(m, p, p.add(0, 1.5, 0), age + i, 0xFFF4A5, fade * 0.7);
                }
            }
            case LIGHT_BOLT -> {
                double life = fade * Math.max(0, 1 - age / 12);
                m.beam(start, end, 0.035, 0xFFFFFF, life);
                m.beam(start, end, 0.13, 0xFFE587, life * 0.35);
                if (age < 4) m.crystal(start.lerp(end, age / 4), direction, 0.17, 0.75, 0xFFFFD7, life);
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 2;
                    m.beam(end, end.add(side.scale(Math.cos(a) * 0.65)).add(vertical.scale(Math.sin(a) * 0.65)),
                            0.035, 0xFFF4B0, life);
                }
            }
            case PURIFYING_LIGHT -> {
                m.beam(anchor.add(0, -0.8, 0), anchor.add(0, 2.8, 0), 0.24 * fade, 0xFFF4C2, bodyAlpha * 0.25);
                m.ring(anchor.add(0, 1.1, 0), X, Z, 0.65, 0.07, age * 0.03, 0xFFF6CF, bodyAlpha);
                impact(m, floor, X, Z, age, 0xFFE5A2, fade, 1.8);
            }
            case SONIC_WAVE -> {
                for (int i = 0; i < 4; i++) {
                    double r = age * 0.18 - i * 0.5;
                    if (r > 0 && r < 4.2) {
                        m.ring(start, X, Z, r, 0.04, 0, 0xE9DDEB, fade * (1 - r / 4.4) * 0.6);
                        m.ring(start, X, UP, r * 0.65, 0.018, 0, 0xFFDAFF, fade * (1 - r / 4.4) * 0.45);
                    }
                }
            }
            case RESONANCE -> {
                for (int i = 0; i < 3; i++) {
                    double radius = 0.45 + 0.25 * Math.sin(age * 0.3 + i * 1.2) + i * 0.18;
                    m.ring(anchor.add(0, (i - 1) * 0.45, 0), X, Z, radius, 0.035, age * 0.04, 0xE3C3EB, fade * 0.6);
                }
            }
            case MULTI_BARRIER_RAY -> {
                if (d.phase() == SpellVisualPayload.BARRIER) {
                    // 每座法阵两层反向旋转的高级阵纹，再叠实体法环、呼吸脉冲与六枚符钉。
                    Vec3 delta = end.subtract(start);
                    double radius = Math.clamp(delta.length(), 0.2, 1.2);
                    Vec3 normal = delta.lengthSqr() < 0.0001 ? Z : delta.normalize();
                    Vec3 axisU = UP.cross(normal);
                    if (axisU.lengthSqr() < 0.0001) axisU = X;
                    axisU = axisU.normalize();
                    Vec3 axisV = normal.cross(axisU).normalize();
                    sprites.advancedSigil(0, start, normal, radius * 1.28, age * 0.012, bodyAlpha * 0.85);
                    sprites.advancedSigil(1, start, normal, radius, -age * 0.019, bodyAlpha * 0.92);
                    m.ring(start, axisU, axisV, radius * 1.32, 0.016, age * 0.014, color, fade * 0.55);
                    m.ring(start, axisU, axisV, radius * 0.76, 0.011, -age * 0.022, 0xF3E2FF, fade * 0.45);
                    m.ring(start, axisU, axisV, radius * (1.0 + 0.05 * Math.sin(age * 0.10)), 0.010,
                            -age * 0.010, color, fade * 0.40);
                    for (int i = 0; i < 6; i++) {
                        double a = age * 0.014 + i * Math.PI / 3.0;
                        Vec3 p = start.add(axisU.scale(Math.cos(a) * radius * 1.16))
                                .add(axisV.scale(Math.sin(a) * radius * 1.16));
                        m.crystal(p, normal, radius * 0.070, radius * 0.11, 0xE7C4FF, fade * 0.85);
                    }
                } else if (d.phase() == SpellVisualPayload.SHOT) {
                    boolean connected = start.distanceToSqr(end) > 0.0004;
                    if (connected) {
                        m.beam(start, end, 0.10 * fade, color, fade * 0.50);
                        m.beam(start, end, 0.032, 0xFBEEFF, fade);
                        helix(m, start, end, 0.17, age * 0.45, color, fade * 0.85, 2);
                    }
                    impact(m, connected ? end : start, X, Z, age, color, fade, 1.5);
                } else {
                    // 展开瞬间：从手心射向首座法阵的一束引导光。
                    m.beam(start, end, 0.075, color, fade * 0.7);
                    m.beam(start, end, 0.022, 0xFFFFFF, fade);
                    impact(m, end, X, Z, age, color, fade, 1.1);
                }
            }
            case FLAME_CAGE -> {
                // 半径 3 格的烈焰牢笼：底部与顶部法环、二十根火柱和密集上升的火舌。
                double radius = 3.0;
                double spin = age * 0.010;
                for (int i = 0; i < 20; i++) {
                    double a = i * Math.PI * 2.0 / 20.0 + spin;
                    Vec3 base = end.add(Math.cos(a) * radius, 0.0, Math.sin(a) * radius);
                    double size = 0.50 + 0.06 * Math.sin(age * 0.16 + i);
                    sprites.flame(base, size, age + i * 3.0, fade * 0.95);
                    sprites.flame(base.add(0, 1.18, 0), size * 0.74, age + i * 5.0, fade * 0.70);
                    m.beam(base, base.add(0, 2.35, 0), 0.020, 0xFFB347, fade * 0.32);
                }
                m.ring(end, X, Z, radius, 0.045, age * 0.020, 0xFF8A34, fade * 0.55);
                m.ring(end.add(0, 2.35, 0), X, Z, radius * 0.95, 0.026, -age * 0.020, 0xFFD36A, fade * 0.45);
            }
            case SPRING_RETURN -> {
                // 12 秒的治疗泉水：地面涟漪覆盖半径 4 格，中央水柱持续上涌。
                double pulse = 0.85 + 0.15 * Math.sin(age * 0.14);
                m.ring(floor, X, Z, 4.0, 0.030, age * 0.008, 0x4FA8FF, fade * 0.24);
                m.ring(floor, X, Z, 3.3, 0.020, -age * 0.012, 0xBFE9FF, fade * 0.20);
                m.ring(floor, X, Z, 1.15 * pulse, 0.035, age * 0.030, 0x7FD4FF, fade * 0.55);
                m.beam(floor, floor.add(0, 2.4, 0), 0.30 * fade, 0x4FA8FF, fade * 0.32);
                m.beam(floor, floor.add(0, 2.9, 0), 0.10 * fade, 0xE8F7FF, fade * 0.50);
                for (int i = 0; i < 6; i++) {
                    double a = age * 0.03 + i * Math.PI / 3.0;
                    double r = 0.45 + 0.25 * Math.sin(age * 0.08 + i);
                    Vec3 p = floor.add(Math.cos(a) * r, 0.2 + (age * 0.05 + i * 0.7) % 2.0, Math.sin(a) * r);
                    m.crystal(p, UP, 0.07, 0.16, 0xBFE9FF, fade * 0.50);
                }
            }
            case ABYSS_DRAGON_STRIKE -> {
                if (d.phase() == SpellVisualPayload.BEAM) dragonBeam(m, start, end, age, fade);
            }
            case SUBZERO_DOMAIN -> {
                if (d.phase() == SpellVisualPayload.DOMAIN_FIELD) {
                    Entity caster = level.getEntity(d.owner());
                    Vec3 base = caster != null ? caster.position() : start;
                    int layers = (int)Math.round(Math.max(0.0, end.y - start.y));
                    domainField(m, sprites, base, layers, age, fade);
                } else if (d.phase() == SpellVisualPayload.DOMAIN_BURST) {
                    domainBurst(m, start, Math.max(1.0, end.y - start.y), age, d.duration(), fade);
                }
            }
            case THUNDER_TEMPEST -> {
                if (d.phase() == SpellVisualPayload.TEMPEST_BALL) {
                    Entity caster = level.getEntity(d.owner());
                    Vec3 base = caster != null
                            ? caster.getPosition(partial).add(0, caster.getBbHeight(), 0)
                            : start.subtract(0, 2.15, 0);
                    Vec3 ball = base.add(0, 2.15, 0);
                    int extras = (int)Math.round(Math.max(0.0, end.y - start.y));
                    // 头顶多重光圈：三层错位的能量阵环，雷球就是从这叠法阵里浮出来的。
                    for (int i = 0; i < 3; i++) {
                        double y = 1.35 + i * 0.36;
                        double radius = 1.55 - i * 0.27;
                        double spin = (i % 2 == 0 ? 1 : -1) * age * 0.022;
                        Vec3 ring = base.add(0, y, 0);
                        sprites.energySigil(2, ring, UP, radius, spin, fade * 0.52);
                        m.ring(ring, X, Z, radius * 1.05, 0.014, -spin * 1.4, 0xFFE45B, fade * 0.42);
                        m.ring(ring, X, Z, radius * 0.72, 0.010, spin * 1.8, 0xFFF7C0, fade * 0.34);
                    }
                    tempestBall(m, sprites, ball, extras, 1.15, age, fade);
                } else if (d.phase() == SpellVisualPayload.TEMPEST_STRIKE) {
                    tempestStrike(m, start, end, age, d.duration(), fade);
                } else if (d.phase() == SpellVisualPayload.TEMPEST_PULL) {
                    tempestPull(m, start, age, fade);
                } else if (d.phase() == SpellVisualPayload.TEMPEST_BLAST) {
                    tempestBlast(m, start, Math.max(1.0, end.y - start.y), age, d.duration(), fade);
                }
            }
            default -> { /* 非能量系已由材质动画绘制。 */ }
        }
    }


    /**
     * 藤条穿刺：一根贴地生长的带刺主藤，主干由两段叠宽的绿色光带组成，
     * 每隔两段长出一根朝上的尖刺与一小片嫩叶。
     *
     * <p>整根藤条一直在动：一根波从根部一路传到鞭梢，越靠前端摆得越开，末端还会缓慢地左右找目标。
     * 这样即使没有正在抽打，它看起来也是活的，而不是插在地上的一根木棍。</p>
     */
    private static void thornVine(Mesh m, Vec3 start, Vec3 end, double age, double fade) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.2) return;
        Vec3 dir = delta.scale(1.0 / length);
        Vec3 side = side(dir);
        double t1 = age * 0.075, t2 = age * 0.11;
        int segments = Math.max(14, (int)(length * 3.0));
        Vec3 last = null;
        for (int i = 0; i <= segments; i++) {
            double t = i / (double)segments;
            // 从根部传到鞭梢的行波：根部几乎不动，越往尖端甩得越开。
            double wobble = Math.sin(t * 6.2 - t1) * 0.46 * t + Math.sin(t * 2.1 + t1 * 0.4) * 0.18 * t;
            double lift = 0.14 + Math.sin(t * Math.PI) * 0.24 + Math.sin(t * 4.0 - t2) * 0.09 * t;
            Vec3 p = start.add(delta.scale(t)).add(side.scale(wobble)).add(0, lift, 0);
            if (last != null) {
                m.beam(last, p, 0.115, 0x244A1A, fade * 0.95);
                m.beam(last, p, 0.048, 0x74C247, fade * 0.9);
                m.beam(last, p, 0.020, 0xDFFFB0, fade * 0.55);
            }
            if (i > 0 && i % 2 == 0) {
                double flip = Math.sin(t * 13.0 + t2) >= 0 ? 1.0 : -1.0;
                Vec3 thornAxis = side.scale(flip * 0.55).add(0, 1.0, 0).normalize();
                m.crystal(p, thornAxis, 0.048, 0.40, 0xA6E070, fade * 0.92);
                m.beam(p, p.add(side.scale(flip * 0.34)).add(0, 0.18, 0), 0.020, 0x9BE06A, fade * 0.6);
            }
            last = p;
        }
        // 鞭梢的尖头：指向当前摆动的方向，看起来随时会甩出去。
        if (last != null) {
            double swing = Math.sin(-t1) * 0.5;
            Vec3 head = side.scale(swing * 0.8).add(dir.scale(0.25)).add(0, 0.55, 0).normalize();
            m.crystal(last, head, 0.085, 0.55, 0xC8F39A, fade * 0.95);
        }
        // 藤条根部一簇新芽，明确“从施法者身后长出来”。
        for (int i = 0; i < 5; i++) {
            double a = i * 1.2566 + age * 0.01;
            Vec3 p = start.add(Math.cos(a) * 0.30, 0.10, Math.sin(a) * 0.30);
            m.crystal(p, new Vec3(Math.cos(a) * 0.4, 1, Math.sin(a) * 0.4).normalize(),
                    0.035, 0.26, 0xC8F39A, fade * 0.8);
        }
    }

    /**
     * 藤条的一次抽打：藤尖朝目标甩过去再收回来，带一串残影与尖头。
     * 目标位置由服务端给出，所以敌人挪动时这次抽打也会落在它身上。
     */
    private static void vineLash(Mesh m, Vec3 from, Vec3 to, double age, int duration, double alpha) {
        Vec3 delta = to.subtract(from);
        if (delta.lengthSqr() < 0.04) return;
        double p = Math.clamp(age / Math.max(1.0, duration - 1.0), 0.0, 1.0);
        // 前三成时间甩出去，剩下的时间收回来。
        double reach = p < 0.3 ? p / 0.3 : 1.0 - (p - 0.3) / 0.7;
        double s = Math.clamp(reach, 0.0, 1.0);
        if (s <= 0.02) return;
        Vec3 side = side(delta);
        int steps = 20;
        Vec3 last = from;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double)steps;
            Vec3 p0 = from.lerp(to, t * s)
                    .add(side.scale(Math.sin(t * Math.PI) * 0.7 * (1.0 - t) * s))
                    .add(0, Math.sin(t * Math.PI) * 0.5 * s * (1.0 - t * 0.4), 0);
            m.beam(last, p0, 0.085, 0x244A1A, alpha * 0.9);
            m.beam(last, p0, 0.038, 0x8FE060, alpha * 0.85);
            last = p0;
        }
        Vec3 head = to.subtract(last);
        if (head.lengthSqr() > 0.01) m.crystal(last, head.normalize(), 0.075, 0.42, 0xD8FFA8, alpha);
        // 甩到尽头时补一圈冲击环，抽中感更明确。
        if (p > 0.25 && p < 0.6) {
            double ring = 0.4 + (p - 0.25) * 2.2;
            m.ring(to, X, Z, ring, 0.045, age * 0.25, 0xC8F39A, alpha * (1.0 - (p - 0.25) * 2.2) * 0.7);
        }
    }

    /** 卍之波：贴地的扩散环加一圈竖直光墙，半径按 6.6 格/秒线性推进。 */
    private static void bankaiWave(Mesh m, Vec3 center, double maxRadius, double age, int duration, double fade) {
        double progress = Math.clamp(age / Math.max(1.0, duration), 0.0, 1.0);
        double radius = maxRadius * progress;
        if (radius < 0.25) return;
        double alpha = fade * Math.min(1.0, (1.0 - progress) * 2.4 + 0.30);
        m.ring(center, X, Z, radius, 0.11, 0, 0x63B93F, alpha * 0.78);
        m.ring(center, X, Z, radius * 0.982, 0.045, 0, 0xE8FFC4, alpha * 0.62);
        for (int i = 0; i < 30; i++) {
            double a = i * Math.PI * 2.0 / 30.0;
            Vec3 base = center.add(Math.cos(a) * radius, 0.0, Math.sin(a) * radius);
            m.beam(base, base.add(0, 2.8, 0), 0.050, 0x7FD54A, alpha * 0.32);
        }
    }

    /** 生命之树领域：远处也能看见的边界环、边界光柱与缓慢旋转的四臂卍纹。 */
    private static void bankaiField(Mesh m, Vec3 center, double radius, double clock, double fade) {
        m.ring(center, X, Z, radius, 0.075, 0, 0x59B03A, fade * 0.45);
        m.ring(center, X, Z, radius * 0.985, 0.026, -clock * 0.0012, 0xC8F39A, fade * 0.34);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2.0 / 24.0 + clock * 0.0008;
            Vec3 base = center.add(Math.cos(a) * radius, 0.0, Math.sin(a) * radius);
            m.beam(base, base.add(0, 3.4, 0), 0.038, 0x6FBF44, fade * 0.20);
        }
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2.0 + clock * 0.0035;
            Vec3 p0 = center.add(Math.cos(a) * 2.0, 0.06, Math.sin(a) * 2.0);
            Vec3 p1 = center.add(Math.cos(a) * 7.0, 0.06, Math.sin(a) * 7.0);
            Vec3 p2 = center.add(Math.cos(a + 1.15) * 8.6, 0.06, Math.sin(a + 1.15) * 8.6);
            m.beam(p0, p1, 0.10, 0x6FBF44, fade * 0.55);
            m.beam(p1, p2, 0.085, 0x9BE06A, fade * 0.48);
        }
    }

    /**
     * 万剑归一：长 64 格、宽 24 格的高空裂口。
     *
     * <p>裂口本体是 8 帧动态贴图（灼白裂芯、青蓝外沿、锯齿状破口），铺在水平面与一层正交竖面上，
     * 从地面仰视、斜视、远处都能看到；再加上从裂口垂下的光柱与游走电弧，
     * 既交代“天上被劈开了一道口子”，也方便在几百格外找到它的位置。</p>
     */
    private static void skyRift(Mesh m, ClientSpellSprites sprites, Vec3 center, Vec3 end, double clock) {
        Vec3 flat = new Vec3(end.x - center.x, 0.0, end.z - center.z);
        double halfLength = flat.length();
        if (halfLength < 1.0) return;
        Vec3 dir = flat.scale(1.0 / halfLength);
        Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
        double halfWidth = Math.max(1.0, end.y - center.y);
        // 贴图本体：8 帧循环，裂口自己在缓慢扩张、扭动。
        sprites.skyRift(center, dir, side, halfLength, halfWidth, clock, 0.95);
        // 破口两侧的炽白辉光，让贴图边缘再多一层亮边。
        for (int i = 0; i < 24; i++) {
            double t = i / 24.0;
            double along = (t - 0.5) * 2.0;
            double taper = Math.sqrt(Math.max(0.0, 1.0 - along * along));
            double sway = Math.sin(t * 8.0 + clock * 0.02) * 0.8;
            Vec3 base = center.add(dir.scale(along * halfLength)).add(side.scale(sway));
            Vec3 left = base.add(side.scale(halfWidth * taper));
            Vec3 right = base.subtract(side.scale(halfWidth * taper));
            m.beam(left, left.add(0, 0.6, 0), 0.42, 0xFFFFFF, 0.30 * taper);
            m.beam(right, right.add(0, 0.6, 0), 0.42, 0xFFFFFF, 0.30 * taper);
        }
        // 垂下的光柱：缓慢明灭，斜着看、离得很远的人也能一眼找到裂口。
        for (int i = 0; i < 11; i++) {
            double t = (i + 0.5) / 11.0;
            double along = (t - 0.5) * 2.0;
            double sway = Math.sin(t * 8.0 + clock * 0.02) * 0.8;
            double pulse = 0.5 + 0.5 * Math.sin(clock * 0.06 + i * 1.7);
            Vec3 base = center.add(dir.scale(along * halfLength)).add(side.scale(sway));
            double drop = 26.0 + 22.0 * pulse;
            m.beam(base, base.subtract(0, drop, 0), 0.55 + pulse * 0.75, 0x9FD8FF, 0.055 + pulse * 0.075);
            m.beam(base, base.subtract(0, drop * 0.55, 0), 0.28, 0xE9FBFF, 0.10 + pulse * 0.10);
        }
        // 游走的电弧：偶尔横穿裂口，让这道口子看起来还在扩大。
        for (int i = 2; i < 60; i += 6) {
            double t = i / 64.0;
            double arc = clock * 0.04 + i;
            if (Math.sin(arc) < 0.35) continue;
            double along = (t - 0.5) * 2.0;
            double taper = Math.sqrt(Math.max(0.0, 1.0 - along * along));
            Vec3 base = center.add(dir.scale(along * halfLength));
            Vec3 a = base.add(side.scale(halfWidth * taper));
            Vec3 b = base.add(dir.scale(halfLength / 32.0)).add(side.scale(halfWidth * taper));
            Vec3 mid = a.lerp(b, 0.5).add(0, Math.sin(arc * 1.7) * 1.6, 0).add(side.scale(Math.cos(arc) * 1.1));
            m.beam(a, mid, 0.10, 0xDFF6FF, 0.75);
            m.beam(mid, b, 0.10, 0xDFF6FF, 0.75);
        }
    }

    /** 一把飞剑：朝飞行方向的剑身、金色剑格、剑柄与高速落下的拖尾。 */
    private static void flyingSword(Mesh m, Vec3 start, Vec3 end, double age, int duration, double alpha) {
        Vec3 delta = end.subtract(start);
        if (delta.lengthSqr() < 0.01) return;
        Vec3 dir = delta.normalize();
        double t = Math.clamp(age / Math.max(1.0, duration - 1.0), 0.0, 1.0);
        Vec3 pos = start.lerp(end, t);
        // 剑尖朝飞行方向（从天上落下时就是朝下的），剑格与剑柄在后面。
        Vec3 tip = pos.add(dir.scale(2.3));
        Vec3 guard = pos.subtract(dir.scale(0.6));
        Vec3 pommel = pos.subtract(dir.scale(1.35));
        // 外发光
        m.beam(pommel, tip, 0.34, 0x9FD8FF, 0.14 * alpha);
        // 剑身：宽的一层剑脊 + 一条炽白的锋
        m.beam(guard, tip, 0.125, 0xC6D8E6, 0.72 * alpha);
        m.beam(guard.lerp(tip, 0.12), tip, 0.048, 0xFFFFFF, 0.95 * alpha);
        // 剑格、剑柄与柄头宝珠
        Vec3 across = side(dir).scale(0.52);
        m.beam(guard.subtract(across), guard.add(across), 0.075, 0xFFD75E, 0.85 * alpha);
        m.beam(pommel, guard, 0.058, 0x8A6B2E, 0.78 * alpha);
        m.crystal(pommel, dir.scale(-1), 0.085, 0.18, 0xFFE9A8, 0.85 * alpha);
        // 高速落下的拖尾：越往后越淡，拉出破空的速度感。
        m.beam(pommel.subtract(dir.scale(7.5)), pommel, 0.36, 0x8FA9BC, 0.10 * alpha);
        m.beam(pommel.subtract(dir.scale(6.0)), pommel, 0.11, 0xE8F4FF, 0.22 * alpha);
    }

    /** 金钟罩：一口半透明的金色大钟罩住施法者，钟身有竖棱与缓慢转动的底环。 */
    private static void goldenBell(Mesh m, Vec3 anchor, double clock, double alpha) {
        // anchor 是生物身体中心，钟口落在脚边，整口钟高约 2.4 格。
        Vec3 base = anchor.subtract(0, 0.85, 0);
        double body = Math.min(1.0, alpha) * 0.55;
        if (body <= 0.01) return;
        double height = 2.45;
        double radius = 1.32;
        int rings = 9;
        Vec3[] last = new Vec3[12];
        for (int i = 0; i <= rings; i++) {
            double h = i / (double)rings;
            double taper = 1.0 - 0.46 * Math.pow(h, 1.35);
            double shoulder = Math.sqrt(Math.max(0.08, 1.0 - Math.pow(h, 3.2) * 0.82));
            double r = radius * taper * shoulder;
            Vec3 center = base.add(0.0, h * height + 0.05, 0.0);
            m.ring(center, X, Z, r, 0.032, 0.0, 0xFFD75E, body * 0.85);
            for (int spoke = 0; spoke < 12; spoke++) {
                double a = spoke * Math.PI / 6.0 + clock * 0.004;
                Vec3 p = center.add(Math.cos(a) * r, 0.0, Math.sin(a) * r);
                if (last[spoke] != null) m.beam(last[spoke], p, 0.026, 0xFFE9A8, body * 0.62);
                last[spoke] = p;
            }
            if (i == rings) m.crystal(center, UP, 0.16, 0.34, 0xFFF3C4, Math.min(1.0, alpha) * 0.9);
        }
        // 钟口的光环更亮，配合顶部宝珠让金钟的轮廓一眼可辨。
        m.ring(base.add(0, 0.07, 0), X, Z, radius, 0.075, clock * 0.01, 0xFFD75E, Math.min(1.0, alpha) * 0.95);
        m.ring(base.add(0, 0.07, 0), X, Z, radius * 0.94, 0.030, -clock * 0.015, 0xFFF6D0,
                Math.min(1.0, alpha) * 0.75);
    }

    /** 蓄力层数编码在 end.y 与 start.y 的差值里，客户端不需要额外状态。 */
    private static int chargeLayers(SpellVisualPayload data) {
        return Math.clamp((int)Math.round(data.end().y - data.start().y), 0, LightSpells.RIFT_MAX_CHARGE);
    }

    /**
     * 曜光裂界的前摇：身前一颗方块大小的光球，身体周围叠着多重法术阵。
     *
     * <p>每蓄一层，最外层就向外延展一层阵环；相邻两层的旋转方向相反、速度不同，
     * 越往外转得越快，看起来像整座法阵在被一层层拧开。</p>
     */
    private static void radiantCharge(Mesh m, ClientSpellSprites sprites, Entity owner, float partial,
                                      double age, int layers, double fade) {
        Vec3 feet = owner.getPosition(partial);
        Vec3 look = owner.getViewVector(partial);
        // 先判长度再归一化：归一化零向量会得到 NaN，把整块几何画飞。
        look = look.lengthSqr() < 0.01 ? Z : look.normalize();
        Vec3 orb = owner.getEyePosition(partial).add(look.scale(2.0));
        double height = Math.max(1.0, owner.getBbHeight());
        double pulse = 0.5 + 0.5 * Math.sin(age * 0.12);
        // 光球：三个正交光环 + 光刺 + 一条竖向光带，方块大小的球体在远处也看得见。
        for (int axis = 0; axis < 3; axis++) {
            Vec3 u = axis == 0 ? X : axis == 1 ? UP : Z;
            Vec3 v = axis == 0 ? Z : axis == 1 ? X : X;
            m.ring(orb, u, v, 0.50 + pulse * 0.05 + layers * 0.02, 0.055,
                    age * (0.05 + axis * 0.02) * (axis == 1 ? -1 : 1), 0xFFF4B0, fade * 0.85);
        }
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3.0 + age * 0.03;
            Vec3 dir = new Vec3(Math.cos(a), 0.35 * Math.sin(age * 0.06 + i), Math.sin(a)).normalize();
            m.crystal(orb, dir, 0.075, 0.42 + pulse * 0.10, 0xFFFFFF, fade * 0.72);
        }
        m.beam(orb.add(0, -0.55, 0), orb.add(0, 0.55, 0), 0.72, 0xFFEBA8, fade * 0.16);
        m.beam(orb.add(0, -0.42, 0), orb.add(0, 0.42, 0), 0.20, 0xFFFFFF, fade * 0.55);
        // 光球与施法者之间的引光带。
        Vec3 chest = owner.getEyePosition(partial).add(0, -0.35, 0);
        m.beam(chest, orb, 0.10, 0xFFF6D2, fade * 0.45);
        // 身体周围的多重法术阵：基础两层，之后每蓄一层向外多一层。
        int rings = 2 + layers;
        for (int i = 0; i < rings; i++) {
            double y = 0.08 + i * 0.15;
            double radius = 1.05 + i * 0.28 + (i == rings - 1 ? pulse * 0.04 : 0.0);
            double spin = (i % 2 == 0 ? 1 : -1) * age * (0.010 + i * 0.0035);
            sprites.energySigil(1, feet.add(0, y, 0), UP, radius, spin, fade * Math.max(0.18, 0.60 - i * 0.05));
            m.ring(feet.add(0, y + 0.004, 0), X, Z, radius, 0.013, spin, 0xFFF8CF,
                    fade * Math.max(0.16, 0.50 - i * 0.045));
            // 每一层再插六根符钉，层与层之间用斜撑连起来，看起来像一座旋转的光之塔。
            for (int k = 0; k < 6; k++) {
                double a = spin + k * Math.PI / 3.0;
                Vec3 p = feet.add(Math.cos(a) * radius, y, Math.sin(a) * radius);
                m.crystal(p, UP, 0.030, 0.16, 0xFFFDF0, fade * 0.42);
                if (i + 1 >= rings) continue;
                double next = radius + 0.28;
                Vec3 q = feet.add(Math.cos(a + 0.38) * next, y + 0.15, Math.sin(a + 0.38) * next);
                m.beam(p, q, 0.008, 0xFFE9A0, fade * 0.28);
            }
        }
        // 贴在光球背面的一枚竖阵，正面也能看出“多重法术阵”而不是一圈圈地砖。
        sprites.energySigil(1, orb.subtract(look.scale(0.55)), look.scale(-1),
                1.05 + layers * 0.10, -age * 0.024, fade * 0.55);
        // 无敌窗口期间脚下一圈更亮的守护环。
        m.ring(feet.add(0, 0.02, 0), X, Z, 1.45 + 0.05 * Math.sin(age * 0.10), 0.020, -age * 0.02,
                0xFFFFFF, fade * 0.34);
        // 上升的光尘，强调这十五秒一直在聚能。
        for (int i = 0; i < 7; i++) {
            double life = (age * 0.9 + i * 2.6) % 16.0 / 16.0;
            double a = i * 2.399 + age * 0.02;
            double radius = 0.55 + 0.85 * life;
            Vec3 p = feet.add(Math.cos(a) * radius, life * height, Math.sin(a) * radius);
            m.crystal(p, UP, 0.028, 0.14, 0xFFFFFF, fade * (1.0 - life) * 0.5);
        }
    }

    /** 光的庇护：脚下暖白环、八束上升光带与头顶光环，罩住被庇护的生物。 */
    private static void lightShelter(Mesh m, Vec3 anchor, double clock, double alpha) {
        double body = Math.min(1.0, alpha);
        if (body <= 0.01) return;
        Vec3 base = anchor.subtract(0, 0.85, 0);
        double pulse = 0.5 + 0.5 * Math.sin(clock * 0.10);
        m.ring(base.add(0, 0.06, 0), X, Z, 0.92 + pulse * 0.06, 0.035, clock * 0.012, 0xFFF3B0, body * 0.85);
        m.ring(base.add(0, 0.06, 0), X, Z, 0.72, 0.020, -clock * 0.018, 0xFFFFFF, body * 0.60);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0 + clock * 0.006;
            Vec3 p = base.add(Math.cos(a) * 0.84, 0.06, Math.sin(a) * 0.84);
            m.beam(p, p.add(0, 1.85 + pulse * 0.35, 0), 0.028, 0xFFF6C6, body * 0.26);
        }
        m.ring(anchor.add(0, 0.78, 0), X, Z, 0.40, 0.018, clock * 0.02, 0xFFFFFF, body * 0.45);
        m.crystal(anchor.add(0, 0.86, 0), UP, 0.075, 0.16, 0xFFFBE0, body * 0.55);
    }

    /** 光球爆裂：向外扩散的光环、边缘光刺与冲天光柱。 */
    private static void radiantBurst(Mesh m, Vec3 center, double radius, double age, int duration, double fade) {
        double t = Math.clamp(age / Math.max(1.0, duration), 0.0, 1.0);
        double wave = radius * (0.25 + 0.75 * t);
        m.ring(center, X, Z, wave, 0.35 * (1.0 - t) + 0.08, age * 0.06, 0xFFF3A8, fade * 0.85);
        m.ring(center, X, Z, wave * 0.72, 0.18, -age * 0.05, 0xFFFFFF, fade * 0.62);
        m.ring(center, X, Z, radius * 0.98, 0.10, 0, 0xFFF7D0, fade * 0.28);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12.0 + age * 0.02;
            Vec3 p = center.add(Math.cos(a) * wave, 0, Math.sin(a) * wave);
            m.crystal(p, UP, 0.18, 1.2 * (1.0 - t) + 0.30, 0xFFFCE4, fade * (1.0 - t) * 0.85);
        }
        double column = 2.0 + 12.0 * (1.0 - t);
        m.beam(center, center.add(0, column, 0), 0.90, 0xFFE9A0, fade * 0.34);
        m.beam(center, center.add(0, column, 0), 0.30, 0xFFFFFF, fade * 0.52);
    }

    private static void triggerRadiantFlash(long now) {
        radiantFlashAt = now;
    }

    /** 屏幕层的黑白闪光：每 2 刻翻转一次，共 16 刻，越到后面越淡。 */
    public static void renderRadiantFlash(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.options.hideGui) return;
        long elapsed = minecraft.level.getGameTime() - radiantFlashAt;
        if (elapsed < 0 || elapsed >= RADIANT_FLASH_TICKS) return;
        boolean white = (elapsed / 2) % 2 == 0;
        double fade = 1.0 - elapsed / (double)RADIANT_FLASH_TICKS;
        int alpha = (int)Math.round((white ? 0.62 : 0.55) * fade * 255.0);
        int color = (alpha << 24) | (white ? 0xFFFFFF : 0x000000);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), color);
    }

    /** 震荡鸣球：能量核外裹三层正交光环，隔一小会儿向外推一圈声纹，整体绕施法者缓慢公转。 */
    private static void sonicOrbs(Mesh m, Vec3 center, int orbs, double clock, double alpha) {
        if (alpha <= 0.01) return;
        double direction = clock * 0.021;
        for (int i = 0; i < orbs; i++) {
            double angle = direction + i * (Math.PI * 2.0 / 3.0);
            double bob = Math.sin(clock * 0.035 + i * 1.4) * 0.18;
            Vec3 pos = center.add(Math.cos(angle) * 2.3, bob, Math.sin(angle) * 2.3);
            m.crystal(pos, UP, 0.11, 0.17, 0xE997FF, alpha * 0.80);
            m.crystal(pos, X, 0.10, 0.15, 0xFFFFFF, alpha * 0.50);
            m.ring(pos, X, Z, 0.24, 0.020, clock * 0.12, 0xF3C6FF, alpha * 0.85);
            m.ring(pos, X, UP, 0.21, 0.016, -clock * 0.17, 0xFFFFFF, alpha * 0.65);
            m.ring(pos, Z, UP, 0.19, 0.014, clock * 0.21, 0xC9A0FF, alpha * 0.55);
            // 向外推的声纹：每一小段循环一次，看起来像球体在“波动”。
            for (int j = 0; j < 2; j++) {
                double t = (clock * 0.02 + j * 0.5 + i * 0.21) % 1.0;
                m.ring(pos, X, Z, 0.25 + t * 0.45, 0.014 * (1.0 - t) + 0.004,
                        clock * 0.10, 0xE997FF, alpha * (1.0 - t) * 0.45);
            }
            m.beam(center, pos, 0.012, 0xE997FF, alpha * 0.22);
        }
        m.ring(center, X, Z, 2.3, 0.012, -clock * 0.02, 0xC9A0FF, alpha * 0.20);
    }

    /** 高频声波：水平圆环与两个竖直椭圆环一起外扩，表现穿透方块的一整片声压。 */
    private static void sonicWave(Mesh m, Vec3 center, double radius, double age, int duration, double fade) {
        double t = Math.clamp(age / Math.max(1.0, duration), 0.0, 1.0);
        double wave = radius * (0.18 + 0.82 * t);
        m.ring(center, X, Z, wave, 0.10 * (1.0 - t) + 0.02, age * 0.05, 0xF3C6FF, fade * 0.80);
        m.ring(center, X, Z, wave * 0.80, 0.06 * (1.0 - t) + 0.01, -age * 0.06, 0xFFFFFF, fade * 0.55);
        m.ring(center, X, UP.scale(1.5), wave * 0.90, 0.045, age * 0.04, 0xE997FF, fade * 0.48);
        m.ring(center, Z, UP.scale(1.5), wave * 0.90, 0.035, -age * 0.05, 0xC9A0FF, fade * 0.40);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6.0;
            Vec3 p = center.add(Math.cos(a) * wave, 0, Math.sin(a) * wave);
            m.crystal(p, UP, 0.045, 0.16, 0xFFFFFF, fade * (1.0 - t) * 0.55);
        }
    }

    /** 反射波：一束白紫色声柱，沿途套着波纹环，终点是一格直径的圆形冲击。 */
    private static void sonicReflect(Mesh m, Vec3 start, Vec3 end, double age, int duration, double fade) {
        Vec3 delta = end.subtract(start);
        if (delta.lengthSqr() < 1.0E-4) return;
        double life = Math.max(0.0, 1.0 - age / Math.max(1.0, duration));
        Vec3 dir = delta.normalize();
        m.beam(start, end, 0.34 * life + 0.05, 0xE997FF, fade * 0.30);
        m.beam(start, end, 0.12 * life + 0.02, 0xFFFFFF, fade * 0.75);
        Vec3 u = side(dir), v = dir.cross(u).normalize();
        for (int i = 0; i <= 5; i++) {
            Vec3 p = start.lerp(end, i / 5.0);
            double ringRadius = 0.30 + 0.22 * ((age * 0.12 + i * 0.2) % 1.0);
            m.ring(p, u, v, ringRadius, 0.018, age * 0.1, 0xF3C6FF, fade * life * 0.55);
        }
        // 监守者声波同款的“直径一格”端面圆环。
        m.ring(end, u, v, 0.50, 0.030, age * 0.12, 0xFFFFFF, fade * life * 0.90);
        m.ring(end, u, v, 0.34, 0.016, -age * 0.18, 0xE997FF, fade * life * 0.70);
    }

    /** 鸣球触碰生物的小爆震：两圈快速外扩的冲击环。 */
    private static void sonicTouch(Mesh m, Vec3 center, double age, int duration, double fade) {
        double life = Math.max(0.0, 1.0 - age / Math.max(1.0, duration));
        if (life <= 0.01) return;
        m.ring(center, X, Z, 0.9 + age * 0.12, 0.030 * life + 0.005, age * 0.20, 0xF3C6FF, fade * life * 0.85);
        m.ring(center, X, UP, 0.7 + age * 0.10, 0.020 * life + 0.004, -age * 0.24, 0xFFFFFF, fade * life * 0.65);
        m.crystal(center, UP, 0.10, 0.18, 0xFFFFFF, fade * life * 0.60);
    }

    private static Vec3 side(Vec3 dir) { return dir.cross(Math.abs(dir.y) > 0.9 ? X : UP).normalize(); }
    private static void sigil(Mesh m, Vec3 p, double radius, double phase, int color, double alpha) {
        m.ring(p, X, Z, radius, 0.024, phase, color, alpha);
        m.ring(p.add(0, 0.012, 0), X, Z, radius * 0.76, 0.017, -phase, color, alpha * 0.7);
        for (int i = 0; i < 6; i++) {
            double a = phase + i * Math.PI / 3, b = a + Math.PI * 2 / 3;
            Vec3 p1 = p.add(Math.cos(a) * radius * 0.65, 0.02, Math.sin(a) * radius * 0.65);
            Vec3 p2 = p.add(Math.cos(b) * radius * 0.65, 0.02, Math.sin(b) * radius * 0.65);
            m.beam(p1, p2, 0.013, color, alpha * 0.6);
        }
    }
    private static void impact(Mesh m, Vec3 p, Vec3 u, Vec3 v, double age, int color, double alpha, double scale) {
        double r = scale * Math.min(1, age / 14.0 + 0.1);
        m.ring(p, u, v, r, 0.045, age * 0.1, color, alpha * 0.7);
        m.ring(p, u, v, r * 0.65, 0.02, -age * 0.1, 0xFFFFFF, alpha * 0.45);
    }
    private static void helix(Mesh m, Vec3 start, Vec3 end, double radius, double phase, int color, double alpha, int strands) {
        Vec3 direction = end.subtract(start).normalize(), side = side(direction), up = direction.cross(side).normalize();
        if (direction.lengthSqr() < 0.01) return;
        for (int strand = 0; strand < strands; strand++) {
            Vec3 last = null;
            for (int i = 0; i <= 24; i++) {
                double t = i / 24.0, a = phase + t * Math.PI * 4 + strand * Math.PI * 2 / strands;
                Vec3 p = start.lerp(end, t).add(side.scale(Math.cos(a) * radius)).add(up.scale(Math.sin(a) * radius));
                if (last != null) m.beam(last, p, 0.018, color, alpha * 0.7);
                last = p;
            }
        }
    }
    /** 深渊龙击：水龙法阵喷射出的高压水柱，三条螺旋水带在末端收束成龙头，沿途留下水痕。 */
    private static void dragonBeam(Mesh m, Vec3 start, Vec3 end, double age, double fade) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.05) return;
        Vec3 dir = delta.normalize();
        // 喷射开始的前半秒里水柱从法阵“长”到尽头，看起来像持续延伸的压力流。
        double reach = Math.min(length, 1.2 + age * 9.0);
        Vec3 head = start.add(dir.scale(reach));
        double headFade = fade * Math.min(1.0, 0.35 + age * 0.25);
        Vec3 axisU = UP.cross(dir);
        if (axisU.lengthSqr() < 0.0001) axisU = X;
        axisU = axisU.normalize();
        Vec3 axisV = dir.cross(axisU).normalize();
        // 水龙法阵：三层反向旋转的阵环与六枚水符钉。
        m.ring(start, axisU, axisV, 1.05, 0.035, age * 0.05, 0x4FA8FF, fade * 0.75);
        m.ring(start, axisU, axisV, 0.78, 0.022, -age * 0.07, 0xBFE9FF, fade * 0.85);
        m.ring(start, axisU, axisV, 0.42, 0.014, age * 0.10, 0xFFFFFF, fade * 0.70);
        for (int i = 0; i < 6; i++) {
            double a = age * 0.06 + i * Math.PI / 3.0;
            Vec3 p = start.add(axisU.scale(Math.cos(a))).add(axisV.scale(Math.sin(a)));
            m.crystal(p, dir, 0.075, 0.20, 0xE8F7FF, fade * 0.80);
        }
        // 高压水柱本体：外淡蓝管、中层亮蓝、内白芯，直径约 1.35 格。
        m.beam(start, head, 0.68, 0x2F86E8, fade * 0.26);
        m.beam(start, head, 0.46, 0x64C8FF, fade * 0.42);
        m.beam(start, head, 0.16, 0xFFFFFF, fade * 0.72);
        // 龙形水带：三条缠绕螺旋，末端逐渐收束。
        for (int strand = 0; strand < 3; strand++) {
            double phase = age * 0.22 + strand * Math.PI * 2.0 / 3.0;
            Vec3 last = null;
            for (int i = 0; i <= 40; i++) {
                double t = i / 40.0;
                double radius = 0.55 * (1.0 - t * 0.35);
                double a = phase + t * Math.PI * 3.2;
                Vec3 p = start.lerp(head, t).add(axisU.scale(Math.cos(a) * radius))
                        .add(axisV.scale(Math.sin(a) * radius));
                if (last != null) m.beam(last, p, 0.075, 0xBFE9FF, fade * 0.48);
                last = p;
            }
        }
        // 龙头：末端锥形水刃加四根獠牙。
        Vec3 tip = head.add(dir.scale(0.55));
        m.crystal(head, dir, 0.55, 1.35, 0x8FE0FF, headFade * 0.72);
        m.crystal(head, dir, 0.28, 0.95, 0xFFFFFF, headFade * 0.50);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2.0 + age * 0.05;
            Vec3 base = head.add(axisU.scale(Math.cos(a) * 0.45)).add(axisV.scale(Math.sin(a) * 0.45));
            m.beam(base, tip, 0.035, 0xE8F7FF, headFade * 0.60);
        }
        // 水痕：每 3 格一圈淡淡的涟漪。
        for (double along = 3.0; along < reach; along += 3.0) {
            m.ring(start.add(dir.scale(along)), axisU, axisV, 0.72, 0.012,
                    age * 0.02 + along, 0x64C8FF, fade * 0.16);
        }
    }

    /**
     * 零下领域叠层：地面双层冰霜大阵 + 环阵公转的雪晶 + 每层一圈反向阵纹，
     * 层与层之间用斜向光束连成塔身，顶端收一枚冰晶，堆到家就是一座高阶仪式塔。
     */
    private static void domainField(Mesh m, ClientSpellSprites sprites, Vec3 base, int layers, double age, double fade) {
        // 底层：大阵正转、内阵反转，再压两圈实体光环把贴图的轮廓勾出来。
        sprites.frostSigil(0, base.add(0, 0.012, 0), UP, 2.05, age * 0.010, fade * 0.60);
        sprites.frostSigil(1, base.add(0, 0.022, 0), UP, 1.34, -age * 0.016, fade * 0.68);
        m.ring(base, X, Z, 1.72, 0.020, age * 0.014, 0x8FE8FF, fade * 0.55);
        m.ring(base, X, Z, 2.16, 0.012, -age * 0.008, 0xEAFBFF, fade * 0.34);
        // 呼吸的寒气柱：把法阵和施法者连成一体。
        double column = 1.9 + 0.14 * Math.sin(age * 0.08);
        m.beam(base, base.add(0, column, 0), 0.46, 0x4FA8FF, fade * 0.14);
        m.beam(base, base.add(0, column, 0), 0.13, 0xF0FBFF, fade * 0.30);
        // 十二枚雪晶绕着底层法阵公转，叠层越多转得越快。
        double orbit = age * (0.018 + layers * 0.0025);
        for (int i = 0; i < 12; i++) {
            double a = orbit + i * Math.PI / 6.0;
            double r = 1.78 + 0.07 * Math.sin(age * 0.09 + i);
            Vec3 p = base.add(Math.cos(a) * r, 0.10 + 0.10 * Math.sin(age * 0.07 + i * 1.7), Math.sin(a) * r);
            m.crystal(p, UP, 0.055, 0.22, 0xF2FDFF, fade * 0.62);
        }
        // 每一层：冰霜阵环 + 六根符钉 + 与下一层交错的斜撑，堆成旋转的塔身。
        for (int i = 0; i < layers; i++) {
            double y = 0.26 + i * 0.28;
            double radius = 1.46 - i * 0.040;
            double layerSpin = (i % 2 == 0 ? 1 : -1) * age * (0.018 + i * 0.0025);
            sprites.frostSigil(i % 2 == 0 ? 0 : 1, base.add(0, y, 0), UP, radius * 0.96, layerSpin, fade * 0.52);
            m.ring(base.add(0, y, 0), X, Z, radius, 0.012, layerSpin, 0xBFF0FF, fade * 0.52);
            for (int k = 0; k < 6; k++) {
                double a = layerSpin + k * Math.PI / 3.0;
                Vec3 p = base.add(Math.cos(a) * radius, y, Math.sin(a) * radius);
                m.crystal(p, UP, 0.040 + 0.004 * i, 0.16, 0xEAFBFF, fade * 0.55);
                if (i + 1 >= layers) continue;
                double next = radius - 0.040, nextY = y + 0.28;
                Vec3 q = base.add(Math.cos(a + 0.42) * next, nextY, Math.sin(a + 0.42) * next);
                m.beam(p, q, 0.010, 0x9FE8FF, fade * 0.34);
            }
        }
        // 塔尖：最后一层上方收一枚悬停冰晶与一圈亮环。
        if (layers > 0) {
            Vec3 crown = base.add(0, 0.26 + layers * 0.28, 0);
            m.ring(crown, X, Z, 0.86, 0.010, -age * 0.030, 0xFFFFFF, fade * 0.45);
            m.crystal(crown, UP, 0.15, 0.52, 0xEAFBFF, fade * 0.68);
            m.crystal(crown.add(0, -0.18, 0), UP, 0.07, 0.30, 0xFFFFFF, fade * 0.45);
        }
    }

    /** 零下领域爆发：向外扩散的极寒冲击波、边缘冰墙与中心寒气柱。 */
    private static void domainBurst(Mesh m, Vec3 center, double radius, double age, int duration, double fade) {
        double t = Math.clamp(age / Math.max(1.0, duration), 0.0, 1.0);
        double wave = radius * (0.15 + 0.85 * t);
        m.ring(center, X, Z, wave, 0.09 * (1 - t) + 0.02, age * 0.05, 0xBFF1FF, fade * 0.75);
        m.ring(center.add(0, 0.05, 0), X, Z, wave * 0.75, 0.05, -age * 0.04, 0xFFFFFF, fade * 0.55);
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8.0 + age * 0.01;
            Vec3 p = center.add(Math.cos(a) * wave, 0, Math.sin(a) * wave);
            m.crystal(p, UP, 0.16, 0.9 * (1 - t) + 0.25, 0xD8F6FF, fade * (1 - t) * 0.8);
        }
        double column = 1.5 + 5.0 * (1 - t);
        m.beam(center, center.add(0, column, 0), 0.55 * fade, 0x8FE0FF, fade * 0.30);
        m.beam(center, center.add(0, column, 0), 0.18 * fade, 0xFFFFFF, fade * 0.45);
        m.ring(center, X, Z, radius * 0.98, 0.03, 0, 0x9FE8FF, fade * 0.25);
    }

    // ───────────────────────── 雷系：伏特状态与天雷滚滚 ─────────────────────────

    /** 伏特状态：脚下电网、缠绕全身的折线电弧与上升电火花。 */
    private static void drawVoltBody(Mesh m, LivingEntity entity, ClientLevel level, float partial) {
        double age = entity.tickCount + partial;
        Vec3 base = entity.position();
        double height = Math.max(0.9, entity.getBbHeight());
        double width = Math.max(0.28, entity.getBbWidth() * 0.55);
        // 脚下电网：两圈反向旋转的电环，跑动时整体跟着身体走。
        m.ring(base.add(0, 0.03, 0), X, Z, width + 0.55, 0.020, age * 0.05, 0xFFE45B, 0.42);
        m.ring(base.add(0, 0.06, 0), X, Z, width + 0.85, 0.012, -age * 0.034, 0xFFF7C0, 0.28);
        // 身体：八条向上缠绕的折线电弧，每 2 刻重新抖一次，看起来一直在放电。
        for (int i = 0; i < 8; i++) {
            double angle = age * 0.14 + i * Math.PI / 4.0;
            double radius = width + 0.30 + 0.10 * Math.sin(i * 2.3 + age * 0.1);
            double y0 = 0.08 + (i % 4) * height * 0.20;
            double y1 = Math.min(height, y0 + height * 0.36);
            Vec3 from = base.add(Math.cos(angle) * radius, y0, Math.sin(angle) * radius);
            Vec3 to = base.add(Math.cos(angle + 1.15) * radius * 0.72, y1, Math.sin(angle + 1.15) * radius * 0.72);
            lightning(m, from, to, age + i * 1.9, 0xFFF19A, 0.52);
        }
        // 头顶：一小圈悬停光环，和脚下的电网一呼一应。
        Vec3 crown = base.add(0, height + 0.22, 0);
        m.ring(crown, X, Z, width + 0.42, 0.014, age * 0.06, 0xFFF7C0, 0.40);
        for (int i = 0; i < 4; i++) {
            double angle = -age * 0.09 + i * Math.PI / 2.0;
            Vec3 p = crown.add(Math.cos(angle) * (width + 0.42), 0.04 * Math.sin(age * 0.2 + i), Math.sin(angle) * (width + 0.42));
            m.crystal(p, UP, 0.045, 0.16, 0xFFFFFF, 0.55);
        }
    }

    /** 天雷滚滚前摇：头顶多重光圈与法阵逐层展开，雷球随读条长大。 */
    private static void tempestCharge(Mesh m, ClientSpellSprites sprites, Entity owner, float partial,
                                      double age, int duration, double fade) {
        Vec3 base = owner.getPosition(partial);
        double height = owner.getBbHeight();
        double progress = Math.clamp(age / Math.max(1.0, duration), 0.0, 1.0);
        double reveal = 0.35 + 0.65 * progress;
        for (int i = 0; i < 3; i++) {
            double y = height + 0.75 + i * 0.44 * reveal;
            double radius = (1.55 - i * 0.27) * reveal;
            double spin = (i % 2 == 0 ? 1 : -1) * age * 0.024;
            Vec3 ring = base.add(0, y, 0);
            sprites.energySigil(2, ring, UP, radius, spin, fade * 0.52);
            m.ring(ring, X, Z, radius * 1.05, 0.014, -spin * 1.4, 0xFFE45B, fade * 0.42);
            m.ring(ring, X, Z, radius * 0.72, 0.010, spin * 1.8, 0xFFF7C0, fade * 0.34);
        }
        Vec3 ball = base.add(0, height + 2.15, 0);
        tempestBall(m, sprites, ball, ClientSpellState.tempestExtras(), 0.42 + 0.73 * progress, age, fade * reveal);
    }

    /** 球状闪电本体：交叉光核、三层正交光环、外甩电弧与追加的小雷球。 */
    private static void tempestBall(Mesh m, ClientSpellSprites sprites, Vec3 center, int extras,
                                    double radius, double age, double fade) {
        if (fade <= 0.01 || radius <= 0.02) return;
        m.crystal(center, UP, radius * 0.62, radius * 0.92, 0xFFF4B0, fade * 0.55);
        m.crystal(center, X, radius * 0.55, radius * 0.86, 0xFFFFFF, fade * 0.45);
        m.crystal(center, Z, radius * 0.48, radius * 0.78, 0xFFE45B, fade * 0.35);
        m.ring(center, X, Z, radius, 0.055, age * 0.55, 0xFFE45B, fade * 0.78);
        m.ring(center, X, UP, radius * 0.92, 0.036, -age * 0.72, 0xFFF7C0, fade * 0.62);
        m.ring(center, Z, UP, radius * 0.84, 0.028, age * 0.90, 0xBFE9FF, fade * 0.52);
        // 向外甩出的电弧：球越转越快，电弧也跟着翻。
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5.0 + age * 0.22;
            double b = a * 1.7 + i;
            Vec3 out = center.add(Math.cos(a) * radius * 1.45, Math.sin(b) * radius * 1.2,
                    Math.sin(a) * radius * 1.45);
            lightning(m, center, out, age + i * 3.1, 0xFFF19A, fade * 0.55);
        }
        sprites.energySigil(2, center.add(0, -radius * 0.18, 0), UP, radius * 2.1, age * 0.03, fade * 0.38);
        // 追加雷球：略小一圈，绕着主球公转。
        for (int i = 0; i < extras; i++) {
            double a = age * 0.07 + i * Math.PI + Math.PI / 2.0;
            double orbit = radius * 1.85;
            Vec3 small = center.add(Math.cos(a) * orbit, Math.sin(a * 1.3) * radius * 0.45, Math.sin(a) * orbit);
            double sr = radius * (i == 0 ? 0.66 : 0.52);
            m.crystal(small, UP, sr * 0.6, sr * 0.88, 0xFFFFFF, fade * 0.55);
            m.ring(small, X, Z, sr, 0.030, -age * 0.80, 0xFFF7C0, fade * 0.55);
            m.ring(small, X, UP, sr * 0.90, 0.022, age * 0.95, 0xFFE45B, fade * 0.45);
            for (int k = 0; k < 4; k++) {
                double c = k * Math.PI / 2.0 + age * 0.30;
                Vec3 out = small.add(Math.cos(c) * sr * 1.4, Math.sin(c * 1.6) * sr, Math.sin(c) * sr * 1.4);
                lightning(m, small, out, age + i * 5.3 + k, 0xFFE873, fade * 0.45);
            }
        }
    }

    /** 天雷电击残影：1 秒内逐渐淡去的电弧 + 命中点残留电环。 */
    private static void tempestStrike(Mesh m, Vec3 from, Vec3 to, double age, int duration, double fade) {
        double life = Math.max(0.0, 1.0 - age / Math.max(1.0, duration));
        if (life <= 0.01) return;
        lightning(m, from, to, age, 0xFFF19A, fade * life);
        lightning(m, from, to, age + 2.7, 0xFFFFFF, fade * life * 0.55);
        m.ring(to, X, Z, 0.55 + age * 0.035, 0.030 * life + 0.006, age * 0.22, 0xFFE873, fade * life * 0.8);
        m.beam(to.add(0, -0.8, 0), to.add(0, 1.0, 0), 0.09 * life, 0xFFF7C0, fade * life * 0.55);
    }

    /** 电磁漩涡：多层反向旋转的吸积环与向内收束的电弧。 */
    private static void tempestPull(Mesh m, Vec3 center, double age, double fade) {
        for (int i = 0; i < 5; i++) {
            double radius = 0.6 + ((age * 0.06 + i * 0.7) % 1.0) * 3.0;
            double y = (i - 2) * 0.30;
            m.ring(center.add(0, y, 0), X, Z, radius, 0.045, age * 0.08 * (i % 2 == 0 ? 1 : -1),
                    0xBFE9FF, fade * 0.45);
        }
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5.0 + age * 0.16;
            double radius = 2.2 + ((age * 0.10 + i * 0.31) % 1.0) * 4.0;
            Vec3 p = center.add(Math.cos(a) * radius, Math.sin(a * 1.7) * 1.3, Math.sin(a) * radius);
            Vec3 q = center.add(Math.cos(a + 0.55) * Math.max(0.5, radius - 1.7),
                    Math.sin(a * 1.7) * 0.7, Math.sin(a + 0.55) * Math.max(0.5, radius - 1.7));
            lightning(m, p, q, age + i, 0xFFF19A, fade * 0.42);
        }
        m.crystal(center, UP, 0.50, 0.85, 0xFFFFFF, fade * 0.50);
    }

    /** 电磁爆炸：向外扩散的电环、放射电弧与冲天雷柱。 */
    private static void tempestBlast(Mesh m, Vec3 center, double radius, double age, int duration, double fade) {
        double t = Math.clamp(age / Math.max(1.0, duration), 0.0, 1.0);
        double wave = radius * (0.15 + 0.85 * t);
        m.ring(center, X, Z, wave, 0.10 * (1 - t) + 0.02, age * 0.06, 0xFFF19A, fade * 0.75);
        m.ring(center.add(0, 0.08, 0), X, Z, wave * 0.72, 0.05, -age * 0.05, 0xFFFFFF, fade * 0.55);
        for (int i = 0; i < 18; i++) {
            double a = i * Math.PI / 9.0 + age * 0.02;
            Vec3 p = center.add(Math.cos(a) * wave, 0, Math.sin(a) * wave);
            lightning(m, center, p, age + i, 0xFFE873, fade * (1 - t) * 0.6);
        }
        double column = 1.0 + 7.0 * (1 - t);
        m.beam(center, center.add(0, column, 0), 0.45 * fade, 0xFFF7C0, fade * 0.30);
        m.beam(center, center.add(0, column, 0), 0.14 * fade, 0xFFFFFF, fade * 0.50);
    }

    /** 毒刺飞行：细长的绿色毒晶沿航线冲刺，尾迹是一条酸液带。 */
    private static void venomNeedle(Mesh m, Vec3 start, Vec3 end, double age, int duration, double fade) {
        Vec3 head = start.lerp(end, Math.clamp(age / Math.max(1.0, duration), 0.0, 1.0));
        Vec3 dir = end.subtract(start);
        if (dir.lengthSqr() < 1.0E-4) return;
        dir = dir.normalize();
        m.beam(start, head, 0.055, 0x9ACD45, fade * 0.28);
        m.beam(start, head, 0.020, 0xD7F28A, fade * 0.45);
        // 刺身：一枚长毒晶配一圈外翻的倒钩，远看也认得出是“刺”。
        m.crystal(head, dir, 0.105, 0.95, 0xD7F28A, fade * 0.88);
        m.crystal(head, dir, 0.050, 1.35, 0xFFFFFF, fade * 0.55);
        for (int i = 0; i < 5; i++) {
            double a = i * 1.2566 + age * 0.30;
            Vec3 p = head.add(Math.cos(a) * 0.22, Math.sin(a * 1.7) * 0.16, Math.sin(a) * 0.22);
            m.crystal(p, dir, 0.035, 0.18, 0x8FBF3A, fade * 0.50);
        }
    }

    /** 毒雾：地面反向旋转的气环，叠一座由下往上收拢的半透明毒穹。 */
    private static void venomMist(Mesh m, Vec3 center, double radius, double clock, double fade) {
        double base = Math.min(1.0, fade) * Math.min(1.0, radius / 6.0);
        if (base <= 0.01) return;
        for (int i = 0; i < 3; i++) {
            double r = radius * (1.0 - i * 0.15);
            m.ring(center.add(0, 0.05 + i * 0.035, 0), X, Z, r, 0.055 - i * 0.014,
                    clock * (0.006 + i * 0.004) * (i % 2 == 0 ? 1 : -1), 0x9ACD45,
                    base * (0.42 - i * 0.10));
        }
        double height = Math.min(radius * 0.78, 16.0);
        Vec3[] last = new Vec3[12];
        for (int layer = 0; layer <= 5; layer++) {
            double h = layer / 5.0;
            Vec3 ring = center.add(0, height * h, 0);
            double r = radius * Math.sqrt(Math.max(0.0, 1.0 - h * h));
            double alpha = base * (0.22 - h * 0.13);
            m.ring(ring, X, Z, r, 0.040, clock * 0.010 * (layer % 2 == 0 ? 1 : -1), 0xB9E06A, alpha);
            for (int spoke = 0; spoke < 12; spoke++) {
                double a = spoke * Math.PI / 6.0 + clock * 0.004;
                Vec3 p = ring.add(Math.cos(a) * r, 0.0, Math.sin(a) * r);
                if (layer > 0 && last[spoke] != null)
                    m.beam(last[spoke], p, 0.020, 0x8FBF3A, base * 0.09);
                last[spoke] = p;
            }
        }
        // 雾里上下翻的孢子晶，让毒雾有厚度。
        for (int i = 0; i < 8; i++) {
            double a = i * 0.7854 + clock * 0.006;
            double rise = ((clock * 0.35 + i * 1.7) % Math.max(1.0, height));
            double r = radius * (0.25 + 0.6 * Math.sqrt((i + 1) / 8.0));
            Vec3 p = center.add(Math.cos(a) * r, 0.25 + rise, Math.sin(a) * r);
            m.crystal(p, UP, 0.060, 0.20, 0xD7F28A, base * 0.50);
        }
    }

    private static void lightning(Mesh m, Vec3 start, Vec3 end, double age, int color, double alpha) {
        Vec3 direction = end.subtract(start).normalize(), side = side(direction), up = direction.cross(side).normalize();
        Vec3 last = start;
        for (int i = 1; i <= 12; i++) {
            double t = i / 12.0, jitter = Math.sin(Math.PI * t) * 0.28;
            double seed = i * 19.73 + Math.floor(age / 2) * 11.7;
            Vec3 p = start.lerp(end, t).add(side.scale(Math.sin(seed) * jitter)).add(up.scale(Math.cos(seed * 1.7) * jitter));
            m.beam(last, p, 0.065, color, alpha * 0.5);
            m.beam(last, p, 0.018, 0xFFFFEE, alpha);
            if (i % 3 == 0) m.beam(p, p.add(side.scale(Math.sin(seed) * 0.55)).add(up.scale(0.25)), 0.015, color, alpha * 0.6);
            last = p;
        }
    }
    private static void shield(Mesh m, Vec3 center, int panels, double radius, double age, int color, double alpha) {
        double formation = Math.min(1, age / 6);
        for (int i = 0; i < panels; i++) {
            double a = i * Math.PI * 2 / panels + age * 0.008, half = Math.PI / panels * 0.78;
            Vec3 left = new Vec3(Math.cos(a - half) * radius, 0, Math.sin(a - half) * radius);
            Vec3 right = new Vec3(Math.cos(a + half) * radius, 0, Math.sin(a + half) * radius);
            Vec3 p1 = center.add(left).add(0, -0.65 * formation, 0), p2 = center.add(right).add(0, -0.65 * formation, 0);
            Vec3 p3 = center.add(right).add(0, 0.7 * formation, 0), p4 = center.add(left).add(0, 0.7 * formation, 0);
            m.quad(p1, p2, p3, p4, color, alpha * 0.10);
            m.beam(p1, p2, 0.025, color, alpha); m.beam(p2, p3, 0.025, color, alpha);
            m.beam(p3, p4, 0.025, color, alpha); m.beam(p4, p1, 0.025, color, alpha);
        }
    }

    private static final class Mesh {
        private final VertexConsumer out;
        private final Matrix4f matrix;
        private final Vec3 camera;
        Mesh(VertexConsumer out, Matrix4f matrix, Vec3 camera) { this.out = out; this.matrix = matrix; this.camera = camera; }
        void vertex(Vec3 p, int rgb, double alpha) {
            Vec3 relative = p.subtract(camera);
            out.addVertex(matrix, (float)relative.x, (float)relative.y, (float)relative.z)
                    .setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, (int)(Math.clamp(alpha, 0, 1) * 255));
        }
        void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, double alpha) {
            vertex(a, color, alpha); vertex(b, color, alpha); vertex(c, color, alpha); vertex(d, color, alpha);
        }
        void beam(Vec3 a, Vec3 b, double width, int color, double alpha) {
            Vec3 delta = b.subtract(a);
            if (delta.lengthSqr() < 0.00001 || width < 0.0001) return;
            Vec3 dir = delta.normalize(), u = side(dir).scale(width), v = dir.cross(u);
            quad(a.add(u), b.add(u), b.subtract(u), a.subtract(u), color, alpha);
            quad(a.add(v), b.add(v), b.subtract(v), a.subtract(v), color, alpha);
        }
        void ring(Vec3 p, Vec3 u, Vec3 v, double radius, double width, double phase, int color, double alpha) {
            for (int i = 0; i < 32; i++) {
                double a = phase + Math.PI * 2 * i / 32, b = phase + Math.PI * 2 * (i + 1) / 32;
                Vec3 da = u.scale(Math.cos(a)).add(v.scale(Math.sin(a))), db = u.scale(Math.cos(b)).add(v.scale(Math.sin(b)));
                quad(p.add(da.scale(radius - width)), p.add(db.scale(radius - width)),
                        p.add(db.scale(radius + width)), p.add(da.scale(radius + width)), color, alpha);
            }
        }
        void crystal(Vec3 p, Vec3 axis, double radius, double length, int color, double alpha) {
            Vec3 u = side(axis), v = axis.cross(u).normalize(), tip = p.add(axis.scale(length)), base = p.subtract(axis.scale(length * 0.65));
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2, b = (i + 1) * Math.PI / 2;
                Vec3 p1 = p.add(u.scale(Math.cos(a) * radius)).add(v.scale(Math.sin(a) * radius));
                Vec3 p2 = p.add(u.scale(Math.cos(b) * radius)).add(v.scale(Math.sin(b) * radius));
                quad(tip, p1, p2, p2, color, alpha * (0.55 + i * 0.1));
                quad(base, p2, p1, p1, color, alpha * (0.35 + i * 0.1));
            }
        }
    }
    private ClientSpellVisuals() { }
}
