package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.LinkedHashMap;
import java.util.Map;

/** 每个法术使用独立的分层音频，蓄力与破碎另有专用音频。 */
public final class SpellSounds {
    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> SOUNDS = new LinkedHashMap<>();
    public static void register() {
        add("spell_charge");
        add("spell_shatter");
        // 万剑归一专用：天空被劈开的巨响与飞剑破空的风切声。
        add("spell_sky_rift");
        add("spell_flying_sword");
        for (SpellDefinition spell : SpellDefinition.values()) if (!spell.isNewSpell()) add("spell_" + spell.id());
        for (String medium : new String[]{"arcane", "fire", "water", "ice", "lightning", "wind",
                "earth", "nature", "metal", "light", "poison", "sound"}) add("spell_impact_" + medium);
    }
    private static void add(String name) {
        SOUNDS.put(name, GeneratedMod.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, name))));
    }
    public static SoundEvent get(String name) { return SOUNDS.get(name).get(); }
    private SpellSounds() { }
}
