package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 武备加工台界面。
 *
 * <p>左边是「上面放武备、下面放魔晶、中间按锻造」，右边是词条面板：
 * 标题（名字 / 等级 / 品质）、一格稍大的主词条、四格稍小的副词条（没满就留空）。
 * 所有文字都走 {@link TextFit}，中英文切换后不会互相盖住，也不会顶出面板。</p>
 */
public final class WeaponWorkbenchScreen extends AbstractContainerScreen<WeaponWorkbenchMenu> {
    private static final int PANEL_X = 104;
    private static final int PANEL_Y = 8;
    private static final int PANEL_WIDTH = 160;
    private static final int PANEL_HEIGHT = 144;
    private static final int MAIN_BOX_Y = 38;
    private static final int MAIN_BOX_HEIGHT = 22;
    private static final int SUB_BOX_Y = 70;
    private static final int SUB_BOX_STEP = 18;
    private static final int SUB_BOX_HEIGHT = 16;

    public WeaponWorkbenchScreen(WeaponWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WeaponWorkbenchMenu.IMAGE_WIDTH;
        imageHeight = WeaponWorkbenchMenu.IMAGE_HEIGHT;
        titleLabelX = 8;
        titleLabelY = 5;
        inventoryLabelX = WeaponWorkbenchMenu.INVENTORY_X;
        inventoryLabelY = 154;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xF0100B1E);
        graphics.fillGradient(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xF01B1330, 0xF0091524);
        graphics.renderOutline(x, y, imageWidth, imageHeight, 0xFF8E68C7);
        graphics.hLine(x + 6, x + imageWidth - 7, y + 15, 0x668E68C7);

        slot(graphics, x + WeaponWorkbenchMenu.EQUIP_X - 1, y + WeaponWorkbenchMenu.EQUIP_Y - 1);
        slot(graphics, x + WeaponWorkbenchMenu.MATERIAL_X - 1, y + WeaponWorkbenchMenu.MATERIAL_Y - 1);

