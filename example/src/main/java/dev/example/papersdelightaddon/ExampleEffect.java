package dev.example.papersdelightaddon;

import dev.tako.papersdelight.api.effect.TimedEffectManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class ExampleEffect extends TimedEffectManager {

    public static final String EFFECT_ID = "example_glow";

    public ExampleEffect(JavaPlugin plugin) {
        super(plugin, EFFECT_ID, "example:glow_effect", "effect.example.glow");
        configure(true, "yellow", "solid");
    }

    @Override
    protected void onApply(Player player, int durationTicks, int amplifier) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, durationTicks, Math.max(0, amplifier), true, false));
    }

    @Override
    protected void onRemove(Player player, RemovalCause cause) {
        player.removePotionEffect(PotionEffectType.GLOWING);
    }

    @Override
    protected void onEffectTick(Player player, int amplifier, int now) {
    }
}
