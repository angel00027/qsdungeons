package org.qs.qsdungeons.dungeon.instance;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.boss.BossDefinition;
import org.qs.qsdungeons.dungeon.BossBarSettings;
import org.qs.qsdungeons.dungeon.Checkpoint;
import org.qs.qsdungeons.dungeon.ChestSpawn;
import org.qs.qsdungeons.dungeon.CompleteMode;
import org.qs.qsdungeons.dungeon.Dungeon;
import org.qs.qsdungeons.dungeon.DungeonDoor;
import org.qs.qsdungeons.dungeon.DungeonKey;
import org.qs.qsdungeons.dungeon.DungeonSettings;
import org.qs.qsdungeons.dungeon.DungeonState;
import org.qs.qsdungeons.dungeon.RewardBlock;
import org.qs.qsdungeons.loot.LootTable;
import org.qs.qsdungeons.spawn.SpawnPoint;
import org.qs.qsdungeons.stats.StatsManager;
import org.qs.qsdungeons.util.BlockKey;
import org.qs.qsdungeons.util.EconomyHook;
import org.qs.qsdungeons.util.Sounds;
import org.qs.qsdungeons.util.Tags;
import org.qs.qsdungeons.util.Text;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Partida activa de una dungeon.
 */
public final class DungeonInstance {

    private final QsDungeons plugin;
    private final Dungeon dungeon;
    private final DungeonSettings settings;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Set<UUID> leftPlayers = new HashSet<>();

    private final Map<String, SpawnerRuntime> spawners = new LinkedHashMap<>();
    private final Map<UUID, BossDefinition> bosses = new LinkedHashMap<>();
    private final Map<UUID, BossBarHandle> bossBars = new LinkedHashMap<>();
    private final Map<BlockKey, ChestSpawn> chestIndex = new HashMap<>();
    private final Map<BlockKey, Long> chestRefillAt = new HashMap<>();
    private final Map<BlockKey, RewardBlock> rewardIndex = new HashMap<>();
    private final Map<String, Long> rewardRestoreAt = new HashMap<>();
    private final Set<String> brokenRewards = new HashSet<>();
    // Ultima ubicacion conocida de cada mob/jefe. Sirve para distinguir un mob
    // muerto de uno que simplemente esta en un chunk descargado.
    private final Map<UUID, Location> lastKnown = new HashMap<>();

    // Oleadas: numeros de oleada configurados, en orden.
    private final List<Integer> waves = new ArrayList<>();
    private int waveIndex = -1;
    private long nextWaveAt;
    private boolean allWavesDone;

    private final Set<String> openedDoors = new HashSet<>();
    private final Set<String> unlockedChests = new HashSet<>();
    private final Set<String> reachedCheckpoints = new HashSet<>();
    private String lastCheckpoint;

    private final Map<UUID, Track> tracks = new HashMap<>();
    private final List<BukkitTask> cinematics = new ArrayList<>();
    private int pendingBossSpawns;

    private DungeonState state = DungeonState.IDLE;
    private long startedAt;
    private long endedAt;
    private long lobbyEndsAt;
    private int startPlayers = 1;
    private int kills;
    private int totalMobs;
    private int totalEnemies;
    private int requiredKills;
    private boolean automaticRequiredKills;
    private int initialBossCount;
    private int plannedBossCount;
    private boolean bossesActivated;
    private long pendingStopAt;
    private boolean pendingSuccess;
    private String failReason = "";
    private BossBarHandle dungeonBar;

    public DungeonInstance(QsDungeons plugin, Dungeon dungeon) {
        this.plugin = plugin;
        this.dungeon = dungeon;
        this.settings = dungeon.getSettings();
    }

    // ---------------------------------------------------------------------
    // Ciclo de vida
    // ---------------------------------------------------------------------

    /**
     * Abre la sala de espera: los jugadores entran pero la partida no empieza
     * hasta reunir min-players y terminar la cuenta atras.
     */
    public void openLobby() {
        state = DungeonState.WAITING;
        lobbyEndsAt = 0L;
        closeAllDoors();
    }

    public void start() {
        if (state == DungeonState.RUNNING) {
            return;
        }
        state = DungeonState.RUNNING;
        startedAt = System.currentTimeMillis();
        endedAt = 0L;
        lobbyEndsAt = 0L;
        pendingStopAt = 0L;
        pendingSuccess = false;
        failReason = "";
        kills = 0;
        automaticRequiredKills = settings.getRequiredKills() <= 0;
        brokenRewards.clear();
        rewardRestoreAt.clear();
        chestRefillAt.clear();
        chestIndex.clear();
        rewardIndex.clear();
        openedDoors.clear();
        unlockedChests.clear();
        reachedCheckpoints.clear();
        lastCheckpoint = null;
        pendingBossSpawns = 0;
        bossesActivated = !settings.isBossesAfterWaves();
        plannedBossCount = dungeon.getBosses().size();
        startPlayers = Math.max(1, getPlayersOnline().size());

        restoreAllRewards();
        restoreChestBlocks(true);
        closeAllDoors();

        for (RewardBlock reward : dungeon.getRewards().values()) {
            if (reward.getLocation() != null) {
                rewardIndex.put(BlockKey.of(reward.getLocation()), reward);
            }
        }
        for (ChestSpawn chest : dungeon.getChests().values()) {
            if (chest.getLocation() != null) {
                chestIndex.put(BlockKey.of(chest.getLocation()), chest);
            }
        }

        int mobCount = 0;
        TreeSet<Integer> waveNumbers = new TreeSet<>();
        for (SpawnPoint point : dungeon.getSpawns().values()) {
            SpawnerRuntime runtime = new SpawnerRuntime(point);
            runtime.setAmount(scaledAmount(point.getAmount()));
            spawners.put(point.getId(), runtime);
            if (point.getLocation() == null) {
                plugin.getLogger().warning("El spawn '" + point.getId() + "' de la dungeon '"
                        + dungeon.getId() + "' no tiene ubicacion.");
                continue;
            }
            waveNumbers.add(point.getWave());
            // Si hay reaparicion, la cantidad es el total de la oleada; si no,
            // solo pueden llegar a existir max-alive mobs.
            mobCount += point.getRespawnSeconds() > 0
                    ? runtime.getAmount()
                    : Math.min(runtime.getAmount(), point.getMaxAlive());
        }
        waves.clear();
        waves.addAll(waveNumbers);
        waveIndex = -1;
        nextWaveAt = 0L;
        allWavesDone = waves.isEmpty();
        totalMobs = mobCount;
        if (!waves.isEmpty()) {
            startWave(0);
        }

        if (bossesActivated) {
            spawnBosses();
        }
        initialBossCount = bossesActivated ? bosses.size() + pendingBossSpawns : plannedBossCount;
        totalEnemies = totalMobs + initialBossCount;
        requiredKills = automaticRequiredKills ? totalEnemies : settings.getRequiredKills();

        if (settings.getBossBar().isEnabled()) {
            dungeonBar = new BossBarHandle(settings.getBossBar(), 0.0F);
        }

        if (plugin.getConfigManager().fillChestsOnStart()) {
            for (ChestSpawn chest : dungeon.getChests().values()) {
                fillChest(chest, false);
            }
        }

        plugin.getDungeonManager().executeCommands(settings.getCommandsOnStart(), null, this);
        broadcast("dungeon.started", Map.of("dungeon", dungeon.getId()));
        playSound("start");
        plugin.getLogger().info("Dungeon '" + dungeon.getId() + "' iniciada con "
                + totalMobs + " mobs y " + initialBossCount + " jefes (" + totalEnemies + " enemigos).");
    }

