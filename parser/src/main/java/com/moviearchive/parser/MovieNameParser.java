package com.moviearchive.parser;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a clean searchable movie title + year from a messy folder/file name.
 *
 * Core insight: whatever comes AFTER the year (quality tags, source tags,
 * release-group signatures) is discardable noise. We only need to isolate
 * the title and the year reliably; we do NOT need to enumerate every
 * possible release-group / quality token.
 */
public class MovieNameParser {

    // Known video file extensions we care about
    private static final Pattern VIDEO_EXT = Pattern.compile(
            "\\.(mkv|mp4|avi|mov|wmv|m4v|ts|m2ts)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern LNK_EXT = Pattern.compile("\\.lnk$", Pattern.CASE_INSENSITIVE);

    // Windows Explorer sometimes appends " - Shortcut" before .lnk
    private static final Pattern SHORTCUT_SUFFIX = Pattern.compile(
            "\\s*-\\s*Shortcut$", Pattern.CASE_INSENSITIVE);

    // Year: 1900-2099, optionally wrapped in parentheses
    private static final Pattern YEAR = Pattern.compile("\\(?((19|20)\\d{2})\\)?");

    // Trailing junk characters to trim off a cleaned title
    private static final Pattern TRAILING_PUNCT = Pattern.compile("[\\s._\\-:]+$");
    private static final Pattern LEADING_PUNCT = Pattern.compile("^[\\s._\\-:]+");

    // A parenthetical alt-title that sits directly before the year, e.g.
    // "La Caraa (The Hidden Face) (2011)"
    private static final Pattern TRAILING_PAREN = Pattern.compile("\\(([^()]+)\\)\\s*$");

    // Title glued to a trailing number with no space: "Winchester73" -> "Winchester 73"
    private static final Pattern GLUED_NUMBER = Pattern.compile("([A-Za-z])(\\d+)$");

    public record ParseResult(
            String rawName,
            String cleanTitle,
            Integer year,
            String alternateTitle,
            boolean isShortcut,
            boolean yearFound
    ) {}

    /**
     * Parses either a bare file name or a folder name representing a movie.
     */
    public ParseResult parse(String nameWithMaybeExtension) {
        String raw = nameWithMaybeExtension;
        String name = raw;

        boolean isShortcut = LNK_EXT.matcher(name).find();
        if (isShortcut) {
            name = LNK_EXT.matcher(name).replaceAll("");
            name = SHORTCUT_SUFFIX.matcher(name).replaceAll("");
        }

        // Strip video extension if present (folders won't have one)
        name = VIDEO_EXT.matcher(name).replaceAll("");

        // Normalize separators commonly used instead of spaces
        name = name.replace('_', ' ').replace('.', ' ');

        // Collapse multiple spaces
        name = name.replaceAll("\\s{2,}", " ").trim();

        Matcher yearMatcher = YEAR.matcher(name);
        Integer year = null;
        String beforeYear = name;
        boolean yearFound = false;

        if (yearMatcher.find()) {
            year = Integer.parseInt(yearMatcher.group(1));
            beforeYear = name.substring(0, yearMatcher.start());
            yearFound = true;
        }

        // Trim edges
        beforeYear = TRAILING_PUNCT.matcher(beforeYear).replaceAll("");
        beforeYear = LEADING_PUNCT.matcher(beforeYear).replaceAll("");

        // Check for a trailing alt-title in parens: "Title (Alt Title)"
        String alternateTitle = null;
        Matcher altMatcher = TRAILING_PAREN.matcher(beforeYear);
        if (altMatcher.find()) {
            alternateTitle = altMatcher.group(1).trim();
            beforeYear = beforeYear.substring(0, altMatcher.start());
            beforeYear = TRAILING_PUNCT.matcher(beforeYear).replaceAll("");
        }

        // Fix title glued to a number ("Winchester73" -> "Winchester 73")
        Matcher glued = GLUED_NUMBER.matcher(beforeYear);
        if (glued.find()) {
            beforeYear = glued.replaceAll("$1 $2");
        }

        beforeYear = beforeYear.replaceAll("\\s{2,}", " ").trim();

        return new ParseResult(raw, beforeYear, year, alternateTitle, isShortcut, yearFound);
    }

    public ParseResult parsePath(Path path) {
        return parse(path.getFileName().toString());
    }
}
