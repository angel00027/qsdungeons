package org.qs.qsdungeons.gui;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.boss.BossDefinition;
import org.qs.qsdungeons.dungeon.Checkpoint;
import org.qs.qsdungeons.dungeon.Dungeon;
import org.qs.qsdungeons.dungeon.DungeonDoor;
import org.qs.qsdungeons.dungeon.DungeonKey;
import org.qs.qsdungeons.dungeon.DungeonManager;
import org.qs.qsdungeons.dungeon.DungeonSettings;
import org.qs.qsdungeons.dungeon.instance.DungeonInstance;
import org.qs.qsdungeons.spawn.SpawnPoint;
import org.qs.qsdungeons.util.EconomyHook;
import org.qs.qsdungeons.util.Sounds;
import org.qs.qsdungeons.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Menus de las funciones avanzadas: ajustes extra (sala de espera, oleadas,
 * escalado, recompensas, requisitos, musica), puertas, llaves y checkpoints.
 * Usa los mismos helpers y menus.yml que {@link GuiManager}.
 */
final class FeatureMenus {

    /** item de menus.yml -> clave de DungeonSettings. */
    private static final Map<String, String> ADVANCED = new LinkedHashMap<>();

    static {
        ADVANCED.put("lobby-seconds", "lobby-seconds");
        ADVANCED.put("wave-delay-seconds", "wave-delay-seconds");
        ADVANCED.put("scaling-health", "scaling.health-per-player");
        ADVANCED.put("scaling-damage", "scaling.damage-per-player");
        ADVANCED.put("scaling-mobs", "scaling.mobs-per-player");
        ADVANCED.put("reward-loot", "reward.loot-table");
        ADVANCED.put("reward-money", "reward.money");
        ADVANCED.put("reward-exp", "reward.exp");
        ADVANCED.put("req-level", "requirements.level");
        ADVANCED.put("req-money", "requirements.money");
        ADVANCED.put("req-completed", "requirements.completed");
        ADVANCED.put("req-item", "requirements.item");
        ADVANCED.put("ambient-sound", "ambient.sound");
        ADVANCED.put("ambient-interval", "ambient.interval-seconds");
        ADVANCED.put("ambient-volume", "ambient.volume");
        ADVANCED.put("ambient-pitch", "ambient.pitch");
    }

    private final QsDungeons plugin;
    private final GuiManager gui;
    private final Map<UUID, Location[]> doorCorners = new HashMap<>();

