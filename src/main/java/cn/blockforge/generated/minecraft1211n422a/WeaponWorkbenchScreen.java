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
    private final ArcaneLayout.Layout layout;
    private final int PANEL_X;
    private final int PANEL_Y;
    private final int PANEL_WIDTH;
    private final int PANEL_HEIGHT;
    private static final int MAIN_BOX_Y = 38;
    private static final int MAIN_BOX_HEIGHT = 22;
    private static final int SUB_BOX_Y = 64;
    private static final int SUB_BOX_STEP = 18;
    private static final int SUB_BOX_HEIGHT = 16;

    public WeaponWorkbenchScreen(WeaponWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WeaponWorkbenchMenu.IMAGE_WIDTH;
        imageHeight = WeaponWorkbenchMenu.IMAGE_HEIGHT;
        titleLabelX = 8;
        titleLabelY = 5;
        layout = ArcaneGui.layout(menu, ArcaneLayout.Kind.WEAPON);
        PANEL_X = layout.plate().x(); PANEL_Y = layout.plate().y();
        PANEL_WIDTH = layout.plate().width(); PANEL_HEIGHT = layout.plate().height();
        inventoryLabelX = layout.inventoryLabelX();
        inventoryLabelY = layout.inventoryLabelY();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        ArcaneGui.panel(graphics, x, y, imageWidth, imageHeight, partialTick);
        ArcaneGui.zones(graphics, layout, x, y);
        ArcaneGui.conduit(graphics, layout.conduit(), x, y);
        // 词条区只有暗底和内沿微光，不再增加金边和四角装饰。
        ArcaneGui.plate(graphics, x + PANEL_X, y + PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT, ArcaneGui.ACCENT);

        drawForgeButton(graphics, mouseX, mouseY);
        ArcaneGui.slots(graphics, menu, x, y);
    }

    private void drawForgeButton(GuiGraphics graphics, int mouseX, int mouseY) {
        ArcaneLayout.Box b = layout.button();
        int x = leftPos + b.x();
        int y = topPos + b.y();
        int w = b.width();
        int h = b.height();
        boolean active = canForge();
        boolean hovered = active && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        ArcaneGui.button(graphics, x, y, w, h, hovered, active, ArcaneGui.GOLD);
        TextFit.centered(graphics, font, Component.translatable("gui.dimensional_mana.weapon_workbench.forge"),
                x + w / 2, y + (h - font.lineHeight) / 2,
                w - 8, active ? ArcaneGui.TEXT : ArcaneGui.TEXT_FAINT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        TextFit.leftShrunk(graphics, font, title, titleLabelX, titleLabelY,
                PANEL_X - titleLabelX - 4, ArcaneGui.TEXT);
        TextFit.left(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                imageWidth - inventoryLabelX * 2, ArcaneGui.TEXT_DIM, false);
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
                    x + PANEL_WIDTH / 2, y + PANEL_HEIGHT / 2 - 4, innerWidth, ArcaneGui.TEXT_FAINT);
            return;
        }
        TextFit.leftShrunk(graphics, font, stack.getHoverName(), x + 6, y + 5, innerWidth, ArcaneGui.TEXT);
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
        graphics.fill(boxX, boxY, boxX + innerWidth, boxY + MAIN_BOX_HEIGHT, 0xFF242833);
        graphics.fill(boxX + 1, boxY + 1, boxX + innerWidth - 1, boxY + 2, 0x185F6377);
        drawScaled(graphics, view.main().name(), boxX + 5, boxY + 6, 1.0F, innerWidth - 78, ArcaneGui.TEXT);
        drawScaledRight(graphics, Component.literal(mainValueText(view.main())), boxX + innerWidth - 5,
                boxY + 6, 1.0F, 62, ArcaneGui.ACCENT);

        // 四条副词条：没满的槽位留空（画一个暗淡的占位框）。
        for (int i = 0; i < EquipmentAffixSystem.SUB_MAX_COUNT; i++) {
            int slotY = y + SUB_BOX_Y + i * SUB_BOX_STEP;
            graphics.fill(boxX, slotY, boxX + innerWidth, slotY + SUB_BOX_HEIGHT, 0x801A1E27);
            EquipmentAffixSystem.AffixLine line = i < view.subs().size() ? view.subs().get(i) : null;
            if (line == null) {
                graphics.drawString(font, "-", boxX + 5, slotY + 5, ArcaneGui.TEXT_FAINT, false);
                continue;
            }
            drawScaled(graphics, line.name(), boxX + 5, slotY + 5, 0.92F, innerWidth - 70, ArcaneGui.TEXT_DIM);
            drawScaledRight(graphics, Component.literal(subValueText(line)), boxX + innerWidth - 5,
                    slotY + 5, 0.92F, 54, ArcaneGui.ACCENT);
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
                color = ArcaneGui.WARN;
            } else if (EquipmentAffixSystem.hasAffix(stack)) {
                hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_template_already");
                color = ArcaneGui.WARN;
            } else {
                hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_template");
                color = ArcaneGui.GOLD;
            }
        } else if (ArmamentForge.isEnhanceStone(material)) {
            if (stack.isEmpty() || !EquipmentAffixSystem.hasAffix(stack)) {
                hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_stone_no_affix");
                color = ArcaneGui.WARN;
            } else if (EquipmentAffixSystem.mainLevel(stack) < ArmamentForge.MAX_LEVEL) {
                hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_stone_need_max",
                        ArmamentForge.MAX_LEVEL);
                color = ArcaneGui.WARN;
            } else if (!EquipmentAffixSystem.hasGrowableSub(stack)) {
                hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_subs_max");
                color = ArcaneGui.GOLD;
            } else {
                hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_stone");
                color = ArcaneGui.ACCENT;
            }
        } else if (ArmamentForge.isRerollMaterial(material)) {
            hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_reroll");
            color = ArcaneGui.ACCENT;
        } else if (ArmamentForge.isReforgeTalisman(material)) {
            if (stack.isEmpty() || !EquipmentAffixSystem.hasAffix(stack)) {
                hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_reforge_no_affix");
                color = ArcaneGui.WARN;
            } else {
                hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_reforge");
                color = ArcaneGui.GOLD;
            }
        } else if (!stack.isEmpty() && EquipmentAffixSystem.hasAffix(stack)) {
            int level = EquipmentAffixSystem.mainLevel(stack);
            if (level >= ArmamentForge.MAX_LEVEL) {
                hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_max");
                color = ArcaneGui.GOLD;
            } else {
                int cost = ArmamentForge.upgradeCost(stack, level + 1);
                int value = ArmamentForge.crystalValue(material);
                if (value < cost) {
                    hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_short", cost);
                    color = ArcaneGui.WARN;
                } else {
                    int refund = ArmamentForge.refundValue(value - cost);
                    hint = refund > 0
                            ? Component.translatable("gui.dimensional_mana.weapon_workbench.hint_cost_refund", cost, refund)
                            : Component.translatable("gui.dimensional_mana.weapon_workbench.hint_cost", cost);
                    color = ArcaneGui.MANA;
                }
            }
        }
        if (hint == null) return;
        drawScaled(graphics, hint, layout.button().x(), layout.button().bottom() + 6,
                0.90F, layout.plate().x() - layout.button().x() - 6, color);
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
            case "SSS" -> ArcaneGui.GOLD;
            case "SS" -> ArcaneGui.ACCENT;
            case "S" -> ArcaneGui.MANA;
            case "A" -> ArcaneGui.OK;
            case "B" -> 0xFFEDEDED;
            default -> 0xFF9AA0AC;
        };
    }

    /**
     * 自适应绘制：主词条原字号、副词条 0.92 倍。
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
        if (ArmamentForge.isReforgeTalisman(material)) return EquipmentAffixSystem.hasAffix(stack);
        int level = EquipmentAffixSystem.mainLevel(stack);
        return level < ArmamentForge.MAX_LEVEL
                && ArmamentForge.crystalValue(material) >= ArmamentForge.upgradeCost(stack, level + 1);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && layout.button().contains(mouseX - leftPos, mouseY - topPos)) {
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

}
