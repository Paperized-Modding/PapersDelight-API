package dev.tako.papersdelight.api.heat;

import org.bukkit.block.Block;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 热源判定门面 —— 由 PapersDelight Server payload 在初始化时注册实现。
 *
 * <p>使用 push 注册而不是从 Client 类加载器反射查找 Server 类，避免正式云载环境中的
 * 父子类加载器隔离导致 {@code HeatSourceService} 不可见。</p>
 *
 * <p>失败时返回 {@code false}：热源是叠加正效果，PD 不可用时视作非热源才是安全降级。</p>
 */
public final class HeatSourceGate {

    private static volatile Predicate<Block> service;
    private static volatile boolean warned;

    private HeatSourceGate() {
    }

    /** 注册 PapersDelight 的热源判定实现。传入 null 会抛出异常。 */
    public static void register(Predicate<Block> resolver) {
        service = Objects.requireNonNull(resolver, "resolver");
        warned = false;
    }

    /** 注销当前实现，通常在 Server payload 停止时调用。 */
    public static void unregister() {
        service = null;
    }

    /**
     * 方块本身此刻是否为激活热源（依 PapersDelight 的 heat_sources 配置）。
     *
     * @param block 目标方块
     * @return true 当且仅当已注册实现判定为激活热源；未注册或异常时返回 false
     */
    public static boolean isActiveHeatSource(Block block) {
        if (block == null) return false;
        Predicate<Block> resolver = service;
        if (resolver == null) return false;
        try {
            return resolver.test(block);
        } catch (Throwable t) {
            warnOnce("PapersDelight 热源判定失败，本次视作非热源", t);
            return false;
        }
    }

    /** 供测试与诊断：当前是否没有可用的热源实现。 */
    public static boolean isUnavailable() {
        return service == null;
    }

    private static void warnOnce(String message, Throwable t) {
        if (warned) return;
        warned = true;
        Logger.getLogger("PapersDelight").log(Level.WARNING, message, t);
    }
}
