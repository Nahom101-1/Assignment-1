package com.ass1.client;

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
     * @param originalQuery the unparsed input line
     */
    NumberOfCountriesMM(
            int cityCount,
            int minPopulation,
            int maxPopulation,
            int zone,
            String originalQuery) {

        super(zone, originalQuery);
        this.cityCount = cityCount;
        this.minPopulation = minPopulation;
        this.maxPopulation = maxPopulation;
    }
}
