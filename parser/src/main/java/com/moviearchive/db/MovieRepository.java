package com.moviearchive.db;

import com.moviearchive.model.Movie;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Local SQLite-backed cache/repository for the archive.
 * This is the piece that guarantees "once fetched, never fetched again":
 * every lookup checks here first before ever touching TMDB.
 */
public class MovieRepository implements AutoCloseable {

    private final Connection conn;

    public MovieRepository(Path dbFile) throws SQLException, IOException {
        String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        this.conn = DriverManager.getConnection(url);
        initSchema();
    }

    private void initSchema() throws SQLException, IOException {
        String schema = loadSchemaSql();
        try (Statement st = conn.createStatement()) {
            for (String stmt : schema.split(";")) {
                if (!stmt.isBlank()) {
                    st.execute(stmt);
                }
            }
        }
        runMigrations();
    }

    /**
     * Lightweight forward-only migration: adds any columns that a schema
     * update introduced but an existing (already-created) database file
     * doesn't have yet. Safe to run every startup - each ALTER TABLE is
     * a no-op (caught and ignored) once the column already exists.
     */
    private void runMigrations() throws SQLException {
        addColumnIfMissing("director", "TEXT");
        addColumnIfMissing("cast_names", "TEXT");
        addColumnIfMissing("trailer_url", "TEXT");
        addColumnIfMissing("watched", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("favorite", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("country", "TEXT");
        addColumnIfMissing("popularity", "REAL");
        addColumnIfMissing("certification", "TEXT");
        addColumnIfMissing("imdb_id", "TEXT");
        addColumnIfMissing("overview_fa", "TEXT");
        addColumnIfMissing("country_fa", "TEXT");
    }

    private void addColumnIfMissing(String column, String type) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE movies ADD COLUMN " + column + " " + type);
        } catch (SQLException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (!msg.contains("duplicate column")) {
                throw e;
            }
        }
    }

