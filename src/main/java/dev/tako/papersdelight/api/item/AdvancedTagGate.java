package dev.tako.papersdelight.api.item;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 高级标签判定入口: PapersDelight 启动时注册实现, 附属插件用它判断物品有没有命中某个高级标签.
 * <p><strong>实现没注册或判定抛异常时一律返回 {@code false}</strong>, 不会把物品误判成命中.
 */
public final class AdvancedTagGate {

    private static volatile BiPredicate<ItemStack, String> service;
    private static volatile Function<String, List<String>> expansionService;
    private static volatile boolean warned;

    private AdvancedTagGate() {
    }

    /** 注册判定实现, 通常在 PapersDelight 启动时调用; 传 {@code null} 会抛异常. */
    public static void register(BiPredicate<ItemStack, String> resolver) {
        register(resolver, null);
    }

    /** 注册判定实现与标签展开实现(高级标签 → 已编译的物品 ID 列表). */
    public static void register(BiPredicate<ItemStack, String> resolver,
                                Function<String, List<String>> expander) {
        service = Objects.requireNonNull(resolver, "resolver");
        expansionService = expander;
        warned = false;
    }

    /** 注销实现, 通常在 PapersDelight 停止时调用. */
    public static void unregister() {
        service = null;
        expansionService = null;
    }

    /**
     * 物品有没有命中这个高级标签(依 PapersDelight 的 advanced_tags 定义).
     *
     * @param item 待判定物品
     * @param tagId 高级标签 id, 带不带 {@code advtag:} 前缀都行
     * @return 命中返回 {@code true}; 没注册实现或判定失败时返回 {@code false}
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
     * 把高级标签展开成已编译的物品 ID 列表.
     *
     * @param tagId 高级标签 id, 带不带 {@code advtag:} 前缀都行
     * @return 有序且不可变的物品 ID 列表; 没注册实现或展开失败时是空列表
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

    /** 当前没有可用的判定实现, 供测试与诊断. */
    public static boolean isUnavailable() {
        return service == null;
    }

    private static void warnOnce(String message, Throwable t) {
        if (warned) return;
        warned = true;
        Logger.getLogger("PapersDelight").log(Level.WARNING, message, t);
    }
}
