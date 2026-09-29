package dev.tako.papersdelight.api.cold;

import org.bukkit.block.Block;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 冷源判定入口: 冷源清单是 PapersBrewin 的配置, 所以由 PapersBrewin 启动时注册实现, PapersDelight 通过这里查询.
 * <p><strong>实现没注册或判定抛异常时返回 {@code false}</strong>, 拿不准就当非冷源, 顶多相关进度不触发, 不会误判.
 */
public final class ColdSourceGate {

    private static volatile Predicate<Block> service;
    private static volatile boolean warned;

    private ColdSourceGate() {
    }

    /** 注册冷源判定实现, 通常在 PapersBrewin 启动时调用; 传 {@code null} 会抛异常. */
    public static void register(Predicate<Block> resolver) {
        service = Objects.requireNonNull(resolver, "resolver");
        warned = false;
    }

    /** 注销实现, 通常在 PapersBrewin 停止时调用. */
    public static void unregister() {
        service = null;
    }

    /**
     * 方块此刻是不是激活冷源(依 PapersBrewin 的 freeze-sources 配置).
     *
     * @param block 目标方块
     * @return 是冷源返回 {@code true}; 没注册实现或判定失败时返回 {@code false}
     */
    public static boolean isActiveColdSource(Block block) {
        if (block == null) return false;
        Predicate<Block> resolver = service;
        if (resolver == null) return false;
        try {
            return resolver.test(block);
        } catch (Throwable t) {
            warnOnce("PapersBrewin 冷源判定失败，本次视作非冷源", t);
            return false;
        }
    }

    /** 当前没有可用的冷源实现, 供测试与诊断. */
    public static boolean isUnavailable() {
        return service == null;
    }

    private static void warnOnce(String message, Throwable t) {
        if (warned) return;
        warned = true;
        Logger.getLogger("PapersDelight").log(Level.WARNING, message, t);
    }
}
