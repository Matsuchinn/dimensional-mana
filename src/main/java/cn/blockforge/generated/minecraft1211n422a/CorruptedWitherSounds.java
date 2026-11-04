package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 魔蚀凋零的专属声线。
 *
 * <p>一点也不借原版凋零的音效：每条都是从零合成的低频轰鸣 + 体腔共鸣 + 空间混响，
 * 音频文件在 {@code assets/dimensional_mana/sounds/corrupted_wither/} 下，
 * 合成脚本是 {@code tools/gen_corrupted_wither_sounds.py}。</p>
 */
public final class CorruptedWitherSounds {
    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> SOUNDS = new LinkedHashMap<>();

    public static void register() {
        for (String id : new String[]{"ambient", "hurt", "death", "spawn", "shoot",
                "charge", "beam", "beam_loop", "phase", "pulse", "blast"}) {
            String name = "corrupted_wither_" + id;
            SOUNDS.put(id, GeneratedMod.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, name))));
        }
    }

    public static SoundEvent get(String id) {
        return SOUNDS.get(id).get();
    }

    private CorruptedWitherSounds() { }
}
