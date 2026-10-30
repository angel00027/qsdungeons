package org.qs.qsdungeons.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.boss.BossDefinition;
import org.qs.qsdungeons.dungeon.Dungeon;
import org.qs.qsdungeons.dungeon.instance.DungeonInstance;
import org.qs.qsdungeons.spawn.SpawnPoint;
import org.qs.qsdungeons.util.Tags;

/**
 * Conecta las muertes de entidades con su instancia y limpia drops vanilla.
 */
public final class DungeonEntityListener implements Listener {

    private final QsDungeons plugin;

    public DungeonEntityListener(QsDungeons plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (Tags.isTestEntity(entity)) {
            return;
        }
        if (!Tags.isDungeonEntity(entity)) {
            return;
        }
        DungeonInstance instance = plugin.getDungeonManager().getInstance(Tags.dungeonOf(entity));
        if (instance == null) {
            return;
        }
        Dungeon dungeon = instance.getDungeon();
        boolean clearDrops = plugin.getConfigManager().clearVanillaDrops();

        String bossId = Tags.bossOf(entity);
        if (bossId != null) {
            BossDefinition boss = dungeon.getBoss(bossId);
            if (boss != null) {
                clearDrops = boss.isClearVanillaDrops();
            }
        } else {
            String spawnId = Tags.spawnOf(entity);
            SpawnPoint spawn = spawnId == null ? null : dungeon.getSpawn(spawnId);
            if (spawn != null) {
                clearDrops = spawn.isClearVanillaDrops();
            }
        }
        if (clearDrops) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
        instance.handleDeath(entity, entity.getKiller());
    }

    /**
     * Los mobs de una partida que termino mientras su chunk estaba descargado
     * se eliminan en cuanto el chunk vuelve a cargarse.
     */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            plugin.getDungeonManager().cleanupOrphan(entity);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (event instanceof org.bukkit.event.entity.EntityCombustByEntityEvent
                || event instanceof org.bukkit.event.entity.EntityCombustByBlockEvent) {
            return;
        }
        Entity entity = event.getEntity();
        if (Tags.isTestEntity(entity)) {
            return;
        }
        if (!Tags.isDungeonEntity(entity)) {
            return;
        }
        String dungeonId = Tags.dungeonOf(entity);
        DungeonInstance instance = plugin.getDungeonManager().getInstance(dungeonId);
        if (instance == null) {
            return;
        }
        Dungeon dungeon = instance.getDungeon();
        String bossId = Tags.bossOf(entity);
        if (bossId != null) {
            BossDefinition boss = dungeon.getBoss(bossId);
            if (boss != null && !boss.isBurnInDaylight()) {
                event.setCancelled(true);
                return;
            }
        }
        String spawnId = Tags.spawnOf(entity);
        if (spawnId != null) {
            SpawnPoint spawn = dungeon.getSpawn(spawnId);
            if (spawn != null && !spawn.isBurnInDaylight()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || !Tags.isDungeonEntity(victim)) {
            return;
        }
        if (Tags.isTestEntity(victim)) {
            return;
        }
        DungeonInstance instance = plugin.getDungeonManager().getInstance(Tags.dungeonOf(victim));
        if (instance == null) {
            return;
        }
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null) {
            return;
        }
        if (!instance.hasPlayer(attacker)) {
            event.setCancelled(true);
        }
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
