package dev.tako.papersdelight.api.util;

import java.util.concurrent.ThreadLocalRandom;

/** 粒子/音效密度节流: 同一区域同时活跃的粒子源太多时按比例跳过一部分. */
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
