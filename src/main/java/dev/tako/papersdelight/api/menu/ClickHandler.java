package dev.tako.papersdelight.api.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

@FunctionalInterface
public interface ClickHandler {

    void onClick(Player player, MenuItem item, InventoryClickEvent event);
}
