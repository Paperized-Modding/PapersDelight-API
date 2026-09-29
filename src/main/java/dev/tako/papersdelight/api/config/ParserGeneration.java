package dev.tako.papersdelight.api.config;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * CraftEngine parser 的一轮注册, 只有生效的那一轮能发布数据.
 * <p>PapersDelight 每轮加载换一个新 generation, 旧的自动失活; 发布与失活共用一把读写锁, 旧 generation 覆盖不了新快照.
 */
public final class ParserGeneration {
    private static final AtomicLong IDS = new AtomicLong();
    private static final ReentrantReadWriteLock COMMIT_LOCK = new ReentrantReadWriteLock(true);

    private final long id = IDS.incrementAndGet();
    private volatile boolean active;

    private ParserGeneration(boolean active) {
        this.active = active;
    }

    /** 创建一个还没生效的 generation, 准备好之后再 {@link #activate()}. */
    public static ParserGeneration candidate() {
        return new ParserGeneration(false);
    }

    public static ParserGeneration active() {
        return new ParserGeneration(true);
    }

    public long id() {
        return id;
    }

    public boolean isActive() {
        return active;
    }

    public void activate() {
        runExclusive(() -> active = true);
    }

    /** 已失活时返回 {@code false}. */
    public boolean commitIfActive(Runnable commit) {
        COMMIT_LOCK.readLock().lock();
        try {
            if (!active) return false;
            commit.run();
            return true;
        } finally {
            COMMIT_LOCK.readLock().unlock();
        }
    }

    /** 在独占锁内执行 generation 切换, 失活或快照重置. */
    public static void runExclusive(Runnable action) {
        supplyExclusive(() -> {
            action.run();
            return null;
        });
    }

    /** 在独占锁内读跨 parser 的状态, 避免读到改到一半的组合. */
    public static <T> T supplyExclusive(Supplier<T> action) {
        COMMIT_LOCK.writeLock().lock();
        try {
            return action.get();
        } finally {
            COMMIT_LOCK.writeLock().unlock();
        }
    }

    public void invalidate() {
        runExclusive(() -> active = false);
    }
}
