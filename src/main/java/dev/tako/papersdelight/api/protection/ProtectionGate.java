package dev.tako.papersdelight.api.protection;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 保护插件门面 —— 复用 CraftEngine 内置的 AntiGriefLib 实例。
 *
 * <p>CE 通过 shadow relocation 把 {@code net.momirealms.antigrieflib} 重定位到
 * {@code net.momirealms.craftengine.libraries.antigrieflib}，无法编译期引用，只能反射：</p>
 * <pre>
 * BukkitCraftEngine.instance().antiGriefProvider().test(Player, Flag, Object)
 * </pre>
 *
 * <p>复用而非自建实例：与 CE 共享 provider 注册、OP 放行（{@code ignoreOP(true)}）
 * 和绕过权限（{@code craftengine.antigrief.bypass}），且不重复打包 AntiGriefLib。</p>
 *
 * <p><b>fail-open：</b>任何反射失败或 provider 异常都放行，保证保护插件故障
 * 不会阻断 PapersDelight 的正常玩法。</p>
 *
 * <p>仅覆盖「保护插件未取消 Bukkit 事件」的残余场景 —— 取消事件的情况已由
 * 各监听器的 {@code ignoreCancelled = true} 处理。</p>
 *
 * <p>必须在方块所属的主线程/区域线程同步调用。</p>
 */
public final class ProtectionGate {

    private static final String CE_PLUGIN_CLASS =
            "net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine";
    private static final String FLAG_CLASS =
            "net.momirealms.craftengine.libraries.antigrieflib.Flag";

    /** 0=未解析 1=可用 2=不可用 */
    private static volatile int state = 0;

    private static Method ceInstance;
    private static Method antiGriefProvider;
    private static Method testMethod;
    private static Object flagInteract;

    private static volatile boolean warned = false;

    private ProtectionGate() {
    }

    /**
     * 玩家能否与该位置的自定义方块交互。
     * <p>只需要 INTERACT 一个 flag：放置/破坏已由各监听器的
     * {@code ignoreCancelled = true} 覆盖，篮子容器由 CE 覆盖。</p>
     */
    public static boolean canInteract(Player player, Location location) {
        if (player == null || location == null) return true;
        // 必须先 resolve()：Flag 常量只在解析成功后才有值
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

    /** 供测试与诊断：反射链是否已判定不可用 */
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
                // initialize=false：不触发 CE 静态初始化副作用
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
