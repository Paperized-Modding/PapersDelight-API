package dev.tako.papersdelight.api.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicBoolean;

public final class PaperScheduler {

    private static final Runnable NOOP_RUNNABLE = () -> {
    };
    private static final boolean FOLIA = detectFolia();

    private static final TaskHandle NOOP = new TaskHandle() {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    };

    private PaperScheduler() {
    }

    public static TaskHandle runMainOrRegion(Plugin plugin, Location location, Runnable task) {
        if (location == null || location.getWorld() == null) return NOOP;
        if (isFolia()) {
            return runRegion(plugin, location, task);
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return NOOP;
        }
        return wrap(Bukkit.getScheduler().runTask(plugin, task));
    }

    public static TaskHandle runGlobal(Plugin plugin, Runnable task) {
        return wrap(Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> task.run()));
    }

    public static TaskHandle runGlobalLater(Plugin plugin, Runnable task, long delayTicks) {
        return wrap(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, ignored -> task.run(), normalizeDelay(delayTicks)));
    }

    public static TaskHandle runGlobalTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return wrap(Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                ignored -> task.run(),
                normalizeDelay(delayTicks),
                normalizePeriod(periodTicks)
        ));
    }

    public static TaskHandle runRegion(Plugin plugin, Location location, Runnable task) {
        return runRegion(plugin, location, task, NOOP_RUNNABLE);
    }

    /** 带退休回调的 region 调度 seam；旧 API 保持原有语义。 */
    public static TaskHandle runRegion(Plugin plugin, Location location, Runnable task, Runnable retired) {
        if (location == null || location.getWorld() == null) { retired.run(); return NOOP; }
        return wrap(Bukkit.getRegionScheduler().run(plugin, location, ignored -> task.run()));
    }

    public static TaskHandle runRegionLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (location == null || location.getWorld() == null) return NOOP;
        return wrap(Bukkit.getRegionScheduler().runDelayed(plugin, location, ignored -> task.run(), normalizeDelay(delayTicks)));
    }

    public static TaskHandle runRegionTimer(Plugin plugin, Location location, Runnable task, long delayTicks, long periodTicks) {
        if (location == null || location.getWorld() == null) return NOOP;
        return wrap(Bukkit.getRegionScheduler().runAtFixedRate(
                plugin,
                location,
                ignored -> task.run(),
                normalizeDelay(delayTicks),
                normalizePeriod(periodTicks)
        ));
    }

    public static TaskHandle runChunk(Plugin plugin, World world, int chunkX, int chunkZ, Runnable task) {
        if (world == null) return NOOP;
        return wrap(Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, ignored -> task.run()));
    }

    public static TaskHandle runChunkLater(Plugin plugin, World world, int chunkX, int chunkZ, Runnable task, long delayTicks) {
        if (world == null) return NOOP;
        return wrap(Bukkit.getRegionScheduler().runDelayed(
                plugin,
                world,
                chunkX,
                chunkZ,
                ignored -> task.run(),
                normalizeDelay(delayTicks)
        ));
    }

    /**
     * 一次性异步任务。用于 IO（数据库读写）等不能占用主线程的操作。
     *
     * <p>插件已禁用时降级为当前线程直接执行——关服路径下调度器已不可用。</p>
     */
    public static TaskHandle runAsync(Plugin plugin, Runnable task) {
        if (!plugin.isEnabled()) {
            try {
                task.run();
            } catch (Throwable throwable) {
                plugin.getLogger().warning("异步任务降级执行失败: " + throwable.getMessage());
            }
            return NOOP;
        }
        return wrap(Bukkit.getAsyncScheduler().runNow(plugin, ignored -> task.run()));
    }

    public static TaskHandle runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return wrap(Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                ignored -> task.run(),
                normalizeDelay(delayTicks) * 50L,
                normalizePeriod(periodTicks) * 50L,
                java.util.concurrent.TimeUnit.MILLISECONDS
        ));
    }

    public static TaskHandle runEntity(Plugin plugin, Entity entity, Runnable task) {
        if (entity == null || !entity.isValid()) return NOOP;
        if (!plugin.isEnabled()) {
            try {
                task.run();
            } catch (Throwable ignored) {
                // 旧 API 无退休回调；关服清理仍保持就地执行语义。
            }
            return NOOP;
        }
        return wrap(entity.getScheduler().run(plugin, ignored -> task.run(), NOOP_RUNNABLE));
    }

    /** 带退休回调的 entity 调度 seam；旧 API 保持兼容。 */
    public static TaskHandle runEntity(Plugin plugin, Entity entity, Runnable task, Runnable retired) {
        if (entity == null || !entity.isValid()) { retired.run(); return NOOP; }
        // 插件已禁用（onDisable/关服）时无法注册 scheduler 任务，降级为直接执行。
        // 关服清理（stopAll、手持烤串结算等）依赖该语义，绝不可改走 retired 而跳过。
        if (!plugin.isEnabled()) {
            try {
                task.run();
            } catch (Throwable ignored) {
                retired.run();
            }
            return NOOP;
        }
        // EntityScheduler 可能在返回 null 前已经执行 retired；不要重复调用。
        ScheduledTask scheduled = entity.getScheduler().run(plugin, ignored -> task.run(), retired);
        return wrap(scheduled);
    }

    public static TaskHandle runEntityLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        return runEntityLater(plugin, entity, task, NOOP_RUNNABLE, delayTicks);
    }

    /** 带退休回调的延迟 entity 调度；调度器无法接受任务时保证调用 retired。 */
    public static TaskHandle runEntityLater(Plugin plugin, Entity entity, Runnable task, Runnable retired, long delayTicks) {
        AtomicBoolean retiredCalled = new AtomicBoolean();
        Runnable retiredOnce = () -> {
            if (retiredCalled.compareAndSet(false, true)) retired.run();
        };
        if (entity == null || !entity.isValid() || !plugin.isEnabled()) {
            retiredOnce.run();
            return NOOP;
        }
        ScheduledTask scheduled = entity.getScheduler().runDelayed(
                plugin,
                ignored -> task.run(),
                retiredOnce,
                normalizeDelay(delayTicks)
        );
        if (scheduled == null) retiredOnce.run();
        return wrap(scheduled);
    }

    public static TaskHandle runEntityTimer(Plugin plugin, Entity entity, Runnable task, long delayTicks, long periodTicks) {
        return runEntityTimer(plugin, entity, task, NOOP_RUNNABLE, delayTicks, periodTicks);
    }

    public static TaskHandle runEntityTimer(
            Plugin plugin,
            Entity entity,
            Runnable task,
            Runnable retired,
            long delayTicks,
            long periodTicks) {
        if (entity == null || !entity.isValid() || !plugin.isEnabled()) {
            retired.run();
            return NOOP;
        }
        ScheduledTask scheduled = entity.getScheduler().runAtFixedRate(
                plugin,
                ignored -> task.run(),
                retired,
                normalizeDelay(delayTicks),
                normalizePeriod(periodTicks)
        );
        if (scheduled == null) retired.run();
        return wrap(scheduled);
    }

    private static long normalizeDelay(long ticks) {
        return Math.max(1L, ticks);
    }

    private static long normalizePeriod(long ticks) {
        return Math.max(1L, ticks);
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    private static boolean detectFolia() {
        if (Bukkit.getName().toLowerCase(java.util.Locale.ROOT).contains("folia")
                || Bukkit.getVersion().toLowerCase(java.util.Locale.ROOT).contains("folia")) {
            return true;
        }

        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false, Bukkit.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static TaskHandle wrap(ScheduledTask task) {
        if (task == null) return NOOP;
        return new PaperTaskHandle(task);
    }

    private static TaskHandle wrap(BukkitTask task) {
        if (task == null) return NOOP;
        return new BukkitTaskHandle(task);
    }

    private record PaperTaskHandle(ScheduledTask task) implements TaskHandle {
        @Override
        public void cancel() {
            task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }
    }

    private record BukkitTaskHandle(BukkitTask task) implements TaskHandle {
        @Override
        public void cancel() {
            task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }
    }
}
