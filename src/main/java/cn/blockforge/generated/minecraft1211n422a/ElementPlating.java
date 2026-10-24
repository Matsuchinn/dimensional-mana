package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.event.GrindstoneEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 元素镀层：把元素结晶粉末镀到护甲或盾牌上的独立强化线，和原版附魔完全分开。
 *
 * <p>数据只存在物品的 {@code minecraft:custom_data} 里（键 {@code ElementPlating}），
 * 因此不需要新注册物品、也不占附魔位；砂轮拆层时按同一份数据返还粉末。</p>
 *
 * <p>三处玩法接口：</p>
 * <ul>
 *   <li>魔晶加工台：中心装备 + 四周 8 份同种粉末，护甲 200 魔素 / 15 秒，盾牌 40 魔素 / 5 秒。</li>
 *   <li>受击结算：护甲每层 5% 该元素伤害抗性（全身 16 层封顶 80%），在最终伤害阶段结算；
 *       举盾时每层 25% 概率完美格挡该元素伤害，同时按公式额外侵蚀盾牌耐久。
 *       元素归属除了自家的 {@code spell_*} 类型，也认原版火焰、冻结与闪电。</li>
 *   <li>砂轮：清掉全部镀层与附魔，每层返还 1~2 份对应粉末。</li>
 * </ul>
 */
public final class ElementPlating {
    /** 单件装备对单一元素的镀层上限。 */
    public static final int MAX_LAYERS_PER_ELEMENT = 4;
    /** 单件装备的总镀层上限（可混搭，例如 2 层火焰 + 2 层冰霜）。 */
    public static final int MAX_TOTAL_LAYERS = 4;
    /** 每层护甲镀层提供的该元素法术抗性。 */
    public static final double RESIST_PER_LAYER = 0.05;
    /** 全身打满 16 层时的抗性极值。 */
    public static final double MAX_RESIST = 0.80;
    /** 盾牌每层镀层提供的完美格挡概率。 */
    public static final double PERFECT_BLOCK_PER_LAYER = 0.25;

    public static final int ARMOR_MANA_COST = 200;
    public static final int ARMOR_TICKS = 300;
    public static final int SHIELD_MANA_COST = 40;
    public static final int SHIELD_TICKS = 100;

    private static final String ROOT_KEY = "ElementPlating";
    private static final int ARCANE_COLOR = 0xFFC85BFF;
    /** 十二种可镀层元素，顺序固定，图鉴与说明按这个顺序显示。 */
    private static final List<String> ELEMENTS = createElements();
    /** 元素结晶粉末物品 id → 元素 id（奥术元素使用 mana 粉末）。 */
    private static final Map<String, String> POWDER_ELEMENT = new LinkedHashMap<>();
    /** 元素 id → 元素结晶粉末物品 id。 */
    private static final Map<String, String> ELEMENT_POWDER = new LinkedHashMap<>();
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    static {
        for (ElementalSlimeVariant variant : ElementalSlimeVariant.values()) {
            POWDER_ELEMENT.put(variant.powderId(), variant.damageElementId());
            ELEMENT_POWDER.put(variant.damageElementId(), variant.powderId());
        }
    }

    private ElementPlating() { }

    private static List<String> createElements() {
        List<String> ids = new ArrayList<>();
        for (ElementType element : ElementType.values()) ids.add(element.id());
        ids.add(MagicEquipment.ARCANE);
        return List.copyOf(ids);
    }

    public static List<String> elements() { return ELEMENTS; }

    public static Component elementName(String elementId) {
        return Component.translatable("gui.dimensional_mana.plating.element." + elementId);
    }

    public static int elementColor(String elementId) {
        if (MagicEquipment.ARCANE.equals(elementId)) return ARCANE_COLOR;
        ElementType type = ElementType.byId(elementId);
        return type == null ? 0xFFFFFFFF : type.color();
    }

