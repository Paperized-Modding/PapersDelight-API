package dev.tako.papersdelight.api.heat;

import org.bukkit.block.Block;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 热源判定入口: PapersDelight 启动时注册实现, 附属插件用它判断方块此刻是不是激活热源.
 * <p><strong>实现没注册或判定抛异常时返回 {@code false}</strong>, 判不出来就当它不是才安全.
 */
public final class HeatSourceGate {

    private static volatile Predicate<Block> service;
    private static volatile boolean warned;

    private HeatSourceGate() {
    }

    /** 通常在 PapersDelight 启动时调用; 传 {@code null} 会抛异常. */
    public static void register(Predicate<Block> resolver) {
        service = Objects.requireNonNull(resolver, "resolver");
        warned = false;
    }

    /** 通常在 PapersDelight 停止时调用. */
    public static void unregister() {
        service = null;
    }

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

    public static boolean isUnavailable() {
        return service == null;
    }

    private static void warnOnce(String message, Throwable t) {
        if (warned) return;
        warned = true;
        Logger.getLogger("PapersDelight").log(Level.WARNING, message, t);
    }
}
