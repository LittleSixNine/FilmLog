package cn.sixnine.filmlog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

final class MemoTags {
    static final List<String> KEYS = Collections.unmodifiableList(Arrays.asList(
        "Portrait", "Landscape", "Street", "Night", "Architecture", "Documentary"));
    private final LinkedHashSet<String> selected = new LinkedHashSet<>();
    final String freeText;

    MemoTags(String memo) {
        ArrayList<String> free = new ArrayList<>();
        for (String part : memo.split(" • ", -1)) {
            String token = part.trim();
            if (token.startsWith("#") && KEYS.contains(token.substring(1))) selected.add(token.substring(1));
            else free.add(part); // Keep unknown hashtags and older plain-text labels intact.
        }
        freeText = String.join(" • ", free).trim();
    }

    boolean contains(String key) { return selected.contains(key); }

    boolean toggle(String key) {
        if (!KEYS.contains(key)) throw new IllegalArgumentException("Unknown memo tag");
        if (selected.remove(key)) return false;
        selected.add(key);
        return true;
    }

    String memo(String text) {
        ArrayList<String> parts = new ArrayList<>();
        if (!text.trim().isEmpty()) parts.add(text.trim());
        for (String key : selected) parts.add("#" + key);
        return String.join(" • ", parts);
    }
}
