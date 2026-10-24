package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 哥布林的粗糙装备与掉落物。
 *
 * <p>这一整套东西都在「次元物语：自然」创造页里，按 刷怪蛋 → 粗糙装备 → 掉落物 的顺序摆放。
 * 装备本身走原版的 {@link ArmorItem} 与 {@link Item} 属性系统，套装的惩罚与「修复成本减半」
 * 由 {@link RoughGearEvents} 统一处理。</p>
 */
public final class RoughEquipment {
    /** 护甲材质要注册进 ARMOR_MATERIAL 注册表，护甲贴图才会去 textures/models/armor/ 里找。 */
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, GeneratedMod.MOD_ID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> CLOTH_MATERIAL = ARMOR_MATERIALS.register(
            "rough_cloth", () -> new ArmorMaterial(
                    Map.of(ArmorItem.Type.HELMET, 1, ArmorItem.Type.CHESTPLATE, 3,
                            ArmorItem.Type.LEGGINGS, 2, ArmorItem.Type.BOOTS, 1),
                    5, Holder.direct(SoundEvents.ARMOR_EQUIP_LEATHER.value()),
                    () -> Ingredient.of(Items.LEATHER),
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "rough_cloth"))),
                    0.0F, 0.0F));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> IRON_MATERIAL = ARMOR_MATERIALS.register(
            "rough_iron", () -> new ArmorMaterial(
                    Map.of(ArmorItem.Type.HELMET, 2, ArmorItem.Type.CHESTPLATE, 5,
                            ArmorItem.Type.LEGGINGS, 4, ArmorItem.Type.BOOTS, 2),
                    7, Holder.direct(SoundEvents.ARMOR_EQUIP_IRON.value()),
                    () -> Ingredient.of(Items.IRON_INGOT),
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "rough_iron"))),
                    1.0F, 0.0F));

    // ---------------------------------------------------------------- 物品 id
    public static final String CLOTH_HELMET = "rough_cloth_helmet";
    public static final String CLOTH_CHESTPLATE = "rough_cloth_chestplate";
    public static final String CLOTH_LEGGINGS = "rough_cloth_leggings";
    public static final String CLOTH_BOOTS = "rough_cloth_boots";
    public static final String IRON_HELMET = "rough_iron_helmet";
    public static final String IRON_CHESTPLATE = "rough_iron_chestplate";
    public static final String IRON_LEGGINGS = "rough_iron_leggings";
    public static final String IRON_BOOTS = "rough_iron_boots";
    public static final String BUCKLER = "rough_buckler";
    public static final String CLEAVER = "crude_iron_cleaver";
    public static final String AXE = "crude_iron_axe";
    public static final String CLOTH_SCRAP = "goblin_cloth_scrap";
    public static final String CLAN_CREST = "goblin_clan_crest";
    public static final String WAR_BANNER = "goblin_war_banner";
    public static final String BROKEN_BOW = "broken_bow";
    public static final String SKELETON_STAFF = "skeleton_staff";
    public static final String DIARY = "goblin_diary";
    public static final String TREASURE_MAP = "torn_treasure_map";

    /** 创造页顺序：布甲 → 铁甲 → 盾 / 武器 → 掉落物。 */
    public static final List<String> ORDER = new ArrayList<>();

    /** 把整套粗糙装备注册进主类；返回的自然页顺序由 {@link #ORDER} 记录。 */
    static void register() {
        armorSet(CLOTH_MATERIAL, RoughSet.CLOTH, "rough_cloth", 15, 65, 45, 25, Rarity.COMMON);
        armorSet(IRON_MATERIAL, RoughSet.IRON, "rough_iron", 155, 285, 235, 195, Rarity.UNCOMMON);

        add(BUCKLER, GeneratedMod.ITEMS.register(BUCKLER, () -> new RoughBucklerItem(
                new Item.Properties().durability(185).rarity(Rarity.COMMON))));

        add(CLEAVER, GeneratedMod.ITEMS.register(CLEAVER, () -> new RoughWeaponItem(
                new Item.Properties().durability(200).rarity(Rarity.COMMON)
                        .attributes(weaponAttributes(6.0, 1.5)))));
        add(AXE, GeneratedMod.ITEMS.register(AXE, () -> new RoughWeaponItem(
                new Item.Properties().durability(200).rarity(Rarity.UNCOMMON)
                        .attributes(weaponAttributes(11.0, 0.8)))));

        add(CLOTH_SCRAP, GeneratedMod.ITEMS.registerSimpleItem(CLOTH_SCRAP,
                new Item.Properties().rarity(Rarity.COMMON)));
        add(CLAN_CREST, GeneratedMod.ITEMS.register(CLAN_CREST, () -> new LoreItem(
                new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON),
                "tooltip.dimensional_mana.goblin_clan_crest")));
        add(WAR_BANNER, GeneratedMod.ITEMS.register(WAR_BANNER, () -> new LoreItem(
                new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON),
                "tooltip.dimensional_mana.goblin_war_banner")));
        add(BROKEN_BOW, GeneratedMod.ITEMS.register(BROKEN_BOW, () -> new BowItem(
                new Item.Properties().durability(50).rarity(Rarity.COMMON))));
        add(SKELETON_STAFF, GeneratedMod.ITEMS.register(SKELETON_STAFF, () -> new MagicStaffItem(
                new Item.Properties().durability(120).rarity(Rarity.UNCOMMON)
                        .attributes(MagicStaffItem.meleeAttributes()),
                new MagicStaffItem.StaffStats(2.0, 0.03, 0.02, 0.02, 0.01))));
        add(DIARY, GeneratedMod.ITEMS.register(DIARY, () -> new LoreItem(
                new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                "tooltip.dimensional_mana.goblin_diary")));
        add(TREASURE_MAP, GeneratedMod.ITEMS.register(TREASURE_MAP, () -> new LoreItem(
                new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                "tooltip.dimensional_mana.torn_treasure_map")));
    }

    /** 主手武器的伤害与攻速：原版攻速基准是 4.0，所以写入的是「目标值 - 4.0」。 */
    public static ItemAttributeModifiers weaponAttributes(double damage, double attackSpeed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attackSpeed - 4.0,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    private static void armorSet(DeferredHolder<ArmorMaterial, ArmorMaterial> material, RoughSet set,
                                 String prefix, int helmet, int chestplate, int leggings, int boots, Rarity rarity) {
        add(prefix + "_helmet", GeneratedMod.ITEMS.register(prefix + "_helmet", () ->
                new RoughArmorItem(set, material, ArmorItem.Type.HELMET,
                        new Item.Properties().durability(helmet).rarity(rarity))));
        add(prefix + "_chestplate", GeneratedMod.ITEMS.register(prefix + "_chestplate", () ->
                new RoughArmorItem(set, material, ArmorItem.Type.CHESTPLATE,
                        new Item.Properties().durability(chestplate).rarity(rarity))));
        add(prefix + "_leggings", GeneratedMod.ITEMS.register(prefix + "_leggings", () ->
                new RoughArmorItem(set, material, ArmorItem.Type.LEGGINGS,
                        new Item.Properties().durability(leggings).rarity(rarity))));
        add(prefix + "_boots", GeneratedMod.ITEMS.register(prefix + "_boots", () ->
                new RoughArmorItem(set, material, ArmorItem.Type.BOOTS,
                        new Item.Properties().durability(boots).rarity(rarity))));
    }

    private static void add(String id, DeferredItem<? extends Item> item) {
        GeneratedMod.MOD_ITEMS.put(id, item);
        ORDER.add(id);
    }

    /** 玩家身上是否穿齐了某一套粗糙装备。 */
    public static boolean wearsFullSet(net.minecraft.world.entity.player.Player player, RoughSet set) {
        if (player == null) return false;
        int worn = 0;
        for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof RoughArmorItem armor && armor.set() == set) worn++;
        }
        return worn >= 4;
    }

    private RoughEquipment() { }
}
