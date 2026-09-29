package dev.example.papersdelightaddon;

import dev.tako.papersdelight.api.menu.MenuEventHandler;
import dev.tako.papersdelight.api.menu.MenuItem;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class ExampleMenuHandler implements MenuEventHandler {

    private final ExampleEffect effect;

    public ExampleMenuHandler(ExampleEffect effect) {
        this.effect = effect;
    }

    @Override
    public void handle(Player player, MenuItem item, InventoryClickEvent event) {
        switch (item.getActionId()) {
            case ExampleMenu.ACTION_APPLY -> effect.applyEffect(player, 200);
            case ExampleMenu.ACTION_CLEAR -> effect.removeEffect(player);
            default -> {
            }
        }
    }
}
