package com.ass1.client;

import java.util.ArrayList;
import com.ass1.common.Comparison

/**
 * Base class for all query types.
 */
abstract class Query {
    int zone;

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
    String countryName;

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
    String countryName;
    Comparison comp;
    int threshold;

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
    int cityCount;
    int threshold;
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
    int cityCount;
    int minPopulation;
    int maxPopulation;

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
    public ArrayList<Query> queries = new ArrayList<>();
}