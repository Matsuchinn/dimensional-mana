package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 粗糙装备的规则集中在这里：套装惩罚、圆盾破防、铁砧修复成本减半。
 *
 * <p>套装惩罚用「临时属性修饰符」实现：穿上整套就加上，脱掉任意一件就摘掉，
 * 重启游戏也不会因为重复叠加而越穿越慢。</p>
 */
public final class RoughGearEvents {
    private static final ResourceLocation CLOTH_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "rough_cloth_set_speed");
    private static final ResourceLocation IRON_ATTACK_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "rough_iron_set_attack_speed");
    private static final AttributeModifier CLOTH_SPEED = new AttributeModifier(
            CLOTH_SPEED_ID, -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final AttributeModifier IRON_ATTACK_SPEED = new AttributeModifier(
            IRON_ATTACK_SPEED_ID, -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    /** 至少要吃下这么多伤害，圆盾才有可能被破防；挠痒式的小攻击不会把盾打穿。 */
    private static final float BUCKLER_BREAK_MIN_DAMAGE = 4.0F;
    private static final float BUCKLER_BREAK_CHANCE = 0.10F;

    /** 每刻核对一次套装状态；属性实例只在真正变化时才改，避免每刻写属性。 */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player player)) return;
        if (player.level().isClientSide) return;
        applyModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), CLOTH_SPEED,
                RoughSet.CLOTH.wearsFullSet(player));
        applyModifier(player.getAttribute(Attributes.ATTACK_SPEED), IRON_ATTACK_SPEED,
                RoughSet.IRON.wearsFullSet(player));
    }

    private static void applyModifier(AttributeInstance instance, AttributeModifier modifier, boolean shouldHave) {
        if (instance == null) return;
        boolean has = instance.getModifier(modifier.id()) != null;
        if (shouldHave && !has) instance.addOrUpdateTransientModifier(modifier);
        else if (!shouldHave && has) instance.removeModifier(modifier.id());
    }

    /**
     * 圆盾破防：格挡成功后有 10% 的概率被强力攻击打穿。
     * 破防的这一次仍然算挨打（不格挡），并额外扣一大截耐久、把玩家往后推。
     */
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!(event.getEntity() instanceof LivingEntity blocker)) return;
        if (!(blocker.level() instanceof ServerLevel level)) return;
        if (!event.getBlocked()) return;
        ItemStack shield = blocker.getUseItem();
        if (!(shield.getItem() instanceof RoughBucklerItem)) return;
        if (event.getOriginalBlockedDamage() < BUCKLER_BREAK_MIN_DAMAGE) return;
        if (level.random.nextFloat() >= BUCKLER_BREAK_CHANCE) return;

        event.setBlocked(false);
        EquipmentSlot slot = blocker.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        shield.hurtAndBreak(30, blocker, slot);
        blocker.stopUsingItem();
        // 被强力攻击打得后退：沿着攻击者指向自己的方向推出去。
        if (event.getDamageSource().getEntity() != null) {
            var attacker = event.getDamageSource().getEntity();
            double dx = blocker.getX() - attacker.getX();
            double dz = blocker.getZ() - attacker.getZ();
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 0.01) {
                dx = 0.0;
                dz = 1.0;
                length = 1.0;
            }
            blocker.knockback(1.2, dx / length, dz / length);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                blocker.getX(), blocker.getY() + 1.0, blocker.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, blocker.blockPosition(), net.minecraft.sounds.SoundEvents.SHIELD_BREAK,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.9F);
    }

    /** 粗糙装备在铁砧上修复时经验成本减半。 */
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        if (!isRoughGear(left)) return;
        ItemStack right = event.getRight();
        if (right.isEmpty()) return;
        if (!left.getItem().isValidRepairItem(left, right) && !right.is(left.getItem())) return;
        if (event.getCost() <= 0) return;
        event.setCost(Math.max(1L, event.getCost() / 2L));
    }

    private static boolean isRoughGear(ItemStack stack) {
        return stack.getItem() instanceof RoughArmorItem
                || stack.getItem() instanceof RoughBucklerItem
                || stack.getItem() instanceof RoughWeaponItem;
    }

    private RoughGearEvents() { }
}
