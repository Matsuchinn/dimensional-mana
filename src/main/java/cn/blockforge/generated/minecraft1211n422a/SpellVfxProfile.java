package cn.blockforge.generated.minecraft1211n422a;

/** 元素美术节奏表：数量、转速、寿命分开控制，不能所有元素套同一密度。 */
final class SpellVfxProfile {
    record Profile(double framesPerTick, int gathering, int ambient, int trailRate, int trailLife) { }
    private static final Profile FIRE = new Profile(0.60, 5, 12, 2, 14);
    private static final Profile WATER = new Profile(0.38, 3, 7, 2, 12);
    private static final Profile ICE = new Profile(0.24, 3, 6, 1, 14);
    private static final Profile EARTH = new Profile(0.18, 2, 4, 1, 9);
    private static final Profile NATURE = new Profile(0.30, 4, 10, 1, 18);
    private static final Profile POISON = new Profile(0.26, 3, 9, 1, 22);
    private static final Profile METAL = new Profile(0.20, 2, 4, 1, 8);
    private static final Profile WIND = new Profile(0.44, 3, 6, 1, 9);
    private static final Profile ARCANE = new Profile(0.32, 4, 8, 2, 12);
    private static final Profile LIGHT = new Profile(0.25, 2, 5, 1, 11);
    private static final Profile LIGHTNING = new Profile(0.75, 3, 7, 2, 7);
    private static final Profile SOUND = new Profile(0.28, 2, 5, 1, 10);
    static Profile of(SpellDefinition spell) {
        return switch (spell.medium()) {
            case "fire" -> FIRE;
            case "water" -> WATER;
            case "ice" -> ICE;
            case "earth" -> EARTH;
            case "nature" -> NATURE;
            case "poison" -> POISON;
            case "metal" -> METAL;
            case "wind" -> WIND;
            case "light" -> LIGHT;
            case "lightning" -> LIGHTNING;
            case "sound" -> SOUND;
            default -> ARCANE;
        };
    }
    static double smooth(double t) { t = Math.clamp(t, 0, 1); return t * t * (3 - 2 * t); }
    private SpellVfxProfile() { }
}
