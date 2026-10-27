package org.qs.qsdungeons.storage;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.qs.qsdungeons.util.Text;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Convierte ItemStack a YAML legible y viceversa.
 *
 * <pre>
 * item:
 *   material: DIAMOND_SWORD
 *   amount: 1
 *   name: "&lt;gradient:#ff0000:#ff9900&gt;Espada&lt;/gradient&gt;"
 *   lore:
 *     - "&lt;gray&gt;Una espada legendaria&lt;/gray&gt;"
 *   enchantments:
 *     sharpness: 5
 *   flags:
 *     - HIDE_ATTRIBUTES
 *   glow: true
 *   unbreakable: false
 *   custom-model-data: 1234
 *   skull: "Notch"
 *   color: "#ff0000"
 * </pre>
 */
public final class ItemSerializer {

    private ItemSerializer() {
    }

    @SuppressWarnings({"deprecation", "removal"})
    public static ItemStack read(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        // El formato Base64 es la fuente principal: conserva todos los
        // componentes/NBT de Paper, incluidos datos de plugins y texturas.
        String encoded = section.getString("base64");
        if (encoded != null && !encoded.isBlank()) {
            try {
                ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
                if (item != null && !item.getType().isAir()) {
                    return item;
                }
            } catch (RuntimeException ignored) {
                // Permite leer formatos antiguos o una entrada dañada usando
                // el formato legible de abajo.
            }
        }

        String materialName = section.getString("material");
        if (materialName == null) {
            return null;
        }
        Material material = Material.matchMaterial(materialName.trim().toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem()) {
            return null;
        }
        int amount = Math.max(1, section.getInt("amount", 1));
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        if (section.isString("name")) {
            meta.displayName(Text.parse(section.getString("name")));
        }

        if (section.isList("lore")) {
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            for (String line : section.getStringList("lore")) {
                lore.add(Text.parse(line));
            }
            meta.lore(lore);
        }

        ConfigurationSection enchantments = section.getConfigurationSection("enchantments");
        if (enchantments != null) {
            for (String key : enchantments.getKeys(false)) {
                Enchantment enchantment = resolveEnchantment(key);
                if (enchantment != null) {
                    meta.addEnchant(enchantment, Math.max(1, enchantments.getInt(key, 1)), true);
                }
            }
        }

        if (section.isList("flags")) {
            for (String rawFlag : section.getStringList("flags")) {
                try {
                    meta.addItemFlags(ItemFlag.valueOf(rawFlag.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    // Flag desconocida: se ignora.
                }
            }
        }

        if (section.getBoolean("glow", false)) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (section.getBoolean("unbreakable", false)) {
            meta.setUnbreakable(true);
        }
        if (section.contains("custom-model-data")) {
            meta.setCustomModelData(section.getInt("custom-model-data"));
        }

        if (meta instanceof SkullMeta skullMeta && section.isString("skull")) {
            applySkull(skullMeta, section.getString("skull"));
        }
        if (section.isString("color")) {
            Color color = parseColor(section.getString("color"));
            if (color != null) {
                if (meta instanceof LeatherArmorMeta leatherArmorMeta) {
                    leatherArmorMeta.setColor(color);
                } else if (meta instanceof PotionMeta potionMeta) {
                    potionMeta.setColor(color);
                }
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    @SuppressWarnings({"deprecation", "removal"})
    public static void write(ConfigurationSection section, ItemStack item) {
        if (section == null || item == null || item.getType().isAir()) {
            return;
        }
        section.set("base64", Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        // Se mantienen estos dos campos para que el archivo siga siendo
        // identificable a simple vista y para facilitar copias antiguas.
        section.set("material", item.getType().name());
        section.set("amount", item.getAmount());
    }

    @SuppressWarnings({"deprecation", "removal"})
    public static Enchantment resolveEnchantment(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(normalized));
        if (enchantment != null) {
            return enchantment;
        }
        // Algunos nombres viejos muy usados.
        return switch (normalized) {
            case "damage_all", "sharp" -> Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"));
            case "protection_environmental" -> Registry.ENCHANTMENT.get(NamespacedKey.minecraft("protection"));
            case "arrow_damage" -> Registry.ENCHANTMENT.get(NamespacedKey.minecraft("power"));
            default -> null;
        };
    }

    private static void applySkull(SkullMeta meta, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String owner = value.trim();
        try {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(owner)));
        } catch (IllegalArgumentException ignored) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
        }
    }

    private static Color parseColor(String value) {
        if (value == null) {
            return null;
        }
        String raw = value.trim();
        try {
            if (raw.startsWith("#") && raw.length() == 7) {
                int rgb = Integer.parseInt(raw.substring(1), 16);
                return Color.fromRGB(rgb);
            }
            if (raw.contains(",")) {
                String[] parts = raw.split(",");
                if (parts.length == 3) {
                    return Color.fromRGB(
                            Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim()));
                }
            }
        } catch (NumberFormatException ignored) {
            // Color invalido.
        }
        return null;
    }

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
