package org.qs.qsdungeons.editor;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.boss.BossDefinition;
import org.qs.qsdungeons.dungeon.Checkpoint;
import org.qs.qsdungeons.dungeon.ChestSpawn;
import org.qs.qsdungeons.dungeon.Dungeon;
import org.qs.qsdungeons.dungeon.DungeonDoor;
import org.qs.qsdungeons.dungeon.DungeonKey;
import org.qs.qsdungeons.dungeon.Region;
import org.qs.qsdungeons.dungeon.RewardBlock;
import org.qs.qsdungeons.spawn.SpawnPoint;
import org.qs.qsdungeons.util.Keys;
import org.qs.qsdungeons.util.Tags;
import org.qs.qsdungeons.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Muestra al administrador, y solo a el, donde aparecera cada cosa de una
 * dungeon: hologramas con la informacion de spawns (oleada, tipo, cantidad),
 * jefes, puertas, checkpoints, cofres y la region, mas particulas con el radio
 * de cada spawn coloreado por oleada.
 */
public final class MarkerService {

    private static final int VIEW_DISTANCE = 64;
    private static final Color[] WAVE_COLORS = {
            Color.fromRGB(0x55FF55), Color.fromRGB(0x55FFFF), Color.fromRGB(0xFFFF55),
            Color.fromRGB(0xFF55FF), Color.fromRGB(0xFFAA00), Color.fromRGB(0x5555FF),
            Color.fromRGB(0xFFFFFF), Color.fromRGB(0xAAAAAA)
    };
    private static final String[] WAVE_TAGS = {
            "#55FF55", "#55FFFF", "#FFFF55", "#FF55FF", "#FFAA00", "#5555FF", "#FFFFFF", "#AAAAAA"
    };

    private final QsDungeons plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public MarkerService(QsDungeons plugin) {
        this.plugin = plugin;
    }

    public boolean isShowing(Player player, Dungeon dungeon) {
        Session session = sessions.get(player.getUniqueId());
        return session != null && dungeon != null && session.dungeonId.equals(dungeon.getId());
    }

    /**
     * Activa o desactiva los marcadores de la dungeon para el jugador.
     *
     * @return true si quedan activados.
     */
    public boolean toggle(Player player, Dungeon dungeon) {
        if (isShowing(player, dungeon)) {
            hide(player);
            return false;
        }
        show(player, dungeon);
        return true;
    }

