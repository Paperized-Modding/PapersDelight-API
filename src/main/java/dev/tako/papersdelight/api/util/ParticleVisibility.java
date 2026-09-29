package dev.tako.papersdelight.api.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

/** 粒子可见性检查: 目标位置附近有没有玩家. */
public final class ParticleVisibility {

    private ParticleVisibility() {}

    public static void register(Plugin plugin) { /* no-op */ }

    public static void clear() { /* no-op */ }

    /** 有玩家在半径内返回 {@code true}; 方块或半径无效时也返回 {@code true}(放行). */
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
