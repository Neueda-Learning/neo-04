package com.neobank.module.service.matching;

/**
 * Turns two already-{@link NameNormalizer normalised} names into a similarity weight in
 * {@code [0.0, 1.0]} — {@code 1.0} identical, {@code 0.0} nothing in common — using a classic
 * Levenshtein edit distance scaled by the longer name's length. No dependency, no locale data:
 * good enough to rank "is this a typo/alias of a real entry" without claiming to be a real
 * phonetic matcher.
 */
final class FuzzyNameScorer {

    private FuzzyNameScorer() {
        // utility
    }

    static double similarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        int distance = levenshtein(a, b);
        int maxLen = Math.max(a.length(), b.length());
        double raw = 1.0 - ((double) distance / maxLen);
        return Math.max(0.0, Math.min(1.0, raw));
    }

    /** Rounds to 2 decimal places — evidence should read {@code 0.91}, not a float tail. */
    static double round(double weight) {
        return Math.round(weight * 100.0) / 100.0;
    }

    private static int levenshtein(String a, String b) {
        int[] previousRow = new int[b.length() + 1];
        int[] currentRow = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previousRow[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            currentRow[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitutionCost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                currentRow[j] = Math.min(
                        Math.min(currentRow[j - 1] + 1, previousRow[j] + 1),
                        previousRow[j - 1] + substitutionCost);
            }
            int[] swap = previousRow;
            previousRow = currentRow;
            currentRow = swap;
        }
        return previousRow[b.length()];
    }
}
