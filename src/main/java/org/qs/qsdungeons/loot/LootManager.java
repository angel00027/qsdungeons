package org.qs.qsdungeons.loot;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.dungeon.instance.DungeonInstance;
import org.qs.qsdungeons.util.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Reparte el loot en cofres, inventarios o el suelo.
 */
public final class LootManager {

    private final QsDungeons plugin;
    private final Random random = new Random();

    public LootManager(QsDungeons plugin) {
        this.plugin = plugin;
    }

    public List<ItemStack> roll(LootTable table) {
        if (table == null) {
            return List.of();
        }
        return splitStacks(table.roll(random));
    }

    public int fillChest(Block block, LootTable table) {
        if (block == null || !(block.getState() instanceof Chest chest)) {
            return 0;
        }
        List<ItemStack> items = roll(table);
        if (items.isEmpty()) {
            return 0;
        }
        Inventory inventory = chest.getBlockInventory();
        if (plugin.getConfigManager().clearChestsOnStart()) {
            inventory.clear();
        }
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null || inventory.getItem(i).getType().isAir()) {
                slots.add(i);
            }
        }
        Collections.shuffle(slots, random);
        int placed = 0;
        for (ItemStack item : items) {
            if (slots.isEmpty()) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 0.5D, 0.5D), item);
                continue;
            }
            int slot = slots.remove(slots.size() - 1);
            inventory.setItem(slot, item);
            placed++;
        }
        return placed;
    }

    public int giveOrDrop(Player player, LootTable table, Location location, boolean direct,
                          DungeonInstance instance) {
        if (table == null) {
            return 0;
        }
        List<LootTable.RolledEntry> rolled = table.rollEntries(random);
        if (rolled.isEmpty()) {
            return 0;
        }
        List<ItemStack> items = splitStacks(rolled.stream()
                .map(LootTable.RolledEntry::item)
                .toList());
        int total = 0;
        for (ItemStack item : items) {
            total += item.getAmount();
        }
        if (player != null && direct) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(items.toArray(new ItemStack[0]));
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        } else {
            Location dropLocation = location == null
                    ? (player == null ? null : player.getLocation())
                    : location;
            if (dropLocation == null) {
                return total;
            }
            for (ItemStack item : items) {
                dropLocation.getWorld().dropItemNaturally(dropLocation, item);
            }
        }
        // Comandos y mensajes solo de las entradas que han salido.
        for (LootTable.RolledEntry rolledEntry : rolled) {
            LootEntry entry = rolledEntry.entry();
            if (!entry.getCommands().isEmpty()) {
                plugin.getDungeonManager().executeCommands(entry.getCommands(), player, instance);
            }
            if (player != null && entry.getMessage() != null && !entry.getMessage().isBlank()) {
                plugin.getMessages().sendRaw(player, entry.getMessage().replace("{item}", entry.describe()), instance);
            }
        }
        return total;
    }

    public int drop(LootTable table, Location location) {
        return giveOrDrop(null, table, location, false, null);
    }

    public List<ItemStack> splitStacks(List<ItemStack> input) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : input) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            int maxStack = Math.max(1, item.getMaxStackSize());
            int amount = item.getAmount();
            while (amount > 0) {
                int stack = Math.min(maxStack, amount);
                ItemStack copy = item.clone();
                copy.setAmount(stack);
                result.add(copy);
                amount -= stack;
            }
        }
        return result;
    }

    public String displayName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "?";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return Text.plain(meta.displayName());
        }
        String name = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }
}
