package com.ass1.client;

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
