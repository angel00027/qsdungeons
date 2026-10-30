package org.qs.qsdungeons.dungeon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.qs.qsdungeons.storage.ItemSerializer;
import org.qs.qsdungeons.util.Keys;
import org.qs.qsdungeons.util.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Llave que suelta un mob o jefe. Abre puertas con trigger KEY o cofres con
 * required-key. Solo sirve dentro de su dungeon y se retira al salir.
 */
public final class DungeonKey {

    private final String id;
    private String name;
    private ItemStack item;
    // "spawn:<id>" o "boss:<id>"; vacio = no la suelta nadie.
    private String dropFrom = "";
    private double dropChance = 1.0D;

    public DungeonKey(String id) {
        this.id = id;
        this.name = "<gold><bold>Llave " + id + "</bold></gold>";
    }

    public static DungeonKey read(String id, ConfigurationSection section) {
        DungeonKey key = new DungeonKey(id);
        if (section == null) {
            return key;
        }
        key.name = section.getString("name", key.name);
        if (section.isConfigurationSection("item")) {
            key.item = ItemSerializer.read(section.getConfigurationSection("item"));
        }
        key.dropFrom = section.getString("drop-from", "");
        key.dropChance = Math.max(0.0D, Math.min(1.0D, section.getDouble("drop-chance", 1.0D)));
        return key;
    }

    public void write(ConfigurationSection section) {
        section.set("name", name);
        if (item != null) {
            ItemSerializer.write(section.createSection("item"), item);
        }
        section.set("drop-from", dropFrom);
        section.set("drop-chance", dropChance);
    }

    /**
     * Crea el item de la llave marcado con la dungeon y el id de la llave.
     */
    public ItemStack createItem(Dungeon dungeon) {
        ItemStack stack = item == null ? new ItemStack(Material.TRIPWIRE_HOOK) : item.clone();
        stack.setAmount(1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.parse(name).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Llave de la dungeon ", NamedTextColor.GRAY)
                    .append(Text.parse(dungeon.getDisplayName()))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Clic derecho sobre su puerta o cofre", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(Keys.KEY_ITEM, PersistentDataType.STRING,
                    dungeon.getId() + ":" + id);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Devuelve "dungeon:llave" si el item es una llave de QSDungeons.
     */
    public static String tagOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(Keys.KEY_ITEM, PersistentDataType.STRING);
    }

    public boolean dropsFrom(String type, String sourceId) {
        return dropFrom != null && dropFrom.equalsIgnoreCase(type + ":" + sourceId);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null || name.isBlank() ? this.name : name;
    }

    public ItemStack getItem() {
        return item;
    }

    public void setItem(ItemStack item) {
        this.item = item == null || item.getType().isAir() ? null : item.clone();
        if (this.item != null) {
            this.item.setAmount(1);
        }
    }

    public String getDropFrom() {
        return dropFrom == null ? "" : dropFrom;
    }

    public void setDropFrom(String dropFrom) {
        this.dropFrom = dropFrom == null ? "" : dropFrom;
    }

    public double getDropChance() {
        return dropChance;
    }

    public void setDropChance(double dropChance) {
        this.dropChance = Math.max(0.0D, Math.min(1.0D, dropChance));
    }
}
