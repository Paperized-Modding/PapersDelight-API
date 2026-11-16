package dev.tako.papersdelight.api.hook;

import org.bukkit.inventory.ItemStack;

/**
 * 煎锅里物品的显示缩放钩子。
 *
 * <p>默认按配置的固定比例显示；附属插件可以让某些物品按自身数据放大或缩小，
 * 例如按鱼的尺寸决定它在锅里占多大。</p>
 */
public final class SkilletDisplayHooks {

    /** 返回缩放倍率，1.0 表示使用默认大小。 */
    public interface ScaleProvider {
        float scale(ItemStack item);
    }

    private static volatile ScaleProvider provider;

    private SkilletDisplayHooks() {}

    public static void register(ScaleProvider hook) {
        provider = hook;
    }

    public static boolean registered() {
        return provider != null;
    }

    public static void unregister() {
        provider = null;
    }

    public static float scaleFor(ItemStack item) {
        ScaleProvider hook = provider;
        if (hook == null || item == null || item.isEmpty()) return 1.0f;
        try {
            float scale = hook.scale(item);
            if (Float.isNaN(scale) || scale <= 0f) return 1.0f;
            return Math.max(0.2f, Math.min(3.0f, scale));
        } catch (Throwable ignored) {
            return 1.0f;
        }
    }
}
