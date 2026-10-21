package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class NewSpellContent {
    public static final net.neoforged.neoforge.registries.DeferredBlock<TemporarySpellLight> TEMPORARY_LIGHT = GeneratedMod.BLOCKS.register(
            "temporary_spell_light", () -> new TemporarySpellLight(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                    .noCollission().noOcclusion().replaceable().noLootTable().instabreak().lightLevel(state -> 10)));
    /** 炽阳之怒火球随身携带的亮度 15 光源；火球离开或消失后自动消退。 */
    public static final net.neoforged.neoforge.registries.DeferredBlock<BlazingSunLight> BLAZING_LIGHT = GeneratedMod.BLOCKS.register(
            "blazing_sun_light", () -> new BlazingSunLight(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                    .noCollission().noOcclusion().replaceable().noLootTable().instabreak().lightLevel(state -> 15)));
    public static final DeferredHolder<MobEffect, MobEffect> FIRE_VULNERABILITY = effect("fire_vulnerability", false, 0xFF8A34);
    public static final DeferredHolder<MobEffect, MobEffect> WATER_PRISON = effect("water_prison", false, 0x58BAFF);
    public static final DeferredHolder<MobEffect, MobEffect> ICE_COFFIN = effect("ice_coffin", false, 0xAAEFFF);
    public static final DeferredHolder<MobEffect, MobEffect> STUN = effect("spell_stun", false, 0xFFE471);
    public static final DeferredHolder<MobEffect, MobEffect> FROST = effect("frost_trail", false, 0xA8E5FF);
    public static final DeferredHolder<MobEffect, MobEffect> MUD = effect("mud_swamp", false, 0x806542);
    public static final DeferredHolder<MobEffect, MobEffect> VINE_SLOW = effect("vine_slow", false, 0x63A647);
    public static final DeferredHolder<MobEffect, MobEffect> LIGHTNESS = effect("lightness", true, 0xBAFFDB);
    public static final DeferredHolder<MobEffect, MobEffect> LIFE_BLESSING = effect("life_blessing", true, 0x75E86F);
    public static final DeferredHolder<MobEffect, MobEffect> GLIMMER = effect("glimmer", true, 0xFFF0AE);
    public static final DeferredHolder<MobEffect, MobEffect> PLAGUE = effect("plague", false, 0x9BAC36);
    public static final DeferredHolder<MobEffect, MobEffect> TINNITUS = effect("tinnitus", false, 0xD6A8E3);
    /** 零下领域的深层冻结：状态本身由服务器状态表维护，牛奶只能暂时抹掉图标。 */
    public static final DeferredHolder<MobEffect, MobEffect> DEEP_FREEZE = effect("deep_freeze", false, 0x7FD9FF);
    /** 伏特状态：移速 +200%、跳跃高度 +50%、受伤 -50%，身体缠绕闪电并持续电击接触的敌人。 */
    public static final DeferredHolder<MobEffect, MobEffect> VOLT_STATE = effect("volt_state", true, 0xFFF07A);
    /** 极性紊乱：受伤 +34% 且没有受击无敌帧，自己造成的伤害 -42%，可被反复电击刷新。 */
    public static final DeferredHolder<MobEffect, MobEffect> POLARITY_DISORDER = effect("polarity_disorder", false, 0x8F7BFF);
    /** 风爆步：移速 +150%，身体缠绕狂风，触碰敌人时击退七格并附加风袭。 */
    public static final DeferredHolder<MobEffect, MobEffect> WIND_STEP = effect("wind_step", true, 0xC9FFE9);
    /** 风袭：8 秒内每触发一次扩散反应，都会额外承受 48 点爆炸伤害。 */
    public static final DeferredHolder<MobEffect, MobEffect> WIND_ASSAULT = effect("wind_assault", false, 0x75D867);
    /** 风狂：风葬回廊的持续状态，移速 +195%、每秒消耗 100 魔素，期间不断生成风暴场。 */
    public static final DeferredHolder<MobEffect, MobEffect> WIND_FRENZY = effect("wind_frenzy", true, 0xE4FFF4);
    /** 磐石外壳：28 秒内替目标承受最多 100 点伤害，并补上护甲、韧性与击退免疫。 */
    public static final DeferredHolder<MobEffect, MobEffect> STONE_SHELL = effect("stone_shell", true, 0xB08A62);
    /** 奥术沉默：60 秒内无法释放任何技能、无法造成法术暴击，是乱魔的核心减益。 */
    public static final DeferredHolder<MobEffect, MobEffect> ARCANE_SILENCE = effect("arcane_silence", false, 0xB78CFF);
    /** 植入寄生：卍之波在敌人身上种下的木种，触发元素反应时额外炸一次木伤，濒死直接斩杀。 */
    public static final DeferredHolder<MobEffect, MobEffect> PARASITE = effect("parasite", false, 0x7FAF3C);
    /** 金钟罩：罩住施法者的金色钟形护罩，拥有 250 点吸收量，期间免疫破甲。 */
    public static final DeferredHolder<MobEffect, MobEffect> GOLDEN_BELL = effect("golden_bell", true, 0xFFD75E);
    /** 剑意：万剑归一的飞剑命中后叠加的金属性攻击加成，每层 +2%，最多 18 层。 */
    public static final DeferredHolder<MobEffect, MobEffect> SWORD_INTENT = effect("sword_intent", true, 0xDCE7F2);
    /** 光的庇护：受伤 -22%、每秒回复 2 点生命，被毒属性攻击时清除中毒并反伤 15 点光伤。 */
    public static final DeferredHolder<MobEffect, MobEffect> LIGHT_SHELTER = effect("light_shelter", true, 0xFFFBE0);
    /** 光蚀：曜光裂界留下的光系侵蚀，按等级定期造成光属性伤害并附着光元素。 */
    public static final DeferredHolder<MobEffect, MobEffect> LIGHT_ECLIPSE = effect("light_eclipse", false, 0xF2D98A);
    /** 猛毒：猛毒攻心的毒刺标记，期间目标身上的中毒伤害翻倍、间隔缩短 4 刻，还能被延长。 */
    public static final DeferredHolder<MobEffect, MobEffect> FIERCE_POISON = effect("fierce_poison", false, 0x7FB93B);
    /** 鸩毒：鸩羽千夜的毒雾印记，层数越高毒易伤越高、移速越慢，还会压低生命上限。 */
    public static final DeferredHolder<MobEffect, MobEffect> ZHEN_POISON = effect("zhen_poison", false, 0x5E8B2E);
    /** 爆震：被高频声波掠过后的四刻余震，每两刻结算一次十六点音伤。 */
    public static final DeferredHolder<MobEffect, MobEffect> SONIC_BLAST = effect("sonic_blast", false, 0xE997FF);
    /** 魔力通透：共鸣核心触发元素共鸣期间的状态图标，数值加成由 {@link ResonanceCore} 结算。 */
    public static final DeferredHolder<MobEffect, MobEffect> MANA_TRANSPARENCY = effect("mana_transparency", true, 0x8FE6FF);
    public static final DeferredHolder<EntityType<?>, EntityType<SpellSummon>> VINE_SERVANT = summon("vine_servant", 0.45F, 0.975F);
    public static final DeferredHolder<EntityType<?>, EntityType<SpellSummon>> FLYING_SWORD = summon("flying_sword", 0.45F, 1.2F);
    public static final DeferredHolder<EntityType<?>, EntityType<SpellSummon>> POISON_TENTACLE = summon("poison_tentacle", 0.6F, 1.4F);
    public static final DeferredHolder<EntityType<?>, EntityType<SpellSummon>> FROST_GOLEM = summon("frost_golem", 0.7F, 1.7F);
    public static final DeferredHolder<EntityType<?>, EntityType<SpellProjectile>> PROJECTILE = GeneratedMod.ENTITY_TYPES.register(
            "elemental_projectile", () -> EntityType.Builder.<SpellProjectile>of(SpellProjectile::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(1).build(GeneratedMod.MOD_ID + ":elemental_projectile"));
    public static final DeferredHolder<EntityType<?>, EntityType<BlazingSun>> BLAZING_SUN = GeneratedMod.ENTITY_TYPES.register(
            "blazing_sun", () -> EntityType.Builder.<BlazingSun>of(BlazingSun::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(12).updateInterval(1).build(GeneratedMod.MOD_ID + ":blazing_sun"));
    /** 万法千陨悬停与飞行中的巨石；只负责位置同步，渲染由 MeteorStoneRenderer 画成石块。 */
    public static final DeferredHolder<EntityType<?>, EntityType<MeteorStone>> METEOR_STONE = GeneratedMod.ENTITY_TYPES.register(
            "meteor_stone", () -> EntityType.Builder.<MeteorStone>of(MeteorStone::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(12).updateInterval(1).build(GeneratedMod.MOD_ID + ":meteor_stone"));
    private static DeferredHolder<MobEffect, MobEffect> effect(String name, boolean good, int color) {
        return GeneratedMod.EFFECTS.register(name, () -> new SpellStatusEffect(name, good, color));
    }
    private static DeferredHolder<EntityType<?>, EntityType<SpellSummon>> summon(String name, float width, float height) {
        return GeneratedMod.ENTITY_TYPES.register(name, () -> EntityType.Builder.of(SpellSummon::new, MobCategory.CREATURE)
                .sized(width, height).clientTrackingRange(10).build(GeneratedMod.MOD_ID + ":" + name));
    }
    public static void init() { }
    private NewSpellContent() { }
}
