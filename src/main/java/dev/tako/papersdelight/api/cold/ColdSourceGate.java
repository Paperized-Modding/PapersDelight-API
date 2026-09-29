package dev.tako.papersdelight.api.cold;

import org.bukkit.block.Block;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 冷源判定门面 —— 由 PapersBrewin payload 在初始化时注册实现。
 *
 * <p>与 {@code HeatSourceGate} 对称：PapersDelight 需要判断某方块是否为激活冷源
 * （用于 BaC 进度 {@code place_temperature_block_near_keg}），但冷源清单
 * （{@code keg.temperature.freeze-sources}）是 PapersBrewin 的配置真相，PD 读不到。
 * 因此由 PapersBrewin push 注册判定实现，PD 通过本门面查询。</p>
 *
 * <p>失败时返回 {@code false}：PB 不可用时视作非冷源是安全降级（进度只是不触发，
 * 不会误判）。</p>
 */
public final class ColdSourceGate {

    private static volatile Predicate<Block> service;
    private static volatile boolean warned;

    private ColdSourceGate() {
    }

    /** 注册 PapersBrewin 的冷源判定实现。传入 null 会抛出异常。 */
    public static void register(Predicate<Block> resolver) {
        service = Objects.requireNonNull(resolver, "resolver");
        warned = false;
    }

    /** 注销当前实现，通常在 PapersBrewin payload 停止时调用。 */
    public static void unregister() {
        service = null;
    }

    /**
     * 方块本身此刻是否为激活冷源（依 PapersBrewin 的 freeze-sources 配置）。
     *
     * @param block 目标方块
     * @return true 当且仅当已注册实现判定为激活冷源；未注册或异常时返回 false
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

    /** 供测试与诊断：当前是否没有可用的冷源实现。 */
    public static boolean isUnavailable() {
        return service == null;
    }

    private static void warnOnce(String message, Throwable t) {
        if (warned) return;
        warned = true;
        Logger.getLogger("PapersDelight").log(Level.WARNING, message, t);
    }
}
