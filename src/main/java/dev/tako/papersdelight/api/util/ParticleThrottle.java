package dev.tako.papersdelight.api.util;

import java.util.concurrent.ThreadLocalRandom;

/** 粒子与音效的密度节流: 附近同类粒子源太多时按概率跳过一部分, PD 各 Manager 与附属插件共用同一份公式. */
public final class ParticleThrottle {

    private ParticleThrottle() {}

    /** 这次该保留的概率, 范围 [minimumRate, 1.0]; 不超过 threshold 时恒为 1.0. */
    public static double retentionRate(int nearby, int threshold, double minimumRate) {
        if (nearby <= threshold) return 1.0;
        return Math.max(minimumRate, (double) threshold / nearby);
    }

    /** {@code true} 表示这次该跳过. */
    public static boolean shouldSkip(int nearby, int threshold, double minimumRate) {
        double rate = retentionRate(nearby, threshold, minimumRate);
        return ThreadLocalRandom.current().nextDouble() > rate;
    }
}
