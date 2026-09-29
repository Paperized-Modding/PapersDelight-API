package dev.tako.papersdelight.api.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

/**
 * 粒子可见性检测：无状态实现，直接查询位置附近的玩家。
 * 相比旧版（PlayerMoveEvent 监听 + ConcurrentHashMap 缓存），
 * 无事件开销、无写竞争、无陈旧缓存风险。
 */
public final class ParticleVisibility {

    private ParticleVisibility() {}

    /** 兼容旧调用，不再需要注册事件。 */
    public static void register(Plugin plugin) { /* no-op */ }

    /** 兼容旧调用。 */
    public static void clear() { /* no-op */ }

    public static boolean hasNearbyViewer(Block block, double rangeBlocks) {
        if (block == null || rangeBlocks <= 0.0D) return true;

        World world = block.getWorld();
        if (world == null) return true;

        double bx = block.getX() + 0.5D;
        double by = block.getY() + 0.5D;
        double bz = block.getZ() + 0.5D;
        Location center = new Location(world, bx, by, bz);
        return !world.getNearbyPlayers(center, rangeBlocks).isEmpty();
    }
}
