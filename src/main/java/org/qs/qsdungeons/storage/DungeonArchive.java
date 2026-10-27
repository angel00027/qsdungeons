package org.qs.qsdungeons.storage;

import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.dungeon.Dungeon;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Exporta e importa dungeons completas como archivos .zip y estructuras de
 * WorldEdit. Todas las rutas se validan para evitar salir de la carpeta
 * destino (zip slip).
 */
public final class DungeonArchive {

    private final QsDungeons plugin;

    public DungeonArchive(QsDungeons plugin) {
        this.plugin = plugin;
    }

    /**
     * Comprime la carpeta completa de una dungeon.
     */
    public File exportConfig(Dungeon dungeon, String rawName) throws IOException {
        if (dungeon == null || dungeon.getFolder() == null) {
            throw new IllegalArgumentException("La dungeon no tiene carpeta.");
        }
        String name = rawName == null || rawName.isBlank()
                ? dungeon.getId() + "-config"
                : rawName.trim();
        File target = uniqueExportFile(name);
        zipFolder(dungeon.getFolder(), target);
        return target;
    }

    /**
     * Comprime la carpeta de la dungeon incluyendo las estructuras guardadas.
     */
    public File exportAll(Dungeon dungeon, String rawName) throws IOException {
        if (dungeon == null || dungeon.getFolder() == null) {
            throw new IllegalArgumentException("La dungeon no tiene carpeta.");
        }
        String name = rawName == null || rawName.isBlank()
                ? dungeon.getId() + "-all"
                : rawName.trim();
        File target = uniqueExportFile(name);
        zipFolder(dungeon.getFolder(), target);
        return target;
    }

    /**
     * Importa un .zip de configuracion en la carpeta de la dungeon indicada.
     *
     * @return carpeta final de la dungeon.
     */
    public File importConfig(String id, File archive) throws IOException {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id");
        }
        if (archive == null || !archive.isFile()) {
            throw new IOException("Archivo no encontrado: " + (archive == null ? "?" : archive.getName()));
        }
        File target = plugin.getRepository().getFolder(id);
        if (target.exists() && new File(target, "dungeon.yml").isFile()) {
            throw new IOException("La dungeon '" + id + "' ya existe. Borrala antes de importar.");
        }

