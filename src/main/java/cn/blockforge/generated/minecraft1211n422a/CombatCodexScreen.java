package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 战斗手册界面：三个书签页，框架照搬《万法知识之书》的自绘风格。
 *
 * <ul>
 *   <li>基础战斗信息：实时读取玩家当前属性，展示物理面板。</li>
 *   <li>斗气与技能加点：斗气等级、经验、技能点与六项斗气技能，点按钮直接加点。</li>
 *   <li>能力：三项斗气技能，显示解锁状态、消耗与说明。</li>
 * </ul>
 *
 * <p>界面只读 {@link ClientQiState} 与玩家自身属性，所有加点请求都发回服务端复核。</p>
 */
public final class CombatCodexScreen extends Screen {
    private enum Page { STATUS, SKILLS, ABILITIES }

    private static final int PANEL_WIDTH = 424;
    private static final int PANEL_HEIGHT = 246;
    private static final int TAB_WIDTH = 126;
    private static final int TAB_GAP = 8;
    /** 面板配色：暗红皮革 + 金色描边，和蓝紫色的万法知识之书成对。 */
    private static final int BACKGROUND_TOP = 0xF0221113;
    private static final int BACKGROUND_BOTTOM = 0xF0180A0C;
    private static final int OUTLINE = 0xFFC9A25C;
    private static final int DIVIDER = 0xFF8E5A3A;
    private static final int TITLE_COLOR = 0xFFFFE3B0;
    private static final int LABEL_COLOR = 0xFFD8C4B0;
    private static final int VALUE_COLOR = 0xFFFFFFFF;
    private static final int HINT_COLOR = 0xFFB99A80;

    private Page page = Page.STATUS;
    private int left;
    private int top;
    private int lastFingerprint = Integer.MIN_VALUE;
    private final List<HoverRow> hoverRows = new ArrayList<>();

    public CombatCodexScreen() {
        super(Component.translatable("gui.dimensional_mana.combat_codex.title"));
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        clearWidgets();
        addTab(Page.STATUS, "gui.dimensional_mana.combat_codex.tab.status", 0);
        addTab(Page.SKILLS, "gui.dimensional_mana.combat_codex.tab.skills", 1);
        addTab(Page.ABILITIES, "gui.dimensional_mana.combat_codex.tab.abilities", 2);
        if (page == Page.SKILLS) addSkillButtons();
        lastFingerprint = ClientQiState.fingerprint();
    }

    private void addTab(Page target, String translation, int index) {
        Button button = Button.builder(Component.translatable(translation), clicked -> {
            page = target;
            rebuildWidgets();
        }).bounds(left + 14 + index * (TAB_WIDTH + TAB_GAP), top + 10, TAB_WIDTH, 20).build();
        button.active = page != target;
        addRenderableWidget(button);
    }

    /** 六项斗气技能各一个升级按钮；点数不够或满级时按钮变灰。 */
    private void addSkillButtons() {
        QiProgression.Skill[] skills = QiProgression.Skill.values();
        for (int i = 0; i < skills.length; i++) {
            QiProgression.Skill skill = skills[i];
            int x = left + 20 + (i % 2) * 200;
            int y = top + 78 + (i / 2) * 50;
            int rank = ClientQiState.skill(skill);
            int cost = QiProgression.skillCostForNextRank(rank);
            Component label = rank >= QiProgression.MAX_SKILL_LEVEL
                    ? Component.translatable("gui.dimensional_mana.qi_skill.max")
                    : Component.translatable("gui.dimensional_mana.qi_skill.upgrade_button", cost);
            Button button = Button.builder(label, clicked ->
                            PacketDistributor.sendToServer(new QiActionPayload("upgrade", skill.id(), 0)))
                    .bounds(x + 150, y + 16, 44, 18).build();
            button.active = rank < QiProgression.MAX_SKILL_LEVEL && ClientQiState.skillPoints() >= cost;
            button.setTooltip(Tooltip.create(Component.translatable(
                    "gui.dimensional_mana.qi_skill.upgrade_tooltip",
                    Component.translatable(skill.translation()), cost)));
            addRenderableWidget(button);
        }
    }

    @Override
    public void tick() {
        if (page == Page.SKILLS && ClientQiState.fingerprint() != lastFingerprint) rebuildWidgets();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 不调用原版模糊背景：自绘文字才不会被糊掉。
        graphics.fill(0, 0, width, height, 0xAA100508);
        graphics.fillGradient(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, BACKGROUND_TOP, BACKGROUND_BOTTOM);
        graphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, OUTLINE);
        graphics.fill(left + 10, top + 36, left + PANEL_WIDTH - 10, top + 38, DIVIDER);
        graphics.drawCenteredString(font, title, width / 2, top - 13, TITLE_COLOR);
        hoverRows.clear();
        switch (page) {
            case STATUS -> renderStatus(graphics);
            case SKILLS -> renderSkills(graphics);
            case ABILITIES -> renderAbilities(graphics);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        for (HoverRow row : hoverRows) {
            if (mouseX >= row.x && mouseX < row.x + row.width && mouseY >= row.y && mouseY < row.y + row.height) {
                graphics.renderComponentTooltip(font, row.tooltip, mouseX, mouseY);
                break;
            }
        }
    }

