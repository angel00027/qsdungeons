package org.qs.qsdungeons.dungeon.instance;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.boss.BossDefinition;
import org.qs.qsdungeons.dungeon.BossBarSettings;
import org.qs.qsdungeons.dungeon.ChestSpawn;
import org.qs.qsdungeons.dungeon.CompleteMode;
import org.qs.qsdungeons.dungeon.Dungeon;
import org.qs.qsdungeons.dungeon.DungeonSettings;
import org.qs.qsdungeons.dungeon.DungeonState;
import org.qs.qsdungeons.dungeon.RewardBlock;
import org.qs.qsdungeons.loot.LootManager;
import org.qs.qsdungeons.loot.LootTable;
import org.qs.qsdungeons.spawn.SpawnPoint;
import org.qs.qsdungeons.util.BlockKey;
import org.qs.qsdungeons.util.Tags;
import org.qs.qsdungeons.util.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    private DungeonState state = DungeonState.IDLE;
    private long startedAt;
    private long endedAt;
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

    public void start() {
        if (state == DungeonState.RUNNING) {
            return;
        }
        state = DungeonState.RUNNING;
        startedAt = System.currentTimeMillis();
        endedAt = 0L;
        pendingStopAt = 0L;
        pendingSuccess = false;
        failReason = "";
        kills = 0;
        automaticRequiredKills = settings.getRequiredKills() <= 0;
        leftPlayers.clear();
        brokenRewards.clear();
        rewardRestoreAt.clear();
        chestRefillAt.clear();
        chestIndex.clear();
        rewardIndex.clear();
        bossesActivated = !settings.isBossesAfterWaves();
        plannedBossCount = dungeon.getBosses().size();

        restoreAllRewards();
        restoreChestBlocks(true);

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
        for (SpawnPoint point : dungeon.getSpawns().values()) {
            SpawnerRuntime runtime = new SpawnerRuntime(point);
            spawners.put(point.getId(), runtime);
            if (point.getLocation() == null) {
                plugin.getLogger().warning("El spawn '" + point.getId() + "' de la dungeon '"
                        + dungeon.getId() + "' no tiene ubicacion.");
                continue;
            }
            // Si hay reaparicion, la cantidad es el total de la oleada; si no,
            // solo pueden llegar a existir max-alive mobs.
            int effectiveTotal = point.getRespawnSeconds() > 0
                    ? point.getAmount()
                    : Math.min(point.getAmount(), point.getMaxAlive());
            mobCount += effectiveTotal;
            if (point.getSpawnIntervalSeconds() > 0) {
                runtime.setPendingSpawns(point.getAmount());
                runtime.setNextSpawnAt(now());
            } else {
                int toSpawn = Math.min(point.getAmount(), point.getMaxAlive());
                for (int i = 0; i < toSpawn; i++) {
                    spawnMob(runtime);
                }
                runtime.setPendingSpawns(Math.max(0, point.getAmount() - toSpawn));
                runtime.setNextSpawnAt(now());
            }
            runtime.setInitialSpawned(true);
            if (runtime.getPendingSpawns() == 0 && point.getRespawnSeconds() > 0) {
                runtime.setNextSpawnAt(now() + point.getRespawnSeconds() * 1000L);
            }
        }

        if (bossesActivated) {
            spawnBosses();
        }
        initialBossCount = bossesActivated ? bosses.size() : plannedBossCount;
        totalMobs = mobCount;
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
        if (bossesActivated && !bosses.isEmpty()) {
            return;
        }
        bossesActivated = true;
        for (BossDefinition definition : dungeon.getBosses().values()) {
            LivingEntity entity = plugin.getBossService().spawn(this, definition);
            if (entity == null) {
                continue;
            }
            bosses.put(entity.getUniqueId(), definition);
            BossBarSettings barSettings = definition.getBossBar();
            if (barSettings != null && barSettings.isEnabled()) {
                bossBars.put(entity.getUniqueId(), new BossBarHandle(barSettings, 1.0F));
            }
            if (definition.isAnnounceSpawn()) {
                broadcast("dungeon.boss-spawn", Map.of("boss", bossName(definition, entity)));
                playSound("boss-spawn");
            }
            plugin.getDungeonManager().executeCommands(definition.getCommandsOnSpawn(), null, this);
        }
        initialBossCount = bosses.size();
        totalEnemies = totalMobs + initialBossCount;
        if (settings.getRequiredKills() <= 0) {
            requiredKills = totalEnemies;
        }
        broadcast("dungeon.bosses-unlocked", Map.of("amount", String.valueOf(bosses.size())));
    }

    public boolean addPlayer(Player player) {
        if (state != DungeonState.RUNNING) {
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
        checkCompletion();
        return true;
    }

    public void removePlayer(Player player, boolean teleport) {
        if (player == null || !players.remove(player.getUniqueId())) {
            return;
        }
        leftPlayers.add(player.getUniqueId());
        hideBars(player);
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
        if (players.isEmpty() && state == DungeonState.RUNNING && settings.isResetOnEmpty()) {
            scheduleStop(settings.getResetDelaySeconds(), false);
        }
    }

    public void tick() {
        long now = now();
        if (state == DungeonState.RUNNING) {
            tickSpawners(now);
            if (!bossesActivated && wavesFinished()) {
                spawnBosses();
            }
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
        if (pendingStopAt > 0 && now >= pendingStopAt) {
            pendingStopAt = 0L;
            plugin.getDungeonManager().stopInstance(this, pendingSuccess);
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
        broadcast("dungeon.completed", Map.of("dungeon", dungeon.getId(), "time", String.valueOf(settings.getEndDelaySeconds())));
        for (Player player : getPlayersOnline()) {
            plugin.getMessages().title(player, "complete", Map.of(
                    "dungeon", dungeon.getId(),
                    "dungeon_name", dungeon.getDisplayName()), this);
            plugin.getConfigManager().playSound(player, "complete");
            plugin.getMessages().send(player, "dungeon.complete-loot", Map.of("time", String.valueOf(settings.getEndDelaySeconds())), this);
        }
        plugin.getDungeonManager().executeCommandsForPlayers(settings.getCommandsOnComplete(), this);
        plugin.getDungeonManager().executeCommands(settings.getCommandsOnComplete(), null, this);
        plugin.getLogger().info("Dungeon '" + dungeon.getId() + "' completada.");
    }

    public void fail(String reason) {
        if (state != DungeonState.RUNNING) {
            return;
        }
        state = DungeonState.FAILED;
        endedAt = now();
        failReason = reason == null || reason.isBlank() ? "?" : reason;
        pendingSuccess = false;
        scheduleStop(settings.getEndDelaySeconds(), false);
        hideBars();
        broadcast("dungeon.failed", Map.of("reason", failReason, "time", String.valueOf(settings.getEndDelaySeconds())));
        for (Player player : getPlayersOnline()) {
            plugin.getMessages().title(player, "fail", Map.of("reason", failReason), this);
            plugin.getConfigManager().playSound(player, "fail");
            plugin.getMessages().send(player, "dungeon.fail-loot", Map.of("reason", failReason, "time", String.valueOf(settings.getEndDelaySeconds())), this);
        }
        plugin.getDungeonManager().executeCommandsForPlayers(settings.getCommandsOnFail(), this);
        plugin.getDungeonManager().executeCommands(settings.getCommandsOnFail(), null, this);
        plugin.getLogger().info("Dungeon '" + dungeon.getId() + "' fallida: " + failReason);
    }

    /**
     * Limpieza final. La llama DungeonManager cuando la partida se detiene.
     */
    public void shutdown() {
        for (UUID uuid : new ArrayList<>(bosses.keySet())) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        bosses.clear();
        for (SpawnerRuntime runtime : spawners.values()) {
            for (UUID uuid : new ArrayList<>(runtime.getAlive())) {
                org.bukkit.entity.Entity entity = Bukkit.getEntity(uuid);
                if (entity != null && !entity.isDead()) {
                    entity.remove();
                }
            }
            runtime.getAlive().clear();
        }
        spawners.clear();
        hideBars();
        restoreAllRewards();
        restoreChestBlocks(false);
        chestRefillAt.clear();
        chestIndex.clear();
        rewardIndex.clear();

        for (Player player : getPlayersOnline()) {
            Location exit = dungeon.getExit() != null ? dungeon.getExit() : dungeon.getEntry();
            if (exit != null) {
                player.teleport(exit);
            }
            plugin.getDungeonManager().unmapPlayer(player.getUniqueId());
        }
        players.clear();
        state = DungeonState.IDLE;
        plugin.getDungeonManager().executeCommands(settings.getCommandsOnReset(), null, this);
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
                org.bukkit.entity.Entity entity = Bukkit.getEntity(uuid);
                return entity == null || entity.isDead() || !entity.isValid();
            });

            if (point.getLocation() == null) {
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
        runtime.getAlive().add(entity.getUniqueId());
        if (!point.getCommandsOnSpawn().isEmpty()) {
            plugin.getDungeonManager().executeCommands(point.getCommandsOnSpawn(), null, this);
        }
        return entity;
    }

    public void handleDeath(LivingEntity entity, Player killer) {
        if (entity == null || state != DungeonState.RUNNING) {
            return;
        }
        String bossId = Tags.bossOf(entity);
        if (bossId != null) {
            BossDefinition definition = bosses.remove(entity.getUniqueId());
            BossBarHandle bar = bossBars.remove(entity.getUniqueId());
            if (bar != null) {
                bar.hideAll();
            }
            kills++;
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
            if (runtime.getPoint().getRespawnSeconds() > 0) {
                runtime.setNextSpawnAt(now() + runtime.getPoint().getRespawnSeconds() * 1000L);
            }
        }
        kills++;
        SpawnPoint point = dungeon.getSpawn(spawnId);
        if (point != null) {
            LootTable table = dungeon.getLootTable(point.getLootTable());
            if (table != null) {
                plugin.getLootManager().giveOrDrop(killer, table, entity.getLocation(),
                        !plugin.getConfigManager().dropNaturally(), this);
            }
        }
        checkCompletion();
    }

    private void checkCompletion() {
        if (state != DungeonState.RUNNING || players.isEmpty()) {
            return;
        }
        if (!bossesActivated) {
            return;
        }
        boolean bossesDone = bosses.isEmpty();
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

    private boolean wavesFinished() {
        for (SpawnerRuntime runtime : spawners.values()) {
            // El respawn puede continuar después de desbloquear los jefes;
            // la oleada se considera terminada cuando ya no quedan mobs vivos
            // ni spawns iniciales pendientes.
            if (runtime.getPendingSpawns() > 0
                    || !runtime.getAlive().isEmpty()) {
                return false;
            }
        }
        return true;
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
                    Map.of("time", plugin.getPlaceholderService().formatTime(reward.getRespawnSeconds())), this);
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
                : (initialBossCount - bosses.size()) / (double) initialBossCount;
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
