package com.moviearchive.ui;

import com.moviearchive.model.Movie;

/**
 * Mutable holder for the "advanced filter" criteria set from the Filters
 * panel's inline range/dropdown fields. Any field left null/blank means
 * "no restriction on this dimension".
 */
public class FilterCriteria {

    public Integer yearFrom;
    public Integer yearTo;
    public Double ratingFrom;
    public Double ratingTo;
    public Integer runtimeFrom;
    public Integer runtimeTo;
    public String country;         // exact match against one of the movie's comma-joined countries
    public String certification;   // exact match

    public boolean isEmpty() {
        return yearFrom == null && yearTo == null
                && ratingFrom == null && ratingTo == null
                && runtimeFrom == null && runtimeTo == null
                && (country == null || country.isBlank())
                && (certification == null || certification.isBlank());
    }

    public void reset() {
        yearFrom = null; yearTo = null;
        ratingFrom = null; ratingTo = null;
        runtimeFrom = null; runtimeTo = null;
        country = null;
        certification = null;
    }

    public boolean matches(Movie m) {
        Integer year = m.getReleaseYear() != null ? m.getReleaseYear() : m.getParsedYear();
        if (yearFrom != null && (year == null || year < yearFrom)) return false;
        if (yearTo != null && (year == null || year > yearTo)) return false;

        Double rating = m.getVoteAverage();
        if (ratingFrom != null && (rating == null || rating < ratingFrom)) return false;
        if (ratingTo != null && (rating == null || rating > ratingTo)) return false;

        Integer runtime = m.getRuntimeMinutes();
        if (runtimeFrom != null && (runtime == null || runtime < runtimeFrom)) return false;
        if (runtimeTo != null && (runtime == null || runtime > runtimeTo)) return false;

        if (country != null && !country.isBlank()) {
            if (m.getCountry() == null || !m.getCountry().toLowerCase().contains(country.toLowerCase())) return false;
        }

        if (certification != null && !certification.isBlank()) {
            if (!certification.equals(m.getCertification())) return false;
        }

        return true;
    }
}
