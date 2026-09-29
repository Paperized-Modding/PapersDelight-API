package dev.tako.papersdelight.api.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 粒子密度节流工具 —— 统一实现 nearby/threshold 节流公式。
 * <p>当同区域粒子源过多时，按保留率概率性跳过部分粒子/音效，保持性能。</p>
 *
 * <p>位于 api 模块，供 PapersDelight 各 Manager 与下游插件（如 PapersBrewin
 * 芳杜锅蒸汽/沸腾音）共用同一份节流公式，避免多份实现漂移。</p>
 */
public final class ParticleThrottle {

    private ParticleThrottle() {}

    /**
     * 计算保留率（retention rate）。
     *
     * @param nearby 附近粒子源数量
     * @param threshold 阈值，nearby ≤ threshold 时总是返回 1.0
     * @param minimumRate 保底保留率，避免过度节流
     * @return 保留率，范围 [minimumRate, 1.0]
     */
    public static double retentionRate(int nearby, int threshold, double minimumRate) {
        if (nearby <= threshold) return 1.0;
        return Math.max(minimumRate, (double) threshold / nearby);
    }

    /**
     * 根据保留率判断是否应跳过当前粒子/音效。
     *
     * @param nearby 附近粒子源数量
     * @param threshold 阈值
     * @param minimumRate 保底保留率
     * @return true 表示应跳过，false 表示应执行
     */
    public static boolean shouldSkip(int nearby, int threshold, double minimumRate) {
        double rate = retentionRate(nearby, threshold, minimumRate);
        return ThreadLocalRandom.current().nextDouble() > rate;
    }
}
