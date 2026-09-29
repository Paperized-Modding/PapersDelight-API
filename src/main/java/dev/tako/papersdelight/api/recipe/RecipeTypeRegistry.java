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
 * 配方类型扩展点注册中心。
 *
 * <p>附属插件在 {@code onLoad()} 里注册自己的 {@link RecipeTypeHandler}，
 * PapersDelight 的 recipe parser 解析时按 type 查找。</p>
 *
 * <p><b>注册时序</b>：PapersDelight 在 {@code onLoad()} 就向 CraftEngine 注册
 * parser 实例，但 parser 只在 CraftEngine 真正加载配置时才读取本注册表，
 * 因此附属插件在自己的 {@code onLoad()} 里注册即可，无需早于 PapersDelight。
 * 注册表支持「parser 已创建、handler 后到」的追加注册。</p>
 */
public final class RecipeTypeRegistry {

    /**
     * PapersDelight 内置的配方 type，不允许被扩展点覆盖。
     * {@code info} 是 {@code single} 的别名（语义等价），一并保留以防被扩展 handler 抢占。
     */
    private static final Set<String> RESERVED_TYPES =
            Set.of("cooking", "cutting", "single", "info", "decomposition",
                    "fluid_filling", "fluid_emptying", "soaking");

    private static final Map<String, RecipeTypeHandler> HANDLERS = new ConcurrentHashMap<>();

    private RecipeTypeRegistry() {
    }

    /**
     * 注册一个配方类型 handler。
     *
     * @param handler 待注册的 handler
     * @throws IllegalArgumentException type 为空、与内置 type 冲突、或已被其他 handler 占用
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
     * 注销 handler。附属插件在 {@code onDisable()} 里调用，
     * 避免插件卸载后 parser 仍持有失效引用。
     *
     * @return 确实移除了则 {@code true}
     */
    public static boolean unregister(@NotNull RecipeTypeHandler handler) {
        return HANDLERS.remove(normalize(handler.typeId()), handler);
    }

    /**
     * 按 type 查找 handler。
     *
     * @return 未注册则返回 {@code null}
     */
    @Nullable
    public static RecipeTypeHandler find(@Nullable String type) {
        if (type == null) return null;
        return HANDLERS.get(normalize(type));
    }

    /** 当前已注册的全部 handler，供 parser 做批量 begin/publish。 */
    @NotNull
    public static Collection<RecipeTypeHandler> handlers() {
        return List.copyOf(HANDLERS.values());
    }

    /** 已注册的 type 集合，仅用于日志与诊断。 */
    @NotNull
    public static Set<String> registeredTypes() {
        return Set.copyOf(HANDLERS.keySet());
    }

    private static String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }
}
