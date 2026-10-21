package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

public final class ArcaneCodexItem extends Item implements ICurioItem {
    public ArcaneCodexItem(Properties properties) {
        super(properties);
        CuriosApi.registerCurio(this, this);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            level.playSound(null, player.blockPosition(), GeneratedMod.CODEX_PAGE_TURN.get(), SoundSource.PLAYERS,
                    0.8F, 0.96F + level.random.nextFloat() * 0.08F);
            PacketDistributor.sendToPlayer(serverPlayer, new OpenCodexGuidePayload(true));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.arcane_codex.open",
                Component.keybind("key.dimensional_mana.open_codex")).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.arcane_codex.guide").withStyle(ChatFormatting.GRAY));
    }

    public static boolean hasAccess(Player player) {
        Item item = GeneratedMod.MOD_ITEMS.get("arcane_codex").get();
        if (player.getInventory().contains(stack -> stack.is(item))) return true;
        return CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.isEquipped(item))
                .orElse(false);
    }

    public static boolean isEquipped(LivingEntity entity) {
        Item item = GeneratedMod.MOD_ITEMS.get("arcane_codex").get();
        return CuriosApi.getCuriosInventory(entity)
                .map(handler -> handler.isEquipped(item))
                .orElse(false);
    }
}
