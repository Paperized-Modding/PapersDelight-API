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
 * 配方类型 handler 注册中心: 附属插件在 {@code onLoad()} 里注册自己的 {@link RecipeTypeHandler}.
 * <p>不用抢在 PapersDelight 前面注册, parser 只在 CraftEngine 真正加载配置时才来查表.
 */
public final class RecipeTypeRegistry {

    private static final Set<String> RESERVED_TYPES =
            Set.of("cooking", "cutting", "single", "info", "decomposition",
                    "fluid_filling", "fluid_emptying", "soaking");

    private static final Map<String, RecipeTypeHandler> HANDLERS = new ConcurrentHashMap<>();

    private RecipeTypeRegistry() {
    }

    /** type 为空, 与内置 type 冲突或已被占用时抛 {@link IllegalArgumentException}. */
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

    /** 插件 {@code onDisable()} 时调用, 避免 parser 继续持有失效引用. */
    public static boolean unregister(@NotNull RecipeTypeHandler handler) {
        return HANDLERS.remove(normalize(handler.typeId()), handler);
    }

    @Nullable
    public static RecipeTypeHandler find(@Nullable String type) {
        if (type == null) return null;
        return HANDLERS.get(normalize(type));
    }

    @NotNull
    public static Collection<RecipeTypeHandler> handlers() {
        return List.copyOf(HANDLERS.values());
    }

    @NotNull
    public static Set<String> registeredTypes() {
        return Set.copyOf(HANDLERS.keySet());
    }

    private static String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }
}
