package dev.tako.papersdelight.api.protection;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 方块保护检查: 玩家能不能和这个自定义方块交互; CE 或保护插件出问题时一律放行(fail-open).
 * <p><strong>必须在方块所属的主线程/region 线程同步调用</strong>.
 */
public final class ProtectionGate {

    private static final String CE_PLUGIN_CLASS =
            "net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine";
    private static final String FLAG_CLASS =
            "net.momirealms.craftengine.libraries.antigrieflib.Flag";

    private static volatile int state = 0;

    private static Method ceInstance;
    private static Method antiGriefProvider;
    private static Method testMethod;
    private static Object flagInteract;

    private static volatile boolean warned = false;

    private ProtectionGate() {
    }

    /** 只有保护插件明确拒绝才返回 {@code false}. */
    public static boolean canInteract(Player player, Location location) {
        if (player == null || location == null) return true;
        // 必须先 resolve():Flag 常量只在解析成功后才有值
        if (!resolve() || flagInteract == null) return true;

        try {
            Object ce = ceInstance.invoke(null);
            if (ce == null) return true;
            Object lib = antiGriefProvider.invoke(ce);
            if (lib == null) return true;
            return (boolean) testMethod.invoke(lib, player, flagInteract, location);
        } catch (Throwable t) {
            warnOnce("PapersDelight 保护检查失败，已放行本次操作", t);
            return true;
        }
    }

    public static boolean isUnavailable() {
        return state == 2;
    }

    private static boolean resolve() {
        int current = state;
        if (current == 1) return true;
        if (current == 2) return false;
        synchronized (ProtectionGate.class) {
            if (state != 0) return state == 1;
            try {
                ClassLoader loader = ProtectionGate.class.getClassLoader();
                // initialize=false:不触发 CE 静态初始化副作用
                Class<?> ceClass = Class.forName(CE_PLUGIN_CLASS, false, loader);
                Class<?> flagClass = Class.forName(FLAG_CLASS, false, loader);

                Method instance = ceClass.getMethod("instance");
                Method provider = ceClass.getMethod("antiGriefProvider");
                Method test = provider.getReturnType()
                        .getMethod("test", Player.class, flagClass, Object.class);

                flagInteract = flagClass.getField("INTERACT").get(null);

                ceInstance = instance;
                antiGriefProvider = provider;
                testMethod = test;
                state = 1;
                return true;
            } catch (Throwable t) {
                warnOnce("未能接入 CraftEngine 的 AntiGriefLib，保护检查将全部放行", t);
                state = 2;
                return false;
            }
        }
    }

    private static void warnOnce(String message, Throwable t) {
        if (warned) return;
        warned = true;
        Logger.getLogger("PapersDelight").log(Level.WARNING, message, t);
    }
}
