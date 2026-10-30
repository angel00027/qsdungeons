package org.qs.qsdungeons.dungeon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Puerta de una dungeon: un cuboide de bloques que desaparece al cumplirse su
 * condicion (oleada superada, llave usada, jefe derrotado o a mano) y vuelve a
 * colocarse al reiniciar la dungeon.
 */
public final class DungeonDoor {

    /** Limite de bloques para evitar puertas gigantes por error. */
    public static final int MAX_BLOCKS = 4096;

    public enum Trigger {
        WAVE, KEY, BOSS, MANUAL;

        public static Trigger from(String raw, Trigger fallback) {
            if (raw == null) {
                return fallback;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }

    private final String id;
    private Region region;
    private final List<String> blocks = new ArrayList<>();
    private Trigger trigger = Trigger.WAVE;
    private String triggerValue = "1";
    private String openSound = "block.iron_door.open";

    public DungeonDoor(String id) {
        this.id = id;
    }

    public static DungeonDoor read(String id, ConfigurationSection section) {
        DungeonDoor door = new DungeonDoor(id);
        if (section == null) {
            return door;
        }
        door.region = Region.read(section, "region");
        door.blocks.addAll(section.getStringList("blocks"));
        door.trigger = Trigger.from(section.getString("trigger"), door.trigger);
        door.triggerValue = section.getString("trigger-value", door.triggerValue);
        door.openSound = section.getString("open-sound", door.openSound);
        return door;
    }

    public void write(ConfigurationSection section) {
        if (region != null) {
            region.write(section, "region");
        }
        section.set("blocks", blocks);
        section.set("trigger", trigger.name());
        section.set("trigger-value", triggerValue);
        section.set("open-sound", openSound);
    }

    /**
     * Guarda los bloques actuales del cuboide como estado "cerrado".
     *
     * @return cantidad de bloques solidos guardados, o -1 si es demasiado grande.
     */
    public int capture(Location first, Location second) {
        Region selection = Region.from(first, second);
        if (selection == null) {
            return -1;
        }
        if (selection.getVolume() > MAX_BLOCKS) {
            return -1;
        }
        World world = selection.getBukkitWorld();
        if (world == null) {
            return -1;
        }
        region = selection;
        blocks.clear();
        int solid = 0;
        for (int x = region.getMinX(); x <= region.getMaxX(); x++) {
            for (int y = region.getMinY(); y <= region.getMaxY(); y++) {
                for (int z = region.getMinZ(); z <= region.getMaxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    String data = block.getBlockData().getAsString();
                    blocks.add(data);
                    if (!block.getType().isAir()) {
                        solid++;
                    }
                }
            }
        }
        return solid;
    }

    /** Coloca los bloques guardados (puerta cerrada). */
    public void close() {
        World world = region == null ? null : region.getBukkitWorld();
        if (world == null || blocks.isEmpty()) {
            return;
        }
        int index = 0;
        for (int x = region.getMinX(); x <= region.getMaxX(); x++) {
            for (int y = region.getMinY(); y <= region.getMaxY(); y++) {
                for (int z = region.getMinZ(); z <= region.getMaxZ(); z++) {
                    if (index >= blocks.size()) {
                        return;
                    }
                    BlockData data = parse(blocks.get(index++));
                    Block block = world.getBlockAt(x, y, z);
                    if (data != null && !block.getBlockData().matches(data)) {
                        block.setBlockData(data, false);
                    }
                }
            }
        }
    }

    /** Elimina los bloques (puerta abierta). */
    public void open() {
        World world = region == null ? null : region.getBukkitWorld();
        if (world == null) {
            return;
        }
        for (int x = region.getMinX(); x <= region.getMaxX(); x++) {
            for (int y = region.getMinY(); y <= region.getMaxY(); y++) {
                for (int z = region.getMinZ(); z <= region.getMaxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.getType().isAir()) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    public boolean contains(Location location) {
        return region != null && region.contains(location);
    }

    public Location getCenter() {
        return region == null ? null : region.getCenter();
    }

    private static BlockData parse(String raw) {
        try {
            return Bukkit.createBlockData(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public String describeTrigger() {
        return switch (trigger) {
            case WAVE -> "Oleada " + triggerValue + " superada";
            case KEY -> "Llave " + triggerValue;
            case BOSS -> "Jefe " + triggerValue + " derrotado";
            case MANUAL -> "Manual (comando)";
        };
    }

    public String getId() {
        return id;
    }

    public Region getRegion() {
        return region;
    }

    public boolean isConfigured() {
        return region != null && !blocks.isEmpty();
    }

    public Trigger getTrigger() {
        return trigger;
    }

    public void setTrigger(Trigger trigger) {
        this.trigger = trigger == null ? Trigger.WAVE : trigger;
    }

    public String getTriggerValue() {
        return triggerValue == null ? "" : triggerValue;
    }

    public void setTriggerValue(String triggerValue) {
        this.triggerValue = triggerValue == null ? "" : triggerValue;
    }

    public String getOpenSound() {
        return openSound == null ? "" : openSound;
    }

    public void setOpenSound(String openSound) {
        this.openSound = openSound == null ? "" : openSound;
    }
}
