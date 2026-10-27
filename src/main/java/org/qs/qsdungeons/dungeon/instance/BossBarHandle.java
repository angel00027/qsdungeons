package org.qs.qsdungeons.dungeon.instance;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.qs.qsdungeons.dungeon.BossBarSettings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Bossbar con visibilidad por cercania y una barra independiente por jugador.
 * Asi cada jugador puede ver sus propios placeholders (por ejemplo PAPI).
 */
public final class BossBarHandle {

    private final BossBarSettings settings;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private float progress;

    public BossBarHandle(BossBarSettings settings, float progress) {
        this.settings = settings;
        this.progress = progress;
    }

    public void tick(Collection<Player> candidates, Location center, float progress,
                     Function<Player, Component> titleProvider) {
        this.progress = clamp(progress);
        if (settings == null || !settings.isEnabled()) {
            hideAll();
            return;
        }
        Set<UUID> keep = new HashSet<>();
        for (Player player : candidates) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (!inRange(player, center)) {
                continue;
            }
            keep.add(player.getUniqueId());
            BossBar bar = bars.computeIfAbsent(player.getUniqueId(), id ->
                    BossBar.bossBar(Component.empty(), this.progress,
                            settings.parseColor(), settings.parseOverlay()));
            Component title = titleProvider.apply(player);
            bar.name(title == null ? Component.empty() : title);
            bar.progress(this.progress);
            player.showBossBar(bar);
        }

        for (UUID uuid : new ArrayList<>(bars.keySet())) {
            if (keep.contains(uuid)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            BossBar bar = bars.remove(uuid);
            if (player != null && bar != null) {
                player.hideBossBar(bar);
            }
        }
    }

    private boolean inRange(Player player, Location center) {
        if (center == null || center.getWorld() == null || settings.getRadius() <= 0.0D) {
            return true;
        }
        if (!player.getWorld().equals(center.getWorld())) {
            return false;
        }
        double radius = settings.getRadius();
        return player.getLocation().distanceSquared(center) <= radius * radius;
    }

    public void hide(Player player) {
        if (player == null) {
            return;
        }
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    public void hideAll() {
        for (Map.Entry<UUID, BossBar> entry : new ArrayList<>(bars.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        bars.clear();
    }

    public float getProgress() {
        return progress;
    }

    public BossBarSettings getSettings() {
        return settings;
    }

    private float clamp(double value) {
        return (float) Math.max(0.0D, Math.min(1.0D, value));
    }
}
