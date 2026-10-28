package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 校验问题串的轻量编码：一条问题 = 语言键 + 若干参数，压成一串存进原有的
 * {@code List<String> problems} 通道（同步负载、绘制界面页脚、调试棒、正式运行提示都走它）。
 *
 * <p>串格式：{@code key\u0001arg1\u0001arg2…}；老存档与老键（不带 \u0001 的纯 key）原样兼容。
 * 参数一律转成字符串，数字用 {@code %d} 语义的十进制文本，界面端再塞回 translatable。</p>
 */
public final class MagicCircleIssue {

    private static final char SEP = '\u0001';

    public static String encode(String key, Object... args) {
        if (args == null || args.length == 0) return key;
        StringBuilder builder = new StringBuilder(key);
        for (Object arg : args) {
            builder.append(SEP).append(arg == null ? "" : String.valueOf(arg));
        }
        return builder.toString();
    }

    public static String key(String encoded) {
        int at = encoded.indexOf(SEP);
        return at < 0 ? encoded : encoded.substring(0, at);
    }

    public static List<String> args(String encoded) {
        List<String> args = new ArrayList<>();
        int at = encoded.indexOf(SEP);
        if (at < 0) return args;
        String rest = encoded.substring(at + 1);
        for (String part : splitLiteral(rest)) args.add(part);
        return args;
    }

    private static List<String> splitLiteral(String rest) {
        List<String> out = new ArrayList<>();
        int start = 0;
        while (true) {
            int at = rest.indexOf(SEP, start);
            if (at < 0) {
                out.add(rest.substring(start));
                return out;
            }
            out.add(rest.substring(start, at));
            start = at + 1;
        }
    }

    /** 变成聊天 / 界面可用的组件；数字参数还原成 Number 让语言文件的格式化符号正常工作。 */
    public static MutableComponent component(String encoded) {
        String key = key(encoded);
        List<String> args = args(encoded);
        if (args.isEmpty()) return Component.translatable(key);
        Object[] values = new Object[args.size()];
        for (int i = 0; i < args.size(); i++) {
            values[i] = asNumberOrText(args.get(i));
        }
        return Component.translatable(key, values);
    }

    private static Object asNumberOrText(String raw) {
        if (!raw.isEmpty() && (Character.isDigit(raw.charAt(0)) || raw.charAt(0) == '-')) {
            try {
                if (raw.indexOf('.') >= 0) return Double.parseDouble(raw);
                return Long.parseLong(raw);
            } catch (NumberFormatException ignored) {
                return raw;
            }
        }
        return raw;
    }

    private MagicCircleIssue() {}
}
