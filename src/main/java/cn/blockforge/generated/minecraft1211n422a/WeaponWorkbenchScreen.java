package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 武备加工台界面。
 *
 * <p>左边是「上面放武备、下面放魔晶、中间按锻造」，右边是词条面板：
 * 标题（名字 / 等级 / 品质）、一格稍大的主词条、四格稍小的副词条（没满就留空）。
 * 所有文字都走 {@link TextFit}，中英文切换后不会互相盖住，也不会顶出面板。</p>
 *
 * <p>材料格放超导魔晶或词条重铸符时，材料格右边会多出一个「＋」按钮：点开是目标词条
 * 锁定面板（可搜索、可勾 1 主 4 副）。洗出勾选的目标词条后，面板里那几行标红加粗，
 * 按钮下方亮起锁定提示，「锻造」直接灰掉，必须取消勾选或点「放弃本次结果」才会恢复。</p>
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

    /** 材料格右侧的「＋」按钮。 */
    private static final int LOCK_BUTTON_SIZE = 14;
    private static final int LOCK_BUTTON_DX = 20;
    private static final int LOCK_BUTTON_DY = 1;

    /** 锁定面板：盖在界面正中的临时弹窗。 */
    private static final int POPUP_W = 256;
    private static final int POPUP_H = 190;
    private static final int ROW_H = 13;
    private static final int ROWS = 8;
    private static final int LIST_TOP = 52;
    private static final int LEFT_COL = 10;
    private static final int RIGHT_COL = 132;
    private static final int COL_W = 114;
    private static final int SEARCH_MAX = 24;
    /** 搜索框在弹窗里的位置。 */
    private static final int SEARCH_X = 10;
    private static final int SEARCH_Y = 22;
    private static final int SEARCH_W = POPUP_W - 60;
    private static final int SEARCH_H = 14;

    /**
     * 弹窗的绘制层级。
     *
     * <p>原版柜子界面把槽位物品画在 z=232（{@code AbstractContainerScreen#renderSlots}），
     * 悬浮的物品同样是 232，槽位高亮是 100。弹窗留在默认的 z=0 时，背包里的物品会直接浮在
     * 弹窗上面，看起来就是「弹窗是半透明的」。抬到 300 就整块压住物品，又低于原版提示框
     * （提示框在 400），鼠标悬停的词条说明仍然看得见。</p>
     */
    private static final float POPUP_Z = 300.0F;

    /** 按钮下方的锁定提示条 / 放弃按钮（界面内坐标）。 */
    private static final int GUARD_X = 13;
    private static final int GUARD_Y = 118;
    private static final int GUARD_W = 76;
    private static final int GUARD_H = 14;
    private static final int DISCARD_Y = 134;

    private boolean lockPanelOpen;
    private String search = "";
    /** 搜索框：用原版 {@link EditBox}，输入法、粘贴、光标、选区都由它管。 */
    private EditBox searchBox;
    private int mainScroll;
    private int subScroll;
    private List<EquipmentAffixSystem.AffixOption> mainOptions = List.of();
    private List<EquipmentAffixSystem.AffixOption> subOptions = List.of();
    private List<EquipmentAffixSystem.AffixOption> mainFiltered = List.of();
    private List<EquipmentAffixSystem.AffixOption> subFiltered = List.of();

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
        ClientWorkbenchGuardState.clear();
    }

    // ------------------------------------------------------------------
    // 背景与按钮
    // ------------------------------------------------------------------

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
        drawLockButton(graphics, mouseX, mouseY);
        ArcaneGui.slots(graphics, menu, x, y);
    }

    /**
     * 弹窗打开时，被它盖住的槽位整格都不画。
     *
     * <p>槽位（物品图标 + 右下角那串数量数字）是原版自己在 {@code renderLabels} 前后走一轮画的。
     * 光把弹窗整体抬到 {@link #POPUP_Z} 只能压住图标：数量数字那一小串照样会浮在弹窗上面，
     * 看着就是「物品数量从面板里透出来」。与其跟绘制层级较劲，不如从源头不画——
     * 被弹窗盖住的格子本来也看不见，弹窗外面的背包格照常显示。</p>
     */
    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (lockPanelOpen && popupCoversSlot(slot)) return;
        super.renderSlot(graphics, slot);
    }

    /** 这一格是不是整格都压在弹窗底下（两边的坐标都是界面内坐标）。 */
    private boolean popupCoversSlot(Slot slot) {
        // 弹窗外沿还多垫了 3 像素实心底，一起算进去。
        int px = (imageWidth - POPUP_W) / 2 - 3;
        int py = (imageHeight - POPUP_H) / 2 - 3;
        return slot.x + 16 > px && slot.x < px + POPUP_W + 6
                && slot.y + 16 > py && slot.y < py + POPUP_H + 6;
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

    /** 「＋」只在材料格放着重抽类材料时出现（超导魔晶 / 词条重铸符）。 */
    private boolean lockButtonVisible() {
        return ArmamentForge.rerollsAffixes(menu.material()) && !menu.equipment().isEmpty();
    }

    private ArcaneLayout.Box lockButtonBox() {
        int slotX = WeaponWorkbenchMenu.MATERIAL_X;
        int slotY = WeaponWorkbenchMenu.MATERIAL_Y;
        return new ArcaneLayout.Box(slotX + LOCK_BUTTON_DX, slotY + LOCK_BUTTON_DY,
                LOCK_BUTTON_SIZE, LOCK_BUTTON_SIZE);
    }

    private void drawLockButton(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!lockButtonVisible()) return;
        ArcaneLayout.Box b = lockButtonBox();
        int x = leftPos + b.x();
        int y = topPos + b.y();
        boolean hovered = b.contains(mouseX - leftPos, mouseY - topPos);
        boolean picked = menu.hasLocks();
        ArcaneGui.button(graphics, x, y, b.width(), b.height(), hovered, true,
                picked ? ArcaneGui.GOLD : ArcaneGui.ACCENT);
        TextFit.centered(graphics, font, Component.literal("＋"), x + b.width() / 2, y + 3,
                b.width() - 2, picked ? ArcaneGui.GOLD : ArcaneGui.TEXT);
        if (picked) {
            // 已经勾过东西：右上角挂一个「锁了几个」的小角标，关掉面板也看得出来。
            int count = (menu.lockedMain().isEmpty() ? 0 : 1) + menu.lockedSubs().size();
            String label = String.valueOf(count);
            int badgeX = x + b.width() - 5;
            graphics.fill(badgeX - 1, y - 1, x + b.width() + 3, y + 7, 0xFF101218);
            graphics.renderOutline(badgeX - 1, y - 1, 4 + font.width(label), 8, ArcaneGui.GOLD);
            graphics.drawString(font, label, badgeX, y, ArcaneGui.GOLD, false);
        }
    }

    // ------------------------------------------------------------------
    // 文字层
    // ------------------------------------------------------------------

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        TextFit.leftShrunk(graphics, font, title, titleLabelX, titleLabelY,
                PANEL_X - titleLabelX - 4, ArcaneGui.TEXT);
        TextFit.left(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                imageWidth - inventoryLabelX * 2, ArcaneGui.TEXT_DIM, false);
        renderAffixPanel(graphics);
        if (protectionActive()) renderGuardBanner(graphics, mouseX, mouseY);
        else renderCostHint(graphics);
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
                view.gradeName(), view.mainLevel(), ArmamentForge.maxLevel(stack));
        if (EquipmentAffixSystem.isTemplateForged(stack)) {
            meta = meta.copy().append(Component.translatable("gui.dimensional_mana.weapon_workbench.template_tag"));
        }
        TextFit.leftShrunk(graphics, font, meta, x + 6, y + 17, innerWidth, gradeColor);

        // 主词条：字体稍大的一格。
        int boxX = x + 6;
        int boxY = y + MAIN_BOX_Y;
        graphics.fill(boxX, boxY, boxX + innerWidth, boxY + MAIN_BOX_HEIGHT, 0xFF242833);
        graphics.fill(boxX + 1, boxY + 1, boxX + innerWidth - 1, boxY + 2, 0x185F6377);
        int mainNameWidth = drawAffixValue(graphics, Component.literal(mainValueText(view.main())),
                view.main().percent(), boxX, innerWidth, boxY + 6, 1.0F, 54);
        drawScaled(graphics, styleLocked(view.main().name(), view.main().lockKey()), boxX + 5, boxY + 6,
                1.0F, mainNameWidth, mainColor(view.main().lockKey()));

        // 四条副词条：没满的槽位留空（画一个暗淡的占位框）。
        for (int i = 0; i < EquipmentAffixSystem.SUB_MAX_COUNT; i++) {
            int slotY = y + SUB_BOX_Y + i * SUB_BOX_STEP;
            graphics.fill(boxX, slotY, boxX + innerWidth, slotY + SUB_BOX_HEIGHT, 0x801A1E27);
            EquipmentAffixSystem.AffixLine line = i < view.subs().size() ? view.subs().get(i) : null;
            if (line == null) {
                graphics.drawString(font, "-", boxX + 5, slotY + 5, ArcaneGui.TEXT_FAINT, false);
                continue;
            }
            int subNameWidth = drawAffixValue(graphics, Component.literal(subValueText(line)),
                    line.percent(), boxX, innerWidth, slotY + 5, 0.92F, 46);
            drawScaled(graphics, styleLocked(line.name(), line.lockKey()), boxX + 5, slotY + 5, 0.92F,
                    subNameWidth, subColor(line.lockKey()));
        }
    }

    /** 目标词条是整行标红的，颜色和加粗都从这一处走。 */
    private Component styleLocked(Component text, String key) {
        return menu.isLockedTarget(key) ? text.copy().withStyle(ChatFormatting.BOLD) : text;
    }

    private int mainColor(String key) {
        return menu.isLockedTarget(key) ? 0xFFFF6B6B : ArcaneGui.TEXT;
    }

    private int subColor(String key) {
        return menu.isLockedTarget(key) ? 0xFFFF6B6B : ArcaneGui.TEXT_DIM;
    }

    /**
     * 画一条词条右边那半截：数值 + 类型标签（百分比 / 数值），返回名字能用的宽度。
     *
     * <p>斗气、物理伤害、最大生命值这些都成对存在：一条按百分比加（+3.5%），一条加固定数值（+12）。
     * 名字一模一样，数值又都是「数字」，不标出来玩家只能靠猜。标签贴着行尾，
     * 百分比用偏蓝的魔素色，固定数值用灰白色。</p>
     */
    private int drawAffixValue(GuiGraphics graphics, Component value, boolean percent,
                               int boxX, int innerWidth, int y, float scale, int valueMax) {
        int rightEdge = boxX + innerWidth - 5;
        Component tag = EquipmentAffixSystem.typeTag(percent);
        int tagWidth = Math.max(12, Math.round(font.width(tag) * scale * 0.78F));
        drawScaledRight(graphics, tag, rightEdge, y + 1, scale * 0.78F, tagWidth + 6,
                percent ? ArcaneGui.MANA : 0xFF8A91A0);
        int valueRight = rightEdge - tagWidth - 4;
        drawScaledRight(graphics, value, valueRight, y, scale, valueMax, ArcaneGui.ACCENT);
        return Math.max(26, valueRight - valueMax - 4 - (boxX + 5));
    }

    /** 防手滑锁定提示：盖在原来消耗提示的位置，红底金字，一眼能看到。 */
    private void renderGuardBanner(GuiGraphics graphics, int mouseX, int mouseY) {
        // 这里是界面内坐标：renderLabels 画的时候画布已经平移过 (leftPos, topPos) 了。
        // 以前又各加了一次 leftPos / topPos，「放弃本次结果」就被画到别处去了——
        // 看得见按钮、点却点不动（真正能点中的位置在看不见的地方），这就是那个 bug。
        int x = GUARD_X;
        int y = GUARD_Y;
        graphics.fill(x, y, x + GUARD_W, y + GUARD_H, 0x66A03038);
        graphics.renderOutline(x, y, GUARD_W, GUARD_H, 0xFFFF6B6B);
        drawWarningMark(graphics, x + 3, y + 2);
        TextFit.centered(graphics, font, Component.translatable("gui.dimensional_mana.weapon_workbench.guard_hit"),
                x + GUARD_W / 2 + 3, y + 3, GUARD_W - 12, 0xFFFFB3B3);

        // 「放弃本次结果」：只有保护生效时才出现。
        int dy = DISCARD_Y;
        boolean hovered = inside(mouseX - leftPos, mouseY - topPos, x, dy, GUARD_W, 12);
        ArcaneGui.button(graphics, x, dy, GUARD_W, 12, hovered, true, ArcaneGui.WARN);
        TextFit.centered(graphics, font, Component.translatable("gui.dimensional_mana.weapon_workbench.discard"),
                x + GUARD_W / 2, dy + 2, GUARD_W - 6, ArcaneGui.WARN);
    }

    /** 不用特殊字符画感叹号图标：11×11 的三角 + 一竖一点，任何字体下都不会缺字。 */
    private static void drawWarningMark(GuiGraphics graphics, int x, int y) {
        for (int row = 0; row < 5; row++) {
            int half = row + 1;
            graphics.fill(x + 5 - half, y + row + 1, x + 5 + half, y + row + 2, 0xFFFF6B6B);
        }
        graphics.fill(x + 5, y + 2, x + 6, y + 4, 0xFF101218);
        graphics.fill(x + 5, y + 5, x + 6, y + 6, 0xFF101218);
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
            } else if (EquipmentAffixSystem.mainLevel(stack) < ArmamentForge.maxLevel(stack)) {
                hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_stone_need_max",
                        ArmamentForge.maxLevel(stack));
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
            if (level >= ArmamentForge.maxLevel(stack)) {
                hint = Component.translatable("gui.dimensional_mana.weapon_workbench.hint_max",
                        ArmamentForge.maxLevel(stack));
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

    // ------------------------------------------------------------------
    // 锁定面板
    // ------------------------------------------------------------------

    private void openLockPanel() {
        lockPanelOpen = true;
        search = "";
        mainScroll = 0;
        subScroll = 0;
        refreshOptions();
        layoutSearchBox();
    }

    private void closeLockPanel() {
        lockPanelOpen = false;
        search = "";
        layoutSearchBox();
    }

    /**
     * 把搜索框摆到弹窗里，并让它拿到焦点。
     *
     * <p>以前是自己拼字符串，只在 {@code charTyped} 里一个字符一个字符地接。中文不是这么进来的：
     * 拼音要在系统输入法里先组词，按下空格/回车「上屏」时才整段交给游戏，游戏也只认原版输入框
     * （聊天栏、铁砧改名、告示牌用的同一个）。所以这里换成 {@link EditBox}：输入法上屏、
     * Ctrl+V 粘贴、方向键、选区、字数上限全都和聊天栏一致。</p>
     *
     * <p>每帧都会调用一次，窗口缩放之后位置自动跟着弹窗走。</p>
     */
    private void layoutSearchBox() {
        if (!lockPanelOpen) {
            if (searchBox != null) {
                setFocused(null);
                searchBox = null;
            }
            return;
        }
        int px = leftPos + (imageWidth - POPUP_W) / 2;
        int py = topPos + (imageHeight - POPUP_H) / 2;
        int x = px + SEARCH_X + 3;
        int y = py + SEARCH_Y + 3;
        int w = SEARCH_W - 8;
        if (searchBox == null) {
            EditBox box = new EditBox(font, x, y, w, font.lineHeight, Component.empty());
            box.setBordered(false);
            box.setMaxLength(SEARCH_MAX);
            box.setTextColor(ArcaneGui.TEXT);
            box.setTextColorUneditable(ArcaneGui.TEXT_FAINT);
            box.setResponder(text -> {
                search = text;
                applySearch();
            });
            box.setValue("");
            box.setFocused(true);
            searchBox = box;
            setFocused(box);
        } else {
            searchBox.setX(x);
            searchBox.setY(y);
            searchBox.setWidth(w);
            searchBox.setHeight(font.lineHeight);
        }
    }

    /** 清空搜索框（按钮用），并重新拿回焦点接着打字。 */
    private void clearSearch() {
        if (searchBox != null) {
            searchBox.setValue("");
            searchBox.setFocused(true);
            setFocused(searchBox);
        } else {
            search = "";
            applySearch();
        }
    }

    private void refreshOptions() {
        ItemStack stack = menu.equipment();
        mainOptions = EquipmentAffixSystem.mainOptions(stack);
        subOptions = EquipmentAffixSystem.subOptions(stack);
        applySearch();
    }

    private void applySearch() {
        // 去掉首尾空白，中文输入法的全角空格也当成空格，免得「搜 mosu」永远搜不到。
        String needle = search.toLowerCase(java.util.Locale.ROOT).replace('\u3000', ' ').trim();
        mainFiltered = filter(mainOptions, needle);
        subFiltered = filter(subOptions, needle);
        mainScroll = Math.max(0, Math.min(mainScroll, maxScroll(mainFiltered.size())));
        subScroll = Math.max(0, Math.min(subScroll, maxScroll(subFiltered.size())));
    }

    /**
     * 搜索：空格分开的每个词都要能在这条词条里找到（全部命中才留下）。
     *
     * <p>能匹配的文字见 {@link EquipmentAffixSystem#searchText}：界面上的中文名、英文 id
     * （{@code mana_percent}、{@code element_damage#fire}）、元素、以及拼音的全拼 / 连写 / 首字母。
     * 所以「mo su」「mosu」「ms」「wulishanghai」「shanghai」「spell crit」都能搜到东西——
     * 游戏里调不出输入法也能用。</p>
     */
    private List<EquipmentAffixSystem.AffixOption> filter(List<EquipmentAffixSystem.AffixOption> source,
                                                          String needle) {
        if (needle.isEmpty()) return source;
        String[] terms = needle.split("\\s+");
        List<EquipmentAffixSystem.AffixOption> out = new ArrayList<>();
        for (EquipmentAffixSystem.AffixOption option : source) {
            String haystack = EquipmentAffixSystem.searchText(option);
            boolean all = true;
            for (String term : terms) {
                if (!haystack.contains(term)) {
                    all = false;
                    break;
                }
            }
            if (all) out.add(option);
        }
        return out;
    }

    private static int maxScroll(int size) {
        return Math.max(0, size - ROWS);
    }

    private boolean protectionActive() {
        return ClientWorkbenchGuardState.active(menu.containerId);
    }

    /** 勾选变了：先改本地那份，再原样发给服务端（服务端会重新校验）。 */
    private void toggleLock(EquipmentAffixSystem.AffixOption option, boolean main) {
        String mainKey = menu.lockedMain();
        List<String> subKeys = new ArrayList<>(menu.lockedSubs());
        boolean picked = menu.isLockedTarget(option.key());
        if (picked) {
            if (main) mainKey = "";
            else subKeys.remove(option.key());
        } else {
            if (!option.selectable()) return;
            if (main) mainKey = option.key();
            else if (subKeys.size() < WeaponWorkbenchMenu.MAX_LOCKED_SUBS) subKeys.add(option.key());
            else return;
        }
        menu.applyLocks(mainKey, subKeys);
        if (minecraft != null) {
            PacketDistributor.sendToServer(new WorkbenchLockPayload(
                    menu.containerId, menu.lockedMain(), menu.lockedSubs()));
        }
    }

    private void renderPopup(GuiGraphics graphics, int mouseX, int mouseY) {
        int px = leftPos + (imageWidth - POPUP_W) / 2;
        int py = topPos + (imageHeight - POPUP_H) / 2;
        layoutSearchBox();
        var pose = graphics.pose();
        pose.pushPose();
        // 抬到物品（232）上面、提示框（400）下面：弹窗里不会再浮出背包里的东西。
        pose.translate(0.0F, 0.0F, POPUP_Z);
        try {
            // 整屏压暗，弹窗以外的东西都退到后面去。
            graphics.fill(leftPos - 2, topPos - 2, leftPos + imageWidth + 2, topPos + imageHeight + 2,
                    0xD0000000);
            // 先铺一块实心底：面板的渐变、法阵都是叠上去的，底下不垫实就还是透。
            graphics.fill(px - 3, py - 3, px + POPUP_W + 3, py + POPUP_H + 3, 0xFF05070C);
            ArcaneGui.panel(graphics, px, py, POPUP_W, POPUP_H, 0.0F);
            TextFit.leftShrunk(graphics, font,
                    Component.translatable("gui.dimensional_mana.weapon_workbench.lock_title"),
                    px + 10, py + 7, POPUP_W - 40, ArcaneGui.TEXT);
            // 右上角关闭。
            ArcaneGui.button(graphics, px + POPUP_W - 20, py + 5, 12, 12,
                    inside(mouseX, mouseY, px + POPUP_W - 20, py + 5, 12, 12), true, ArcaneGui.ACCENT);
            TextFit.centered(graphics, font, Component.literal("✗"), px + POPUP_W - 14, py + 8, 10, ArcaneGui.TEXT_DIM);

            drawSearchField(graphics, px, py, mouseX, mouseY);
            if (menu.equipment().isEmpty() || !EquipmentAffixSystem.hasAffix(menu.equipment())) {
                TextFit.centered(graphics, font,
                        Component.translatable("gui.dimensional_mana.weapon_workbench.lock_need_equipment"),
                        px + POPUP_W / 2, py + 96, POPUP_W - 30, ArcaneGui.TEXT_FAINT);
                return;
            }
            drawColumn(graphics, px + LEFT_COL, py + LIST_TOP, mainFiltered, mainScroll, true,
                    lockedMainLabel(), mouseX, mouseY);
            drawColumn(graphics, px + RIGHT_COL, py + LIST_TOP, subFiltered, subScroll, false,
                    lockedSubLabel(), mouseX, mouseY);
            drawPopupFooter(graphics, px, py, mouseX, mouseY);
        } finally {
            pose.popPose();
        }
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void drawSearchField(GuiGraphics graphics, int px, int py, int mouseX, int mouseY) {
        int x = px + SEARCH_X;
        int y = py + SEARCH_Y;
        ArcaneGui.field(graphics, x, y, SEARCH_W, SEARCH_H);
        if (searchBox != null) searchBox.render(graphics, mouseX, mouseY, 0.0F);
        // 空的时候照旧显示灰提示（输入框自己有光标，提示画在它上面，字不会和光标叠在一起）。
        if (search.isEmpty()) {
            graphics.drawString(font,
                    TextFit.clamp(font,
                            Component.translatable("gui.dimensional_mana.weapon_workbench.lock_search").getString(),
                            SEARCH_W - 8),
                    x + 4, y + 3, ArcaneGui.TEXT_FAINT, false);
        }
        int clearX = x + SEARCH_W + 4;
        ArcaneGui.button(graphics, clearX, y, 36, 14,
                inside(mouseX, mouseY, clearX, y, 36, 14), true, ArcaneGui.ACCENT);
        TextFit.centered(graphics, font, Component.translatable("gui.dimensional_mana.weapon_workbench.lock_clear"),
                clearX + 18, y + 3, 32, ArcaneGui.TEXT_DIM);
    }

    private Component lockedMainLabel() {
        return menu.lockedMain().isEmpty()
                ? Component.translatable("gui.dimensional_mana.weapon_workbench.lock_main_col",
                Component.translatable("gui.dimensional_mana.weapon_workbench.lock_one"))
                : Component.translatable("gui.dimensional_mana.weapon_workbench.lock_main_col",
                Component.translatable("gui.dimensional_mana.weapon_workbench.lock_picked"));
    }

    private Component lockedSubLabel() {
        return Component.translatable("gui.dimensional_mana.weapon_workbench.lock_sub_col",
                menu.lockedSubs().size(), WeaponWorkbenchMenu.MAX_LOCKED_SUBS);
    }

    /**
     * 画一栏词条。
     *
     * <p>传进来的 {@code x} / {@code y} 已经是屏幕坐标（{@code px} 里含了 {@code leftPos}），
     * 所以下面的填充、裁剪、命中判定都直接用它们，不能再加一次 {@code leftPos}——
     * 多减加一次就会把裁剪框整体推出去，列表行被裁光，看着就是「一条词条都搜不出来」。</p>
     */
    private void drawColumn(GuiGraphics graphics, int x, int y, List<EquipmentAffixSystem.AffixOption> items,
                            int scroll, boolean main, Component label, int mouseX, int mouseY) {
        TextFit.leftShrunk(graphics, font, label, x, y - 12, COL_W, ArcaneGui.TEXT_DIM);
        int listHeight = ROWS * ROW_H;
        // 实心底：半透明的话后面的槽位和物品会映出来，看着像没盖住。
        graphics.fill(x, y, x + COL_W, y + listHeight, 0xFF101218);
        graphics.renderOutline(x, y, COL_W, listHeight, ArcaneGui.SLOT_EDGE);
        graphics.enableScissor(x, y, x + COL_W, y + listHeight);
        try {
            for (int row = 0; row < ROWS; row++) {
                int index = scroll + row;
                if (index >= items.size()) break;
                drawRow(graphics, x, y + row * ROW_H, items.get(index), main, mouseX, mouseY);
            }
        } finally {
            graphics.disableScissor();
        }
        if (items.isEmpty()) {
            TextFit.centered(graphics, font,
                    Component.translatable("gui.dimensional_mana.weapon_workbench.lock_no_match"),
                    x + COL_W / 2, y + 8, COL_W - 8, ArcaneGui.TEXT_FAINT);
        }
        drawScrollBar(graphics, x + COL_W - 3, y, listHeight, scroll, items.size());
    }

    private void drawRow(GuiGraphics graphics, int x, int y, EquipmentAffixSystem.AffixOption option,
                         boolean main, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, COL_W, ROW_H);
        boolean picked = menu.isLockedTarget(option.key());
        boolean selectable = option.selectable() || picked;
        if (hovered && selectable) graphics.fill(x, y, x + COL_W - 4, y + ROW_H - 1, 0x33FFFFFF);
        int boxX = x + 3, boxY = y + 3, boxSize = 8;
        graphics.fill(boxX, boxY, boxX + boxSize, boxY + boxSize,
                picked ? 0xFFD9C9A8 : 0xFF14161F);
        graphics.renderOutline(boxX - 1, boxY - 1, boxSize + 2, boxSize + 2,
                selectable ? ArcaneGui.SLOT_EDGE : 0xFF2A2E38);
        if (picked) {
            graphics.drawString(font, Component.literal("✓"), boxX, boxY, 0xFF101218, false);
        }
        int color = !selectable ? 0xFF5C6270
                : picked ? (main ? ArcaneGui.GOLD : ArcaneGui.ACCENT) : ArcaneGui.TEXT_DIM;
        // 类型标签：斗气 / 物理伤害 / 最大生命值这些都有「百分比」和「数值」两条，名字完全一样，
        // 不标出来根本分不清勾的是哪一条。标签贴在最右边，名字让出它的宽度。
        Component tag = EquipmentAffixSystem.typeTag(option.percent());
        int tagWidth = Math.max(12, Math.round(font.width(tag) * 0.78F));
        TextFit.leftShrunk(graphics, font, option.name(), boxX + boxSize + 4, y + 3,
                COL_W - boxSize - 18 - tagWidth, color);
        drawScaledRight(graphics, tag, x + COL_W - 4, y + 4, 0.78F, tagWidth + 6,
                !selectable ? 0xFF5C6270 : (option.percent() ? ArcaneGui.MANA : 0xFF8A91A0));
    }

    private void drawScrollBar(GuiGraphics graphics, int x, int y, int height, int scroll, int size) {
        int max = maxScroll(size);
        if (max <= 0) return;
        int thumb = Math.max(10, height * ROWS / size);
        int offset = (height - thumb) * scroll / max;
        graphics.fill(x, y, x + 2, y + height, 0x88101218);
        graphics.fill(x, y + offset, x + 2, y + offset + thumb, ArcaneGui.ACCENT);
    }

    private void drawPopupFooter(GuiGraphics graphics, int px, int py, int mouseX, int mouseY) {
        int y = py + POPUP_H - 30;
        if (protectionActive()) {
            ArcaneGui.button(graphics, px + 10, y, 96, 14,
                    inside(mouseX, mouseY, px + 10, y, 96, 14), true, ArcaneGui.WARN);
            TextFit.centered(graphics, font,
                    Component.translatable("gui.dimensional_mana.weapon_workbench.discard"),
                    px + 10 + 48, y + 3, 90, ArcaneGui.WARN);
        }
        int doneX = px + POPUP_W - 66;
        ArcaneGui.button(graphics, doneX, y, 56, 14,
                inside(mouseX, mouseY, doneX, y, 56, 14), true, ArcaneGui.GOLD);
        TextFit.centered(graphics, font, Component.translatable("gui.dimensional_mana.weapon_workbench.lock_done"),
                doneX + 28, y + 3, 50, ArcaneGui.TEXT);
        TextFit.leftShrunk(graphics, font,
                Component.translatable("gui.dimensional_mana.weapon_workbench.lock_hint"),
                px + 10, y + 18, POPUP_W - 20, ArcaneGui.TEXT_FAINT);
    }

    // ------------------------------------------------------------------
    // 输入
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (lockPanelOpen) {
            handlePopupClick(mouseX, mouseY, button);
            return true;
        }
        if (button == 0) {
            if (lockButtonVisible() && lockButtonBox().contains(mouseX - leftPos, mouseY - topPos)) {
                openLockPanel();
                return true;
            }
            if (layout.button().contains(mouseX - leftPos, mouseY - topPos)) {
                if (canForge()) sendButton(WeaponWorkbenchMenu.BUTTON_FORGE);
                return true;
            }
            if (protectionActive() && inside(mouseX, mouseY, leftPos + GUARD_X, topPos + DISCARD_Y,
                    GUARD_W, 12)) {
                sendButton(WeaponWorkbenchMenu.BUTTON_DISCARD);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendButton(int id) {
        if (minecraft == null || minecraft.gameMode == null) return;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void handlePopupClick(double mouseX, double mouseY, int button) {
        int px = leftPos + (imageWidth - POPUP_W) / 2;
        int py = topPos + (imageHeight - POPUP_H) / 2;
        // 搜索框先接一手：点进去能落光标、拖能选字，跟聊天栏一样。
        if (searchBox != null) {
            setFocused(searchBox);
            searchBox.setFocused(true);
            if (searchBox.mouseClicked(mouseX, mouseY, button)) return;
        }
        // 关闭。
        if (inside(mouseX, mouseY, px + POPUP_W - 20, py + 5, 12, 12)) {
            closeLockPanel();
            return;
        }
        // 清空搜索。
        if (inside(mouseX, mouseY, px + SEARCH_X + SEARCH_W + 4, py + SEARCH_Y, 36, 14)) {
            clearSearch();
            return;
        }
        int footerY = py + POPUP_H - 30;
        if (protectionActive() && inside(mouseX, mouseY, px + 10, footerY, 96, 14)) {
            sendButton(WeaponWorkbenchMenu.BUTTON_DISCARD);
            return;
        }
        if (inside(mouseX, mouseY, px + POPUP_W - 66, footerY, 56, 14)) {
            closeLockPanel();
            return;
        }
        if (menu.equipment().isEmpty() || !EquipmentAffixSystem.hasAffix(menu.equipment())) return;
        // 两栏的勾选行。
        clickColumn(mouseX, mouseY, px + LEFT_COL, py + LIST_TOP, mainFiltered, mainScroll, true);
        clickColumn(mouseX, mouseY, px + RIGHT_COL, py + LIST_TOP, subFiltered, subScroll, false);
        if (button == 1) closeLockPanel();
    }

    private void clickColumn(double mouseX, double mouseY, int x, int y,
                             List<EquipmentAffixSystem.AffixOption> items, int scroll, boolean main) {
        for (int row = 0; row < ROWS; row++) {
            int index = scroll + row;
            if (index >= items.size()) return;
            if (inside(mouseX, mouseY, x, y + row * ROW_H, COL_W, ROW_H)) {
                toggleLock(items.get(index), main);
                return;
            }
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (lockPanelOpen) {
            if (searchBox != null) searchBox.mouseReleased(mouseX, mouseY, button);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (lockPanelOpen) {
            // 按住拖过搜索框是选字，和聊天栏一致。
            if (searchBox != null) searchBox.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!lockPanelOpen) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int px = leftPos + (imageWidth - POPUP_W) / 2;
        int py = topPos + (imageHeight - POPUP_H) / 2;
        int step = scrollY > 0 ? -1 : 1;
        if (inside(mouseX, mouseY, px + LEFT_COL, py + LIST_TOP, COL_W, ROWS * ROW_H)) {
            mainScroll = Math.max(0, Math.min(maxScroll(mainFiltered.size()), mainScroll + step));
        } else if (inside(mouseX, mouseY, px + RIGHT_COL, py + LIST_TOP, COL_W, ROWS * ROW_H)) {
            subScroll = Math.max(0, Math.min(maxScroll(subFiltered.size()), subScroll + step));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!lockPanelOpen) return super.keyPressed(keyCode, scanCode, modifiers);
        // 只有 Esc 收面板。回车 / 空格不再收：中文输入法就是拿它们「上屏」的，
        // 一旦在这里关掉面板，上屏的那几个字就没地方去了——这才是打不出中文的元凶。
        if (keyCode == InputConstants.KEY_ESCAPE) {
            closeLockPanel();
            return true;
        }
        // 退格、Delete、方向键、Home/End、Ctrl+A/C/V 全交给原版输入框。
        if (searchBox != null && searchBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        // 剩下的键一律吃掉：既不会触发背包快捷键，也不会误关界面。
        return true;
    }

    /**
     * 打字一律交给原版 {@link EditBox}。
     *
     * <p>中文是输入法「上屏」那一刻整段送进来的，这里不要自己拼字符串：交给输入框，
     * 聊天栏 / 铁砧改名怎么打字，这里就怎么打字（输入法、粘贴、选区、字数上限都由它处理）。</p>
     */
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!lockPanelOpen) return super.charTyped(codePoint, modifiers);
        if (searchBox != null) searchBox.charTyped(codePoint, modifiers);
        return true;
    }

    // ------------------------------------------------------------------
    // 渲染与提示
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // 材料或武备被换掉了就自动收面板，避免对着空白列表勾选。
        if (lockPanelOpen && (menu.equipment().isEmpty() || !ArmamentForge.rerollsAffixes(menu.material()))) {
            closeLockPanel();
        }
        if (lockPanelOpen) {
            hoveredSlot = null;
            renderPopup(graphics, mouseX, mouseY);
            renderPopupTooltip(graphics, mouseX, mouseY);
        } else {
            renderSlotTooltips(graphics, mouseX, mouseY);
            renderTooltip(graphics, mouseX, mouseY);
            renderLockButtonTooltip(graphics, mouseX, mouseY);
            renderGuardTooltip(graphics, mouseX, mouseY);
        }
    }

    /** 关界面时把输入框的焦点交回去，免得下次打开还挂着上一次的焦点。 */
    @Override
    public void removed() {
        setFocused(null);
        searchBox = null;
        lockPanelOpen = false;
        super.removed();
    }

    private void renderPopupTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int px = leftPos + (imageWidth - POPUP_W) / 2;
        int py = topPos + (imageHeight - POPUP_H) / 2;
        if (menu.equipment().isEmpty() || !EquipmentAffixSystem.hasAffix(menu.equipment())) return;
        Component tip = null;
        if (overColumn(mouseX, mouseY, px + LEFT_COL, py + LIST_TOP, mainFiltered, mainScroll)) {
            tip = hoveredRowTooltip(mouseX, mouseY, px + LEFT_COL, py + LIST_TOP, mainFiltered, mainScroll);
        } else if (overColumn(mouseX, mouseY, px + RIGHT_COL, py + LIST_TOP, subFiltered, subScroll)) {
            tip = hoveredRowTooltip(mouseX, mouseY, px + RIGHT_COL, py + LIST_TOP, subFiltered, subScroll);
        }
        if (tip == null) return;
        graphics.renderTooltip(font, tip, mouseX, mouseY);
    }

    private boolean overColumn(double mouseX, double mouseY, int x, int y,
                               List<EquipmentAffixSystem.AffixOption> items, int scroll) {
        if (!inside(mouseX, mouseY, x, y, COL_W, ROWS * ROW_H)) return false;
        int row = (int) ((mouseY - y) / ROW_H);
        return scroll + row < items.size();
    }

    private Component hoveredRowTooltip(double mouseX, double mouseY, int x, int y,
                                        List<EquipmentAffixSystem.AffixOption> items, int scroll) {
        int row = (int) ((mouseY - y) / ROW_H);
        int index = scroll + row;
        if (index < 0 || index >= items.size()) return null;
        EquipmentAffixSystem.AffixOption option = items.get(index);
        if (option.selectable()) {
            return Component.translatable("gui.dimensional_mana.weapon_workbench.lock_tooltip");
        }
        return Component.translatable("gui.dimensional_mana.weapon_workbench.lock_tooltip_blocked",
                Component.translatable(option.schoolOk()
                        ? "gui.dimensional_mana.weapon_workbench.lock_reason_item"
                        : "gui.dimensional_mana.weapon_workbench.lock_reason_school"));
    }

    private void renderLockButtonTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!lockButtonVisible()) return;
        ArcaneLayout.Box b = lockButtonBox();
        if (!inside(mouseX, mouseY, leftPos + b.x(), topPos + b.y(), b.width(), b.height())) return;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.dimensional_mana.weapon_workbench.lock_button")
                .withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("gui.dimensional_mana.weapon_workbench.lock_button_tip")
                .withStyle(ChatFormatting.GRAY));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private void renderGuardTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!protectionActive()) return;
        if (!inside(mouseX, mouseY, leftPos + GUARD_X, topPos + GUARD_Y, GUARD_W, GUARD_H)) return;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.dimensional_mana.weapon_workbench.guard_hit")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        lines.add(Component.translatable("gui.dimensional_mana.weapon_workbench.guard_hit_tip")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.dimensional_mana.weapon_workbench.discard_tip")
                .withStyle(ChatFormatting.DARK_GRAY));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
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
        tooltip.add(Component.translatable("tooltip.dimensional_mana.grade_level_cap",
                EquipmentAffixSystem.mainMaxLevel(stack)).withStyle(ChatFormatting.DARK_AQUA));
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
        // 防手滑：洗出全部目标词条之后，按钮先灰着，等玩家确认。
        if (protectionActive()) return false;
        // 锻造模板：只给还没有词条的白板装备赋武备。
        if (ArmamentForge.isSmithingTemplate(material)) return !EquipmentAffixSystem.hasAffix(stack);
        if (!EquipmentAffixSystem.hasAffix(stack)) return false;
        // 武备强化石：武备到本评级上限 + 还有没满的副词条才能敲。
        if (ArmamentForge.isEnhanceStone(material)) {
            return EquipmentAffixSystem.mainLevel(stack) >= ArmamentForge.maxLevel(stack)
                    && EquipmentAffixSystem.hasGrowableSub(stack);
        }
        if (ArmamentForge.isRerollMaterial(material)) return true;
        if (ArmamentForge.isReforgeTalisman(material)) return EquipmentAffixSystem.hasAffix(stack);
        int level = EquipmentAffixSystem.mainLevel(stack);
        return level < ArmamentForge.maxLevel(stack)
                && ArmamentForge.crystalValue(material) >= ArmamentForge.upgradeCost(stack, level + 1);
    }
}
