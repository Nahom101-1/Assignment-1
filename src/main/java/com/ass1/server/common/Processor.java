package com.ass1.server.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Answers the four dataset queries.
 *
 * Instead of scanning the CSV file on every call, the file is parsed exactly once
 * and turned into an index:
 *
 *   country -> { sorted array of city populations, total population }
 *
 * Because the populations are sorted, "how many cities are >= / <= X" becomes a
 * binary search instead of a linear scan, and the total population is already
 * computed. The index is immutable after construction, so it is safe to share
 * between the zone servers running in the same JVM.
 */
public class Processor {

    private static final int COUNTRY_COLUMN = 3;
    private static final int POPULATION_COLUMN = 4;

    /**
     * One entry per country: city populations (ascending) + precomputed sum.
     *
     * @param cityPopulations sorted ascending
     */
    private record CountryIndex(int[] cityPopulations, long totalPopulation) {

        /**
         * Number of cities with population >= threshold.
         */
        int countAtLeast(int threshold) {
            return cityPopulations.length - lowerBound(threshold);
        }

        /**
         * Number of cities with population <= threshold.
         */
        int countAtMost(int threshold) {
            return lowerBound(threshold + 1);
        }

        /**
         * Number of cities with min <= population <= max.
         */
        int countBetween(int min, int max) {
            if (min > max) {
                return 0;
            }
            return lowerBound(max + 1) - lowerBound(min);
        }

        /**
         * Index of the first element >= value (classic lower bound).
         */
        private int lowerBound(int value) {
            int low = 0;
            int high = cityPopulations.length;
            while (low < high) {
                int mid = (low + high) >>> 1;
                if (cityPopulations[mid] < value) {
                    low = mid + 1;
                } else {
                    high = mid;
                }
            }
            return low;
        }
    }

    private final Map<String, CountryIndex> index;

    public Processor(Path dataset) {
        if (!Files.isReadable(dataset)) {
            throw new IllegalArgumentException("Dataset file not found: " + dataset.toAbsolutePath());
        }

        this.index = Processor.parse(dataset.toAbsolutePath().normalize());
    }

    public long getPopulationOfCountry(String countryName) {
        CountryIndex country = index.get(key(countryName));
        return country == null ? 0L : country.totalPopulation;
    }

    public int getNumberOfCities(String countryName, int threshold, String comp) {
        CountryIndex country = index.get(key(countryName));
        if (country == null) {
            return 0;
        }
        return isMin(comp) ? country.countAtLeast(threshold) : country.countAtMost(threshold);
    }

    public int getNumberOfCountries(int cityCount, int threshold, String comp) {
        boolean min = isMin(comp);
        int countries = 0;
        for (CountryIndex country : index.values()) {
            int matching = min ? country.countAtLeast(threshold) : country.countAtMost(threshold);
            if (matching >= cityCount) {
                countries++;
            }
        }
        return countries;
    }

    public int getNumberOfCountriesMM(int cityCount, int minPopulation, int maxPopulation) {
        int countries = 0;
        for (CountryIndex country : index.values()) {
            if (country.countBetween(minPopulation, maxPopulation) >= cityCount) {
                countries++;
            }
        }
        return countries;
    }

    private static boolean isMin(String comp) {
        return comp != null && comp.trim().equalsIgnoreCase("min");
    }

    private static String key(String countryName) {
        return countryName == null ? "" : countryName.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, CountryIndex> parse(Path dataset) {
        // populationsByCountry = "Belgium" -> [300, 100, 50, ...], "Norway" -> [100, 50, 10, ...]
        Map<String, List<Integer>> populationsByCountry = new HashMap<>();

        try (Stream<String> lines = Files.lines(dataset, StandardCharsets.UTF_8)) {
            lines.skip(1).forEach(line -> collect(line, populationsByCountry));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read dataset " + dataset, e);
        }

        // index = "Belgium" -> CountryIndex([50, 100, 300], 450), "Norway" -> CountryIndex([10, 50, 100], 160)
        Map<String, CountryIndex> index = new HashMap<>(populationsByCountry.size() * 2);

        populationsByCountry.forEach((country, populations) -> {
            int[] sorted = new int[populations.size()];
            long total = 0;
            for (int i = 0; i < sorted.length; i++) {
                sorted[i] = populations.get(i);
                total += sorted[i];
            }
            Arrays.sort(sorted);
            index.put(country, new CountryIndex(sorted, total));
        });
        return Map.copyOf(index);
    }

    private static void collect(String line, Map<String, List<Integer>> target) {
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
        target.computeIfAbsent(key(country), k -> new ArrayList<>()).add(population);
    }
}
