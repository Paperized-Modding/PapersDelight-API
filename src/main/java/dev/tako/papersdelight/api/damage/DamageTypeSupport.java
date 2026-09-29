package dev.tako.papersdelight.api.damage;

import io.papermc.paper.ServerBuildInfo;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;

/**
 * 自定义伤害类型注册能力的版本门面检测工具。
 *
 * <p>这是 API 门面侧唯一允许提及 Paper 1.21.4+ 注册表事件类名，
 * 以及后续 bridge 适配器全限定名的位置。为保持 paper-api 1.21 的
 * {@code compileOnly} 编译基线，本类不得 import 或静态引用任何 1.21.4+
 * 专有类型；所有能力探测均通过反射完成，并在类加载时缓存一次。</p>
 */
public final class DamageTypeSupport {

    private static final @NotNull String REGISTRY_EVENTS_CLASS_NAME =
            "io.papermc.paper.registry.event.RegistryEvents";
    private static final @NotNull String DAMAGE_TYPE_FIELD_NAME = "DAMAGE_TYPE";

    private static final @NotNull String ADAPTER_V1_21_4 =
            "dev.tako.papersdelight.bridge.v1_21_4.DamageTypeComposeRegistrar";
    private static final @NotNull String ADAPTER_V1_21_10 =
            "dev.tako.papersdelight.bridge.v1_21_10.DamageTypeComposeRegistrar";
    private static final @NotNull String ADAPTER_V1_21_11 =
            "dev.tako.papersdelight.bridge.v1_21_11.DamageTypeComposeRegistrar";

    /**
     * 1.21.4 legacy ABI 区间的伤害类型适配器类名。
     *
     * <p>保留该常量只为兼容已有内部契约与能力入口探测；运行时真实调用必须走
     * {@link #currentAdapterClassName()}，不得再依赖此固定值。</p>
     */
    public static final @NotNull String ADAPTER_CLASS_NAME = ADAPTER_V1_21_4;

    private static final boolean REGISTRY_EVENT_CAPABILITY_PRESENT = computeRegistryEventCapabilityPresent();
    /**
     * legacy 1.21.4 适配器类的存在性缓存。
     *
     * <p>仅覆盖 {@link #ADAPTER_CLASS_NAME}，不反映当前运行版本适配器的可用性。
     * 计算过程刻意不涉及 {@code Bukkit}，避免类初始化时依赖运行中的服务器。</p>
     */
    private static final boolean ADAPTER_CLASS_PRESENT = computeAdapterClassPresent();

    private DamageTypeSupport() {
        throw new UnsupportedOperationException("DamageTypeSupport is a utility class");
    }

    /**
     * 返回当前运行时是否具备 Paper 1.21.4+ 的 damage type 注册事件能力。
     *
     * <p>判定方式为反射加载 {@code io.papermc.paper.registry.event.RegistryEvents}，
     * 并确认其存在名为 {@code DAMAGE_TYPE} 的 public static 字段。检测结果在类加载时
     * 缓存一次；任何缺类、缺字段或链接失败都会被视为不具备能力，不会向调用方传播。</p>
     *
     * @return 若当前 classpath 暴露 1.21.4+ damage type 注册事件能力则为 {@code true}
     */
    public static boolean isRegistryEventCapabilityPresent() {
        return REGISTRY_EVENT_CAPABILITY_PRESENT;
    }

    /**
     * 返回 legacy 1.21.4 适配器类在当前 classpath 中是否可加载。
     *
     * <p><strong>本方法只探测 {@link #ADAPTER_CLASS_NAME} 指向的 legacy v1_21_4 契约类，
     * 不代表当前运行版本所需的适配器是否存在。</strong>其他 ABI 区间（1.21.5～1.21.10、
     * 1.21.11/26.x）的适配器不在本探测范围内；判断当前版本适配器请改用
     * {@link #currentAdapterClassName()} 并由调用方自行处理加载失败。</p>
     *
     * <p>检测使用当前类的 {@link ClassLoader} 且 {@code initialize=false}，结果在类加载时
     * 缓存一次，任何加载失败都返回 {@code false}。刻意不查询 {@code Bukkit}，以保证
     * 类初始化不依赖运行中的服务器。</p>
     *
     * @return 若 {@link #ADAPTER_CLASS_NAME} 指向的 legacy 适配器类可加载则为 {@code true}
     */
    public static boolean isAdapterClassPresent() {
        return ADAPTER_CLASS_PRESENT;
    }

