package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.Minecraft;

/** 客户端只读法术状态；所有学习和施法判定均在服务器完成。 */
public final class ClientSpellState {
    private static int[] progress = new int[SpellDefinition.values().length];
    private static String learningSpell = "";
    private static String[] slots = new String[SpellData.SLOT_COUNT];
    private static int selectedSlot;
    private static String castingSpell = "";
    private static int castTicksRemaining;
    private static int castTicksTotal;
    private static int cooldownTicks;
    private static boolean targeting;
    private static boolean targetSelected;
    private static int barrierTicks;
    private static int barrierCount;
    private static int blazingSunCharge;
    private static int blazingSunTicks;
    private static int dragonBeamTicks;
    private static int subzeroLayers;
    private static int subzeroTicks;
    private static int tempestExtras;
    private static int tempestTicks;
    private static int windStacks;
    private static int meteorRocks;
    private static int meteorTicks;
    private static int tideTicks;
    private static int senseTicks;
    private static int bankaiTicks;
    private static int swordRiftTicks;
    private static int lightCharge;
    private static int soulChordExtras;

    public static void update(SpellSyncPayload payload) {
        progress = payload.progress().clone();
        learningSpell = payload.learningSpell();
        slots = new String[SpellData.SLOT_COUNT];
        System.arraycopy(payload.slots(), 0, slots, 0, Math.min(slots.length, payload.slots().length));
        selectedSlot = Math.max(0, Math.min(SpellData.SLOT_COUNT - 1, payload.selectedSlot()));
        castingSpell = payload.castingSpell();
        castTicksRemaining = payload.castTicksRemaining();
        castTicksTotal = payload.castTicksTotal();
        cooldownTicks = payload.selectedCooldownTicks();
        targeting = payload.targeting();
        targetSelected = payload.targetSelected();
        barrierTicks = payload.barrierTicks();
        barrierCount = payload.barrierCount();
        blazingSunCharge = payload.blazingSunCharge();
        blazingSunTicks = payload.blazingSunTicks();
        dragonBeamTicks = payload.dragonBeamTicks();
        subzeroLayers = payload.subzeroLayers();
        subzeroTicks = payload.subzeroTicks();
        tempestExtras = payload.tempestExtras();
        tempestTicks = payload.tempestTicks();
        windStacks = payload.windStacks();
        meteorRocks = payload.meteorRocks();
        meteorTicks = payload.meteorTicks();
        tideTicks = payload.tideTicks();
        senseTicks = payload.senseTicks();
        bankaiTicks = payload.bankaiTicks();
        swordRiftTicks = payload.swordRiftTicks();
        lightCharge = payload.lightCharge();
        soulChordExtras = payload.soulChordExtras();
    }

    public static void clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) return;
        if (castTicksRemaining > 0) castTicksRemaining--;
        if (cooldownTicks > 0) cooldownTicks--;
        if (barrierTicks > 0) barrierTicks--;
        if (blazingSunTicks > 0) blazingSunTicks--;
        if (dragonBeamTicks > 0) dragonBeamTicks--;
        if (subzeroTicks > 0) subzeroTicks--;
        if (tempestTicks > 0) tempestTicks--;
        if (meteorTicks > 0) meteorTicks--;
        if (tideTicks > 0) tideTicks--;
        if (senseTicks > 0) senseTicks--;
        if (bankaiTicks > 0) bankaiTicks--;
        if (swordRiftTicks > 0) swordRiftTicks--;
    }

    public static int progress(SpellDefinition spell) {
        return spell.ordinal() < progress.length ? progress[spell.ordinal()] : 0;
    }
    public static boolean learned(SpellDefinition spell) { return progress(spell) >= SpellData.REQUIRED_SUCCESSES; }
    public static SpellDefinition learning() { return SpellDefinition.byId(learningSpell); }
    public static SpellDefinition slot(int index) {
        return index >= 0 && index < slots.length ? SpellDefinition.byId(slots[index]) : null;
    }
    public static int selectedSlot() { return selectedSlot; }
    public static SpellDefinition selected() { return slot(selectedSlot); }
    public static SpellDefinition casting() { return SpellDefinition.byId(castingSpell); }
    public static int castTicksRemaining() { return castTicksRemaining; }
    public static int castTicksTotal() { return castTicksTotal; }
    public static int cooldownTicks() { return cooldownTicks; }
    public static boolean targeting() { return targeting; }
    public static boolean targetSelected() { return targetSelected; }
    public static int barrierTicks() { return barrierTicks; }
    public static int barrierCount() { return barrierCount; }
    public static int blazingSunCharge() { return blazingSunCharge; }
    public static int blazingSunTicks() { return blazingSunTicks; }
    public static int dragonBeamTicks() { return dragonBeamTicks; }
    public static int subzeroLayers() { return subzeroLayers; }
    public static int subzeroTicks() { return subzeroTicks; }
    public static int tempestExtras() { return tempestExtras; }
    public static int tempestTicks() { return tempestTicks; }
    /** 风葬回廊当前叠了多少层风葬（风十字斩会按层数分摊斩击）。 */
    public static int windStacks() { return windStacks; }
    /** 万法千陨当前已经升起的石块数量。 */
    public static int meteorRocks() { return meteorRocks; }
    /** 万法千陨距离自动投掷还剩多少刻。 */
    public static int meteorTicks() { return meteorTicks; }
    /** 魔素潮汐的混乱魔力锚点还剩多少刻。 */
    public static int tideTicks() { return tideTicks; }
    /** 魔素感知的高亮还剩多少刻。 */
    public static int senseTicks() { return senseTicks; }
    /** 不息卍解的生命之树还剩多少刻。 */
    public static int bankaiTicks() { return bankaiTicks; }
    /** 万剑归一的天空裂缝还剩多少刻。 */
    public static int swordRiftTicks() { return swordRiftTicks; }
    /** 曜光裂界在前摇里已经蓄了多少层。 */
    public static int lightCharge() { return lightCharge; }
    /** 曜光裂界读条期间按 Alt + 左键可以蓄力。 */
    public static boolean radiantCharging() { return casting() == SpellDefinition.RADIANT_RIFT; }
    /** 光之庇护剩余时间（刻），没有庇护时是 0。 */
    public static int lightShelterTicks() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return 0;
        var shelter = minecraft.player.getEffect(NewSpellContent.LIGHT_SHELTER);
        return shelter == null ? 0 : shelter.getDuration();
    }
    /** 万法千陨召唤期间按 Alt + 左键可以追加石块。 */
    public static boolean meteorSummoning() { return meteorTicks > 0; }
    /** 天雷滚滚读条期间按 Alt + 左键可以追加雷球。 */
    public static boolean tempestCharging() { return casting() == SpellDefinition.THUNDER_TEMPEST; }
    /** 断魂鸣弦读条期间按 Alt + 左键可以追加震荡鸣球。 */
    public static boolean sonicCharging() { return casting() == SpellDefinition.SOUL_CHORD; }
    /** 断魂鸣弦前摇期间已经追加了几颗鸣球（最多两颗，另外一颗是主球）。 */
    public static int soulChordExtras() { return soulChordExtras; }

    /** 输入取消先在本地生效，避免等待服务器回包期间继续吞掉攻击或使用键。 */
    public static void cancelTargetingLocally() {
        targeting = false;
        targetSelected = false;
    }

    private ClientSpellState() {}
}
