package dev.tako.papersdelight.api.effect;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * 限时效果会话的 PDC 持久化。
 *
 * <p>PD 三效果与 PB 四效果共用：把「剩余 tick / 总时长 / 等级 / 额外状态」写进玩家
 * {@link PersistentDataContainer}，随玩家存档保存，无需数据库。存的是剩余量，
 * 离线冻结、重连续期。</p>
 *
 * <p>每个效果用两把 key：
 * <ul>
 *   <li>{@code <plugin>:effect_<id>} — {@code int[]{remaining, total, amplifier}}</li>
 *   <li>{@code <plugin>:effect_<id>_extra} — {@code byte[]} 子类额外状态</li>
 * </ul>
 * </p>
 */
public final class EffectPdcStore {

    private static final byte[] EMPTY = new byte[0];

    private final NamespacedKey coreKey;
    private final NamespacedKey extraKey;

    /**
     * @param plugin   命名空间来源（用 {@link NamespacedKey#NamespacedKey(Plugin, String)}）
     * @param effectId 效果标识（如 {@code nourishment}、{@code tipsy}）
     */
    public EffectPdcStore(Plugin plugin, String effectId) {
        this.coreKey = new NamespacedKey(plugin, "effect_" + effectId);
        this.extraKey = new NamespacedKey(plugin, "effect_" + effectId + "_extra");
    }

    /** 写入会话快照。{@code remainingTicks <= 0} 视为无效，直接清除。 */
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

    /** 读回会话快照；无存档返回 {@code null}。 */
    public EffectPdcRecord read(Player player) {
        if (player == null) return null;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int[] core = pdc.get(coreKey, PersistentDataType.INTEGER_ARRAY);
        if (core == null || core.length < 3) return null;
        byte[] extra = pdc.get(extraKey, PersistentDataType.BYTE_ARRAY);
        return new EffectPdcRecord(core[0], core[1], core[2], extra == null ? EMPTY : extra);
    }

    /** 删除该效果的全部 PDC 数据。 */
    public void clear(Player player) {
        if (player == null) return;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.remove(coreKey);
        pdc.remove(extraKey);
    }
}
