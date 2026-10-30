package org.qs.qsdungeons.dungeon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.dungeon.instance.DungeonInstance;
import org.qs.qsdungeons.stats.StatsManager;
import org.qs.qsdungeons.storage.LocationSerializer;
import org.qs.qsdungeons.util.EconomyHook;
import org.qs.qsdungeons.util.Tags;
import org.qs.qsdungeons.util.Text;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Carga, guarda y controla las dungeons y sus partidas activas.
 */
public final class DungeonManager {

    private final QsDungeons plugin;
    private final Map<String, Dungeon> dungeons = new LinkedHashMap<>();
    private final Map<String, DungeonInstance> instances = new LinkedHashMap<>();
    private final Map<UUID, String> playerInstances = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<String, Long> autoEnterAttempts = new HashMap<>();
    private final Map<UUID, Long> autoEnterSuppressed = new HashMap<>();
    private final Map<UUID, Location> respawnExit = new HashMap<>();
    private final Map<UUID, Location> respawnInside = new HashMap<>();
    private File cooldownFile;

    public DungeonManager(QsDungeons plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------------
    // Carga y guardado
    // ---------------------------------------------------------------------

    public void load() {
        dungeons.clear();
        dungeons.putAll(plugin.getRepository().loadAll());
        loadCooldowns();
        if (dungeons.isEmpty() && plugin.getConfigManager().isGenerateExample()) {
            try {
                org.qs.qsdungeons.storage.ExampleDungeon.create(plugin.getRepository());
                dungeons.putAll(plugin.getRepository().loadAll());
                plugin.getLogger().info("Se creo una dungeon de ejemplo en plugins/QSDungeons/dungeons/ejemplo");
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "No se pudo crear la dungeon de ejemplo", exception);
            }
        }
        plugin.getLogger().info("Cargadas " + dungeons.size() + " dungeon(s).");
    }

    public void reload() {
        stopAllInstances();
        plugin.getConfigManager().reload();
        plugin.getMessages().reload();
        if (plugin.getGuiManager() != null) {
            plugin.getGuiManager().reload();
        }
        load();
    }

    public void saveAll() {
        plugin.getRepository().saveAll(dungeons.values());
    }

    public Dungeon getDungeon(String id) {
        return id == null ? null : dungeons.get(id.toLowerCase());
    }

    public Collection<Dungeon> getDungeons() {
        return dungeons.values();
    }

    public Collection<DungeonInstance> getInstances() {
        return instances.values();
    }

    public DungeonInstance getInstance(String dungeonId) {
        return dungeonId == null ? null : instances.get(dungeonId.toLowerCase());
    }

    public DungeonInstance getPlayerInstance(Player player) {
        if (player == null) {
            return null;
        }
        String id = playerInstances.get(player.getUniqueId());
        return id == null ? null : instances.get(id);
    }

    public DungeonInstance getPlayerInstance(UUID uuid) {
        String id = playerInstances.get(uuid);
        return id == null ? null : instances.get(id);
    }

    public void mapPlayer(UUID uuid, DungeonInstance instance) {
        playerInstances.put(uuid, instance.getDungeon().getId());
    }

    public void unmapPlayer(UUID uuid) {
        playerInstances.remove(uuid);
    }

    public boolean exists(String id) {
        return getDungeon(id) != null;
    }

    public Dungeon create(String id) {
        String normalized = id.toLowerCase();
        if (dungeons.containsKey(normalized)) {
            return null;
        }
        Dungeon dungeon = plugin.getRepository().create(normalized);
        if (dungeon != null) {
            dungeons.put(normalized, dungeon);
        }
        return dungeon;
    }

    public boolean delete(String id) {
        Dungeon dungeon = getDungeon(id);
        if (dungeon == null) {
            return false;
        }
        DungeonInstance instance = getInstance(id);
        if (instance != null) {
            instance.shutdown();
            instances.remove(dungeon.getId());
        }
        dungeons.remove(dungeon.getId());
        return plugin.getRepository().delete(dungeon.getId());
    }

    // ---------------------------------------------------------------------
    // Entrar y salir
    // ---------------------------------------------------------------------