        // 中间的关系线：装备 → 材料 → 锻造，让人一眼看出是从上往下喂。
        graphics.vLine(x + WeaponWorkbenchMenu.EQUIP_X + 8, y + WeaponWorkbenchMenu.EQUIP_Y + 18,
                y + WeaponWorkbenchMenu.MATERIAL_Y - 2, 0x66B98CFF);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(graphics, x + WeaponWorkbenchMenu.INVENTORY_X + col * 18 - 1,
                        y + WeaponWorkbenchMenu.INVENTORY_Y + row * 18 - 1);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(graphics, x + WeaponWorkbenchMenu.INVENTORY_X + col * 18 - 1,
                    y + WeaponWorkbenchMenu.HOTBAR_Y - 1);
        }

        graphics.fill(x + PANEL_X, y + PANEL_Y, x + PANEL_X + PANEL_WIDTH, y + PANEL_Y + PANEL_HEIGHT, 0x90140E24);
        graphics.renderOutline(x + PANEL_X, y + PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT, 0xFF8E68C7);
        drawForgeButton(graphics, mouseX, mouseY);
    }

    private void drawForgeButton(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + WeaponWorkbenchMenu.BUTTON_X;
        int y = topPos + WeaponWorkbenchMenu.BUTTON_Y;
        int w = WeaponWorkbenchMenu.BUTTON_WIDTH;
        int h = WeaponWorkbenchMenu.BUTTON_HEIGHT;
        boolean active = canForge();
        boolean hovered = active && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        graphics.fill(x, y, x + w, y + h, !active ? 0xFF221A30 : hovered ? 0xFF4B3575 : 0xFF33244F);
        graphics.renderOutline(x, y, w, h, !active ? 0xFF5A4A70 : hovered ? 0xFFFFD26F : 0xFFB98CFF);
        TextFit.centered(graphics, font, Component.translatable("gui.dimensional_mana.weapon_workbench.forge"),
                leftPos + WeaponWorkbenchMenu.BUTTON_X + w / 2, topPos + WeaponWorkbenchMenu.BUTTON_Y + 5,
                w - 8, active ? 0xFFF3E9FF : 0xFF7C6E94);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        TextFit.leftShrunk(graphics, font, title, titleLabelX, titleLabelY,
                PANEL_X - titleLabelX - 4, 0xFFEAD9FF);
        TextFit.left(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                imageWidth - inventoryLabelX * 2, 0xFFBDB0CE, false);
        renderAffixPanel(graphics);
        renderCostHint(graphics);
    }

    /** 右边那块词条面板。 */
    private void renderAffixPanel(GuiGraphics graphics) {
        int x = PANEL_X;
        int y = PANEL_Y;
        int innerWidth = PANEL_WIDTH - 12;
        ItemStack stack = menu.equipment();
        if (stack.isEmpty()) {
            TextFit.centered(graphics, font,
                    Component.translatable("gui.dimensional_mana.weapon_workbench.put_equipment"),
                    x + PANEL_WIDTH / 2, y + PANEL_HEIGHT / 2 - 4, innerWidth, 0xFF8A7FA8);
            return;
        }
        TextFit.leftShrunk(graphics, font, stack.getHoverName(), x + 6, y + 5, innerWidth, 0xFFF0E6FF);
        EquipmentAffixSystem.View view = EquipmentAffixSystem.view(stack);
        if (view.empty()) {
            TextFit.centered(graphics, font,
                    Component.translatable("gui.dimensional_mana.weapon_workbench.no_affix"),
                    x + PANEL_WIDTH / 2, y + PANEL_HEIGHT / 2 - 4, innerWidth, 0xFFB58C8C);
            return;
        }
        int gradeColor = gradeColor(view.gradeName());
        Component meta = Component.translatable("gui.dimensional_mana.weapon_workbench.meta",
                view.gradeName(), view.mainLevel(), ArmamentForge.MAX_LEVEL);
        if (EquipmentAffixSystem.isTemplateForged(stack)) {
            meta = meta.copy().append(Component.translatable("gui.dimensional_mana.weapon_workbench.template_tag"));
        }
        TextFit.leftShrunk(graphics, font, meta, x + 6, y + 17, innerWidth, gradeColor);

        // 主词条：字体稍大的一格。
        int boxX = x + 6;
        int boxY = y + MAIN_BOX_Y;
        graphics.fill(boxX, boxY, boxX + innerWidth, boxY + MAIN_BOX_HEIGHT, 0xA03A2559);
        graphics.renderOutline(boxX, boxY, innerWidth, MAIN_BOX_HEIGHT, 0xFFD8A94C);
        drawScaled(graphics, view.main().name(), boxX + 5, boxY + 5, 1.12F, innerWidth - 78, 0xFFF7E7BE);
        drawScaledRight(graphics, Component.literal(mainValueText(view.main())), boxX + innerWidth - 5,
                boxY + 5, 1.05F, 62, 0xFFFFE9A8);

        // 四条副词条：没满的槽位留空（画一个暗淡的占位框）。
        for (int i = 0; i < EquipmentAffixSystem.SUB_MAX_COUNT; i++) {
            int slotY = y + SUB_BOX_Y + i * SUB_BOX_STEP;
            graphics.fill(boxX, slotY, boxX + innerWidth, slotY + SUB_BOX_HEIGHT, 0x70241A37);
            graphics.renderOutline(boxX, slotY, innerWidth, SUB_BOX_HEIGHT, 0xFF5C4A78);
            EquipmentAffixSystem.AffixLine line = i < view.subs().size() ? view.subs().get(i) : null;
            if (line == null) {
                graphics.drawString(font, "-", boxX + 5, slotY + 5, 0xFF5F5670, false);
                continue;
            }
            drawScaled(graphics, line.name(), boxX + 5, slotY + 5, 0.92F, innerWidth - 70, 0xFFDDD2F0);
            drawScaledRight(graphics, Component.literal(subValueText(line)), boxX + innerWidth - 5,
                    slotY + 5, 0.92F, 54, 0xFFFFD9F5);
        }
    }

    /** 按钮下面的消耗提示：够不够、缺多少、洗练还是升级。 */
    private void renderCostHint(GuiGraphics graphics) {
        ItemStack stack = menu.equipment();
        ItemStack material = menu.material();
        Component hint = null;
        int color = 0xFF9A8FB5;
        if (material.isEmpty()) {
            hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_material");
        } else if (ArmamentForge.isSmithingTemplate(material)) {
            if (stack.isEmpty()) {
                hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_template_need");
                color = 0xFFFF7777;
            } else if (EquipmentAffixSystem.hasAffix(stack)) {
                hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_template_already");
                color = 0xFFFF7777;
            } else {
                hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_template");
                color = 0xFFD8C06C;
            }
        } else if (ArmamentForge.isEnhanceStone(material)) {
            if (stack.isEmpty() || !EquipmentAffixSystem.hasAffix(stack)) {
                hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_stone_no_affix");
                color = 0xFFFF7777;
            } else if (EquipmentAffixSystem.mainLevel(stack) < ArmamentForge.MAX_LEVEL) {
                hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_stone_need_max",
                        ArmamentForge.MAX_LEVEL);
                color = 0xFFFF7777;
            } else if (!EquipmentAffixSystem.hasGrowableSub(stack)) {
                hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_subs_max");
                color = 0xFFFFD86B;
            } else {
                hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_stone");
                color = 0xFFD98CFF;
            }
        } else if (ArmamentForge.isRerollMaterial(material)) {
            hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_reroll");
            color = 0xFFD98CFF;
        } else if (!stack.isEmpty() && EquipmentAffixSystem.hasAffix(stack)) {
            int level = EquipmentAffixSystem.mainLevel(stack);
            if (level >= ArmamentForge.MAX_LEVEL) {
                hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_max");
                color = 0xFFFFD86B;
            } else {
                int cost = ArmamentForge.upgradeCost(stack, level + 1);
                int value = ArmamentForge.crystalValue(material);
                if (value < cost) {
                    hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_short", cost);
                    color = 0xFFFF7777;
                } else {
                    int refund = ArmamentForge.refundValue(value - cost);
                    hint = refund > 0
                            ? Component.translatable("gui.dimensional_mana.weapon_workbench.hint_cost_refund", cost, refund)
                            : Component.translatable("gui.dimensional_mana.weapon_workbench.hint_cost", cost);
                    color = 0xFF9AD7FF;
                }
            }
        }
        if (hint == null) return;
        drawScaled(graphics, hint, WeaponWorkbenchMenu.BUTTON_X - 2, WeaponWorkbenchMenu.BUTTON_Y + 22,
                0.82F, 92, color);
    }

    /** 主词条按 LV 显示。 */
    private static String mainValueText(EquipmentAffixSystem.AffixLine line) {
        return line.displayValue() + " · Lv." + line.level();
    }

    /** 副词条：初始 1 级是「无等级」只写数值，提升后按 +1～+4 标。 */
    private static String subValueText(EquipmentAffixSystem.AffixLine line) {
        return line.level() <= 1 ? line.displayValue()
                : line.displayValue() + " · +" + (line.level() - 1);
    }

    private static int gradeColor(String grade) {
        return switch (grade == null ? "" : grade) {
            case "SSS" -> 0xFFFFC861;
            case "SS" -> 0xFFD98CFF;
            case "S" -> 0xFF7FA8FF;
            case "A" -> 0xFF6FE08C;
            case "B" -> 0xFFEDEDED;
            default -> 0xFF9AA0AC;
        };
    }

    /**
     * 放大绘制：主词条 1.15 倍、副词条 0.92 倍。
     *
     * <p>放不下时先整体缩字（最多缩到 {@link TextFit#MIN_SCALE}），实在放不下才截断。
     * 英文词条名（“Spell Crit Damage”）比中文长一倍，不缩字就只能看前半截。</p>
     */
    private void drawScaled(GuiGraphics graphics, Component text, int x, int y, float baseScale,
                            int maxWidth, int color) {
        float scale = fittedScale(text, baseScale, maxWidth);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        Component fitted = TextFit.clamp(font, text, Math.max(1, (int)(maxWidth / scale)));
        graphics.drawString(font, fitted, 0, 0, color, false);
        pose.popPose();
    }

    private void drawScaledRight(GuiGraphics graphics, Component text, int rightX, int y, float baseScale,
                                 int maxWidth, int color) {
        float scale = fittedScale(text, baseScale, maxWidth);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(rightX, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        Component fitted = TextFit.clamp(font, text, Math.max(1, (int)(maxWidth / scale)));
        graphics.drawString(font, fitted, -font.width(fitted), 0, color, false);
        pose.popPose();
    }

    private float fittedScale(Component text, float baseScale, int maxWidth) {
        int raw = font.width(text);
        if (raw <= 0) return baseScale;
        float allowed = Math.max(1, maxWidth) / baseScale;
        if (raw <= allowed) return baseScale;
        return baseScale * Math.max(TextFit.MIN_SCALE, allowed / raw);
    }

    private boolean canForge() {
        ItemStack stack = menu.equipment();
        ItemStack material = menu.material();
        if (stack.isEmpty() || material.isEmpty()) return false;
        // 锻造模板：只给还没有词条的白板装备赋武备。
        if (ArmamentForge.isSmithingTemplate(material)) return !EquipmentAffixSystem.hasAffix(stack);
        if (!EquipmentAffixSystem.hasAffix(stack)) return false;
        // 武备强化石：武备满级 + 还有没满的副词条才能敲。
        if (ArmamentForge.isEnhanceStone(material)) {
            return EquipmentAffixSystem.mainLevel(stack) >= ArmamentForge.MAX_LEVEL
                    && EquipmentAffixSystem.hasGrowableSub(stack);
        }
        if (ArmamentForge.isRerollMaterial(material)) return true;
        int level = EquipmentAffixSystem.mainLevel(stack);
        return level < ArmamentForge.MAX_LEVEL
                && ArmamentForge.crystalValue(material) >= ArmamentForge.upgradeCost(stack, level + 1);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = leftPos + WeaponWorkbenchMenu.BUTTON_X;
        int y = topPos + WeaponWorkbenchMenu.BUTTON_Y;
        if (button == 0 && mouseX >= x && mouseX < x + WeaponWorkbenchMenu.BUTTON_WIDTH
                && mouseY >= y && mouseY < y + WeaponWorkbenchMenu.BUTTON_HEIGHT) {
            if (canForge() && minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WeaponWorkbenchMenu.BUTTON_FORGE);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderSlotTooltips(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /** 悬停词条面板时给出完整的词条说明（含数值与等级）。 */
    private void renderSlotTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + PANEL_X;
        int y = topPos + PANEL_Y;
        if (mouseX < x || mouseX >= x + PANEL_WIDTH || mouseY < y || mouseY >= y + PANEL_HEIGHT) return;
        ItemStack stack = menu.equipment();
        if (stack.isEmpty()) return;
        EquipmentAffixSystem.View view = EquipmentAffixSystem.view(stack);
        if (view.empty()) return;
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(stack.getHoverName().copy().withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.affix_grade", view.gradeName())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.affix_school",
                Component.translatable("tooltip.dimensional_mana.affix_school." + view.schoolId()))
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(view.main().asLine().copy().withStyle(ChatFormatting.GOLD));
        for (EquipmentAffixSystem.AffixLine line : view.subs()) {
            tooltip.add(Component.literal("  ").append(line.asLine()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("gui.dimensional_mana.weapon_workbench.rerolls", view.rerolls())
                .withStyle(ChatFormatting.DARK_GRAY));
        graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    private static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF0C0913);
        graphics.renderOutline(x, y, 18, 18, 0xFF6B558D);
    }
}
