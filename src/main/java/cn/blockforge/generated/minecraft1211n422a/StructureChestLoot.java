package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * 多模组环境下的「结构宝箱表」判定，以及带重名保护的加池入口。
 *
 * <p>本模组的战利品（碎片、饰品、强化石、魔力布匹、装备胚子……）都是往「结构宝箱」表里追加
 * 独立池子来实现的。以前一批注入点只认 {@code minecraft:chests/*}，于是别的模组新增的结构
 * （{@code othermod:chests/goblin_camp} 之类）里的箱子永远开不出本模组的东西——多模组服务器里
 * 就会觉得「这些物品只存在于原版结构」。</p>
 *
 * <p>这里把判定统一成一条规则，任何命名空间都能命中：</p>
 * <ul>
 *   <li>{@code chests/xxx}、{@code chests}：原版与绝大多数模组的写法；</li>
 *   <li>{@code chest/xxx}：少数模组把目录写成单数；</li>
 *   <li>{@code xxx/chests/yyy}：把宝箱表埋在结构目录下的模组；</li>
 *   <li>最后一段是 {@code chest_xxx} / {@code chests_xxx} 的表，如 {@code chest_loot}。</li>
 * </ul>
 *
 * <p>刻意<b>不</b>认 {@code blocks/chest} 这类方块掉落表：那是「打掉箱子掉箱子」的表，
 * 往里加战利品会让拆箱子直接爆出碎片。所以只有出现在 {@code blocks/}、{@code entities/}
 * 这类目录之外的 {@code chest} 段才算宝箱表。</p>
 */
public final class StructureChestLoot {
    /** 方块 / 实体掉落表所在目录：这些目录里的 chest 段是物品名，不是宝箱表。 */
    private static final String[] NON_CHEST_ROOTS = {"blocks", "block", "entities", "entity"};

    /** 本模组自己的表：内容是自己设计过的，不再往上面叠通用池。 */
    public static boolean isOwnTable(ResourceLocation table) {
        return table != null && table.getNamespace().equals(GeneratedMod.MOD_ID);
    }

    /** 任何命名空间下的结构宝箱表（含本模组自己的宝箱表）。 */
    public static boolean isStructureChest(ResourceLocation table) {
        return table != null && isChestPath(table.getPath());
    }

    /** 别的模组（含原版）的结构宝箱表：本模组自己的表排除掉，避免同一份内容叠两遍。 */
    public static boolean isForeignStructureChest(ResourceLocation table) {
        return table != null && !isOwnTable(table) && isChestPath(table.getPath());
    }

    /** 只看路径：这张表是不是「结构宝箱」表。 */
    public static boolean isChestPath(String path) {
        if (path == null || path.isEmpty()) return false;
        if (path.equals("chests") || path.startsWith("chests/")) return true;
        if (path.equals("chest") || path.startsWith("chest/")) return true;
        String[] segments = path.split("/");
        String root = segments[0];
        boolean blockOrEntity = false;
        for (String candidate : NON_CHEST_ROOTS) {
            if (root.equals(candidate)) {
                blockOrEntity = true;
                break;
            }
        }
        if (blockOrEntity) return false;
        String last = segments[segments.length - 1];
        for (int i = 1; i < segments.length; i++) {
            // 结构目录下的 chests 段，如 othermod:structures/goblin/chests/loot。
            if (segments[i].equals("chests")) return true;
        }
        // 最后一段自己就是宝箱表：chest / chest_loot / chests_common。
        return last.equals("chest") || last.startsWith("chest_") || last.startsWith("chests_");
    }

    /**
     * 加一个独立池子；同名池子已经在表里就不再重复加。
     *
     * <p>数据包 {@code /reload}、或者别的模组把同一张表再加载一次时，
     * 这一步能保证本模组的池子只会存在一份，概率不会因为重载翻倍。</p>
     */
    public static void addPool(LootTable table, LootPool pool) {
        if (table == null || pool == null) return;
        // 表已经被别的模组冻结：往里加池子会抛异常，宁可这一张不加，也别让整个数据包加载失败。
        if (table.isFrozen()) return;
        String name = pool.getName();
        if (name != null && !name.isEmpty() && table.getPool(name) != null) return;
        table.addPool(pool);
    }

    private StructureChestLoot() { }
}
