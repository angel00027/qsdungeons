package org.qs.qsdungeons.boss;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.dungeon.instance.DungeonInstance;
import org.qs.qsdungeons.spawn.EntityConfigurer;
import org.qs.qsdungeons.spawn.MobType;
import org.qs.qsdungeons.spawn.MythicMobsHook;
import org.qs.qsdungeons.util.Keys;
import org.qs.qsdungeons.util.Tags;

/**
 * Crea las entidades de los jefes.
 */
public final class BossService {

    private final QsDungeons plugin;

    public BossService(QsDungeons plugin) {
        this.plugin = plugin;
    }

    public LivingEntity spawn(DungeonInstance instance, BossDefinition definition) {
        if (definition == null) {
            return null;
        }
        Location location = definition.getLocation();
        if (location == null && instance.getDungeon().getRegion() != null) {
            location = instance.getDungeon().getRegion().getCenter();
        }
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("El jefe '" + definition.getId() + "' no tiene una ubicacion valida.");
            return null;
        }
        // Centrar en el bloque para evitar aparecer dentro de una pared.
        location = location.clone();
        location.setX(location.getBlockX() + 0.5D);
        location.setZ(location.getBlockZ() + 0.5D);

        LivingEntity living = null;
        if (definition.getType() == MobType.MYTHIC) {
            MythicMobsHook hook = plugin.getMythicMobsHook();
            if (hook == null || !hook.isAvailable()) {
                plugin.getLogger().warning("El jefe '" + definition.getId()
                        + "' usa MythicMobs pero el plugin no esta disponible.");
                return null;
            }
            Entity entity = hook.spawn(definition.getEntity(), location);
            if (entity instanceof LivingEntity mythicLiving) {
                living = mythicLiving;
                EntityConfigurer.applyEquipment(living, definition.getEquipment());
                EntityConfigurer.applyName(living, definition.getDisplayName());
                living.setGlowing(definition.isGlowing());
                living.setPersistent(definition.isPersistent());
            }
        } else {
            EntityType type;
            try {
                type = EntityType.valueOf(definition.getEntity().trim().toUpperCase());
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Entidad vanilla invalida en el jefe '" + definition.getId() + "'.");
                return null;
            }
            if (!type.isSpawnable() || type.getEntityClass() == null
                    || !LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
                plugin.getLogger().warning("La entidad " + type + " del jefe no se puede usar.");
                return null;
            }
            Entity entity = location.getWorld().spawnEntity(location, type);
            if (entity instanceof LivingEntity vanillaLiving) {
                living = vanillaLiving;
                EntityConfigurer.applyAttributes(living, definition.getHealth(), definition.getDamage(),
                        definition.getSpeed(), definition.getFollowRange());
                EntityConfigurer.applyEquipment(living, definition.getEquipment());
                EntityConfigurer.applyName(living, definition.getDisplayName());
                EntityConfigurer.applyOptions(living, definition.isGlowing(), definition.isBaby(),
                        definition.isAi(), definition.isPersistent());
            }
        }
        if (living == null) {
            return null;
        }
        Tags.set(living, Keys.DUNGEON, instance.getDungeon().getId());
        Tags.set(living, Keys.BOSS, definition.getId());
        Tags.set(living, Keys.ENTITY, definition.getEntity());
        return living;
    }
}
