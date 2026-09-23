package com.ass1.server.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * Answers the four dataset queries with a deliberately <em>naive</em> implementation.
 *
 * <p>As the assignment requires, the server re-reads and re-parses the <b>complete</b> dataset
 * on <b>every</b> request. Nothing is indexed, sorted or memoised here: each public method opens
 * the CSV, streams it top to bottom, and throws the parsed data away again.
 *
 * <p>This is intentionally the slow path. The speed-up is supposed to come from the result
 * {@link Cache} sitting in front of this class (see {@link Worker}), so a repeated query skips
 * the file scan entirely. If this class cached the dataset itself, the cached vs non-cached
 * measurement asked for in the assignment would be meaningless.
 *
 * <p>Stateless, so it is safe to share between servers running in the same JVM.
 */
public class Processor {

    private static final int COUNTRY_COLUMN = 3;
    private static final int POPULATION_COLUMN = 4;

    private final Path dataset;

    public Processor(Path dataset) {
        if (!Files.isReadable(dataset)) {
            throw new IllegalArgumentException("Dataset file not found: " + dataset.toAbsolutePath());
        }
        this.dataset = dataset.toAbsolutePath().normalize();
    }

    /** Total population of every city belonging to the given country. */
    public long getPopulationOfCountry(String countryName) {
        String wanted = key(countryName);
        long[] total = {0L};

        scan((country, population) -> {
            if (country.equals(wanted)) {
                total[0] += population;
            }
        });
        return total[0];
    }

    /** Cities in the given country whose population is >= (min) or <= (max) the threshold. */
    public int getNumberOfCities(String countryName, int threshold, String comp) {
        String wanted = key(countryName);
        boolean min = isMin(comp);
        int[] cities = {0};

        scan((country, population) -> {
            if (country.equals(wanted) && matches(population, threshold, min)) {
                cities[0]++;
            }
        });
        return cities[0];
    }

    /** Countries having at least {@code cityCount} cities that satisfy the threshold. */
    public int getNumberOfCountries(int cityCount, int threshold, String comp) {
        boolean min = isMin(comp);
        Map<String, Integer> matchesPerCountry = new HashMap<>();

        scan((country, population) -> {
            if (matches(population, threshold, min)) {
                matchesPerCountry.merge(country, 1, Integer::sum);
            }
        });
        return countCountriesWithAtLeast(matchesPerCountry, cityCount);
    }

    /** Countries having at least {@code cityCount} cities inside the population range. */
    public int getNumberOfCountriesMM(int cityCount, int minPopulation, int maxPopulation) {
        Map<String, Integer> matchesPerCountry = new HashMap<>();

        scan((country, population) -> {
            if (population >= minPopulation && population <= maxPopulation) {
                matchesPerCountry.merge(country, 1, Integer::sum);
            }
        });
        return countCountriesWithAtLeast(matchesPerCountry, cityCount);
    }

    /**
     * Reads the entire dataset from disk and hands every valid (country, population) pair to
     * {@code row}. Called once per request on purpose: this is the naive full scan.
     */
    private void scan(BiConsumer<String, Integer> row) {
        try (Stream<String> lines = Files.lines(dataset, StandardCharsets.UTF_8)) {
            lines.skip(1).forEach(line -> parseLine(line, row)); // skip(1) drops the header
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read dataset " + dataset, e);
        }
    }

    /** Extracts country and population from one CSV line, ignoring unusable rows. */
    private static void parseLine(String line, BiConsumer<String, Integer> row) {
        if (line.isBlank()) {
            return;
        }
        String[] columns = line.split(";", -1);
        if (columns.length <= POPULATION_COLUMN) {
            return; // broken line
        }
        String country = columns[COUNTRY_COLUMN].trim();
        String populationText = columns[POPULATION_COLUMN].trim();
        if (country.isEmpty() || populationText.isEmpty()) {
            return;
        }
        int population;
        try {
            population = Integer.parseInt(populationText);
        } catch (NumberFormatException ignored) {
            return; // city without a usable population number
        }
        row.accept(key(country), population);
    }

    private static int countCountriesWithAtLeast(Map<String, Integer> matchesPerCountry, int cityCount) {
        int countries = 0;
        for (int matching : matchesPerCountry.values()) {
            if (matching >= cityCount) {
                countries++;
            }
        }
        return countries;
    }

    /** "min" means population >= threshold, anything else means population <= threshold. */
    private static boolean matches(int population, int threshold, boolean min) {
        return min ? population >= threshold : population <= threshold;
    }

    private static boolean isMin(String comp) {
        return comp != null && comp.trim().equalsIgnoreCase("min");
    }

    private static String key(String countryName) {
        return countryName == null ? "" : countryName.trim().toLowerCase(Locale.ROOT);
    }
}

