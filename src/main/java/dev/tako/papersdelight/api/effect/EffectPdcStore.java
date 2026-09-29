package dev.tako.papersdelight.api.effect;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** 限时效果的 PDC 存取, 不需要数据库. */
public final class EffectPdcStore {

    private static final byte[] EMPTY = new byte[0];

    private final NamespacedKey coreKey;
    private final NamespacedKey extraKey;

    /** {@code effectId} 决定 PDC key, 改了会丢存档. */
    public EffectPdcStore(Plugin plugin, String effectId) {
        this.coreKey = new NamespacedKey(plugin, "effect_" + effectId);
        this.extraKey = new NamespacedKey(plugin, "effect_" + effectId + "_extra");
    }

    /** 写入快照, {@code remainingTicks <= 0} 视为无效, 直接清除. */
    public void save(Player player, int remainingTicks, int totalTicks, int amplifier, byte[] extra) {
        if (player == null) return;
        if (remainingTicks <= 0) {
            clear(player);
            return;
        }
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(coreKey, PersistentDataType.INTEGER_ARRAY,
                new int[]{remainingTicks, Math.max(totalTicks, remainingTicks), Math.max(0, amplifier)});
        pdc.set(extraKey, PersistentDataType.BYTE_ARRAY, extra == null ? EMPTY : extra);
    }

    /** 读回快照, 没有存档返回 {@code null}. */
    public EffectPdcRecord read(Player player) {
        if (player == null) return null;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int[] core = pdc.get(coreKey, PersistentDataType.INTEGER_ARRAY);
        if (core == null || core.length < 3) return null;
        byte[] extra = pdc.get(extraKey, PersistentDataType.BYTE_ARRAY);
        return new EffectPdcRecord(core[0], core[1], core[2], extra == null ? EMPTY : extra);
    }

    /** 删除该效果的全部 PDC 数据. */
    public void clear(Player player) {
        if (player == null) return;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.remove(coreKey);
        pdc.remove(extraKey);
    }
}
