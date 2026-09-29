package dev.tako.papersdelight.api.menu;

import java.lang.reflect.Method;

public final class AnnotationHandlerRegistrar {

    private AnnotationHandlerRegistrar() {}

    public static void registerHandlers(Object instance, ActionMapEventHandler handler) {
        Method[] methods = instance.getClass().getDeclaredMethods();
        for (Method m : methods) {
            OnGuiClick ann = m.getAnnotation(OnGuiClick.class);
            if (ann != null) {
                handler.bind(ann.actionId(), (player, item, event) -> {
                    try {
                        m.invoke(instance, player, item, event);
                    } catch (Exception e) {
                        org.bukkit.Bukkit.getLogger().log(java.util.logging.Level.SEVERE,
                                "Error invoking GUI handler " + ann.actionId(), e);
                    }
                });
            }
        }
    }
}
