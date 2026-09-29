package dev.tako.papersdelight.api.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 粒子与音效的密度节流: 附近同类粒子源太多时, 按概率随机跳过一部分.
 * <p>PD 各 Manager 与附属插件共用同一份公式.
 */
public final class ParticleThrottle {

    private ParticleThrottle() {}

    /**
     * 计算这次的保留概率.
     *
     * @param nearby 附近的同类粒子源数量
     * @param threshold 超过这个数才开始节流
     * @param minimumRate 保底保留率, 别节流过头
     * @return 保留率, 范围 [minimumRate, 1.0]
     */
    public static double retentionRate(int nearby, int threshold, double minimumRate) {
        if (nearby <= threshold) return 1.0;
        return Math.max(minimumRate, (double) threshold / nearby);
    }

    /**
     * 按保留率掷一次骰子.
     *
     * @return {@code true} 表示这次该跳过
     */
    public static boolean shouldSkip(int nearby, int threshold, double minimumRate) {
        double rate = retentionRate(nearby, threshold, minimumRate);
        return ThreadLocalRandom.current().nextDouble() > rate;
    }
}
