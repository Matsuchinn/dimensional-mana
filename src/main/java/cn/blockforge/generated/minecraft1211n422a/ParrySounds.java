package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * 完美招架的音效池。
 *
 * <p>sounds.json 里的 {@code parry_perfect} 现在有十四段素材、每次弹刀随机挑一段：
 * 最早那六段（铁砧、钟、裂音、回声、玻璃、锣）是玩家点名要听回来的「像剑碰撞」的音色；
 * 后来重画的六段剑鸣（标准、双刀、长鸣、重剑、极短、火花）保持钢铁对撞的短促起音；
 * 最后两段（deflect、clang）是 r204 从 Freesound 上挑的免费 CC0 剑击采样，照只狼震刀那声「铛」
 * 收的尾：削掉静音和低频、音量对齐池内其它素材、转成 24kHz 单声道 ogg。</p>
 *
 * <p>播放时整组再做 ±6% 音高抖动，偶尔补一记更高的回响和一点水晶泛音，
 * 十四段素材配上抖动，连续弹几十次也不会重样。</p>
 */
public final class ParrySounds {
    /** 剑鸣本体在 {@link GeneratedMod#PARRY_PERFECT} 注册，这里只负责按随机规则播放。 */

    /**
     * 播一整套完美招架音效：主剑鸣 + 随机高音回响 + 偶尔一点水晶泛音。
     *
     * @param at 两把刀相交的位置（玩家与怪物中点），做成世界里的立体声，走路时能听出方向。
     */
    public static void perfect(ServerLevel level, Vec3 at, RandomSource random) {
        // ±6% 音高抖动：同一段素材听起来像换了一把剑，十四段就变成了几十种音色。
        float pitch = 0.94F + random.nextFloat() * 0.12F;
        level.playSound(null, at.x, at.y, at.z, GeneratedMod.PARRY_PERFECT.get(), SoundSource.PLAYERS, 1.0F, pitch);
        // 四次里有一次补一记更高的「回响」：像两把刀相撞后刃口还在颤。
        // 池子里多了长余韵的旧素材，回响概率和音量都收一点，免得叠成敲锣。
        if (random.nextFloat() < 0.25F) {
            level.playSound(null, at.x, at.y, at.z, GeneratedMod.PARRY_PERFECT.get(), SoundSource.PLAYERS,
                    0.32F, pitch * 1.19F);
        }
        // 三成概率补一点很轻的水晶泛音，让某些弹刀多一丝火星味；音量压得很低，不会盖过剑鸣。
        if (random.nextFloat() < 0.30F) {
            play(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.20F, 1.72F + random.nextFloat() * 0.22F);
        }
    }

    private static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private ParrySounds() { }
}
