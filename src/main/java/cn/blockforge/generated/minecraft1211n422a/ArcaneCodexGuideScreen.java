package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/** 帕秋莉手册式的首页、章节阅读与分类配方图鉴。 */
public final class ArcaneCodexGuideScreen extends Screen {
    private enum View { HOME, CHAPTER_INDEX, CHAPTER, RECIPE_CATEGORIES, RECIPE_KINDS, RECIPE_INDEX, RECIPE_DETAIL }

    private static final int PANEL_WIDTH = 440;
    private static final int PANEL_HEIGHT = 268;
    private static final int RECIPE_ENTRIES_PER_PAGE = 18;
    private static final List<GuidePage> PAGES = List.of(
            page("basics", 8), page("crystals", 9), page("furnaces", 8), page("processor", 10),
            page("grinding", 8), page("potions", 9), page("exploration", 8), page("enchantments", 8),
            page("magic_equipment", 12), page("progression", 9), page("magic_system", 8), page("schools", 13),
            page("media", 8), page("element_talents", 5), page("element_talents_more", 5),
            page("implemented_spells", 6), page("implemented_spells_more", 6),
            page("reaction_rules", 6), page("reaction_priority", 7), page("reaction_charge", 6),
            page("reaction_amplify", 6), page("reaction_status", 5), page("reaction_secondary", 6),
            page("living_casters", 6), page("new_spells_rules", 6), page("new_fire_water", 6),
            page("new_ice_lightning", 7), page("new_wind_earth", 6), page("new_nature_metal", 6),
            page("new_light_poison", 6), page("new_sound", 6), page("new_advanced_arcane", 6),
            page("tiers", 7), page("insight", 7), page("new_fire_siege", 8), page("new_ice_domain", 10),
            page("new_thunder", 8), page("new_wind_storm", 9), page("new_earth_stone", 9),
            page("new_arcane_chaos", 9), page("new_nature_tree", 9), page("new_metal_ward_swords", 8),
            page("new_light_shelter_rift", 9), page("new_poison_pair", 9), page("new_sound_bells", 9),
            page("achievements", 9), page("adventurer_trade", 9)
    );
    private static final List<Chapter> CHAPTERS = List.of(
            chapter("basics", Items.BOOK, 0, 5),
            chapter("magic", Items.ENCHANTED_BOOK, 10, 11, 12, 13, 14, 32),
            chapter("reactions", Items.BLAZE_POWDER, 17, 18, 19, 20, 21, 22),
            chapter("spells", Items.AMETHYST_SHARD, 15, 16, 24, 25, 26, 27, 28, 29, 30, 31,
                    34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44),
            chapter("crystals", Items.AMETHYST_CLUSTER, 1, 4),
            chapter("devices", Items.CRAFTING_TABLE, 2, 3),
            chapter("recipes", Items.KNOWLEDGE_BOOK),
            chapter("exploration", Items.SPYGLASS, 6, 23, 46),
            chapter("progression", Items.EXPERIENCE_BOTTLE, 7, 8, 9, 33, 45)
    );
    private static final ResourceLocation ARCANE_ICON = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID,
            "textures/mob_effect/arcane_attachment.png");
    private static final List<ProductCategory> PRODUCT_CATEGORIES = List.of(
            new ProductCategory("all", Items.KNOWLEDGE_BOOK),
            new ProductCategory("components", Items.NETHERITE_SCRAP),
            new ProductCategory("materials", Items.AMETHYST_SHARD),
            new ProductCategory("devices", Items.CRAFTING_TABLE),
            new ProductCategory("alchemy", Items.POTION),
            new ProductCategory("magic", Items.ENCHANTED_BOOK)
    );
    private static final List<RecipeKind> RECIPE_KINDS = List.of(
            new RecipeKind("crafting", Items.CRAFTING_TABLE),
            new RecipeKind("smelting", Items.FURNACE),
            new RecipeKind("blasting", Items.BLAST_FURNACE),
            new RecipeKind("mana_forge", Items.CRYING_OBSIDIAN),
            new RecipeKind("professional", Items.NETHER_STAR),
            new RecipeKind("stonecutting", Items.STONECUTTER),
            new RecipeKind("smithing", Items.SMITHING_TABLE),
            new RecipeKind("grinding", Items.BOWL),
            new RecipeKind("processor", Items.IRON_INGOT),
            new RecipeKind("brewing", Items.BREWING_STAND)
    );

    private View view = View.HOME;
    private int selectedChapter;
    private int page;
    /** 章节正文的滚动量（像素）与当前排版出的最大可滚动距离。 */
    private double chapterScroll;
    private int chapterScrollMax;
    private int chapterScrollPage = -1;
    /** 章节索引（页卡网格）的滚动量。 */
    private double chapterIndexScroll;
    private int chapterIndexScrollMax;
    private int chapterIndexPage = -1;
    private int recipeIndexPage;
    private int recipeDetailPage;
    private int left;
    private int top;
    private String selectedCategory = "materials";
    private String selectedKind = "crafting";
    private ItemStack selectedRecipeProduct = ItemStack.EMPTY;
    private ItemStack hoveredStack = ItemStack.EMPTY;
    /** 配方只在打开本次图鉴时整理一次，避免每一帧重复遍历配方管理器和全部物品注册表。 */
    private List<GuideRecipe> guideRecipeCache;
    private final Map<SmithingRecipe, List<GuideIngredient>> smithingIngredients = new IdentityHashMap<>();

    public ArcaneCodexGuideScreen() {
        super(Component.translatable("gui.dimensional_mana.guide.book_title"));
    }

    private static GuidePage page(String id, int lines) {
        return new GuidePage(id, "gui.dimensional_mana.guide.page." + id + ".title", lines,
                "gui.dimensional_mana.guide.page." + id + ".line.");
    }

    /**
     * 章节页码只保留真实存在的页面。这样以后增删图鉴页时，即使漏改章节清单，最多只会少一张卡片，
     * 不会再因为 {@link List#get(int)} 越界让玩家打开整章就闪退。
     */
    private static Chapter chapter(String id, Item icon, int... pageIndexes) {
        List<Integer> validPages = new ArrayList<>(pageIndexes.length);
        for (int pageIndex : pageIndexes) {
            if (pageIndex >= 0 && pageIndex < PAGES.size() && !validPages.contains(pageIndex)) validPages.add(pageIndex);
        }
        return new Chapter(id, List.copyOf(validPages), icon);
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        clearWidgets();
        if (view != View.HOME) {
            addRenderableWidget(Button.builder(Component.translatable("gui.dimensional_mana.guide.home"), button -> {
                view = View.HOME;
                selectedRecipeProduct = ItemStack.EMPTY;
                rebuildWidgets();
            }).bounds(left + 14, top + PANEL_HEIGHT - 28, 64, 18).build());
        }
        switch (view) {
            case CHAPTER -> addChapterNavigation();
            case RECIPE_KINDS -> addBackButton("gui.dimensional_mana.guide.back_to_categories", View.RECIPE_CATEGORIES, 100);
            case RECIPE_INDEX -> addRecipeIndexNavigation();
            case RECIPE_DETAIL -> addRecipeDetailNavigation();
            default -> { }
        }
    }

    private void addBackButton(String key, View destination, int buttonWidth) {
        addRenderableWidget(Button.builder(Component.translatable(key), button -> {
            view = destination;
            playTurn();
            rebuildWidgets();
        }).bounds(left + 86, top + PANEL_HEIGHT - 28, buttonWidth, 18).build());
    }

    private void addChapterNavigation() {
        Chapter chapter = CHAPTERS.get(selectedChapter);
        int position = chapter.pages().indexOf(page);
        addRenderableWidget(Button.builder(Component.translatable("gui.dimensional_mana.guide.back_to_chapter"), button -> {
            view = View.CHAPTER_INDEX;
            playTurn();
            rebuildWidgets();
        }).bounds(left + 86, top + PANEL_HEIGHT - 28, 84, 18).build());
        Button previous = Button.builder(Component.translatable("gui.dimensional_mana.guide.previous"), button -> {
            if (position > 0) { page = chapter.pages().get(position - 1); playTurn(); rebuildWidgets(); }
        }).bounds(left + 178, top + PANEL_HEIGHT - 28, 70, 18).build();
        previous.active = position > 0;
        addRenderableWidget(previous);
        Button next = Button.builder(Component.translatable("gui.dimensional_mana.guide.next"), button -> {
            if (position + 1 < chapter.pages().size()) {
                page = chapter.pages().get(position + 1); playTurn(); rebuildWidgets();
            }
        }).bounds(left + PANEL_WIDTH - 84, top + PANEL_HEIGHT - 28, 70, 18).build();
        next.active = position + 1 < chapter.pages().size();
        addRenderableWidget(next);
    }

    private void addRecipeIndexNavigation() {
        addBackButton("gui.dimensional_mana.guide.back_to_kinds", View.RECIPE_KINDS, 100);
        int pages = recipeIndexPageCount();
        Button previous = Button.builder(Component.translatable("gui.dimensional_mana.guide.previous"), button -> {
            if (recipeIndexPage > 0) { recipeIndexPage--; playTurn(); rebuildWidgets(); }
        }).bounds(left + 194, top + PANEL_HEIGHT - 28, 70, 18).build();
        previous.active = recipeIndexPage > 0;
        addRenderableWidget(previous);
        Button next = Button.builder(Component.translatable("gui.dimensional_mana.guide.next"), button -> {
            if (recipeIndexPage + 1 < pages) { recipeIndexPage++; playTurn(); rebuildWidgets(); }
        }).bounds(left + PANEL_WIDTH - 84, top + PANEL_HEIGHT - 28, 70, 18).build();
        next.active = recipeIndexPage + 1 < pages;
        addRenderableWidget(next);
    }

    private void addRecipeDetailNavigation() {
        addRenderableWidget(Button.builder(Component.translatable("gui.dimensional_mana.guide.back_to_recipes"), button -> {
            view = View.RECIPE_INDEX;
            recipeDetailPage = 0;
            playTurn();
            rebuildWidgets();
        }).bounds(left + 86, top + PANEL_HEIGHT - 28, 100, 18).build());
        RecipeEntry entry = selectedRecipeEntry();
        int pages = entry == null ? 1 : entry.recipes.size();
        Button previous = Button.builder(Component.translatable("gui.dimensional_mana.guide.previous"), button -> {
            if (recipeDetailPage > 0) { recipeDetailPage--; playTurn(); rebuildWidgets(); }
        }).bounds(left + 194, top + PANEL_HEIGHT - 28, 70, 18).build();
        previous.active = recipeDetailPage > 0;
        addRenderableWidget(previous);
        Button next = Button.builder(Component.translatable("gui.dimensional_mana.guide.next"), button -> {
            if (recipeDetailPage + 1 < pages) { recipeDetailPage++; playTurn(); rebuildWidgets(); }
        }).bounds(left + PANEL_WIDTH - 84, top + PANEL_HEIGHT - 28, 70, 18).build();
        next.active = recipeDetailPage + 1 < pages;
        addRenderableWidget(next);
    }

    private void playTurn() {
        if (minecraft != null && minecraft.player != null) minecraft.player.playSound(GeneratedMod.CODEX_PAGE_TURN.get(), 0.7F, 1.0F);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0090610);
        graphics.fillGradient(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFFF2E2C0, 0xFFD8C291);
        graphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF6E4C2B);
        graphics.fill(left + PANEL_WIDTH / 2 - 2, top + 8, left + PANEL_WIDTH / 2 + 2,
                top + PANEL_HEIGHT - 36, 0x334D321E);
        graphics.drawCenteredString(font, title, width / 2, top + 10, 0xFF4E285B);
        hoveredStack = ItemStack.EMPTY;
        switch (view) {
            case HOME -> renderHome(graphics, mouseX, mouseY);
            case CHAPTER_INDEX -> renderChapterIndex(graphics, mouseX, mouseY);
            case CHAPTER -> renderChapter(graphics);
            case RECIPE_CATEGORIES -> renderRecipeCategories(graphics, mouseX, mouseY);
            case RECIPE_KINDS -> renderRecipeKinds(graphics, mouseX, mouseY);
            case RECIPE_INDEX -> renderRecipeIndex(graphics, mouseX, mouseY);
            case RECIPE_DETAIL -> renderRecipeDetail(graphics, mouseX, mouseY);
        }
    }

    private void renderHome(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawWordWrap(font, Component.translatable("gui.dimensional_mana.guide.home_intro"),
                left + 22, top + 30, PANEL_WIDTH - 44, 0xFF3B2A32);
        for (int i = 0; i < CHAPTERS.size(); i++) {
            Chapter chapter = CHAPTERS.get(i);
            int x = left + 16 + (i % 3) * 138;
            int y = top + 66 + (i / 3) * 55;
            boolean hover = inside(mouseX, mouseY, x, y, 132, 48);
            graphics.fill(x, y, x + 132, y + 48, hover ? 0x66A36BC1 : 0x33705063);
            graphics.renderOutline(x, y, 132, 48, hover ? 0xFF8A3FA5 : 0xFF8A6A46);
            ItemStack icon = new ItemStack(chapter.icon());
            graphics.renderItem(icon, x + 8, y + 15);
            graphics.drawString(font, Component.translatable("gui.dimensional_mana.guide.chapter." + chapter.id()),
                    x + 31, y + 9, 0xFF4A274E, false);
            graphics.drawWordWrap(font, Component.translatable("gui.dimensional_mana.guide.chapter." + chapter.id() + ".hint"),
                    x + 31, y + 23, 94, 0xFF5E5144);
            if (hover) hoveredStack = icon;
        }
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.guide.home_footer"),
                width / 2, top + PANEL_HEIGHT - 22, 0xFF715D49);
    }

    private void renderChapterIndex(GuiGraphics graphics, int mouseX, int mouseY) {
        Chapter chapter = CHAPTERS.get(selectedChapter);
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.guide.chapter_index",
                Component.translatable("gui.dimensional_mana.guide.chapter." + chapter.id())), width / 2, top + 31, 0xFF6A2B76);
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.guide.chapter_index_hint"),
                width / 2, top + 45, 0xFF715D49);
        // 页数多的章节（例如“法术”有十四页）也要能看全：超出可视区就滚动。
        int rows = (chapter.pages().size() + 3) / 4;
        int viewTop = top + 62;
        int viewBottom = top + PANEL_HEIGHT - 34;
        chapterIndexScrollMax = Math.max(0, rows * 70 - (viewBottom - viewTop));
        if (selectedChapter != chapterIndexPage) { chapterIndexScroll = 0; chapterIndexPage = selectedChapter; }
        chapterIndexScroll = Mth.clamp(chapterIndexScroll, 0, chapterIndexScrollMax);
        graphics.enableScissor(left + 12, viewTop, left + PANEL_WIDTH - 12, viewBottom);
        for (int i = 0; i < chapter.pages().size(); i++) {
            GuidePage guide = PAGES.get(chapter.pages().get(i));
            int x = left + 19 + (i % 4) * 103;
            int y = chapterIndexCardY(i);
            boolean hover = inside(mouseX, mouseY, x, y, 92, 58)
                    && mouseY >= viewTop && mouseY < viewBottom;
            graphics.fill(x, y, x + 92, y + 58, hover ? 0x669A5AA8 : 0x33FFFFFF);
            graphics.renderOutline(x, y, 92, 58, hover ? 0xFF8A3FA5 : 0xFF9A805E);
            renderPageIcon(graphics, guide.id(), x + 38, y + 7);
            graphics.drawWordWrap(font, Component.translatable(guide.titleKey()), x + 6, y + 29, 80,
                    hover ? 0xFF6A2B76 : 0xFF4A274E);
        }
        graphics.disableScissor();
        if (chapterIndexScrollMax > 0) renderScrollbar(graphics, viewTop, viewBottom, chapterIndexScroll, chapterIndexScrollMax, left + PANEL_WIDTH - 13);
    }

    /** 章节索引卡片的纵坐标会随滚动偏移移动；点击判定用同一个函数，避免点错页。 */
    private int chapterIndexCardY(int index) {
        return top + 65 - (int)Math.round(chapterIndexScroll) + (index / 4) * 70;
    }

    private void renderChapter(GuiGraphics graphics) {
        GuidePage guide = PAGES.get(page);
        graphics.drawCenteredString(font, Component.translatable(guide.titleKey()), width / 2, top + 29, 0xFF6A2B76);
        if (guide.id().equals("reaction_rules")) { chapterScroll = 0; chapterScrollMax = 0; renderAttachmentAtlas(graphics); return; }
        if (guide.id().equals("reaction_priority")) { chapterScroll = 0; chapterScrollMax = 0; renderReactionPriority(graphics); return; }
        if (guide.id().equals("reaction_charge")) { chapterScroll = 0; chapterScrollMax = 0; renderChargeGuide(graphics); return; }
        // 正文按行高排版：先算总高度，超出可视区就变成可滚动区域，长页不再被裁掉。
        if (page != chapterScrollPage) { chapterScroll = 0; chapterScrollPage = page; }
        int textWidth = PANEL_WIDTH - 40;
        int viewTop = top + 46;
        int viewBottom = top + PANEL_HEIGHT - 46;
        int lineHeight = guide.lines() > 10 ? 14 : 16;
        List<Component> lines = new ArrayList<>();
        int totalHeight = 0;
        for (int i = 1; i <= guide.lines(); i++) {
            Component line = richGuideLine(guide.id(), i, guide.linePrefix() + i);
            lines.add(line);
            totalHeight += Math.max(lineHeight, font.split(line, textWidth).size() * 10 + 3);
        }
        chapterScrollMax = Math.max(0, totalHeight - (viewBottom - viewTop));
        chapterScroll = Mth.clamp(chapterScroll, 0, chapterScrollMax);
        int y = viewTop - (int)Math.round(chapterScroll);
        graphics.enableScissor(left + 12, viewTop, left + PANEL_WIDTH - 12, viewBottom);
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            graphics.drawWordWrap(font, line, left + 20, y, textWidth, i == 0 ? 0xFF6C3E20 : 0xFF3D343A);
            y += Math.max(lineHeight, font.split(line, textWidth).size() * 10 + 3);
        }
        graphics.disableScissor();
        if (chapterScrollMax > 0) renderChapterScrollbar(graphics, viewTop, viewBottom);
        Chapter chapter = CHAPTERS.get(selectedChapter);
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.guide.page_number",
                        chapter.pages().indexOf(page) + 1, chapter.pages().size()),
                width / 2, top + PANEL_HEIGHT - 43, 0xFF705C4B);
    }

    /** 右侧滚动条：滑块长度与可见比例一致，让人一眼看出“下面还有字”。 */
    private void renderChapterScrollbar(GuiGraphics graphics, int viewTop, int viewBottom) {
        renderScrollbar(graphics, viewTop, viewBottom, chapterScroll, chapterScrollMax, left + PANEL_WIDTH - 13);
    }

    private void renderScrollbar(GuiGraphics graphics, int viewTop, int viewBottom, double scroll, int maxScroll,
                                 int trackX) {
        int trackHeight = viewBottom - viewTop;
        graphics.fill(trackX, viewTop, trackX + 4, viewBottom, 0x33806B56);
        double ratio = (double)trackHeight / (trackHeight + maxScroll);
        int thumbHeight = Math.max(12, (int)Math.round(trackHeight * ratio));
        int thumbY = viewTop + (int)Math.round((trackHeight - thumbHeight)
                * (maxScroll <= 0 ? 0 : scroll / maxScroll));
        graphics.fill(trackX, thumbY, trackX + 4, thumbY + thumbHeight, 0xFF8A3FA5);
        graphics.renderOutline(trackX - 1, thumbY - 1, 6, thumbHeight + 2, 0xFF6E4C2B);
    }

    private Component richGuideLine(String pageId, int line, String key) {
        if (pageId.equals("basics") && line == 1) return Component.translatable(key,
                Component.keybind("key.dimensional_mana.open_codex").withStyle(ChatFormatting.AQUA));
        if (pageId.equals("implemented_spells") && line == 9) return Component.translatable(key,
                Component.keybind("key.dimensional_mana.spell_wheel").withStyle(ChatFormatting.AQUA),
                Component.keybind("key.dimensional_mana.cast_spell").withStyle(ChatFormatting.AQUA));
        if (pageId.equals("reaction_amplify") && line == 1) return Component.translatable(key,
                reactionName(Reaction.VAPORIZE), multiplier("×2.0"), reactionName(Reaction.MELT), multiplier("×1.8"));
        return Component.translatable(key);
    }

    private static Component reactionName(Reaction reaction) {
        return Component.translatable(reaction.translationKey()).withStyle(ChatFormatting.BOLD)
                .withColor(reaction.color() & 0xFFFFFF);
    }

    private static Component multiplier(String value) {
        return Component.literal(value).withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD);
    }

    private static Component emphasized(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD);
    }

    private void renderAttachmentAtlas(GuiGraphics graphics) {
        for (int i = 0; i <= ElementType.values().length; i++) {
            boolean arcane = i == ElementType.values().length;
            ElementType element = arcane ? null : ElementType.values()[i];
            int x = left + 17 + (i % 4) * 102;
            int y = top + 48 + (i / 4) * 52;
            int color = arcane ? 0xFFE46CFF : element.color();
            graphics.fill(x, y, x + 94, y + 44, 0x33FFFFFF);
            graphics.renderOutline(x, y, 94, 44, color);
            if (arcane) graphics.blit(ARCANE_ICON, x + 6, y + 7, 0, 0, 24, 24, 16, 16);
            else graphics.renderItem(new ItemStack(element.icon()), x + 10, y + 9);
            Component name = arcane ? Component.translatable("gui.dimensional_mana.medium.arcane")
                    : Component.translatable(element.translationKey());
            graphics.drawString(font, name.copy().withStyle(ChatFormatting.BOLD), x + 34, y + 6, color, false);
            String seconds = String.format(Locale.ROOT, "%.1f", (arcane ? ElementReactionSystem.ARCANE_ATTACHMENT_TICKS
                    : element.attachmentTicks()) / 20.0F);
            Component type = Component.translatable(arcane ? "gui.dimensional_mana.guide.attachment.arcane"
                    : element.shortAttachment() ? "gui.dimensional_mana.guide.attachment.short"
                    : "gui.dimensional_mana.guide.attachment.long", seconds);
            graphics.drawWordWrap(font, type, x + 34, y + 20, 55, 0xFF5E5144);
        }
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.guide.attachment.hint"),
                width / 2, top + 210, 0xFF715D49);
    }

    private void renderReactionPriority(GuiGraphics graphics) {
        Reaction[][] groups = {
                {Reaction.VAPORIZE, Reaction.MELT},
                {Reaction.OVERLOAD, Reaction.ELECTRO_CHARGED, Reaction.SUPERCONDUCT, Reaction.CATALYZE},
                {Reaction.FREEZE}, {Reaction.CHARGE}, {Reaction.BURNING, Reaction.CORRUPTION},
                {Reaction.SWIRL}, {Reaction.MIRE, Reaction.SPREAD, Reaction.SMELT, Reaction.PURIFY, Reaction.SHOCKWAVE, Reaction.BLIND}
        };
        for (int i = 0; i < groups.length; i++) {
            int x = left + 18 + (i % 2) * 205;
            int y = top + 51 + (i / 2) * 43;
            graphics.fill(x, y, x + 196, y + 36, 0x33FFFFFF);
            graphics.renderOutline(x, y, 196, 36, groups[i][0].color());
            graphics.drawString(font, Component.translatable("gui.dimensional_mana.guide.reaction_group." + i)
                    .withStyle(ChatFormatting.BOLD), x + 8, y + 5, groups[i][0].color(), false);
            Component names = Component.empty();
            for (int j = 0; j < groups[i].length; j++) {
                if (j > 0) names = names.copy().append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
                names = names.copy().append(reactionName(groups[i][j]));
            }
            graphics.drawWordWrap(font, names, x + 8, y + 18, 180, 0xFF3D343A);
        }
    }

    private void renderChargeGuide(GuiGraphics graphics) {
        graphics.blit(ARCANE_ICON, left + 26, top + 51, 0, 0, 32, 32, 16, 16);
        graphics.drawString(font, Component.translatable("effect.dimensional_mana.arcane_attachment")
                .withStyle(ChatFormatting.BOLD, ChatFormatting.LIGHT_PURPLE), left + 66, top + 54, 0xFFE46CFF, false);
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.guide.arcane_duration", 7)
                .withStyle(ChatFormatting.DARK_PURPLE), left + 66, top + 69, 0xFFA33AC1, false);
        for (int i = 0; i < ElementType.values().length; i++) {
            ElementType element = ElementType.values()[i];
            int x = left + 232 + (i % 6) * 31;
            int y = top + 49 + (i / 6) * 31;
            graphics.fill(x, y, x + 24, y + 24, 0x33FFFFFF);
            graphics.renderOutline(x, y, 24, 24, element.color());
            graphics.renderItem(new ItemStack(element.icon()), x + 4, y + 4);
        }
        int y = top + 105;
        for (int i = 1; i <= 6; i++) {
            String key = "gui.dimensional_mana.guide.page.reaction_charge.line." + i;
            Component line = switch (i) {
                case 1 -> Component.translatable(key, reactionName(Reaction.CHARGE));
                case 2 -> Component.translatable(key, multiplier("×2"), emphasized("gui.dimensional_mana.guide.value.15s"),
                        emphasized("gui.dimensional_mana.guide.value.25s"));
                case 4 -> Component.translatable(key, emphasized("gui.dimensional_mana.guide.value.10s"), multiplier("30%"));
                default -> Component.translatable(key);
            };
            graphics.drawWordWrap(font, line, left + 22, y, PANEL_WIDTH - 44, 0xFF3D343A);
            y += Math.max(17, font.split(line, PANEL_WIDTH - 44).size() * 10 + 2);
        }
    }

    private void renderPageIcon(GuiGraphics graphics, String id, int x, int y) {
        if (id.equals("reaction_charge")) {
            graphics.blit(ARCANE_ICON, x, y, 0, 0, 16, 16, 16, 16);
            return;
        }
        graphics.renderItem(new ItemStack(pageIcon(id)), x, y);
    }

    private static Item pageIcon(String id) {
        if (id.startsWith("reaction")) return Items.BLAZE_POWDER;
        if (id.contains("spell") || id.startsWith("new_")) return Items.AMETHYST_SHARD;
        if (id.contains("crystal") || id.equals("grinding")) return Items.AMETHYST_CLUSTER;
        if (id.equals("furnaces") || id.equals("processor")) return Items.CRAFTING_TABLE;
        if (id.equals("living_casters")) return Items.IRON_SWORD;
        if (id.equals("progression") || id.equals("insight") || id.equals("element_talents")
                || id.equals("element_talents_more")) return Items.EXPERIENCE_BOTTLE;
        if (id.equals("tiers")) return Items.ENCHANTED_BOOK;
        if (id.equals("exploration")) return Items.SPYGLASS;
        if (id.equals("achievements")) return Items.EXPERIENCE_BOTTLE;
        if (id.equals("adventurer_trade")) return Items.EMERALD;
        return Items.ENCHANTED_BOOK;
    }

    private void renderRecipeCategories(GuiGraphics graphics, int mouseX, int mouseY) {
        List<GuideRecipe> recipes = allGuideRecipes();
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.guide.recipes.categories_title"),
                width / 2, top + 31, 0xFF6A2B76);
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.guide.recipes.categories_hint"),
                width / 2, top + 45, 0xFF715D49);
        for (int i = 0; i < PRODUCT_CATEGORIES.size(); i++) {
            ProductCategory category = PRODUCT_CATEGORIES.get(i);
            int x = left + 24 + (i % 2) * 205;
            int y = top + 60 + (i / 2) * 57;
            long count = recipes.stream().filter(recipe -> matchesCategory(recipe, category.id())).map(recipe -> recipe.result.getItem()).distinct().count();
            boolean hover = inside(mouseX, mouseY, x, y, 182, 56);
            graphics.fill(x, y, x + 182, y + 56, hover ? 0x669A5AA8 : 0x33FFFFFF);
            graphics.renderOutline(x, y, 182, 56, hover ? 0xFF8A3FA5 : 0xFF9A805E);
            ItemStack icon = new ItemStack(category.icon());
            graphics.renderItem(icon, x + 12, y + 18);
            graphics.drawString(font, Component.translatable("gui.dimensional_mana.guide.recipe.category." + category.id()),
                    x + 38, y + 10, 0xFF4A274E, false);
            graphics.drawWordWrap(font, Component.translatable("gui.dimensional_mana.guide.recipe.category." + category.id() + ".hint", count),
                    x + 38, y + 25, 135, 0xFF5E5144);
            if (hover) hoveredStack = icon;
        }
    }

    private void renderRecipeKinds(GuiGraphics graphics, int mouseX, int mouseY) {
        List<GuideRecipe> recipes = allGuideRecipes().stream().filter(recipe -> matchesCategory(recipe, selectedCategory)).toList();
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.guide.recipes.kinds_title",
                Component.translatable("gui.dimensional_mana.guide.recipe.category." + selectedCategory)), width / 2, top + 30, 0xFF6A2B76);
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.guide.recipes.kinds_hint"),
                width / 2, top + 44, 0xFF715D49);
        List<RecipeKind> kinds = availableKinds(recipes);
        for (int i = 0; i < kinds.size(); i++) {
            RecipeKind kind = kinds.get(i);
            int x = left + 19 + (i % 5) * 82;
            int y = top + 68 + (i / 5) * 72;
            long count = recipes.stream().filter(recipe -> recipe.kind.equals(kind.id())).map(recipe -> recipe.result.getItem()).distinct().count();
            boolean hover = inside(mouseX, mouseY, x, y, 74, 58);
            graphics.fill(x, y, x + 74, y + 58, hover ? 0x669A5AA8 : 0x33FFFFFF);
            graphics.renderOutline(x, y, 74, 58, hover ? 0xFF8A3FA5 : 0xFF9A805E);
            ItemStack icon = new ItemStack(kind.icon());
            graphics.renderItem(icon, x + 29, y + 6);
            graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.guide.recipe.kind." + kind.id()),
                    x + 37, y + 26, 0xFF4A274E);
            graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.guide.recipes.kind_count", count),
                    x + 37, y + 42, 0xFF715D49);
            if (hover) hoveredStack = icon;
        }
    }

    private void renderRecipeIndex(GuiGraphics graphics, int mouseX, int mouseY) {
        List<RecipeEntry> entries = filteredRecipeEntries();
        int pages = Math.max(1, (entries.size() + RECIPE_ENTRIES_PER_PAGE - 1) / RECIPE_ENTRIES_PER_PAGE);
        recipeIndexPage = Math.min(recipeIndexPage, pages - 1);
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.guide.recipes.index_title",
                Component.translatable("gui.dimensional_mana.guide.recipe.kind." + selectedKind)), width / 2, top + 29, 0xFF6A2B76);
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.guide.recipes.index_hint", entries.size()),
                width / 2, top + 42, 0xFF715D49);
        int first = recipeIndexPage * RECIPE_ENTRIES_PER_PAGE;
        for (int cell = 0; cell < RECIPE_ENTRIES_PER_PAGE; cell++) {
            int index = first + cell;
            if (index >= entries.size()) break;
            RecipeEntry entry = entries.get(index);
            int x = left + 20 + (cell % 6) * 68;
            int y = top + 61 + (cell / 6) * 49;
            boolean hover = inside(mouseX, mouseY, x, y, 62, 42);
            graphics.fill(x, y, x + 62, y + 42, hover ? 0x668D4E9D : 0x33FFFFFF);
            graphics.renderOutline(x, y, 62, 42, hover ? 0xFF8A3FA5 : 0xFF9A805E);
            graphics.renderItem(entry.result, x + 23, y + 5);
            graphics.renderItemDecorations(font, entry.result, x + 23, y + 5);
            String name = entry.result.getHoverName().getString();
            if (font.width(name) > 58) name = font.plainSubstrByWidth(name, 50) + "…";
            graphics.drawCenteredString(font, Component.literal(name), x + 31, y + 28, 0xFF4A274E);
            if (entry.recipes.size() > 1) graphics.drawString(font, Component.literal("×" + entry.recipes.size()),
                    x + 41, y + 7, 0xFF6A2B76, false);
            if (hover) hoveredStack = entry.result;
        }
        if (entries.isEmpty()) graphics.drawCenteredString(font,
                Component.translatable("gui.dimensional_mana.guide.recipes.empty"), width / 2, top + 126, 0xFF715D49);
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.guide.page_number", recipeIndexPage + 1, pages),
                width / 2, top + PANEL_HEIGHT - 43, 0xFF705C4B);
    }

    private void renderRecipeDetail(GuiGraphics graphics, int mouseX, int mouseY) {
        RecipeEntry entry = selectedRecipeEntry();
        if (entry == null || entry.recipes.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.guide.recipes.missing"),
                    width / 2, top + 105, 0xFF8A3F4A);
            return;
        }
        recipeDetailPage = Math.min(recipeDetailPage, entry.recipes.size() - 1);
        GuideRecipe recipe = entry.recipes.get(recipeDetailPage);
        graphics.renderItem(entry.result, left + 26, top + 29);
        graphics.drawString(font, entry.result.getHoverName(), left + 49, top + 31, 0xFF4A274E, false);
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.guide.recipes.detail_count",
                recipeDetailPage + 1, entry.recipes.size()), left + 49, top + 43, 0xFF837363, false);
        graphics.drawWordWrap(font, Component.translatable("gui.dimensional_mana.guide.recipes.introduction",
                entry.result.getHoverName(), Component.translatable("gui.dimensional_mana.guide.recipe.kind." + recipe.kind)),
                left + 238, top + 29, 180, 0xFF5E5144);
        renderLargeRecipe(graphics, recipe, left + 25, top + 70, mouseX, mouseY);
        graphics.drawCenteredString(font, Component.literal(recipe.sourceId), width / 2,
                top + PANEL_HEIGHT - 43, 0xFF8B7762);
    }

    private void renderLargeRecipe(GuiGraphics graphics, GuideRecipe recipe, int x, int y, int mouseX, int mouseY) {
        int gridX = x + 31;
        int gridY = y + Math.max(0, (82 - recipe.height * 24) / 2);
        long cycle = minecraft.level == null ? 0 : minecraft.level.getGameTime() / 30L;
        graphics.fill(x, y, x + 390, y + 130, 0x33FFFFFF);
        graphics.renderOutline(x, y, 390, 130, 0xFF8A6A46);
        for (int i = 0; i < Math.min(9, recipe.ingredients.size()); i++) {
            GuideIngredient ingredient = recipe.ingredients.get(i);
            int ix = gridX + (i % recipe.width) * 24;
            int iy = gridY + (i / recipe.width) * 24;
            graphics.fill(ix - 2, iy - 2, ix + 18, iy + 18, 0x33806B56);
            graphics.renderOutline(ix - 2, iy - 2, 20, 20, 0xFFAD9271);
            if (ingredient.choices.isEmpty()) continue;
            ItemStack stack = ingredient.choices.get((int)(cycle % ingredient.choices.size()));
            graphics.renderItem(stack, ix, iy);
            graphics.renderItemDecorations(font, stack, ix, iy);
            if (inside(mouseX, mouseY, ix, iy, 16, 16)) hoveredStack = stack;
        }
        graphics.drawCenteredString(font, Component.literal("→"), x + 151, y + 37, 0xFF6A2B76);
        graphics.fill(x + 178, y + 17, x + 222, y + 61, 0x33705063);
        graphics.renderOutline(x + 178, y + 17, 44, 44, 0xFF8A3FA5);
        graphics.renderItem(recipe.result, x + 192, y + 31);
        graphics.renderItemDecorations(font, recipe.result, x + 192, y + 31);
        if (inside(mouseX, mouseY, x + 192, y + 31, 16, 16)) hoveredStack = recipe.result;
        graphics.drawWordWrap(font, Component.translatable("gui.dimensional_mana.guide.recipe.type." + recipe.descriptionType),
                x + 239, y + 15, 140, 0xFF5C4B5F);
        graphics.drawWordWrap(font, recipe.note, x + 239, y + 67, 140, 0xFF837363);
        if (!recipe.byproducts.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.dimensional_mana.guide.recipe.byproducts"),
                    x + 12, y + 96, 0xFF5C4B5F, false);
            for (int i = 0; i < recipe.byproducts.size(); i++) {
                ItemStack extra = recipe.byproducts.get(i);
                int ix = x + 105 + i * 25;
                int iy = y + 91;
                graphics.renderItem(extra, ix, iy);
                graphics.renderItemDecorations(font, extra, ix, iy);
                if (inside(mouseX, mouseY, ix, iy, 16, 16)) hoveredStack = extra;
            }
        }
    }

    private List<GuideRecipe> allGuideRecipes() {
        if (guideRecipeCache != null) return guideRecipeCache;
        if (minecraft == null || minecraft.level == null) return List.of();
        List<GuideRecipe> recipes = new ArrayList<>();
        for (Object raw : minecraft.level.getRecipeManager().getOrderedRecipes()) {
            RecipeHolder<?> holder = (RecipeHolder<?>) raw;
            // 只接受数据包中真正属于本模组的配方；兼容炉动态镜像的原版/其他模组配方虽然
            // 使用 dimensional_mana 命名空间，但位于 compat/standard_smelting，不能进入知识之书。
            if (!isOwnCodexRecipe(holder)) continue;
            ItemStack result = holder.value().getResultItem(minecraft.level.registryAccess());
            if (result.isEmpty()) continue;
            String kind = loadedRecipeKind(holder);
            List<GuideIngredient> ingredients = new ArrayList<>();
            if (holder.value() instanceof SmithingRecipe smithing) {
                // 锻造配方不提供通用 getIngredients()，需分别读取模板、基础物品和追加材料。
                ingredients.addAll(smithingIngredients.computeIfAbsent(smithing, recipe -> List.of(
                        matchingItems(recipe::isTemplateIngredient), matchingItems(recipe::isBaseIngredient),
                        matchingItems(recipe::isAdditionIngredient))));
            } else {
                for (Ingredient ingredient : holder.value().getIngredients()) {
                    List<ItemStack> choices = new ArrayList<>();
                    for (ItemStack choice : ingredient.getItems()) choices.add(choice.copyWithCount(1));
                    ingredients.add(new GuideIngredient(choices));
                }
            }
            int recipeWidth = holder.value() instanceof ShapedRecipe shaped ? Math.max(1, shaped.getWidth())
                    : (ingredients.size() <= 3 ? Math.max(1, ingredients.size()) : 3);
            int recipeHeight = holder.value() instanceof ShapedRecipe shaped ? Math.max(1, shaped.getHeight())
                    : Math.max(1, (ingredients.size() + recipeWidth - 1) / recipeWidth);
            String descriptionType = holder.value() instanceof ShapedRecipe ? "shaped"
                    : holder.value().getType() == RecipeType.CRAFTING ? "shapeless" : kind;
            Component note = Component.translatable("gui.dimensional_mana.guide.recipes.alternatives_hint");
            if (holder.value() instanceof AbstractCookingRecipe cooking) {
                String seconds = String.format(Locale.ROOT, "%.1f", cooking.getCookingTime() / 20.0F);
                note = holder.id().getPath().startsWith("recycle_")
                        ? Component.translatable(holder.id().getPath().contains("arcane_fragment")
                                ? "gui.dimensional_mana.guide.recipe.arcane_recycling_note"
                                : "gui.dimensional_mana.guide.recipe.fragment_recycling_note", seconds)
                        : Component.translatable("gui.dimensional_mana.guide.recipe.cooking_time", seconds);
            }
            recipes.add(new GuideRecipe(result.copy(), ingredients, recipeWidth, recipeHeight, kind,
                    productCategory(result), descriptionType, note, holder.id().toString()));
        }
        addGrindingRecipes(recipes);
        addProcessorRecipes(recipes);
        addBrewingRecipes(recipes);
        guideRecipeCache = List.copyOf(recipes);
        return guideRecipeCache;
    }

    private static boolean isOwnCodexRecipe(RecipeHolder<?> holder) {
        return holder.id().getNamespace().equals(GeneratedMod.MOD_ID)
                && !holder.id().getPath().startsWith("compat/");
    }

    private static String loadedRecipeKind(RecipeHolder<?> holder) {
        RecipeType<?> type = holder.value().getType();
        if (type == RecipeType.CRAFTING) return "crafting";
        if (type == RecipeType.SMELTING) return "smelting";
        if (type == RecipeType.BLASTING) return "blasting";
        if (type == RecipeType.STONECUTTING) return "stonecutting";
        if (type == RecipeType.SMITHING) return "smithing";
        if (type == GeneratedMod.MANA_FORGE_RECIPE_TYPE.get()) return "mana_forge";
        if (type == GeneratedMod.PROFESSIONAL_RECIPE_TYPE.get()) return "professional";
        return "crafting";
    }

    private static void addGrindingRecipes(List<GuideRecipe> recipes) {
        addSynthetic(recipes, "grinding/lapis", new ItemStack(Items.LAPIS_LAZULI),
                modStack("lapis_dust", 6), "grinding", "materials", "grinding",
                Component.translatable("gui.dimensional_mana.guide.recipe.grinding_any"));
        addSynthetic(recipes, "grinding/amethyst", new ItemStack(Items.AMETHYST_SHARD),
                modStack("amethyst_dust", 2), "grinding", "materials", "grinding",
                Component.translatable("gui.dimensional_mana.guide.recipe.grinding_any"));
        String[] grades = {"lesser", "medium", "advanced", "supreme", "special"};
        for (int tier = 0; tier < grades.length; tier++) {
            String grade = grades[tier];
            addSynthetic(recipes, "grinding/" + grade, modStack(grade + "_mana_crystal", 1),
                    modStack(grade + "_mana_crystal_dust", 4), "grinding", "materials", "grinding",
                    Component.translatable("gui.dimensional_mana.guide.recipe.grinding_tier", tier + 1));
        }
    }

    private static void addProcessorRecipes(List<GuideRecipe> recipes) {
        // 直接使用真正的加工配方，不再手抄一份，以免新材料在机器里可做、书里却漏掉。
        for (CrystalProcessorBlockEntity.ProcessRecipe process : CrystalProcessorBlockEntity.recipes()) {
            if (process.outputs().isEmpty()) continue;
            boolean shaped = !process.pattern().isEmpty();
            List<GuideIngredient> ingredients = new ArrayList<>();
            if (shaped) {
                for (String ingredient : process.pattern()) ingredients.add(processorIngredient(ingredient, 1));
            } else {
                for (CrystalProcessorBlockEntity.IngredientStack ingredient : process.inputs()) {
                    ingredients.add(processorIngredient(ingredient.itemId(), ingredient.count()));
                }
            }
            int recipeWidth = shaped ? 3 : Math.min(3, Math.max(1, ingredients.size()));
            int recipeHeight = shaped ? 3 : Math.max(1, (ingredients.size() + recipeWidth - 1) / recipeWidth);
            CrystalProcessorBlockEntity.OutputStack output = process.outputs().getFirst();
            ItemStack result = guideStack(output.itemId(), output.count());
            if (result.isEmpty()) continue;
            List<ItemStack> byproducts = process.outputs().stream().skip(1)
                    .map(extra -> guideStack(extra.itemId(), extra.count())).filter(stack -> !stack.isEmpty()).toList();
            String seconds = String.format(Locale.ROOT, "%g", process.processTicks() / 20.0);
            // 秒数保持简短，避免说明超出配方框。
            if (process.processTicks() % 20 == 0) seconds = Integer.toString(process.processTicks() / 20);
            Component note = Component.translatable("gui.dimensional_mana.guide.recipe.magic_component_note",
                    seconds, process.manaCost());
            recipes.add(new GuideRecipe(result, ingredients, recipeWidth, recipeHeight, "processor",
                    productCategory(result), shaped ? "processor_shaped" : "processor", note,
                    GeneratedMod.MOD_ID + ":manual/processor/" + process.id(), byproducts));
        }
    }

    private static GuideIngredient processorIngredient(String id, int count) {
        if (id == null || id.isBlank()) return new GuideIngredient(List.of());
        // 加工配方里的占位材料必须先展开成具体物品；漏掉一个就会去 MOD_ITEMS 里查到 null 而崩掉整本图鉴。
        if (id.equals("#magic_fragment")) return anyElementOrSchoolFragment();
        if (id.startsWith("#element_fragment:"))
            return elementFragments(id.substring("#element_fragment:".length()), count);
        if (id.equals("#special_element_fragment")) return specialElementFragments(count);
        if (id.equals("#special_school_fragment")) return specialSchoolFragments(count);
        if (id.equals("#element_crystal")) return anyElementCrystal();
        if (id.equals("#enchanted_book_thorns_3")) return single(new ItemStack(Items.ENCHANTED_BOOK, count));
        // 以后再加新占位也不会崩溃：认不出来就当成“材料未知”的空格子。
        if (id.startsWith("#")) return new GuideIngredient(List.of());
        return single(guideStack(id, count));
    }

    /** 元素结晶的引子：对应元素的任意非特级碎片。 */
    private static GuideIngredient elementFragments(String elementId, int count) {
        List<ItemStack> choices = new ArrayList<>();
        for (String grade : List.of("lesser", "medium", "advanced", "supreme")) {
            String fragmentId = "arcane".equals(elementId) ? grade + "_arcane_fragment"
                    : grade + "_" + elementId + "_fragment";
            addChoice(choices, modStack(fragmentId, count));
        }
        return new GuideIngredient(List.copyOf(choices));
    }

    private static GuideIngredient specialElementFragments(int count) {
        List<ItemStack> choices = new ArrayList<>();
        for (ElementType element : ElementType.values()) {
            addChoice(choices, modStack(element.fragmentId("special"), count));
        }
        return new GuideIngredient(List.copyOf(choices));
    }

    private static GuideIngredient specialSchoolFragments(int count) {
        List<ItemStack> choices = new ArrayList<>();
        for (String school : GeneratedMod.SCHOOL_IDS) {
            addChoice(choices, modStack("special_" + school + "_school_fragment", count));
        }
        return new GuideIngredient(List.copyOf(choices));
    }

    private static void addChoice(List<ItemStack> choices, ItemStack stack) {
        if (!stack.isEmpty()) choices.add(stack);
    }

    private static GuideIngredient anyElementCrystal() {
        List<ItemStack> choices = new ArrayList<>();
        for (ElementType element : ElementType.values()) addChoice(choices, modStack(element.id() + "_element_crystal", 1));
        addChoice(choices, modStack(MagicEquipment.ARCANE + "_element_crystal", 1));
        return new GuideIngredient(List.copyOf(choices));
    }

    private static GuideIngredient anyElementOrSchoolFragment() {
        List<ItemStack> choices = new ArrayList<>();
        for (String grade : GeneratedMod.FRAGMENT_GRADES) {
            for (ElementType element : ElementType.values()) addChoice(choices, modStack(element.fragmentId(grade), 1));
            for (String school : GeneratedMod.SCHOOL_IDS)
                addChoice(choices, modStack(grade + "_" + school + "_school_fragment", 1));
        }
        return new GuideIngredient(List.copyOf(choices));
    }

    private static void addBrewingRecipes(List<GuideRecipe> recipes) {
        // 剧毒Ⅱ药水 + 特级魔晶粉 → 剧毒Ⅲ药水（中毒 III，18 秒）。
        ItemStack strongPoison = PotionContents.createItemStack(Items.POTION, Potions.STRONG_POISON);
        ItemStack poisonIII = PotionContents.createItemStack(Items.POTION, GeneratedMod.POISON_III);
        ItemStack specialDust = modStack("special_mana_crystal_dust", 1);
        if (!poisonIII.isEmpty() && !specialDust.isEmpty()) {
            recipes.add(new GuideRecipe(poisonIII, List.of(single(strongPoison), single(specialDust)),
                    2, 1, "brewing", "alchemy", "brewing",
                    Component.translatable("gui.dimensional_mana.guide.recipe.brewing_poison_iii_note"),
                    GeneratedMod.MOD_ID + ":manual/brewing/poison_iii"));
        }
        ItemStack thickPotion = PotionContents.createItemStack(Items.POTION, Potions.THICK);
        List<GuideIngredient> ingredients = List.of(single(thickPotion), single(modStack("lesser_mana_crystal_dust", 1)));
        ItemStack harmonizer = modStack("mana_harmonizer", 1);
        if (harmonizer.isEmpty()) return;
        recipes.add(new GuideRecipe(harmonizer, ingredients, 2, 1, "brewing", "alchemy", "brewing",
                Component.translatable("gui.dimensional_mana.guide.recipe.brewing_note"),
                GeneratedMod.MOD_ID + ":manual/brewing/mana_harmonizer"));
    }

    private static void addSynthetic(List<GuideRecipe> recipes, String id, ItemStack input, ItemStack result,
                                     String kind, String category, String descriptionType, Component note) {
        if (result.isEmpty() || input.isEmpty()) return;
        recipes.add(new GuideRecipe(result, List.of(single(input)), 1, 1, kind, category, descriptionType, note,
                GeneratedMod.MOD_ID + ":manual/" + id));
    }

    private static GuideIngredient single(ItemStack stack) {
        return new GuideIngredient(List.of(stack));
    }

    private static GuideIngredient matchingItems(Predicate<ItemStack> predicate) {
        List<ItemStack> choices = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            if (!stack.isEmpty() && predicate.test(stack)) choices.add(stack.copyWithCount(1));
        }
        return new GuideIngredient(List.copyOf(choices));
    }

    private static ItemStack modStack(String id, int count) {
        // 查不到就返回空栈：图鉴是只读界面，宁可少画一格也不能让整本书崩掉。
        var holder = GeneratedMod.MOD_ITEMS.get(id);
        return holder == null ? ItemStack.EMPTY : new ItemStack(holder.get(), count);
    }

    /** 图鉴里的物品：允许出现原版物品（写成 minecraft: 前缀）。 */
    private static ItemStack guideStack(String id, int count) {
        if (id == null || id.isBlank() || count <= 0) return ItemStack.EMPTY;
        if (id.startsWith("minecraft:")) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) return ItemStack.EMPTY;
            return new ItemStack(BuiltInRegistries.ITEM.get(location), count);
        }
        return modStack(id, count);
    }

    private static boolean matchesCategory(GuideRecipe recipe, String category) {
        // “全部配方”兜底；魔构件保留在材料分类中，并提供醒目的专门入口。
        return category.equals("all") || recipe.category.equals(category)
                || category.equals("materials") && recipe.category.equals("components");
    }

    private static String productCategory(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.endsWith("_magic_component")) return "components";
        if (path.contains("potion") || path.contains("harmonizer")) return "alchemy";
        if (path.contains("grinder") || path.contains("pestle") || path.contains("forge") || path.contains("furnace")
                || path.contains("processor") || path.contains("detector") || path.contains("glasses") || path.contains("orb")) return "devices";
        if (path.contains("crystal") || path.contains("dust") || path.contains("liquid_mana")) return "materials";
        return "magic";
    }

    private List<RecipeKind> availableKinds(List<GuideRecipe> recipes) {
        return RECIPE_KINDS.stream().filter(kind -> recipes.stream().anyMatch(recipe -> recipe.kind.equals(kind.id()))).toList();
    }

    private List<RecipeEntry> filteredRecipeEntries() {
        List<RecipeEntry> entries = new ArrayList<>();
        for (GuideRecipe recipe : allGuideRecipes()) {
            if (!matchesCategory(recipe, selectedCategory) || !recipe.kind.equals(selectedKind)) continue;
            RecipeEntry matching = null;
            for (RecipeEntry entry : entries) {
                if (ItemStack.isSameItemSameComponents(entry.result, recipe.result)) { matching = entry; break; }
            }
            if (matching == null) {
                matching = new RecipeEntry(recipe.result.copy());
                entries.add(matching);
            }
            matching.recipes.add(recipe);
        }
        entries.sort(Comparator.comparing(entry -> entry.result.getHoverName().getString()));
        return entries;
    }

    private RecipeEntry selectedRecipeEntry() {
        if (selectedRecipeProduct.isEmpty()) return null;
        for (RecipeEntry entry : filteredRecipeEntries()) {
            if (ItemStack.isSameItemSameComponents(entry.result, selectedRecipeProduct)) return entry;
        }
        return null;
    }

    private int recipeIndexPageCount() {
        return Math.max(1, (filteredRecipeEntries().size() + RECIPE_ENTRIES_PER_PAGE - 1) / RECIPE_ENTRIES_PER_PAGE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (view == View.HOME && button == 0) {
            for (int i = 0; i < CHAPTERS.size(); i++) {
                int x = left + 16 + (i % 3) * 138;
                int y = top + 66 + (i / 3) * 55;
                if (inside(mouseX, mouseY, x, y, 132, 48)) {
                    selectedChapter = i;
                    Chapter chapter = CHAPTERS.get(i);
                    view = chapter.pages().isEmpty() ? View.RECIPE_CATEGORIES : View.CHAPTER_INDEX;
                    playTurn();
                    rebuildWidgets();
                    return true;
                }
            }
        }
        if (view == View.CHAPTER_INDEX && button == 0) {
            Chapter chapter = CHAPTERS.get(selectedChapter);
            int viewTop = top + 62;
            int viewBottom = top + PANEL_HEIGHT - 34;
            // 被裁剪到可视区外的卡片不能响应点击，否则滚动章节时可能误开隐藏页面。
            if (mouseY < viewTop || mouseY >= viewBottom) return super.mouseClicked(mouseX, mouseY, button);
            for (int i = 0; i < chapter.pages().size(); i++) {
                int x = left + 19 + (i % 4) * 103;
                int y = chapterIndexCardY(i);
                if (inside(mouseX, mouseY, x, y, 92, 58)) {
                    page = chapter.pages().get(i);
                    view = View.CHAPTER;
                    playTurn();
                    rebuildWidgets();
                    return true;
                }
            }
        }
        if (view == View.RECIPE_CATEGORIES && button == 0) {
            for (int i = 0; i < PRODUCT_CATEGORIES.size(); i++) {
                int x = left + 24 + (i % 2) * 205;
                int y = top + 60 + (i / 2) * 57;
                if (inside(mouseX, mouseY, x, y, 182, 56)) {
                    selectedCategory = PRODUCT_CATEGORIES.get(i).id();
                    view = View.RECIPE_KINDS;
                    playTurn();
                    rebuildWidgets();
                    return true;
                }
            }
        }
        if (view == View.RECIPE_KINDS && button == 0) {
            List<RecipeKind> kinds = availableKinds(allGuideRecipes().stream().filter(recipe -> matchesCategory(recipe, selectedCategory)).toList());
            for (int i = 0; i < kinds.size(); i++) {
                int x = left + 19 + (i % 5) * 82;
                int y = top + 68 + (i / 5) * 72;
                if (inside(mouseX, mouseY, x, y, 74, 58)) {
                    selectedKind = kinds.get(i).id();
                    recipeIndexPage = 0;
                    view = View.RECIPE_INDEX;
                    playTurn();
                    rebuildWidgets();
                    return true;
                }
            }
        }
        if (view == View.RECIPE_INDEX && button == 0) {
            List<RecipeEntry> entries = filteredRecipeEntries();
            int first = recipeIndexPage * RECIPE_ENTRIES_PER_PAGE;
            for (int cell = 0; cell < RECIPE_ENTRIES_PER_PAGE; cell++) {
                int index = first + cell;
                if (index >= entries.size()) break;
                int x = left + 20 + (cell % 6) * 68;
                int y = top + 61 + (cell / 6) * 49;
                if (inside(mouseX, mouseY, x, y, 62, 42)) {
                    selectedRecipeProduct = entries.get(index).result.copy();
                    recipeDetailPage = 0;
                    view = View.RECIPE_DETAIL;
                    playTurn();
                    rebuildWidgets();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** 章节正文：鼠标滚轮上下滑，长页也能看全。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (view == View.CHAPTER && chapterScrollMax > 0) {
            chapterScroll = Mth.clamp(chapterScroll - scrollY * 20.0, 0, chapterScrollMax);
            return true;
        }
        if (view == View.CHAPTER_INDEX && chapterIndexScrollMax > 0) {
            chapterIndexScroll = Mth.clamp(chapterIndexScroll - scrollY * 20.0, 0, chapterIndexScrollMax);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!hoveredStack.isEmpty()) graphics.renderTooltip(font, hoveredStack, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private record GuidePage(String id, String titleKey, int lines, String linePrefix) { }
    private record Chapter(String id, List<Integer> pages, Item icon) { }
    private record ProductCategory(String id, Item icon) { }
    private record RecipeKind(String id, Item icon) { }
    private record GuideIngredient(List<ItemStack> choices) { }
    private record GuideRecipe(ItemStack result, List<GuideIngredient> ingredients, int width, int height,
                               String kind, String category, String descriptionType, Component note, String sourceId,
                               List<ItemStack> byproducts) {
        private GuideRecipe(ItemStack result, List<GuideIngredient> ingredients, int width, int height,
                            String kind, String category, String descriptionType, Component note, String sourceId) {
            this(result, ingredients, width, height, kind, category, descriptionType, note, sourceId, List.of());
        }
    }

    private static final class RecipeEntry {
        private final ItemStack result;
        private final List<GuideRecipe> recipes = new ArrayList<>();

        private RecipeEntry(ItemStack result) { this.result = result; }
    }
}
