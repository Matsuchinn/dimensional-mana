package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ManaCommands {
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dimensional_mana")
                .executes(context -> showHelp(context.getSource()))
                .then(Commands.literal("help")
                        .executes(context -> showHelp(context.getSource())))
                .then(Commands.literal("status")
                        .executes(context -> showStatus(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(context -> showStatus(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("combat")
                        .executes(context -> showCombat(context.getSource()))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(context -> showCombat(context.getSource(),
                                        EntityArgument.getEntity(context, "target")))))
                .then(mobCommand())
                .then(Commands.literal("mana")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1_000_000_000.0))
                                        .executes(context -> setMana(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                DoubleArgumentType.getDouble(context, "value"))))))
                .then(Commands.literal("battle_qi")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100_000_000))
                                        .executes(context -> setBattleQi(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                IntegerArgumentType.getInteger(context, "value"))))))
                .then(Commands.literal("max_mana")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0, 100_000_000.0))
                                        .executes(context -> setMaxMana(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                DoubleArgumentType.getDouble(context, "value"))))))
                .then(Commands.literal("max_battle_qi")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(1, 100_000_000))
                                        .executes(context -> setMaxBattleQi(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                IntegerArgumentType.getInteger(context, "value"))))))
                .then(debugPlayerCommand("unlock_spells", ManaCommands::unlockSpells))
                .then(debugPlayerCommand("unlock_elements", ManaCommands::unlockElements))
                .then(debugPlayerCommand("max_skills", ManaCommands::maxSkills))
                .then(debugPlayerCommand("unlock_all", ManaCommands::unlockAll))
                .then(cooldownCommand())
                .then(manaCostCommand())
                .then(spellCritCommand()));
    }

    /** /dimensional_mana cooldown on|off|reset|status [玩家]：开关技能冷却。 */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> cooldownCommand() {
        return Commands.literal("cooldown")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> cooldownStatus(context.getSource(), context.getSource().getPlayerOrException()))
                .then(Commands.literal("status")
                        .executes(context -> cooldownStatus(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> cooldownStatus(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("on")
                        .executes(context -> setGlobalCooldown(context.getSource(), true))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> setPlayerCooldown(context.getSource(),
                                        EntityArgument.getPlayer(context, "player"), true))))
                .then(Commands.literal("off")
                        .executes(context -> setGlobalCooldown(context.getSource(), false))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> setPlayerCooldown(context.getSource(),
                                        EntityArgument.getPlayer(context, "player"), false))))
                .then(Commands.literal("reset")
                        .executes(context -> resetCooldown(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> resetCooldown(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))));
    }

    /** /dimensional_mana manacost on|off|status [玩家]：开关施法的魔素消耗。 */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> manaCostCommand() {
        return Commands.literal("manacost")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> manaCostStatus(context.getSource(), context.getSource().getPlayerOrException()))
                .then(Commands.literal("status")
                        .executes(context -> manaCostStatus(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> manaCostStatus(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("on")
                        .executes(context -> setGlobalManaCost(context.getSource(), true))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> setPlayerManaCost(context.getSource(),
                                        EntityArgument.getPlayer(context, "player"), true))))
                .then(Commands.literal("off")
                        .executes(context -> setGlobalManaCost(context.getSource(), false))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> setPlayerManaCost(context.getSource(),
                                        EntityArgument.getPlayer(context, "player"), false))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> debugPlayerCommand(
            String name, DebugAction action) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> action.run(context.getSource(), context.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> action.run(context.getSource(), EntityArgument.getPlayer(context, "player"))));
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.help.title")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana status [玩家]")
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana mana <玩家> <数值>")
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana battle_qi <玩家> <数值>")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana max_mana <玩家> <数值>")
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana max_battle_qi <玩家> <数值>")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana unlock_spells|unlock_elements|max_skills|unlock_all [玩家]")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana cooldown on|off|reset|status [玩家]")
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana manacost on|off|status [玩家]")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana spell_crit set|clear|status [玩家] [百分比]")
                .withStyle(ChatFormatting.RED), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana combat [生物]")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana mob list|show|set|remove|reset|refresh")
                .withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.help.admin")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int showStatus(CommandSourceStack source, ServerPlayer player) {
        ManaData.Snapshot data = ManaData.get(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.status",
                player.getDisplayName(), format(data.mana()), format(data.maxMana()), data.battleQi(), data.maxBattleQi()), false);
        return 1;
    }

    // ------------------------------------------------------------ 战斗结算自检

    /** 准心 12 格内对着的生物；没有就返回 null。 */
    private static int showCombat(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.dimensional_mana.combat.no_target"));
            return 0;
        }
        net.minecraft.world.phys.HitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil
                .getHitResultOnViewVector(player, entity -> entity instanceof LivingEntity, 12.0);
        if (!(hit instanceof net.minecraft.world.phys.EntityHitResult entityHit)
                || !(entityHit.getEntity() instanceof LivingEntity living)) {
            source.sendFailure(Component.translatable("command.dimensional_mana.combat.no_target"));
            return 0;
        }
        return showCombat(source, living);
    }

    private static int showCombat(CommandSourceStack source, net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            source.sendFailure(Component.translatable("command.dimensional_mana.combat.no_target"));
            return 0;
        }
        return showCombat(source, living);
    }

    /**
     * /dimensional_mana combat：把一只生物的斗气减伤与元素抗性打在聊天栏里。
     * 斗气减伤是第一顺位、抗性在它之后，这样能直接看到两步各自扣掉多少。
     */
    private static int showCombat(CommandSourceStack source, LivingEntity target) {
        ManaData.Snapshot data = ManaData.get(target);
        double reduction = ManaEvents.battleQiReduction(target);
        double sample = 20.0;
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.combat.header",
                target.getDisplayName(), format(target.getHealth()), format(target.getMaxHealth()),
                data.battleQi(), data.maxBattleQi()).withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.combat.battle_qi",
                percent(reduction), percent(ManaConfig.BATTLE_QI_REDUCTION_CAP.get()),
                format(sample * (1.0 - reduction))).withStyle(ChatFormatting.GOLD), false);
        double damagePerQi = ManaConfig.BASE_BATTLE_QI_COST.get() <= 0.0 ? 0.0
                : ManaConfig.DAMAGE_PER_BATTLE_QI_COST.get() / ManaConfig.BASE_BATTLE_QI_COST.get();
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.combat.cost",
                format(damagePerQi), ManaConfig.MIN_BATTLE_QI_COST.get()).withStyle(ChatFormatting.GRAY), false);

        MobProfile profile = MobProfile.of(target.getType());
        if (profile == null) {
            source.sendSuccess(() -> Component.translatable("command.dimensional_mana.combat.no_profile")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return 1;
        }
        if (!MobCombatSystem.resistancesActive(target)) {
            source.sendSuccess(() -> Component.translatable("command.dimensional_mana.combat.inactive")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return 1;
        }
        java.util.List<Component> entries = new java.util.ArrayList<>();
        for (CombatElement element : CombatElement.values()) {
            resistanceEntry(entries, Component.translatable(element.translationKey()), profile.resistance(element));
        }
        resistanceEntry(entries, Component.translatable("hud.dimensional_mana.appraisal.trait.physical"),
                profile.typeResistance("physical"));
        resistanceEntry(entries, Component.translatable("hud.dimensional_mana.appraisal.trait.explosion"),
                profile.typeResistance("explosion"));
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.dimensional_mana.combat.no_profile")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return 1;
        }
        Component joined = entries.get(0);
        for (int i = 1; i < entries.size(); i++) {
            joined = Component.literal("")
                    .append(joined).append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(entries.get(i));
        }
        final Component line = joined;
        source.sendSuccess(() -> Component.empty()
                .append(Component.translatable("command.dimensional_mana.combat.resist_label")
                        .withStyle(ChatFormatting.AQUA))
                .append(line), false);
        // 空手一下只有 1 点基础伤害，护甲会先把它砍到 0.2 左右，斗气和抗性再各乘一次；
        // 最终只有零点几点时，血条/伤害数字会四舍五入成 1，很容易让人以为减伤没生效。
        // 这里把完整的结算链算给玩家看，直接确认斗气与物理抗性到底有没有参与。
        DamageSource physical = target.damageSources().mobAttack(target);
        float afterQi = (float)(1.0 - reduction);
        float armorToughness = target.getAttributes().hasAttribute(Attributes.ARMOR_TOUGHNESS)
                ? (float)target.getAttributeValue(Attributes.ARMOR_TOUGHNESS) : 0.0F;
        float afterArmor = CombatRules.getDamageAfterAbsorb(target, afterQi, physical,
                target.getArmorValue(), armorToughness);
        float afterResist = afterArmor * (float)MobCombatSystem.damageMultiplier(target, physical);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.combat.simulation",
                format(afterQi), format(afterArmor), format(afterResist))
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    /** 抗性为正写成「抗性」，为负写成「易伤」，0 直接跳过。 */
    private static void resistanceEntry(java.util.List<Component> entries, Component name, double value) {
        if (value == 0.0) return;
        entries.add(Component.translatable(value > 0.0
                ? "command.dimensional_mana.combat.resist_entry"
                : "command.dimensional_mana.combat.vulnerable_entry", name, resistancePercent(Math.abs(value)))
                .withStyle(value > 0.0 ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    /** 百分比先格式化成字符串再交给翻译：语言文件只认 %s。 */
    private static String percent(double ratio) {
        return String.format(java.util.Locale.ROOT, "%.2f%%", ratio * 100.0);
    }

    private static String resistancePercent(double ratio) {
        return String.format(java.util.Locale.ROOT, "%.0f%%", ratio * 100.0);
    }

    // ------------------------------------------------------------ 生物专配（profiles 列表）

    /**
     * /dimensional_mana mob：在游戏里看和改 special_mobs / resistances 两条配置列表。
     *
     * <p>之所以单独做一套指令，是因为游戏内配置界面的输入框只放得下 32 个字符，
     * 稍微复杂的生物一行就写不下；走指令没有字数限制，改完还会直接写回配置文件。</p>
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> mobCommand() {
        return Commands.literal("mob")
                .executes(context -> mobHelp(context.getSource()))
                .then(Commands.literal("list")
                        .executes(context -> mobList(context.getSource())))
                .then(Commands.literal("show")
                        .then(Commands.argument("mob", ResourceLocationArgument.id())
                                .executes(context -> mobShow(context.getSource(),
                                        ResourceLocationArgument.getId(context, "mob")))))
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("mob", ResourceLocationArgument.id())
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .then(Commands.argument("value",
                                                        DoubleArgumentType.doubleArg(-1_000_000_000.0, 1_000_000_000.0))
                                                .executes(context -> mobSet(context.getSource(),
                                                        ResourceLocationArgument.getId(context, "mob"),
                                                        StringArgumentType.getString(context, "key"),
                                                        DoubleArgumentType.getDouble(context, "value")))))))
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("mob", ResourceLocationArgument.id())
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .executes(context -> mobRemove(context.getSource(),
                                                ResourceLocationArgument.getId(context, "mob"),
                                                StringArgumentType.getString(context, "key"))))))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("mob", ResourceLocationArgument.id())
                                .executes(context -> mobReset(context.getSource(),
                                        ResourceLocationArgument.getId(context, "mob")))))
                .then(Commands.literal("refresh")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> mobRefresh(context.getSource())))
                .then(Commands.literal("compact")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> mobCompact(context.getSource())));
    }

    private static int mobHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.help.title")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        for (String line : new String[]{
                "/dimensional_mana mob list",
                "/dimensional_mana mob show <生物>",
                "/dimensional_mana mob set <生物> <键> <数值>",
                "/dimensional_mana mob remove <生物> <键>",
                "/dimensional_mana mob reset <生物>",
                "/dimensional_mana mob refresh",
                "/dimensional_mana mob compact"}) {
            source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.AQUA), false);
        }
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.help.keys")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int mobList(CommandSourceStack source) {
        java.util.Set<String> ids = MobProfile.configuredIds();
        if (ids.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.list_empty")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        String joined = String.join(", ", ids);
        int size = ids.size();
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.list", size, joined)
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int mobShow(CommandSourceStack source, ResourceLocation id) {
        String mobId = MobProfile.normalizeId(id.toString());
        MobProfile profile = MobProfile.byId(mobId);
        if (profile == null) {
            source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.no_profile", mobId)
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.title", mobId)
                .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.stats",
                statValue(profile.mana()), statValue(profile.battleQi()),
                statValue(profile.manaRegen()), statValue(profile.battleQiRegen()))
                .withStyle(ChatFormatting.AQUA), false);
        java.util.List<Component> entries = new java.util.ArrayList<>();
        for (CombatElement element : CombatElement.values()) {
            resistanceEntry(entries, Component.translatable(element.translationKey()), profile.resistance(element));
        }
        resistanceEntry(entries, Component.translatable("hud.dimensional_mana.appraisal.trait.physical"),
                profile.typeResistance("physical"));
        resistanceEntry(entries, Component.translatable("hud.dimensional_mana.appraisal.trait.explosion"),
                profile.typeResistance("explosion"));
        resistanceEntry(entries, Component.translatable("hud.dimensional_mana.appraisal.trait.magic_crit"),
                profile.special("magic_crit"));
        resistanceEntry(entries, Component.translatable("hud.dimensional_mana.appraisal.trait.melt"),
                profile.special("melt"));
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.resist_none")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return 1;
        }
        Component joined = entries.get(0);
        for (int i = 1; i < entries.size(); i++) {
            joined = Component.literal("")
                    .append(joined).append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(entries.get(i));
        }
        final Component line = joined;
        source.sendSuccess(() -> Component.empty()
                .append(Component.translatable("command.dimensional_mana.mob.resist_label")
                        .withStyle(ChatFormatting.AQUA))
                .append(line), false);
        return 1;
    }

    /** 配置里没写的值显示成「按难度公式」，免得玩家以为被设成了 0。 */
    private static Component statValue(Double value) {
        return value == null
                ? Component.translatable("command.dimensional_mana.mob.value_default")
                : Component.literal(format(value));
    }

    private static int mobSet(CommandSourceStack source, ResourceLocation id, String key, double value) {
        if (!MobCombatConfig.SPEC.isLoaded()) return configNotReady(source);
        if (!MobProfile.knownKey(key)) {
            source.sendFailure(Component.translatable("command.dimensional_mana.mob.bad_key", key));
            return 0;
        }
        String mobId = MobProfile.normalizeId(id.toString());
        boolean stat = MobProfile.isStatKey(key);
        String shortKey = MobProfile.shortKey(key);
        MobCombatConfig.writeMobValue(stat, mobId, shortKey, value);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.set",
                mobId, shortKey, format(value)).withStyle(ChatFormatting.GREEN), true);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.set_hint")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int mobRemove(CommandSourceStack source, ResourceLocation id, String key) {
        if (!MobCombatConfig.SPEC.isLoaded()) return configNotReady(source);
        if (!MobProfile.knownKey(key)) {
            source.sendFailure(Component.translatable("command.dimensional_mana.mob.bad_key", key));
            return 0;
        }
        String mobId = MobProfile.normalizeId(id.toString());
        boolean stat = MobProfile.isStatKey(key);
        String shortKey = MobProfile.shortKey(key);
        boolean removed = MobCombatConfig.removeMobValue(stat, mobId, shortKey);
        source.sendSuccess(() -> Component.translatable(removed
                ? "command.dimensional_mana.mob.remove"
                : "command.dimensional_mana.mob.remove_none", mobId, shortKey)
                .withStyle(removed ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        return removed ? 1 : 0;
    }

    private static int mobReset(CommandSourceStack source, ResourceLocation id) {
        if (!MobCombatConfig.SPEC.isLoaded()) return configNotReady(source);
        String mobId = MobProfile.normalizeId(id.toString());
        MobCombatConfig.resetMob(mobId);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.reset", mobId)
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    /** 配置改完之后让已经刷出来的生物立刻按新数值重算，不用重开存档。 */
    private static int mobRefresh(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        int count = 0;
        for (Object candidate : level.getAllEntities()) {
            if (!(candidate instanceof Mob mob)) continue;
            if (!MobCombatSystem.isManagedMob(mob)) continue;
            mob.getPersistentData().remove(ManaData.ROOT_TAG);
            ManaData.init(mob);
            count++;
        }
        int refreshed = count;
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.refresh", refreshed)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int configNotReady(CommandSourceStack source) {
        source.sendFailure(Component.translatable("command.dimensional_mana.mob.not_loaded"));
        return 0;
    }

    /** 老配置里的超长行在游戏内配置界面里改不动，这里一次性拆成短行并保存。 */
    private static int mobCompact(CommandSourceStack source) {
        if (!MobCombatConfig.SPEC.isLoaded()) return configNotReady(source);
        int lists = MobCombatConfig.compactLists();
        source.sendSuccess(() -> Component.translatable(lists > 0
                ? "command.dimensional_mana.mob.compact"
                : "command.dimensional_mana.mob.compact_none", lists)
                .withStyle(lists > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        return 1;
    }

    private static int setMana(CommandSourceStack source, ServerPlayer player, double value) {
        ManaData.setMana(player, value, true);
        sync(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mana.success",
                player.getDisplayName(), format(ManaData.get(player).mana())), true);
        return 1;
    }

    private static int setBattleQi(CommandSourceStack source, ServerPlayer player, int value) {
        ManaData.setBattleQi(player, value);
        sync(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.battle_qi.success",
                player.getDisplayName(), ManaData.get(player).battleQi()), true);
        return 1;
    }

    private static int setMaxMana(CommandSourceStack source, ServerPlayer player, double value) {
        ManaData.setMaxMana(player, value);
        sync(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.max_mana.success",
                player.getDisplayName(), format(value)), true);
        return 1;
    }

    private static int setMaxBattleQi(CommandSourceStack source, ServerPlayer player, int value) {
        ManaData.setMaxBattleQi(player, value);
        sync(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.max_battle_qi.success",
                player.getDisplayName(), ManaData.get(player).maxBattleQi()), true);
        return 1;
    }

    private static int unlockSpells(CommandSourceStack source, ServerPlayer player) {
        SpellCasting.clearTransientState(player);
        SpellData.unlockAll(player);
        return debugSuccess(source, player, "command.dimensional_mana.unlock_spells.success");
    }

    private static int unlockElements(CommandSourceStack source, ServerPlayer player) {
        ElementTalentData.unlockAll(player);
        return debugSuccess(source, player, "command.dimensional_mana.unlock_elements.success");
    }

    private static int maxSkills(CommandSourceStack source, ServerPlayer player) {
        MagicProgression.maxAllSkills(player);
        ProgressSyncPayload.send(player);
        sync(player);
        return debugSuccess(source, player, "command.dimensional_mana.max_skills.success");
    }

    private static int unlockAll(CommandSourceStack source, ServerPlayer player) {
        ElementTalentData.unlockAll(player);
        SpellCasting.clearTransientState(player);
        SpellData.unlockAll(player);
        MagicProgression.maxAllSkills(player);
        // 斗气侧：等级、六项技能全满，三项能力解锁并放好轮盘槽。
        // 必须在补斗气之前做——「斗气强化」满级会把斗气上限抬高，后补才能补到新上限。
        QiProgression.maxAll(player);
        ManaData.setMana(player, ManaData.get(player).maxMana(), false);
        ManaData.setBattleQi(player, ManaData.get(player).maxBattleQi());
        ProgressSyncPayload.send(player);
        sync(player);
        return debugSuccess(source, player, "command.dimensional_mana.unlock_all.success");
    }

    private static int setGlobalCooldown(CommandSourceStack source, boolean enabled) {
        ManaCheats.setGlobalCooldown(enabled);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "command.dimensional_mana.cooldown.global_on"
                : "command.dimensional_mana.cooldown.global_off").withStyle(ChatFormatting.AQUA), true);
        resyncAll(source);
        return 1;
    }

    private static int setPlayerCooldown(CommandSourceStack source, ServerPlayer player, boolean enabled) {
        ManaCheats.setPlayerCooldown(player, enabled);
        SpellSyncPayload.send(player);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "command.dimensional_mana.cooldown.player_on"
                : "command.dimensional_mana.cooldown.player_off", player.getDisplayName())
                .withStyle(ChatFormatting.AQUA), true);
        if (enabled && !ManaCheats.globalCooldown()) hintGlobalOff(source);
        return 1;
    }

    private static int resetCooldown(CommandSourceStack source, ServerPlayer player) {
        SpellCasting.clearCooldowns(player);
        SpellSyncPayload.send(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.cooldown.reset",
                player.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int cooldownStatus(CommandSourceStack source, ServerPlayer player) {
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.cooldown.status",
                player.getDisplayName(), toggle(ManaCheats.cooldownEnabled(player)),
                toggle(ManaCheats.globalCooldown())).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int setGlobalManaCost(CommandSourceStack source, boolean enabled) {
        ManaCheats.setGlobalManaCost(enabled);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "command.dimensional_mana.manacost.global_on"
                : "command.dimensional_mana.manacost.global_off").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return 1;
    }

    private static int setPlayerManaCost(CommandSourceStack source, ServerPlayer player, boolean enabled) {
        ManaCheats.setPlayerManaCost(player, enabled);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "command.dimensional_mana.manacost.player_on"
                : "command.dimensional_mana.manacost.player_off", player.getDisplayName())
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        if (enabled && !ManaCheats.globalManaCost()) hintGlobalOff(source);
        return 1;
    }

    /** 全局还是关闭状态时提醒一句，避免管理员以为单开没生效。 */
    private static void hintGlobalOff(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.hint.global_off")
                .withStyle(ChatFormatting.GRAY), false);
    }

    private static int manaCostStatus(CommandSourceStack source, ServerPlayer player) {
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.manacost.status",
                player.getDisplayName(), toggle(ManaCheats.manaCostEnabled(player)),
                toggle(ManaCheats.globalManaCost())).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return 1;
    }

    /** /dimensional_mana spell_crit set|clear|status [玩家]：作弊改法术暴击率，方便验证暴击表现。 */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> spellCritCommand() {
        return Commands.literal("spell_crit")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> spellCritStatus(context.getSource(), context.getSource().getPlayerOrException()))
                .then(Commands.literal("status")
                        .executes(context -> spellCritStatus(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> spellCritStatus(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                                        .executes(context -> setSpellCrit(context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                DoubleArgumentType.getDouble(context, "percent"))))))
                .then(Commands.literal("clear")
                        .executes(context -> clearSpellCrit(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> clearSpellCrit(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))));
    }

    private static int setSpellCrit(CommandSourceStack source, ServerPlayer player, double percent) {
        SpellAttributes.setCheatCritChance(player, percent / 100.0);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.spell_crit.set",
                player.getDisplayName(), critPercent(SpellAttributes.critChance(player)))
                .withStyle(ChatFormatting.RED), true);
        return 1;
    }

    private static int clearSpellCrit(CommandSourceStack source, ServerPlayer player) {
        boolean removed = SpellAttributes.clearCheatCritChance(player);
        source.sendSuccess(() -> Component.translatable(removed
                ? "command.dimensional_mana.spell_crit.clear"
                : "command.dimensional_mana.spell_crit.clear_none", player.getDisplayName())
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int spellCritStatus(CommandSourceStack source, ServerPlayer player) {
        double total = SpellAttributes.critChance(player);
        double cheat = SpellAttributes.cheatCritChance(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.spell_crit.status",
                player.getDisplayName(), critPercent(total), critPercent(total - cheat), critPercent(cheat))
                .withStyle(ChatFormatting.RED), false);
        return 1;
    }

    /** 百分比先格式化成字符串再交给翻译：语言文件只认 %s，写 %.1f 会让整行退回未替换的原文。 */
    private static String critPercent(double ratio) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", ratio * 100.0);
    }

    private static Component toggle(boolean enabled) {
        return Component.translatable(enabled
                ? "command.dimensional_mana.toggle.on" : "command.dimensional_mana.toggle.off");
    }

    /** 全局开关改动后同步所有在线玩家的 HUD，避免冷却数字残留。 */
    private static void resyncAll(CommandSourceStack source) {
        if (source.getServer() == null) return;
        for (ServerPlayer online : source.getServer().getPlayerList().getPlayers()) SpellSyncPayload.send(online);
    }

    private static int debugSuccess(CommandSourceStack source, ServerPlayer player, String key) {
        source.sendSuccess(() -> Component.translatable(key, player.getDisplayName()), true);
        return 1;
    }

    private static void sync(ServerPlayer player) {
        ManaData.Snapshot data = ManaData.get(player);
        PacketDistributor.sendToPlayer(player, new ManaSyncPayload(
                data.mana(), data.maxMana(), data.battleQi(), data.maxBattleQi(), data.exhaustionTicks() > 0,
                ManaConfig.OVERCHARGE_SAFE_RATIO.get()));
    }

    @FunctionalInterface
    private interface DebugAction { int run(CommandSourceStack source, ServerPlayer player); }

    private static String format(double value) {
        return value == Math.rint(value) ? String.format("%.0f", value) : String.format("%.2f", value);
    }

    private ManaCommands() {}
}