    private String loadSchemaSql() throws IOException {
        try (var in = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
            if (in == null) throw new IOException("schema.sql not found on classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Returns true if this file path is already in the archive (cache hit). */
    public boolean existsByFilePath(String filePath) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM movies WHERE file_path = ?")) {
            ps.setString(1, filePath);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Looks up a cached movie by TMDB id, so re-scans never re-fetch metadata. */
    public Movie findByTmdbId(int tmdbId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM movies WHERE tmdb_id = ? LIMIT 1")) {
            ps.setInt(1, tmdbId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Movie m = mapRowWithoutTags(rs);
                m.getTags().addAll(loadTags(m.getId()));
                return m;
            }
        }
    }

    public long insert(Movie m) throws SQLException {
        String sql = """
            INSERT INTO movies
            (file_path, raw_name, parsed_title, parsed_year, tmdb_id, title,
             original_title, release_year, overview, overview_fa, vote_average, vote_count,
             runtime_minutes, poster_path, backdrop_path, match_status,
             director, cast_names, trailer_url, country, country_fa, popularity, certification,
             imdb_id, watched, favorite, date_added, last_updated)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, m.getFilePath());
            ps.setString(2, m.getRawName());
            ps.setString(3, m.getParsedTitle());
            setNullableInt(ps, 4, m.getParsedYear());
            setNullableInt(ps, 5, m.getTmdbId());
            ps.setString(6, m.getTitle());
            ps.setString(7, m.getOriginalTitle());
            setNullableInt(ps, 8, m.getReleaseYear());
            ps.setString(9, m.getOverview());
            ps.setString(10, m.getOverviewFa());
            if (m.getVoteAverage() != null) ps.setDouble(11, m.getVoteAverage()); else ps.setNull(11, Types.REAL);
            setNullableInt(ps, 12, m.getVoteCount());
            setNullableInt(ps, 13, m.getRuntimeMinutes());
            ps.setString(14, m.getPosterPath());
            ps.setString(15, m.getBackdropPath());
            ps.setString(16, m.getMatchStatus().name());
            ps.setString(17, m.getDirector());
            ps.setString(18, joinCast(m.getCast()));
            ps.setString(19, m.getTrailerUrl());
            ps.setString(20, m.getCountry());
            ps.setString(21, m.getCountryFa());
            if (m.getPopularity() != null) ps.setDouble(22, m.getPopularity()); else ps.setNull(22, Types.REAL);
            ps.setString(23, m.getCertification());
            ps.setString(24, m.getImdbId());
            ps.setInt(25, m.isWatched() ? 1 : 0);
            ps.setInt(26, m.isFavorite() ? 1 : 0);
            ps.setLong(27, now);
            ps.setLong(28, now);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                saveTags(id, m.getTags());
                return id;
            }
        }
    }

    private static final String CAST_SEPARATOR = " | ";

    private String joinCast(List<String> cast) {
        return (cast == null || cast.isEmpty()) ? null : String.join(CAST_SEPARATOR, cast);
    }

    private List<String> splitCast(String stored) {
        if (stored == null || stored.isBlank()) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (String part : stored.split("\\s*\\|\\s*")) {
            if (!part.isBlank()) result.add(part.trim());
        }
        return result;
    }

    private void saveTags(long movieId, List<String> tags) throws SQLException {
        for (String tag : tags) {
            long tagId = upsertTag(tag);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO movie_tags (movie_id, tag_id) VALUES (?,?)")) {
                ps.setLong(1, movieId);
                ps.setLong(2, tagId);
                ps.executeUpdate();
            }
        }
    }

    private long upsertTag(String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO tags (name) VALUES (?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM tags WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /**
     * Updates an existing row (used after a manual re-match via ReviewDialog,
     * or any future re-scan that refreshes metadata). Matches by file_path,
     * which is the natural unique key for a movie unit in the archive.
     */
    public void update(Movie m) throws SQLException {
        String sql = """
            UPDATE movies SET
                parsed_title = ?, parsed_year = ?, tmdb_id = ?, title = ?,
                original_title = ?, release_year = ?, overview = ?, overview_fa = ?, vote_average = ?,
                vote_count = ?, runtime_minutes = ?, poster_path = ?, backdrop_path = ?,
                match_status = ?, director = ?, cast_names = ?, trailer_url = ?,
                country = ?, country_fa = ?, popularity = ?, certification = ?, imdb_id = ?,
                watched = ?, favorite = ?, last_updated = ?
            WHERE file_path = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, m.getParsedTitle());
            setNullableInt(ps, 2, m.getParsedYear());
            setNullableInt(ps, 3, m.getTmdbId());
            ps.setString(4, m.getTitle());
            ps.setString(5, m.getOriginalTitle());
            setNullableInt(ps, 6, m.getReleaseYear());
            ps.setString(7, m.getOverview());
            ps.setString(8, m.getOverviewFa());
            if (m.getVoteAverage() != null) ps.setDouble(9, m.getVoteAverage()); else ps.setNull(9, Types.REAL);
            setNullableInt(ps, 10, m.getVoteCount());
            setNullableInt(ps, 11, m.getRuntimeMinutes());
            ps.setString(12, m.getPosterPath());
            ps.setString(13, m.getBackdropPath());
            ps.setString(14, m.getMatchStatus().name());
            ps.setString(15, m.getDirector());
            ps.setString(16, joinCast(m.getCast()));
            ps.setString(17, m.getTrailerUrl());
            ps.setString(18, m.getCountry());
            ps.setString(19, m.getCountryFa());
            if (m.getPopularity() != null) ps.setDouble(20, m.getPopularity()); else ps.setNull(20, Types.REAL);
            ps.setString(21, m.getCertification());
            ps.setString(22, m.getImdbId());
            ps.setInt(23, m.isWatched() ? 1 : 0);
            ps.setInt(24, m.isFavorite() ? 1 : 0);
            ps.setLong(25, System.currentTimeMillis());
            ps.setString(26, m.getFilePath());
            ps.executeUpdate();
        }

        // Refresh tags: clear old links then re-save current tag list.
        try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM movie_tags WHERE movie_id = (SELECT id FROM movies WHERE file_path = ?)")) {
            del.setString(1, m.getFilePath());
            del.executeUpdate();
        }
        try (PreparedStatement idQuery = conn.prepareStatement(
                "SELECT id FROM movies WHERE file_path = ?")) {
            idQuery.setString(1, m.getFilePath());
            try (ResultSet rs = idQuery.executeQuery()) {
                if (rs.next()) {
                    saveTags(rs.getLong(1), m.getTags());
                }
            }
        }
    }

    /**
     * Loads every movie. Tags are batched via a single JOIN query instead of
     * one extra query per row - at a few thousand movies, the old per-row
     * approach meant a few thousand extra round-trips, which is the kind of
     * thing that turns "loads instantly" into "takes forever".
     */
    public List<Movie> findAll() throws SQLException {
        Map<Long, List<String>> tagsByMovie = loadAllTagsGrouped();
        List<Movie> result = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM movies ORDER BY title")) {
            while (rs.next()) {
                Movie m = mapRowWithoutTags(rs);
                m.getTags().addAll(tagsByMovie.getOrDefault(m.getId(), List.of()));
                result.add(m);
            }
        }
        return result;
    }

    public List<Movie> findNeedingReview() throws SQLException {
        return findByStatuses(Movie.MatchStatus.NEEDS_REVIEW, Movie.MatchStatus.NOT_FOUND, Movie.MatchStatus.PENDING);
    }

    /** Fetches movies matching any of the given statuses - used to build the review queue. */
    public List<Movie> findByStatuses(Movie.MatchStatus... statuses) throws SQLException {
        String placeholders = String.join(",", java.util.Collections.nCopies(statuses.length, "?"));
        String sql = "SELECT * FROM movies WHERE match_status IN (" + placeholders + ") ORDER BY raw_name";
        Map<Long, List<String>> tagsByMovie = loadAllTagsGrouped();
        List<Movie> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < statuses.length; i++) {
                ps.setString(i + 1, statuses[i].name());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Movie m = mapRowWithoutTags(rs);
                    m.getTags().addAll(tagsByMovie.getOrDefault(m.getId(), List.of()));
                    result.add(m);
                }
            }
        }
        return result;
    }

    /** All distinct tag names currently in the archive, for the sidebar filter list. */
    public List<String> findAllTagNames() throws SQLException {
        List<String> tags = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM tags ORDER BY name COLLATE NOCASE")) {
            while (rs.next()) tags.add(rs.getString(1));
        }
        return tags;
    }

    // --- Library folders (multi-folder support) ---

    public void addScanPath(String path) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO scan_paths (path, last_scanned) VALUES (?, NULL)")) {
            ps.setString(1, path);
            ps.executeUpdate();
        }
    }

    public void removeScanPath(String path) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM scan_paths WHERE path = ?")) {
            ps.setString(1, path);
            ps.executeUpdate();
        }
    }

    public List<String> listScanPaths() throws SQLException {
        List<String> paths = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT path FROM scan_paths ORDER BY path")) {
            while (rs.next()) paths.add(rs.getString(1));
        }
        return paths;
    }

    public void touchScanPath(String path) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE scan_paths SET last_scanned = ? WHERE path = ?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, path);
            ps.executeUpdate();
        }
    }

    /**
     * Distinct countries present in the archive, as English name -> Persian
     * name (falls back to the English name itself if no Persian translation
     * was ever fetched for that country). Used to build a bilingual filter
     * dropdown while filtering logic itself keeps matching on the English name.
     */
    public java.util.Map<String, String> findAllCountries() throws SQLException {
        java.util.Map<String, String> result = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT country, country_fa FROM movies WHERE country IS NOT NULL AND country <> ''")) {
            while (rs.next()) {
                String[] en = rs.getString(1).split("\\s*,\\s*");
                String faRaw = rs.getString(2);
                String[] fa = (faRaw == null || faRaw.isBlank()) ? new String[0] : faRaw.split("\\s*,\\s*");
                for (int i = 0; i < en.length; i++) {
                    if (en[i].isBlank()) continue;
                    String persian = (i < fa.length && !fa[i].isBlank()) ? fa[i].trim() : en[i].trim();
                    result.putIfAbsent(en[i].trim(), persian);
                }
            }
        }
        return result;
    }

    /** Distinct certifications present in the archive (for the advanced-filter dropdown). */
    public List<String> findAllCertifications() throws SQLException {
        java.util.Set<String> certs = new java.util.TreeSet<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT certification FROM movies WHERE certification IS NOT NULL AND certification <> ''")) {
            while (rs.next()) certs.add(rs.getString(1));
        }
        return new ArrayList<>(certs);
    }

    /** Single JOIN query grouping all movie_id -> [tag names], used to batch-hydrate findAll()/findByStatuses(). */
    private Map<Long, List<String>> loadAllTagsGrouped() throws SQLException {
        Map<Long, List<String>> result = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT mt.movie_id, t.name FROM movie_tags mt JOIN tags t ON t.id = mt.tag_id ORDER BY mt.movie_id")) {
            while (rs.next()) {
                long movieId = rs.getLong(1);
                String tagName = rs.getString(2);
                result.computeIfAbsent(movieId, k -> new ArrayList<>()).add(tagName);
            }
        }
        return result;
    }

    /** Maps every column except tags - callers attach tags themselves (either via loadTags() or a bulk map). */
    private Movie mapRowWithoutTags(ResultSet rs) throws SQLException {
        Movie m = new Movie();
        m.setId(rs.getLong("id"));
        m.setFilePath(rs.getString("file_path"));
        m.setRawName(rs.getString("raw_name"));
        m.setParsedTitle(rs.getString("parsed_title"));
        m.setParsedYear((Integer) rs.getObject("parsed_year"));
        m.setTmdbId((Integer) rs.getObject("tmdb_id"));
        m.setTitle(rs.getString("title"));
        m.setOriginalTitle(rs.getString("original_title"));
        m.setReleaseYear((Integer) rs.getObject("release_year"));
        m.setOverview(rs.getString("overview"));
        m.setOverviewFa(rs.getString("overview_fa"));
        m.setVoteAverage((Double) rs.getObject("vote_average"));
        m.setVoteCount((Integer) rs.getObject("vote_count"));
        m.setRuntimeMinutes((Integer) rs.getObject("runtime_minutes"));
        m.setPosterPath(rs.getString("poster_path"));
        m.setBackdropPath(rs.getString("backdrop_path"));
        m.setMatchStatus(Movie.MatchStatus.valueOf(rs.getString("match_status")));
        m.setDirector(rs.getString("director"));
        m.setCast(splitCast(rs.getString("cast_names")));
        m.setTrailerUrl(rs.getString("trailer_url"));
        m.setCountry(rs.getString("country"));
        m.setCountryFa(rs.getString("country_fa"));
        m.setPopularity((Double) rs.getObject("popularity"));
        m.setCertification(rs.getString("certification"));
        m.setImdbId(rs.getString("imdb_id"));
        m.setWatched(rs.getInt("watched") != 0);
        m.setFavorite(rs.getInt("favorite") != 0);
        m.setDateAdded(rs.getLong("date_added"));
        return m;
    }

    /** Loads tag names for a single movie - used where batching isn't worth it (single-row lookups). */
    private List<String> loadTags(long movieId) throws SQLException {
        List<String> tags = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT t.name FROM tags t JOIN movie_tags mt ON t.id = mt.tag_id WHERE mt.movie_id = ? ORDER BY t.name")) {
            ps.setLong(1, movieId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tags.add(rs.getString(1));
            }
        }
        return tags;
    }

    private void setNullableInt(PreparedStatement ps, int idx, Integer value) throws SQLException {
        if (value == null) ps.setNull(idx, Types.INTEGER);
        else ps.setInt(idx, value);
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }
}