    /**
     * 按当前运行的 Minecraft 版本解析应使用的伤害类型适配器类名。
     *
     * <p>版本来源为 {@link ServerBuildInfo#buildInfo()} 的
     * {@link ServerBuildInfo#minecraftVersionId()}，它由 ServiceLoader 提供，
     * 不依赖 {@code Bukkit.server} 是否已赋值。因此本方法在
     * {@code PluginBootstrap.bootstrap} 阶段即可安全调用；Paper 自身的
     * {@code PaperBootstrap} 也在服务器实例创建前使用同一入口。</p>
     *
     * <p>版本查询仅在真正需要解析适配器时发生，不在类初始化阶段执行。</p>
     *
     * @return 当前版本对应的适配器全限定类名
     * @throws IllegalStateException 若当前版本不在受支持区间内
     */
    public static @NotNull String currentAdapterClassName() {
        return adapterClassName(ServerBuildInfo.buildInfo().minecraftVersionId());
    }

    /**
     * 按给定 Minecraft 版本字符串解析伤害类型适配器类名。
     *
     * <p>区间划分与 {@code BridgeProvider} 保持一致：1.21.0～1.21.4 使用 1.21.4 适配器
     * （其中 1.21.0～1.21.3 缺少注册事件能力，会在门面侧先行降级）；1.21.5～1.21.10 使用
     * 1.21.10 适配器；1.21.11 与 26.1/26.2 使用 1.21.11 适配器。</p>
     *
     * @param minecraftVersion Minecraft 版本字符串，允许携带 {@code -R0.1-SNAPSHOT} 后缀
     * @return 对应的适配器全限定类名
     * @throws NullPointerException  若 {@code minecraftVersion} 为 {@code null}
     * @throws IllegalStateException 若版本不在受支持区间内或格式非法
     */
    public static @NotNull String adapterClassName(@NotNull String minecraftVersion) {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");

        String[] parts = minecraftVersion.split("-")[0].split("\\.");
        if (parts.length < 2 || parts.length > 3) {
            throw unsupported(minecraftVersion);
        }
        int major;
        int minor;
        int patch;
        try {
            major = Integer.parseInt(parts[0]);
            minor = Integer.parseInt(parts[1]);
            patch = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;
        } catch (NumberFormatException e) {
            throw unsupported(minecraftVersion);
        }

        if (major == 1 && minor == 21 && patch <= 4) {
            return ADAPTER_V1_21_4;
        } else if (major == 1 && minor == 21 && patch <= 10) {
            return ADAPTER_V1_21_10;
        } else if (major == 1 && minor == 21 && patch == 11) {
            return ADAPTER_V1_21_11;
        } else if (major == 26 && (minor == 1 || minor == 2)) {
            return ADAPTER_V1_21_11;
        } else {
            throw unsupported(minecraftVersion);
        }
    }

    private static IllegalStateException unsupported(String minecraftVersion) {
        return new IllegalStateException("不支持的 Minecraft 版本，无法选择伤害类型适配器："
                + minecraftVersion + "；支持范围为 1.21.0～1.21.11、26.1.x～26.2.x");
    }

    private static boolean computeRegistryEventCapabilityPresent() {
        try {
            ClassLoader classLoader = DamageTypeSupport.class.getClassLoader();
            Class<?> registryEventsClass = Class.forName(REGISTRY_EVENTS_CLASS_NAME, false, classLoader);
            Field damageTypeField = registryEventsClass.getField(DAMAGE_TYPE_FIELD_NAME);
            return Modifier.isStatic(damageTypeField.getModifiers());
        } catch (ClassNotFoundException | NoSuchFieldException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean computeAdapterClassPresent() {
        try {
            ClassLoader classLoader = DamageTypeSupport.class.getClassLoader();
            Class.forName(ADAPTER_CLASS_NAME, false, classLoader);
            return true;
        } catch (ClassNotFoundException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }
}
