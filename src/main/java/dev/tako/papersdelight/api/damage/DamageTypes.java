package dev.tako.papersdelight.api.damage;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 注册自定义伤害类型,按类型对实体造成伤害的入口, 供附属插件使用, 注册要在 bootstrap 阶段完成.
 * <p><strong>除参数为 {@code null} 以外不会抛异常</strong>, 注册能力缺失(1.21~1.21.3),注册失败,同 key 定义冲突都会降级成 {@link DamageTypeRegistrationState#FALLBACK} 并记日志.
 */
public final class DamageTypes {

    private static final @NotNull Map<Key, DamageTypeHandle> HANDLES = new ConcurrentHashMap<>();
    private static final @NotNull Object LOCK = new Object();
    private static final @NotNull Logger LOGGER = Logger.getLogger("PapersDelight-DamageTypes");

    static KeyPresence keyPresence = DamageTypes::isKeyInRuntimeRegistry;

    static DamageDispatcher damageDispatcher = DamageTypes::dispatchDamage;

    static BooleanSupplier capabilityCheck = DamageTypeSupport::isRegistryEventCapabilityPresent;

    static AdapterInvoker adapterInvoker = DamageTypes::invokeAdapterReflectively;

    static Supplier<String> adapterClassNameResolver = DamageTypeSupport::currentAdapterClassName;

    /** 兜底用的原版伤害类型键({@code minecraft:generic}). */
    public static final @NotNull Key GENERIC_KEY = Key.key("minecraft", "generic");

    private DamageTypes() {
        throw new UnsupportedOperationException("DamageTypes is a utility class");
    }

    private static void invokeAdapterReflectively(BootstrapContext context, DamageTypeDefinition definition) throws Throwable {
        Class<?> adapterClass = Class.forName(adapterClassNameResolver.get());
        Method registerMethod = adapterClass.getMethod("register", BootstrapContext.class, DamageTypeDefinition.class);
        registerMethod.invoke(null, context, definition);
    }

    private static boolean isKeyInRuntimeRegistry(Key key) {
        try {
            return Registry.DAMAGE_TYPE.get(new NamespacedKey(key.namespace(), key.value())) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static DamageType resolveType(Key key) {
        try {
            DamageType type = Registry.DAMAGE_TYPE.get(new NamespacedKey(key.namespace(), key.value()));
            return type != null ? type : DamageType.GENERIC;
        } catch (Throwable t) {
            return DamageType.GENERIC;
        }
    }

    private static void dispatchDamage(LivingEntity victim, double amount, Key effectiveKey, Entity causer) {
        DamageType type = resolveType(effectiveKey);
        DamageSource.Builder builder = DamageSource.builder(type);
        if (causer != null) {
            builder.withCausingEntity(causer);
        }
        DamageSource source = builder.build();
        victim.damage(amount, source);
    }

    /**
     * 注册一个自定义伤害类型, 同一个定义重复注册会直接复用已有的句柄.
     *
     * @param context    Paper bootstrap 上下文
     * @param definition 伤害类型定义
     * @return 本次注册得到的句柄
     * @throws NullPointerException 当 {@code context} 或 {@code definition} 为 {@code null} 时
     */
    @NotNull
    public static DamageTypeHandle register(@NotNull BootstrapContext context,
                                            @NotNull DamageTypeDefinition definition) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(definition, "definition");

        synchronized (LOCK) {
            Key key = definition.key();
            DamageTypeHandle existing = HANDLES.get(key);
            if (existing != null) {
                if (existing.getDefinition().equals(definition)) {
                    return existing;
                }

                LOGGER.severe("检测到伤害类型键冲突，已强制降级为 FALLBACK。key=" + key
                        + ", existing=" + existing.getDefinition()
                        + ", incoming=" + definition);
                existing.downgrade();
                return new DamageTypeHandle(definition, DamageTypeRegistrationState.FALLBACK);
            }

            boolean capabilityPresent;
            try {
                capabilityPresent = capabilityCheck.getAsBoolean();
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "伤害类型注册能力检测异常，回退解析: " + key, t);
                capabilityPresent = false;
            }

            if (!capabilityPresent) {
                LOGGER.info("伤害类型注册能力缺失（1.21-1.21.3），回退解析: " + key);
                DamageTypeHandle handle = new DamageTypeHandle(definition, DamageTypeRegistrationState.FALLBACK);
                HANDLES.put(key, handle);
                return handle;
            }

            try {
                adapterInvoker.invoke(context, definition);
                DamageTypeHandle handle = new DamageTypeHandle(definition, DamageTypeRegistrationState.REGISTERED);
                HANDLES.put(key, handle);
                return handle;
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "伤害类型注册失败，回退: " + key, t);
                DamageTypeHandle handle = new DamageTypeHandle(definition, DamageTypeRegistrationState.FALLBACK);
                HANDLES.put(key, handle);
                return handle;
            }
        }
    }

    /**
     * 查询本进程记录过的伤害类型句柄.
     *
     * @param key 伤害类型键
     * @return 记录过的句柄, 没有则返回 {@code null}
     */
    static DamageTypeHandle peek(Key key) {
        return HANDLES.get(key);
    }

    // ── 运行期解析 API ────────────────────────────────────────────────────────

    /**
     * 计算实际生效的伤害类型键.
     * <p><strong>以运行期注册表里有没有这个键为准</strong>, 不看本进程注册是否成功; 自定义键与回退键都没有时返回 {@link #GENERIC_KEY}.
     *
     * @param customKey 自定义伤害类型键
     * @param fallback  自定义键缺失时的回退键
     * @return 实际生效的键, 恒非空
     * @throws NullPointerException 当任一参数为 {@code null} 时
     */
    @NotNull
    public static Key effectiveKey(@NotNull Key customKey, @NotNull Key fallback) {
        Objects.requireNonNull(customKey, "customKey");
        Objects.requireNonNull(fallback, "fallback");
        if (keyPresence.isPresent(customKey)) {
            return customKey;
        }
        if (keyPresence.isPresent(fallback)) {
            return fallback;
        }
        return GENERIC_KEY;
    }

    /**
     * 计算句柄实际生效的伤害类型键, 等价于用句柄的 key 与 fallback 调 {@link #effectiveKey(Key, Key)}.
     *
     * @param handle 伤害类型句柄
     * @return 实际生效的键, 恒非空
     * @throws NullPointerException 当 {@code handle} 为 {@code null} 时
     */
    @NotNull
    public static Key effectiveKey(@NotNull DamageTypeHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return effectiveKey(handle.getKey(), handle.getFallback());
    }

    /**
     * 解析出运行期真正使用的 {@link DamageType}, 注册表里查不到时回落到 {@link DamageType#GENERIC}.
     *
     * @param customKey 自定义伤害类型键
     * @param fallback  自定义键缺失时的回退键
     * @return 运行期伤害类型, 恒非空
     * @throws NullPointerException 当任一参数为 {@code null} 时
     */
    @NotNull
    public static DamageType resolve(@NotNull Key customKey, @NotNull Key fallback) {
        return resolveType(effectiveKey(customKey, fallback));
    }

    /**
     * 解析句柄运行期真正使用的 {@link DamageType}.
     *
     * @param handle 伤害类型句柄
     * @return 运行期伤害类型, 恒非空
     * @throws NullPointerException 当 {@code handle} 为 {@code null} 时
     */
    @NotNull
    public static DamageType resolve(@NotNull DamageTypeHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return resolve(handle.getKey(), handle.getFallback());
    }

    // ── 造成伤害 API ──────────────────────────────────────────────────────────

    /**
     * 对目标造成自定义伤害, 不带攻击者.
     *
     * @param victim 受伤实体
     * @param amount 伤害数值
     * @param handle 伤害类型句柄
     * @throws NullPointerException 当 {@code victim} 或 {@code handle} 为 {@code null} 时
     * @see #damage(LivingEntity, double, DamageTypeHandle, Entity)
     */
    public static void damage(@NotNull LivingEntity victim, double amount, @NotNull DamageTypeHandle handle) {
        damage(victim, amount, handle, null);
    }

    /**
     * 对目标造成自定义伤害.
     * <p><strong>自定义伤害类型缺失时静默回落到 {@link DamageType#GENERIC}</strong>, 不会抛异常.
     *
     * @param victim 受伤实体
     * @param amount 伤害数值
     * @param handle 伤害类型句柄
     * @param causer 攻击者, 可为 {@code null}
     * @throws NullPointerException 当 {@code victim} 或 {@code handle} 为 {@code null} 时
     */
    public static void damage(@NotNull LivingEntity victim,
                              double amount,
                              @NotNull DamageTypeHandle handle,
                              @Nullable Entity causer) {
        Objects.requireNonNull(victim, "victim");
        Objects.requireNonNull(handle, "handle");
        Key effectiveKey = effectiveKey(handle);
        damageDispatcher.dispatch(victim, amount, effectiveKey, causer);
    }

    /**
     * 按伤害类型键对目标造成伤害, 不带攻击者.
     *
     * @param victim    受伤实体
     * @param amount    伤害数值
     * @param customKey 自定义伤害类型键
     * @param fallback  自定义键缺失时的回退键
     * @throws NullPointerException 当 {@code victim},{@code customKey} 或 {@code fallback} 为 {@code null} 时
     * @see #damage(LivingEntity, double, Key, Key, Entity)
     */
    public static void damage(@NotNull LivingEntity victim,
                              double amount,
                              @NotNull Key customKey,
                              @NotNull Key fallback) {
        damage(victim, amount, customKey, fallback, null);
    }

    /**
     * 按伤害类型键对目标造成伤害.
     * <p><strong>自定义伤害类型缺失时静默回落到回退键, 回退键也没有则回落到 {@link #GENERIC_KEY}</strong>, 不会抛异常.
     *
     * @param victim    受伤实体
     * @param amount    伤害数值
     * @param customKey 自定义伤害类型键
     * @param fallback  自定义键缺失时的回退键
     * @param causer    攻击者, 可为 {@code null}
     * @throws NullPointerException 当 {@code victim},{@code customKey} 或 {@code fallback} 为 {@code null} 时
     */
    public static void damage(@NotNull LivingEntity victim,
                              double amount,
                              @NotNull Key customKey,
                              @NotNull Key fallback,
                              @Nullable Entity causer) {
        Objects.requireNonNull(victim, "victim");
        Objects.requireNonNull(customKey, "customKey");
        Objects.requireNonNull(fallback, "fallback");
        Key effectiveKey = effectiveKey(customKey, fallback);
        damageDispatcher.dispatch(victim, amount, effectiveKey, causer);
    }

    static void resetForTesting() {
        synchronized (LOCK) {
            HANDLES.clear();
            capabilityCheck = DamageTypeSupport::isRegistryEventCapabilityPresent;
            adapterInvoker = DamageTypes::invokeAdapterReflectively;
            adapterClassNameResolver = DamageTypeSupport::currentAdapterClassName;
            keyPresence = DamageTypes::isKeyInRuntimeRegistry;
            damageDispatcher = DamageTypes::dispatchDamage;
        }
    }
}

@FunctionalInterface
interface KeyPresence {

    boolean isPresent(Key key);
}

@FunctionalInterface
interface DamageDispatcher {

    void dispatch(LivingEntity victim, double amount, Key effectiveKey, Entity causer);
}

@FunctionalInterface
interface AdapterInvoker {

    void invoke(BootstrapContext context, DamageTypeDefinition definition) throws Throwable;
}
