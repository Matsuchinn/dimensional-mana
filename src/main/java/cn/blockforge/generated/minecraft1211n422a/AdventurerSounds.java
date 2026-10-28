package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * r212 冒险者专属人声：右键搭话、成交道谢、拒绝三连，全换成男声说话，
 * 不再借村民的「嗯嗯啊啊」。台词见资源包 sounds/adventurer/（来源与许可记录在 docs/更新说明-r212.md）。
 */
public final class AdventurerSounds {
    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> SOUNDS = new LinkedHashMap<>();

    public static void register() {
        for (String id : new String[]{"greet", "thanks", "deny", "restock"}) {
            String name = "adventurer_" + id;
            SOUNDS.put(id, GeneratedMod.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, name))));
        }
    }

    public static SoundEvent get(String id) { return SOUNDS.get(id).get(); }

    private AdventurerSounds() {}
}
