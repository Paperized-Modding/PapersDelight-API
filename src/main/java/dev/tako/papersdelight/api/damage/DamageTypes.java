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
 * 注册自定义伤害类型并按类型对实体造成伤害, 供附属插件使用, 注册必须在 bootstrap 阶段完成.
 * <p><strong>除参数为 {@code null} 外不会抛异常</strong>, 注册能力缺失(1.21~1.21.3), 注册失败或同 key 定义冲突都会降级为 {@link DamageTypeRegistrationState#FALLBACK} 并记日志.
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

    /** 注册一个自定义伤害类型, 同一份定义重复注册会复用已有句柄. */
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

    /** 查询本进程记录过的伤害类型句柄, 没有则返回 {@code null}. */
    static DamageTypeHandle peek(Key key) {
        return HANDLES.get(key);
    }

    // ── 运行期解析 API ────────────────────────────────────────────────────────

    /** 按运行期注册表计算实际生效的键, 自定义键与回退键都没有时返回 {@link #GENERIC_KEY}. */
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

    /** 计算句柄实际生效的键, 等价于 {@link #effectiveKey(Key, Key)}. */
    @NotNull
    public static Key effectiveKey(@NotNull DamageTypeHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return effectiveKey(handle.getKey(), handle.getFallback());
    }

    /** 解析运行期真正使用的 {@link DamageType}, 注册表里查不到时回落到 {@link DamageType#GENERIC}. */
    @NotNull
    public static DamageType resolve(@NotNull Key customKey, @NotNull Key fallback) {
        return resolveType(effectiveKey(customKey, fallback));
    }

    /** 解析句柄运行期真正使用的 {@link DamageType}. */
    @NotNull
    public static DamageType resolve(@NotNull DamageTypeHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return resolve(handle.getKey(), handle.getFallback());
    }

    // ── 造成伤害 API ──────────────────────────────────────────────────────────

    /** 对目标造成自定义伤害, 不带攻击者. */
    public static void damage(@NotNull LivingEntity victim, double amount, @NotNull DamageTypeHandle handle) {
        damage(victim, amount, handle, null);
    }

    /** 对目标造成自定义伤害, 伤害类型缺失时静默回落到 {@link DamageType#GENERIC}, 不会抛异常. */
    public static void damage(@NotNull LivingEntity victim,
                              double amount,
                              @NotNull DamageTypeHandle handle,
                              @Nullable Entity causer) {
        Objects.requireNonNull(victim, "victim");
        Objects.requireNonNull(handle, "handle");
        Key effectiveKey = effectiveKey(handle);
        damageDispatcher.dispatch(victim, amount, effectiveKey, causer);
    }

    /** 按伤害类型键对目标造成伤害, 不带攻击者. */
    public static void damage(@NotNull LivingEntity victim,
                              double amount,
                              @NotNull Key customKey,
                              @NotNull Key fallback) {
        damage(victim, amount, customKey, fallback, null);
    }

    /** 按伤害类型键对目标造成伤害, 键缺失时按 customKey, fallback, {@link #GENERIC_KEY} 顺序回落. */
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
