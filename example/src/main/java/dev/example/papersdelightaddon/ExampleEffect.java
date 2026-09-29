package dev.example.papersdelightaddon;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ExampleEffect implements Listener {

    public static final String EFFECT_ID = "example_glow";

    private final JavaPlugin plugin;
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();

    public ExampleEffect(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void applyEffect(Player player, int durationTicks) {
        removeEffect(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, durationTicks, 0, true, false));
        tasks.put(player.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin, () -> removeEffect(player), durationTicks));
    }

    public void removeEffect(Player player) {
        BukkitTask task = tasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        player.removePotionEffect(PotionEffectType.GLOWING);
    }

    public void stopAll() {
        tasks.values().forEach(BukkitTask::cancel);
        tasks.clear();
        Bukkit.getOnlinePlayers().forEach(player -> player.removePotionEffect(PotionEffectType.GLOWING));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeEffect(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        removeEffect(event.getEntity());
    }
}
