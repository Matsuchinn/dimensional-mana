package cn.blockforge.generated.minecraft1211n422a;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** 先规划道路和地块，再建房：规划不读取区块，跨区块生成时结果一致。 */
public final class GoblinCampLayout {
    public enum Size {
        SMALL(48, 32, 2), MEDIUM(64, 48, 3), LARGE(80, 64, 4);
        public final int width, depth, gates;
        Size(int width, int depth, int gates) { this.width = width; this.depth = depth; this.gates = gates; }
    }
    public enum Kind { CHIEF, SHAMAN, HUT, GATHERING, FIRE, RUBBISH, TIMBER }
    // 朝向：0 南、1 西、2 北、3 东；局部 +Z 始终是入口。
    public record Plot(Kind kind, int x, int z, int halfW, int halfD, int facing, int style) {
        public int wx(int u, int v) { return x + switch (facing) { case 1 -> -v; case 2 -> -u; case 3 -> v; default -> u; }; }
        public int wz(int u, int v) { return z + switch (facing) { case 1 -> u; case 2 -> -v; case 3 -> -u; default -> v; }; }
        private int eaves() { return kind == Kind.RUBBISH || kind == Kind.TIMBER || kind == Kind.FIRE ? 0 : 1; }
        public int radiusX() { return (facing % 2 == 0 ? halfW : halfD) + eaves(); }
        public int radiusZ() { return (facing % 2 == 0 ? halfD : halfW) + eaves(); }
        public boolean contains(int px, int pz, int margin) {
            return Math.abs(px - x) <= radiusX() + margin && Math.abs(pz - z) <= radiusZ() + margin;
        }
    }
    public record Road(int x1, int z1, int x2, int z2) {
        public boolean contains(int x, int z, int margin) {
            return x >= Math.min(x1, x2) - margin && x <= Math.max(x1, x2) + margin
                    && z >= Math.min(z1, z2) - margin && z <= Math.max(z1, z2) + margin;
        }
    }
    public record Gate(int x, int z, int facing) {}
    public final Size size;
    public final long seed;
    public final List<Plot> plots = new ArrayList<>();
    public final List<Road> roads = new ArrayList<>();
    public final List<Gate> gates = new ArrayList<>();
    public final int cx, cz, west, east, north, south;

    public GoblinCampLayout(Size size, long seed) {
        this.size = size;
        this.seed = seed;
        Random random = new Random(seed);
        cx = size.width / 2; cz = size.depth / 2;
        west = cx - 8; east = cx + 8; north = cz - 7; south = cz + 7;
        plots.add(new Plot(Kind.CHIEF, cx, cz, 5, 4, 0, random.nextInt(3)));
        roads.add(new Road(west, north, east, north));
        roads.add(new Road(east, north, east, south));
        roads.add(new Road(east, south, west, south));
        roads.add(new Road(west, south, west, north));
        roads.add(new Road(cx, cz + 5, cx, south));
        gates.add(new Gate(cx, 1, 2));
        gates.add(new Gate(cx, size.depth - 2, 0));
        roads.add(new Road(cx, 0, cx, north));
        roads.add(new Road(cx, south, cx, size.depth - 1));
        boolean eastGate = size == Size.LARGE || random.nextBoolean();
        if (size.gates >= 3) {
            gates.add(new Gate(eastGate ? size.width - 2 : 1, cz, eastGate ? 3 : 1));
            roads.add(new Road(eastGate ? east : 0, cz, eastGate ? size.width - 1 : west, cz));
        }
        if (size.gates == 4) {
            gates.add(new Gate(1, cz, 1));
            roads.add(new Road(0, cz, west, cz));
        }
        List<Kind> corners = new ArrayList<>(List.of(Kind.SHAMAN, Kind.HUT, Kind.GATHERING, Kind.FIRE));
        Collections.shuffle(corners, random);
        int[][] anchors = {{8, 8}, {size.width - 9, 8}, {8, size.depth - 9}, {size.width - 9, size.depth - 9}};
        for (int i = 0; i < anchors.length; i++) {
            int x = anchors[i][0], z = anchors[i][1];
            addBuilding(new Plot(corners.get(i), x, z, 4, 4, z < cz ? 0 : 2, random.nextInt(3)));
        }
        if (size != Size.SMALL) {
            for (int x : new int[]{cx - 12, cx + 12}) {
                for (int z : new int[]{8, size.depth - 9}) {
                    // 屋宽、屋深、布顶/木顶随机；不牺牲道路和屋檐间距。
                    addBuilding(new Plot(Kind.HUT, x, z, 3 + random.nextInt(2), 3 + random.nextInt(2),
                            z < cz ? 0 : 2, random.nextInt(3)));
                }
            }
        }
        if (size == Size.LARGE) {
            for (int x : new int[]{8, size.width - 9}) {
                for (int z : new int[]{cz - 10, cz + 10}) {
                    addBuilding(new Plot(Kind.HUT, x, z, 3 + random.nextInt(2), 3,
                            x < cx ? 3 : 1, random.nextInt(3)));
                }
            }
        }
        addYard(Kind.RUBBISH, 3, size == Size.SMALL ? 1 : 2, random);
        addYard(Kind.TIMBER, 3, 1, random);
    }

    private void addBuilding(Plot plot) {
        plots.add(plot);
        if (plot.kind == Kind.FIRE) return;
        int x = plot.wx(0, plot.halfD + 1), z = plot.wz(0, plot.halfD + 1);
        int tx = Math.max(west, Math.min(east, x));
        int tz = Math.max(north, Math.min(south, z));
        if (plot.facing % 2 == 0) {
            int turn = size == Size.LARGE && (plot.x < 14 || plot.x > size.width - 15)
                    ? (z < cz ? 16 : size.depth - 17) : tz;
            roads.add(new Road(x, z, x, turn));
            roads.add(new Road(x, turn, tx, turn));
            roads.add(new Road(tx, turn, tx, tz));
        } else {
            roads.add(new Road(x, z, tx, z));
            roads.add(new Road(tx, z, tx, tz));
        }
    }

    private void addYard(Kind kind, int w, int d, Random random) {
        List<int[]> candidates = new ArrayList<>();
        for (int z = 4; z < size.depth - 4; z++)
            for (int x = 5; x < size.width - 5; x++) candidates.add(new int[]{x, z});
        Collections.shuffle(candidates, random);
        for (int[] c : candidates) {
            boolean clear = true;
            for (int x = c[0] - w; x <= c[0] + w && clear; x++) {
                for (int z = c[1] - d; z <= c[1] + d; z++) {
                    if (isRoad(x, z, 2)) { clear = false; break; }
                    for (Plot p : plots) if (p.contains(x, z, 0)) { clear = false; break; }
                    if (!clear) break;
                }
            }
            if (clear) {
                plots.add(new Plot(kind, c[0], c[1], w, d, 0, random.nextInt(3)));
                return;
            }
        }
        throw new IllegalStateException("哥布林营地没有预留杂物地块：" + size + "/" + kind);
    }

    public boolean isRoad(int x, int z, int margin) {
        for (Road road : roads) if (road.contains(x, z, margin)) return true;
        return false;
    }
    public Plot chief() { return plots.getFirst(); }
    public Plot rubbish() { return plots.stream().filter(p -> p.kind == Kind.RUBBISH).findFirst().orElseThrow(); }
    private GoblinCampLayout() { throw new UnsupportedOperationException(); }
}
