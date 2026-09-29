package dev.tako.papersdelight.api.config;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * 一次 CraftEngine parser 注册事务共享的生命周期 token。
 * CE 26.8 无法从 BuiltInRegistries.CONFIG_PARSER 移除 parser，实例在进程内常驻，
 * 因此每轮 enable 通过轮换 generation 来切换生效批次，失活批次必须自行拒绝发布。
 * parser publication 与 unregister/reset 共用同一把读写锁，避免旧 generation
 * 在失活后覆盖静态快照。
 */
public final class ParserGeneration {
    private static final AtomicLong IDS = new AtomicLong();
    private static final ReentrantReadWriteLock COMMIT_LOCK = new ReentrantReadWriteLock(true);

    private final long id = IDS.incrementAndGet();
    private volatile boolean active;

    private ParserGeneration(boolean active) {
        this.active = active;
    }

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

    /** 在共享 publication 读锁内再次确认 generation，并完成候选构造与发布。 */
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

    /** 在独占锁内执行 generation 切换、失活和快照重置。 */
    public static void runExclusive(Runnable action) {
        supplyExclusive(() -> {
            action.run();
            return null;
        });
    }

    /**
     * 在阻塞所有 parser publication 的同一把锁内读取跨 parser 状态，避免拿到半更新组合。
     */
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
