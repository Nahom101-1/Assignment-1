package com.ass1.client;

import java.io.IOException;
import com.ass1.common.Comparison;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Base class for all query types.
 */
abstract class Query {
    final int zone;

    /**
     * Creates a query for a specific client zone.
     *
     * @param zone the zone the query originates from
     */
    Query(int zone) {
        this.zone = zone;
    }
}

/**
 * Query for retrieving the total population of a country.
 */
class PopulationOfCountry extends Query {
    final String countryName;

    /**
     * Creates a population-of-country query.
     *
     * @param countryName the name of the country
     * @param zone        the zone the query originates from
     */
    PopulationOfCountry(String countryName, int zone) {
        super(zone);
        this.countryName = countryName;
    }
}

/**
 * Query for counting cities that satisfy a population threshold.
 */
class NumberOfCities extends Query {
    final String countryName;
    final Comparison comp;
    final int threshold;

    /**
     * Creates a number-of-cities query.
     *
     * @param countryName the name of the country
     * @param threshold   the population threshold
     * @param comp        the comparison type
     * @param zone        the zone the query comes from
     */
    NumberOfCities(String countryName, int threshold, Comparison comp, int zone) {
        super(zone);
        this.countryName = countryName;
        this.comp = comp;
        this.threshold = threshold;
    }
}

/**
 * Query for counting countries that satisfy the given city requirements.
 */
class NumberOfCountries extends Query {
    final int cityCount;
    final int threshold;
    Comparison comp;

    /**
     * Creates a number-of-countries query.
     *
     * @param cityCount the required number of cities
     * @param threshold the population threshold
     * @param comp      the comparison type
     * @param zone      the zone the query comes from
     */
    NumberOfCountries(int cityCount, int threshold, Comparison comp, int zone) {
        super(zone);
        this.cityCount = cityCount;
        this.comp = comp;
        this.threshold = threshold;
    }
}
/**
 * Query for counting countries with cities within a population range.
 */
class NumberOfCountriesMM extends Query {
    final int cityCount;
    final int minPopulation;
    final int maxPopulation;

    /**
     * Creates a min-max number-of-countries query.
     *
     * @param cityCount     the required number of cities
     * @param minPopulation the minimum city population
     * @param maxPopulation the maximum city population
     * @param zone          the zone the query originates from
     */
    NumberOfCountriesMM(
            int cityCount,
            int minPopulation,
            int maxPopulation,
            int zone) {

        super(zone);
        this.cityCount = cityCount;
        this.minPopulation = minPopulation;
        this.maxPopulation = maxPopulation;
    }
}

/**
 * Client for reading and executing statistics queries.
 */
public class Client {
    private final List<Query> queries = new ArrayList<>();
    public void readQueries(String filePath) throws IOException {

        try (BufferedReader reader =
                     new BufferedReader(new FileReader(filePath))) {

            String line;

            while ((line = reader.readLine()) != null) {
                queries.add(parseQuery(line));
            }
        }
    }
    private Query parseQuery(String line) {

        String[] parts = line.split("\\s+"); // \\s+ = one or more whitespace characters
        // Get the zone from the last element and parse it as an integer.
        int zone = Integer.parseInt(parts[parts.length - 1].replace("Zone:", ""));


        switch (parts[0]) {
            case "getPopulationofCountry": {
                String countryName = String.join(
                        " ",
                        Arrays.copyOfRange(parts, 1, parts.length - 1)
                );
                return new PopulationOfCountry(countryName, zone);
            }

            case "getNumberofCities": {
                Comparison compType =
                        Comparison.valueOf(parts[parts.length - 2].toUpperCase());
                int threshold = Integer.parseInt((parts[parts.length - 3]));
                String countryName = String.join(
                        " ",
                        Arrays.copyOfRange(parts, 1, parts.length - 3)
                );
                return new NumberOfCities(countryName, threshold, compType, zone);
            }

            case "getNumberofCountries": {
                int cityCount = Integer.parseInt(parts[1]);
                Comparison compType =
                        Comparison.valueOf(parts[parts.length - 2].toUpperCase());
                int threshold = Integer.parseInt((parts[parts.length - 3]));
                return new NumberOfCountries(cityCount, threshold, compType, zone);
            }

            case "getNumberofCountriesMM": {
                int cityCount = Integer.parseInt(parts[1]);
                int minPopulation = Integer.parseInt((parts[2]));
                int maxPopulation = Integer.parseInt((parts[3]));
                return new NumberOfCountriesMM(cityCount, minPopulation, maxPopulation, zone);

            }
            default: {
                throw new IllegalArgumentException(
                        "Method not supported: " + parts[0]
                );
            }
        }
    }
}