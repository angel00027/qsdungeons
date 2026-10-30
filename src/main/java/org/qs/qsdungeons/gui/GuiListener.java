package org.qs.qsdungeons.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.qs.qsdungeons.QsDungeons;

/**
 * Listener de clics del GUI y de las entradas por chat usadas para pedir
 * valores (nombres, ids y numeros).
 */
public final class GuiListener implements Listener {

    private final QsDungeons plugin;

    public GuiListener(QsDungeons plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        if (holder.getMenu().equals("equipment")) {
            boolean top = event.getClickedInventory() == event.getView().getTopInventory();
            boolean allowed = java.util.Arrays.stream(holder.getData()
                            .getOrDefault("equipment-slots", "").split(","))
                    .anyMatch(value -> value.equals(String.valueOf(event.getRawSlot())));
            if (top && !allowed) {
                // Las ranuras decorativas no se mueven, pero sus botones siguen funcionando.
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    plugin.getGuiManager().handleClick(player, holder, event.getRawSlot(),
                            event.isRightClick(), event.isShiftClick());
                }
            } else {
                event.setCancelled(!top && event.isShiftClick());
            }
            if (!event.isCancelled() && (top || !event.isShiftClick())) {
                scheduleEquipmentSave(holder);
            }
            return;
        }
        if (holder.getMenu().equals("loot-editor")) {
            boolean top = event.getClickedInventory() == event.getView().getTopInventory();
            boolean allowed = String.valueOf(event.getRawSlot()).equals(
                    holder.getData().getOrDefault("loot-input-slot", "-1"));
            if (top && !allowed) {
                // Solo la ranura de entrada acepta objetos; el resto son botones/lista.
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    plugin.getGuiManager().handleClick(player, holder, event.getRawSlot(),
                            event.isRightClick(), event.isShiftClick());
                }
            } else {
                event.setCancelled(!top && event.isShiftClick());
            }
            if (!event.isCancelled() && top) {
                if (event.getWhoClicked() instanceof Player player) {
                    scheduleLootSave(player, holder);
                }
            }
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof GuiHolder)) {
            return;
        }
        plugin.getGuiManager().handleClick(player, holder, event.getRawSlot(),
                event.isRightClick(), event.isShiftClick());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder) {
            if (event.getInventory().getHolder() instanceof GuiHolder holder
                    && holder.getMenu().equals("equipment")) {
                String allowed = holder.getData().getOrDefault("equipment-slots", "");
                event.setCancelled(event.getRawSlots().stream().anyMatch(slot ->
                        !java.util.Arrays.stream(allowed.split(","))
                                .anyMatch(value -> value.equals(String.valueOf(slot)))));
                if (!event.isCancelled()) {
                    scheduleEquipmentSave(holder);
                }
            } else if (event.getInventory().getHolder() instanceof GuiHolder holder
                    && holder.getMenu().equals("loot-editor")) {
                String allowed = holder.getData().getOrDefault("loot-input-slot", "-1");
                event.setCancelled(event.getRawSlots().stream().anyMatch(slot ->
                        !String.valueOf(slot).equals(allowed)));
                if (!event.isCancelled()) {
                    if (event.getWhoClicked() instanceof Player player) {
                        scheduleLootSave(player, holder);
                    }
                }
            } else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // Al abrir el siguiente menu se vuelve a iniciar el refresco si lo usa.
        if (event.getPlayer() instanceof Player player
                && event.getInventory().getHolder() instanceof GuiHolder) {
            plugin.getGuiManager().stopPlayerMenuRefresh(player);
        }
        if (event.getInventory().getHolder() instanceof GuiHolder holder
                && holder.getMenu().equals("equipment")) {
            plugin.getGuiManager().saveEquipment(holder);
        } else if (event.getInventory().getHolder() instanceof GuiHolder holder
                && holder.getMenu().equals("loot-editor")) {
            plugin.getGuiManager().addLootFromGui(holder);
        }
    }

    private void scheduleEquipmentSave(GuiHolder holder) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> plugin.getGuiManager().saveEquipment(holder));
    }

    private void scheduleLootSave(Player player, GuiHolder holder) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> plugin.getGuiManager().addLootFromGui(player, holder));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getGuiManager().hasPrompt(player)) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.getServer().getScheduler().runTask(plugin,
                () -> plugin.getGuiManager().acceptPrompt(player, message));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        // Un comando cancela el prompt de chat para no dejar el editor bloqueado.
        plugin.getGuiManager().clearPrompt(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getGuiManager().stopPlayerMenuRefresh(event.getPlayer());
        plugin.getGuiManager().clearPrompt(event.getPlayer());
    }
}
