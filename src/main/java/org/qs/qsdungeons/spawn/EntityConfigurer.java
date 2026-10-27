package org.qs.qsdungeons.spawn;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.qs.qsdungeons.util.Text;

import java.util.Map;

/**
 * Aplica atributos, equipo y aspecto a entidades normales.
 */
public final class EntityConfigurer {

    private EntityConfigurer() {
    }

    public static void applyAttributes(LivingEntity entity, double health, double damage,
                                       double speed, double followRange) {
        if (health > 0) {
            AttributeInstance maxHealth = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(health);
            }
            entity.setHealth(Math.min(health, maxHealth == null ? health : maxHealth.getValue()));
        }
        if (damage > 0) {
            AttributeInstance attackDamage = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
            if (attackDamage != null) {
                attackDamage.setBaseValue(damage);
            }
        }
        if (speed > 0) {
            AttributeInstance movementSpeed = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (movementSpeed != null) {
                movementSpeed.setBaseValue(speed);
            }
        }
        if (followRange > 0) {
            AttributeInstance attribute = entity.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
            if (attribute != null) {
                attribute.setBaseValue(followRange);
            }
        }
    }

    public static void applyEquipment(LivingEntity entity, Map<EquipmentSlot, ItemStack> equipment) {
        if (equipment == null || equipment.isEmpty() || entity.getEquipment() == null) {
            return;
        }
        for (Map.Entry<EquipmentSlot, ItemStack> entry : equipment.entrySet()) {
            entity.getEquipment().setItem(entry.getKey(), entry.getValue().clone(), true);
        }
    }

    public static void applyName(LivingEntity entity, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        entity.customName(Text.parse(name));
        entity.setCustomNameVisible(true);
    }

    public static void applyOptions(LivingEntity entity, boolean glowing, boolean baby,
                                    boolean ai, boolean persistent) {
        entity.setGlowing(glowing);
        entity.setPersistent(persistent);
        if (entity instanceof Mob mob) {
            mob.setAI(ai);
            mob.setRemoveWhenFarAway(!persistent);
        }
        if (baby && entity instanceof Ageable ageable) {
            ageable.setBaby();
        }
    }
}
