package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import net.minecraft.world.item.Item;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * 物品图标的逐帧动画（共鸣核心 8 帧、超导魔晶 4 帧）。
 *
 * <p>动画不是靠贴图动画（.mcmeta）做的：一部分优化/光影模组会关掉贴图动画，物品就僵成一张静态图。
 * 这里改成给物品注册一个 {@code dimensional_mana:frame} 物品属性，模型用 overrides 按帧号切换，
 * 帧号随系统时间循环。这样动画由模型选择驱动，任何客户端都能动，也不受贴图动画开关影响。</p>
 */
final class ClientItemAnimations {
    private static final ResourceLocation FRAME_PROPERTY =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "frame");

    /** 在客户端初始化时装上帧属性；必须在模型烘焙之前完成。 */
    static void register(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            register("resonance_core", 8, 150);
            register("superconducting_crystal", 4, 200);
            // 高等魔法刻蚀改成 4 帧呼吸式动画：符文亮度与高光随帧循环起落。
            register("greater_magic_etching", 4, 170);
        });
    }

    private static void register(String itemId, int frames, int frameMillis) {
        DeferredItem<? extends Item> holder = GeneratedMod.MOD_ITEMS.get(itemId);
        if (holder == null) return;
        ItemProperties.register(holder.get(), FRAME_PROPERTY,
                (stack, level, entity, seed) -> (float)((Util.getMillis() / frameMillis) % frames));
    }

    private ClientItemAnimations() { }
}
