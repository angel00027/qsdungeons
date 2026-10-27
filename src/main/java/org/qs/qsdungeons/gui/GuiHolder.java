package org.qs.qsdungeons.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Holder de todos los menus del GUI. Guarda el contexto necesario para
 * responder a los clics sin depender de listeners por menu.
 */
public final class GuiHolder implements InventoryHolder {

    private final String menu;
    private final String dungeonId;
    private final String targetId;
    private final int page;
    private final Map<String, String> data = new HashMap<>();
    private final Map<Integer, String> actions = new HashMap<>();
    private Inventory inventory;

    public GuiHolder(String menu, String dungeonId, String targetId, int page) {
        this.menu = menu;
        this.dungeonId = dungeonId;
        this.targetId = targetId;
        this.page = Math.max(0, page);
    }

    public String getMenu() {
        return menu;
    }

    public String getDungeonId() {
        return dungeonId;
    }

    public String getTargetId() {
        return targetId;
    }

    public int getPage() {
        return page;
    }

    public Map<String, String> getData() {
        return data;
    }

    public Map<Integer, String> getActions() {
        return actions;
    }

    public void bind(int slot, String action) {
        if (slot >= 0) {
            actions.put(slot, action);
        }
    }

    public String actionAt(int slot) {
        return actions.get(slot);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
