package com.moviearchive.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.moviearchive.model.Movie;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Exports/imports the full archive as portable JSON.
 * Two backup strategies are supported by design:
 *  1. Copy the raw .db file (fastest, byte-for-byte, includes everything)
 *  2. Export to JSON (human-readable, diffable, safe across schema versions)
 * The poster/backdrop image files themselves live under a `posters/` folder
 * next to the database and should be zipped up alongside either backup.
 */
public class ArchiveExporter {

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public record ArchiveExport(String formatVersion, long exportedAt, List<Movie> movies) {}

    public void exportToJson(List<Movie> movies, Path outFile) throws IOException {
        ArchiveExport export = new ArchiveExport("1.0", System.currentTimeMillis(), movies);
        mapper.writeValue(outFile.toFile(), export);
    }

    public ArchiveExport importFromJson(Path inFile) throws IOException {
        return mapper.readValue(inFile.toFile(), ArchiveExport.class);
    }
}
