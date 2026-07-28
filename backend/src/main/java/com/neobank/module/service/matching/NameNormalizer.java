package com.neobank.module.service.matching;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Names compare equal across case, accents and punctuation — "MAREK NOWAK", "Marek Nowák" and
 * "marek nowak" must all normalise to the same string (uc-02 AC5).
 */
final class NameNormalizer {

    private NameNormalizer() {
        // utility
    }

    static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        String withoutAccents = decomposed.replaceAll("\\p{M}", "");
        String lower = withoutAccents.toLowerCase(Locale.ROOT);
        String lettersAndDigitsOnly = lower.replaceAll("[^a-z0-9]+", " ");
        return lettersAndDigitsOnly.trim().replaceAll("\\s+", " ");
    }
}
