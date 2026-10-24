package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import java.util.LinkedHashMap;
import java.util.Map;

/** 哥布林专属声线；音频来源及许可见资源包内的素材说明。 */
public final class GoblinSounds {
    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> SOUNDS = new LinkedHashMap<>();

    public static void register() {
        for (String id : new String[]{"ambient", "hurt", "death", "young", "shaman", "chief", "panic", "roar", "attack"}) {
            String name = "goblin_" + id;
            SOUNDS.put(id, GeneratedMod.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, name))));
        }
    }

    public static SoundEvent get(String id) { return SOUNDS.get(id).get(); }
    private GoblinSounds() {}
}
