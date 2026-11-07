package cn.blockforge.generated.minecraft1211n422a;

/** 界面几何的唯一来源；只接收真实槽位，便于离屏测试同一套布局。 */
public final class ArcaneLayout {
    public enum Kind { GRINDER, FURNACE, PROCESSOR, WEAPON, GRINDSTONE, ANVIL }

    /** 坐标为面板内坐标；所有绘制、悬停和点击共用同一个矩形。 */
    public record Box(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public int cx() { return x + width / 2; }
        public int cy() { return y + height / 2; }
        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
        public Box pad(int n) { return new Box(x - n, y - n, width + 2 * n, height + 2 * n); }
    }

    public record Layout(Box machine, Box inventory, Box button, Box arrow, Box flame,
                         Box conduit, Box plate, Box mana, Box fuel, Box name,
                         int inventoryLabelX, int inventoryLabelY) { }
    private static final Box EMPTY = new Box(0, 0, 0, 0);
    private ArcaneLayout() { }

    private static Box bounds(int[][] slots, int from, int to) {
        int x = Integer.MAX_VALUE, y = Integer.MAX_VALUE, right = 0, bottom = 0;
        for (int i = from; i < to; i++) {
            if (slots[i][0] < 0 || slots[i][1] < 0) continue;
            x = Math.min(x, slots[i][0]); y = Math.min(y, slots[i][1]);
            right = Math.max(right, slots[i][0] + 16); bottom = Math.max(bottom, slots[i][1] + 16);
        }
        return x == Integer.MAX_VALUE ? EMPTY : new Box(x, y, right - x, bottom - y);
    }

    private static Box slot(int[][] slots, int i) { return new Box(slots[i][0], slots[i][1], 16, 16); }
    private static Box arrow(Box input, Box output) {
        int left = input.right() + 6;
        return new Box(left, output.cy() - 3, Math.max(6, output.x() - 6 - left), 6);
    }
    private static Box button(int center, int top, int width) { return new Box(center - width / 2, top, width, 18); }

    public static Layout of(Kind kind, int[][] slots) {
        int machineCount = slots.length - 36;
        if (machineCount < 2) throw new IllegalArgumentException("界面至少需要两个机器槽位和三十六个背包槽位");
        Box m = bounds(slots, 0, machineCount), inv = bounds(slots, machineCount, slots.length);
        Box a = slot(slots, 0), b = slot(slots, 1);
        Box button = EMPTY, arrow = EMPTY, flame = EMPTY, conduit = EMPTY, plate = EMPTY;
        Box mana = EMPTY, fuel = EMPTY, name = EMPTY;
        switch (kind) {
            case GRINDER -> {
                arrow = arrow(a, b);
                button = button((a.cx() + b.cx()) / 2, m.bottom() + 6, 64);
            }
            case FURNACE -> {
                Box out = slot(slots, 2);
                arrow = arrow(a, out);
                int bottom = b.y() - 2, top = a.bottom() + 2;
                flame = new Box((a.cx() + b.cx()) / 2 - 8, top, 16, Math.max(0, bottom - top));
            }
            case PROCESSOR -> {
                Box inputs = bounds(slots, 0, 9), outputs = bounds(slots, 9, 13), f = slot(slots, 13);
                arrow = arrow(slot(slots, 2), slot(slots, 9));
                fuel = new Box(f.cx() - 16, f.y() - 12, 32, 4);
                mana = new Box(inputs.x(), m.bottom() + 7, outputs.right() - inputs.x(), 6);
            }
            case WEAPON -> {
                int axis = (a.cx() + b.cx()) / 2;
                conduit = new Box(axis - 3, a.bottom() + 3, 6, b.y() - a.bottom() - 6);
                button = button(axis, b.bottom() + 16, 76);
                int plateX = button.right() + 14;
                int plateTop = a.y() - 12;
                plate = new Box(plateX, plateTop, inv.width(), inv.y() - 18 - plateTop);
            }
            case GRINDSTONE -> {
                Box out = slot(slots, 2);
                arrow = arrow(a, out);
                button = button((a.cx() + out.cx()) / 2, m.bottom() + 8, 98);
            }
            case ANVIL -> {
                Box out = slot(slots, 2);
                arrow = arrow(b, out);
                name = new Box(b.x() - 16, a.y() - 25, inv.right() - (b.x() - 16) - 1, 16);
            }
        }
        // 功能分区随槽位伸缩，不再额外绘制标题分割线或金色角标。
        Box machine = m.pad(5);
        int zoneTop = Math.max(16, machine.y());
        machine = new Box(machine.x(), zoneTop, machine.width(), machine.bottom() - zoneTop);
        return new Layout(machine, inv.pad(4), button, arrow, flame, conduit, plate, mana, fuel, name,
                inv.x(), inv.y() - 13);
    }
}
