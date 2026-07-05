package com.moviearchive.tmdb;

import com.moviearchive.model.Movie;

import java.util.List;
import java.util.Locale;

/**
 * Decides, given TMDB search candidates, whether we can auto-accept the top
 * result or whether the user needs to confirm manually. This is the piece
 * that protects archive quality - silently picking the wrong movie is worse
 * than asking once.
 */
public class MatchEngine {

    public enum Decision { AUTO_ACCEPT, NEEDS_REVIEW, NOT_FOUND }

    public record Verdict(Decision decision, TmdbClient.SearchCandidate best) {}

    /**
     * @param parsedTitle title extracted by MovieNameParser
     * @param parsedYear  year extracted by MovieNameParser (nullable)
     * @param candidates  results from TmdbClient.search(), already sorted by TMDB relevance
     */
    public Verdict decide(String parsedTitle, Integer parsedYear, List<TmdbClient.SearchCandidate> candidates) {
        if (candidates.isEmpty()) {
            return new Verdict(Decision.NOT_FOUND, null);
        }

        TmdbClient.SearchCandidate top = candidates.get(0);

        boolean yearMatches = parsedYear == null || yearOf(top.releaseDate()) == null
                || Math.abs(yearOf(top.releaseDate()) - parsedYear) <= 1;

        double similarity = titleSimilarity(parsedTitle, top.title());
        if (similarity < 0.6) {
            similarity = Math.max(similarity, titleSimilarity(parsedTitle, top.originalTitle()));
        }

        boolean strongTitle = similarity >= 0.75;

        // Only one candidate close in popularity to the top? If a second
        // candidate is nearly as popular AND also matches the year, that's
        // ambiguous - flag for review rather than guess.
        boolean ambiguous = false;
        if (candidates.size() > 1) {
            TmdbClient.SearchCandidate second = candidates.get(1);
            Integer secondYear = yearOf(second.releaseDate());
            boolean secondYearOk = parsedYear == null || secondYear == null
                    || Math.abs(secondYear - parsedYear) <= 1;
            if (secondYearOk && second.popularity() > top.popularity() * 0.7) {
                ambiguous = true;
            }
        }

        if (yearMatches && strongTitle && !ambiguous) {
            return new Verdict(Decision.AUTO_ACCEPT, top);
        }
        return new Verdict(Decision.NEEDS_REVIEW, top);
    }

    private Integer yearOf(String releaseDate) {
        if (releaseDate == null || releaseDate.length() < 4) return null;
        try {
            return Integer.parseInt(releaseDate.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Simple normalized similarity: lowercase, strip punctuation, compare
     * token overlap. Good enough to separate "clearly right" from
     * "needs a human" without pulling in a full fuzzy-matching library.
     */
    private double titleSimilarity(String a, String b) {
        if (a == null || b == null) return 0;
        String na = normalize(a);
        String nb = normalize(b);
        if (na.equals(nb)) return 1.0;

        String[] tokensA = na.split(" ");
        String[] tokensB = nb.split(" ");
        int matches = 0;
        for (String ta : tokensA) {
            for (String tb : tokensB) {
                if (ta.equals(tb) && !ta.isBlank()) { matches++; break; }
            }
        }
        int maxLen = Math.max(tokensA.length, tokensB.length);
        return maxLen == 0 ? 0 : (double) matches / maxLen;
    }

    private String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", "").trim();
    }
}
