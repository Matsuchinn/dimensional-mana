package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 魔精钢装备：以特级魔精钢打造的一整套护甲、工具与锻造模板。
 *
 * <p>模型形状沿用原版下界合金装备（护甲用 generated、工具用 handheld），
 * 贴图是独立绘制的四帧动态贴图；装备本身固定为史诗品质。</p>
 *
 * <p>护甲数值全部写进 {@link ArmorMaterial}：护甲值 3 / 8 / 6 / 3、韧性每件 4.0、
 * 击退抗性每件 15%。「独立伤害减免」不是原版属性，由 {@link MojiangSteelEvents#onDamagePre}
 * 在护甲减伤之后按件乘算。</p>
 */
public final class MojiangSteelEquipment {
    public static final String HELMET = "mojing_steel_helmet";
    public static final String CHESTPLATE = "mojing_steel_chestplate";
    public static final String LEGGINGS = "mojing_steel_leggings";
    public static final String BOOTS = "mojing_steel_boots";
    public static final String SWORD = "mojing_steel_sword";
    public static final String AXE = "mojing_steel_axe";
    public static final String PICKAXE = "mojing_steel_pickaxe";
    public static final String SHOVEL = "mojing_steel_shovel";
    public static final String HOE = "mojing_steel_hoe";
    public static final String TEMPLATE = "mojing_steel_smithing_template";

    /** 保留原版页清单以兼容旧代码；魔精钢物品现在统一放入“次元物语：魔法”。 */
    public static final List<String> COMBAT_TAB_IDS = List.of();
    public static final List<String> TOOLS_TAB_IDS = List.of();
    public static final List<String> INGREDIENT_TAB_IDS = List.of();
    public static final List<String> ALL_IDS;

    /**
     * 「次元物语：魔法」页里紧跟在法杖后面的摆放顺序：四件护甲 → 剑 → 斧 → 镐 → 锹 → 锄。
     * 锻造模板是材料，不在这份清单里，仍留在后面的杂项段。
     */
    public static final List<String> EQUIPMENT_IDS = List.of(
            HELMET, CHESTPLATE, LEGGINGS, BOOTS, SWORD, AXE, PICKAXE, SHOVEL, HOE);

    static {
        List<String> all = new ArrayList<>();
        all.addAll(COMBAT_TAB_IDS);
        all.addAll(TOOLS_TAB_IDS);
        all.addAll(INGREDIENT_TAB_IDS);
        ALL_IDS = List.copyOf(all);
    }

    /** 20 是原版工具与护甲统一的「附魔值」上限基准；魔精钢拉到 40，明显更容易出多词条高等级。 */
    public static final int ENCHANTMENT_VALUE = 40;
    public static final float TOUGHNESS = 4.0F;
    public static final float KNOCKBACK_RESISTANCE = 0.15F;
    public static final float MINING_SPEED = 12.0F;
    public static final int TOOL_DURABILITY = 4062;

    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, GeneratedMod.MOD_ID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MATERIAL = ARMOR_MATERIALS.register(
            "mojing_steel", () -> new ArmorMaterial(
                    Map.of(ArmorItem.Type.HELMET, 3, ArmorItem.Type.CHESTPLATE, 8,
                            ArmorItem.Type.LEGGINGS, 6, ArmorItem.Type.BOOTS, 3),
                    ENCHANTMENT_VALUE, Holder.direct(SoundEvents.ARMOR_EQUIP_NETHERITE.value()),
                    () -> Ingredient.of(specialSteel()),
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "mojing_steel"))),
                    TOUGHNESS, KNOCKBACK_RESISTANCE));

    /** 工具等级：耐久 4062、挖掘速度 12、附魔值 40，修复材料是特级魔精钢。 */
    public static final Tier TIER = new Tier() {
        @Override public int getUses() { return TOOL_DURABILITY; }
        @Override public float getSpeed() { return MINING_SPEED; }
        @Override public float getAttackDamageBonus() { return 0.0F; }
        @Override public TagKey<Block> getIncorrectBlocksForDrops() { return BlockTags.INCORRECT_FOR_NETHERITE_TOOL; }
        @Override public int getEnchantmentValue() { return ENCHANTMENT_VALUE; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(specialSteel()); }
    };

    private MojiangSteelEquipment() { }

    /** 由主类静态块调用：物品进注册表，同时记下“次元物语：魔法”页的摆放顺序。 */
    static void register() {
        armor(HELMET, ArmorItem.Type.HELMET, 830);
        armor(CHESTPLATE, ArmorItem.Type.CHESTPLATE, 1017);
        armor(LEGGINGS, ArmorItem.Type.LEGGINGS, 960);
        armor(BOOTS, ArmorItem.Type.BOOTS, 845);

        // 注释里的伤害值就是游戏内 tooltip 显示的攻击伤害：原版玩家基础攻击力是 1，
        // 所以写入属性修正时要减掉这 1 点。攻速同理，基准是 4.0。
        add(SWORD, GeneratedMod.ITEMS.register(SWORD, () -> new MojiangSteelSwordItem(TIER,
                new Item.Properties().rarity(Rarity.EPIC).attributes(weaponAttributes(10.0, 1.7)))));
        add(AXE, GeneratedMod.ITEMS.register(AXE, () -> new AxeItem(TIER,
                new Item.Properties().rarity(Rarity.EPIC).attributes(weaponAttributes(14.0, 1.1)))));
        add(PICKAXE, GeneratedMod.ITEMS.register(PICKAXE, () -> new PickaxeItem(TIER,
                new Item.Properties().rarity(Rarity.EPIC).attributes(weaponAttributes(7.0, 1.3)))));
        add(SHOVEL, GeneratedMod.ITEMS.register(SHOVEL, () -> new ShovelItem(TIER,
                new Item.Properties().rarity(Rarity.EPIC).attributes(weaponAttributes(7.5, 1.1)))));
        add(HOE, GeneratedMod.ITEMS.register(HOE, () -> new HoeItem(TIER,
                new Item.Properties().rarity(Rarity.EPIC).attributes(weaponAttributes(2.0, 4.1)))));

        add(TEMPLATE, GeneratedMod.ITEMS.register(TEMPLATE, MojiangSteelEquipment::createTemplate));
    }

    private static void armor(String id, ArmorItem.Type type, int durability) {
        add(id, GeneratedMod.ITEMS.register(id, () -> new MojiangSteelArmorItem(MATERIAL, type,
                new Item.Properties().durability(durability).rarity(Rarity.EPIC))));
    }

    private static Item createTemplate() {
        List<ResourceLocation> baseIcons = List.of(
                ResourceLocation.withDefaultNamespace("item/empty_slot_helmet"),
                ResourceLocation.withDefaultNamespace("item/empty_slot_chestplate"),
                ResourceLocation.withDefaultNamespace("item/empty_slot_leggings"),
                ResourceLocation.withDefaultNamespace("item/empty_slot_boots"),
                ResourceLocation.withDefaultNamespace("item/empty_slot_sword"),
                ResourceLocation.withDefaultNamespace("item/empty_slot_axe"),
                ResourceLocation.withDefaultNamespace("item/empty_slot_pickaxe"),
                ResourceLocation.withDefaultNamespace("item/empty_slot_shovel"),
                ResourceLocation.withDefaultNamespace("item/empty_slot_hoe"));
        List<ResourceLocation> additionIcons = List.of(
                ResourceLocation.withDefaultNamespace("item/empty_slot_ingot"));
        return new SmithingTemplateItem(
                Component.translatable("item.dimensional_mana.mojing_steel_smithing_template.applies_to")
                        .withStyle(ChatFormatting.BLUE),
                Component.translatable("item.dimensional_mana.mojing_steel_smithing_template.ingredients")
                        .withStyle(ChatFormatting.BLUE),
                Component.translatable("item.dimensional_mana.mojing_steel_smithing_template.upgrade")
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("item.dimensional_mana.mojing_steel_smithing_template.base_slot")
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("item.dimensional_mana.mojing_steel_smithing_template.additions_slot")
                        .withStyle(ChatFormatting.GRAY),
                baseIcons, additionIcons);
    }

    /**
     * 主手攻击属性。{@code damage} 与 {@code attackSpeed} 都是 tooltip 里看到的最终值，
     * 写入修正时分别减掉玩家基础攻击力 1 与攻速基准 4。
     */
    public static ItemAttributeModifiers weaponAttributes(double damage, double attackSpeed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                        damage - 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                        attackSpeed - 4.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    private static Item specialSteel() {
        return GeneratedMod.MOD_ITEMS.get("special_magic_steel").get();
    }

    private static void add(String id, DeferredItem<? extends Item> item) {
        GeneratedMod.MOD_ITEMS.put(id, item);
        GeneratedMod.MAGIC_ITEM_IDS.add(id);
    }
}
