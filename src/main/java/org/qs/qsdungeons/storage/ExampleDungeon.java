package org.qs.qsdungeons.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.qs.qsdungeons.boss.BossDefinition;
import org.qs.qsdungeons.dungeon.Dungeon;
import org.qs.qsdungeons.dungeon.Region;
import org.qs.qsdungeons.loot.LootEntry;
import org.qs.qsdungeons.loot.LootTable;
import org.qs.qsdungeons.spawn.SpawnPoint;

/**
 * Crea una dungeon de ejemplo (desactivada) la primera vez que arranca el plugin.
 * Sirve como plantilla viva de todos los archivos YAML.
 */
public final class ExampleDungeon {

    private ExampleDungeon() {
    }

    public static void create(DungeonRepository repository) {
        Dungeon dungeon = repository.create("ejemplo");
        if (dungeon == null) {
            return;
        }
        // La dungeon de ejemplo no se activa automaticamente para no afectar al servidor.
        dungeon.setEnabled(false);
        dungeon.setDisplayName("<gradient:#8e2de2:#4a00e0><bold>Dungeon de Ejemplo</bold></gradient>");

        World world = Bukkit.getWorld(repository.getPlugin().getConfigManager().getDefaultWorld());
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        if (world != null) {
            Location spawn = world.getSpawnLocation().clone();
            dungeon.setEntry(spawn);
            dungeon.setExit(spawn);
            dungeon.setRegion(new Region(world.getName(),
                    spawn.getBlockX() - 10, spawn.getBlockY() - 2, spawn.getBlockZ() - 10,
                    spawn.getBlockX() + 10, spawn.getBlockY() + 8, spawn.getBlockZ() + 10));

            SpawnPoint spawnPoint = new SpawnPoint("zombi");
            spawnPoint.setEntity("ZOMBIE");
            spawnPoint.setLocation(spawn.clone().add(3, 0, 3));
            spawnPoint.setAmount(3);
            spawnPoint.setMaxAlive(3);
            // La dungeon de ejemplo tiene una oleada finita. El respawn solo
            // se activa cuando se configura explícitamente un valor mayor a 0.
            spawnPoint.setRespawnSeconds(0);
            spawnPoint.setName("<red>Zombi de la cripta");
            spawnPoint.setLootTable("comun");
            dungeon.getSpawns().put(spawnPoint.getId(), spawnPoint);
        }

        LootTable common = new LootTable("comun");
        common.setRolls(1, 2);
        common.getEntries().add(new LootEntry(new ItemStack(Material.BREAD, 2)));
        common.getEntries().add(new LootEntry(new ItemStack(Material.IRON_INGOT, 1)));
        common.getEntries().add(new LootEntry(new ItemStack(Material.GOLD_NUGGET, 3)));
        dungeon.getLootTables().put(common.getId(), common);

        LootTable bossLoot = new LootTable("jefe");
        bossLoot.setRolls(2, 3);
        bossLoot.getEntries().add(new LootEntry(new ItemStack(Material.DIAMOND, 2)));
        bossLoot.getEntries().add(new LootEntry(new ItemStack(Material.GOLDEN_APPLE, 1)));
        dungeon.getLootTables().put(bossLoot.getId(), bossLoot);

        if (world != null) {
            BossDefinition boss = new BossDefinition("guardian");
            boss.setEntity("ZOMBIE");
            boss.setDisplayName("<dark_red><bold>Guardian de la Cripta</bold></dark_red>");
            boss.setHealth(80.0D);
            boss.setDamage(6.0D);
            boss.setLootTable("jefe");
            boss.setLocation(dungeon.getEntry().clone().add(5, 0, 5));
            boss.getBossBar().setTitle("<dark_red><bold>{boss}</bold></dark_red> <dark_gray>| <white>{boss_health}<gray>/<white>{boss_max_health}");
            dungeon.getBosses().put(boss.getId(), boss);
        }

        dungeon.getSettings().set("auto-enter", "false");
        dungeon.getSettings().set("auto-start", "false");
        repository.save(dungeon);
    }
}
