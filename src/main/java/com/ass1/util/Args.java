package com.ass1.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Tiny helper that reads command line options written as {@code --name value}.
 *
 * <p>Example: {@code server --port 1101 --cache lru} is read as
 * {@code {port=1101, cache=lru}}. Everything that is not an option is ignored,
 * so the mode word ("server", "client", ...) can stay in front.</p>
 */
public final class Args {

    private final Map<String, String> values = new HashMap<>();

    /**
     * @param argv raw arguments from {@code main}
     * @param skip how many leading words to ignore (normally 1, the mode word)
     */
    public Args(String[] argv, int skip) {
        for (int i = skip; i < argv.length; i++) {
            String token = argv[i];
            if (!token.startsWith("--")) {
                continue;
            }
            String name = token.substring(2).toLowerCase();
            // a flag without a value (e.g. --verbose) counts as "true"
            if (i + 1 < argv.length && !argv[i + 1].startsWith("--")) {
                values.put(name, argv[++i]);
            } else {
                values.put(name, "true");
            }
        }
    }

    /** Returns the option value, or {@code fallback} when the option is missing. */
    public String get(String name, String fallback) {
        return values.getOrDefault(name.toLowerCase(), fallback);
    }

    /** Returns the option as a number, or {@code fallback} when missing/not a number. */
    public int getInt(String name, int fallback) {
        try {
            return Integer.parseInt(get(name, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

}