    // ---------------------------------------------------------------- 书签 1：物理面板

    private void renderStatus(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        var player = minecraft.player;
        int leftX = left + 20;
        int rightX = left + 220;
        int y = top + 48;

        graphics.drawString(font, Component.translatable("gui.dimensional_mana.combat_codex.section.base"),
                leftX, y, 0xFFFFC46B, false);
        y += 13;
        drawRow(graphics, leftX, y, "gui.dimensional_mana.combat_codex.health",
                format(player.getHealth()) + " / " + format(player.getMaxHealth()),
                "gui.dimensional_mana.combat_codex.health.source");
        drawAttributeRow(graphics, leftX, y += 15, "gui.dimensional_mana.combat_codex.armor", Attributes.ARMOR);
        drawAttributeRow(graphics, leftX, y += 15, "gui.dimensional_mana.combat_codex.toughness", Attributes.ARMOR_TOUGHNESS);
        drawRow(graphics, leftX, y += 15, "gui.dimensional_mana.combat_codex.battle_qi",
                ClientManaState.battleQi() + " / " + ClientManaState.maxBattleQi(),
                "gui.dimensional_mana.combat_codex.battle_qi.source");
        // 斗气进度条：位置跟着最后一行的文字走。
        int barY = y + 10;
        int barWidth = 168;
        int filled = ClientManaState.maxBattleQi() <= 0 ? 0
                : (int)Math.round(Math.min(1.0, ClientManaState.battleQi()
                / (double)Math.max(1, ClientManaState.maxBattleQi())) * barWidth);
        graphics.fill(leftX, barY, leftX + barWidth, barY + 5, 0xFF3A2318);
        graphics.fill(leftX, barY, leftX + Math.max(0, Math.min(barWidth, filled)), barY + 5, 0xFFFFC84A);
        graphics.renderOutline(leftX, barY, barWidth, 5, 0xFFB88A45);
        drawRow(graphics, leftX, y += 19, "gui.dimensional_mana.combat_codex.qi_regen",
                String.format(java.util.Locale.ROOT, "%.1f", qiRegenPerSecond()) + " /s",
                "gui.dimensional_mana.combat_codex.qi_regen.source");
        // 「回气」只在凝聚斗气时生效：每凝聚 1 点额外补多少。
        drawRow(graphics, leftX, y += 15, "gui.dimensional_mana.combat_codex.qi_condense",
                String.format(java.util.Locale.ROOT, "+%.1f", qiCondenseBonusPerPoint()),
                "gui.dimensional_mana.combat_codex.qi_condense.source");

        y = top + 48;
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.combat_codex.section.melee"),
                rightX, y, 0xFFFFC46B, false);
        y += 13;
        drawAttributeRow(graphics, rightX, y, "gui.dimensional_mana.combat_codex.melee_damage", Attributes.ATTACK_DAMAGE);
        double meleeBonus = BattleQiGloveItem.worn(player) ? (BattleQiGloveItem.MELEE_DAMAGE_MULTIPLIER - 1.0) : 0.0;
        drawRow(graphics, rightX, y += 15, "gui.dimensional_mana.combat_codex.melee_bonus",
                String.format(java.util.Locale.ROOT, "+%.0f%%", meleeBonus * 100.0),
                "gui.dimensional_mana.combat_codex.melee_bonus.source");
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        double speed = attackSpeed == null ? 0.0 : attackSpeed.getValue();
        drawRow(graphics, rightX, y += 15, "gui.dimensional_mana.combat_codex.attack_speed",
                String.format(java.util.Locale.ROOT, "%.2f", speed), "gui.dimensional_mana.combat_codex.attack_speed.source");
        double critChance = minecraft.level == null ? 0.0 : CriticalSystem.chance(minecraft.level.getDifficulty());
        double critDamage = minecraft.level == null ? 1.0 : CriticalSystem.multiplier(minecraft.level.getDifficulty());
        drawRow(graphics, rightX, y += 15, "gui.dimensional_mana.combat_codex.crit_chance",
                String.format(java.util.Locale.ROOT, "%.0f%%", critChance * 100.0),
                "gui.dimensional_mana.combat_codex.crit_chance.source");
        drawRow(graphics, rightX, y += 15, "gui.dimensional_mana.combat_codex.crit_damage",
                String.format(java.util.Locale.ROOT, "%.0f%%", critDamage * 100.0),
                "gui.dimensional_mana.combat_codex.crit_damage.source");
        drawRow(graphics, rightX, y += 15, "gui.dimensional_mana.combat_codex.execute",
                String.format(java.util.Locale.ROOT, "%d%%", ClientQiState.skill(QiProgression.Skill.EXECUTE)),
                "gui.dimensional_mana.combat_codex.execute.source");