    private void spawnBosses() {
        if (bossesActivated && (!bosses.isEmpty() || pendingBossSpawns > 0)) {
            return;
        }
        bossesActivated = true;
        int planned = 0;
        for (BossDefinition definition : dungeon.getBosses().values()) {
            if (definition.isCinematicEnabled() && definition.getCinematicDelay() > 0) {
                pendingBossSpawns++;
                planned++;
                playCinematic(definition);
            } else if (spawnBoss(definition)) {
                planned++;
            }
        }
        initialBossCount = planned;
        totalEnemies = totalMobs + initialBossCount;
        if (settings.getRequiredKills() <= 0) {
            requiredKills = totalEnemies;
        }
        if (settings.isBossesAfterWaves()) {
            broadcast("dungeon.bosses-unlocked", Map.of("amount", String.valueOf(planned)));
        }
    }

    private boolean spawnBoss(BossDefinition definition) {
        LivingEntity entity = plugin.getBossService().spawn(this, definition);
        if (entity == null) {
            return false;
        }
        applyScaling(entity);
        bosses.put(entity.getUniqueId(), definition);
        lastKnown.put(entity.getUniqueId(), entity.getLocation());
        BossBarSettings barSettings = definition.getBossBar();
        if (barSettings != null && barSettings.isEnabled()) {
            bossBars.put(entity.getUniqueId(), new BossBarHandle(barSettings, 1.0F));
        }
        if (definition.isCinematicEnabled() && definition.getCinematicDelay() <= 0) {
            showCinematicTitle(definition, entity.getLocation());
        }
        if (definition.isAnnounceSpawn()) {
            broadcast("dungeon.boss-spawn", Map.of("boss", bossName(definition, entity)));
            playSound("boss-spawn");
        }
        plugin.getDungeonManager().executeCommands(definition.getCommandsOnSpawn(), null, this);
        return true;
    }

    public boolean addPlayer(Player player) {
        if (state != DungeonState.RUNNING && state != DungeonState.WAITING) {
            plugin.getMessages().send(player, "join.not-running", Map.of("dungeon", dungeon.getId()));
            return false;
        }
        if (players.contains(player.getUniqueId())) {
            plugin.getMessages().send(player, "join.already", Map.of("dungeon", dungeon.getId()));
            return false;
        }
        if (!settings.isAllowRejoin() && leftPlayers.contains(player.getUniqueId())) {
            plugin.getMessages().send(player, "join.no-permission", Map.of("dungeon", dungeon.getId()));
            return false;
        }
        if (players.size() >= settings.getMaxPlayers()) {
            plugin.getMessages().send(player, "join.full", Map.of("dungeon", dungeon.getId()));
            return false;
        }
        if (!settings.getEntryPermission().isBlank()
                && !player.hasPermission(settings.getEntryPermission())) {
            plugin.getMessages().send(player, "join.no-permission", Map.of("dungeon", dungeon.getId()));
            return false;
        }
        players.add(player.getUniqueId());
        // Si la dungeon estaba esperando el reinicio por quedarse vacia, se
        // cancela: un jugador ha vuelto a entrar.
        pendingStopAt = 0L;
        pendingSuccess = false;
        plugin.getDungeonManager().mapPlayer(player.getUniqueId(), this);
        Location entry = dungeon.getEntry();
        if (entry == null && dungeon.getRegion() != null) {
            entry = dungeon.getRegion().getCenter();
        }
        if (state == DungeonState.RUNNING && lastCheckpoint != null) {
            entry = getRespawnLocation();
        }
        if (entry != null) {
            player.teleport(entry);
        }
        plugin.getMessages().send(player, "join.success", Map.of("dungeon", dungeon.getId()), this);
        plugin.getMessages().title(player, "join", Map.of(
                "dungeon", dungeon.getId(),
                "dungeon_name", dungeon.getDisplayName()), this);
        plugin.getConfigManager().playSound(player, "join");
        broadcastExcept(player, "join.broadcast", Map.of(
                "player", player.getName(),
                "dungeon", dungeon.getId()));
        if (state == DungeonState.WAITING) {
            plugin.getMessages().send(player, "lobby.joined", Map.of(
                    "players", String.valueOf(getPlayerCount()),
                    "min_players", String.valueOf(Math.max(1, settings.getMinPlayers()))), this);
        }
        checkCompletion();
        return true;
    }

    public void removePlayer(Player player, boolean teleport) {
        if (player == null || !players.remove(player.getUniqueId())) {
            return;
        }
        leftPlayers.add(player.getUniqueId());
        hideBars(player);
        stopTrack(player);
        removeKeys(player);
        plugin.getDungeonManager().unmapPlayer(player.getUniqueId());
        if (teleport) {
            Location exit = dungeon.getExit() != null ? dungeon.getExit() : dungeon.getEntry();
            if (exit != null) {
                player.teleport(exit);
            }
        }
        plugin.getMessages().send(player, "leave.success", Map.of("dungeon", dungeon.getId()));
        broadcast("leave.broadcast", Map.of(
                "player", player.getName(),
                "dungeon", dungeon.getId()));
        plugin.getConfigManager().playSound(player, "leave");
        if (players.isEmpty() && state == DungeonState.WAITING) {
            scheduleStop(1, false);
        } else if (players.isEmpty() && state == DungeonState.RUNNING && settings.isResetOnEmpty()) {
            scheduleStop(settings.getResetDelaySeconds(), false);
        }
    }

    public void tick() {
        long now = now();
        if (state == DungeonState.WAITING) {
            tickLobby(now);
        } else if (state == DungeonState.RUNNING) {
            boolean hasPlayers = !getPlayersOnline().isEmpty();
            tickSpawners(now);
            tickWaves(now, hasPlayers);
            // Sin jugadores dentro no se desbloquean los jefes: los mobs pueden
            // estar en chunks descargados y parecer eliminados.
            if (!bossesActivated && hasPlayers && wavesFinished()) {
                spawnBosses();
            }
            tickCheckpoints();
            tickChests(now);
            tickRewards(now);
            tickBars();
            if (settings.getTimeLimitSeconds() > 0
                    && !players.isEmpty()
                    && getElapsedSeconds() >= settings.getTimeLimitSeconds()) {
                fail(plugin.getMessages().raw("dungeon.reason-time"));
            } else {
                checkCompletion();
            }
        }
        if (state != DungeonState.IDLE) {
            tickAudio(now);
        }
        if (pendingStopAt > 0 && now >= pendingStopAt) {
            pendingStopAt = 0L;
            plugin.getDungeonManager().stopInstance(this, pendingSuccess);
        }
    }