    public boolean join(Player player, Dungeon dungeon) {
        if (dungeon == null) {
            return false;
        }
        if (!dungeon.isEnabled()) {
            plugin.getMessages().send(player, "join.not-running", Map.of("dungeon", dungeon.getId()));
            return false;
        }
        if (!dungeon.isReady()) {
            plugin.getMessages().send(player, "join.not-ready", Map.of("dungeon", dungeon.getId()));
            return false;
        }
        DungeonInstance current = getPlayerInstance(player);
        if (current != null) {
            plugin.getMessages().send(player, "join.already", Map.of("dungeon", current.getDungeon().getId()));
            return false;
        }
        if (player.hasPermission("qsdungeons.cooldown.bypass") || player.isOp()) {
            // bypass cooldown
        } else {
            long cooldown = getCooldownRemaining(player, dungeon);
            if (cooldown > 0) {
                plugin.getMessages().send(player, "join.cooldown", Map.of(
                        "dungeon", dungeon.getId(),
                        "time", plugin.getPlaceholderService().formatDuration(cooldown)));
                return false;
            }
        }
        if (!dungeon.getSettings().getEntryPermission().isBlank()
                && !player.hasPermission(dungeon.getSettings().getEntryPermission())) {
            plugin.getMessages().send(player, "join.no-permission", Map.of("dungeon", dungeon.getId()));
            return false;
        }

        DungeonInstance instance = getInstance(dungeon.getId());
        boolean rejoin = instance != null && instance.getLeftPlayers().contains(player.getUniqueId());
        if (!rejoin && !checkRequirements(player, dungeon)) {
            return false;
        }
        if (instance == null || (!instance.isRunning() && !instance.isWaiting())) {
            if (!dungeon.getSettings().isAutoStart()) {
                plugin.getMessages().send(player, "join.not-running", Map.of("dungeon", dungeon.getId()));
                return false;
            }
            DungeonSettings settings = dungeon.getSettings();
            boolean lobby = settings.getLobbySeconds() > 0 || settings.getMinPlayers() > 1;
            if (!(lobby ? openLobby(dungeon) : start(dungeon, null))) {
                plugin.getMessages().send(player, "join.not-running", Map.of("dungeon", dungeon.getId()));
                return false;
            }
            instance = getInstance(dungeon.getId());
        }
        if (instance == null) {
            return false;
        }
        if (!instance.addPlayer(player)) {
            return false;
        }
        if (!rejoin) {
            chargeRequirements(player, dungeon);
        }
        return true;
    }

    /**
     * Teletransporta al punto de tp de la dungeon. Si no tiene uno, entra
     * directamente.
     */
    public boolean teleport(Player player, Dungeon dungeon) {
        if (player == null || dungeon == null) {
            return false;
        }
        if (dungeon.getWarp() == null || dungeon.getWarp().getWorld() == null) {
            return join(player, dungeon);
        }
        if (getPlayerInstance(player) != null) {
            plugin.getMessages().send(player, "join.already", Map.of("dungeon", dungeon.getId()));
            return false;
        }
        player.teleport(dungeon.getWarp());
        plugin.getMessages().send(player, "tp.success", Map.of("dungeon", dungeon.getId()));
        return true;
    }

    // ---------------------------------------------------------------------
    // Requisitos de entrada
    // ---------------------------------------------------------------------

    private boolean bypassRequirements(Player player) {
        return player.isOp() || player.hasPermission("qsdungeons.requirements.bypass");
    }

    public boolean checkRequirements(Player player, Dungeon dungeon) {
        if (bypassRequirements(player)) {
            return true;
        }
        DungeonSettings settings = dungeon.getSettings();
        if (settings.getRequiredLevel() > 0 && player.getLevel() < settings.getRequiredLevel()) {
            plugin.getMessages().send(player, "join.req-level", Map.of(
                    "level", String.valueOf(settings.getRequiredLevel()), "dungeon", dungeon.getId()));
            return false;
        }
        for (String required : settings.getRequiredDungeons()) {
            if (!plugin.getStatsManager().hasCompleted(player.getUniqueId(), required)) {
                Dungeon other = getDungeon(required);
                plugin.getMessages().send(player, "join.req-completed", Map.of(
                        "required", other == null ? required : Text.plain(Text.parse(other.getDisplayName())),
                        "dungeon", dungeon.getId()));
                return false;
            }
        }
        if (settings.getEntryCost() > 0) {
            if (!plugin.getEconomy().isAvailable()) {
                plugin.getLogger().warning("La dungeon '" + dungeon.getId()
                        + "' cobra entrada pero Vault/economia no esta disponible; no se cobrara.");
            } else if (!plugin.getEconomy().has(player, settings.getEntryCost())) {
                plugin.getMessages().send(player, "join.req-money", Map.of(
                        "amount", EconomyHook.format(settings.getEntryCost()), "dungeon", dungeon.getId()));
                return false;
            }
        }
        ItemStack item = settings.getEntryItem();
        if (item != null && !player.getInventory().containsAtLeast(item, item.getAmount())) {
            plugin.getMessages().send(player, "join.req-item", Map.of(
                    "item", itemName(item), "amount", String.valueOf(item.getAmount()),
                    "dungeon", dungeon.getId()));
            return false;
        }
        return true;
    }

