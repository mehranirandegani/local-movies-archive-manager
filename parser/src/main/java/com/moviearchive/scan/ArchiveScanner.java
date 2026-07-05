package com.moviearchive.scan;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Walks a root archive folder and finds "movie units": either
 *   (a) a video file sitting directly in a folder (parse the FILE name), or
 *   (b) a folder whose only meaningful content is one video file
 *       (parse the FOLDER name - usually cleaner, see samples like
 *        "Lucky Trouble (2011) 720p").
 *
 * .lnk shortcuts are reported separately so the caller can decide how to
 * handle them (skip, or resolve target).
 */
public class ArchiveScanner {

    private static final Pattern VIDEO_EXT = Pattern.compile(
            "\\.(mkv|mp4|avi|mov|wmv|m4v|ts|m2ts)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LNK_EXT = Pattern.compile("\\.lnk$", Pattern.CASE_INSENSITIVE);

    public sealed interface MovieUnit permits FileUnit, FolderUnit, ShortcutUnit {}

    /** A loose video file directly under the scan root - use the file name. */
    public record FileUnit(Path videoFile) implements MovieUnit {}

    /** A folder containing (typically) one video file - use the folder name. */
    public record FolderUnit(Path folder, Path videoFile) implements MovieUnit {}

    /** A Windows shortcut found instead of a real file - flagged, not parsed. */
    public record ShortcutUnit(Path lnkFile) implements MovieUnit {}

    public List<MovieUnit> scan(Path root) throws IOException {
        List<MovieUnit> units = new ArrayList<>();
        try (Stream<Path> entries = Files.list(root)) {
            for (Path entry : entries.toList()) {
                if (Files.isDirectory(entry)) {
                    findVideoInFolder(entry).ifPresentOrElse(
                            video -> units.add(new FolderUnit(entry, video)),
                            () -> { /* folder with no video - skip, e.g. extras-only folder */ }
                    );
                } else if (LNK_EXT.matcher(entry.getFileName().toString()).find()) {
                    units.add(new ShortcutUnit(entry));
                } else if (VIDEO_EXT.matcher(entry.getFileName().toString()).find()) {
                    units.add(new FileUnit(entry));
                }
            }
        }
        return units;
    }

    private java.util.Optional<Path> findVideoInFolder(Path folder) throws IOException {
        try (Stream<Path> files = Files.walk(folder, 2)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> VIDEO_EXT.matcher(p.getFileName().toString()).find())
                    // prefer the largest file, in case of sample.mkv alongside the real one
                    .max((a, b) -> {
                        try {
                            return Long.compare(Files.size(a), Files.size(b));
                        } catch (IOException e) {
                            return 0;
                        }
                    });
        }
    }
}
