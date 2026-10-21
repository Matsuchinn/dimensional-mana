package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public final class ArcaneCodexScreen extends Screen {
    private enum Page { SKILLS, SPELLS, STATUS }

    private static final int PANEL_WIDTH = 390;
    private static final int PANEL_HEIGHT = 238;
    private static final int SPELL_VIEW_OFFSET_X = 151;
    private static final int SPELL_VIEW_OFFSET_Y = 64;
    private static final int SPELL_VIEW_WIDTH = 221;
    private static final int SPELL_VIEW_HEIGHT = 92;
    private static final int SPELL_CARD_STEP_X = 101;
    private static final int SPELL_CARD_STEP_Y = 50;
    private static final int SPELL_CARD_WIDTH = 88;
    private static final int SPELL_CARD_HEIGHT = 40;
    private Page page = Page.SKILLS;
    private int left;
    private int top;
    private int lastFingerprint = Integer.MIN_VALUE;
    private final List<HoverRow> hoverRows = new ArrayList<>();
    private SpellDefinition selectedSpell;
    private String selectedMedium = "arcane";
    private double spellScrollX;
    private double spellScrollY;
    private boolean draggingSpellPage;
    private double spellDragStartX;
    private double spellDragStartY;
    private double spellDragStartScrollX;
    private double spellDragStartScrollY;

    public ArcaneCodexScreen() {
        super(Component.translatable("gui.dimensional_mana.codex.title"));
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        clearWidgets();
        addTab(Page.SKILLS, "gui.dimensional_mana.codex.skills", left + 18);
        addTab(Page.SPELLS, "gui.dimensional_mana.codex.spells", left + 137);
        addTab(Page.STATUS, "gui.dimensional_mana.codex.status", left + 256);
        if (page == Page.SKILLS) addSkillButtons();
        if (page == Page.SPELLS && selectedSpell != null) addSpellActionButton();
        lastFingerprint = fingerprint();
    }

    private void addTab(Page target, String translation, int x) {
        Button button = Button.builder(Component.translatable(translation), clicked -> {
            page = target;
            rebuildWidgets();
        }).bounds(x, top + 12, 116, 20).build();
        button.active = page != target;
        addRenderableWidget(button);
    }

    private void addSpellActionButton() {
        SpellDefinition spell = selectedSpell;
        boolean learned = ClientSpellState.learned(spell);
        boolean learning = ClientSpellState.learning() == spell;
        boolean accessible = ClientElementTalentState.canAccess(spell);
        String key = learned ? "gui.dimensional_mana.spell.assign" : learning
                ? "gui.dimensional_mana.spell.learning" : "gui.dimensional_mana.spell.learn";
        Button button = Button.builder(Component.translatable(key), clicked -> {
            if (learned || learning) {
                PacketDistributor.sendToServer(new SpellActionPayload("assign", spell.id(), -1));
            } else {
                PacketDistributor.sendToServer(new SpellActionPayload("learn", spell.id(), -1));
            }
        }).bounds(left + PANEL_WIDTH - 151, top + PANEL_HEIGHT - 33, 128, 20).build();
        button.active = accessible && (learned || !learning);
        addRenderableWidget(button);
    }

    private void addSkillButtons() {
        MagicProgression.Skill[] skills = MagicProgression.Skill.values();
        for (int i = 0; i < skills.length; i++) {
            MagicProgression.Skill skill = skills[i];
            int column = i % 2;
            int row = i / 2;
            int x = left + 22 + column * 190;
            int y = top + 85 + row * 32;
            int rank = ClientProgressState.skill(skill);
            int cost = MagicProgression.skillCostForNextRank(rank);
            String suffix = rank >= MagicProgression.MAX_SKILL_LEVEL ? "MAX" : "+  " + cost + "SP";
            Button button = Button.builder(Component.literal(suffix), clicked ->
                    PacketDistributor.sendToServer(new UpgradeSkillPayload(skill.id())))
                    .bounds(x + 128, y, 48, 18).build();
            button.active = rank < MagicProgression.MAX_SKILL_LEVEL && ClientProgressState.skillPoints() >= cost;
            button.setTooltip(Tooltip.create(Component.translatable("gui.dimensional_mana.skill.upgrade_tooltip",
                    Component.translatable(skill.translation()), cost)));
            addRenderableWidget(button);
        }
    }

    @Override
    public void tick() {
        if ((page == Page.SKILLS || page == Page.SPELLS) && fingerprint() != lastFingerprint) rebuildWidgets();
    }

    private int fingerprint() {
        int value = ClientProgressState.skillPoints();
        for (MagicProgression.Skill skill : MagicProgression.Skill.values()) value = value * 31 + ClientProgressState.skill(skill);
        for (SpellDefinition spell : SpellDefinition.values()) value = value * 31 + ClientSpellState.progress(spell);
        value = value * 31 + ClientSpellState.selectedSlot();
        value = value * 31 + (ClientSpellState.learning() == null ? 0 : ClientSpellState.learning().ordinal() + 1);
        value = value * 31 + ClientElementTalentState.fingerprint();
        return value;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 不调用 Screen.renderBackground：原版菜单模糊层会让自绘文字和图标失焦。
        graphics.fill(0, 0, width, height, 0xA0080510);
        graphics.fillGradient(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xF0191330, 0xF0081728);
        graphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFFB98CFF);
        graphics.fill(left + 10, top + 38, left + PANEL_WIDTH - 10, top + 40, 0xFF7253A6);
        graphics.drawCenteredString(font, title, width / 2, top - 13, 0xFFEAD9FF);
        hoverRows.clear();
        switch (page) {
            case SKILLS -> renderSkills(graphics);
            case SPELLS -> renderSpells(graphics);
            case STATUS -> renderStatus(graphics);
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

    private void renderSkills(GuiGraphics graphics) {
        int level = ClientProgressState.magicLevel();
        int required = ClientProgressState.requiredExperience();
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.magic_level", level, MagicProgression.MAX_MAGIC_LEVEL),
                left + 22, top + 49, 0xFFFFD86B, false);
        String xp = level >= MagicProgression.MAX_MAGIC_LEVEL
                ? Component.translatable("gui.dimensional_mana.magic_xp.max").getString()
                : ClientProgressState.magicExperience() + " / " + required;
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.magic_xp", xp), left + 145, top + 49, 0xFF9AD7FF, false);
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.skill_points", ClientProgressState.skillPoints()),
                left + 300, top + 49, 0xFF84FFB2, false);
        int progressWidth = level >= MagicProgression.MAX_MAGIC_LEVEL || required <= 0 ? 242
                : (int)Math.min(242, ClientProgressState.magicExperience() * 242L / required);
        graphics.fill(left + 22, top + 66, left + 264, top + 72, 0xFF241B39);
        graphics.fill(left + 22, top + 66, left + 22 + progressWidth, top + 72, 0xFF7B56E7);
        graphics.renderOutline(left + 22, top + 66, 242, 6, 0xFFB9A2FF);

        MagicProgression.Skill[] skills = MagicProgression.Skill.values();
        for (int i = 0; i < skills.length; i++) {
            MagicProgression.Skill skill = skills[i];
            int x = left + 22 + (i % 2) * 190;
            int y = top + 85 + (i / 2) * 32;
            int rank = ClientProgressState.skill(skill);
            graphics.drawString(font, Component.translatable(skill.translation()), x, y + 1, 0xFFF2EAFF, false);
            graphics.drawString(font, Component.literal(rank + " / " + MagicProgression.MAX_SKILL_LEVEL), x, y + 11,
                    rank >= MagicProgression.MAX_SKILL_LEVEL ? 0xFFFFD86B : 0xFFAEB9CE, false);
            List<Component> tooltip = List.of(
                    Component.translatable(skill.translation()).withStyle(ChatFormatting.LIGHT_PURPLE),
                    Component.translatable("gui.dimensional_mana.skill.effect." + skill.id(), rank).withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.dimensional_mana.skill.cost_rule").withStyle(ChatFormatting.DARK_GRAY));
            hoverRows.add(new HoverRow(x, y, 124, 25, tooltip));
        }
    }

    private void renderSpells(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.spell.element_talents"),
                left + 18, top + 48, 0xFFFFD86B, false);
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.spell.right_click_hint"),
                left + 150, top + 48, 0xFFAA98BE, false);
        String[] media = {"arcane", "fire", "water", "ice", "lightning", "wind", "earth", "nature", "metal", "light", "poison", "sound"};
        for (int i = 0; i < media.length; i++) {
            String medium = media[i];
            int x = left + 18 + (i % 3) * 42;
            int y = top + 64 + (i / 3) * 40;
            ElementType element = ElementType.fromMedium(medium);
            boolean unlocked = element == null || ClientElementTalentState.unlocked(element);
            boolean selected = medium.equals(selectedMedium);
            graphics.fill(x, y, x + 36, y + 34, selected ? 0xC0482B68 : 0x80120F20);
            graphics.renderOutline(x, y, 36, 34, selected ? 0xFFFFD26F : unlocked ? 0xFF8F6DB7 : 0xFF4D4854);
            ItemStack icon = new ItemStack(element == null ? net.minecraft.world.item.Items.AMETHYST_SHARD : element.icon());
            if (!unlocked) graphics.setColor(0.25F, 0.25F, 0.25F, 1.0F);
            graphics.renderItem(icon, x + 10, y + 3);
            graphics.setColor(1, 1, 1, 1);
            graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.medium." + medium), x + 18, y + 21,
                    unlocked ? 0xFFF2EAFF : 0xFF77717D);
        }

        List<SpellDefinition> spells = spellsForMedium(selectedMedium);
        spellScrollX = Math.min(spellScrollX, maxSpellScrollX(spells));
        spellScrollY = Math.min(spellScrollY, maxSpellScrollY(spells));
        int viewX = left + SPELL_VIEW_OFFSET_X;
        int viewY = top + SPELL_VIEW_OFFSET_Y;
        graphics.fill(viewX, viewY, viewX + SPELL_VIEW_WIDTH, viewY + SPELL_VIEW_HEIGHT, 0x94100B1C);
        graphics.enableScissor(viewX, viewY, viewX + SPELL_VIEW_WIDTH, viewY + SPELL_VIEW_HEIGHT);
        for (SpellDefinition spell : spells) {
            int row = tierRow(spell);
            int column = tierColumn(spells, spell);
            int x = viewX + 7 + column * SPELL_CARD_STEP_X - (int)Math.round(spellScrollX);
            int y = viewY + 3 + row * SPELL_CARD_STEP_Y - (int)Math.round(spellScrollY);
            boolean active = selectedSpell == spell;
            boolean accessible = ClientElementTalentState.canAccess(spell);
            int progress = ClientSpellState.progress(spell);
            graphics.fill(x, y, x + SPELL_CARD_WIDTH, y + SPELL_CARD_HEIGHT,
                    active ? 0xD03A2457 : 0xB0161024);
            graphics.fill(x, y, x + 3, y + SPELL_CARD_HEIGHT, spell.isIntermediate() ? 0xFFD993FF
                    : spell.isAdvanced() ? 0xFFFFC861 : 0xFF7CDFFF);
            graphics.renderOutline(x, y, SPELL_CARD_WIDTH, SPELL_CARD_HEIGHT,
                    active ? 0xFFFFD26F : accessible ? 0xFF8F6DB7 : 0xFF4D4854);
            if (!accessible || progress <= 0) graphics.setColor(0.28F, 0.28F, 0.28F, 1.0F);
            drawSpellIcon(graphics, spell, x + 7, y + 11, 1.0F);
            graphics.setColor(1, 1, 1, 1);
            graphics.drawString(font, Component.translatable(spell.translationKey()), x + 27, y + 4,
                    accessible ? 0xFFFFFFFF : 0xFF88818A, false);
            String stateKey = !accessible ? "gui.dimensional_mana.spell.state.element_locked"
                    : progress >= 3 ? "gui.dimensional_mana.spell.state.available"
                    : "gui.dimensional_mana.spell.state.fragment_recipe";
            graphics.drawString(font, Component.translatable(stateKey), x + 27, y + 16,
                    !accessible ? 0xFFFF7777 : progress >= 3 ? 0xFF79F1B2 : 0xFFE1B9FF, false);
            graphics.drawString(font, Component.literal(progress + " / " + SpellData.REQUIRED_SUCCESSES),
                    x + 27, y + 28, 0xFFBEB4C6, false);
            if (!accessible) graphics.renderItem(new ItemStack(net.minecraft.world.item.Items.TRIPWIRE_HOOK), x + 68, y + 19);
            int hoverLeft = Math.max(x, viewX), hoverTop = Math.max(y, viewY);
            int hoverRight = Math.min(x + SPELL_CARD_WIDTH, viewX + SPELL_VIEW_WIDTH);
            int hoverBottom = Math.min(y + SPELL_CARD_HEIGHT, viewY + SPELL_VIEW_HEIGHT);
            if (hoverRight > hoverLeft && hoverBottom > hoverTop) hoverRows.add(new HoverRow(
                    hoverLeft, hoverTop, hoverRight - hoverLeft, hoverBottom - hoverTop, List.of(
                    Component.translatable(spell.translationKey()).withStyle(ChatFormatting.LIGHT_PURPLE),
                    Component.translatable("gui.dimensional_mana.spell.tier." + spell.grade()).withStyle(ChatFormatting.GOLD),
                    Component.translatable("gui.dimensional_mana.spell.school_medium",
                            Component.translatable("gui.dimensional_mana.school." + spell.school()),
                            Component.translatable("gui.dimensional_mana.medium." + spell.medium())).withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.dimensional_mana.spell.cast_cost",
                            String.format("%.1f", spell.castTicks() / 20.0), (int)spell.manaCost()).withStyle(ChatFormatting.AQUA),
                    Component.translatable(spell.translationKey() + ".description").withStyle(ChatFormatting.WHITE),
                    Component.translatable(spell.translationKey() + ".stats").withStyle(ChatFormatting.AQUA))));
        }
        graphics.disableScissor();
        graphics.renderOutline(viewX, viewY, SPELL_VIEW_WIDTH, SPELL_VIEW_HEIGHT, 0xFF8F6DB7);
        drawSpellScrollbars(graphics, spells, viewX, viewY);
        if (selectedSpell != null) {
            int x = left + 160;
            int y = top + 165;
            drawSpellSummary(graphics, Component.translatable(selectedSpell.translationKey() + ".description"), x, y, 0xFFEDE4F3);
            drawSpellSummary(graphics, Component.translatable(selectedSpell.translationKey() + ".stats"), x, y + 26, 0xFF9EDAFF);
            int progress = ClientSpellState.progress(selectedSpell);
            String state = !ClientElementTalentState.canAccess(selectedSpell) ? "gui.dimensional_mana.spell.state.element_locked"
                    : progress >= SpellData.REQUIRED_SUCCESSES ? "gui.dimensional_mana.spell.state.learned"
                    : ClientSpellState.learning() == selectedSpell ? "gui.dimensional_mana.spell.state.learning"
                    : "gui.dimensional_mana.spell.state.unlearned";
            Component stateText = state.endsWith("learning")
                    ? Component.translatable(state, progress, SpellData.REQUIRED_SUCCESSES, Component.keybind("key.dimensional_mana.cast_spell"))
                    : state.endsWith("element_locked") ? Component.translatable(state)
                    : Component.translatable(state, progress, SpellData.REQUIRED_SUCCESSES);
            graphics.drawString(font, stateText, x, y + 55, progress >= 3 ? 0xFF79F1B2 : 0xFFFFD279, false);
            String elementFragmentId = selectedSpell.elementFragmentId();
            if (elementFragmentId != null && ClientElementTalentState.canAccess(selectedSpell)
                    && progress < SpellData.REQUIRED_SUCCESSES) {
                ItemStack elementFragment = new ItemStack(GeneratedMod.MOD_ITEMS.get(elementFragmentId).get());
                ItemStack schoolFragment = new ItemStack(GeneratedMod.MOD_ITEMS.get(
                        selectedSpell.schoolFragmentId(selectedSpell.grade())).get());
                graphics.renderItem(elementFragment, x, y + 69);
                graphics.renderItem(schoolFragment, x + 19, y + 69);
                graphics.drawString(font, Component.translatable("gui.dimensional_mana.spell.unlock_material_pair"),
                        x + 40, y + 73, 0xFFE1B9FF, false);
            }
        }
    }

    private void drawSpellSummary(GuiGraphics graphics, Component text, int x, int y, int color) {
        // 长法术介绍最多占三行，避免覆盖学习进度和材料；悬停卡片可看完整说明。
        var lines = font.split(text, 244);
        graphics.pose().pushPose(); graphics.pose().translate(x, y, 0); graphics.pose().scale(0.85F, 0.85F, 1);
        for (int i = 0; i < Math.min(3, lines.size()); i++) graphics.drawString(font, lines.get(i), 0, i * 9, color, false);
        graphics.pose().popPose();
    }

    private static List<SpellDefinition> spellsForMedium(String medium) {
        List<SpellDefinition> result = new ArrayList<>();
        for (SpellDefinition spell : SpellDefinition.values()) if (spell.medium().equals(medium)) result.add(spell);
        return result;
    }

    private static int tierRow(SpellDefinition spell) {
        return switch (spell.grade()) {
            case "medium" -> 1;
            case "advanced" -> 2;
            case "supreme" -> 3;
            case "special" -> 4;
            default -> 0;
        };
    }

    private static int tierColumn(List<SpellDefinition> spells, SpellDefinition target) {
        int column = 0;
        for (SpellDefinition spell : spells) {
            if (spell == target) return column;
            if (tierRow(spell) == tierRow(target)) column++;
        }
        return column;
    }

    private static int longestTierCount(List<SpellDefinition> spells) {
        int[] counts = new int[5];
        for (SpellDefinition spell : spells) counts[Math.min(counts.length - 1, tierRow(spell))]++;
        int maximum = 0;
        for (int count : counts) maximum = Math.max(maximum, count);
        return maximum;
    }

    private static double maxSpellScrollX(List<SpellDefinition> spells) {
        return Math.max(0, longestTierCount(spells) * SPELL_CARD_STEP_X + 4 - SPELL_VIEW_WIDTH);
    }

    private static double maxSpellScrollY(List<SpellDefinition> spells) {
        int lastRow = 0;
        for (SpellDefinition spell : spells) lastRow = Math.max(lastRow, tierRow(spell));
        int contentHeight = (lastRow + 1) * SPELL_CARD_STEP_Y + 2;
        return Math.max(0, contentHeight - SPELL_VIEW_HEIGHT);
    }

    private void drawSpellScrollbars(GuiGraphics graphics, List<SpellDefinition> spells, int viewX, int viewY) {
        double maximumX = maxSpellScrollX(spells), maximumY = maxSpellScrollY(spells);
        int trackX = viewX + 4, trackY = viewY + SPELL_VIEW_HEIGHT - 4, trackWidth = SPELL_VIEW_WIDTH - 12;
        graphics.fill(trackX, trackY, trackX + trackWidth, trackY + 2, 0xFF332541);
        int thumbWidth = maximumX <= 0 ? trackWidth : Math.max(24,
                trackWidth * SPELL_VIEW_WIDTH / (SPELL_VIEW_WIDTH + (int)maximumX));
        int thumbX = maximumX <= 0 ? trackX : trackX + (int)Math.round(spellScrollX / maximumX * (trackWidth - thumbWidth));
        graphics.fill(thumbX, trackY - 1, thumbX + thumbWidth, trackY + 3,
                draggingSpellPage ? 0xFFFFD26F : 0xFF936ED0);
        int verticalX = viewX + SPELL_VIEW_WIDTH - 4, verticalY = viewY + 4, verticalHeight = SPELL_VIEW_HEIGHT - 12;
        graphics.fill(verticalX, verticalY, verticalX + 2, verticalY + verticalHeight, 0xFF332541);
        int thumbHeight = maximumY <= 0 ? verticalHeight : Math.max(18,
                verticalHeight * SPELL_VIEW_HEIGHT / (SPELL_VIEW_HEIGHT + (int)maximumY));
        int thumbY = maximumY <= 0 ? verticalY : verticalY + (int)Math.round(spellScrollY / maximumY * (verticalHeight - thumbHeight));
        graphics.fill(verticalX - 1, thumbY, verticalX + 3, thumbY + thumbHeight,
                draggingSpellPage ? 0xFFFFD26F : 0xFF936ED0);
    }

    private boolean insideSpellView(double mouseX, double mouseY) {
        int x = left + SPELL_VIEW_OFFSET_X;
        int y = top + SPELL_VIEW_OFFSET_Y;
        return mouseX >= x && mouseX < x + SPELL_VIEW_WIDTH && mouseY >= y && mouseY < y + SPELL_VIEW_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (page == Page.SPELLS) {
            String[] media = {"arcane", "fire", "water", "ice", "lightning", "wind", "earth", "nature", "metal", "light", "poison", "sound"};
            for (int i = 0; i < media.length; i++) {
                int x = left + 18 + (i % 3) * 42;
                int y = top + 64 + (i / 3) * 40;
                if (mouseX >= x && mouseX < x + 36 && mouseY >= y && mouseY < y + 34) {
                    selectedMedium = media[i];
                    selectedSpell = null;
                    spellScrollX = 0;
                    spellScrollY = 0;
                    rebuildWidgets();
                    return true;
                }
            }
            List<SpellDefinition> spells = spellsForMedium(selectedMedium);
            if (button == 1 && insideSpellView(mouseX, mouseY)) {
                int viewX = left + SPELL_VIEW_OFFSET_X;
                int viewY = top + SPELL_VIEW_OFFSET_Y;
                for (SpellDefinition spell : spells) {
                    int x = viewX + 7 + tierColumn(spells, spell) * SPELL_CARD_STEP_X - (int)Math.round(spellScrollX);
                    int y = viewY + 3 + tierRow(spell) * SPELL_CARD_STEP_Y - (int)Math.round(spellScrollY);
                    if (mouseX >= Math.max(x, viewX) && mouseX < Math.min(x + SPELL_CARD_WIDTH, viewX + SPELL_VIEW_WIDTH)
                            && mouseY >= Math.max(y, viewY) && mouseY < Math.min(y + SPELL_CARD_HEIGHT, viewY + SPELL_VIEW_HEIGHT)) {
                        selectedSpell = spell;
                        rebuildWidgets();
                        return true;
                    }
                }
            }
            if (button == 0 && insideSpellView(mouseX, mouseY)) {
                draggingSpellPage = true;
                spellDragStartX = mouseX;
                spellDragStartY = mouseY;
                spellDragStartScrollX = spellScrollX;
                spellDragStartScrollY = spellScrollY;
                return true;
            }
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (page == Page.SPELLS && selectedSpell != null && (button == 0 || button == 1)) {
            selectedSpell = null;
            rebuildWidgets();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingSpellPage && button == 0) {
            List<SpellDefinition> spells = spellsForMedium(selectedMedium);
            spellScrollX = Math.max(0, Math.min(maxSpellScrollX(spells),
                    spellDragStartScrollX - (mouseX - spellDragStartX)));
            spellScrollY = Math.max(0, Math.min(maxSpellScrollY(spells),
                    spellDragStartScrollY - (mouseY - spellDragStartY)));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingSpellPage) {
            draggingSpellPage = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalDelta, double verticalDelta) {
        if (page == Page.SPELLS && insideSpellView(mouseX, mouseY)) {
            List<SpellDefinition> spells = spellsForMedium(selectedMedium);
            if (horizontalDelta != 0) spellScrollX = Math.max(0,
                    Math.min(maxSpellScrollX(spells), spellScrollX - horizontalDelta * 22));
            if (verticalDelta != 0) spellScrollY = Math.max(0,
                    Math.min(maxSpellScrollY(spells), spellScrollY - verticalDelta * 18));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalDelta, verticalDelta);
    }

    private static void drawSpellIcon(GuiGraphics graphics, SpellDefinition spell, int x, int y, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.blit(spell.icon(), 0, 0, 0, 0, 16, 16, 16, 16);
        graphics.pose().popPose();
    }

    private void renderStatus(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int leftX = left + 18;
        int rightX = left + 205;
        int y = top + 48;
        drawStatusRow(graphics, leftX, y, "gui.dimensional_mana.status.health",
                format(mc.player.getHealth()) + " / " + format(mc.player.getMaxHealth()),
                "gui.dimensional_mana.source.health");
        drawAttributeRow(graphics, leftX, y += 18, "gui.dimensional_mana.status.attack", Attributes.ATTACK_DAMAGE);
        drawAttributeRow(graphics, leftX, y += 18, "gui.dimensional_mana.status.armor", Attributes.ARMOR);
        drawStatusRow(graphics, leftX, y += 18, "gui.dimensional_mana.status.food",
                mc.player.getFoodData().getFoodLevel() + " / 20", "gui.dimensional_mana.source.food");
        drawStatusRow(graphics, leftX, y += 18, "gui.dimensional_mana.status.mana",
                format(ClientManaState.mana()) + " / " + format(ClientManaState.maxMana()),
                "gui.dimensional_mana.source.mana");
        drawStatusRow(graphics, leftX, y += 18, "gui.dimensional_mana.status.aura",
                ClientManaState.aura() + " / " + ClientManaState.maxAura(), "gui.dimensional_mana.source.aura");
        drawStatusRow(graphics, leftX, y += 18, "gui.dimensional_mana.status.affinity",
                String.format("%.3f", ClientProgressState.affinity()), "gui.dimensional_mana.source.affinity");
        drawStatusRow(graphics, leftX, y += 18, "gui.dimensional_mana.status.concentration",
                String.format("%.2f", ClientProgressState.concentration()), "gui.dimensional_mana.source.concentration");
        drawStatusRow(graphics, leftX, y += 18, "gui.dimensional_mana.status.exhaustion",
                ClientManaState.exhausted() ? Component.translatable("gui.dimensional_mana.yes").getString()
                        : Component.translatable("gui.dimensional_mana.no").getString(), "gui.dimensional_mana.source.exhaustion");
        drawStatusRow(graphics, leftX, y += 18, "gui.dimensional_mana.status.daily_growth",
                ClientProgressState.dailyGrowths() + " / 4", "gui.dimensional_mana.source.daily_growth");

        y = top + 48;
        drawAttributeRow(graphics, rightX, y, "attribute.name.dimensional_mana.spell_power", SpellAttributes.SPELL_POWER);
        drawAttributeRow(graphics, rightX, y += 18, "attribute.name.dimensional_mana.spell_haste", SpellAttributes.SPELL_HASTE);
        drawPercentAttributeRow(graphics, rightX, y += 18, "attribute.name.dimensional_mana.spell_crit_chance", SpellAttributes.SPELL_CRIT_CHANCE);
        drawPercentAttributeRow(graphics, rightX, y += 18, "attribute.name.dimensional_mana.spell_crit_damage", SpellAttributes.SPELL_CRIT_DAMAGE);
        drawPercentAttributeRow(graphics, rightX, y += 18, "attribute.name.dimensional_mana.mana_cost_reduction", SpellAttributes.MANA_COST_REDUCTION);
        drawPercentAttributeRow(graphics, rightX, y += 18, "attribute.name.dimensional_mana.spell_resistance", SpellAttributes.SPELL_RESISTANCE);
        drawStatusRow(graphics, rightX, y += 18, "gui.dimensional_mana.status.magic_level",
                ClientProgressState.magicLevel() + " / " + MagicProgression.MAX_MAGIC_LEVEL, "gui.dimensional_mana.source.magic_level");
        drawStatusRow(graphics, rightX, y += 18, "gui.dimensional_mana.status.magic_xp",
                String.valueOf(ClientProgressState.magicExperience()), "gui.dimensional_mana.source.magic_xp");
        drawStatusRow(graphics, rightX, y += 18, "gui.dimensional_mana.status.skill_points",
                String.valueOf(ClientProgressState.skillPoints()), "gui.dimensional_mana.source.skill_points");
        drawStatusRow(graphics, rightX, y += 18, "gui.dimensional_mana.status.spent_points",
                String.valueOf(ClientProgressState.spentSkillPoints()), "gui.dimensional_mana.source.spent_points");
    }

    private void drawAttributeRow(GuiGraphics graphics, int x, int y, String label, net.minecraft.core.Holder<Attribute> attribute) {
        AttributeInstance instance = minecraft.player == null ? null : minecraft.player.getAttribute(attribute);
        double value = instance == null ? 0.0 : instance.getValue();
        double base = instance == null ? 0.0 : instance.getBaseValue();
        drawStatusRow(graphics, x, y, label, format(value), label, base, value);
    }

    private void drawPercentAttributeRow(GuiGraphics graphics, int x, int y, String label, net.minecraft.core.Holder<Attribute> attribute) {
        AttributeInstance instance = minecraft.player == null ? null : minecraft.player.getAttribute(attribute);
        double value = instance == null ? 0.0 : instance.getValue();
        double base = instance == null ? 0.0 : instance.getBaseValue();
        drawStatusRow(graphics, x, y, label, String.format("%.1f%%", value * 100.0), label, base, value);
    }

    private void drawStatusRow(GuiGraphics graphics, int x, int y, String label, String value, String source) {
        graphics.drawString(font, Component.translatable(label), x, y, 0xFFCBC0E0, false);
        graphics.drawString(font, value, x + 112, y, 0xFFFFFFFF, false);
        hoverRows.add(new HoverRow(x, y - 2, 170, 14, List.of(
                Component.translatable(label).withStyle(ChatFormatting.LIGHT_PURPLE),
                Component.translatable(source).withStyle(ChatFormatting.GRAY))));
    }

    private void drawStatusRow(GuiGraphics graphics, int x, int y, String label, String value,
                               String source, double base, double finalValue) {
        graphics.drawString(font, Component.translatable(label), x, y, 0xFFCBC0E0, false);
        graphics.drawString(font, value, x + 112, y, 0xFFFFFFFF, false);
        hoverRows.add(new HoverRow(x, y - 2, 170, 14, List.of(
                Component.translatable(label).withStyle(ChatFormatting.LIGHT_PURPLE),
                Component.translatable("gui.dimensional_mana.source.attribute_base", format(base)).withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.dimensional_mana.source.attribute_modifier", format(finalValue - base)).withStyle(ChatFormatting.AQUA),
                Component.translatable(source).withStyle(ChatFormatting.DARK_GRAY))));
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.format("%.0f", value) : String.format("%.3f", value);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record HoverRow(int x, int y, int width, int height, List<Component> tooltip) {}
}
