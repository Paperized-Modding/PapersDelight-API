package dev.tako.papersdelight.api.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public interface MenuEventHandler {

    void handle(Player player, MenuItem item, InventoryClickEvent event);

    default void handleDrag(Player player, MenuItem item, InventoryDragEvent event) {
    }

    default boolean canQuickMove(Player player, MenuItem target, ItemStack moving) {
        return true;
    }
}
