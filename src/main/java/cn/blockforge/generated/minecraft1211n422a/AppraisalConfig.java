package cn.blockforge.generated.minecraft1211n422a;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 专业鉴定眼镜的魔素消耗、雷达扫描参数与 HUD 排布开关。
 *
 * <p>玩家反馈「信息太多、想自己调」的部分都放在这里：哪些条目要显示、
 * 提示文字往哪偏、抗性最多列几条，都能在 {@code config/dimensional_mana-appraisal.toml} 里改。</p>
 */
public final class AppraisalConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue IDLE_MANA_COST;
    public static final ModConfigSpec.DoubleValue LOCK_MANA_COST;
    public static final ModConfigSpec.DoubleValue LOCK_RANGE;
    public static final ModConfigSpec.DoubleValue SCAN_MANA_COST;
    public static final ModConfigSpec.DoubleValue SCAN_RADIUS;
    public static final ModConfigSpec.IntValue SCAN_DURATION_TICKS;
    public static final ModConfigSpec.IntValue SYNC_INTERVAL_TICKS;

    public static final ModConfigSpec.BooleanValue SHOW_TARGET_BARS;
    public static final ModConfigSpec.BooleanValue SHOW_REGEN_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_RESISTANCE_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_SKILL_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_SCANLINE;
    public static final ModConfigSpec.BooleanValue SHOW_RADAR;
    public static final ModConfigSpec.IntValue HUD_OFFSET_X;
    public static final ModConfigSpec.IntValue HUD_OFFSET_Y;
    public static final ModConfigSpec.IntValue MAX_RESISTANCE_ENTRIES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("专业鉴定眼镜：魔素消耗、雷达扫描与 HUD 显示设置。",
                        "Professional Appraisal Glasses: mana costs, radar scan and HUD layout.",
                        "眼镜戴在「首饰」栏位；魔素为 0 时只会退回「只显示生命值」的基础模式。")
                .push("appraisal");
        IDLE_MANA_COST = b.comment("佩戴时每秒的基础魔素消耗（默认 0.8）。",
                        "只要戴在首饰栏就会扣，不需要拿在手上；扣到 0 为止，不会因此进入魔力枯竭。",
                        "Mana drained per second while equipped.")
                .translation("config.dimensional_mana.appraisal.idle_mana_cost")
                .defineInRange("idle_mana_cost", 0.8, 0.0, 10000.0);
        LOCK_MANA_COST = b.comment("准心锁定生物、解析其深层数据时，每秒额外消耗的魔素（默认 0.5）。",
                        "与基础消耗叠加：锁定状态下总计 0.8 + 0.5 = 1.3 魔素/秒。",
                        "Extra mana per second while a mob is under the crosshair.")
                .translation("config.dimensional_mana.appraisal.lock_mana_cost")
                .defineInRange("lock_mana_cost", 0.5, 0.0, 10000.0);
        LOCK_RANGE = b.comment("准心锁定的最远距离，单位格（默认 12.0）。",
                        "超过这个距离就只显示环境浓度，不解析生物数据。",
                        "Maximum range (in blocks) for locking a target.")
                .translation("config.dimensional_mana.appraisal.lock_range")
                .defineInRange("lock_range", 12.0, 1.0, 128.0);
        SCAN_MANA_COST = b.comment("主动扫描（默认 V 键）一次消耗的魔素（默认 20）。",
                        "魔素不足时不会发出扫描，也不会扣魔素。",
                        "Mana cost of one active radar scan.")
                .translation("config.dimensional_mana.appraisal.scan_mana_cost")
                .defineInRange("scan_mana_cost", 20.0, 0.0, 100000.0);
        SCAN_RADIUS = b.comment("主动扫描的半径，单位格（默认 32.0）。")
                .translation("config.dimensional_mana.appraisal.scan_radius")
                .defineInRange("scan_radius", 32.0, 1.0, 256.0);
        SCAN_DURATION_TICKS = b.comment("主动扫描的轮廓光持续多少刻（默认 100 刻 = 5 秒）。")
                .translation("config.dimensional_mana.appraisal.scan_duration_ticks")
                .defineInRange("scan_duration_ticks", 100, 1, 72000);
        SYNC_INTERVAL_TICKS = b.comment("服务端把解析结果同步给客户端的间隔（默认 5 刻 = 0.25 秒）。",
                        "调小看起来更流畅但发包更频繁；调大到 20 刻就是每秒刷新一次。",
                        "How often (in ticks) the server pushes appraisal data to the client.")
                .translation("config.dimensional_mana.appraisal.sync_interval_ticks")
                .defineInRange("sync_interval_ticks", 5, 1, 100);
        b.pop();

        b.comment("HUD 显示开关：不想看哪一类就关掉哪一类。",
                        "HUD toggles; turn off any part you do not want to see.")
                .push("appraisal_hud");
        SHOW_TARGET_BARS = b.comment("显示目标名称与生命 / 魔素 / 斗气三条状态条（默认开启）。")
                .translation("config.dimensional_mana.appraisal_hud.show_target_bars")
                .define("show_target_bars", true);
        SHOW_REGEN_LINE = b.comment("在准心下方显示「M恢复 / B恢复」每秒恢复量（默认开启）。")
                .translation("config.dimensional_mana.appraisal_hud.show_regen_line")
                .define("show_regen_line", true);
        SHOW_RESISTANCE_LINE = b.comment("在准心下方显示抗性与易伤（默认开启）。")
                .translation("config.dimensional_mana.appraisal_hud.show_resistance_line")
                .define("show_resistance_line", true);
        SHOW_SKILL_LINE = b.comment("显示目标专属技能的冷却状态（默认开启）。")
                .translation("config.dimensional_mana.appraisal_hud.show_skill_line")
                .define("show_skill_line", true);
        SHOW_SCANLINE = b.comment("在屏幕右侧显示生命 / 魔素 / 斗气的时间轴扫描小窗（默认开启）。")
                .translation("config.dimensional_mana.appraisal_hud.show_scanline")
                .define("show_scanline", true);
        SHOW_RADAR = b.comment("允许使用主动扫描的轮廓光（关闭后按键不生效、也不扣魔素）。")
                .translation("config.dimensional_mana.appraisal_hud.show_radar")
                .define("show_radar", true);
        HUD_OFFSET_X = b.comment("目标面板距离准心向右的像素偏移（默认 12）。",
                        "右边放不下时面板会自动翻到准心左侧。")
                .translation("config.dimensional_mana.appraisal_hud.hud_offset_x")
                .defineInRange("hud_offset_x", 12, -400, 400);
        HUD_OFFSET_Y = b.comment("目标面板相对准心的上下微调（默认 0 = 上下居中）。",
                        "负数往上、正数往下；面板本来就以准心为中心，不需要再手动挪。")
                .translation("config.dimensional_mana.appraisal_hud.hud_offset_y")
                .defineInRange("hud_offset_y", 0, -400, 400);
        MAX_RESISTANCE_ENTRIES = b.comment("抗性 / 易伤每行最多列几条（默认 3），超出部分用「+N」表示。")
                .translation("config.dimensional_mana.appraisal_hud.max_resistance_entries")
                .defineInRange("max_resistance_entries", 3, 1, 12);
        b.pop();
        SPEC = b.build();
    }

    private AppraisalConfig() {}
}
