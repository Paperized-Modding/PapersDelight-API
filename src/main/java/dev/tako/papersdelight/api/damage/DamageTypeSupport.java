package dev.tako.papersdelight.api.damage;

import io.papermc.paper.ServerBuildInfo;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;

/**
 * 伤害类型注册能力与适配器类名的版本探测工具.
 * <p><strong>本类不能 import 或静态引用 1.21.4+ 的专有类型</strong>, 探测全部靠反射完成, 以保持 paper-api 1.21 的 {@code compileOnly} 编译基线.
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
     * legacy 1.21.4 区间的适配器类名, 只为兼容旧引用保留.
     * <p><strong>运行时不要依赖这个固定值</strong>, 请改用 {@link #currentAdapterClassName()}.
     */
    public static final @NotNull String ADAPTER_CLASS_NAME = ADAPTER_V1_21_4;

    private static final boolean REGISTRY_EVENT_CAPABILITY_PRESENT = computeRegistryEventCapabilityPresent();
    private static final boolean ADAPTER_CLASS_PRESENT = computeAdapterClassPresent();

    private DamageTypeSupport() {
        throw new UnsupportedOperationException("DamageTypeSupport is a utility class");
    }

    /**
     * 当前运行时有没有 Paper 1.21.4+ 的 damage type 注册事件能力.
     *
     * @return 有则 {@code true}, 缺类或缺字段都算没有
     */
    public static boolean isRegistryEventCapabilityPresent() {
        return REGISTRY_EVENT_CAPABILITY_PRESENT;
    }

    /**
     * legacy 1.21.4 适配器类在当前 classpath 里能不能加载.
     * <p><strong>只探测 {@link #ADAPTER_CLASS_NAME}</strong>, 不代表当前运行版本的适配器存在; 判断当前版本请改用 {@link #currentAdapterClassName()}.
     *
     * @return 能加载则 {@code true}
     */
    public static boolean isAdapterClassPresent() {
        return ADAPTER_CLASS_PRESENT;
    }

    /**
     * 按当前运行的 Minecraft 版本给出该用的适配器类名.
     * <p>版本来自 {@link ServerBuildInfo#buildInfo()}, 不依赖 {@code Bukkit.server} 是否已赋值, 所以在 {@code PluginBootstrap.bootstrap} 阶段就能安全调用.
     *
     * @return 当前版本对应的适配器全限定类名
     * @throws IllegalStateException 当当前版本不在支持范围内时
     */
    public static @NotNull String currentAdapterClassName() {
        return adapterClassName(ServerBuildInfo.buildInfo().minecraftVersionId());
    }

    /**
     * 按给定的 Minecraft 版本字符串给出该用的适配器类名.
     * <p>1.21.0~1.21.4 用 1.21.4 适配器(1.21.0~1.21.3 没有注册事件能力, 注册时会直接降级), 1.21.5~1.21.10 用 1.21.10 适配器, 1.21.11 与 26.1/26.2 用 1.21.11 适配器.
     *
     * @param minecraftVersion Minecraft 版本字符串, 可以带 {@code -R0.1-SNAPSHOT} 之类的后缀
     * @return 对应的适配器全限定类名
     * @throws NullPointerException  当 {@code minecraftVersion} 为 {@code null} 时
     * @throws IllegalStateException 当版本不在支持范围内或格式非法时
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
