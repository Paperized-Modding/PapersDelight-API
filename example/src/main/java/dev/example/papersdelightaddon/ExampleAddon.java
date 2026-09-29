package dev.example.papersdelightaddon;

import dev.tako.papersdelight.api.PapersDelightApi;
import dev.tako.papersdelight.api.menu.MenuManager;
import dev.tako.papersdelight.api.menu.SimpleMenuModule;
import cn.chengzhimeow.ccscheduler.scheduler.CCScheduler;
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

        MenuManager.getInstance().registerModule(new SimpleMenuModule.Builder()
                .id(ExampleMenu.MODULE_ID)
                .menu(ExampleMenu::create)
                .handler(new ExampleMenuHandler(effect))
                .build());

        CCScheduler.getInstance().getGlobalRegionScheduler()
                .runTaskTimer(this, 200L, 1200L, () -> getLogger().info("ExampleAddon heartbeat"));

        getLogger().info("ExampleAddon enabled (PapersDelight API v" + PapersDelightApi.VERSION + ")");
    }

    @Override
    public void onDisable() {
        if (effect != null) {
            effect.stopAll();
        }
    }
}
