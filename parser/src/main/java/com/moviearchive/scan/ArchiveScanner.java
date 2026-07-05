package com.moviearchive.scan;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Walks an archive folder - to ANY depth - and finds "movie units": either
 *   (a) a video file sitting directly in a folder (parse the FILE name), or
 *   (b) a folder that directly contains EXACTLY ONE video file (parse the
 *       FOLDER name - usually cleaner, e.g. "Lucky Trouble (2011) 720p").
 *
 * The "exactly one" part matters: a folder can also be a flat category/
 * collection folder with MANY loose movies dropped directly inside it (e.g.
 * "هندی/" holding 130+ .mkv files side by side, no per-movie subfolders).
 * Such a folder must NOT be treated as a single movie (which would silently
 * discard everything but one file) - it's recursed into instead, exactly
 * like any other folder, so each loose video inside becomes its own unit.
 * Folders with zero videos directly inside (pure genre/category folders)
 * are recursed into the same way, to any depth.
 *
 * A lone "sample" file (name contains "sample") next to the real movie file
 * is ignored when counting, so a folder with a real movie + a sample clip
 * still correctly counts as "one video" rather than being misread as a
 * flat collection.
 *
 * Resilient by design: if one subfolder can't be read (permission denied,
 * a broken link, an unusual/garbled name, etc.), that subfolder is skipped
 * and reported through the optional warning callback - it does NOT abort
 * scanning the rest of the tree.
 *
 * .lnk shortcuts are reported separately so the caller can decide how to
 * handle them (skip, or resolve target).
 */
public class ArchiveScanner {

    private static final Pattern VIDEO_EXT = Pattern.compile(
            "\\.(mkv|mp4|avi|mov|wmv|m4v|ts|m2ts|flv|webm|mpg|mpeg|vob|3gp|rmvb|divx|iso)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LNK_EXT = Pattern.compile("\\.lnk$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SAMPLE_NAME = Pattern.compile("\\bsample\\b", Pattern.CASE_INSENSITIVE);

    // Safety net against pathological cases (e.g. a circular symlink) - not a realistic limit for actual archives.
    private static final int MAX_DEPTH = 25;

    public sealed interface MovieUnit permits FileUnit, FolderUnit, ShortcutUnit {}

    /** A loose video file directly under some folder - use the file name. */
    public record FileUnit(Path videoFile) implements MovieUnit {}

    /** A folder that directly contains exactly one video file - use the folder name. */
    public record FolderUnit(Path folder, Path videoFile) implements MovieUnit {}

    /** A Windows shortcut found instead of a real file - flagged, not parsed. */
    public record ShortcutUnit(Path lnkFile) implements MovieUnit {}

    private Consumer<String> onWarning = msg -> { };

    /** Optional: called with a short message whenever a folder is skipped due to an error, so the caller can surface it. */
    public void setOnWarning(Consumer<String> onWarning) {
        this.onWarning = onWarning != null ? onWarning : (msg -> { });
    }

    public List<MovieUnit> scan(Path root) {
        List<MovieUnit> units = new ArrayList<>();
        scanDirectory(root, units, 0);
        return units;
    }

    /**
     * Handles the immediate contents of `dir`: loose video files and
     * shortcuts here always become their own units (this is what correctly
     * handles both a true archive root AND a flat "category" folder full of
     * loose movies - both are just "a directory with loose video files in
     * it" as far as this block is concerned). Sub-folders are then
     * evaluated one by one: exactly one direct video makes a sub-folder a
     * dedicated movie folder; anything else (zero, or several) means it
     * isn't, so we recurse into it with this same logic instead.
     */
    private void scanDirectory(Path dir, List<MovieUnit> units, int depth) {
        if (depth > MAX_DEPTH) {
            onWarning.accept("رد شد (عمق بیش‌ازحد، احتمال لینک حلقوی): " + dir);
            return;
        }

        List<Path> subdirs = new ArrayList<>();

        try (Stream<Path> entries = Files.list(dir)) {
            for (Path entry : entries.toList()) {
                if (Files.isDirectory(entry)) {
                    subdirs.add(entry);
                } else if (LNK_EXT.matcher(entry.getFileName().toString()).find()) {
                    units.add(new ShortcutUnit(entry));
                } else if (VIDEO_EXT.matcher(entry.getFileName().toString()).find()) {
                    units.add(new FileUnit(entry));
                }
            }
        } catch (Exception e) {
            // Deliberately broad: Files.list()/toList() can throw things that
            // do NOT extend IOException (e.g. DirectoryIteratorException, or
            // encoding-related errors triggered by unusual Unicode names -
            // Persian, Finglish, emoji, etc. in folder names). One bad
            // folder must never abort scanning the rest of the tree.
            onWarning.accept("رد شد (قابل خواندن نبود): " + dir + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }

        for (Path sub : subdirs) {
            DirListing listing;
            try {
                listing = listDirectly(sub);
            } catch (Exception e) {
                onWarning.accept("رد شد (قابل خواندن نبود): " + sub + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
                continue;
            }

            // Ignore an obvious "sample" clip when deciding whether this
            // folder holds exactly one real movie.
            List<Path> withoutSamples = listing.videos().stream()
                    .filter(p -> !SAMPLE_NAME.matcher(p.getFileName().toString()).find())
                    .toList();
            List<Path> effective = withoutSamples.isEmpty() ? listing.videos() : withoutSamples;

            if (effective.size() == 1 && listing.subdirs().isEmpty()) {
                // Exactly one movie in here, and nothing else to explore -
                // this is a proper dedicated movie folder.
                units.add(new FolderUnit(sub, effective.get(0)));
            } else {
                // Any of: zero videos (pure category folder), several videos
                // (a flat collection of loose movies, like "هندی/" with 130+
                // files side by side), or - importantly - a folder that has
                // BOTH loose video(s) AND further subfolders (like the real
                // archive root itself, which has a handful of loose movies
                // plus "هندی/"، "خارجی/" category subfolders). In every one
                // of these cases we must recurse: the block above already
                // handles picking up any loose videos here as individual
                // units, and subfolders keep getting explored to any depth.
                scanDirectory(sub, units, depth + 1);
            }
        }
    }

    private record DirListing(List<Path> videos, List<Path> subdirs) {}

    /** Video files AND subdirectories sitting directly inside `folder` (one listing, so we don't read the directory twice). */
    private DirListing listDirectly(Path folder) throws IOException {
        List<Path> videos = new ArrayList<>();
        List<Path> subdirs = new ArrayList<>();
        try (Stream<Path> entries = Files.list(folder)) {
            for (Path entry : entries.toList()) {
                if (Files.isDirectory(entry)) {
                    subdirs.add(entry);
                } else if (VIDEO_EXT.matcher(entry.getFileName().toString()).find()) {
                    videos.add(entry);
                }
            }
        }
        return new DirListing(videos, subdirs);
    }
}