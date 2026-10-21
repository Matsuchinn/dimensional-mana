package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.Locale;

/** 十一种初级元素媒介。奥术是始终开放的高级媒介，不占三选名额。 */
public enum ElementType {
    FIRE("fire", 0xFFFF6633, Items.BLAZE_POWDER, "textures/item/blaze_powder.png"),
    WATER("water", 0xFF4BA7FF, Items.WATER_BUCKET, "textures/item/water_bucket.png"),
    ICE("ice", 0xFFA8ECFF, Items.SNOWBALL, "textures/item/snowball.png"),
    LIGHTNING("lightning", 0xFFFFE45B, Items.LIGHTNING_ROD, "textures/item/lightning_rod.png"),
    WIND("wind", 0xFFB9FFE2, Items.FEATHER, "textures/item/feather.png"),
    EARTH("earth", 0xFF9B714D, Items.DRIPSTONE_BLOCK, "textures/item/pointed_dripstone.png"),
    NATURE("nature", 0xFF67C653, Items.OAK_SAPLING, "textures/block/oak_sapling.png"),
    METAL("metal", 0xFFC8D0D8, Items.IRON_INGOT, "textures/item/iron_ingot.png"),
    LIGHT("light", 0xFFFFF4A8, Items.GLOWSTONE_DUST, "textures/item/glowstone_dust.png"),
    POISON("poison", 0xFF9ACD45, Items.SPIDER_EYE, "textures/item/spider_eye.png"),
    SOUND("sound", 0xFFE997FF, Items.NOTE_BLOCK, "textures/block/note_block.png");

    private final String id;
    private final int color;
    private final Item icon;
    private final String iconTexture;

    ElementType(String id, int color, Item icon, String iconTexture) {
        this.id = id;
        this.color = color;
        this.icon = icon;
        this.iconTexture = iconTexture;
    }

    public String id() { return id; }
    public int color() { return color; }
    public Item icon() { return icon; }
    public String iconTexture() { return iconTexture; }
    public String translationKey() { return "gui.dimensional_mana.element." + id; }
    public String descriptionKey() { return translationKey() + ".description"; }
    public String fragmentId(String grade) { return grade + "_" + id + "_fragment"; }
    public String scrollId() { return id + "_talent_scroll"; }
    public String attachmentEffectId() { return id + "_attachment"; }

    /** 用户给出的附着时长，单位为游戏刻（20 刻 = 1 秒）。 */
    public int attachmentTicks() {
        return switch (this) {
            case WIND -> 110;
            case SOUND -> 60;
            case LIGHT -> 84;
            case LIGHTNING -> 100;
            case FIRE -> 216;
            case WATER -> 400;
            case ICE -> 160;
            case EARTH -> 220;
            case NATURE -> 200;
            case METAL -> 140;
            case POISON -> 210;
        };
    }

    public boolean shortAttachment() {
        return this == WIND || this == SOUND || this == LIGHT || this == LIGHTNING;
    }

    public static ElementType byId(String id) {
        if (id == null) return null;
        String normalized = id.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(value -> value.id.equals(normalized)).findFirst().orElse(null);
    }

    public static ElementType fromMedium(String medium) {
        return byId(medium);
    }
}
