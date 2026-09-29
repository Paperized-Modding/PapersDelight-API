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
 * 自定义伤害类型注册门面。
 *
 * <p>本类只负责“注册期”记录与降级决策，不负责后续的解析、effective key
 * 计算或伤害应用逻辑。它的核心契约是：除空参数外，任何注册失败都必须
 * 以 {@link DamageTypeRegistrationState#FALLBACK} 收敛，且绝不向调用方抛出异常。</p>
 *
 * <h3>降级矩阵</h3>
 * <ul>
 *   <li>能力缺失（1.21-1.21.3）→ {@code FALLBACK}</li>
 *   <li>适配器反射失败 / 适配器抛错 → {@code FALLBACK}</li>
 *   <li>同一 key 发生不同定义冲突 → 两边都降级为 {@code FALLBACK}</li>
 *   <li>空参数 → 仅抛 {@link NullPointerException}</li>
 * </ul>
 *
 * <p>注册结果会被记录在本进程的句柄表中；这不是 Minecraft 注册表真实内容的镜像，
 * 只是后续流程可查询的本地事实记录。</p>
 */
public final class DamageTypes {

    private static final @NotNull Map<Key, DamageTypeHandle> HANDLES = new ConcurrentHashMap<>();
    private static final @NotNull Object LOCK = new Object();
    private static final @NotNull Logger LOGGER = Logger.getLogger("PapersDelight-DamageTypes");

    /**
     * 运行期键存在性探测 seam。
     *
     * <p>默认检查 Bukkit 运行期注册表是否包含该键；测试可替换为人工桩，以避免依赖真实
     * 服务器注册表并精确控制 effective key 的解析结果。</p>
     */
    static KeyPresence keyPresence = DamageTypes::isKeyInRuntimeRegistry;

    /**
     * 造成伤害的分发 seam。
     *
     * <p>默认按运行期解析出的伤害类型构造 {@link DamageSource} 并调用目标实体；测试可替换为
     * 记录参数或完全隔离 Bukkit 调用的桩。</p>
     */
    static DamageDispatcher damageDispatcher = DamageTypes::dispatchDamage;

    /**
     * 注册能力探测 seam。
     *
     * <p>默认委托 {@link DamageTypeSupport#isRegistryEventCapabilityPresent()}，测试可替换为
     * 人工桩，以覆盖能力存在/缺失分支而不依赖运行时 classpath。</p>
     */
    static BooleanSupplier capabilityCheck = DamageTypeSupport::isRegistryEventCapabilityPresent;

    /**
     * 适配器调用 seam。
     *
     * <p>默认通过反射调用后续 bridge 适配器；测试可替换为 no-op、抛错或记录参数的桩，
     * 以验证注册门面对异常与透传的处理。</p>
     */
    static AdapterInvoker adapterInvoker = DamageTypes::invokeAdapterReflectively;

    /**
     * 适配器类名解析 seam。
     *
     * <p>默认按当前运行的 Minecraft 版本委托
     * {@link DamageTypeSupport#currentAdapterClassName()}；测试可替换为固定字符串，
     * 以在保留默认反射链的前提下验证真实调用确实走动态解析。</p>
     */
    static Supplier<String> adapterClassNameResolver = DamageTypeSupport::currentAdapterClassName;

    /**
     * 兜底 damage type 的运行期键（{@code minecraft:generic}）。
     */
    public static final @NotNull Key GENERIC_KEY = Key.key("minecraft", "generic");

    private DamageTypes() {
        throw new UnsupportedOperationException("DamageTypes is a utility class");
    }

    /**
     * 通过反射调用后续 bridge 适配器。
     *
     * <p>适配器类名从 {@link #adapterClassNameResolver} seam 获取，默认按当前运行的
     * Minecraft 版本经 {@link DamageTypeSupport#currentAdapterClassName()} 选择，
     * 不再固定指向 1.21.4。</p>
     *
     * <p>契约约定：适配器类必须提供
     * {@code public static void register(BootstrapContext, DamageTypeDefinition)}。
     * 反射失败、方法调用失败或适配器内部抛出的异常/错误都会沿着 {@code Throwable}
     * 继续传播，由 {@link #register(BootstrapContext, DamageTypeDefinition)} 统一兜底降级。</p>
     */
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
     * 注册一个自定义伤害类型。
     *
     * <p>该方法只做注册期决策：它会先检查同 key 的本地记录，再根据运行时能力选择
     * 真注册或降级回退。除空参数外，本方法永不抛出异常；所有能力缺失、适配器故障、
     * 以及 key 冲突都必须收敛为 {@code FALLBACK}。</p>
     *
     * @param context    Paper bootstrap 上下文；仅在真正尝试调用适配器时使用
     * @param definition 伤害类型定义
     * @return 本次注册得到的句柄
     * @throws NullPointerException 若 {@code context} 或 {@code definition} 为 {@code null}
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
     * 查询本进程记录的某个伤害类型句柄。
     *
     * <p>注意：此方法只反映当前 JVM 进程中通过 {@link #register(BootstrapContext, DamageTypeDefinition)}
     * 记录下来的本地事实，不代表 Minecraft 注册表的真实内容，也不执行任何解析逻辑。
     * 它主要用于后续 resolve 流程与测试诊断。</p>
     *
     * @param key 伤害类型键
     * @return 本地记录的句柄；若从未注册则返回 {@code null}
     */
    static DamageTypeHandle peek(Key key) {
        return HANDLES.get(key);
    }

    // ── 运行期解析 API ────────────────────────────────────────────────────────

    /**
     * 计算实际生效的伤害类型键。
     *
     * <p>解析规则依次为：若运行期注册表包含 {@code customKey} 则返回它；否则若注册表包含
     * {@code fallback} 则返回它；两者都不存在时返回 {@link #GENERIC_KEY}。</p>
     *
     * <p><b>以运行期注册表是否含该键为准，而非本进程是否注册成功。</b>因此即便本进程未通过
     * {@link #register} 成功注册，只要服主装了提供该键的真实数据包，也会命中自定义键；
     * 反之，本进程记录的 {@link DamageTypeRegistrationState} 不参与本判断。</p>
     *
     * @param customKey 自定义伤害类型键
     * @param fallback  回退键
     * @return 实际生效的键，恒非空
     * @throws NullPointerException 若任一参数为 {@code null}
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
     * 计算句柄实际生效的伤害类型键。
     *
     * <p>以句柄的 key 与 fallback 委托 {@link #effectiveKey(Key, Key)}；解析同样以运行期
     * 注册表为准，不受句柄 {@link DamageTypeRegistrationState} 影响。</p>
     *
     * @param handle 伤害类型句柄
     * @return 实际生效的键，恒非空
     * @throws NullPointerException 若 {@code handle} 为 {@code null}
     */
    @NotNull
    public static Key effectiveKey(@NotNull DamageTypeHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return effectiveKey(handle.getKey(), handle.getFallback());
    }

    /**
     * 解析出实际生效的运行期 {@link DamageType}。
     *
     * <p>先经 {@link #effectiveKey(Key, Key)} 决定生效键，再查询运行期注册表；查询失败或
     * 缺失时回退 {@link DamageType#GENERIC}。返回值恒非空。</p>
     *
     * @param customKey 自定义伤害类型键
     * @param fallback  回退键
     * @return 运行期伤害类型，恒非空
     * @throws NullPointerException 若任一参数为 {@code null}
     */
    @NotNull
    public static DamageType resolve(@NotNull Key customKey, @NotNull Key fallback) {
        return resolveType(effectiveKey(customKey, fallback));
    }

    /**
     * 解析句柄实际生效的运行期 {@link DamageType}。
     *
     * @param handle 伤害类型句柄
     * @return 运行期伤害类型，恒非空
     * @throws NullPointerException 若 {@code handle} 为 {@code null}
     */
    @NotNull
    public static DamageType resolve(@NotNull DamageTypeHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return resolve(handle.getKey(), handle.getFallback());
    }

    // ── 造成伤害 API ──────────────────────────────────────────────────────────

    /**
     * 以句柄对应的伤害类型对目标造成伤害（无攻击者）。
     *
     * @param victim 受伤实体
     * @param amount 伤害数值
     * @param handle 伤害类型句柄
     * @throws NullPointerException 若 {@code victim} 或 {@code handle} 为 {@code null}
     * @see #damage(LivingEntity, double, DamageTypeHandle, Entity)
     */
    public static void damage(@NotNull LivingEntity victim, double amount, @NotNull DamageTypeHandle handle) {
        damage(victim, amount, handle, null);
    }

    /**
     * 以句柄对应的伤害类型对目标造成伤害。
     *
     * <p>内部先经 {@link #effectiveKey(DamageTypeHandle)} 解析生效键，再解析运行期
     * {@link DamageType}。<b>绝不会因自定义伤害类型缺失而抛异常</b>：缺失时静默回退为
     * {@link DamageType#GENERIC}。</p>
     *
     * @param victim 受伤实体
     * @param amount 伤害数值
     * @param handle 伤害类型句柄
     * @param causer 造成伤害的攻击者，可为 {@code null}
     * @throws NullPointerException 若 {@code victim} 或 {@code handle} 为 {@code null}
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
     * 以自定义键对应的伤害类型对目标造成伤害（无攻击者）。
     *
     * @param victim    受伤实体
     * @param amount    伤害数值
     * @param customKey 自定义伤害类型键
     * @param fallback  自定义键在运行期缺失时的回退键
     * @throws NullPointerException 若 {@code victim}、{@code customKey} 或 {@code fallback} 为 {@code null}
     * @see #damage(LivingEntity, double, Key, Key, Entity)
     */
    public static void damage(@NotNull LivingEntity victim,
                              double amount,
                              @NotNull Key customKey,
                              @NotNull Key fallback) {
        damage(victim, amount, customKey, fallback, null);
    }

    /**
     * 以自定义键对应的伤害类型对目标造成伤害。
     *
     * <p>内部经 {@link #effectiveKey(Key, Key)} 解析生效键（自定义键存在→用它；否则回退键存在→用
     * 回退键；两者都缺失→{@link #GENERIC_KEY}），再解析运行期 {@link DamageType}。<b>绝不会因
     * 自定义伤害类型缺失而抛异常</b>：缺失时按上述顺序静默降级。</p>
     *
     * @param victim    受伤实体
     * @param amount    伤害数值
     * @param customKey 自定义伤害类型键
     * @param fallback  自定义键在运行期缺失时的回退键
     * @param causer    造成伤害的攻击者，可为 {@code null}
     * @throws NullPointerException 若 {@code victim}、{@code customKey} 或 {@code fallback} 为 {@code null}
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

    /**
     * 重置门面内部状态，仅供测试使用。
     *
     * <p>该方法会清空本地句柄表，并将能力探针、适配器调用与类名解析 seam 恢复为默认实现，
     * 以保证每个测试用例之间彼此隔离。</p>
     */
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

/**
 * 运行期键存在性探测 seam。
 *
 * <p>默认实现查询 Bukkit 运行期注册表；测试可替换以精确控制解析分支，无需真实服务器。</p>
 */
@FunctionalInterface
interface KeyPresence {

    /**
     * @param key 待查询的伤害类型键
     * @return 运行期注册表是否包含该键
     */
    boolean isPresent(Key key);
}

/**
 * 造成伤害的分发 seam。
 *
 * <p>默认实现按解析出的伤害类型构造 {@link DamageSource} 并调用受伤实体；测试可替换为
 * 记录参数的桩，从而在纯 JVM 环境下隔离真实 Bukkit 调用。</p>
 */
@FunctionalInterface
interface DamageDispatcher {

    /**
     * 执行一次伤害分发。
     *
     * @param victim       受伤实体
     * @param amount       伤害数值
     * @param effectiveKey 实际生效的伤害类型键
     * @param causer       攻击者，可为 {@code null}
     */
    void dispatch(LivingEntity victim, double amount, Key effectiveKey, Entity causer);
}

/**
 * 伤害类型适配器调用 seam。
 *
 * <p>默认实现会由 {@link DamageTypes} 反射调用后续 bridge 适配器；测试可替换该 seam，
 * 以验证门面对能力缺失、适配器异常和参数透传的处理是否符合契约。</p>
 */
@FunctionalInterface
interface AdapterInvoker {

    /**
     * 执行一次适配器注册。
     *
     * @param context    Paper bootstrap 上下文
     * @param definition 伤害类型定义
     * @throws Throwable 任何适配器侧异常/错误都允许原样抛出，由门面统一兜底
     */
    void invoke(BootstrapContext context, DamageTypeDefinition definition) throws Throwable;
}