        Path temp = Files.createTempDirectory(plugin.getDataFolder().toPath(), "import-");
        try {
            unzip(archive, temp.toFile());
            File root = findDungeonRoot(temp.toFile());
            if (root == null) {
                throw new IOException("El .zip no contiene un archivo dungeon.yml.");
            }
            copyDirectory(root, target);
            return target;
        } finally {
            deleteRecursively(temp.toFile());
        }
    }

    /**
     * Copia una estructura externa a la carpeta schematics de la dungeon.
     */
    public File importStructure(Dungeon dungeon, File source) throws IOException {
        if (dungeon == null || source == null || !source.isFile()) {
            throw new IOException("Archivo de estructura no encontrado.");
        }
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String extension = dot > 0 ? name.substring(dot) : ".schem";
        String base = dot > 0 ? name.substring(0, dot) : name;
        File folder = plugin.getRepository().getSchematicsFolder(dungeon);
        File target = new File(folder, WorldEditHookNames.sanitize(base) + extension);
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    /**
     * Busca un archivo por ruta directa, en exports/ o en la carpeta del plugin.
     */
    public File resolveFile(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        File direct = new File(raw);
        if (direct.isFile()) {
            return direct;
        }
        File inExports = new File(plugin.getRepository().getExportsFolder(), raw);
        if (inExports.isFile()) {
            return inExports;
        }
        File inData = new File(plugin.getDataFolder(), raw);
        if (inData.isFile()) {
            return inData;
        }
        return null;
    }

    /**
     * Ruta corta y legible para mostrar en mensajes.
     */
    public String displayPath(File file) {
        if (file == null) {
            return "?";
        }
        try {
            Path base = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
            Path path = file.toPath().toAbsolutePath().normalize();
            if (path.startsWith(base)) {
                return base.relativize(path).toString().replace('\\', '/');
            }
        } catch (Exception ignored) {
            // Se devuelve la ruta completa.
        }
        return file.getAbsolutePath();
    }

    public List<String> listExports() {
        List<String> result = new ArrayList<>();
        File[] files = plugin.getRepository().getExportsFolder().listFiles(File::isFile);
        if (files == null) {
            return result;
        }
        for (File file : files) {
            result.add(file.getName());
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    // ------------------------------------------------------------------
    // Internos
    // ------------------------------------------------------------------

    private File uniqueExportFile(String rawName) {
        String name = rawName.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
        if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            name = name + ".zip";
        }
        File folder = plugin.getRepository().getExportsFolder();
        File file = new File(folder, name);
        if (!file.exists()) {
            return file;
        }
        String base = name.substring(0, name.length() - 4);
        int counter = 1;
        while (file.exists()) {
            file = new File(folder, base + "-" + counter + ".zip");
            counter++;
        }
        return file;
    }

    private void zipFolder(File source, File target) throws IOException {
        if (!source.isDirectory()) {
            throw new IOException("La carpeta de la dungeon no existe.");
        }
        Path base = source.toPath().toAbsolutePath().normalize();
        try (ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(target)))) {
            Files.walk(base)
                    .sorted(Comparator.naturalOrder())
                    .filter(path -> !path.equals(base))
                    .forEach(path -> {
                        String entryName = base.relativize(path).toString().replace('\\', '/');
                        try {
                            if (Files.isDirectory(path)) {
                                output.putNextEntry(new ZipEntry(entryName + "/"));
                                output.closeEntry();
                                return;
                            }
                            output.putNextEntry(new ZipEntry(entryName));
                            Files.copy(path, output);
                            output.closeEntry();
                        } catch (IOException exception) {
                            throw new ArchiveException(exception);
                        }
                    });
        } catch (ArchiveException exception) {
            throw exception.io;
        }
    }

    private void unzip(File archive, File destination) throws IOException {
        long maxBytes = plugin.getConfigManager().getMaxUnzipBytes();
        long total = 0L;
        Path base = destination.toPath().toAbsolutePath().normalize();
        try (ZipInputStream input = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = input.getNextEntry()) != null) {
                Path target = base.resolve(entry.getName()).normalize();
                if (!target.startsWith(base)) {
                    throw new IOException("Entrada de zip invalida: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (OutputStream output = new BufferedOutputStream(new FileOutputStream(target.toFile()))) {
                    int read;
                    while ((read = input.read(buffer)) > 0) {
                        total += read;
                        if (maxBytes > 0 && total > maxBytes) {
                            throw new IOException("El .zip supera el limite de descompresion.");
                        }
                        output.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private File findDungeonRoot(File folder) {
        if (new File(folder, "dungeon.yml").isFile()) {
            return folder;
        }
        File[] children = folder.listFiles(File::isDirectory);
        if (children == null) {
            return null;
        }
        for (File child : children) {
            File found = findDungeonRoot(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void copyDirectory(File source, File target) throws IOException {
        Path sourcePath = source.toPath().toAbsolutePath().normalize();
        Path targetPath = target.toPath().toAbsolutePath().normalize();
        Files.createDirectories(targetPath);
        try (var stream = Files.walk(sourcePath)) {
            List<Path> paths = stream.sorted(Comparator.naturalOrder()).toList();
            for (Path path : paths) {
                if (path.equals(sourcePath)) {
                    continue;
                }
                Path destination = targetPath.resolve(sourcePath.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        try (var stream = Files.walk(file.toPath())) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Limpieza best-effort.
                }
            });
        } catch (IOException ignored) {
            // Limpieza best-effort.
        }
    }

    /**
     * Pequena clase interna para poder propagar IOException desde lambdas.
     */
    private static final class ArchiveException extends RuntimeException {
        private final IOException io;

        private ArchiveException(IOException io) {
            this.io = io;
        }
    }

    /**
     * Evita acoplar DungeonArchive a WorldEdit solo para sanear nombres.
     */
    private static final class WorldEditHookNames {
        private static String sanitize(String raw) {
            String name = raw == null ? "" : raw.trim();
            name = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            return name.isBlank() ? "estructura" : name;
        }
    }
}
