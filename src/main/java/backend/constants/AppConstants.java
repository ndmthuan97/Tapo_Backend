package backend.constants;

public final class AppConstants {

    private AppConstants() {
        // Utility class — prevent instantiation
    }

    // ── User / Avatar ─────────────────────────────────────────────────────────
    public static final String DEFAULT_AVATAR =
            "https://cvjhcyjtxxtxbszxjrwh.supabase.co/storage/v1/object/public/avatars/default-avatar.png";

    // ── Pagination ────────────────────────────────────────────────────────────
    public static final int MAX_PAGE_SIZE    = 70;
    public static final int DEFAULT_PAGE_SIZE  = 8;
    public static final int DEFAULT_PAGE_INDEX = 1;
}