        y = top + PANEL_HEIGHT - 74;
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.combat_codex.section.defense"),
                leftX, y, 0xFFFFC46B, false);
        y += 13;
        drawRow(graphics, leftX, y, "gui.dimensional_mana.combat_codex.perfect_window",
                BattleQiParry.PERFECT_WINDOW_TICKS + " tick",
                "gui.dimensional_mana.combat_codex.perfect_window.source");
        double parry = (1.0 - BattleQiParry.STANCE_DAMAGE_MULTIPLIER) * 100.0
                + ClientQiState.skill(QiProgression.Skill.IRON_WALL) * 2.0;
        drawRow(graphics, leftX, y += 15, "gui.dimensional_mana.combat_codex.parry_reduction",
                String.format(java.util.Locale.ROOT, "%.0f%%", parry),
                "gui.dimensional_mana.combat_codex.parry_reduction.source");
        double vulnerability = (BattleQiParry.VULNERABILITY_MULTIPLIER - 1.0) * 100.0
                + ClientQiState.skill(QiProgression.Skill.MIKIRI) * 5.0;
        drawRow(graphics, leftX, y += 15, "gui.dimensional_mana.combat_codex.vulnerability",
                String.format(java.util.Locale.ROOT, "+%.0f%%", vulnerability),
                "gui.dimensional_mana.combat_codex.vulnerability.source");
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.combat_codex.status_hint"),
                rightX, top + PANEL_HEIGHT - 26, HINT_COLOR, false);
    }

    private double qiRegenPerSecond() {
        // 自然恢复现在只有剑士护符提供；「回气」不再参与，单独显示在下面凝聚一栏。
        return ClientQiState.wearing(SwordsmanCharmItem.class) ? SwordsmanCharmItem.REGEN_PER_SECOND : 0.0;
    }

    /** 「回气」：凝聚斗气时每凝聚 1 点额外恢复多少。 */
    private double qiCondenseBonusPerPoint() {
        return ClientQiState.skill(QiProgression.Skill.RECOVERY) * 0.1;
    }

    // ---------------------------------------------------------------- 书签 2：斗气与加点

    private void renderSkills(GuiGraphics graphics) {
        int x = left + 20;
        int y = top + 48;
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.qi_level",
                ClientQiState.level(), QiProgression.MAX_QI_LEVEL), x, y, 0xFFFFD86B, false);
        String xp = ClientQiState.level() >= QiProgression.MAX_QI_LEVEL
                ? Component.translatable("gui.dimensional_mana.magic_xp.max").getString()
                : ClientQiState.experience() + " / " + ClientQiState.requiredExperience();
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.qi_xp", xp), x + 150, y, 0xFFE8C08A, false);
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.qi_skill_points", ClientQiState.skillPoints()),
                x + 300, y, 0xFF84FFB2, false);
        int required = ClientQiState.requiredExperience();
        int progressWidth = ClientQiState.level() >= QiProgression.MAX_QI_LEVEL || required <= 0 ? 384
                : (int)Math.min(384, ClientQiState.experience() * 384L / required);
        graphics.fill(x, y + 12, x + 384, y + 18, 0xFF38201B);
        graphics.fill(x, y + 12, x + Math.max(0, progressWidth), y + 18, 0xFFC9622F);
        graphics.renderOutline(x, y + 12, 384, 6, 0xFFD9A15C);
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.qi_xp_rule"),
                x, y + 22, HINT_COLOR, false);

        QiProgression.Skill[] skills = QiProgression.Skill.values();
        for (int i = 0; i < skills.length; i++) {
            QiProgression.Skill skill = skills[i];
            int cardX = x + (i % 2) * 200;
            int cardY = top + 78 + (i / 2) * 50;
            int rank = ClientQiState.skill(skill);
            graphics.fill(cardX, cardY, cardX + 194, cardY + 40, 0xA0301A18);
            graphics.renderOutline(cardX, cardY, 194, 40, rank >= QiProgression.MAX_SKILL_LEVEL ? 0xFFFFD86B : 0xFF8E5A3A);
            graphics.drawString(font, Component.translatable(skill.translation()), cardX + 6, cardY + 4, 0xFFFFF0DC, false);
            graphics.drawString(font, Component.literal(rank + " / " + QiProgression.MAX_SKILL_LEVEL),
                    cardX + 6, cardY + 15, rank >= QiProgression.MAX_SKILL_LEVEL ? 0xFFFFD86B : 0xFFC2A98F, false);
            List<Component> tooltip = List.of(
                    Component.translatable(skill.translation()).withStyle(ChatFormatting.GOLD),
                    Component.translatable("gui.dimensional_mana.qi_skill.effect." + skill.id(), rank)
                            .withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.dimensional_mana.qi_skill.cost_rule").withStyle(ChatFormatting.DARK_GRAY));
            hoverRows.add(new HoverRow(cardX, cardY, 150, 40, tooltip));
        }
    }

    // ---------------------------------------------------------------- 书签 3：能力

    private void renderAbilities(GuiGraphics graphics) {
        int x = left + 20;
        int y = top + 48;
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.qi_ability.title"), x, y, 0xFFFFC46B, false);
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.qi_ability.slot_hint",
                Component.keybind("key.dimensional_mana.qi_cast"), ClientQiState.selectedSlot() + 1),
                x, y + 12, HINT_COLOR, false);
        QiProgression.Ability[] abilities = QiProgression.Ability.values();
        for (int i = 0; i < abilities.length; i++) {
            QiProgression.Ability ability = abilities[i];
            int cardY = y + 28 + i * 56;
            boolean learned = ClientQiState.learned(ability);
            int slot = slotOf(ability);
            graphics.fill(x, cardY, x + 384, cardY + 50, learned ? 0xA03A1B16 : 0x80201616);
            graphics.renderOutline(x, cardY, 384, 50, learned ? 0xFFFFC46B : 0xFF5C453A);
            graphics.drawString(font, Component.translatable(ability.translation()), x + 6, cardY + 4,
                    learned ? 0xFFFFF0DC : 0xFF8A7568, false);
            graphics.drawString(font, Component.translatable(
                            learned ? "gui.dimensional_mana.qi_ability.learned" : "gui.dimensional_mana.qi_ability.locked"),
                    x + 150, cardY + 4, learned ? 0xFF84FFB2 : 0xFFB07A6A, false);
            if (learned) {
                graphics.drawString(font, Component.translatable("gui.dimensional_mana.qi_ability.equipped",
                                slot >= 0 ? slot + 1 : 0), x + 260, cardY + 4, 0xFFE8C08A, false);
            }
            graphics.drawString(font, Component.translatable("gui.dimensional_mana.qi_manual." + ability.id() + ".1"),
                    x + 6, cardY + 17, 0xFFC2A98F, false);
            graphics.drawString(font, Component.translatable("gui.dimensional_mana.qi_manual.cost", ability.cost()),
                    x + 6, cardY + 29, 0xFF7FDFFF, false);
            graphics.drawString(font, Component.translatable("gui.dimensional_mana.qi_manual." + ability.id() + ".2"),
                    x + 6, cardY + 40, 0xFFB99A80, false);
            hoverRows.add(new HoverRow(x, cardY, 384, 50, List.of(
                    Component.translatable(ability.translation()).withStyle(ChatFormatting.GOLD),
                    Component.translatable("gui.dimensional_mana.qi_manual.cost", ability.cost())
                            .withStyle(ChatFormatting.AQUA),
                    Component.translatable("gui.dimensional_mana.qi_manual." + ability.id() + ".1")
                            .withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.dimensional_mana.qi_manual." + ability.id() + ".2")
                            .withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.dimensional_mana.qi_ability.obtain").withStyle(ChatFormatting.DARK_GRAY))));
        }
    }

    private static int slotOf(QiProgression.Ability ability) {
        for (int i = 0; i < QiProgression.SLOT_COUNT; i++) {
            if (ability.id().equals(ClientQiState.slotId(i))) return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------- 绘制工具

    private void drawAttributeRow(GuiGraphics graphics, int x, int y, String label,
                                  net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        AttributeInstance instance = minecraft.player == null ? null : minecraft.player.getAttribute(attribute);
        double value = instance == null ? 0.0 : instance.getValue();
        drawRow(graphics, x, y, label, format(value), label);
    }

    private void drawRow(GuiGraphics graphics, int x, int y, String label, String value, String source) {
        graphics.drawString(font, Component.translatable(label), x, y, LABEL_COLOR, false);
        graphics.drawString(font, value, x + 112, y, VALUE_COLOR, false);
        hoverRows.add(new HoverRow(x, y - 2, 200, 13, List.of(
                Component.translatable(label).withStyle(ChatFormatting.GOLD),
                Component.translatable(source).withStyle(ChatFormatting.GRAY))));
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.format("%.0f", value) : String.format("%.2f", value);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record HoverRow(int x, int y, int width, int height, List<Component> tooltip) {}
}
