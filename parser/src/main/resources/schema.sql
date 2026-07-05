-- Local movie archive database (SQLite)
-- This is the "cache" mentioned in the requirements: once a movie is
-- matched here, we never need to call TMDB again for it.

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS movies (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,

    -- local file info
    file_path         TEXT NOT NULL UNIQUE,
    raw_name          TEXT NOT NULL,
    parsed_title      TEXT,
    parsed_year       INTEGER,

    -- TMDB metadata
    tmdb_id           INTEGER,
    title             TEXT,
    original_title    TEXT,
    release_year      INTEGER,
    overview          TEXT,
    overview_fa       TEXT,       -- Persian translation, when TMDB has one
    vote_average      REAL,
    vote_count        INTEGER,
    runtime_minutes   INTEGER,
    poster_path       TEXT,       -- local cached file path, e.g. posters/12345.jpg
    backdrop_path     TEXT,

    match_status      TEXT NOT NULL DEFAULT 'PENDING',
                        -- PENDING | MATCHED | NEEDS_REVIEW | NOT_FOUND | MANUAL

    director          TEXT,
    cast_names        TEXT,       -- "Actor (Character) | Actor2 (Character2) | ..."
    trailer_url       TEXT,
    country           TEXT,       -- "United States, France"
    country_fa        TEXT,       -- same list, Persian names, same order
    popularity        REAL,       -- TMDB popularity score
    certification     TEXT,       -- e.g. "PG-13"
    imdb_id           TEXT,       -- e.g. "tt1375666"

    watched           INTEGER NOT NULL DEFAULT 0,  -- 0/1 - personal field, not from TMDB
    favorite          INTEGER NOT NULL DEFAULT 0,  -- 0/1 - personal field, not from TMDB

    date_added        INTEGER NOT NULL,   -- epoch millis
    last_updated      INTEGER NOT NULL    -- epoch millis
);

CREATE INDEX IF NOT EXISTS idx_movies_status ON movies(match_status);
CREATE INDEX IF NOT EXISTS idx_movies_title  ON movies(title);
CREATE INDEX IF NOT EXISTS idx_movies_tmdb   ON movies(tmdb_id);

CREATE TABLE IF NOT EXISTS tags (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    name    TEXT NOT NULL UNIQUE COLLATE NOCASE,
    kind    TEXT NOT NULL DEFAULT 'KEYWORD'  -- GENRE | KEYWORD
);

CREATE TABLE IF NOT EXISTS movie_tags (
    movie_id INTEGER NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    tag_id   INTEGER NOT NULL REFERENCES tags(id)   ON DELETE CASCADE,
    PRIMARY KEY (movie_id, tag_id)
);

-- Root folders the user has registered for scanning, so re-scans
-- can be incremental (only check for new files) and multiple library
-- folders can be scanned together in one "Scan All" pass.
CREATE TABLE IF NOT EXISTS scan_paths (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    path        TEXT NOT NULL UNIQUE,
    last_scanned INTEGER
);

-- App-level key/value settings (TMDB api key location note: store in a local
-- config file, not in this shared-export-able DB, to avoid leaking it on export).
CREATE TABLE IF NOT EXISTS settings (
    key   TEXT PRIMARY KEY,
    value TEXT
);
