package dev.tako.papersdelight.api.recipe;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配方类型 handler 的注册中心, 附属插件在 {@code onLoad()} 里注册自己的 {@link RecipeTypeHandler}.
 * <p>注册时机不用抢在 PapersDelight 前面: parser 只在 CraftEngine 真正加载配置时才来查这张表, 后到的 handler 一样能用上.
 */
public final class RecipeTypeRegistry {

    private static final Set<String> RESERVED_TYPES =
            Set.of("cooking", "cutting", "single", "info", "decomposition",
                    "fluid_filling", "fluid_emptying", "soaking");

    private static final Map<String, RecipeTypeHandler> HANDLERS = new ConcurrentHashMap<>();

    private RecipeTypeRegistry() {
    }

    /**
     * 注册一个配方类型 handler.
     *
     * @param handler 待注册的 handler
     * @throws IllegalArgumentException type 为空, 与内置 type 冲突, 或该 type 已被别的 handler 占用
     */
    public static void register(@NotNull RecipeTypeHandler handler) {
        String type = normalize(handler.typeId());
        if (type.isEmpty()) {
            throw new IllegalArgumentException("recipe type must not be blank");
        }
        if (RESERVED_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "recipe type '" + type + "' is reserved by PapersDelight");
        }
        RecipeTypeHandler previous = HANDLERS.putIfAbsent(type, handler);
        if (previous != null && previous != handler) {
            throw new IllegalArgumentException(
                    "recipe type '" + type + "' is already registered by "
                            + previous.getClass().getName());
        }
    }

    /**
     * 注销 handler, 插件 {@code onDisable()} 时调用, 避免 parser 继续持有失效引用.
     *
     * @return 确实移除了返回 {@code true}
     */
    public static boolean unregister(@NotNull RecipeTypeHandler handler) {
        return HANDLERS.remove(normalize(handler.typeId()), handler);
    }

    /**
     * 按 type 查找 handler.
     *
     * @return 没注册返回 {@code null}
     */
    @Nullable
    public static RecipeTypeHandler find(@Nullable String type) {
        if (type == null) return null;
        return HANDLERS.get(normalize(type));
    }

    /** 当前已注册的全部 handler. */
    @NotNull
    public static Collection<RecipeTypeHandler> handlers() {
        return List.copyOf(HANDLERS.values());
    }

    /** 当前已注册的 type 集合, 用于日志与诊断. */
    @NotNull
    public static Set<String> registeredTypes() {
        return Set.copyOf(HANDLERS.keySet());
    }

    private static String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }
}