    FeatureMenus(QsDungeons plugin, GuiManager gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    boolean open(Player player, String menu, Dungeon dungeon, String targetId, int page) {
        if (dungeon == null) {
            return false;
        }
        switch (menu) {
            case "advanced" -> openAdvanced(player, dungeon);
            case "doors" -> openDoors(player, dungeon, page);
            case "door-editor" -> openDoorEditor(player, dungeon, dungeon.getDoors().get(targetId));
            case "keys" -> openKeys(player, dungeon, page);
            case "key-editor" -> openKeyEditor(player, dungeon, dungeon.getKeys().get(targetId));
            case "checkpoints" -> openCheckpoints(player, dungeon, page);
            case "checkpoint-editor" -> openCheckpointEditor(player, dungeon, dungeon.getCheckpoints().get(targetId));
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------
    // Ajustes avanzados
    // ---------------------------------------------------------------------

    void openAdvanced(Player player, Dungeon dungeon) {
        GuiHolder holder = new GuiHolder("advanced", dungeon.getId(), null, 0);
        Inventory inventory = gui.create(holder);
        for (String itemKey : ADVANCED.keySet()) {
            Map<String, String> ph = gui.base(dungeon);
            ph.put("value", advancedValue(dungeon.getSettings(), itemKey));
            gui.button(inventory, holder, "advanced", itemKey, "adv|" + itemKey, ph);
        }
        Map<String, String> ph = gui.base(dungeon);
        ph.put("value", gui.empty(dungeon.getSettings().getAmbientSound()));
        gui.button(inventory, holder, "advanced", "ambient-test", "adv|ambient-test", ph);
        gui.button(inventory, holder, "advanced", "settings", "open|settings", ph);
        gui.button(inventory, holder, "advanced", "back", "open|dungeon-editor", ph);
        gui.open(player, inventory);
    }

    private String advancedValue(DungeonSettings settings, String itemKey) {
        return switch (itemKey) {
            case "lobby-seconds" -> settings.getLobbySeconds() <= 0 ? "Sin sala de espera"
                    : gui.duration(settings.getLobbySeconds());
            case "wave-delay-seconds" -> gui.duration(settings.getWaveDelaySeconds());
            case "scaling-health" -> percent(settings.getScalingHealth());
            case "scaling-damage" -> percent(settings.getScalingDamage());
            case "scaling-mobs" -> percent(settings.getScalingMobs());
            case "reward-loot" -> gui.empty(settings.getRewardLootTable());
            case "reward-money" -> "$" + EconomyHook.format(settings.getRewardMoney());
            case "reward-exp" -> settings.getRewardExp() + " XP";
            case "req-level" -> settings.getRequiredLevel() <= 0 ? "Ninguno" : "Nivel " + settings.getRequiredLevel();
            case "req-money" -> settings.getEntryCost() <= 0 ? "Gratis" : "$" + EconomyHook.format(settings.getEntryCost());
            case "req-completed" -> settings.getRequiredDungeons().isEmpty() ? "Ninguna"
                    : String.join(", ", settings.getRequiredDungeons());
            case "req-item" -> settings.getEntryItem() == null ? "Ninguno"
                    : settings.getEntryItem().getAmount() + "x " + DungeonManager.itemName(settings.getEntryItem());
            case "ambient-sound" -> gui.empty(settings.getAmbientSound());
            case "ambient-interval" -> gui.duration(settings.getAmbientInterval());
            case "ambient-volume" -> gui.number(settings.getAmbientVolume());
            case "ambient-pitch" -> gui.number(settings.getAmbientPitch());
            default -> "-";
        };
    }

    private static String percent(double value) {
        return "+" + Math.round(value * 100) + "% por jugador extra";
    }

    private void handleAdvanced(Player player, Dungeon dungeon, String itemKey, boolean rightClick) {
        DungeonSettings settings = dungeon.getSettings();
        if (itemKey.equals("ambient-test")) {
            String sound = settings.getAmbientSound();
            if (sound.isBlank()) {
                gui.msg(player, "invalid-value", Map.of("value", "ambient.sound"));
            } else {
                Sounds.stop(player, sound, SoundCategory.RECORDS);
                Sounds.play(player, sound, SoundCategory.RECORDS,
                        (float) settings.getAmbientVolume(), (float) settings.getAmbientPitch());
            }
            return;
        }
        String key = ADVANCED.get(itemKey);
        if (key == null) {
            return;
        }
        switch (itemKey) {
            case "reward-loot" -> {
                if (rightClick) {
                    settings.setRewardLootTable("");
                    gui.save(dungeon);
                    openAdvanced(player, dungeon);
                } else {
                    gui.openLootPicker(player, dungeon, "dungeon-reward", null, 0);
                }
                return;
            }
            case "req-item" -> {
                if (rightClick) {
                    settings.setEntryItem(null);
                } else {
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (hand.getType().isAir()) {
                        gui.msg(player, "empty-hand", Map.of());
                        return;
                    }
                    settings.setEntryItem(hand);
                }
                gui.save(dungeon);
                openAdvanced(player, dungeon);
                return;
            }
            case "req-completed", "ambient-sound" -> {
                prompt(player, dungeon, key, advancedValue(settings, itemKey));
                return;
            }
            default -> {
            }
        }
        if (rightClick) {
            prompt(player, dungeon, key, advancedValue(settings, itemKey));
            return;
        }
        String next = switch (itemKey) {
            case "lobby-seconds" -> String.valueOf(settings.getLobbySeconds() >= 120 ? 0 : settings.getLobbySeconds() + 10);
            case "wave-delay-seconds" -> String.valueOf(settings.getWaveDelaySeconds() >= 60 ? 0 : settings.getWaveDelaySeconds() + 5);
            case "scaling-health" -> cyclePercent(settings.getScalingHealth());
            case "scaling-damage" -> cyclePercent(settings.getScalingDamage());
            case "scaling-mobs" -> cyclePercent(settings.getScalingMobs());
            case "reward-money" -> String.valueOf(settings.getRewardMoney() + 100);
            case "reward-exp" -> String.valueOf(settings.getRewardExp() + 50);
            case "req-level" -> String.valueOf(settings.getRequiredLevel() >= 100 ? 0 : settings.getRequiredLevel() + 5);
            case "req-money" -> String.valueOf(settings.getEntryCost() + 100);
            case "ambient-interval" -> String.valueOf(settings.getAmbientInterval() >= 600 ? 10 : settings.getAmbientInterval() + 10);
            case "ambient-volume" -> String.valueOf(settings.getAmbientVolume() >= 2.0D ? 0.2D
                    : Math.round((settings.getAmbientVolume() + 0.2D) * 10) / 10.0D);
            case "ambient-pitch" -> String.valueOf(settings.getAmbientPitch() >= 2.0D ? 0.5D
                    : Math.round((settings.getAmbientPitch() + 0.1D) * 10) / 10.0D);
            default -> null;
        };
        if (next != null && settings.set(key, next)) {
            gui.save(dungeon);
        }
        openAdvanced(player, dungeon);
    }

    private static String cyclePercent(double current) {
        double next = current >= 1.0D ? 0.0D : Math.round((current + 0.1D) * 10) / 10.0D;
        return String.valueOf(next);
    }

    private void prompt(Player player, Dungeon dungeon, String key, String current) {
        gui.startPrompt(player, "value", Map.of("key", key, "current", current),
                () -> openAdvanced(player, dungeon),
                input -> {
                    if (!dungeon.getSettings().set(key, input)) {
                        gui.msg(player, "invalid-value", Map.of("value", input));
                    } else {
                        gui.save(dungeon);
                        gui.msg(player, "saved", Map.of("dungeon", dungeon.getId()));
                    }
                    openAdvanced(player, dungeon);
                });
    }

    // ---------------------------------------------------------------------
    // Puertas
    // ---------------------------------------------------------------------

    private void openDoors(Player player, Dungeon dungeon, int page) {
        GuiHolder holder = new GuiHolder("doors", dungeon.getId(), null, page);
        Inventory inventory = gui.create(holder);
        List<GuiManager.ListEntry> entries = new ArrayList<>();
        for (DungeonDoor door : GuiManager.sorted(dungeon.getDoors().values(), DungeonDoor::getId)) {
            entries.add(new GuiManager.ListEntry(door.getId(), doorPlaceholders(dungeon, door)));
        }
        gui.fillDynamic(inventory, holder, "doors", "door-entry", entries, "door.entry");
        gui.navigation(inventory, holder, "doors", "dungeon-editor", page);
        gui.button(inventory, holder, "doors", "create", "door.create", gui.base(dungeon));
        gui.open(player, inventory);
    }

    private Map<String, String> doorPlaceholders(Dungeon dungeon, DungeonDoor door) {
        Map<String, String> ph = gui.base(dungeon);
        ph.put("door", door.getId());
        ph.put("trigger", door.getTrigger().name());
        ph.put("trigger_value", gui.empty(door.getTriggerValue()));
        ph.put("trigger_text", door.describeTrigger());
        ph.put("region", door.getRegion() == null ? gui.empty("") : door.getRegion().describe());
        ph.put("configured", gui.yesNo(door.isConfigured()));
        ph.put("sound", gui.empty(door.getOpenSound()));
        DungeonInstance instance = plugin.getDungeonManager().getInstance(dungeon.getId());
        ph.put("open", gui.yesNo(instance != null && instance.isDoorOpen(door.getId())));
        return ph;
    }

    private void openDoorEditor(Player player, Dungeon dungeon, DungeonDoor door) {
        if (door == null) {
            openDoors(player, dungeon, 0);
            return;
        }
        GuiHolder holder = new GuiHolder("door-editor", dungeon.getId(), door.getId(), 0);
        Inventory inventory = gui.create(holder);
        Map<String, String> ph = doorPlaceholders(dungeon, door);
        Location[] corners = doorCorners.get(player.getUniqueId());
        ph.put("corner1", gui.location(corners == null ? null : corners[0]));
        ph.put("corner2", gui.location(corners == null ? null : corners[1]));
        for (String key : List.of("info", "corner1", "corner2", "capture", "trigger", "trigger-value",
                "sound", "test-open", "test-close", "teleport", "delete")) {
            gui.button(inventory, holder, "door-editor", key, "door.field|" + key, ph);
        }
        gui.button(inventory, holder, "door-editor", "back", "open|doors", ph);
        gui.open(player, inventory);
    }

    private void handleDoorField(Player player, Dungeon dungeon, DungeonDoor door, String field, boolean rightClick) {
        if (door == null) {
            openDoors(player, dungeon, 0);
            return;
        }
        switch (field) {
            case "corner1", "corner2" -> {
                Block block = player.getTargetBlockExact(10);
                Location location = block == null ? player.getLocation().getBlock().getLocation() : block.getLocation();
                Location[] corners = doorCorners.computeIfAbsent(player.getUniqueId(), ignored -> new Location[2]);
                corners[field.equals("corner1") ? 0 : 1] = location;
                gui.msg(player, "position-set", Map.of("dungeon", door.getId()));
            }
            case "capture" -> {
                Location[] corners = doorCorners.get(player.getUniqueId());
                if (corners == null || corners[0] == null || corners[1] == null) {
                    gui.msg(player, "region-missing", Map.of());
                    break;
                }
                int solid = door.capture(corners[0], corners[1]);
                if (solid < 0) {
                    gui.msg(player, "door-too-big", Map.of("max", String.valueOf(DungeonDoor.MAX_BLOCKS)));
                } else {
                    gui.save(dungeon);
                    gui.msg(player, "door-captured", Map.of("door", door.getId(), "blocks", String.valueOf(solid)));
                }
            }
            case "trigger" -> {
                DungeonDoor.Trigger[] values = DungeonDoor.Trigger.values();
                DungeonDoor.Trigger next = values[(door.getTrigger().ordinal() + 1) % values.length];
                door.setTrigger(next);
                door.setTriggerValue(defaultTriggerValue(dungeon, next));
                gui.save(dungeon);
            }
            case "trigger-value" -> {
                if (rightClick) {
                    gui.startPrompt(player, "value", Map.of("key", "trigger-value", "current", door.getTriggerValue()),
                            () -> openDoorEditor(player, dungeon, door), input -> {
                                door.setTriggerValue(input.trim().toLowerCase(Locale.ROOT));
                                gui.save(dungeon);
                                openDoorEditor(player, dungeon, door);
                            });
                    return;
                }
                door.setTriggerValue(nextTriggerValue(dungeon, door));
                gui.save(dungeon);
            }
            case "sound" -> {
                gui.startPrompt(player, "value", Map.of("key", "open-sound", "current", door.getOpenSound()),
                        () -> openDoorEditor(player, dungeon, door), input -> {
                            door.setOpenSound(input.equalsIgnoreCase("none") ? "" : input.trim());
                            gui.save(dungeon);
                            openDoorEditor(player, dungeon, door);
                        });
                return;
            }
            case "test-open" -> {
                if (door.isConfigured()) {
                    door.open();
                    Sounds.play(player, door.getOpenSound(), SoundCategory.BLOCKS, 1.0F, 1.0F);
                }
            }
            case "test-close" -> {
                if (door.isConfigured()) {
                    door.close();
                }
            }
            case "teleport" -> {
                if (door.getCenter() != null) {
                    player.closeInventory();
                    player.teleport(door.getCenter());
                    return;
                }
            }
            case "delete" -> {
                gui.openConfirm(player, dungeon, door.getId(), "delete-door", door.getId(), "doors");
                return;
            }
            default -> {
            }
        }
        openDoorEditor(player, dungeon, door);
    }

    private String defaultTriggerValue(Dungeon dungeon, DungeonDoor.Trigger trigger) {
        return switch (trigger) {
            case WAVE -> "1";
            case KEY -> dungeon.getKeys().isEmpty() ? "" : dungeon.getKeys().keySet().iterator().next();
            case BOSS -> dungeon.getBosses().isEmpty() ? "" : dungeon.getBosses().keySet().iterator().next();
            case MANUAL -> "";
        };
    }

    private String nextTriggerValue(Dungeon dungeon, DungeonDoor door) {
        List<String> options = new ArrayList<>();
        switch (door.getTrigger()) {
            case WAVE -> {
                TreeSet<Integer> waves = new TreeSet<>();
                for (SpawnPoint spawn : dungeon.getSpawns().values()) {
                    waves.add(spawn.getWave());
                }
                if (waves.isEmpty()) {
                    waves.add(1);
                }
                waves.forEach(wave -> options.add(String.valueOf(wave)));
            }
            case KEY -> options.addAll(dungeon.getKeys().keySet());
            case BOSS -> options.addAll(dungeon.getBosses().keySet());
            case MANUAL -> {
            }
        }
        if (options.isEmpty()) {
            return door.getTriggerValue();
        }
        int index = options.indexOf(door.getTriggerValue());
        return options.get((index + 1) % options.size());
    }

    // ---------------------------------------------------------------------
    // Llaves
    // ---------------------------------------------------------------------

    private void openKeys(Player player, Dungeon dungeon, int page) {
        GuiHolder holder = new GuiHolder("keys", dungeon.getId(), null, page);
        Inventory inventory = gui.create(holder);
        List<GuiManager.ListEntry> entries = new ArrayList<>();
        for (DungeonKey key : GuiManager.sorted(dungeon.getKeys().values(), DungeonKey::getId)) {
            entries.add(new GuiManager.ListEntry(key.getId(), keyPlaceholders(dungeon, key)));
        }
        gui.fillDynamic(inventory, holder, "keys", "key-entry", entries, "key.entry");
        gui.navigation(inventory, holder, "keys", "dungeon-editor", page);
        gui.button(inventory, holder, "keys", "create", "key.create", gui.base(dungeon));
        gui.open(player, inventory);
    }

    private Map<String, String> keyPlaceholders(Dungeon dungeon, DungeonKey key) {
        Map<String, String> ph = gui.base(dungeon);
        ph.put("key", key.getId());
        ph.put("name", key.getName());
        ph.put("item", key.getItem() == null ? "TRIPWIRE_HOOK" : key.getItem().getType().name());
        ph.put("drop_from", gui.empty(key.getDropFrom()));
        ph.put("chance", Math.round(key.getDropChance() * 100) + "%");
        List<String> uses = new ArrayList<>();
        for (DungeonDoor door : dungeon.getDoors().values()) {
            if (door.getTrigger() == DungeonDoor.Trigger.KEY && door.getTriggerValue().equalsIgnoreCase(key.getId())) {
                uses.add("puerta " + door.getId());
            }
        }
        dungeon.getChests().values().stream()
                .filter(chest -> chest.getRequiredKey().equalsIgnoreCase(key.getId()))
                .forEach(chest -> uses.add("cofre " + chest.getId()));
        ph.put("uses", uses.isEmpty() ? gui.empty("") : String.join(", ", uses));
        return ph;
    }

    private void openKeyEditor(Player player, Dungeon dungeon, DungeonKey key) {
        if (key == null) {
            openKeys(player, dungeon, 0);
            return;
        }
        GuiHolder holder = new GuiHolder("key-editor", dungeon.getId(), key.getId(), 0);
        Inventory inventory = gui.create(holder);
        Map<String, String> ph = keyPlaceholders(dungeon, key);
        for (String field : List.of("info", "name", "item", "drop-from", "drop-chance", "give", "delete")) {
            gui.button(inventory, holder, "key-editor", field, "key.field|" + field, ph);
        }
        inventory.setItem(13, key.createItem(dungeon));
        gui.button(inventory, holder, "key-editor", "back", "open|keys", ph);
        gui.open(player, inventory);
    }

    private void handleKeyField(Player player, Dungeon dungeon, DungeonKey key, String field, boolean rightClick) {
        if (key == null) {
            openKeys(player, dungeon, 0);
            return;
        }
        switch (field) {
            case "name" -> {
                gui.startPrompt(player, "value", Map.of("key", "name", "current", key.getName()),
                        () -> openKeyEditor(player, dungeon, key), input -> {
                            key.setName(input);
                            gui.save(dungeon);
                            openKeyEditor(player, dungeon, key);
                        });
                return;
            }
            case "item" -> {
                if (rightClick) {
                    key.setItem(null);
                } else {
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (hand.getType().isAir()) {
                        gui.msg(player, "empty-hand", Map.of());
                        return;
                    }
                    key.setItem(hand);
                }
                gui.save(dungeon);
            }
            case "drop-from" -> {
                List<String> options = new ArrayList<>();
                options.add("");
                for (SpawnPoint spawn : GuiManager.sorted(dungeon.getSpawns().values(), SpawnPoint::getId)) {
                    options.add("spawn:" + spawn.getId());
                }
                for (BossDefinition boss : GuiManager.sorted(dungeon.getBosses().values(), BossDefinition::getId)) {
                    options.add("boss:" + boss.getId());
                }
                int index = options.indexOf(key.getDropFrom());
                key.setDropFrom(options.get((index + 1) % options.size()));
                gui.save(dungeon);
            }
            case "drop-chance" -> {
                if (rightClick) {
                    gui.startPrompt(player, "value", Map.of("key", "drop-chance",
                                    "current", Math.round(key.getDropChance() * 100) + "%"),
                            () -> openKeyEditor(player, dungeon, key), input -> {
                                try {
                                    double value = Double.parseDouble(input.replace("%", "").replace(',', '.').trim());
                                    key.setDropChance(value > 1.0D ? value / 100.0D : value);
                                    gui.save(dungeon);
                                } catch (NumberFormatException exception) {
                                    gui.msg(player, "invalid-value", Map.of("value", input));
                                }
                                openKeyEditor(player, dungeon, key);
                            });
                    return;
                }
                double next = key.getDropChance() >= 1.0D ? 0.1D : Math.round((key.getDropChance() + 0.1D) * 10) / 10.0D;
                key.setDropChance(next);
                gui.save(dungeon);
            }
            case "give" -> {
                player.getInventory().addItem(key.createItem(dungeon));
            }
            case "delete" -> {
                gui.openConfirm(player, dungeon, key.getId(), "delete-key", key.getId(), "keys");
                return;
            }
            default -> {
            }
        }
        openKeyEditor(player, dungeon, key);
    }

    // ---------------------------------------------------------------------
    // Checkpoints
    // ---------------------------------------------------------------------

    private void openCheckpoints(Player player, Dungeon dungeon, int page) {
        GuiHolder holder = new GuiHolder("checkpoints", dungeon.getId(), null, page);
        Inventory inventory = gui.create(holder);
        List<GuiManager.ListEntry> entries = new ArrayList<>();
        for (Checkpoint checkpoint : GuiManager.sorted(dungeon.getCheckpoints().values(), Checkpoint::getId)) {
            entries.add(new GuiManager.ListEntry(checkpoint.getId(), checkpointPlaceholders(dungeon, checkpoint)));
        }
        gui.fillDynamic(inventory, holder, "checkpoints", "checkpoint-entry", entries, "checkpoint.entry");
        gui.navigation(inventory, holder, "checkpoints", "dungeon-editor", page);
        gui.button(inventory, holder, "checkpoints", "create", "checkpoint.create", gui.base(dungeon));
        gui.open(player, inventory);
    }

    private Map<String, String> checkpointPlaceholders(Dungeon dungeon, Checkpoint checkpoint) {
        Map<String, String> ph = gui.base(dungeon);
        ph.put("checkpoint", checkpoint.getId());
        ph.put("location", gui.location(checkpoint.getLocation()));
        ph.put("radius", gui.number(checkpoint.getRadius()));
        return ph;
    }

    private void openCheckpointEditor(Player player, Dungeon dungeon, Checkpoint checkpoint) {
        if (checkpoint == null) {
            openCheckpoints(player, dungeon, 0);
            return;
        }
        GuiHolder holder = new GuiHolder("checkpoint-editor", dungeon.getId(), checkpoint.getId(), 0);
        Inventory inventory = gui.create(holder);
        Map<String, String> ph = checkpointPlaceholders(dungeon, checkpoint);
        for (String field : List.of("info", "location", "radius", "teleport", "delete")) {
            gui.button(inventory, holder, "checkpoint-editor", field, "checkpoint.field|" + field, ph);
        }
        gui.button(inventory, holder, "checkpoint-editor", "back", "open|checkpoints", ph);
        gui.open(player, inventory);
    }

    private void handleCheckpointField(Player player, Dungeon dungeon, Checkpoint checkpoint, String field,
                                       boolean rightClick) {
        if (checkpoint == null) {
            openCheckpoints(player, dungeon, 0);
            return;
        }
        switch (field) {
            case "location" -> {
                checkpoint.setLocation(player.getLocation().clone());
                gui.save(dungeon);
                gui.msg(player, "position-set", Map.of("dungeon", checkpoint.getId()));
            }
            case "radius" -> {
                if (rightClick) {
                    gui.startPrompt(player, "value", Map.of("key", "radius", "current", gui.number(checkpoint.getRadius())),
                            () -> openCheckpointEditor(player, dungeon, checkpoint), input -> {
                                try {
                                    checkpoint.setRadius(Double.parseDouble(input.replace(',', '.').trim()));
                                    gui.save(dungeon);
                                } catch (NumberFormatException exception) {
                                    gui.msg(player, "invalid-value", Map.of("value", input));
                                }
                                openCheckpointEditor(player, dungeon, checkpoint);
                            });
                    return;
                }
                checkpoint.setRadius(checkpoint.getRadius() >= 10 ? 1 : checkpoint.getRadius() + 1);
                gui.save(dungeon);
            }
            case "teleport" -> {
                if (checkpoint.getLocation() != null) {
                    player.closeInventory();
                    player.teleport(checkpoint.getLocation());
                    return;
                }
            }
            case "delete" -> {
                gui.openConfirm(player, dungeon, checkpoint.getId(), "delete-checkpoint", checkpoint.getId(),
                        "checkpoints");
                return;
            }
            default -> {
            }
        }
        openCheckpointEditor(player, dungeon, checkpoint);
    }

    // ---------------------------------------------------------------------
    // Clics, creacion y borrado
    // ---------------------------------------------------------------------

    void handleClick(Player player, GuiHolder holder, Dungeon dungeon, String action, String payload,
                     boolean rightClick) {
        if (dungeon == null) {
            return;
        }
        switch (action) {
            case "adv" -> handleAdvanced(player, dungeon, payload, rightClick);
            case "feature.markers" -> {
                player.closeInventory();
                boolean shown = plugin.getMarkerService().toggle(player, dungeon);
                plugin.getMessages().send(player, shown ? "markers.shown" : "markers.hidden",
                        Map.of("dungeon", dungeon.getId()));
            }
            case "feature.warp" -> {
                dungeon.setWarp(rightClick ? null : player.getLocation().clone());
                gui.save(dungeon);
                plugin.getMessages().send(player, rightClick ? "tp.warp-cleared" : "tp.warp-set",
                        Map.of("dungeon", dungeon.getId()));
                gui.openDungeonEditor(player, dungeon);
            }
            case "feature.top" -> {
                player.closeInventory();
                player.performCommand("dungeon top " + dungeon.getId());
            }
            case "door.entry" -> {
                if (rightClick) {
                    gui.openConfirm(player, dungeon, payload, "delete-door", payload, "doors");
                } else {
                    openDoorEditor(player, dungeon, dungeon.getDoors().get(payload));
                }
            }
            case "door.create" -> create(player, dungeon, "door-id", id -> {
                DungeonDoor door = new DungeonDoor(id);
                dungeon.getDoors().put(id, door);
                gui.save(dungeon);
                openDoorEditor(player, dungeon, door);
            }, () -> openDoors(player, dungeon, 0), dungeon.getDoors().keySet());
            case "door.field" -> handleDoorField(player, dungeon, dungeon.getDoors().get(holder.getTargetId()),
                    payload, rightClick);
            case "key.entry" -> {
                if (rightClick) {
                    gui.openConfirm(player, dungeon, payload, "delete-key", payload, "keys");
                } else {
                    openKeyEditor(player, dungeon, dungeon.getKeys().get(payload));
                }
            }
            case "key.create" -> create(player, dungeon, "key-id", id -> {
                DungeonKey key = new DungeonKey(id);
                dungeon.getKeys().put(id, key);
                gui.save(dungeon);
                openKeyEditor(player, dungeon, key);
            }, () -> openKeys(player, dungeon, 0), dungeon.getKeys().keySet());
            case "key.field" -> handleKeyField(player, dungeon, dungeon.getKeys().get(holder.getTargetId()),
                    payload, rightClick);
            case "checkpoint.entry" -> {
                if (rightClick) {
                    gui.openConfirm(player, dungeon, payload, "delete-checkpoint", payload, "checkpoints");
                } else {
                    openCheckpointEditor(player, dungeon, dungeon.getCheckpoints().get(payload));
                }
            }
            case "checkpoint.create" -> create(player, dungeon, "checkpoint-id", id -> {
                Checkpoint checkpoint = new Checkpoint(id);
                checkpoint.setLocation(player.getLocation().clone());
                dungeon.getCheckpoints().put(id, checkpoint);
                gui.save(dungeon);
                openCheckpointEditor(player, dungeon, checkpoint);
            }, () -> openCheckpoints(player, dungeon, 0), dungeon.getCheckpoints().keySet());
            case "checkpoint.field" -> handleCheckpointField(player, dungeon,
                    dungeon.getCheckpoints().get(holder.getTargetId()), payload, rightClick);
            default -> {
                // Accion no reconocida: no se hace nada.
            }
        }
    }

    private void create(Player player, Dungeon dungeon, String promptKey,
                        java.util.function.Consumer<String> created, Runnable cancel,
                        java.util.Set<String> existing) {
        gui.startPrompt(player, promptKey, Map.of("dungeon", dungeon.getId()), cancel, input -> {
            String id = input.toLowerCase(Locale.ROOT);
            if (!GuiManager.validId(id)) {
                gui.msg(player, "invalid-id", Map.of("value", input));
                cancel.run();
                return;
            }
            if (existing.contains(id)) {
                gui.msg(player, "exists", Map.of("dungeon", id));
                cancel.run();
                return;
            }
            gui.msg(player, "created", Map.of("dungeon", id));
            created.accept(id);
        });
    }

    boolean confirmDelete(Player player, Dungeon dungeon, String action, String target) {
        switch (action) {
            case "delete-door" -> {
                DungeonDoor door = dungeon.getDoors().remove(target);
                if (door != null && door.isConfigured()) {
                    door.close();
                }
                gui.save(dungeon);
                gui.msg(player, "deleted", Map.of("dungeon", target));
                openDoors(player, dungeon, 0);
            }
            case "delete-key" -> {
                dungeon.getKeys().remove(target);
                gui.save(dungeon);
                gui.msg(player, "deleted", Map.of("dungeon", target));
                openKeys(player, dungeon, 0);
            }
            case "delete-checkpoint" -> {
                dungeon.getCheckpoints().remove(target);
                gui.save(dungeon);
                gui.msg(player, "deleted", Map.of("dungeon", target));
                openCheckpoints(player, dungeon, 0);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    static String plain(String miniMessage) {
        return Text.plain(Text.parse(miniMessage));
    }
}
