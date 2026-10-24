package cn.blockforge.generated.minecraft1211n422a;

/** 两套粗糙护甲；套装惩罚与提示文案都按这里区分。 */
public enum RoughSet {
    /** 粗糙布甲：穿齐整移速 -5%。 */
    CLOTH("tooltip.dimensional_mana.rough_cloth_set"),
    /** 粗糙铁甲：穿齐整攻击速度（敲击） -5%。 */
    IRON("tooltip.dimensional_mana.rough_iron_set");

    private final String penaltyKey;

    RoughSet(String penaltyKey) {
        this.penaltyKey = penaltyKey;
    }

    public String penaltyKey() {
        return penaltyKey;
    }

    public boolean wearsFullSet(net.minecraft.world.entity.player.Player player) {
        return RoughEquipment.wearsFullSet(player, this);
    }
}