    private void tickLobby(long now) {
        Collection<Player> online = getPlayersOnline();
        if (online.isEmpty()) {
            if (pendingStopAt == 0L) {
                scheduleStop(1, false);
            }
            return;
        }
        int min = Math.max(1, settings.getMinPlayers());
        if (online.size() < min) {
            if (lobbyEndsAt != 0L) {
                lobbyEndsAt = 0L;
                broadcast("lobby.cancelled", Map.of("min_players", String.valueOf(min)));
            }
            for (Player player : online) {
                plugin.getMessages().actionBar(player, "lobby.waiting", Map.of(
                        "players", String.valueOf(online.size()),
                        "min_players", String.valueOf(min)), this);
            }
            return;
        }
        if (lobbyEndsAt == 0L) {
            lobbyEndsAt = now + Math.max(0, settings.getLobbySeconds()) * 1000L;
            if (settings.getLobbySeconds() > 0) {
                broadcast("lobby.countdown-start", Map.of(
                        "time", plugin.getPlaceholderService().formatDuration(settings.getLobbySeconds())));
            }
        }
        long left = (lobbyEndsAt - now + 999L) / 1000L;
        if (left <= 0) {
            start();
            return;
        }
        for (Player player : online) {
            plugin.getMessages().actionBar(player, "lobby.countdown",
                    Map.of("time", String.valueOf(left)), this);
            if (left <= 5) {
                player.showTitle(Title.title(
                        Text.parse("<gold><bold>" + left + "</bold></gold>"),
                        Text.parse(plugin.getMessages().format(player,
                                plugin.getMessages().raw("lobby.countdown-subtitle"), Map.of(), this, null)),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(200))));
                Sounds.play(player, "block.note_block.hat", SoundCategory.MASTER, 1.0F, 1.2F);
            }
        }
    }

    public void scheduleStop(int seconds, boolean success) {
        pendingStopAt = now() + Math.max(1, seconds) * 1000L;
        pendingSuccess = success;
    }

    public void complete() {
        if (state != DungeonState.RUNNING) {
            return;
        }
        state = DungeonState.COMPLETED;
        endedAt = now();
        pendingSuccess = true;
        scheduleStop(settings.getEndDelaySeconds(), true);
        hideBars();
        broadcast("dungeon.completed", Map.of("dungeon", dungeon.getId(), "time",
                plugin.getPlaceholderService().formatDuration(settings.getEndDelaySeconds())));
        recordCompletionStats();
        for (Player player : getPlayersOnline()) {
            plugin.getMessages().title(player, "complete", Map.of(
                    "dungeon", dungeon.getId(),
                    "dungeon_name", dungeon.getDisplayName()), this);
            plugin.getConfigManager().playSound(player, "complete");
            plugin.getMessages().send(player, "dungeon.complete-loot",
                    Map.of("time", plugin.getPlaceholderService().formatDuration(settings.getEndDelaySeconds())), this);
            giveRewards(player);
        }
        plugin.getDungeonManager().executeCommandsForPlayers(settings.getCommandsOnComplete(), this);
        plugin.getDungeonManager().executeCommands(settings.getCommandsOnComplete(), null, this);
        plugin.getLogger().info("Dungeon '" + dungeon.getId() + "' completada.");
    }

    public void fail(String reason) {
        if (state != DungeonState.RUNNING && state != DungeonState.WAITING) {
            return;
        }
        boolean wasRunning = state == DungeonState.RUNNING;
        state = DungeonState.FAILED;
        endedAt = now();
        failReason = reason == null || reason.isBlank() ? "?" : reason;
        pendingSuccess = false;
        scheduleStop(settings.getEndDelaySeconds(), false);
        hideBars();
        broadcast("dungeon.failed", Map.of("reason", failReason, "time",
                plugin.getPlaceholderService().formatDuration(settings.getEndDelaySeconds())));
        for (Player player : getPlayersOnline()) {
            plugin.getMessages().title(player, "fail", Map.of("reason", failReason), this);
            plugin.getConfigManager().playSound(player, "fail");
            plugin.getMessages().send(player, "dungeon.fail-loot", Map.of("reason", failReason,
                    "time", plugin.getPlaceholderService().formatDuration(settings.getEndDelaySeconds())), this);
            if (wasRunning) {
                plugin.getStatsManager().add(player.getUniqueId(), player.getName(), dungeon.getId(),
                        StatsManager.FAILS, 1);
            }
        }
        plugin.getDungeonManager().executeCommandsForPlayers(settings.getCommandsOnFail(), this);
        plugin.getDungeonManager().executeCommands(settings.getCommandsOnFail(), null, this);
        plugin.getLogger().info("Dungeon '" + dungeon.getId() + "' fallida: " + failReason);
    }

    /**
     * Limpieza final. La llama DungeonManager cuando la partida se detiene.
     */
    public void shutdown() {
        state = DungeonState.IDLE;
        for (BukkitTask task : cinematics) {
            task.cancel();
        }
        cinematics.clear();
        pendingBossSpawns = 0;
        removeEntities();
        hideBars();
        bossBars.clear();
        dungeonBar = null;
        restoreAllRewards();
        restoreChestBlocks(false);
        closeAllDoors();
        chestRefillAt.clear();
        chestIndex.clear();
        rewardIndex.clear();

        for (Player player : getPlayersOnline()) {
            stopTrack(player);
            removeKeys(player);
            Location exit = dungeon.getExit() != null ? dungeon.getExit() : dungeon.getEntry();
            if (exit != null) {
                player.teleport(exit);
            }
            plugin.getDungeonManager().unmapPlayer(player.getUniqueId());
        }
        tracks.clear();
        players.clear();
        plugin.getDungeonManager().executeCommands(settings.getCommandsOnReset(), null, this);
    }

    /**
     * Elimina todos los mobs y jefes de esta partida. Los que estan en chunks
     * cargados se borran ya; los que estan en chunks descargados se borran
     * cuando el chunk vuelva a cargarse (DungeonManager#cleanupOrphan), porque
     * ya no pertenecen a ninguna partida.
     */
    private void removeEntities() {
        Set<UUID> tracked = new HashSet<>(bosses.keySet());
        for (SpawnerRuntime runtime : spawners.values()) {
            tracked.addAll(runtime.getAlive());
        }
        for (UUID uuid : tracked) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        bosses.clear();
        for (SpawnerRuntime runtime : spawners.values()) {
            runtime.getAlive().clear();
        }
        spawners.clear();
        lastKnown.clear();
        // Barrido de seguridad: cualquier entidad marcada con esta dungeon en
        // los chunks cargados de la region (por ejemplo mobs que el plugin
        // perdio de vista) tambien se elimina, igual que las llaves tiradas.
        if (dungeon.getRegion() != null && dungeon.getRegion().getBukkitWorld() != null) {
            org.bukkit.World world = dungeon.getRegion().getBukkitWorld();
            int minChunkX = dungeon.getRegion().getMinX() >> 4;
            int maxChunkX = dungeon.getRegion().getMaxX() >> 4;
            int minChunkZ = dungeon.getRegion().getMinZ() >> 4;
            int maxChunkZ = dungeon.getRegion().getMaxZ() >> 4;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    if (!world.isChunkLoaded(chunkX, chunkZ)) {
                        continue;
                    }
                    for (org.bukkit.entity.Entity entity : world.getChunkAt(chunkX, chunkZ).getEntities()) {
                        if (entity instanceof Item item && isOwnKey(item.getItemStack())) {
                            entity.remove();
                        } else if (!Tags.isTestEntity(entity)
                                && dungeon.getId().equals(Tags.dungeonOf(entity))) {
                            entity.remove();
                        }
                    }
                }
            }
        }
    }

    /**
     * Indica si la entidad es un mob o jefe vivo de esta partida.
     */
    public boolean owns(UUID uuid) {
        if (state == DungeonState.IDLE || uuid == null) {
            return false;
        }
        if (bosses.containsKey(uuid)) {
            return true;
        }
        for (SpawnerRuntime runtime : spawners.values()) {
            if (runtime.getAlive().contains(uuid)) {
                return true;
            }
        }
        return false;
    }

    private boolean isGone(UUID uuid) {
        org.bukkit.entity.Entity entity = Bukkit.getEntity(uuid);
        if (entity != null) {
            if (entity.isDead() || !entity.isValid()) {
                return true;
            }
            lastKnown.put(uuid, entity.getLocation());
            return false;
        }
        // No esta cargada: solo se considera eliminada si su chunk si lo esta.
        Location location = lastKnown.get(uuid);
        if (location == null || location.getWorld() == null) {
            return true;
        }
        return location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    // ---------------------------------------------------------------------
    // Oleadas
    // ---------------------------------------------------------------------

    private void startWave(int index) {
        waveIndex = index;
        nextWaveAt = 0L;
        int wave = waves.get(index);
        for (SpawnerRuntime runtime : spawners.values()) {
            if (runtime.getPoint().getWave() == wave && runtime.getPoint().getLocation() != null) {
                activate(runtime);
            }
        }
        if (waves.size() > 1) {
            Map<String, String> placeholders = Map.of(
                    "wave", String.valueOf(index + 1),
                    "waves", String.valueOf(waves.size()));
            broadcast("dungeon.wave-start", placeholders);
            for (Player player : getPlayersOnline()) {
                plugin.getMessages().title(player, "wave", placeholders, this);
                Sounds.play(player, "event.raid.horn", SoundCategory.HOSTILE, 0.8F, 1.0F);
            }
        }
    }

    private void activate(SpawnerRuntime runtime) {
        SpawnPoint point = runtime.getPoint();
        runtime.setActive(true);
        if (point.getSpawnIntervalSeconds() > 0) {
            runtime.setPendingSpawns(runtime.getAmount());
            runtime.setNextSpawnAt(now());
        } else {
            int toSpawn = Math.min(runtime.getAmount(), point.getMaxAlive());
            for (int i = 0; i < toSpawn; i++) {
                spawnMob(runtime);
            }
            runtime.setPendingSpawns(Math.max(0, runtime.getAmount() - toSpawn));
            runtime.setNextSpawnAt(now());
        }
        runtime.setInitialSpawned(true);
        if (runtime.getPendingSpawns() == 0 && point.getRespawnSeconds() > 0) {
            runtime.setNextSpawnAt(now() + point.getRespawnSeconds() * 1000L);
        }
    }

    private void tickWaves(long now, boolean hasPlayers) {
        if (allWavesDone || waveIndex < 0) {
            return;
        }
        if (nextWaveAt > 0L) {
            if (now >= nextWaveAt && hasPlayers) {
                startWave(waveIndex + 1);
            }
            return;
        }
        int wave = waves.get(waveIndex);
        if (!hasPlayers || !waveCleared(wave)) {
            return;
        }
        for (SpawnerRuntime runtime : spawners.values()) {
            if (runtime.getPoint().getWave() == wave) {
                runtime.setFinished(true);
            }
        }
        openDoors(DungeonDoor.Trigger.WAVE, String.valueOf(wave));
        if (waveIndex + 1 < waves.size()) {
            broadcast("dungeon.wave-cleared", Map.of(
                    "wave", String.valueOf(waveIndex + 1),
                    "waves", String.valueOf(waves.size()),
                    "time", String.valueOf(settings.getWaveDelaySeconds())));
            nextWaveAt = now + Math.max(0, settings.getWaveDelaySeconds()) * 1000L;
            if (nextWaveAt <= now) {
                startWave(waveIndex + 1);
            }
        } else {
            allWavesDone = true;
            if (waves.size() > 1) {
                broadcast("dungeon.waves-cleared", Map.of("waves", String.valueOf(waves.size())));
            }
        }
    }

    private boolean waveCleared(int wave) {
        for (SpawnerRuntime runtime : spawners.values()) {
            if (runtime.getPoint().getWave() != wave || runtime.getPoint().getLocation() == null) {
                continue;
            }
            if (!runtime.isActive() || runtime.getPendingSpawns() > 0 || !runtime.getAlive().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean wavesFinished() {
        return allWavesDone;
    }

    // ---------------------------------------------------------------------
    // Spawns y muertes
    // ---------------------------------------------------------------------

    private void tickSpawners(long now) {
        boolean hasPlayers = !getPlayersOnline().isEmpty();
        for (SpawnerRuntime runtime : spawners.values()) {
            SpawnPoint point = runtime.getPoint();

            // Elimina de la lista de vivos las entidades que ya no existen.
            runtime.getAlive().removeIf(uuid -> {
                if (isGone(uuid)) {
                    lastKnown.remove(uuid);
                    return true;
                }
                return false;
            });

            if (point.getLocation() == null || !runtime.isActive() || runtime.isFinished()) {
                continue;
            }
            // Los mobs de una dungeon no se generan si no hay nadie dentro.
            if (!hasPlayers) {
                continue;
            }

            // Oleada inicial repartida en el tiempo (spawn-interval-seconds).
            if (runtime.getPendingSpawns() > 0) {
                if (runtime.getAlive().size() >= Math.max(1, point.getMaxAlive())) {
                    continue;
                }
                if (now < runtime.getNextSpawnAt()) {
                    continue;
                }
                if (spawnMob(runtime) != null) {
                    runtime.setPendingSpawns(runtime.getPendingSpawns() - 1);
                    if (runtime.getPendingSpawns() > 0) {
                        int interval = Math.max(1, point.getSpawnIntervalSeconds());
                        runtime.setNextSpawnAt(now + interval * 1000L);
                    } else {
                        runtime.setNextSpawnAt(now + Math.max(1, point.getRespawnSeconds()) * 1000L);
                    }
                } else {
                    runtime.setNextSpawnAt(now + 5000L);
                }
                continue;
            }

            if (point.getRespawnSeconds() <= 0) {
                continue;
            }
            if (runtime.getAlive().size() >= Math.max(1, point.getMaxAlive())) {
                continue;
            }
            if (now < runtime.getNextSpawnAt()) {
                continue;
            }
            if (spawnMob(runtime) != null) {
                // Este spawn ocurre después de terminar la oleada inicial y
                // representa una reaparición real. Debe reflejarse también en
                // la meta y en el total mostrado por la bossbar.
                totalMobs++;
                totalEnemies++;
                if (automaticRequiredKills) {
                    requiredKills++;
                }
                runtime.setNextSpawnAt(now + point.getRespawnSeconds() * 1000L);
            } else {
                runtime.setNextSpawnAt(now + 5000L);
            }
        }
    }

    private LivingEntity spawnMob(SpawnerRuntime runtime) {
        SpawnPoint point = runtime.getPoint();
        LivingEntity entity = plugin.getMobSpawner().spawn(this, point);
        if (entity == null) {
            return null;
        }
        applyScaling(entity);
        runtime.getAlive().add(entity.getUniqueId());
        lastKnown.put(entity.getUniqueId(), entity.getLocation());
        if (!point.getCommandsOnSpawn().isEmpty()) {
            plugin.getDungeonManager().executeCommands(point.getCommandsOnSpawn(), null, this);
        }
        return entity;
    }

    private int scaledAmount(int base) {
        double factor = 1.0D + settings.getScalingMobs() * (startPlayers - 1);
        return Math.max(1, (int) Math.round(base * factor));
    }

    /**
     * Aumenta vida y dano segun los jugadores dentro (scaling.*-per-player).
     */
    private void applyScaling(LivingEntity entity) {
        int count = Math.max(1, getPlayersOnline().size());
        if (count <= 1) {
            return;
        }
        double healthFactor = 1.0D + settings.getScalingHealth() * (count - 1);
        double damageFactor = 1.0D + settings.getScalingDamage() * (count - 1);
        if (healthFactor > 1.0D) {
            AttributeInstance health = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(Math.min(2048.0D, health.getBaseValue() * healthFactor));
                entity.setHealth(health.getValue());
            }
        }
        if (damageFactor > 1.0D) {
            AttributeInstance damage = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
            if (damage != null) {
                damage.setBaseValue(damage.getBaseValue() * damageFactor);
            }
        }
    }

    public void handleDeath(LivingEntity entity, Player killer) {
        if (entity == null || state != DungeonState.RUNNING) {
            return;
        }
        String bossId = Tags.bossOf(entity);
        if (bossId != null) {
            BossDefinition definition = bosses.remove(entity.getUniqueId());
            lastKnown.remove(entity.getUniqueId());
            BossBarHandle bar = bossBars.remove(entity.getUniqueId());
            if (bar != null) {
                bar.hideAll();
            }
            kills++;
            if (killer != null && hasPlayer(killer)) {
                plugin.getStatsManager().add(killer.getUniqueId(), killer.getName(), dungeon.getId(),
                        StatsManager.BOSS_KILLS, 1);
            }
            if (definition != null) {
                if (definition.isAnnounceDeath()) {
                    broadcast("dungeon.boss-death", Map.of("boss", bossName(definition, entity)));
                    playSound("boss-death");
                }
                LootTable table = dungeon.getLootTable(definition.getLootTable());
                if (table != null) {
                    int amount = plugin.getLootManager().giveOrDrop(killer, table, entity.getLocation(),
                            !plugin.getConfigManager().dropNaturally(), this);
                    if (amount > 0 && killer != null && plugin.getConfigManager().announceReward()) {
                        plugin.getMessages().send(killer, "loot.boss-drop",
                                Map.of("amount", String.valueOf(amount)), this);
                    }
                }
                plugin.getDungeonManager().executeCommands(definition.getCommandsOnDeath(), killer, this);
            }
            dropKeys("boss", bossId, killer, entity.getLocation());
            openDoors(DungeonDoor.Trigger.BOSS, bossId);
            checkCompletion();
            return;
        }

        String spawnId = Tags.spawnOf(entity);
        if (spawnId == null) {
            return;
        }
        SpawnerRuntime runtime = spawners.get(spawnId);
        if (runtime != null) {
            runtime.getAlive().remove(entity.getUniqueId());
            lastKnown.remove(entity.getUniqueId());
            if (runtime.getPoint().getRespawnSeconds() > 0) {
                runtime.setNextSpawnAt(now() + runtime.getPoint().getRespawnSeconds() * 1000L);
            }
        }
        kills++;
        if (killer != null && hasPlayer(killer)) {
            plugin.getStatsManager().add(killer.getUniqueId(), killer.getName(), dungeon.getId(),
                    StatsManager.KILLS, 1);
        }
        SpawnPoint point = dungeon.getSpawn(spawnId);
        if (point != null) {
            LootTable table = dungeon.getLootTable(point.getLootTable());
            if (table != null) {
                plugin.getLootManager().giveOrDrop(killer, table, entity.getLocation(),
                        !plugin.getConfigManager().dropNaturally(), this);
            }
        }
        dropKeys("spawn", spawnId, killer, entity.getLocation());
        checkCompletion();
    }

    private void checkCompletion() {
        if (state != DungeonState.RUNNING || players.isEmpty()) {
            return;
        }
        if (!bossesActivated) {
            return;
        }
        boolean bossesDone = bosses.isEmpty() && pendingBossSpawns == 0;
        boolean killsDone = requiredKills > 0 && kills >= requiredKills;
        CompleteMode mode = settings.getCompleteOn();
        boolean complete = switch (mode) {
            case BOSSES -> initialBossCount > 0 ? bossesDone : killsDone;
            case KILLS -> killsDone;
            case BOSSES_AND_KILLS -> bossesDone && killsDone;
            case MANUAL -> false;
        };
        if (complete) {
            complete();
        }
    }

    // ---------------------------------------------------------------------
    // Cinematica y musica
    // ---------------------------------------------------------------------

    private Location bossLocation(BossDefinition definition) {
        Location location = definition.getLocation();
        if (location == null && dungeon.getRegion() != null) {
            location = dungeon.getRegion().getCenter();
        }
        return location;
    }

    private List<Player> playersNear(Location location, double radius) {
        List<Player> near = new ArrayList<>();
        for (Player player : getPlayersOnline()) {
            if (location == null || radius <= 0
                    || (player.getWorld().equals(location.getWorld())
                    && player.getLocation().distanceSquared(location) <= radius * radius)) {
                near.add(player);
            }
        }
        return near;
    }

    private void showCinematicTitle(BossDefinition definition, Location location) {
        String name = definition.getDisplayName() == null || definition.getDisplayName().isBlank()
                ? definition.getEntity() : definition.getDisplayName();
        Map<String, String> placeholders = Map.of("boss", name);
        for (Player player : playersNear(location, definition.getCinematicRadius())) {
            player.showTitle(Title.title(
                    Text.parse(plugin.getMessages().format(player, definition.getCinematicTitle(), placeholders, this, null)),
                    Text.parse(plugin.getMessages().format(player, definition.getCinematicSubtitle(), placeholders, this, null)),
                    Title.Times.times(Duration.ofMillis(300),
                            Duration.ofMillis(Math.max(1500L, definition.getCinematicDelay() * 1000L)),
                            Duration.ofMillis(600))));
            Sounds.play(player, definition.getCinematicSound(), SoundCategory.HOSTILE, 1.0F, 1.0F);
        }
    }

    /**
     * Muestra el titulo y particulas a los jugadores cercanos y hace aparecer
     * al jefe al terminar la pausa.
     */
    private void playCinematic(BossDefinition definition) {
        Location location = bossLocation(definition);
        if (location == null || location.getWorld() == null) {
            pendingBossSpawns--;
            return;
        }
        showCinematicTitle(definition, location);
        Particle particle = parseParticle(definition.getCinematicParticle());
        int totalTicks = Math.max(1, definition.getCinematicDelay() * 20);
        BukkitTask task = new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                if (state != DungeonState.RUNNING) {
                    cancel();
                    return;
                }
                if (ticks >= totalTicks) {
                    cancel();
                    pendingBossSpawns = Math.max(0, pendingBossSpawns - 1);
                    if (!spawnBoss(definition)) {
                        initialBossCount = Math.max(0, initialBossCount - 1);
                        totalEnemies = totalMobs + initialBossCount;
                    }
                    checkCompletion();
                    return;
                }
                double progress = ticks / (double) totalTicks;
                for (int arm = 0; arm < 3; arm++) {
                    double angle = ticks * 0.35D + arm * (Math.PI * 2.0D / 3.0D);
                    double radius = 2.0D * (1.0D - progress) + 0.3D;
                    Location point = location.clone().add(Math.cos(angle) * radius,
                            progress * 3.0D, Math.sin(angle) * radius);
                    location.getWorld().spawnParticle(particle, point, 2, 0.02D, 0.02D, 0.02D, 0.0D);
                }
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        cinematics.add(task);
    }

    private static Particle parseParticle(String raw) {
        try {
            Particle particle = Particle.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            if (particle.getDataType() == Void.class) {
                return particle;
            }
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // Particula invalida o que necesita datos extra.
        }
        return Particle.SOUL_FIRE_FLAME;
    }

    /**
     * Musica: cerca de un jefe con music.sound suena la del jefe; si no, la
     * ambiente de la dungeon. Cada pista se repite cada interval-seconds.
     */
    private void tickAudio(long now) {
        for (Player player : getPlayersOnline()) {
            String id = "";
            String sound = "";
            int interval = 60;
            float volume = 1.0F;
            float pitch = 1.0F;
            for (Map.Entry<UUID, BossDefinition> entry : bosses.entrySet()) {
                BossDefinition definition = entry.getValue();
                if (definition.getMusicSound().isBlank()) {
                    continue;
                }
                org.bukkit.entity.Entity boss = Bukkit.getEntity(entry.getKey());
                if (boss != null && boss.getWorld().equals(player.getWorld())
                        && boss.getLocation().distanceSquared(player.getLocation())
                        <= definition.getMusicRadius() * definition.getMusicRadius()) {
                    id = "boss:" + definition.getId();
                    sound = definition.getMusicSound();
                    interval = definition.getMusicInterval();
                    break;
                }
            }
            if (id.isEmpty() && !settings.getAmbientSound().isBlank() && state == DungeonState.RUNNING) {
                id = "ambient";
                sound = settings.getAmbientSound();
                interval = settings.getAmbientInterval();
                volume = (float) settings.getAmbientVolume();
                pitch = (float) settings.getAmbientPitch();
            }
            Track current = tracks.get(player.getUniqueId());
            if (current != null && !current.id().equals(id)) {
                Sounds.stop(player, current.sound(), SoundCategory.RECORDS);
                tracks.remove(player.getUniqueId());
                current = null;
            }
            if (id.isEmpty()) {
                continue;
            }
            if (current == null || now >= current.nextAt()) {
                Sounds.play(player, sound, SoundCategory.RECORDS, volume, pitch);
                tracks.put(player.getUniqueId(), new Track(id, sound, now + Math.max(1, interval) * 1000L));
            }
        }
    }

    private void stopTrack(Player player) {
        Track track = tracks.remove(player.getUniqueId());
        if (track != null) {
            Sounds.stop(player, track.sound(), SoundCategory.RECORDS);
        }
    }

    private record Track(String id, String sound, long nextAt) {
    }

    // ---------------------------------------------------------------------
    // Puertas, llaves y checkpoints
    // ---------------------------------------------------------------------

    private void closeAllDoors() {
        for (DungeonDoor door : dungeon.getDoors().values()) {
            if (door.isConfigured()) {
                door.close();
            }
        }
        openedDoors.clear();
    }

    private void openDoors(DungeonDoor.Trigger trigger, String value) {
        for (DungeonDoor door : dungeon.getDoors().values()) {
            if (door.getTrigger() == trigger && door.getTriggerValue().equalsIgnoreCase(value)) {
                openDoor(door);
            }
        }
    }

    public boolean openDoor(DungeonDoor door) {
        if (door == null || !door.isConfigured() || !openedDoors.add(door.getId())) {
            return false;
        }
        door.open();
        Location center = door.getCenter();
        for (Player player : getPlayersOnline()) {
            Sounds.play(player, center == null ? player.getLocation() : center, door.getOpenSound(),
                    SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
        broadcast("dungeon.door-open", Map.of("door", door.getId()));
        return true;
    }

    public boolean isDoorOpen(String doorId) {
        return openedDoors.contains(doorId);
    }

    private void dropKeys(String type, String sourceId, Player killer, Location location) {
        for (DungeonKey key : dungeon.getKeys().values()) {
            if (!key.dropsFrom(type, sourceId)
                    || ThreadLocalRandom.current().nextDouble() > key.getDropChance()) {
                continue;
            }
            ItemStack item = key.createItem(dungeon);
            if (killer != null && hasPlayer(killer)) {
                for (ItemStack leftover : killer.getInventory().addItem(item).values()) {
                    killer.getWorld().dropItemNaturally(killer.getLocation(), leftover);
                }
            } else if (location != null && location.getWorld() != null) {
                location.getWorld().dropItemNaturally(location, item);
            }
            broadcast("dungeon.key-drop", Map.of(
                    "key", Text.plain(Text.parse(key.getName())),
                    "player", killer == null ? "?" : killer.getName()));
        }
    }

    private boolean isOwnKey(ItemStack stack) {
        String tag = DungeonKey.tagOf(stack);
        return tag != null && tag.startsWith(dungeon.getId() + ":");
    }

    private void removeKeys(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isOwnKey(contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    /**
     * El jugador usa una llave sobre un bloque: abre la puerta KEY que lo
     * contiene o desbloquea el cofre que la necesita.
     */
    public boolean tryUseKey(Player player, Block block, ItemStack hand) {
        String tag = DungeonKey.tagOf(hand);
        if (tag == null || !tag.startsWith(dungeon.getId() + ":")) {
            return false;
        }
        String keyId = tag.substring(dungeon.getId().length() + 1);
        for (DungeonDoor door : dungeon.getDoors().values()) {
            if (door.getTrigger() == DungeonDoor.Trigger.KEY
                    && door.getTriggerValue().equalsIgnoreCase(keyId)
                    && !openedDoors.contains(door.getId())
                    && door.contains(block.getLocation())) {
                hand.setAmount(hand.getAmount() - 1);
                openDoor(door);
                plugin.getMessages().send(player, "dungeon.key-used", Map.of("key", keyId), this);
                return true;
            }
        }
        ChestSpawn chest = chestIndex.get(BlockKey.of(block));
        if (chest != null && chest.getRequiredKey().equalsIgnoreCase(keyId)
                && !unlockedChests.contains(chest.getId())) {
            hand.setAmount(hand.getAmount() - 1);
            unlockedChests.add(chest.getId());
            Sounds.play(player, block.getLocation(), "block.chest.locked", SoundCategory.BLOCKS, 1.0F, 1.4F);
            plugin.getMessages().send(player, "dungeon.key-used", Map.of("key", keyId), this);
            return true;
        }
        return false;
    }

    /**
     * Devuelve el id de la llave que necesita el cofre, o null si esta libre.
     */
    public String lockedChestKey(Block block) {
        ChestSpawn chest = chestIndex.get(BlockKey.of(block));
        if (chest == null || chest.getRequiredKey().isBlank() || unlockedChests.contains(chest.getId())) {
            return null;
        }
        return chest.getRequiredKey();
    }

    private void tickCheckpoints() {
        if (dungeon.getCheckpoints().isEmpty()) {
            return;
        }
        for (Player player : getPlayersOnline()) {
            for (Checkpoint checkpoint : dungeon.getCheckpoints().values()) {
                if (reachedCheckpoints.contains(checkpoint.getId()) || !checkpoint.isInside(player.getLocation())) {
                    continue;
                }
                reachedCheckpoints.add(checkpoint.getId());
                lastCheckpoint = checkpoint.getId();
                broadcast("dungeon.checkpoint", Map.of("checkpoint", checkpoint.getId(), "player", player.getName()));
                for (Player member : getPlayersOnline()) {
                    Sounds.play(member, "block.beacon.activate", SoundCategory.MASTER, 0.8F, 1.4F);
                }
            }
        }
    }

    /**
     * Donde reaparece un jugador que muere: el ultimo checkpoint alcanzado
     * por el grupo o, si no hay ninguno, la entrada.
     */
    public Location getRespawnLocation() {
        if (lastCheckpoint != null) {
            Checkpoint checkpoint = dungeon.getCheckpoints().get(lastCheckpoint);
            if (checkpoint != null && checkpoint.getLocation() != null) {
                return checkpoint.getLocation();
            }
        }
        return dungeon.getEntry();
    }

    // ---------------------------------------------------------------------
    // Estadisticas y recompensas
    // ---------------------------------------------------------------------

    private void recordCompletionStats() {
        StatsManager stats = plugin.getStatsManager();
        long seconds = getElapsedSeconds();
        List<StatsManager.Entry> before = stats.top(dungeon.getId(), 1);
        long previousRecord = before.isEmpty() ? Long.MAX_VALUE : before.get(0).seconds();
        for (Player player : getPlayersOnline()) {
            boolean personal = stats.recordCompletion(player.getUniqueId(), player.getName(),
                    dungeon.getId(), seconds);
            if (personal) {
                plugin.getMessages().send(player, "dungeon.record-personal",
                        Map.of("time", plugin.getPlaceholderService().formatDuration(seconds)), this);
            }
        }
        if (seconds < previousRecord && !getPlayersOnline().isEmpty()) {
            broadcast("dungeon.record-global", Map.of(
                    "time", plugin.getPlaceholderService().formatDuration(seconds)));
        }
    }

    private void giveRewards(Player player) {
        List<String> parts = new ArrayList<>();
        LootTable table = settings.getRewardLootTable().isBlank()
                ? null : dungeon.getLootTable(settings.getRewardLootTable());
        if (table != null) {
            int amount = plugin.getLootManager().giveOrDrop(player, table, player.getLocation(), true, this);
            if (amount > 0) {
                parts.add(amount + " objetos");
            }
        }
        if (settings.getRewardExp() > 0) {
            player.giveExp(settings.getRewardExp());
            parts.add(settings.getRewardExp() + " XP");
        }
        if (settings.getRewardMoney() > 0) {
            if (plugin.getEconomy().deposit(player, settings.getRewardMoney())) {
                parts.add("$" + EconomyHook.format(settings.getRewardMoney()));
            } else {
                plugin.getLogger().warning("No se pudo dar el dinero de recompensa de '" + dungeon.getId()
                        + "': Vault o un plugin de economia no esta disponible.");
            }
        }
        if (!parts.isEmpty()) {
            plugin.getMessages().send(player, "dungeon.reward",
                    Map.of("reward", String.join(", ", parts)), this);
        }
    }

    // ---------------------------------------------------------------------
    // Cofres y bloques premiados
    // ---------------------------------------------------------------------

    private void fillChest(ChestSpawn chest, boolean announce) {
        if (chest == null || !chest.isEnabled() || chest.getLocation() == null) {
            return;
        }
        LootTable table = dungeon.getLootTable(chest.getLootTable());
        if (table == null) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("El cofre '" + chest.getId() + "' usa una tabla inexistente: "
                        + chest.getLootTable());
            }
            return;
        }
        Block block = chest.getLocation().getBlock();
        int placed = plugin.getLootManager().fillChest(block, table);
        if (announce && placed > 0) {
            for (Player player : getPlayersOnline()) {
                if (player.getLocation().distanceSquared(chest.getLocation()) < 64.0D) {
                    plugin.getMessages().send(player, "loot.chest-filled",
                            Map.of("amount", String.valueOf(placed)), this);
                }
            }
        }
        if (chest.getRefillSeconds() > 0) {
            chestRefillAt.put(BlockKey.of(chest.getLocation()),
                    now() + chest.getRefillSeconds() * 1000L);
        }
    }

    private void tickChests(long now) {
        for (Map.Entry<BlockKey, Long> entry : new ArrayList<>(chestRefillAt.entrySet())) {
            if (now < entry.getValue()) {
                continue;
            }
            ChestSpawn chest = chestIndex.get(entry.getKey());
            if (chest == null) {
                chestRefillAt.remove(entry.getKey());
                continue;
            }
            fillChest(chest, false);
            if (chest.getRefillSeconds() > 0) {
                chestRefillAt.put(entry.getKey(), now + chest.getRefillSeconds() * 1000L);
            } else {
                chestRefillAt.remove(entry.getKey());
            }
        }
    }

    public boolean handleChestOpen(Player player, Block block) {
        ChestSpawn chest = chestIndex.get(BlockKey.of(block));
        if (chest == null || !(block.getState() instanceof Chest chestState)) {
            return false;
        }
        // El contenido se genera al iniciar la dungeon (o por el refill
        // configurado), nunca por cerrar y volver a abrir el cofre.
        return true;
    }

    public void handleChestBroken(Block block) {
        if (block == null) {
            return;
        }
        BlockKey key = BlockKey.of(block);
        chestIndex.remove(key);
        chestRefillAt.remove(key);
    }

    public RewardBlock getRewardAt(Block block) {
        return rewardIndex.get(BlockKey.of(block));
    }

    public boolean hasChest(Block block) {
        return chestIndex.containsKey(BlockKey.of(block));
    }

    public boolean handleRewardBreak(Player player, Block block, RewardBlock reward) {
        if (reward == null || brokenRewards.contains(reward.getId())) {
            return false;
        }
        LootTable table = dungeon.getLootTable(reward.getLootTable());
        int amount = 0;
        if (table != null) {
            amount = plugin.getLootManager().giveOrDrop(player, table, block.getLocation(),
                    reward.isDirectGive(), this);
        }
        if (reward.isAnnounce() && amount > 0) {
            plugin.getMessages().send(player, "loot.reward-broken",
                    Map.of("amount", String.valueOf(amount)), this);
        }
        plugin.getDungeonManager().executeCommands(reward.getCommands(), player, this);
        plugin.getConfigManager().playSound(player, "reward");
        block.setType(org.bukkit.Material.AIR, false);
        if (reward.isOneTime()) {
            brokenRewards.add(reward.getId());
        } else if (reward.getRespawnSeconds() > 0) {
            rewardRestoreAt.put(reward.getId(), now() + reward.getRespawnSeconds() * 1000L);
            plugin.getMessages().send(player, "loot.reward-respawn",
                            Map.of("time", plugin.getPlaceholderService().formatDuration(reward.getRespawnSeconds())), this);
        } else {
            brokenRewards.add(reward.getId());
        }
        return true;
    }

    private void tickRewards(long now) {
        for (Map.Entry<String, Long> entry : new ArrayList<>(rewardRestoreAt.entrySet())) {
            if (now < entry.getValue()) {
                continue;
            }
            rewardRestoreAt.remove(entry.getKey());
            RewardBlock reward = dungeon.getReward(entry.getKey());
            if (reward != null && reward.getLocation() != null) {
                reward.getLocation().getBlock().setBlockData(reward.parseBlockData(), false);
                brokenRewards.remove(reward.getId());
            }
        }
    }

    private void restoreAllRewards() {
        for (RewardBlock reward : dungeon.getRewards().values()) {
            if (reward.getLocation() != null) {
                Block block = reward.getLocation().getBlock();
                if (block.getType().isAir() || brokenRewards.contains(reward.getId())) {
                    block.setBlockData(reward.parseBlockData(), false);
                }
            }
        }
        brokenRewards.clear();
        rewardRestoreAt.clear();
    }

    /**
     * Vuelve a colocar los cofres registrados. Si un jugador los rompio en una
     * partida anterior, se restauran aqui para que la siguiente partida pueda
     * reclamar sus recompensas. Con {@code clearInventories} tambien se vacia
     * el inventario para empezar de cero.
     */
    public void restoreChestBlocks(boolean clearInventories) {
        for (ChestSpawn chest : dungeon.getChests().values()) {
            if (chest.getLocation() == null || chest.getLocation().getWorld() == null) {
                continue;
            }
            Block block = chest.getLocation().getBlock();
            boolean missing = block.getType().isAir() || !(block.getState() instanceof Chest);
            if (missing) {
                block.setBlockData(chest.parseBlockData(), false);
            }
            if (clearInventories && block.getState() instanceof Chest chestState) {
                chestState.getBlockInventory().clear();
                chestState.update(true, false);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Bossbars
    // ---------------------------------------------------------------------

    private void tickBars() {
        if (dungeonBar != null) {
            Location center = dungeon.getRegion() != null
                    ? dungeon.getRegion().getCenter()
                    : dungeon.getEntry();
            dungeonBar.tick(getPlayersOnline(), center, (float) getProgress(), player ->
                    Text.parse(plugin.getPlaceholderService().apply(
                            settings.getBossBar().getTitle(), player, this, null)));
        }
        for (Map.Entry<UUID, BossBarHandle> entry : new ArrayList<>(bossBars.entrySet())) {
            BossDefinition definition = bosses.get(entry.getKey());
            org.bukkit.entity.Entity entity = Bukkit.getEntity(entry.getKey());
            if (definition == null || !(entity instanceof LivingEntity living) || entity.isDead()) {
                entry.getValue().hideAll();
                bossBars.remove(entry.getKey());
                continue;
            }
            AttributeInstance maxHealthAttribute = living.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double maxHealth = maxHealthAttribute == null ? 0.0D : maxHealthAttribute.getValue();
            float progress = maxHealth <= 0
                    ? 0.0F
                    : (float) (living.getHealth() / maxHealth);
            BossBarSettings barSettings = definition.getBossBar();
            entry.getValue().tick(getPlayersOnline(), living.getLocation(), progress, player ->
                    Text.parse(plugin.getPlaceholderService().apply(
                            barSettings.getTitle(), player, this, living)));
        }
    }

    public void hideBars(Player player) {
        if (dungeonBar != null) {
            dungeonBar.hide(player);
        }
        for (BossBarHandle bar : bossBars.values()) {
            bar.hide(player);
        }
    }

    public void hideBars() {
        if (dungeonBar != null) {
            dungeonBar.hideAll();
        }
        for (BossBarHandle bar : bossBars.values()) {
            bar.hideAll();
        }
    }

    private void playSound(String key) {
        for (Player player : getPlayersOnline()) {
            plugin.getConfigManager().playSound(player, key);
        }
    }

    private void broadcast(String key, Map<String, String> placeholders) {
        plugin.getMessages().broadcast(this, key, placeholders);
    }

    private void broadcastExcept(Player excluded, String key, Map<String, String> placeholders) {
        for (Player player : getPlayersOnline()) {
            if (!player.equals(excluded)) {
                plugin.getMessages().send(player, key, placeholders, this);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Getters y utilidades
    // ---------------------------------------------------------------------

    private String bossName(BossDefinition definition, LivingEntity entity) {
        if (definition.getDisplayName() != null && !definition.getDisplayName().isBlank()) {
            return Text.plain(Text.parse(definition.getDisplayName()));
        }
        if (entity.customName() != null) {
            return Text.plain(entity.customName());
        }
        return entity.getType().name();
    }

    public long getElapsedSeconds() {
        if (startedAt <= 0) {
            return 0L;
        }
        long end = endedAt > 0 ? endedAt : now();
        return Math.max(0, (end - startedAt) / 1000L);
    }

    public long getRemainingSeconds() {
        if (settings.getTimeLimitSeconds() <= 0) {
            return 0L;
        }
        return Math.max(0, settings.getTimeLimitSeconds() - getElapsedSeconds());
    }

    public double getProgress() {
        double bossProgress = initialBossCount <= 0
                ? 1.0D
                : Math.max(0.0D, (initialBossCount - bosses.size() - pendingBossSpawns) / (double) initialBossCount);
        double killProgress = requiredKills <= 0
                ? 0.0D
                : Math.min(1.0D, kills / (double) requiredKills);
        return switch (settings.getCompleteOn()) {
            case BOSSES -> initialBossCount > 0 ? bossProgress : killProgress;
            case KILLS -> killProgress;
            case BOSSES_AND_KILLS -> (bossProgress + killProgress) / 2.0D;
            case MANUAL -> killProgress;
        };
    }

    public LivingEntity getPrimaryBoss() {
        for (UUID uuid : bosses.keySet()) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof LivingEntity living && !living.isDead()) {
                return living;
            }
        }
        return null;
    }

    public Collection<Player> getPlayersOnline() {
        List<Player> online = new ArrayList<>();
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                online.add(player);
            }
        }
        return online;
    }

    public int getPlayerCount() {
        return getPlayersOnline().size();
    }

    public boolean hasPlayer(Player player) {
        return player != null && players.contains(player.getUniqueId());
    }

    public boolean isRunning() {
        return state == DungeonState.RUNNING;
    }

    public boolean isWaiting() {
        return state == DungeonState.WAITING;
    }

    /** Oleada actual empezando en 1 (0 si aun no hay). */
    public int getCurrentWave() {
        return Math.max(0, waveIndex + 1);
    }

    public int getWaveCount() {
        return waves.size();
    }

    public long getLobbySecondsLeft() {
        return lobbyEndsAt <= 0 ? 0L : Math.max(0L, (lobbyEndsAt - now() + 999L) / 1000L);
    }

    public String getLastCheckpoint() {
        return lastCheckpoint == null ? "" : lastCheckpoint;
    }

    public Dungeon getDungeon() {
        return dungeon;
    }

    public DungeonSettings getSettings() {
        return settings;
    }

    public DungeonState getState() {
        return state;
    }

    public Set<UUID> getPlayers() {
        return players;
    }

    public Set<UUID> getLeftPlayers() {
        return leftPlayers;
    }

    public int getKills() {
        return kills;
    }

    public int getTotalMobs() {
        return totalMobs;
    }

    public int getTotalEnemies() {
        return totalEnemies;
    }

    public int getRequiredKills() {
        return requiredKills;
    }

    public int getInitialBossCount() {
        return initialBossCount;
    }

    public Map<UUID, BossDefinition> getBosses() {
        return bosses;
    }

    public Map<UUID, BossBarHandle> getBossBars() {
        return bossBars;
    }

    public String getFailReason() {
        return failReason;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getEndedAt() {
        return endedAt;
    }

    private long now() {
        return System.currentTimeMillis();
    }
}
