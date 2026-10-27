package org.qs.qsdungeons.spawn;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.dungeon.instance.DungeonInstance;
import org.qs.qsdungeons.util.Keys;
import org.qs.qsdungeons.util.Tags;

import java.util.Random;

/**
 * Crea las entidades declaradas en los puntos de spawn.
 */
public final class MobSpawner {

    private final QsDungeons plugin;
    private final Random random = new Random();

    public MobSpawner(QsDungeons plugin) {
        this.plugin = plugin;
    }

    public LivingEntity spawn(DungeonInstance instance, SpawnPoint point) {
        if (point == null || point.getLocation() == null) {
            return null;
        }
        Location base = point.getLocation();
        if (base.getWorld() == null) {
            return null;
        }
        Location location = resolveLocation(base, point.getRadius(), point.isRequireGround(),
                point.getMinPlayerDistance(), instance);
        LivingEntity living = null;
        if (point.getType() == MobType.MYTHIC) {
            MythicMobsHook hook = plugin.getMythicMobsHook();
            if (hook == null || !hook.isAvailable()) {
                plugin.getLogger().warning("El spawn '" + point.getId()
                        + "' usa MythicMobs pero el plugin no esta disponible.");
                return null;
            }
            Entity entity = hook.spawn(point.getEntity(), location);
            if (entity instanceof LivingEntity mythicLiving) {
                living = mythicLiving;
                EntityConfigurer.applyEquipment(living, point.getEquipment());
                EntityConfigurer.applyName(living, point.getName());
                living.setGlowing(point.isGlowing());
                living.setPersistent(point.isPersistent());
            }
        } else {
            EntityType type;
            try {
                type = EntityType.valueOf(point.getEntity().trim().toUpperCase());
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Entidad vanilla invalida en spawn '" + point.getId() + "': " + point.getEntity());
                return null;
            }
            if (!type.isSpawnable() || type.getEntityClass() == null
                    || !LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
                plugin.getLogger().warning("La entidad " + type + " no se puede usar en un spawn.");
                return null;
            }
            Entity entity = base.getWorld().spawnEntity(location, type);
            if (entity instanceof LivingEntity vanillaLiving) {
                living = vanillaLiving;
                EntityConfigurer.applyAttributes(living, point.getHealth(), point.getDamage(),
                        point.getSpeed(), point.getFollowRange());
                EntityConfigurer.applyEquipment(living, point.getEquipment());
                EntityConfigurer.applyName(living, point.getName());
                EntityConfigurer.applyOptions(living, point.isGlowing(), point.isBaby(),
                        point.isAi(), point.isPersistent());
            }
        }
        if (living == null) {
            return null;
        }
        // Algunos mobs personalizados pueden heredar invulnerabilidad o
        // ticks de inmunidad del propio modelo. Los mobs de una dungeon deben
        // poder recibir daño normalmente, salvo que otro plugin lo vuelva a
        // cambiar explícitamente después del spawn.
        living.setInvulnerable(false);
        living.setNoDamageTicks(0);
        Tags.set(living, Keys.DUNGEON, instance.getDungeon().getId());
        Tags.set(living, Keys.SPAWN, point.getId());
        Tags.set(living, Keys.ENTITY, point.getEntity());
        return living;
    }

    public Location resolveLocation(Location base, double radius) {
        return resolveLocation(base, radius, true, 0, null);
    }

    /**
     * Busca una ubicacion segura alrededor del punto de spawn. Se intentan
     * varias posiciones y alturas antes de usar el punto original.
     */
    public Location resolveLocation(Location base, double radius, boolean requireGround,
                                    int minPlayerDistance, DungeonInstance instance) {
        if (base == null || base.getWorld() == null) {
            return null;
        }
        World world = base.getWorld();
        Location fallback = center(base);
        double minDistanceSquared = (double) Math.max(0, minPlayerDistance) * Math.max(0, minPlayerDistance);
        if (radius <= 0.0D) {
            // Sin radio igualmente comprobamos suelo y distancia a jugadores.
            Location candidate = findSafeY(world, base.getBlockX(), base.getBlockY(),
                    base.getBlockZ(), requireGround);
            if (candidate != null
                    && (instance == null || !tooCloseToPlayers(candidate, instance, minDistanceSquared))) {
                return candidate;
            }
            return fallback;
        }
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(random.nextDouble()) * radius;
            int x = base.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = base.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            Location candidate = findSafeY(world, x, base.getBlockY(), z, requireGround);
            if (candidate == null) {
                continue;
            }
            if (instance != null && tooCloseToPlayers(candidate, instance, minDistanceSquared)) {
                continue;
            }
            return candidate;
        }
        return fallback;
    }

    private Location findSafeY(World world, int x, int baseY, int z, boolean requireGround) {
        for (int offset = 3; offset >= -4; offset--) {
            int y = baseY + offset;
            Block feet = world.getBlockAt(x, y, z);
            Block head = feet.getRelative(BlockFace.UP);
            Block ground = feet.getRelative(BlockFace.DOWN);
            if (!feet.isPassable() || !head.isPassable()) {
                continue;
            }
            if (feet.isLiquid() || head.isLiquid()) {
                continue;
            }
            if (requireGround && (!ground.getType().isSolid() || ground.isLiquid())) {
                continue;
            }
            if (!world.getWorldBorder().isInside(feet.getLocation())) {
                continue;
            }
            return center(feet.getLocation());
        }
        return null;
    }

    private boolean tooCloseToPlayers(Location location, DungeonInstance instance, double minDistanceSquared) {
        if (minDistanceSquared <= 0.0D) {
            return false;
        }
        for (Player player : instance.getPlayersOnline()) {
            if (player.getWorld().equals(location.getWorld())
                    && player.getLocation().distanceSquared(location) < minDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private Location center(Location location) {
        Location centered = location.clone();
        centered.setX(centered.getBlockX() + 0.5D);
        centered.setZ(centered.getBlockZ() + 0.5D);
        return centered;
    }
}
