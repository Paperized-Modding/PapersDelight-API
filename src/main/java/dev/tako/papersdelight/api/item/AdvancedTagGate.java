package dev.tako.papersdelight.api.item;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 高级标签判定门面 —— 由 PapersDelight Server payload 在初始化时注册实现。
 *
 * <p>使用 push 注册而不是从 Client 类加载器反射查找 Server 类，避免正式云载环境中的
 * 父子类加载器隔离导致 Server 侧高级标签服务不可见。</p>
 *
 * <p>PD 不可用或判定异常时返回 {@code false}，避免把任意物品误判为命中标签。</p>
 */
public final class AdvancedTagGate {

    private static volatile BiPredicate<ItemStack, String> service;
    private static volatile Function<String, List<String>> expansionService;
    private static volatile boolean warned;

    private AdvancedTagGate() {
    }

    /** 注册 PapersDelight 的高级标签判定实现。传入 null 会抛出异常。 */
    public static void register(BiPredicate<ItemStack, String> resolver) {
        register(resolver, null);
    }

    /** 注册高级标签判定与已编译标签展开实现。 */
    public static void register(BiPredicate<ItemStack, String> resolver,
                                Function<String, List<String>> expander) {
        service = Objects.requireNonNull(resolver, "resolver");
        expansionService = expander;
        warned = false;
    }

    /** 注销当前实现，通常在 Server payload 停止时调用。 */
    public static void unregister() {
        service = null;
        expansionService = null;
    }

    /**
     * 物品是否命中某高级标签（依 PapersDelight 的 advanced_tags 定义）。
     *
     * @param item  待判定物品
     * @param tagId 高级标签 id（可带或不带 {@code advtag:} 前缀）
     * @return true 当且仅当已注册实现判定命中；未注册或异常时返回 false
     */
    public static boolean isAdvancedTagged(ItemStack item, String tagId) {
        if (item == null || item.isEmpty() || tagId == null || tagId.isBlank()) return false;
        BiPredicate<ItemStack, String> resolver = service;
        if (resolver == null) return false;
        try {
            return resolver.test(item, tagId);
        } catch (Throwable t) {
            warnOnce("PapersDelight 高级标签判定失败，本次视作未命中", t);
            return false;
        }
    }

    /**
     * 展开高级标签为已编译的物品 ID 列表。
     *
     * @param tagId 高级标签 ID，可带或不带 {@code advtag:} 前缀
     * @return 有序且不可变的物品 ID 列表；未注册、非法或异常时为空列表
     */
    public static List<String> resolveItems(String tagId) {
        if (tagId == null || tagId.isBlank()) return List.of();
        String normalized = tagId.startsWith("advtag:")
                ? tagId.substring("advtag:".length()).trim() : tagId.trim();
        if (normalized.isEmpty()) return List.of();
        Function<String, List<String>> expander = expansionService;
        if (expander == null) return List.of();
        try {
            List<String> resolved = expander.apply(normalized);
            return resolved == null || resolved.isEmpty() ? List.of() : List.copyOf(resolved);
        } catch (Throwable t) {
            return List.of();
        }
    }

    /** 供测试与诊断：当前是否没有可用的高级标签实现。 */
    public static boolean isUnavailable() {
        return service == null;
    }

    private static void warnOnce(String message, Throwable t) {
        if (warned) return;
        warned = true;
        Logger.getLogger("PapersDelight").log(Level.WARNING, message, t);
    }
}
