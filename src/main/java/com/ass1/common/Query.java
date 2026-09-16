package com.ass1.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One line of the input file after parsing.
 *
 * <p>The input format is<br>
 * {@code <method name> <arg1> <arg2> <arg3> Zone:<n>}<br>
 * and country names may contain spaces ("Equatorial Guinea"), so the line is
 * not simply split into fixed columns: numbers and the words "min"/"max" are
 * recognised by their shape and everything left over is the country name.</p>
 */
public final class Query implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Names of the four remote methods, used for grouping the statistics. */
    public static final String POPULATION_OF_COUNTRY = "getPopulationofCountry";
    public static final String NUMBER_OF_CITIES = "getNumberofCities";
    public static final String NUMBER_OF_COUNTRIES = "getNumberofCountries";
    public static final String NUMBER_OF_COUNTRIES_MM = "getNumberofCountriesMM";

    private final String raw;
    private final String method;
    private final int zone;
    private final String country;
    private final String comparison;   // "min" or "max"
    private final int cityCount;
    private final long threshold;      // also used as minimum population for the MM method
    private final long maxThreshold;

    private Query(String raw, String method, int zone, String country, String comparison,
                  int cityCount, long threshold, long maxThreshold) {
        this.raw = raw;
        this.method = method;
        this.zone = zone;
        this.country = country;
        this.comparison = comparison;
        this.cityCount = cityCount;
        this.threshold = threshold;
        this.maxThreshold = maxThreshold;
    }

    /**
     * Turns one text line into a {@link Query} object.
     *
     * @param line e.g. {@code getNumberofCities Equatorial Guinea 11947 min Zone:5}
     * @return the parsed query, never null
     * @throws IllegalArgumentException when the line does not follow the format
     */
    public static Query parse(String line) {
        String trimmed = line.trim();
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length < 2) {
            throw new IllegalArgumentException("Not a valid query line: " + line);
        }

        String method = tokens[0];
        int zone = -1;
        List<String> middle = new ArrayList<>();
        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.toLowerCase(Locale.ROOT).startsWith("zone:")) {
                String zoneText = token.substring("zone:".length()).trim();
                try {
                    zone = Integer.parseInt(zoneText);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Zone is not a number in line: " + line, e);
                }
            } else {
                middle.add(token);
            }
        }
        if (zone < 1) {
            throw new IllegalArgumentException("Missing or invalid Zone:<n> in line: " + line);
        }

        // separate the tokens into: numbers, the min/max word, and the country words
        List<Long> numbers = new ArrayList<>();
        String comparison = null;
        StringBuilder countryName = new StringBuilder();
        for (String token : middle) {
            if (token.equalsIgnoreCase("min") || token.equalsIgnoreCase("max")) {
                comparison = token.toLowerCase(Locale.ROOT);
            } else if (isNumber(token)) {
                numbers.add(Long.parseLong(token));
            } else {
                if (!countryName.isEmpty()) {
                    countryName.append(' ');
                }
                countryName.append(token);
            }
        }

        String country = countryName.toString();
        long first = numbers.isEmpty() ? 0 : numbers.get(0);
        long second = numbers.size() < 2 ? 0 : numbers.get(1);
        long third = numbers.size() < 3 ? 0 : numbers.get(2);

        return switch (method) {
            case POPULATION_OF_COUNTRY ->
                    new Query(trimmed, method, zone, country, null, 0, 0, 0);
            case NUMBER_OF_CITIES ->
                    new Query(trimmed, method, zone, country, orDefault(comparison), 0, first, 0);
            case NUMBER_OF_COUNTRIES ->
                    new Query(trimmed, method, zone, null, orDefault(comparison), (int) first, second, 0);
            case NUMBER_OF_COUNTRIES_MM ->
                    new Query(trimmed, method, zone, null, null, (int) first, second, third);
            default -> throw new IllegalArgumentException("Unknown method in line: " + line);
        };
    }

    private static boolean isNumber(String token) {
        if (token.isEmpty()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String orDefault(String comparison) {
        return comparison == null ? "min" : comparison;
    }

    /**
     * Key used by both caches. The zone is NOT part of the key, because the same
     * question asked from another zone has the same answer.
     */
    public String cacheKey() {
        return switch (method) {
            case POPULATION_OF_COUNTRY -> method + "|" + country;
            case NUMBER_OF_CITIES -> method + "|" + country + "|" + threshold + "|" + comparison;
            case NUMBER_OF_COUNTRIES -> method + "|" + cityCount + "|" + threshold + "|" + comparison;
            default -> method + "|" + cityCount + "|" + threshold + "|" + maxThreshold;
        };
    }

    public String raw() {
        return raw;
    }

    public String method() {
        return method;
    }

    public int zone() {
        return zone;
    }

    public String country() {
        return country;
    }

    public String comparison() {
        return comparison;
    }

    public int cityCount() {
        return cityCount;
    }

    public long threshold() {
        return threshold;
    }

    public long maxThreshold() {
        return maxThreshold;
    }

    @Override
    public String toString() {
        return raw;
    }
}