    private void chargeRequirements(Player player, Dungeon dungeon) {
        if (bypassRequirements(player)) {
            return;
        }
        DungeonSettings settings = dungeon.getSettings();
        boolean paid = false;
        if (settings.getEntryCost() > 0 && plugin.getEconomy().isAvailable()) {
            paid = plugin.getEconomy().withdraw(player, settings.getEntryCost());
        }
        ItemStack item = settings.getEntryItem();
        if (item != null) {
            player.getInventory().removeItem(item.clone());
            paid = true;
        }
        if (paid) {
            plugin.getMessages().send(player, "join.paid", Map.of("dungeon", dungeon.getId()));
        }
    }

    public static String itemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().displayName() != null) {
            return Text.plain(item.getItemMeta().displayName());
        }
        String raw = item.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    /**
     * Abre una sala de espera para la dungeon.
     */
    public boolean openLobby(Dungeon dungeon) {
        if (dungeon == null || instances.containsKey(dungeon.getId()) || !dungeon.isReady()) {
            return false;
        }
        DungeonInstance instance = new DungeonInstance(plugin, dungeon);
        instances.put(dungeon.getId(), instance);
        instance.openLobby();
        return true;
    }

    public boolean start(Dungeon dungeon, CommandSender sender) {
        if (dungeon == null) {
            return false;
        }
        DungeonInstance existing = instances.get(dungeon.getId());
        if (existing != null && existing.isWaiting()) {
            existing.start();
            if (sender != null) {
                plugin.getMessages().send(sender, "dungeon.started", Map.of("dungeon", dungeon.getId()));
            }
            return true;
        }
        if (instances.containsKey(dungeon.getId())) {
            if (sender != null) {
                plugin.getMessages().send(sender, "dungeon.already-running",
                        Map.of("dungeon", dungeon.getId()));
            }
            return false;
        }
        if (!dungeon.isReady()) {
            if (sender != null) {
                plugin.getMessages().send(sender, "dungeon.cannot-start",
                        Map.of("dungeon", dungeon.getId(), "reason", "region/entrada sin configurar"));
            }
            return false;
        }
        DungeonInstance instance = new DungeonInstance(plugin, dungeon);
        instances.put(dungeon.getId(), instance);
        try {
            instance.start();
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Error iniciando la dungeon " + dungeon.getId(), exception);
            instances.remove(dungeon.getId());
            instance.shutdown();
            if (sender != null) {
                plugin.getMessages().send(sender, "dungeon.cannot-start", Map.of(
                        "dungeon", dungeon.getId(),
                        "reason", exception.getMessage() == null ? "error interno" : exception.getMessage()));
            }
            return false;
        }
        if (sender != null) {
            plugin.getMessages().send(sender, "dungeon.started", Map.of("dungeon", dungeon.getId()));
        }
        return true;
    }

    public void leave(Player player, boolean teleport) {
        DungeonInstance instance = getPlayerInstance(player);
        if (instance == null) {
            plugin.getMessages().send(player, "leave.not-in");
            return;
        }
        suppressAutoEnter(player, 3000L);
        instance.removePlayer(player, teleport);
    }

    /**
     * Detiene una partida y la elimina del registro. No muestra mensajes de completado.
     */
    public void stopInstance(DungeonInstance instance, boolean success) {
        if (instance == null) {
            return;
        }
        Dungeon dungeon = instance.getDungeon();
        DungeonState state = instance.getState();
        List<UUID> participants = new ArrayList<>(instance.getPlayers());
        instances.remove(dungeon.getId());
        if (state == DungeonState.COMPLETED) {
            for (UUID uuid : participants) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    continue;
                }
                if (player.hasPermission("qsdungeons.cooldown.bypass") || player.isOp()) {
                    continue;
                }
                if (instance.getSettings().getCooldownSeconds() > 0) {
                    setCooldown(uuid, dungeon);
                }
            }
            saveCooldowns();
        }
        instance.shutdown();
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Instancia de '" + dungeon.getId() + "' detenida (success=" + success + ").");
        }
    }

    public void stop(Dungeon dungeon, CommandSender sender, boolean success) {
        DungeonInstance instance = dungeon == null ? null : getInstance(dungeon.getId());
        if (instance == null) {
            if (sender != null) {
                plugin.getMessages().send(sender, "dungeon.not-running",
                        Map.of("dungeon", dungeon == null ? "?" : dungeon.getId()));
            }
            return;
        }
        if (success) {
            instance.complete();
        } else {
            instance.fail(plugin.getMessages().raw("dungeon.reason-admin"));
        }
        if (sender != null) {
            plugin.getMessages().send(sender, "dungeon.stopped",
                    Map.of("dungeon", dungeon.getId()));
        }
    }

    public void stopNow(Dungeon dungeon, String reason) {
        DungeonInstance instance = dungeon == null ? null : getInstance(dungeon.getId());
        if (instance != null) {
            instance.fail(reason);
            stopInstance(instance, false);
        }
    }

    public void reset(Dungeon dungeon) {
        DungeonInstance instance = dungeon == null ? null : getInstance(dungeon.getId());
        if (instance != null) {
            stopInstance(instance, false);
        }
    }

    /**
     * Activa o desactiva una dungeon. Al desactivarla se detiene la partida en
     * curso al instante y se eliminan sus mobs y jefes.
     */
    public void setEnabled(Dungeon dungeon, boolean enabled) {
        if (dungeon == null) {
            return;
        }
        dungeon.setEnabled(enabled);
        if (!enabled) {
            stopNow(dungeon, plugin.getMessages().raw("dungeon.reason-admin"));
        }
    }

    /**
     * Elimina una entidad de dungeon que ya no pertenece a ninguna partida
     * activa (mobs que quedaron en chunks descargados, reinicios, etc.).
     */
    public boolean cleanupOrphan(Entity entity) {
        if (entity == null || entity instanceof Player
                || !Tags.isDungeonEntity(entity) || Tags.isTestEntity(entity)) {
            return false;
        }
        DungeonInstance instance = getInstance(Tags.dungeonOf(entity));
        if (instance != null && instance.owns(entity.getUniqueId())) {
            return false;
        }
        entity.remove();
        return true;
    }

    public int cleanupLoadedOrphans() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (cleanupOrphan(entity)) {
                    removed++;
                }
            }
        }
        return removed;
    }

    public void stopAllInstances() {
        for (DungeonInstance instance : new ArrayList<>(instances.values())) {
            try {
                instance.shutdown();
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Error deteniendo instancia", exception);
            }
        }
        instances.clear();
        playerInstances.clear();
    }

    // ---------------------------------------------------------------------
    // Tick
    // ---------------------------------------------------------------------

    public void tick() {
        for (DungeonInstance instance : new ArrayList<>(instances.values())) {
            try {
                instance.tick();
            } catch (Exception exception) {
                plugin.getLogger().log(Level.SEVERE, "Error en tick de la dungeon "
                        + instance.getDungeon().getId(), exception);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Eventos de jugador
    // ---------------------------------------------------------------------

    public void handleMove(Player player, Location from, Location to) {
        if (player == null || to == null || to.getWorld() == null) {
            return;
        }
        if (from != null && from.getWorld() != null
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Long suppressed = autoEnterSuppressed.get(player.getUniqueId());
        if (suppressed != null) {
            if (System.currentTimeMillis() < suppressed) {
                return;
            }
            autoEnterSuppressed.remove(player.getUniqueId());
        }

        DungeonInstance current = getPlayerInstance(player);
        if (current != null) {
            Dungeon dungeon = current.getDungeon();
            if (dungeon.getSettings().isExitOnLeaveRegion()
                    && dungeon.getRegion() != null
                    && !dungeon.getRegion().contains(to)) {
                leave(player, true);
            }
            return;
        }

        for (Dungeon dungeon : dungeons.values()) {
            if (!dungeon.isEnabled()
                    || dungeon.getRegion() == null || !dungeon.isReady()) {
                continue;
            }
            if (!dungeon.getRegion().contains(to)) {
                continue;
            }
            if (!player.hasPermission("qsdungeons.cooldown.bypass") && !player.isOp()
                    && getCooldownRemaining(player, dungeon) > 0) {
                ejectCooldownPlayer(player, dungeon);
                return;
            }
            if (!dungeon.getSettings().isAutoEnter()) {
                continue;
            }
            String key = player.getUniqueId() + ":" + dungeon.getId();
            long now = System.currentTimeMillis();
            Long last = autoEnterAttempts.get(key);
            if (last != null && now - last < 4000L) {
                return;
            }
            autoEnterAttempts.put(key, now);
            join(player, dungeon);
            return;
        }
    }

    private void ejectCooldownPlayer(Player player, Dungeon dungeon) {
        long cooldown = getCooldownRemaining(player, dungeon);
        plugin.getMessages().send(player, "join.cooldown", Map.of(
                "dungeon", dungeon.getId(),
                "time", plugin.getPlaceholderService().formatDuration(cooldown)));
        ConfigurationSection section = plugin.getConfig()
                .getConfigurationSection("cooldown-protection");
        if (section == null || !section.getBoolean("enabled", false)) {
            return;
        }
        Location destination = LocationSerializer.read(section, "location");
        if (destination == null || destination.getWorld() == null) {
            return;
        }
        suppressAutoEnter(player, 3000L);
        player.teleport(destination);
    }

    public void handleQuit(Player player) {
        DungeonInstance instance = getPlayerInstance(player);
        if (instance != null) {
            instance.removePlayer(player, false);
        }
        playerInstances.remove(player.getUniqueId());
        autoEnterSuppressed.remove(player.getUniqueId());
        respawnExit.remove(player.getUniqueId());
        respawnInside.remove(player.getUniqueId());
    }

    public void handleDeath(Player player) {
        DungeonInstance instance = getPlayerInstance(player);
        if (instance == null) {
            return;
        }
        if (instance.isRunning()) {
            plugin.getStatsManager().add(player.getUniqueId(), player.getName(),
                    instance.getDungeon().getId(), StatsManager.DEATHS, 1);
        }
        if (!instance.getSettings().isLeaveOnDeath()) {
            // Sigue en la partida: reaparece en el ultimo checkpoint o la entrada.
            Location inside = instance.getRespawnLocation();
            if (inside != null) {
                respawnInside.put(player.getUniqueId(), inside);
            }
            return;
        }
        Location exit = instance.getDungeon().getExit() != null
                ? instance.getDungeon().getExit()
                : instance.getDungeon().getEntry();
        if (exit != null) {
            respawnExit.put(player.getUniqueId(), exit);
        }
        instance.removePlayer(player, false);
    }

    /**
     * @return la ubicacion donde debe reaparecer el jugador, o null si no la
     *         controla la dungeon.
     */
    public Location handleRespawn(Player player) {
        Location inside = respawnInside.remove(player.getUniqueId());
        if (inside != null && getPlayerInstance(player) != null) {
            plugin.getMessages().send(player, "dungeon.respawn-checkpoint");
            return inside;
        }
        Location exit = respawnExit.remove(player.getUniqueId());
        if (exit == null) {
            return null;
        }
        suppressAutoEnter(player, 3000L);
        plugin.getMessages().send(player, "leave.respawn");
        return exit;
    }

    public void suppressAutoEnter(Player player, long millis) {
        if (player != null) {
            autoEnterSuppressed.put(player.getUniqueId(), System.currentTimeMillis() + millis);
        }
    }

    public Dungeon findDungeonAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        for (Dungeon dungeon : dungeons.values()) {
            if (dungeon.getRegion() != null && dungeon.getRegion().contains(location)) {
                return dungeon;
            }
        }
        return null;
    }

    public boolean isProtected(Location location) {
        Dungeon dungeon = findDungeonAt(location);
        return dungeon != null && dungeon.getSettings().isProtection();
    }

    // ---------------------------------------------------------------------
    // Comandos
    // ---------------------------------------------------------------------

    public void executeCommands(List<String> commands, Player player, DungeonInstance instance) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (String command : commands) {
            executeCommand(command, player, instance);
        }
    }

    public void executeCommandsForPlayers(List<String> commands, DungeonInstance instance) {
        if (commands == null || commands.isEmpty() || instance == null) {
            return;
        }
        for (String command : commands) {
            if (command == null || command.isBlank()) {
                continue;
            }
            if (command.startsWith("[player]") || command.contains("{player}")) {
                for (Player player : instance.getPlayersOnline()) {
                    executeCommand(command, player, instance);
                }
            } else {
                executeCommand(command, null, instance);
            }
        }
    }

    private void executeCommand(String command, Player player, DungeonInstance instance) {
        if (command == null || command.isBlank() || command.trim().startsWith("#")) {
            return;
        }
        String raw = command.trim();
        boolean asPlayer = raw.startsWith("[player]");
        if (asPlayer) {
            raw = raw.substring("[player]".length()).trim();
        } else if (raw.startsWith("[console]")) {
            raw = raw.substring("[console]".length()).trim();
        }
        if (raw.isBlank()) {
            return;
        }
        String resolved = plugin.getPlaceholderService().apply(raw, player, instance, null);
        if (asPlayer) {
            if (player != null) {
                player.performCommand(resolved);
            }
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }
    }

    // ---------------------------------------------------------------------
    // Cooldowns
    // ---------------------------------------------------------------------

    public long getCooldownRemaining(Player player, Dungeon dungeon) {
        if (player == null || dungeon == null) {
            return 0L;
        }
        Map<String, Long> map = cooldowns.get(player.getUniqueId());
        if (map == null) {
            return 0L;
        }
        Long until = map.get(dungeon.getId());
        if (until == null) {
            return 0L;
        }
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0) {
            map.remove(dungeon.getId());
            return 0L;
        }
        return (remaining + 999L) / 1000L;
    }

    public void setCooldown(UUID uuid, Dungeon dungeon) {
        if (uuid == null || dungeon == null || dungeon.getSettings().getCooldownSeconds() <= 0) {
            return;
        }
        long until = System.currentTimeMillis() + dungeon.getSettings().getCooldownSeconds() * 1000L;
        cooldowns.computeIfAbsent(uuid, ignored -> new HashMap<>()).put(dungeon.getId(), until);
    }

    private void loadCooldowns() {
        cooldowns.clear();
        cooldownFile = new File(plugin.getRepository().getDataFolder(), "cooldowns.yml");
        if (!cooldownFile.isFile()) {
            return;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(cooldownFile);
            ConfigurationSection section = yaml.getConfigurationSection("cooldowns");
            if (section == null) {
                return;
            }
            long now = System.currentTimeMillis();
            for (String rawUuid : section.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(rawUuid);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                ConfigurationSection dungeonSection = section.getConfigurationSection(rawUuid);
                if (dungeonSection == null) {
                    continue;
                }
                Map<String, Long> map = new HashMap<>();
                for (String dungeonId : dungeonSection.getKeys(false)) {
                    long until = dungeonSection.getLong(dungeonId);
                    if (until > now) {
                        map.put(dungeonId, until);
                    }
                }
                if (!map.isEmpty()) {
                    cooldowns.put(uuid, map);
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "No se pudieron cargar los cooldowns", exception);
        }
    }

    public void saveCooldowns() {
        if (cooldownFile == null) {
            cooldownFile = new File(plugin.getRepository().getDataFolder(), "cooldowns.yml");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<String, Long>> entry : cooldowns.entrySet()) {
            for (Map.Entry<String, Long> cooldown : entry.getValue().entrySet()) {
                if (cooldown.getValue() > now) {
                    yaml.set("cooldowns." + entry.getKey() + "." + cooldown.getKey(), cooldown.getValue());
                }
            }
        }
        try {
            yaml.save(cooldownFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "No se pudieron guardar los cooldowns", exception);
        }
    }

    public String describeInstance(DungeonInstance instance) {
        if (instance == null) {
            return plugin.getMessages().raw("placeholder.empty");
        }
        return Text.plain(Text.parse(instance.getDungeon().getDisplayName()));
    }
}