    public void show(Player player, Dungeon dungeon) {
        hide(player);
        Session session = new Session(dungeon.getId());
        sessions.put(player.getUniqueId(), session);
        rebuild(player, dungeon, session);
        session.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Dungeon current = plugin.getDungeonManager().getDungeon(session.dungeonId);
            if (!player.isOnline() || current == null) {
                hide(player);
                return;
            }
            session.ticks += 10;
            if (session.ticks % 40 == 0) {
                rebuild(player, current, session);
            }
            drawParticles(player, current);
        }, 10L, 10L);
    }

    public void hide(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.task != null) {
            session.task.cancel();
        }
        removeDisplays(session);
    }

    public void hideAll() {
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                hide(player);
            } else {
                Session session = sessions.remove(uuid);
                if (session != null) {
                    if (session.task != null) {
                        session.task.cancel();
                    }
                    removeDisplays(session);
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Hologramas
    // ---------------------------------------------------------------------

    private void rebuild(Player player, Dungeon dungeon, Session session) {
        List<Marker> markers = collect(dungeon);
        StringBuilder signature = new StringBuilder();
        for (Marker marker : markers) {
            signature.append(marker.text).append('@').append(marker.location.getWorld().getName())
                    .append(marker.location.getBlockX()).append(',').append(marker.location.getBlockY())
                    .append(',').append(marker.location.getBlockZ()).append('|');
        }
        if (signature.toString().equals(session.signature)) {
            return;
        }
        session.signature = signature.toString();
        removeDisplays(session);
        for (Marker marker : markers) {
            World world = marker.location.getWorld();
            TextDisplay display = world.spawn(marker.location, TextDisplay.class, entity -> {
                entity.setVisibleByDefault(false);
                entity.setPersistent(false);
                entity.text(Text.parse(marker.text));
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setSeeThrough(true);
                entity.setShadowed(true);
                entity.setLineWidth(240);
                entity.setBackgroundColor(Color.fromARGB(150, 0, 0, 0));
                Tags.set(entity, Keys.MARKER, dungeon.getId());
            });
            player.showEntity(plugin, display);
            session.displays.add(display.getUniqueId());
        }
    }

    private void removeDisplays(Session session) {
        for (UUID uuid : session.displays) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
        session.displays.clear();
        session.signature = "";
    }

    private List<Marker> collect(Dungeon dungeon) {
        List<Marker> markers = new ArrayList<>();
        for (SpawnPoint spawn : dungeon.getSpawns().values()) {
            if (spawn.getLocation() == null || spawn.getLocation().getWorld() == null) {
                continue;
            }
            String color = WAVE_TAGS[(spawn.getWave() - 1) % WAVE_TAGS.length];
            StringBuilder text = new StringBuilder();
            text.append("<").append(color).append("><bold>⚔ Spawn ").append(spawn.getId()).append("</bold></")
                    .append(color).append(">\n<gray>Oleada <white>").append(spawn.getWave())
                    .append("</white> · <white>").append(spawn.getType().name()).append("</white>\n<white>")
                    .append(spawn.getEntity()).append("</white> <gray>x").append(spawn.getAmount())
                    .append(" (max vivos ").append(spawn.getMaxAlive()).append(")\n<gray>Radio ")
                    .append(format(spawn.getRadius()));
            if (spawn.getRespawnSeconds() > 0) {
                text.append(" · reaparece ").append(spawn.getRespawnSeconds()).append("s");
            }
            appendKeys(text, dungeon, "spawn", spawn.getId());
            markers.add(new Marker(spawn.getLocation().clone().add(0, 2.4D, 0), text.toString()));
        }
        for (BossDefinition boss : dungeon.getBosses().values()) {
            Location location = boss.getLocation();
            if (location == null && dungeon.getRegion() != null) {
                location = dungeon.getRegion().getCenter();
            }
            if (location == null || location.getWorld() == null) {
                continue;
            }
            StringBuilder text = new StringBuilder();
            text.append("<red><bold>☠ Jefe ").append(boss.getId()).append("</bold></red>\n<white>")
                    .append(boss.getEntity()).append("</white> <gray>(").append(boss.getType().name())
                    .append(")\n<gray>Vida ").append(format(boss.getHealth()));
            if (boss.isCinematicEnabled()) {
                text.append(" · cinematica ").append(boss.getCinematicDelay()).append("s");
            }
            if (!boss.getMusicSound().isBlank()) {
                text.append("\n<gray>Musica: <white>").append(boss.getMusicSound());
            }
            if (boss.getLocation() == null) {
                text.append("\n<yellow>Sin ubicacion: usa el centro de la region");
            }
            appendKeys(text, dungeon, "boss", boss.getId());
            markers.add(new Marker(location.clone().add(0, 3.0D, 0), text.toString()));
        }
        for (Checkpoint checkpoint : dungeon.getCheckpoints().values()) {
            if (checkpoint.getLocation() != null && checkpoint.getLocation().getWorld() != null) {
                markers.add(new Marker(checkpoint.getLocation().clone().add(0, 2.2D, 0),
                        "<aqua><bold>⚑ Checkpoint " + checkpoint.getId() + "</bold></aqua>\n<gray>Radio "
                                + format(checkpoint.getRadius())));
            }
        }
        for (DungeonDoor door : dungeon.getDoors().values()) {
            Location center = door.getCenter();
            if (center != null) {
                markers.add(new Marker(center.clone().add(0, 1.0D, 0),
                        "<yellow><bold>▣ Puerta " + door.getId() + "</bold></yellow>\n<gray>Se abre: <white>"
                                + door.describeTrigger()));
            }
        }
        for (ChestSpawn chest : dungeon.getChests().values()) {
            if (chest.getLocation() != null && chest.getLocation().getWorld() != null) {
                String text = "<gold>Cofre " + chest.getId() + "</gold>\n<gray>Loot: <white>"
                        + (chest.getLootTable().isBlank() ? "-" : chest.getLootTable());
                if (!chest.getRequiredKey().isBlank()) {
                    text += "\n<gold>Necesita llave " + chest.getRequiredKey();
                }
                markers.add(new Marker(chest.getLocation().clone().add(0.5D, 1.4D, 0.5D), text));
            }
        }
        for (RewardBlock reward : dungeon.getRewards().values()) {
            if (reward.getLocation() != null && reward.getLocation().getWorld() != null) {
                markers.add(new Marker(reward.getLocation().clone().add(0.5D, 1.4D, 0.5D),
                        "<gold>Bloque premiado " + reward.getId() + "</gold>"));
            }
        }
        addPoint(markers, dungeon.getEntry(), "<light_purple><bold>Entrada</bold></light_purple>");
        addPoint(markers, dungeon.getExit(), "<light_purple><bold>Salida</bold></light_purple>");
        addPoint(markers, dungeon.getWarp(), "<light_purple><bold>Punto TP</bold></light_purple>");
        return markers;
    }

    private void appendKeys(StringBuilder text, Dungeon dungeon, String type, String id) {
        for (DungeonKey key : dungeon.getKeys().values()) {
            if (key.dropsFrom(type, id)) {
                text.append("\n<gold>Suelta llave <white>").append(key.getId()).append("</white> (")
                        .append(Math.round(key.getDropChance() * 100)).append("%)");
            }
        }
    }

    private void addPoint(List<Marker> markers, Location location, String text) {
        if (location != null && location.getWorld() != null) {
            markers.add(new Marker(location.clone().add(0, 2.2D, 0), text));
        }
    }

    // ---------------------------------------------------------------------
    // Particulas
    // ---------------------------------------------------------------------

    private void drawParticles(Player player, Dungeon dungeon) {
        for (SpawnPoint spawn : dungeon.getSpawns().values()) {
            Color color = WAVE_COLORS[(spawn.getWave() - 1) % WAVE_COLORS.length];
            circle(player, spawn.getLocation(), Math.max(0.6D, spawn.getRadius()), color);
            column(player, spawn.getLocation(), 2.0D, color);
        }
        for (BossDefinition boss : dungeon.getBosses().values()) {
            Location location = boss.getLocation();
            if (location == null && dungeon.getRegion() != null) {
                location = dungeon.getRegion().getCenter();
            }
            circle(player, location, 1.5D, Color.RED);
            column(player, location, 3.0D, Color.RED);
        }
        for (Checkpoint checkpoint : dungeon.getCheckpoints().values()) {
            circle(player, checkpoint.getLocation(), checkpoint.getRadius(), Color.AQUA);
        }
        for (DungeonDoor door : dungeon.getDoors().values()) {
            box(player, door.getRegion(), Color.YELLOW, 0.5D);
        }
        Region region = dungeon.getRegion();
        if (region != null) {
            long perimeter = 4L * ((region.getMaxX() - region.getMinX()) + (region.getMaxZ() - region.getMinZ()))
                    + 4L * (region.getMaxY() - region.getMinY());
            box(player, region, Color.PURPLE, Math.max(1.0D, perimeter / 600.0D));
        }
    }

    private boolean near(Player player, Location location) {
        return location != null && location.getWorld() != null
                && location.getWorld().equals(player.getWorld())
                && location.distanceSquared(player.getLocation()) <= VIEW_DISTANCE * VIEW_DISTANCE;
    }

    private void dust(Player player, Location location, Color color) {
        if (near(player, location)) {
            player.spawnParticle(Particle.DUST, location, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(color, 1.0F));
        }
    }

    private void circle(Player player, Location center, double radius, Color color) {
        if (center == null || !near(player, center)) {
            return;
        }
        int points = (int) Math.max(12, Math.min(64, radius * 10));
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.15D, Math.sin(angle) * radius);
            dust(player, point, color);
        }
    }

    private void column(Player player, Location base, double height, Color color) {
        if (base == null || !near(player, base)) {
            return;
        }
        for (double y = 0; y <= height; y += 0.4D) {
            dust(player, base.clone().add(0, y, 0), color);
        }
    }

    private void box(Player player, Region region, Color color, double step) {
        if (region == null || region.getBukkitWorld() == null
                || !region.getBukkitWorld().equals(player.getWorld())) {
            return;
        }
        double minX = region.getMinX();
        double minY = region.getMinY();
        double minZ = region.getMinZ();
        double maxX = region.getMaxX() + 1;
        double maxY = region.getMaxY() + 1;
        double maxZ = region.getMaxZ() + 1;
        World world = region.getBukkitWorld();
        double[][] corners = {{minX, minZ}, {maxX, minZ}, {maxX, maxZ}, {minX, maxZ}};
        for (double y : new double[]{minY, maxY}) {
            line(player, world, minX, y, minZ, maxX, y, minZ, step, color);
            line(player, world, maxX, y, minZ, maxX, y, maxZ, step, color);
            line(player, world, maxX, y, maxZ, minX, y, maxZ, step, color);
            line(player, world, minX, y, maxZ, minX, y, minZ, step, color);
        }
        for (double[] corner : corners) {
            line(player, world, corner[0], minY, corner[1], corner[0], maxY, corner[1], step, color);
        }
    }

    private void line(Player player, World world, double x1, double y1, double z1,
                      double x2, double y2, double z2, double step, Color color) {
        double length = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) + Math.pow(z2 - z1, 2));
        int points = (int) Math.max(1, Math.min(400, length / step));
        for (int i = 0; i <= points; i++) {
            double t = i / (double) points;
            dust(player, new Location(world, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, z1 + (z2 - z1) * t), color);
        }
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private record Marker(Location location, String text) {
    }

    private static final class Session {
        private final String dungeonId;
        private final List<UUID> displays = new ArrayList<>();
        private String signature = "";
        private BukkitTask task;
        private int ticks;

        private Session(String dungeonId) {
            this.dungeonId = dungeonId;
        }
    }
}
