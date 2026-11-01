package cn.blockforge.generated.minecraft1211n422a;

import java.lang.reflect.Method;

/**
 * 光影包探测：判断当前帧的透明绘制会不会被光影接管。
 *
 * <p>原版管线里，贴图 quad 走<b>加色混合</b>（亮的叠加、暗的等于透明），这是法阵辉光的全部原理。
 * 一旦开了 Iris / Oculus / OptiFine 的光影包，绘制会被光影自己的 shader 程序接管，
 * 混合方程、alpha 处理全都由光影包说了算——加色通道常常被当成普通半透明甚至不透明来合成，
 * 于是「柔光垫」就变成一块块实心盘子。</p>
 *
 * <p>这里用反射探测（不硬依赖任何光影 mod，装了哪一个都能识别），结果缓存 2 秒：
 * 玩家在视频设置里开开关关光影，最多两帧之后就跟上。</p>
 */
public final class ShaderCompat {

    /** 0 = 自动：检测到光影包就切兼容渲染。 */
    public static final int MODE_AUTO = 0;
    /** 1 = 强制兼容：无视探测结果，永远走半透明通道。 */
    public static final int MODE_FORCE_COMPAT = 1;
    /** 2 = 强制原版：永远走加色辉光（光影下会看到盘子，但原版下最华丽）。 */
    public static final int MODE_FORCE_VANILLA = 2;

    /** 探测结果缓存：2 秒刷新一次，配置改成强制项时立即生效。 */
    private static final long REFRESH_NANOS = 2_000_000_000L;

    private static volatile int forcedMode = MODE_AUTO;
    private static long nextProbeAt;
    private static boolean cachedShaderInUse;

    /** 渲染入口调这个：true = 本帧应使用光影兼容渲染（普通半透明、不画实心伪辉光垫）。 */
    public static boolean compatActive() {
        int mode = mode();
        if (mode == MODE_FORCE_COMPAT) return true;
        if (mode == MODE_FORCE_VANILLA) return false;
        return shaderInUse();
    }

    /** 当前生效的模式（配置读不到时退回自动）。 */
    public static int mode() {
        if (forcedMode != MODE_AUTO) return forcedMode;
        try {
            return RenderCompatConfig.MAGIC_CIRCLE_MODE.get();
        } catch (Throwable ignored) {
            return MODE_AUTO;   // 配置尚未加载（极早期帧）：按自动处理
        }
    }

    /** 供调试 / 后续界面覆盖用（-1 表示交还给配置文件）。 */
    public static void setForcedMode(int mode) {
        forcedMode = mode;
    }

    /** 是否正在使用光影（或任何会接管透明混合的分层缓冲管线）。缓存 2 秒。 */
    public static boolean shaderInUse() {
        long now = System.nanoTime();
        if (now >= nextProbeAt) {
            nextProbeAt = now + REFRESH_NANOS;
            cachedShaderInUse = probeIris() || probeOptifine();
        }
        return cachedShaderInUse;
    }

    // ---------------------------------------------------------------- 探测实现

    /** Iris / Oculus：IrisApi.getInstance().isShaderPackInUse()。两个历史包名都试。 */
    private static boolean probeIris() {
        String[] classes = {
                "net.irisshaders.iris.api.v0.IrisApi",   // 1.20.2+（Oculus 移植版也是这个名）
                "net.coderbot.iris.api.v0.IrisApi",      // 1.16~1.20.1
        };
        for (String cn : classes) {
            try {
                Class<?> api = Class.forName(cn, false, ShaderCompat.class.getClassLoader());
                Object instance = invokeStatic(api, "getInstance");
                if (instance == null) continue;
                Method m = api.getMethod("isShaderPackInUse");
                Object r = m.invoke(instance);
                if (r instanceof Boolean b && b) return true;
            } catch (Throwable ignored) {
                // 没装、或 API 签名不同：换下一种
            }
        }
        return false;
    }

    /** OptiFine：Config.isShaders()。静态布尔，直接反射。 */
    private static boolean probeOptifine() {
        try {
            Class<?> cfg = Class.forName("net.optifine.Config", false, ShaderCompat.class.getClassLoader());
            Object r = invokeStatic(cfg, "isShaders");
            return r instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object invokeStatic(Class<?> owner, String method) {
        try {
            return owner.getMethod(method).invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ShaderCompat() {}
}
