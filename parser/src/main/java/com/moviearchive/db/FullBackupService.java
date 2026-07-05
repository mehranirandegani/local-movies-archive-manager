package com.moviearchive.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * The JSON export in ArchiveExporter is great for reading/diffing, but it
 * only stores local poster file *paths* - useless after a reinstall or on
 * another machine. This class makes a true, self-contained backup: the
 * SQLite database file plus every cached poster image, all in one .zip.
 * Restoring it means zero TMDB calls are needed again.
 */
public class FullBackupService {

    private static final String DB_ENTRY = "archive.db";
    private static final String POSTERS_PREFIX = "posters/";

    public void exportFull(Path dbFile, Path postersDir, Path outputZip) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputZip))) {
            addFile(zos, dbFile, DB_ENTRY);

            if (Files.isDirectory(postersDir)) {
                try (Stream<Path> files = Files.walk(postersDir)) {
                    for (Path p : files.filter(Files::isRegularFile).toList()) {
                        String entryName = POSTERS_PREFIX + postersDir.relativize(p).toString().replace('\\', '/');
                        addFile(zos, p, entryName);
                    }
                }
            }
        }
    }

    private void addFile(ZipOutputStream zos, Path file, String entryName) throws IOException {
        if (!Files.exists(file)) return;
        zos.putNextEntry(new ZipEntry(entryName));
        Files.copy(file, zos);
        zos.closeEntry();
    }

    /**
     * Restores a full backup, OVERWRITING the current database file and
     * poster cache at the given locations. Callers must close any open
     * MovieRepository connection to targetDbFile before calling this, and
     * reopen it afterward.
     */
    public void importFull(Path zipFile, Path targetDbFile, Path targetPostersDir) throws IOException {
        Files.createDirectories(targetPostersDir);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                Path target;
                if (entry.getName().equals(DB_ENTRY)) {
                    target = targetDbFile;
                } else if (entry.getName().startsWith(POSTERS_PREFIX)) {
                    target = targetPostersDir.resolve(entry.getName().substring(POSTERS_PREFIX.length()));
                } else {
                    continue; // unknown entry from a future format - skip rather than fail
                }

                Files.createDirectories(target.getParent());
                Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                zis.closeEntry();
            }
        }
    }
}
