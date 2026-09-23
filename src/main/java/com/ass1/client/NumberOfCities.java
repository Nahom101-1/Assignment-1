package com.ass1.client;

import com.ass1.common.Comparison;

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