    /** 罗马数字只用到 1~4，写成常量表比循环更好读。 */
    public static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> Integer.toString(value);
        };
    }

    // ------------------------------------------------------------------ 数据读写

    private static CompoundTag readTag(ItemStack stack) {
        if (stack.isEmpty()) return new CompoundTag();
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return root.contains(ROOT_KEY, Tag.TAG_COMPOUND) ? root.getCompound(ROOT_KEY) : new CompoundTag();
    }

    private static void writeTag(ItemStack stack, CompoundTag plating) {
        if (stack.isEmpty()) return;
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (plating.isEmpty()) root.remove(ROOT_KEY);
        else root.put(ROOT_KEY, plating.copy());
        if (root.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else CustomData.set(DataComponents.CUSTOM_DATA, stack, root);
    }

    public static boolean hasPlating(ItemStack stack) {
        return !stack.isEmpty() && !readTag(stack).isEmpty();
    }

    public static int layers(ItemStack stack, String elementId) {
        if (stack.isEmpty() || elementId == null) return 0;
        return readTag(stack).getInt(elementId);
    }

    public static int totalLayers(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        CompoundTag tag = readTag(stack);
        int total = 0;
        for (String elementId : ELEMENTS) total += tag.getInt(elementId);
        return total;
    }

    /** 按固定元素顺序读出全部镀层，跳过 0 层。 */
    public static List<Plating> allLayers(ItemStack stack) {
        List<Plating> result = new ArrayList<>();
        if (stack.isEmpty()) return result;
        CompoundTag tag = readTag(stack);
        for (String elementId : ELEMENTS) {
            int value = tag.getInt(elementId);
            if (value > 0) result.add(new Plating(elementId, value));
        }
        return result;
    }

    public record Plating(String elementId, int layers) { }

    /** 镀上一层的原位强化；调用方保证层数上限已经检查过。 */
    public static void addLayer(ItemStack stack, String elementId) {
        CompoundTag tag = readTag(stack);
        tag.putInt(elementId, tag.getInt(elementId) + 1);
        writeTag(stack, tag);
    }

    /** 完全清除镀层（砂轮用）。 */
    public static ItemStack stripped(ItemStack stack) {
        ItemStack copy = stack.copy();
        writeTag(copy, new CompoundTag());
        return copy;
    }

    /**
     * 砂轮拆除：清镀层并把非诅咒附魔一起去掉，行为与原版砂轮保持一致。
     */
    public static ItemStack grindstoneResult(ItemStack stack) {
        ItemStack result = stripped(stack);
        ItemEnchantments enchantments = result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (!enchantments.isEmpty()) {
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(enchantments);
            // 显式声明成带泛型的 Predicate：removeIf 的签名在混淆映射里是原始类型。
            java.util.function.Predicate<Holder<Enchantment>> removeNonCurses =
                    holder -> !holder.is(EnchantmentTags.CURSE);
            mutable.removeIf(removeNonCurses);
            result.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        }
        result.remove(DataComponents.STORED_ENCHANTMENTS);
        return result;
    }

    // ------------------------------------------------------------------ 粉末映射

    /** 元素结晶粉末对应的元素 id；不是粉末时返回 {@code null}。 */
    public static String powderElementId(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!id.getNamespace().equals(GeneratedMod.MOD_ID)) return null;
        return POWDER_ELEMENT.get(id.getPath());
    }

    public static ItemStack powder(String elementId, int count) {
        String itemId = ELEMENT_POWDER.get(elementId);
        if (itemId == null) return ItemStack.EMPTY;
        var holder = GeneratedMod.MOD_ITEMS.get(itemId);
        return holder == null ? ItemStack.EMPTY : new ItemStack(holder.get(), count);
    }

    public static boolean isArmor(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ArmorItem;
    }

    public static boolean isShield(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ShieldItem;
    }

    // ------------------------------------------------------------------ 加工台

    /** 一次镀层作业：元素、装备种类、魔素与耗时。 */
    public record PlatingJob(String elementId, boolean shield, int manaCost, int ticks) {
        public String recipeId() { return "plating_" + elementId + (shield ? "_shield" : "_armor"); }
    }

    /**
     * 魔晶加工台的镀层判定：中心必须是护甲或盾牌，其余 8 格各放一份同种元素结晶粉末。
     *
     * @return 不满足配方（或层数已满）时返回 {@code null}
     */
    public static PlatingJob findJob(List<ItemStack> slots) {
        if (slots.size() < 9) return null;
        ItemStack center = slots.get(4);
        boolean shield = isShield(center);
        if (!shield && !isArmor(center)) return null;
        String elementId = null;
        for (int slot = 0; slot < 9; slot++) {
            if (slot == 4) continue;
            String powder = powderElementId(slots.get(slot));
            if (powder == null) return null;
            if (elementId == null) elementId = powder;
            else if (!elementId.equals(powder)) return null;
        }
        if (elementId == null) return null;
        if (totalLayers(center) >= MAX_TOTAL_LAYERS) return null;
        if (layers(center, elementId) >= MAX_LAYERS_PER_ELEMENT) return null;
        return shield
                ? new PlatingJob(elementId, true, SHIELD_MANA_COST, SHIELD_TICKS)
                : new PlatingJob(elementId, false, ARMOR_MANA_COST, ARMOR_TICKS);
    }

    // ------------------------------------------------------------------ 受击结算

    /**
     * 把一次伤害归到某个元素上。
     *
     * <p>自家法术有 {@code spell_*} 专属伤害类型，直接读类型名；原版火焰、冻结与闪电没有
     * 这些类型，但玩家真正挨到的“元素伤害”大半就是岩浆、着火、细雪和落雷，所以按原版
     * 伤害标签一并认领。冰镀层因此能减冻结、火镀层能减岩浆，而不只是挡法术。</p>
     *
     * @return 元素 id；不属于任何元素时返回 {@code null}
     */
    public static String damageElementOf(DamageSource source) {
        if (source == null) return null;
        String spell = SpellDamage.elementIdOf(source);
        if (spell != null) return spell;
        if (source.is(DamageTypeTags.IS_FIRE)) return ElementType.FIRE.id();
        if (source.is(DamageTypeTags.IS_FREEZING)) return ElementType.ICE.id();
        if (source.is(DamageTypeTags.IS_LIGHTNING)) return ElementType.LIGHTNING.id();
        return null;
    }

    /**
     * 盾牌的完美格挡：举盾且盾牌镀了该元素时掷一次概率，成功就把这一下整个取消。
     *
     * <p>这里刻意不排除 {@link DamageTypeTags#BYPASSES_SHIELD}：普通元素伤害原版盾牌本来
     * 就能完全挡下，镀层真正能发挥作用的地方恰恰是被盾牌穿透的元素伤害（例如声波）。</p>
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled()) return;
        // 必杀伤害不走盾牌侵蚀：伤害 ÷ 10 再强转 int 会直接整数溢出，把护盾耐久算成负数。
        if (LethalDamage.isLethal(event.getSource(), event.getAmount())) return;
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        if (!player.isBlocking() || event.getAmount() <= 0.0F) return;
        String elementId = damageElementOf(event.getSource());
        if (elementId == null) return;
        ItemStack shield = player.getUseItem();
        int layers = layers(shield, elementId);
        if (layers <= 0) return;

        // 元素侵蚀：(伤害 ÷ 10 + 1) × 层数，无论是否完美格挡都会发生。
        int erosion = ((int)(event.getAmount() / 10.0F) + 1) * layers;
        shield.hurtAndBreak(erosion, player, player.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        glow(player, elementId, 8);
        if (player.getRandom().nextFloat() < Math.min(1.0F, layers * PERFECT_BLOCK_PER_LAYER)) {
            perfectBlockEffect(player, elementId);
            event.setCanceled(true);
        }
    }

    /**
     * 护甲的镀层减伤：在 {@code LivingDamageEvent.Pre} 结算，此时盔甲、附魔与药水减伤都已
     * 经算完，这里乘上的抗性就是最终落到血量上的数字，不会被后续流程覆盖。
     *
     * <p>每层 5% 该元素抗性，全身层数相加、封顶 80%；每层再让该部位多掉 1 点耐久。</p>
     */
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        float amount = event.getNewDamage();
        if (amount <= 0.0F) return;
        String elementId = damageElementOf(event.getSource());
        if (elementId == null) return;
        int totalArmorLayers = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack armor = player.getItemBySlot(slot);
            int layers = layers(armor, elementId);
            if (layers <= 0) continue;
            totalArmorLayers += layers;
            armor.hurtAndBreak(layers, player, slot);
        }
        if (totalArmorLayers <= 0) return;
        double resist = Math.min(MAX_RESIST, totalArmorLayers * RESIST_PER_LAYER);
        event.setNewDamage(amount * (float)(1.0 - resist));
        glow(player, elementId, 10);
    }

    private static void perfectBlockEffect(Player player, String elementId) {
        if (!(player.level() instanceof ServerLevel level)) return;
        DustParticleOptions dust = dust(elementId, 1.0F);
        double radius = 1.15;
        for (int i = 0; i < 20; i++) {
            double angle = i * Math.PI * 2.0 / 20.0;
            level.sendParticles(dust,
                    player.getX() + Math.cos(angle) * radius,
                    player.getY() + 0.9,
                    player.getZ() + Math.sin(angle) * radius,
                    1, 0.02, 0.02, 0.02, 0.0);
        }
        level.sendParticles(dust, player.getX(), player.getY() + 1.0, player.getZ(), 18, 0.5, 0.7, 0.5, 0.04);
        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS, 1.0F, 1.35F);
    }

    /** 带镀层装备受击时身上泛出的元素微光。 */
    private static void glow(LivingEntity entity, String elementId, int count) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        level.sendParticles(dust(elementId, 0.85F),
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
                count, 0.42, 0.55, 0.42, 0.02);
    }

    private static DustParticleOptions dust(String elementId, float scale) {
        int rgb = elementColor(elementId);
        return new DustParticleOptions(new Vector3f(
                ((rgb >> 16) & 255) / 255.0F,
                ((rgb >> 8) & 255) / 255.0F,
                (rgb & 255) / 255.0F), scale);
    }

    // ------------------------------------------------------------------ 提示文本

    /** 物品名前的 [火焰镀层 I] 前缀；没有镀层时返回空文本。 */
    public static Component prefix(ItemStack stack) {
        MutableComponent result = Component.empty();
        for (Plating plating : allLayers(stack)) {
            result.append(Component.translatable("gui.dimensional_mana.plating.prefix",
                            elementName(plating.elementId()), roman(plating.layers()))
                    .setStyle(Style.EMPTY.withColor(elementColor(plating.elementId()))));
        }
        return result;
    }

    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!hasPlating(stack)) return;
        List<Plating> layers = allLayers(stack);
        if (layers.isEmpty()) return;
        List<Component> tooltip = event.getToolTip();
        if (tooltip.isEmpty()) return;
        tooltip.set(0, prefix(stack).copy().append(tooltip.get(0)));
        boolean shield = isShield(stack);
        List<Component> info = new ArrayList<>();
        for (Plating plating : layers) {
            int count = plating.layers();
            if (shield) {
                info.add(Component.translatable("gui.dimensional_mana.plating.shield.chance",
                                elementName(plating.elementId()), (int)Math.round(count * PERFECT_BLOCK_PER_LAYER * 100))
                        .withStyle(ChatFormatting.GOLD));
                info.add(Component.translatable("gui.dimensional_mana.plating.shield.wear",
                                elementName(plating.elementId()), count)
                        .withStyle(ChatFormatting.RED));
            } else {
                info.add(Component.translatable("gui.dimensional_mana.plating.armor.resist",
                                elementName(plating.elementId()),
                                (int)Math.round(count * RESIST_PER_LAYER * 100))
                        .withStyle(ChatFormatting.AQUA));
                info.add(Component.translatable("gui.dimensional_mana.plating.armor.wear",
                                elementName(plating.elementId()), count)
                        .withStyle(ChatFormatting.RED));
            }
        }
        info.add(Component.translatable("gui.dimensional_mana.plating.total",
                        totalLayers(stack), MAX_TOTAL_LAYERS).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.addAll(Math.min(1, tooltip.size()), info);
    }

    // ------------------------------------------------------------------ 砂轮

    /** 放上砂轮时直接给出“拆掉镀层与附魔”的成品；原版砂轮逻辑不会自己处理这类装备。 */
    public static void onGrindstonePlace(GrindstoneEvent.OnPlaceItem event) {
        ItemStack top = event.getTopItem();
        if (!hasPlating(top)) return;
        event.setOutput(grindstoneResult(top));
        event.setXp(0);
    }

    /** 取走成品时按层返还 1~2 份对应元素结晶粉末。 */
    public static void onGrindstoneTake(GrindstoneEvent.OnTakeItem event) {
        // 这里必须读 getTopItem()：事件里的 newTopItem 默认是空的（表示取走后输入槽清空），
        // 原始装备放在父类的 top/bottom 里。
        ItemStack top = event.getTopItem();
        if (!hasPlating(top)) return;
        Player player = event.getPlayer();
        if (player == null) return;
        for (Plating plating : allLayers(top)) {
            int count = plating.layers() * (1 + player.getRandom().nextInt(2));
            ItemStack powders = powder(plating.elementId(), count);
            if (powders.isEmpty()) continue;
            if (!player.getInventory().add(powders)) player.drop(powders, false);
        }
    }

    /** 调试与图鉴共用的只读描述：当前元素与层数。 */
    public static String describe(ItemStack stack) {
        StringBuilder builder = new StringBuilder();
        for (Plating plating : allLayers(stack)) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(plating.elementId()).append('=').append(plating.layers());
        }
        return builder.toString();
    }

    /** 供别的系统判断某件装备是否吃某个元素加成（保留给后续功能）。 */
    public static boolean hasElement(ItemStack stack, String elementId) {
        return layers(stack, elementId) > 0;
    }
}
