package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.GatherEffectScreenTooltipsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientModEvents {
    private static final ResourceLocation MANA_HUD = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "mana_hud");
    private static final KeyMapping OPEN_CODEX = new KeyMapping("key.dimensional_mana.open_codex",
            InputConstants.Type.KEYSYM, InputConstants.KEY_K, "key.categories.dimensional_mana");
    private static final KeyMapping SPELL_WHEEL = new KeyMapping("key.dimensional_mana.spell_wheel",
            InputConstants.Type.MOUSE, 3, "key.categories.dimensional_mana");
    private static final KeyMapping CAST_SPELL = new KeyMapping("key.dimensional_mana.cast_spell",
            InputConstants.Type.MOUSE, 4, "key.categories.dimensional_mana");
    /** 主手持魔法大师披风时按 Shift+O 切换凝聚球功能（O 键可改绑）。 */
    private static final KeyMapping TOGGLE_ORB = new KeyMapping("key.dimensional_mana.toggle_orb",
            InputConstants.Type.KEYSYM, InputConstants.KEY_O, "key.categories.dimensional_mana");
    /** 静止不动时按住 Alt+Z 消耗魔素凝聚斗气（Z 键可改绑）。 */
    private static final KeyMapping CONDENSE_BATTLE_QI = new KeyMapping("key.dimensional_mana.condense_battle_qi",
            InputConstants.Type.KEYSYM, InputConstants.KEY_Z, "key.categories.dimensional_mana");
    /** 斗气技能轮盘：默认大小写锁定键（Caps Lock），按住选、松开即关（可改绑）。 */
    private static final KeyMapping QI_WHEEL = new KeyMapping("key.dimensional_mana.qi_wheel",
            InputConstants.Type.KEYSYM, InputConstants.KEY_CAPSLOCK, "key.categories.dimensional_mana");
    /** 点按 Alt 施放当前选中的斗气技能（可改绑）。 */
    private static final KeyMapping QI_CAST = new KeyMapping("key.dimensional_mana.qi_cast",
            InputConstants.Type.KEYSYM, InputConstants.KEY_LALT, "key.categories.dimensional_mana");
    /** 专业鉴定眼镜的主动扫描：手持任意物品或空手都能按（V 键可改绑）。 */
    private static final KeyMapping APPRAISAL_SCAN = new KeyMapping("key.dimensional_mana.appraisal_scan",
            InputConstants.Type.KEYSYM, InputConstants.KEY_V, "key.categories.dimensional_mana");
    /**
     * 一键停机：把正在跑的法阵全部关掉（X 键可改绑，原版没用 X，也不和本模组其它键冲突）。
     *
     * <p>属于「拍急停按钮」性质：不用对着阵、不用拿调试棒，按一下就全停，
     * 召唤物、维持费、红石 / 计时触发一起停下。具体关哪几个由服务端按登记的主人算。</p>
     */
    private static final KeyMapping STOP_ALL_CIRCLES = new KeyMapping("key.dimensional_mana.stop_all_circles",
            InputConstants.Type.KEYSYM, InputConstants.KEY_X, "key.categories.dimensional_mana");
    /**
     * 法阵码导入面板：随时按一下就能开，不必先摸出一张空白载体（P 键可改绑）。
     *
     * <p>之前只有「空白载体 + 潜行右键空气」这一个入口，对不准空气、手里拿错东西就开不出来，
     * 玩家会以为导入坏了。现在给一个固定键位，另外右键方块时也能开。</p>
     */
    private static final KeyMapping CODE_PANEL = new KeyMapping("key.dimensional_mana.code_panel",
            InputConstants.Type.KEYSYM, InputConstants.KEY_P, "key.categories.dimensional_mana");
    private static final ResourceLocation APPRAISAL_HUD = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "appraisal_hud");
    private static final ResourceLocation PRO_APPRAISAL_HUD = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "pro_appraisal_hud");
    private static final ResourceLocation REACTION_TEXT_HUD = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "reaction_text");
    private static final ResourceLocation RESONANCE_CORE_HUD = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "resonance_core_hud");
    private static final ResourceLocation RADIANT_FLASH_HUD = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "radiant_flash");
    private static final ResourceLocation PARRY_HUD = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "parry_hud");
    /** 招架姿态：右键按住多少刻、这一轮有没有把 begin 发出去。 */
    private static int parryHoldTicks;
    private static boolean parryStanceSent;
    /** 姿态下左键是否还按着：只在「按下去的那一下」发一次弹反，按住不会连发。 */
    private static boolean parryAttackWasDown;
    /** 风狂期间被临时压低的鼠标灵敏度原值；状态结束后必须还原。 */
    private static Double windFrenzySensitivity;
    /** 点按 Alt 施放斗气的边沿检测：上一刻是否按着、这一轮 Alt 有没有被组合键占用。 */
    private static boolean qiCastHeld;
    private static boolean qiCastSuppressed;
    /** 老版本把斗气轮盘默认绑在鼠标键 5，这里只迁移一次到大小写锁定键。 */
    private static boolean qiWheelDefaultMigrated;
    /** 断魂鸣弦按住追加的节流：十刻一颗，刚好赶得上服务器每十刻一次的状态同步。 */
    private static final int SONIC_HOLD_INTERVAL = 10;
    private static int sonicHoldAt;

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientModEvents::registerHud);
        modBus.addListener(ClientModEvents::registerScreens);
        modBus.addListener(ClientModEvents::registerEntityRenderers);
        modBus.addListener(ClientModEvents::registerLayerDefinitions);
        modBus.addListener(ClientModEvents::registerKeys);
        modBus.addListener(ClientModEvents::registerFluidRendering);
        modBus.addListener(ClientModEvents::registerMagicModels);
        modBus.addListener(ClientModEvents::mapMagicModels);
        modBus.addListener(ClientModEvents::registerItemColors);
        modBus.addListener(ClientModEvents::clientSetup);
        NeoForge.EVENT_BUS.addListener(ClientModEvents::addEffectTooltips);
        NeoForge.EVENT_BUS.addListener(ClientModEvents::clientTick);
        NeoForge.EVENT_BUS.addListener(ClientModEvents::interactionKey);
        NeoForge.EVENT_BUS.addListener(ClientModEvents::movementInput);
        NeoForge.EVENT_BUS.addListener(ClientSpellVisuals::tick);
        NeoForge.EVENT_BUS.addListener(ClientSpellVisuals::render);
        NeoForge.EVENT_BUS.addListener(ClientParryVisuals::tick);
        NeoForge.EVENT_BUS.addListener(ClientParryVisuals::cameraAngles);
        NeoForge.EVENT_BUS.addListener(ClientParryVisuals::renderWorld);
        // 招架姿势：第三人称把手臂举成举盾的样子，第一人称把刀抬到胸前横住。
        NeoForge.EVENT_BUS.addListener(ClientParryVisuals::onRenderPlayer);
        NeoForge.EVENT_BUS.addListener(ClientParryVisuals::onRenderHand);
        NeoForge.EVENT_BUS.addListener(ClientReactionText::tick);
        NeoForge.EVENT_BUS.addListener(ClientReactionText::renderWorld);
        NeoForge.EVENT_BUS.addListener(ClientFrozenVisuals::tick);
        NeoForge.EVENT_BUS.addListener(ClientManaSenseState::tick);
        NeoForge.EVENT_BUS.addListener(ClientManaRadarState::tick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event)
                -> {
            ClientResonanceCoreState.clear();
            ClientMagicToggleState.clear();
            ClientParryVisuals.clear();
            // 退出世界时把鉴定 HUD 的数据清干净，免得下次进服先闪一帧上一个存档的目标。
            ClientProAppraisalState.clear();
            ClientQiState.clear();
            ClientMagicCirclePayloadHandler.clearPendingRemote();
            qiCastHeld = false;
            qiCastSuppressed = false;
        });
        NeoForge.EVENT_BUS.addListener(ClientFrozenVisuals::render);
        NeoForge.EVENT_BUS.addListener(ClientManaSenseState::render);
        NeoForge.EVENT_BUS.addListener(ClientManaRadarState::render);
        NeoForge.EVENT_BUS.addListener(InventoryGlowEffects::render);
    }

    private static ModelResourceLocation inventoryModel(String id) {
        return ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, id));
    }

    /** RegisterAdditional 加载的是 models 下的完整路径，且侧载模型必须使用 standalone 变体。 */
    private static ModelResourceLocation standaloneItemModel(String id) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "item/" + id));
    }

    /** 客户端初始化：登记物品图标的帧动画属性，时间点必须早于模型烘焙。 */
    private static void clientSetup(FMLClientSetupEvent event) {
        ClientItemAnimations.register(event);
        event.enqueueWork(ClientModEvents::registerGoblinItemProperties);
    }

    /**
     * 破损弓 / 圆盾的模型切换条件。
     *
     * <p>原版只给 {@code Items.BOW} 和 {@code Items.SHIELD} 登记了 {@code pulling} / {@code pull} /
     * {@code blocking} 这几个属性，自定义物品必须自己再登记一遍，否则模型 JSON 里写了 overrides
     * 也永远不会切换——拉弓时看不到箭、举盾时看不到盾面就是这个原因。</p>
     */
    private static void registerGoblinItemProperties() {
        Item bow = GeneratedMod.MOD_ITEMS.get(RoughEquipment.BROKEN_BOW).get();
        ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pulling"),
                (stack, level, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
        ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pull"),
                (stack, level, entity, seed) -> {
                    if (entity == null || entity.getUseItem() != stack) return 0.0F;
                    return (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20.0F;
                });
        Item buckler = GeneratedMod.MOD_ITEMS.get(RoughEquipment.BUCKLER).get();
        ItemProperties.register(buckler, ResourceLocation.withDefaultNamespace("blocking"),
                (stack, level, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
    }

    private static void registerMagicModels(ModelEvent.RegisterAdditional event) {
        event.register(standaloneItemModel("magic_fragment_shared"));
        event.register(standaloneItemModel("school_fragment_shared"));
        event.register(standaloneItemModel("talent_scroll_shared"));
    }

    private static void mapMagicModels(ModelEvent.ModifyBakingResult event) {
        BakedModel elementFragment = event.getModels().get(standaloneItemModel("magic_fragment_shared"));
        BakedModel schoolFragment = event.getModels().get(standaloneItemModel("school_fragment_shared"));
        BakedModel scroll = event.getModels().get(standaloneItemModel("talent_scroll_shared"));
        if (elementFragment == null || schoolFragment == null || scroll == null) return;
        for (String id : GeneratedMod.MAGIC_ITEM_IDS) {
            if (id.endsWith("_school_fragment")) event.getModels().put(inventoryModel(id), schoolFragment);
            else if (id.endsWith("_fragment")) event.getModels().put(inventoryModel(id), elementFragment);
            else if (id.endsWith("_talent_scroll")) event.getModels().put(inventoryModel(id), scroll);
        }
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CODEX);
        event.register(SPELL_WHEEL);
        event.register(CAST_SPELL);
        event.register(TOGGLE_ORB);
        event.register(CONDENSE_BATTLE_QI);
        event.register(APPRAISAL_SCAN);
        event.register(QI_WHEEL);
        event.register(QI_CAST);
        event.register(STOP_ALL_CIRCLES);
        event.register(CODE_PANEL);
    }

    /** 使用当前玩家配置的按键判断，而不是写死左 Alt。 */
    public static boolean matchesSpellWheelKey(int keyCode, int scanCode) {
        return SPELL_WHEEL.matches(keyCode, scanCode);
    }

    /** 同样支持玩家把法术轮盘改绑到鼠标按键。 */
    public static boolean matchesSpellWheelMouse(int button) {
        return SPELL_WHEEL.matchesMouse(button);
    }

    /** 轮盘关闭时清掉自身按键状态和积压点击，不影响玩家其他按键。 */
    public static void releaseSpellWheelInput() {
        SPELL_WHEEL.setDown(false);
        while (SPELL_WHEEL.consumeClick()) {
            // 清空界面打开期间可能由按键重复或整合包转发产生的积压点击。
        }
    }

    /** 斗气轮盘可能被玩家改绑到鼠标键，那时键盘释放事件不会来，靠这两个方法判断真实松开。 */
    public static boolean matchesQiWheelKey(int keyCode, int scanCode) {
        return QI_WHEEL.matches(keyCode, scanCode);
    }

    public static boolean matchesQiWheelMouse(int button) {
        return QI_WHEEL.matchesMouse(button);
    }

    /** 轮盘关闭时清掉按键状态与积压点击。 */
    public static void releaseQiWheelInput() {
        QI_WHEEL.setDown(false);
        while (QI_WHEEL.consumeClick()) {
            // 清空界面打开期间可能积压的点击。
        }
    }

    /**
     * 老存档的按键表里还留着「鼠标键 5」这个旧默认值，而按键默认值只在 options.txt 没有
     * 记录时才生效。所以启动后核对一次：还停在旧默认值就换成大小写锁定键并存回配置，
     * 玩家自己改过的按键（不是鼠标键 5）不会被碰。
     */
    private static void migrateQiWheelDefault(Minecraft minecraft) {
        if (qiWheelDefaultMigrated) return;
        qiWheelDefaultMigrated = true;
        InputConstants.Key legacy = InputConstants.Type.MOUSE.getOrCreate(5);
        if (!QI_WHEEL.getKey().equals(legacy)) return;
        QI_WHEEL.setKey(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_CAPSLOCK));
        KeyMapping.resetMapping();
        minecraft.options.save();
    }

    private static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        migrateQiWheelDefault(minecraft);
        ClientSpellState.clientTick();
        syncWindFrenzySensitivity(minecraft);
        while (OPEN_CODEX.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null && ArcaneCodexItem.hasAccess(minecraft.player)) {
                minecraft.player.playSound(GeneratedMod.CODEX_SPACE_OPEN.get(), 0.85F, 1.0F);
                minecraft.setScreen(ClientElementTalentState.chosen()
                        ? new ArcaneCodexScreen() : new ElementTalentScreen(false));
            } else if (minecraft.player != null && minecraft.screen == null) {
                minecraft.player.displayClientMessage(Component.translatable("message.dimensional_mana.arcane_codex_required")
                        .withStyle(ChatFormatting.RED), true);
            }
        }
        while (SPELL_WHEEL.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) minecraft.setScreen(new SpellWheelScreen());
        }
        while (CAST_SPELL.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                if (ClientSpellState.targeting()) ClientSpellState.cancelTargetingLocally();
                PacketDistributor.sendToServer(new SpellActionPayload("cast", "", ClientSpellState.selectedSlot()));
            }
        }
        // Shift+O：主手拿着魔法大师披风时切换凝聚球功能。按键本身可改绑，Shift 是固定前提。
        while (TOGGLE_ORB.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null
                    && net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                PacketDistributor.sendToServer(new SpellActionPayload("toggle_orb", "", 0));
            }
        }
        while (QI_WHEEL.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) minecraft.setScreen(new QiWheelScreen());
        }
        qiCastInput(minecraft);
        sonicHoldAdd(minecraft);
        condenseBattleQiHold(minecraft);
        parryInput(minecraft);
        // 专业鉴定眼镜的扫描线小窗是纯客户端动画，每刻自己往前走一格。
        ClientProAppraisalState.tick();
        // V 键主动扫描：佩戴并由服务端复核魔素，客户端只管把请求发出去。
        while (APPRAISAL_SCAN.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                PacketDistributor.sendToServer(new SpellActionPayload("appraisal_scan", "", 0));
            }
        }
        // X 键一键停机：不用对着阵、不用拿调试棒，服务端按「登记在这个玩家名下的运行中锚点」全关。
        while (STOP_ALL_CIRCLES.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                PacketDistributor.sendToServer(MagicCircleNetActionPayload.stopAll());
            }
        }
        // P 键：随时打开法阵码导入面板（面板本身是纯客户端的，服务端只负责校验和发物品）。
        while (CODE_PANEL.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                ClientMagicCircleScreens.openCodeImport();
            }
        }
    }

    /**
     * 断魂鸣弦：按住 Alt + 左键持续追加鸣球。
     *
     * <p>原版的左键只在“按下的那一下”触发一次，按住不放不会重复，
     * 所以这里按刻轮询按键状态：按住期间每十刻补一颗，补满两颗（主球之外）就停。
     * 追加数量由服务器同步回来，不会因为反复请求而多扣魔素。</p>
     */
    private static void sonicHoldAdd(Minecraft minecraft) {
        if (sonicHoldAt > 0) sonicHoldAt--;
        if (minecraft.player == null || minecraft.screen != null || !ClientSpellState.sonicCharging()) {
            sonicHoldAt = 0;
            return;
        }
        if (!minecraft.options.keyAttack.isDown() || !net.minecraft.client.gui.screens.Screen.hasAltDown()
                || !minecraft.mouseHandler.isMouseGrabbed()) return;
        // 按住左键期间不让挖方块：这一整段输入是给追加鸣球用的，不能顺手把脚下方块啃掉。
        if (minecraft.gameMode != null && minecraft.gameMode.isDestroying()) minecraft.gameMode.stopDestroyBlock();
        if (ClientSpellState.soulChordExtras() >= SonicSpells.ORB_MAX - 1) return;
        if (sonicHoldAt > 0) return;
        sonicHoldAt = SONIC_HOLD_INTERVAL;
        PacketDistributor.sendToServer(new SpellActionPayload("sonic_add", "", 0));
    }

    /** 凝聚斗气时的发包节流：10 刻一次，和服务器的「每 10 刻 1 点」对齐。 */
    private static int condenseCooldown;

    /**
     * 静止不动时按住 Alt+Z 凝聚斗气：每 10 刻请求服务器扣 1 点魔素换 1 点斗气。
     * 这里只控制发起时机，真正的「是不是站着不动、有没有魔素」由服务端复核。
     */
    private static void condenseBattleQiHold(Minecraft minecraft) {
        if (condenseCooldown > 0) condenseCooldown--;
        var player = minecraft.player;
        if (player == null || minecraft.screen != null || !CONDENSE_BATTLE_QI.isDown()
                || !net.minecraft.client.gui.screens.Screen.hasAltDown()) {
            condenseCooldown = 0;
            return;
        }
        boolean standing = player.onGround() && !player.isInWaterOrBubble()
                && player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4;
        if (!standing) {
            condenseCooldown = 0;
            return;
        }
        if (condenseCooldown > 0) return;
        condenseCooldown = 10;
        PacketDistributor.sendToServer(new SpellActionPayload("condense_battle_qi", "", 0));
    }

    /**
     * 点按 Alt 施放斗气技能。
     *
     * <p>用「松开 Alt」的那一刻发包：按住 Alt 只是组合键的前缀（Alt+Z 凝聚斗气、Alt+左键追加法术、
     * Alt+大小写锁定键打开斗气轮盘），这些组合全都不会顺手丢一个技能出去。按下期间碰到任何一个
     * 组合键，这一轮 Alt 就直接作废，松开也不施放。</p>
     */
    private static void qiCastInput(Minecraft minecraft) {
        var player = minecraft.player;
        boolean eligible = player != null && minecraft.screen == null && minecraft.mouseHandler.isMouseGrabbed()
                && ClientSpellState.casting() == null && !ClientSpellState.targeting()
                && !NewSpellSystem.immobilized(player);
        boolean down = QI_CAST.isDown();
        if (down) {
            if (!qiCastHeld) qiCastSuppressed = !eligible;
            // 组合键占用：凝聚斗气、左/右键、法术轮盘、斗气轮盘。
            if (CONDENSE_BATTLE_QI.isDown() || minecraft.options.keyAttack.isDown()
                    || minecraft.options.keyUse.isDown() || SPELL_WHEEL.isDown() || QI_WHEEL.isDown()) {
                qiCastSuppressed = true;
            }
        } else if (qiCastHeld) {
            if (!qiCastSuppressed && eligible && ClientQiState.selected() != null) {
                PacketDistributor.sendToServer(new QiActionPayload("cast", "", 0));
            }
            qiCastSuppressed = false;
        }
        qiCastHeld = down;
    }

    /**
     * 斗气招架：主手剑、副手空时长按右键 5 刻进入招架姿态，松开或换手立刻退出。
     *
     * <p>进入/退出都只发一次请求，服务器自己也会每刻复核前置条件；
     * 客户端留着这个状态只是为了画镜头下沉和剑身光晕。</p>
     *
     * <p>姿态下按左键弹反，没有冷却：但只在按下去的那一下发一次，按住不放不会连发，
     * 也不会因为原版「按住左键打空就一直挥手」把剑抽成风车。</p>
     */
    private static void parryInput(Minecraft minecraft) {
        var player = minecraft.player;
        boolean eligible = player != null && minecraft.screen == null && minecraft.mouseHandler.isMouseGrabbed()
                && BattleQiParry.canParry(player)
                // 正在读条或选目标时不抢输入，交给施法流程。
                && ClientSpellState.casting() == null && !ClientSpellState.targeting();
        boolean holding = eligible && minecraft.options.keyUse.isDown();
        if (!holding) {
            parryHoldTicks = 0;
            parryAttackWasDown = false;
            if (parryStanceSent) {
                parryStanceSent = false;
                PacketDistributor.sendToServer(new SpellActionPayload("parry_end", "", 0));
            }
            ClientParryVisuals.setStance(false);
            return;
        }
        if (parryHoldTicks < BattleQiParry.STANCE_HOLD_TICKS) parryHoldTicks++;
        if (!parryStanceSent && parryHoldTicks >= BattleQiParry.STANCE_HOLD_TICKS) {
            parryStanceSent = true;
            ClientParryVisuals.setStance(true);
            PacketDistributor.sendToServer(new SpellActionPayload("parry_begin", "", 0));
        }
        boolean attackDown = minecraft.options.keyAttack.isDown();
        if (parryStanceSent) {
            if (attackDown && !parryAttackWasDown) {
                PacketDistributor.sendToServer(new SpellActionPayload("parry_attempt", "", 0));
                // 这一刀是玩家自己按出来的，放开挥刀锁让动作播出来。
                if (player != null) player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                ClientParryVisuals.allowSwing();
            }
            // 姿态下按住左键不许啃方块：这一整段输入是给弹反用的。
            if (attackDown && minecraft.gameMode != null && minecraft.gameMode.isDestroying()) {
                minecraft.gameMode.stopDestroyBlock();
            }
        }
        parryAttackWasDown = attackDown;
    }

    private static void movementInput(net.neoforged.neoforge.client.event.MovementInputUpdateEvent event) {
        if (!NewSpellSystem.immobilized(event.getEntity())) return;
        var input = event.getInput();
        input.forwardImpulse = 0; input.leftImpulse = 0;
        input.up = false; input.down = false; input.left = false; input.right = false;
        input.jumping = false; input.shiftKeyDown = false;
        event.getEntity().setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }
    private static void interactionKey(InputEvent.InteractionKeyMappingTriggered event) {
        var player = Minecraft.getInstance().player;
        if (player != null && NewSpellSystem.immobilized(player)) {
            event.setCanceled(true); event.setSwingHand(false); return;
        }
        // 招架姿态下：左键改成弹反，不再挥砍或挖方块；右键的使用逻辑一并拦掉。
        // 弹反请求在 parryInput 里按「按下那一下」发，这里只负责把原版动作吃掉，
        // 连手臂都不用抬——否则按住左键会一直抽刀。
        if (ClientParryVisuals.stance() && !ClientSpellState.targeting()) {
            if (event.isAttack()) {
                event.setCanceled(true);
                event.setSwingHand(false);
                return;
            }
            if (event.isUseItem()) {
                event.setCanceled(true);
                event.setSwingHand(false);
                return;
            }
        }
        // 天雷滚滚前摇期间：按住 Alt 左键追加雷球，服务器扣 555 魔素并校验两次上限。
        if (event.isAttack() && ClientSpellState.tempestCharging()
                && net.minecraft.client.gui.screens.Screen.hasAltDown()) {
            PacketDistributor.sendToServer(new SpellActionPayload("tempest_add", "", 0));
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        // 万法千陨召唤期间：按住 Alt 左键追加石块，服务器扣 85 魔素并校验 48 块上限。
        if (event.isAttack() && ClientSpellState.meteorSummoning()
                && net.minecraft.client.gui.screens.Screen.hasAltDown()) {
            PacketDistributor.sendToServer(new SpellActionPayload("meteor_add", "", 0));
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        // 零下领域叠层期间：按住 Alt 左键继续叠层，服务器扣 144 魔素并校验 12 层上限。
        if (event.isAttack() && ClientSpellState.subzeroTicks() > 0
                && net.minecraft.client.gui.screens.Screen.hasAltDown()) {
            PacketDistributor.sendToServer(new SpellActionPayload("domain_layer", "", 0));
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        // 炽阳之怒悬停期间：按住 Alt 左键充能，服务器扣 90 魔素并校验 18 层上限。
        if (event.isAttack() && ClientSpellState.blazingSunTicks() > 0
                && net.minecraft.client.gui.screens.Screen.hasAltDown()) {
            PacketDistributor.sendToServer(new SpellActionPayload("sun_charge", "", 0));
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        // 曜光裂界读条期间：按住 Alt 左键蓄力，服务器扣 190 魔素并校验六层上限。
        if (event.isAttack() && ClientSpellState.radiantCharging()
                && net.minecraft.client.gui.screens.Screen.hasAltDown()) {
            PacketDistributor.sendToServer(new SpellActionPayload("light_charge", "", 0));
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        // 断魂鸣弦读条期间：按住 Alt 左键追加鸣球，服务器扣 680 魔素并校验三颗上限。
        if (event.isAttack() && ClientSpellState.sonicCharging()
                && net.minecraft.client.gui.screens.Screen.hasAltDown()) {
            // 与按住追加共用同一个节流，避免一次按下被算成两颗。
            sonicHoldAt = SONIC_HOLD_INTERVAL;
            PacketDistributor.sendToServer(new SpellActionPayload("sonic_add", "", 0));
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        // 多重结界射线展开期间：按住 Alt 左键加阵，服务器扣 150 魔素并校验上限。
        if (event.isAttack() && ClientSpellState.barrierCount() > 0 && net.minecraft.client.gui.screens.Screen.hasAltDown()) {
            PacketDistributor.sendToServer(new SpellActionPayload("barrier_add", "", 0));
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (!ClientSpellState.targeting()) return;
        if (event.isUseItem()) {
            PacketDistributor.sendToServer(new SpellActionPayload("target_select", "", 0));
            event.setCanceled(true);
            event.setSwingHand(false);
        } else if (event.isAttack()) {
            // 确认后立即释放客户端输入状态，不等待服务器回包；这样任何整合包的滚轮监听都不会
            // 看到一个残留的“正在选目标”状态。服务器仍会独立校验目标并同步最终结果。
            ClientSpellState.cancelTargetingLocally();
            PacketDistributor.sendToServer(new SpellActionPayload("target_confirm", "", 0));
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GeneratedMod.WANDERING_WITCH.get(), WanderingWitchRenderer::new);
        event.registerEntityRenderer(GeneratedMod.ADVENTURER.get(), AdventurerRenderer::new);
        for (ElementalSlimeVariant variant : ElementalSlimeVariant.values())
            event.registerEntityRenderer(GeneratedMod.elementalSlimeType(variant), ElementalSlimeRenderer::new);
        for (GoblinVariant variant : GoblinVariant.values())
            event.registerEntityRenderer(GeneratedMod.goblinType(variant),
                    context -> new GoblinRenderer(context, variant));
        event.registerEntityRenderer(NewSpellContent.VINE_SERVANT.get(), SpellSummonRenderer::new);
        event.registerEntityRenderer(NewSpellContent.FLYING_SWORD.get(), SpellSummonRenderer::new);
        event.registerEntityRenderer(NewSpellContent.POISON_TENTACLE.get(), SpellSummonRenderer::new);
        event.registerEntityRenderer(NewSpellContent.FROST_GOLEM.get(), SpellSummonRenderer::new);
        event.registerEntityRenderer(NewSpellContent.PROJECTILE.get(), SpellProjectileRenderer::new);
        event.registerEntityRenderer(NewSpellContent.BLAZING_SUN.get(), BlazingSunRenderer::new);
        event.registerEntityRenderer(NewSpellContent.METEOR_STONE.get(), MeteorStoneRenderer::new);
        event.registerEntityRenderer(GeneratedMod.MANA_ORB.get(), ManaOrbRenderer::new);
        // 第 3 阶段：贴面投影锚点上的光点 / 连线 / 呼吸脉冲。
        event.registerBlockEntityRenderer(GeneratedMod.MAGIC_CIRCLE_PROJECTION_BLOCK_ENTITY.get(),
                MagicCircleProjectionRenderer::new);
    }

    /** 元素结晶戒指共用一张底图：第二层宝石按元素染色，所以十二枚外观一致、颜色不同。 */
    /** 三套哥布林骨骼（幼崽 / 成年 / 首领）各注册一次模型层。 */
    private static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (GoblinShape shape : GoblinShape.values()) {
            event.registerLayerDefinition(shape.layer(), () -> GoblinModelGeometry.create(shape));
        }
    }

    private static void registerItemColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        java.util.List<net.minecraft.world.level.ItemLike> rings = new java.util.ArrayList<>();
        for (ElementType element : ElementType.values()) {
            rings.add(GeneratedMod.MOD_ITEMS.get(MagicEquipment.ringId(element.id())).get());
        }
        rings.add(GeneratedMod.MOD_ITEMS.get(MagicEquipment.ringId(MagicEquipment.ARCANE)).get());
        net.minecraft.client.color.item.ItemColor color = (stack, tintIndex) ->
                tintIndex == 1 && stack.getItem() instanceof ElementRingItem ring ? ring.tintColor() : -1;
        event.register(color, rings.toArray(new net.minecraft.world.level.ItemLike[0]));

        // 新饰品同样共用一张底图：第二层宝石按各自的梯度配色染色。
        java.util.List<net.minecraft.world.level.ItemLike> relics = new java.util.ArrayList<>();
        for (Relics.Relic relic : Relics.ALL) {
            var holder = GeneratedMod.MOD_ITEMS.get(relic.id());
            if (holder != null) relics.add(holder.get());
        }
        net.minecraft.client.color.item.ItemColor relicColor = (stack, tintIndex) ->
                tintIndex == 1 && stack.getItem() instanceof RelicAccessoryItem item
                        ? item.relic().tint() : -1;
        event.register(relicColor, relics.toArray(new net.minecraft.world.level.ItemLike[0]));
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(GeneratedMod.GRINDER_MENU.get(), GrinderScreen::new);
        event.register(GeneratedMod.MANA_FORGE_MENU.get(), ManaFurnaceScreen::new);
        event.register(GeneratedMod.PROFESSIONAL_FURNACE_MENU.get(), ManaFurnaceScreen::new);
        event.register(GeneratedMod.CRYSTAL_PROCESSOR_MENU.get(), CrystalProcessorScreen::new);
        event.register(GeneratedMod.ADVENTURER_TRADE_MENU.get(), AdventurerTradeScreen::new);
    }

    private static void registerFluidRendering(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            private static final ResourceLocation STILL = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "block/pure_liquid_mana_still");
            private static final ResourceLocation FLOW = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "block/pure_liquid_mana_flow");

            @Override
            public ResourceLocation getStillTexture() {
                return STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return FLOW;
            }

            @Override
            public int getTintColor() {
                return 0xFFFFFFFF;
            }
        }, GeneratedMod.PURE_LIQUID_MANA_TYPE.get());
    }

    private static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAboveAll(MANA_HUD, (graphics, delta) -> renderManaHud(graphics));
        event.registerAboveAll(APPRAISAL_HUD, (graphics, delta) -> renderAppraisalHud(graphics));
        event.registerAboveAll(PRO_APPRAISAL_HUD, (graphics, delta) -> ProAppraisalHud.render(graphics));
        event.registerAboveAll(REACTION_TEXT_HUD, ClientReactionText::renderHud);
        event.registerAboveAll(RESONANCE_CORE_HUD, (graphics, delta) -> ResonanceCoreHud.renderHud(graphics));
        // 曜光裂界爆裂的 16 刻黑白闪光画在最上层，压过其它 HUD。
        event.registerAboveAll(RADIANT_FLASH_HUD, (graphics, delta) -> ClientSpellVisuals.renderRadiantFlash(graphics));
        // 招架的准心白圈、完美招架白闪/金色光晕、斗气不足的泛红都画在最上层。
        event.registerAboveAll(PARRY_HUD, (graphics, delta) -> ClientParryVisuals.renderHud(graphics));
    }

    private static void addEffectTooltips(GatherEffectScreenTooltipsEvent event) {
        if (event.getEffectInstance().is(GeneratedMod.BATTLE_QI_COLLAPSE)) {
            event.getTooltip().add(Component.translatable(
                    "effect.dimensional_mana.battle_qi_collapse.damage_taken").withStyle(ChatFormatting.RED));
            event.getTooltip().add(Component.translatable(
                    "effect.dimensional_mana.battle_qi_collapse.damage_dealt").withStyle(ChatFormatting.DARK_RED));
            return;
        }
        if (!event.getEffectInstance().is(GeneratedMod.MANA_EXHAUSTION)) return;
        event.getTooltip().add(Component.translatable("effect.dimensional_mana.mana_exhaustion.no_regen")
                .withStyle(ChatFormatting.GRAY));
        event.getTooltip().add(Component.translatable("effect.dimensional_mana.mana_exhaustion.damage_taken")
                .withStyle(ChatFormatting.RED));
        event.getTooltip().add(Component.translatable("effect.dimensional_mana.mana_exhaustion.damage_dealt")
                .withStyle(ChatFormatting.DARK_RED));
    }

    private static void renderManaHud(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;

        int centerX = Math.max(28, Math.min(graphics.guiWidth() - 28, ClientManaConfig.HUD_X_FROM_LEFT.get()));
        int centerY = Math.max(30, Math.min(graphics.guiHeight() - 30,
                graphics.guiHeight() - ClientManaConfig.HUD_Y_FROM_BOTTOM.get()));
        int radius = 17;
        double maxMana = Math.max(1.0, ClientManaState.maxMana());
        double manaRatio = Math.max(0.0, ClientManaState.mana() / maxMana);
        double safeRatio = Math.max(1.0, ClientManaState.overchargeSafeRatio());
        boolean overloaded = manaRatio > safeRatio;
        double normalFill = Math.min(1.0, manaRatio);
        double overchargeRange = Math.max(0.01, safeRatio - 1.0);
        double overchargeFill = Math.min(1.0, Math.max(0.0, manaRatio - 1.0) / overchargeRange);
        int normalTop = centerY + radius - (int)Math.round(normalFill * radius * 2.0);
        int overchargeTop = centerY + radius - (int)Math.round(overchargeFill * radius * 2.0);
        float overloadHue = (minecraft.level == null ? 0L : minecraft.level.getGameTime()) % 40L / 40.0F;
        int overloadRgb = Mth.hsvToRgb(overloadHue, 0.72F, 1.0F);
        int overloadColor = 0xE8000000 | overloadRgb;

        drawDisc(graphics, centerX + 2, centerY + 2, radius + 2, 0x78000000);
        drawDisc(graphics, centerX, centerY, radius + 1, overloaded ? 0xFF2A1638 : 0xFF101629);
        for (int y = -radius; y <= radius; y++) {
            int half = (int)Math.floor(Math.sqrt(radius * radius - y * y));
            int screenY = centerY + y;
            int color = overloaded && ((minecraft.level == null ? 0L : minecraft.level.getGameTime()) / 3L + y) % 2L == 0L
                    ? overloadColor : 0xDE18223C;
            if (!overloaded && screenY >= normalTop) {
                int blue = Math.max(105, 230 - (y + radius) * 3);
                color = 0xE0000000 | (35 << 16) | (blue << 8) | 255;
            }
            if (!overloaded && overchargeFill > 0.0 && screenY >= overchargeTop) color = 0xEC5FF5E8;
            graphics.fill(centerX - half, screenY, centerX + half + 1, screenY + 1, color);
        }
        graphics.fill(centerX - 8, centerY - 12, centerX - 3, centerY - 10, 0x70FFFFFF);
        graphics.fill(centerX - 10, centerY - 9, centerX - 7, centerY - 7, 0x45FFFFFF);

        double battleQiRatio = Math.max(0.0, ClientManaState.battleQi() / (double)Math.max(1, ClientManaState.maxBattleQi()));
        drawBattleQiRing(graphics, centerX, centerY, radius + 4, Math.min(1.0, battleQiRatio), 0xFF3C3422, 0xFFFFC84A);
        if (battleQiRatio > 1.0) {
            double excessRatio = Math.min(1.0, battleQiRatio - 1.0);
            drawBattleQiRing(graphics, centerX, centerY, radius + 7, excessRatio, 0xFF40243C, 0xFFFF67DB);
        }
        if (ClientManaState.exhausted()) drawBattleQiRing(graphics, centerX, centerY, radius + 1, 1.0, 0xFFFF4050, 0xFFFF4050);

        double textScale = ClientManaConfig.MANA_TEXT_SCALE.get();
        int manaTextColor = overloaded ? 0xFF000000 | overloadRgb
                : overchargeFill > 0.0 ? 0xFFBFFFF9 : 0xFFFFFFFF;
        drawScaledCenteredString(graphics, minecraft, String.format("%.0f", ClientManaState.mana()),
                centerX, centerY - 5, manaTextColor, textScale);
        drawScaledCenteredString(graphics, minecraft, "/ " + String.format("%.0f", ClientManaState.maxMana()),
                centerX, centerY + 5, overloaded ? 0xFFFFFFFF : 0xFFBED5FF, textScale);
        renderSelectedSpellHud(graphics, minecraft, centerX + radius + 13, centerY - 12);
        renderQiSlotIcon(graphics, minecraft, centerX + radius + 13, centerY - 12);
    }

    /**
     * 斗气技能图标：缩成一个小方块，贴在法术技能选择块的右边。
     *
     * <p>以前是一整块带文字的提示板，压在左下角魔素球和法术图标上面；现在只留一个
     * 带细边框的技能图标——有法术选中时排在「按 X 施法」那行文字后面，
     * 没选法术时就直接放在法术图标的位置，两种情况都不会和法术 HUD 重叠。</p>
     */
    private static void renderQiSlotIcon(GuiGraphics graphics, Minecraft minecraft, int spellX, int spellY) {
        QiProgression.Ability ability = ClientQiState.selected();
        if (ability == null) return;
        var holder = GeneratedMod.MOD_ITEMS.get(ability.manualId());
        if (holder == null) return;
        int x;
        int y;
        SpellDefinition spell = ClientSpellState.selected();
        if (spell != null) {
            Component castKey = Component.translatable("hud.dimensional_mana.cast_key",
                    CAST_SPELL.getTranslatedKeyMessage());
            x = spellX + 25 + minecraft.font.width(castKey) + 6;
            y = spellY + 10;
        } else {
            x = spellX;
            y = spellY;
        }
        // 玩家把魔素 HUD 挪到屏幕右边时别让图标跑出屏幕。
        if (x > graphics.guiWidth() - 20) x = graphics.guiWidth() - 20;
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0x88180A08);
        graphics.renderOutline(x - 1, y - 1, 18, 18, 0xB0C9A25C);
        graphics.renderItem(new ItemStack(holder.get()), x + 1, y + 1);
    }

    private static void renderSelectedSpellHud(GuiGraphics graphics, Minecraft minecraft, int x, int y) {
        SpellDefinition spell = ClientSpellState.selected();
        if (spell == null) return;
        graphics.fill(x - 2, y - 2, x + 22, y + 22, 0xB0120B20);
        graphics.renderOutline(x - 2, y - 2, 24, 24, 0xFFAF7EE8);
        graphics.blit(spell.icon(), x + 2, y + 2, 0, 0, 16, 16, 16, 16);
        if (ClientSpellState.cooldownTicks() > 0) {
            int cover = Math.min(20, (int)Math.ceil(ClientSpellState.cooldownTicks() / (double)Math.max(1, spell.cooldownTicks()) * 20.0));
            graphics.fill(x, y + 20 - cover, x + 20, y + 20, 0xA0000000);
            graphics.drawCenteredString(minecraft.font, String.format("%.1f", ClientSpellState.cooldownTicks() / 20.0),
                    x + 10, y + 6, 0xFFFFFFFF);
        }
        graphics.drawString(minecraft.font, Component.translatable(spell.translationKey()), x + 25, y + 1, 0xFFE9DCFF, true);
        graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.cast_key",
                CAST_SPELL.getTranslatedKeyMessage()), x + 25, y + 12, 0xFFAB97C2, true);
        SpellDefinition casting = ClientSpellState.casting();
        if (casting != null && ClientSpellState.castTicksTotal() > 0) {
            int barWidth = 82;
            int filled = (int)Math.round((1.0 - ClientSpellState.castTicksRemaining()
                    / (double)ClientSpellState.castTicksTotal()) * barWidth);
            graphics.fill(x, y + 27, x + barWidth, y + 33, 0xB0181225);
            graphics.fill(x, y + 27, x + Math.max(0, Math.min(barWidth, filled)), y + 33, 0xFF8E4DDA);
            graphics.renderOutline(x, y + 27, barWidth, 6, 0xFFD1AEFF);
            graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.casting",
                    Component.translatable(casting.translationKey())), x, y + 36, 0xFFFFE5A6, true);
        }
        if (ClientSpellState.barrierCount() > 0) {
            graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.barrier_field",
                    ClientSpellState.barrierCount(), 9,
                    String.format(java.util.Locale.ROOT, "%.1f", ClientSpellState.barrierTicks() / 20.0)),
                    x, y + 47, 0xFFD8A6FF, true);
        }
        if (ClientSpellState.blazingSunTicks() > 0) {
            graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.blazing_sun",
                    ClientSpellState.blazingSunCharge(), BlazingSun.MAX_CHARGE,
                    String.format(java.util.Locale.ROOT, "%.1f", ClientSpellState.blazingSunTicks() / 20.0)),
                    x, y + 47, 0xFFFFB25C, true);
        }
        if (ClientSpellState.dragonBeamTicks() > 0) {
            graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.dragon_beam",
                    String.format(java.util.Locale.ROOT, "%.1f", ClientSpellState.dragonBeamTicks() / 20.0)),
                    x, y + 47, 0xFF7FD4FF, true);
        }
        if (ClientSpellState.subzeroTicks() > 0) {
            graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.subzero_domain",
                    ClientSpellState.subzeroLayers(),
                    String.format(java.util.Locale.ROOT, "%.1f", ClientSpellState.subzeroTicks() / 20.0)),
                    x, y + 47, 0xFF9FE8FF, true);
        }
        if (ClientSpellState.tempestTicks() > 0) {
            graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.thunder_tempest",
                    ClientSpellState.tempestExtras(), 2,
                    String.format(java.util.Locale.ROOT, "%.1f", ClientSpellState.tempestTicks() / 20.0)),
                    x, y + 47, 0xFFFFE45B, true);
        }
        if (ClientSpellState.meteorTicks() > 0) {
            graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.meteor",
                    ClientSpellState.meteorRocks(), EarthSpells.METEOR_MAX,
                    String.format(java.util.Locale.ROOT, "%.1f", ClientSpellState.meteorTicks() / 20.0)),
                    x, y + 47, 0xFFD0A46A, true);
        }
        if (ClientSpellState.bankaiTicks() > 0) {
            graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.endless_bankai",
                    String.format(java.util.Locale.ROOT, "%.1f", ClientSpellState.bankaiTicks() / 20.0)),
                    x, y + 47, 0xFF9BE06A, true);
        }
        if (ClientSpellState.swordRiftTicks() > 0) {
            // 剑意层数直接读自身效果，和状态栏图标永远一致，不需要额外的同步字段。
            var intent = minecraft.player == null ? null : minecraft.player.getEffect(NewSpellContent.SWORD_INTENT);
            int stacks = intent == null ? 0 : intent.getAmplifier() + 1;
            graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.sky_rift",
                    String.format(java.util.Locale.ROOT, "%.1f", ClientSpellState.swordRiftTicks() / 20.0),
                    stacks, 18),
                    x, y + 47, 0xFFCFE6F5, true);
        }
        if (ClientSpellState.radiantCharging()) {
            graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.radiant_charge",
                    ClientSpellState.lightCharge(), LightSpells.RIFT_MAX_CHARGE),
                    x, y + 47, 0xFFFFF3B0, true);
        }
        if (ClientSpellState.lightShelterTicks() > 0) {
            graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.light_shelter",
                    String.format(java.util.Locale.ROOT, "%.1f", ClientSpellState.lightShelterTicks() / 20.0)),
                    x, y + 57, 0xFFFFF7D6, true);
        }
        if (ClientSpellState.tideTicks() > 0) {
            graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.mana_tide",
                    String.format(java.util.Locale.ROOT, "%.1f", ClientSpellState.tideTicks() / 20.0)),
                    x, y + 47, 0xFFC9A6FF, true);
        }
        if (ClientSpellState.senseTicks() > 0) {
            graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.mana_sense",
                    String.format(java.util.Locale.ROOT, "%.1f", ClientSpellState.senseTicks() / 20.0)),
                    x, y + 58, 0xFFFFE9A8, true);
        }
        // 伏特状态是原版效果，直接读自身效果时长显示，不用额外的同步字段。
        if (minecraft.player != null) {
            var volt = minecraft.player.getEffect(NewSpellContent.VOLT_STATE);
            if (volt != null) {
                graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.volt_state",
                        String.format(java.util.Locale.ROOT, "%.1f", volt.getDuration() / 20.0)),
                        x, y + 58, 0xFFFFE45B, true);
            }
            // 风系两术同样读自身效果：风爆步看剩余时长，风狂另外显示已经叠了多少层风葬。
            var windStep = minecraft.player.getEffect(NewSpellContent.WIND_STEP);
            if (windStep != null) {
                graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.wind_step",
                        String.format(java.util.Locale.ROOT, "%.1f", windStep.getDuration() / 20.0)),
                        x, y + 58, 0xFFC9FFE9, true);
            }
            var windFrenzy = minecraft.player.getEffect(NewSpellContent.WIND_FRENZY);
            if (windFrenzy != null) {
                graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.wind_frenzy",
                        String.format(java.util.Locale.ROOT, "%.1f", windFrenzy.getDuration() / 20.0),
                        ClientSpellState.windStacks()),
                        x, y + 58, 0xFFE4FFF4, true);
                graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.wind_frenzy_hint"),
                        x, y + 69, 0xFF9AD8BE, true);
            }
            // 磐石外壳是原版效果，剩余时间直接读自身效果即可。
            var stoneShell = minecraft.player.getEffect(NewSpellContent.STONE_SHELL);
            if (stoneShell != null) {
                graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.stone_shell",
                        String.format(java.util.Locale.ROOT, "%.1f", stoneShell.getDuration() / 20.0)),
                        x, y + 58, 0xFFD8B27A, true);
            }
        }
    }

    /**
     * 风狂期间转向速度 -24%：临时把鼠标灵敏度压到 76%，状态结束后原样还原。
     * 一直每刻核对，退出世界或状态丢失时也能立刻恢复玩家的灵敏度。
     */
    private static void syncWindFrenzySensitivity(Minecraft minecraft) {
        boolean active = minecraft.player != null && minecraft.player.hasEffect(NewSpellContent.WIND_FRENZY);
        if (active) {
            if (windFrenzySensitivity == null) {
                windFrenzySensitivity = minecraft.options.sensitivity().get();
                minecraft.options.sensitivity().set(windFrenzySensitivity * (1.0 - WindSpells.TURN_PENALTY));
            }
        } else if (windFrenzySensitivity != null) {
            minecraft.options.sensitivity().set(windFrenzySensitivity);
            windFrenzySensitivity = null;
        }
    }

    private static void renderAppraisalHud(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientAppraisalState.glassesActive() || minecraft.player == null || minecraft.level == null
                || minecraft.options.hideGui) return;
        int x = graphics.guiWidth() / 2 + 12;
        int y = graphics.guiHeight() / 2 - 10;
        String concentration = Component.translatable("hud.dimensional_mana.concentration",
                String.format("%.2f", ClientAppraisalState.concentration())).getString();
        graphics.drawString(minecraft.font, concentration, x, y, 0xFF79E8FF, true);
        y += 11;

        if (minecraft.hitResult instanceof EntityHitResult entityHit) {
            if (entityHit.getEntity() instanceof LivingEntity living
                    && living.getId() == ClientAppraisalState.entityId()) {
                graphics.drawString(minecraft.font, ClientAppraisalState.targetName(), x, y, 0xFFFFFFFF, true);
                graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.target.health",
                        formatOne(ClientAppraisalState.health()), formatOne(ClientAppraisalState.maxHealth())),
                        x, y + 10, 0xFFFF6B6B, true);
                graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.target.mana",
                        formatOne(ClientAppraisalState.mana()), formatOne(ClientAppraisalState.maxMana())),
                        x, y + 20, 0xFF70D8FF, true);
                graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.target.battle_qi",
                        ClientAppraisalState.battleQi(), ClientAppraisalState.maxBattleQi()),
                        x, y + 30, 0xFFFFCD59, true);
                return;
            }
            if (entityHit.getEntity() instanceof ItemEntity itemEntity) {
                double mana = ManaContent.item(itemEntity.getItem(), ClientAppraisalState.concentration());
                drawContentLine(graphics, minecraft, x, y, itemEntity.getItem().getHoverName(), mana);
                return;
            }
        }
        if (minecraft.hitResult instanceof BlockHitResult blockHit) {
            var state = minecraft.level.getBlockState(blockHit.getBlockPos());
            if (!state.isAir()) {
                double mana = ManaContent.block(minecraft.level, blockHit.getBlockPos(), ClientAppraisalState.concentration());
                drawContentLine(graphics, minecraft, x, y, state.getBlock().getName(), mana);
            }
        }
    }

    private static void drawContentLine(GuiGraphics graphics, Minecraft minecraft, int x, int y,
                                        Component name, double mana) {
        graphics.drawString(minecraft.font, name, x, y, 0xFFFFFFFF, true);
        graphics.drawString(minecraft.font, Component.translatable("hud.dimensional_mana.target.content", formatOne(mana)),
                x, y + 10, 0xFFBA8CFF, true);
    }

    private static String formatOne(double value) {
        return String.format(value >= 1000 ? "%.0f" : "%.1f", value);
    }

    private static void drawScaledCenteredString(GuiGraphics graphics, Minecraft minecraft, String text,
                                                  int centerX, int y, int color, double scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0.0F);
        graphics.pose().scale((float)scale, (float)scale, 1.0F);
        graphics.drawString(minecraft.font, text, -minecraft.font.width(text) / 2, 0, color, true);
        graphics.pose().popPose();
    }

    private static void drawDisc(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int half = (int)Math.floor(Math.sqrt(radius * radius - y * y));
            graphics.fill(centerX - half, centerY + y, centerX + half + 1, centerY + y + 1, color);
        }
    }

    private static void drawBattleQiRing(GuiGraphics graphics, int centerX, int centerY, int radius,
                                     double progress, int backgroundColor, int fillColor) {
        int steps = 96;
        int filledSteps = (int)Math.round(progress * steps);
        for (int i = 0; i < steps; i++) {
            double angle = -Math.PI / 2.0 + Math.PI * 2.0 * i / steps;
            int x = centerX + (int)Math.round(Math.cos(angle) * radius);
            int y = centerY + (int)Math.round(Math.sin(angle) * radius);
            int color = i < filledSteps ? fillColor : backgroundColor;
            graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }

    private ClientModEvents() {}
}
