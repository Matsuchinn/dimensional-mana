package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

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
    /** 参数是一个 {@link Component}（而不是纯文本）时的前缀标记。 */
    private static final char COMPONENT = '\u0002';
    /** Component 内部各段参数的分隔符。 */
    private static final char SUB = '\u0003';
    /** 转义符：让参数里的分隔符不打架。 */
    private static final char ESCAPE = '\u0004';

    public static String encode(String key, Object... args) {
        if (args == null || args.length == 0) return key;
        StringBuilder builder = new StringBuilder(key);
        for (Object arg : args) {
            builder.append(SEP).append(encodeArg(arg));
        }
        return builder.toString();
    }

    /**
     * 参数编码：普通值照旧转字符串；{@link Component}（声明组名、词缀名、元素简称这些
     * 本身还要跟着语言变短词）写成带标记的 Component 串，客户端解码后仍是可翻译组件。
     *
     * <p>这样「终端 #3 缺声明（召唤边界）」在英文环境里显示的是 Summon，而不是中文的「召唤边界」。</p>
     */
    private static String encodeArg(Object arg) {
        if (arg == null) return "";
        if (arg instanceof Component component) return COMPONENT + encodeComponent(component);
        return escape(String.valueOf(arg));
    }

    private static String encodeComponent(Component component) {
        StringBuilder builder = new StringBuilder();
        if (component.getContents() instanceof TranslatableContents translatable) {
            builder.append('T').append(escape(translatable.getKey()));
            for (Object arg : translatable.getArgs()) {
                builder.append(SUB).append(encodeArg(arg));
            }
            return builder.toString();
        }
        if (!component.getSiblings().isEmpty()) {
            builder.append('S');
            for (Component sibling : component.getSiblings()) {
                builder.append(SUB).append(COMPONENT).append(encodeComponent(sibling));
            }
            return builder.toString();
        }
        // 纯文本。
        builder.append('L').append(escape(component.getString()));
        return builder.toString();
    }

    private static String escape(String raw) {
        StringBuilder builder = new StringBuilder(raw.length() + 4);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ESCAPE || c == SEP || c == COMPONENT || c == SUB) builder.append(ESCAPE);
            builder.append(c);
        }
        return builder.toString();
    }

    private static String unescape(String raw) {
        StringBuilder builder = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ESCAPE && i + 1 < raw.length()) {
                builder.append(raw.charAt(++i));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    /** 把一个 Component 串还原成组件；认不得的退回纯文本。 */
    private static Component decodeComponent(String raw) {
        if (raw.isEmpty()) return Component.literal("");
        char kind = raw.charAt(0);
        String body = raw.substring(1);
        if (kind == 'T') {
            int at = body.indexOf(SUB);
            String key = unescape(at < 0 ? body : body.substring(0, at));
            if (at < 0) return Component.translatable(key);
            List<Object> values = new ArrayList<>();
            int start = at + 1;
            while (true) {
                int next = body.indexOf(SUB, start);
                String piece = next < 0 ? body.substring(start) : body.substring(start, next);
                values.add(decodeComponentArg(piece));
                if (next < 0) break;
                start = next + 1;
            }
            return Component.translatable(key, values.toArray());
        }
        if (kind == 'S') {
            MutableComponent joined = Component.empty();
            int start = 0;
            while (start <= body.length()) {
                int next = body.indexOf(SUB, start);
                String piece = next < 0 ? body.substring(start) : body.substring(start, next);
                if (!piece.isEmpty() && piece.charAt(0) == COMPONENT) joined.append(decodeComponent(piece.substring(1)));
                start = next < 0 ? body.length() + 1 : next + 1;
            }
            return joined;
        }
        return Component.literal(unescape(body));
    }

    private static Object decodeComponentArg(String raw) {
        if (!raw.isEmpty() && raw.charAt(0) == COMPONENT) return decodeComponent(raw.substring(1));
        return asNumberOrText(raw);
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
            String raw = args.get(i);
            if (!raw.isEmpty() && raw.charAt(0) == COMPONENT) {
                values[i] = decodeComponent(raw.substring(1));
            } else {
                values[i] = asNumberOrText(raw);
            }
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
