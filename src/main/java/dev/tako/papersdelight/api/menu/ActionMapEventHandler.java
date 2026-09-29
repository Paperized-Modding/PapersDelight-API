package dev.tako.papersdelight.api.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.Map;

public class ActionMapEventHandler implements MenuEventHandler {

    private final Map<String, ClickHandler> handlers = new HashMap<>();

    public ActionMapEventHandler bind(String actionId, ClickHandler handler) {
        handlers.put(actionId, handler);
        return this;
    }

    @Override
    public void handle(Player player, MenuItem item, InventoryClickEvent event) {
        ClickHandler handler = handlers.get(item.getActionId());
        if (handler != null) {
            handler.onClick(player, item, event);
        }
    }
}
