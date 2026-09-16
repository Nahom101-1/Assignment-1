package com.ass1.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The "naive implementation" required by the assignment: every question is
 * answered by reading the whole CSV file again, no pre-processing and no
 * permanent data structure is kept in memory.
 *
 * <p>Dataset columns (separated by ';'):<br>
 * {@code Geoname ID;Name;Country Code;Country name EN;Population;Timezone;Coordinates}</p>
 */
public final class NaiveStatistics {

    private static final int COUNTRY_COLUMN = 3;
    private static final int POPULATION_COLUMN = 4;

    private final Path dataset;

    public NaiveStatistics(Path dataset) {
        this.dataset = dataset;
        if (!Files.isReadable(dataset)) {
            throw new IllegalArgumentException("Dataset file not found: " + dataset.toAbsolutePath());
        }
    }

    /** Sum of the population of all cities of one country. */
    public long populationOfCountry(String country) {
        long[] total = {0};
        scan((countryName, population) -> {
            if (countryName.equalsIgnoreCase(country)) {
                total[0] += population;
            }
        });
        return total[0];
    }

    /**
     * Number of cities of one country whose population is at least ("min") or
     * at most ("max") the given threshold.
     */
    public long numberOfCities(String country, long threshold, String comparison) {
        boolean min = isMin(comparison);
        long[] count = {0};
        scan((countryName, population) -> {
            if (countryName.equalsIgnoreCase(country) && matches(population, threshold, min)) {
                count[0]++;
            }
        });
        return count[0];
    }

    /**
     * Number of countries having at least {@code cityCount} cities whose
     * population is at least ("min") or at most ("max") the threshold.
     */
    public long numberOfCountries(int cityCount, long threshold, String comparison) {
        boolean min = isMin(comparison);
        Map<String, Integer> perCountry = new HashMap<>();
        scan((countryName, population) -> {
            if (matches(population, threshold, min)) {
                perCountry.merge(countryName.toLowerCase(Locale.ROOT), 1, Integer::sum);
            }
        });
        return countAtLeast(perCountry, cityCount);
    }

    /**
     * Number of countries having at least {@code cityCount} cities whose
     * population lies between {@code minPopulation} and {@code maxPopulation}.
     */
    public long numberOfCountriesMM(int cityCount, long minPopulation, long maxPopulation) {
        Map<String, Integer> perCountry = new HashMap<>();
        scan((countryName, population) -> {
            if (population >= minPopulation && population <= maxPopulation) {
                perCountry.merge(countryName.toLowerCase(Locale.ROOT), 1, Integer::sum);
            }
        });
        return countAtLeast(perCountry, cityCount);
    }

    private static long countAtLeast(Map<String, Integer> perCountry, int cityCount) {
        return perCountry.values().stream().filter(cities -> cities >= cityCount).count();
    }

    private static boolean isMin(String comparison) {
        return comparison == null || comparison.equalsIgnoreCase("min");
    }

    private static boolean matches(long population, long threshold, boolean min) {
        return min ? population >= threshold : population <= threshold;
    }

    /**
     * Reads the dataset line by line and hands (country name, population) of every
     * city to the given function. This is the only place that knows the file format.
     */
    private void scan(BiConsumer<String, Long> row) {
        try (BufferedReader reader = Files.newBufferedReader(dataset, StandardCharsets.UTF_8)) {
            String line = reader.readLine(); // skip the header line
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] columns = line.split(";", -1);
                if (columns.length <= POPULATION_COLUMN) {
                    continue; // broken line, ignore it
                }
                String country = columns[COUNTRY_COLUMN].trim();
                String populationText = columns[POPULATION_COLUMN].trim();
                if (country.isEmpty() || populationText.isEmpty()) {
                    continue;
                }
                try {
                    row.accept(country, Long.parseLong(populationText));
                } catch (NumberFormatException ignored) {
                    // a city without a usable population number is skipped
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read dataset " + dataset, e);
        }
    }
}
