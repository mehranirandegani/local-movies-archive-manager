package com.moviearchive.ui;

import com.moviearchive.model.Movie;

import java.util.Map;

/**
 * TMDB's genre list is a small, fixed, well-known set (~19 genres) that
 * never changes, so a hardcoded translation table is more reliable than
 * fetching a translation per movie (no extra API calls, no risk of a
 * missing/inconsistent translation). Keywords, on the other hand, are
 * free-form and only ever come back from TMDB in English - there's no
 * translation source for those, so they're always shown as-is.
 */
public final class Localization {

    private Localization() {}

    public static final String FA = "fa";
    public static final String EN = "en";

    private static final Map<String, String> GENRE_FA = Map.ofEntries(
            Map.entry("Action", "اکشن"),
            Map.entry("Adventure", "ماجراجویی"),
            Map.entry("Animation", "انیمیشن"),
            Map.entry("Comedy", "کمدی"),
            Map.entry("Crime", "جنایی"),
            Map.entry("Documentary", "مستند"),
            Map.entry("Drama", "درام"),
            Map.entry("Family", "خانوادگی"),
            Map.entry("Fantasy", "فانتزی"),
            Map.entry("History", "تاریخی"),
            Map.entry("Horror", "ترسناک"),
            Map.entry("Music", "موسیقی"),
            Map.entry("Mystery", "معمایی"),
            Map.entry("Romance", "عاشقانه"),
            Map.entry("Science Fiction", "علمی-تخیلی"),
            Map.entry("TV Movie", "تلویزیونی"),
            Map.entry("Thriller", "هیجان‌انگیز"),
            Map.entry("War", "جنگی"),
            Map.entry("Western", "وسترن")
    );

    /** Returns true if this tag is one of TMDB's known genres (as opposed to a free-form keyword). */
    public static boolean isGenre(String tag) {
        return GENRE_FA.containsKey(tag);
    }

    /** Returns "Persian (English)" for a known genre when language is fa, otherwise the tag unchanged. */
    public static String displayTag(String tag, String language) {
        if (FA.equals(language)) {
            String fa = GENRE_FA.get(tag);
            if (fa != null) return fa + " (" + tag + ")";
        }
        return tag;
    }

    /** Picks the overview matching the current language, falling back to whichever one exists. */
    public static String displayOverview(Movie m, String language) {
        if (FA.equals(language)) {
            if (m.getOverviewFa() != null && !m.getOverviewFa().isBlank()) return m.getOverviewFa();
            return m.getOverview();
        }
        if (m.getOverview() != null && !m.getOverview().isBlank()) return m.getOverview();
        return m.getOverviewFa();
    }

    /** Picks the country string matching the current language; in fa mode shows "Persian (English)". */
    public static String displayCountry(Movie m, String language) {
        if (m.getCountry() == null || m.getCountry().isBlank()) return m.getCountryFa();
        if (FA.equals(language) && m.getCountryFa() != null && !m.getCountryFa().isBlank()) {
            return m.getCountryFa() + " (" + m.getCountry() + ")";
        }
        return m.getCountry();
    }
}
