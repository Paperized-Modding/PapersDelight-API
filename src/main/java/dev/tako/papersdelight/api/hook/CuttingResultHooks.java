package dev.tako.papersdelight.api.hook;

import org.bukkit.inventory.ItemStack;

/**
 * 切制产出的数量钩子。
 *
 * <p>有些配方的产出数量取决于原料本身的数据（例如按鱼的大小切出不同份数），
 * 静态配置写不出来，由附属插件注册一个修饰器在产出前改写数量。</p>
 */
public final class CuttingResultHooks {

    /** 返回实际产出数量；返回原值表示不干预。 */
    public interface Modifier {
        int count(ItemStack input, String resultId, int original);
    }

    private static volatile Modifier modifier;

    private CuttingResultHooks() {}

    public static void register(Modifier hook) {
        modifier = hook;
    }

    public static void unregister() {
        modifier = null;
    }

    public static int apply(ItemStack input, String resultId, int original) {
        Modifier hook = modifier;
        if (hook == null || input == null || input.isEmpty()) return original;
        try {
            return Math.max(0, hook.count(input, resultId, original));
        } catch (Throwable ignored) {
            return original;
        }
    }
}
