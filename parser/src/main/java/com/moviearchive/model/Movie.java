package com.moviearchive.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single movie entry in the local archive database.
 * This is the row shape for the `movies` table (see schema.sql).
 */
public class Movie {

    public enum MatchStatus {
        PENDING,        // not yet queried against TMDB
        MATCHED,        // high-confidence automatic match
        NEEDS_REVIEW,   // multiple close candidates or low confidence - user must confirm
        NOT_FOUND,      // TMDB returned nothing
        MANUAL          // user manually picked / entered
    }

    private Long id;

    // --- Local file info ---
    private String filePath;          // full path to the video file or folder
    private String rawName;           // original folder/file name before parsing
    private String parsedTitle;       // title guessed by MovieNameParser
    private Integer parsedYear;

    // --- TMDB metadata (filled after lookup) ---
    private Integer tmdbId;
    private String title;
    private String originalTitle;
    private Integer releaseYear;
    private String overview;          // plot description (English, or TMDB's best available)
    private String overviewFa;        // Persian translation, when TMDB has one
    private Double voteAverage;       // rating, e.g. 7.8
    private Integer voteCount;
    private Integer runtimeMinutes;
    private String posterPath;        // local cached file path to poster image
    private String backdropPath;      // local cached file path to backdrop image

    private MatchStatus matchStatus = MatchStatus.PENDING;
    private List<String> tags = new ArrayList<>(); // genres + keywords combined

    // --- Extra TMDB metadata ---
    private String director;
    private List<String> cast = new ArrayList<>(); // formatted as "Name (Character)"
    private String trailerUrl;
    private String country;           // comma-joined production countries, e.g. "United States, France"
    private String countryFa;         // same list, Persian names, same order (when available)
    private Double popularity;        // TMDB popularity score (not IMDb's)
    private String certification;     // e.g. "PG-13" - US certification when available
    private String imdbId;            // e.g. "tt1375666" - used to link/open the movie's IMDb page

    // --- Personal fields (not from TMDB) ---
    private boolean watched;
    private boolean favorite;

    private long dateAdded;           // epoch millis

    // --- getters / setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getRawName() { return rawName; }
    public void setRawName(String rawName) { this.rawName = rawName; }

    public String getParsedTitle() { return parsedTitle; }
    public void setParsedTitle(String parsedTitle) { this.parsedTitle = parsedTitle; }

    public Integer getParsedYear() { return parsedYear; }
    public void setParsedYear(Integer parsedYear) { this.parsedYear = parsedYear; }

    public Integer getTmdbId() { return tmdbId; }
    public void setTmdbId(Integer tmdbId) { this.tmdbId = tmdbId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getOriginalTitle() { return originalTitle; }
    public void setOriginalTitle(String originalTitle) { this.originalTitle = originalTitle; }

    public Integer getReleaseYear() { return releaseYear; }
    public void setReleaseYear(Integer releaseYear) { this.releaseYear = releaseYear; }

    public String getOverview() { return overview; }
    public void setOverview(String overview) { this.overview = overview; }

    public String getOverviewFa() { return overviewFa; }
    public void setOverviewFa(String overviewFa) { this.overviewFa = overviewFa; }

    public Double getVoteAverage() { return voteAverage; }
    public void setVoteAverage(Double voteAverage) { this.voteAverage = voteAverage; }

    public Integer getVoteCount() { return voteCount; }
    public void setVoteCount(Integer voteCount) { this.voteCount = voteCount; }

    public Integer getRuntimeMinutes() { return runtimeMinutes; }
    public void setRuntimeMinutes(Integer runtimeMinutes) { this.runtimeMinutes = runtimeMinutes; }

    public String getPosterPath() { return posterPath; }
    public void setPosterPath(String posterPath) { this.posterPath = posterPath; }

    public String getBackdropPath() { return backdropPath; }
    public void setBackdropPath(String backdropPath) { this.backdropPath = backdropPath; }

    public MatchStatus getMatchStatus() { return matchStatus; }
    public void setMatchStatus(MatchStatus matchStatus) { this.matchStatus = matchStatus; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getDirector() { return director; }
    public void setDirector(String director) { this.director = director; }

    public List<String> getCast() { return cast; }
    public void setCast(List<String> cast) { this.cast = cast; }

    public String getTrailerUrl() { return trailerUrl; }
    public void setTrailerUrl(String trailerUrl) { this.trailerUrl = trailerUrl; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getCountryFa() { return countryFa; }
    public void setCountryFa(String countryFa) { this.countryFa = countryFa; }

    public Double getPopularity() { return popularity; }
    public void setPopularity(Double popularity) { this.popularity = popularity; }

    public String getCertification() { return certification; }
    public void setCertification(String certification) { this.certification = certification; }

    public String getImdbId() { return imdbId; }
    public void setImdbId(String imdbId) { this.imdbId = imdbId; }

    public boolean isWatched() { return watched; }
    public void setWatched(boolean watched) { this.watched = watched; }

    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public long getDateAdded() { return dateAdded; }
    public void setDateAdded(long dateAdded) { this.dateAdded = dateAdded; }

    /**
     * Equality by file_path (the natural unique key), not object identity.
     * This is what lets the UI find "the currently selected movie" again
     * inside a freshly-filtered/re-sorted list after a search or filter
     * change, so the grid can scroll back to it once the filter is cleared.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Movie other)) return false;
        return filePath != null && filePath.equals(other.filePath);
    }

    @Override
    public int hashCode() {
        return filePath == null ? 0 : filePath.hashCode();
    }
}
