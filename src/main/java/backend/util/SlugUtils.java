package backend.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Utility for generating URL-friendly slugs from Vietnamese or English text.
 * Converts diacritics (e.g., "Laptop ASUS VivoBook 15" → "laptop-asus-vivobook-15").
 */
public final class SlugUtils {

    private SlugUtils() {}

    private static final Pattern NON_LATIN   = Pattern.compile("[^\\w-]");
    private static final Pattern EXTRA_DASH  = Pattern.compile("-{2,}");

    public static String toSlug(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        // Remove diacritical marks
        normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // Lowercase and replace spaces / special chars with dash
        return EXTRA_DASH.matcher(
                NON_LATIN.matcher(
                        normalized.toLowerCase().trim().replace(' ', '-')
                ).replaceAll("")
        ).replaceAll("-");
    }
}
