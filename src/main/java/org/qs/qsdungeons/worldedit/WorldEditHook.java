package org.qs.qsdungeons.worldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.dungeon.Dungeon;
import org.qs.qsdungeons.dungeon.Region;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Puente opcional con WorldEdit para guardar y pegar estructuras.
 *
 * <p>La clase solo se instancia cuando WorldEdit (o FastAsyncWorldEdit) esta
 * presente. Si no lo esta, el resto del plugin sigue funcionando y los
 * comandos de transferencia avisan al usuario.</p>
 */
public final class WorldEditHook {

    private final QsDungeons plugin;
    private final boolean available;

    public WorldEditHook(QsDungeons plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().isPluginEnabled("WorldEdit")
                || Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Copia la region de la dungeon a un archivo de estructura dentro de la
     * carpeta schematics de esa dungeon.
     */
    public File saveStructure(Player player, Dungeon dungeon, String rawName, boolean includeEntities)
            throws Exception {
        ensureAvailable();
        if (dungeon == null) {
            throw new IllegalArgumentException("dungeon");
        }
        Region region = dungeon.getRegion();
        if (region == null) {
            throw new IllegalStateException("La dungeon no tiene region configurada.");
        }
        org.bukkit.World bukkitWorld = region.getBukkitWorld();
        if (bukkitWorld == null) {
            throw new IllegalStateException("El mundo de la region no esta cargado.");
        }

        String name = sanitizeName(rawName);
        File folder = plugin.getRepository().getSchematicsFolder(dungeon);
        File target = new File(folder, name + ".schem");
        if (target.exists() && !target.delete()) {
            throw new IOException("No se pudo reemplazar " + target.getName());
        }

        World world = BukkitAdapter.adapt(bukkitWorld);
        BlockVector3 min = BlockVector3.at(region.getMinX(), region.getMinY(), region.getMinZ());
        BlockVector3 max = BlockVector3.at(region.getMaxX(), region.getMaxY(), region.getMaxZ());
        CuboidRegion cuboid = new CuboidRegion(world, min, max);

        BlockArrayClipboard clipboard = new BlockArrayClipboard(cuboid);
        clipboard.setOrigin(min);
        ForwardExtentCopy copy = new ForwardExtentCopy(world, cuboid, clipboard, min);
        copy.setCopyingEntities(includeEntities);
        copy.setCopyingBiomes(false);
        Operations.complete(copy);

        ClipboardFormat format = ClipboardFormats.findByFile(target);
        if (format == null) {
            format = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC;
        }
        try (ClipboardWriter writer = format.getWriter(new FileOutputStream(target))) {
            writer.write(clipboard);
        }
        return target;
    }

    /**
     * Pega una estructura en la ubicacion actual del jugador.
     */
    public File paste(Player player, Dungeon dungeon, String rawName, boolean ignoreAir) throws Exception {
        ensureAvailable();
        if (player == null) {
            throw new IllegalArgumentException("player");
        }
        File file = findStructure(dungeon, rawName);
        if (file == null) {
            throw new FileNotFoundException(rawName);
        }
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            throw new IOException("Formato de estructura desconocido: " + file.getName());
        }

        Clipboard clipboard;
        try (InputStream input = new FileInputStream(file);
             ClipboardReader reader = format.getReader(input)) {
            clipboard = reader.read();
        }

        Location location = player.getLocation();
        BlockVector3 to = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        try (EditSession session = WorldEdit.getInstance()
                .newEditSessionBuilder()
                .world(BukkitAdapter.adapt(player.getWorld()))
                .maxBlocks(-1)
                .build()) {
            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(session)
                    .to(to)
                    .ignoreAirBlocks(ignoreAir)
                    .copyEntities(plugin.getConfigManager().includeEntities())
                    .build();
            Operations.complete(operation);
        }
        return file;
    }

    /**
     * Busca una estructura por nombre en la carpeta schematics de la dungeon.
     */
    public File findStructure(Dungeon dungeon, String rawName) {
        if (dungeon == null || rawName == null || rawName.isBlank()) {
            return null;
        }
        File folder = plugin.getRepository().getSchematicsFolder(dungeon);
        String name = rawName.trim();
        File direct = new File(folder, name);
        if (direct.isFile()) {
            return direct;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".schem") || lower.endsWith(".schematic") || lower.endsWith(".nbt")) {
            return null;
        }
        List<String> extensions = new ArrayList<>(List.of(".schem", ".schematic", ".nbt"));
        for (String extension : extensions) {
            File candidate = new File(folder, name + extension);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Lista las estructuras guardadas de una dungeon, sin extension.
     */
    public List<String> listStructures(Dungeon dungeon) {
        List<String> result = new ArrayList<>();
        if (dungeon == null) {
            return result;
        }
        File[] files = plugin.getRepository().getSchematicsFolder(dungeon)
                .listFiles(file -> file.isFile() && isStructure(file.getName()));
        if (files == null) {
            return result;
        }
        for (File file : files) {
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            result.add(dot > 0 ? name.substring(0, dot) : name);
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public void giveWand(Player player) {
        ensureAvailable();
        Material material = Material.matchMaterial(WorldEdit.getInstance().getConfiguration().wandItem);
        if (material == null || !material.isItem()) {
            material = Material.WOODEN_AXE;
        }
        player.getInventory().addItem(new ItemStack(material));
    }

    private void ensureAvailable() {
        if (!available) {
            throw new IllegalStateException(plugin.getMessages().raw("general.worldedit-missing"));
        }
    }

    private static boolean isStructure(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".schem") || lower.endsWith(".schematic") || lower.endsWith(".nbt");
    }

    public static String sanitizeName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        name = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        if (name.isBlank()) {
            name = "estructura";
        }
        if (name.length() > 48) {
            name = name.substring(0, 48);
        }
        return name;
    }
}
