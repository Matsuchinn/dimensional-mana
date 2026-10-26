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
 * <p>sounds.json 里的 {@code parry_perfect} 挂了六段不同的剑鸣（标准、双刀、长鸣、重剑、极短、火花），
 * 全部是「钢铁对撞」的音色：宽频起音、密集的金属振动、半秒内收尾。原版每次播放会从中随机挑一段，
 * 这里再给整组做音高抖动，偶尔补一记更高的回响和一点水晶泛音，所以连续弹十次也不会完全重样。</p>
 *
 * <p>这里不再叠原版的钟声（BELL_BLOCK）、铁砧（ANVIL_LAND）这类长余韵打击声：那几样正是上一版
 * 听起来「敲锣打鼓」的原因，钟和铁砧的低频拖尾会把剑鸣盖成鼓。</p>
 */
public final class ParrySounds {
    /** 剑鸣本体在 {@link GeneratedMod#PARRY_PERFECT} 注册，这里只负责按随机规则播放。 */

    /**
     * 播一整套完美招架音效：主剑鸣 + 随机高音回响 + 偶尔一点水晶泛音。
     *
     * @param at 两把刀相交的位置（玩家与怪物中点），做成世界里的立体声，走路时能听出方向。
     */
    public static void perfect(ServerLevel level, Vec3 at, RandomSource random) {
        // ±6% 音高抖动：同一段剑鸣听起来像换了一把剑，六段素材就变成了几十种音色。
        float pitch = 0.94F + random.nextFloat() * 0.12F;
        level.playSound(null, at.x, at.y, at.z, GeneratedMod.PARRY_PERFECT.get(), SoundSource.PLAYERS, 1.0F, pitch);
        // 三次里有一次补一记更高的「回响」：像两把刀相撞后刃口还在颤，不加任何低频，只加亮度。
        if (random.nextFloat() < 0.34F) {
            level.playSound(null, at.x, at.y, at.z, GeneratedMod.PARRY_PERFECT.get(), SoundSource.PLAYERS,
                    0.40F, pitch * 1.19F);
        }
        // 四成概率补一点很轻的水晶泛音，让某些弹刀多一丝火星味；音量压得很低，不会盖过剑鸣。
        if (random.nextFloat() < 0.40F) {
            play(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.20F, 1.72F + random.nextFloat() * 0.22F);
        }
    }

    private static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private ParrySounds() { }
}
