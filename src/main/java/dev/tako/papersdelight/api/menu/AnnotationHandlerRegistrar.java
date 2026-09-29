package dev.tako.papersdelight.api.menu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * 把 {@link OnGuiClick} 标注的方法绑到 {@link ActionMapEventHandler}.
 * <p>方法查找只在注册时做一次, 点击时走 {@link MethodHandle} 直接调用, 不再用反射 {@code invoke}.
 */
public final class AnnotationHandlerRegistrar {

    private static final MethodType CLICK_TYPE =
            MethodType.methodType(void.class, Player.class, MenuItem.class, InventoryClickEvent.class);

    private AnnotationHandlerRegistrar() {
    }

    public static void registerHandlers(Object instance, ActionMapEventHandler handler) {
        if (instance == null || handler == null) return;

        Class<?> type = instance.getClass();
        MethodHandles.Lookup lookup = lookupFor(type);
        for (Method method : type.getDeclaredMethods()) {
            OnGuiClick annotation = method.getAnnotation(OnGuiClick.class);
            if (annotation == null) continue;

            MethodHandle click;
            try {
                click = lookup.unreflect(method).bindTo(instance).asType(CLICK_TYPE);
            } catch (Throwable t) {
                Bukkit.getLogger().log(Level.SEVERE, "无法绑定 GUI 处理器: " + annotation.actionId(), t);
                continue;
            }

            String actionId = annotation.actionId();
            handler.bind(actionId, (player, item, event) -> {
                try {
                    click.invokeExact(player, item, event);
                } catch (Throwable t) {
                    Bukkit.getLogger().log(Level.SEVERE, "Error invoking GUI handler " + actionId, t);
                }
            });
        }
    }

    private static MethodHandles.Lookup lookupFor(Class<?> type) {
        try {
            return MethodHandles.privateLookupIn(type, MethodHandles.lookup());
        } catch (IllegalAccessException | RuntimeException ignored) {
            return MethodHandles.lookup();
        }
    }
}
