package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 卡顿看门狗：服务器刻停住太久时，把当时的线程栈自己写进 {@code logs/}。
 *
 * <p>玩家反馈过「玩着玩着就卡住、地形不加载、TPS 归零、连退出都退不出去」。这种卡死一般不是崩
 * 溃——游戏没崩，只是主线程被某段代码卡住了，所以 {@code crash-reports} 里什么都没有，
 * 光看日志尾巴也看不出是谁。真正能定位的是<b>卡住那一刻的线程栈</b>，而那一刻玩家自己很难抓到。</p>
 *
 * <p>所以这里起一个极低优先级的后台线程（守护线程，每个服务器一个）：服务器每推进一刻就记一次
 * 时间戳；一旦超过 {@value #HANG_SECONDS} 秒一刻都没推进，并且当时有玩家在线（排除单人游戏
 * 加载世界、退出存档时的正常停顿），就把所有线程的栈写进
 * {@code logs/dimensional_mana-hang-<时间>.txt}，同时往主日志里写一条提示。</p>
 *
 * <p>它只在卡住时干活，正常游玩每秒只比较一次时间戳，对性能没有可测量的影响；
 * 写文件失败也只是少一份现场，不会影响游戏。</p>
 */
public final class TickWatchdog {
    /** 多少秒没有推进才算「卡住」。读档、切维度、自动保存偶尔会有 1～3 秒停顿，别误报。 */
    private static final long HANG_SECONDS = 8L;
    private static final long HANG_NANOS = HANG_SECONDS * 1_000_000_000L;
    /** 两次记录之间至少隔一分钟，免得同一个卡死刷出几十份文件。 */
    private static final long COOLDOWN_NANOS = 60_000_000_000L;

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("dimensional_mana/watchdog");

    /** 服务器刻计数器：只要还在推进，值就会变。 */
    private static volatile long ticks;
    /** 最近一次「刻推进」的时间点（纳秒）。 */
    private static volatile long lastProgress = System.nanoTime();
    private static volatile MinecraftServer server;
    private static volatile Thread thread;
    private static volatile long lastDump;

    private TickWatchdog() { }

    /** 每刻一次：记下「服务器还活着」。 */
    public static void onTick(ServerTickEvent.Post event) {
        ticks++;
        lastProgress = System.nanoTime();
    }

    public static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        lastProgress = System.nanoTime();
        start();
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        server = null;
        Thread running = thread;
        thread = null;
        if (running != null) running.interrupt();
    }

    private static synchronized void start() {
        if (thread != null) return;
        Thread watchdog = new Thread(TickWatchdog::loop, "dimensional_mana-tick-watchdog");
        watchdog.setDaemon(true);
        watchdog.setPriority(Thread.MIN_PRIORITY);
        thread = watchdog;
        watchdog.start();
    }

    private static void loop() {
        while (server != null) {
            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException interrupted) {
                return;
            }
            MinecraftServer current = server;
            if (current == null) return;
            long now = System.nanoTime();
            long stalled = now - lastProgress;
            if (stalled < HANG_NANOS) continue;
            if (now - lastDump < COOLDOWN_NANOS) continue;
            // 单人游戏读档 / 退出存档时主线程也会停几秒，这时候没人在线，不值得记一份现场。
            if (current.getPlayerList().getPlayerCount() <= 0) continue;
            lastDump = now;
            dump(current, stalled);
        }
    }

    /** 把卡住那一刻的线程栈写进 logs/；写不出来就只在主日志里留一句话。 */
    private static void dump(MinecraftServer current, long stalledNanos) {
        long seconds = stalledNanos / 1_000_000_000L;
        Path directory = current.getServerDirectory().resolve("logs");
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = directory.resolve("dimensional_mana-hang-" + stamp + ".txt");
        StringBuilder text = new StringBuilder(32_768);
        text.append("# 「次元物语：魔素」卡顿现场：服务器刻已经 ").append(seconds).append(" 秒没有推进。\n");
        text.append("# 卡住那一刻主线程正在做的事情就在下面的 Server thread 栈里。\n");
        text.append("# 反馈问题时把这个文件、以及 logs/latest.log 一起发出来即可。\n");
        text.append("# 记录时间 ").append(stamp)
                .append("，已推进 ").append(ticks).append(" 刻。\n\n");
        appendStacks(text);
        try {
            Files.createDirectories(directory);
            Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
            LOGGER.warn("检测到服务器刻卡住 {} 秒，已把线程栈写到 {}（下次卡住时把这份文件发出来即可定位）",
                    seconds, file);
        } catch (IOException | RuntimeException failed) {
            LOGGER.warn("检测到服务器刻卡住 {} 秒，但现场文件写不出来（{}）；"
                    + "主线程当时的信息见下面这段栈：", seconds, failed.toString());
            LOGGER.warn(text.toString());
        }
    }

    /** 先写主线程，再写渲染线程，最后按名字排序写其余线程——看得懂的部分排在前面。 */
    private static void appendStacks(StringBuilder text) {
        Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
        List<Thread> ordered = new ArrayList<>(all.keySet());
        ordered.sort(Comparator
                .comparing((Thread t) -> !"Server thread".equals(t.getName()))
                .thenComparing(t -> !"Render thread".equals(t.getName()))
                .thenComparing(Thread::getName));
        for (Thread thread : ordered) {
            text.append("========== ").append(thread.getName())
                    .append(" [").append(thread.getState()).append("] ==========\n");
            for (StackTraceElement element : all.getOrDefault(thread, new StackTraceElement[0])) {
                text.append("    at ").append(element).append('\n');
            }
            text.append('\n');
        }
    }
}
