package dev.example.papersdelightaddon;

import dev.tako.papersdelight.api.PapersDelightApi;
import dev.tako.papersdelight.api.menu.MenuService;
import dev.tako.papersdelight.api.menu.SimpleMenuModule;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExampleAddon extends JavaPlugin {

    private ExampleEffect effect;

    @Override
    public void onEnable() {
        if (!PapersDelightApi.isCompatible(PapersDelightApi.VERSION)) {
            getLogger().warning("PapersDelight API is not compatible with this build, disabling addon.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        effect = new ExampleEffect(this);
        getServer().getPluginManager().registerEvents(effect, this);

        MenuService menus = MenuService.get();
        if (menus == null) {
            getLogger().warning("PapersDelight menu service is unavailable, GUI module not registered.");
        } else {
            menus.registerModule(new SimpleMenuModule.Builder()
                    .id(ExampleMenu.MODULE_ID)
                    .menu(ExampleMenu::create)
                    .handler(new ExampleMenuHandler(effect))
                    .build());
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> getLogger().info("ExampleAddon heartbeat"), 200L, 1200L);

        getLogger().info("ExampleAddon enabled (PapersDelight API v" + PapersDelightApi.VERSION + ")");
    }

    @Override
    public void onDisable() {
        MenuService menus = MenuService.get();
        if (menus != null) {
            menus.unregisterModule(ExampleMenu.MODULE_ID);
        }
        if (effect != null) {
            effect.stopAll();
        }
    }
}
