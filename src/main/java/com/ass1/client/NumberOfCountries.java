package com.ass1.client;

import com.ass1.common.Comparison;

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
     * @param cityCount     the required number of cities
     * @param threshold     the population threshold
     * @param comp          the comparison type
     * @param zone          the zone the query comes from
     * @param originalQuery the unparsed input line
     */
    NumberOfCountries(int cityCount, int threshold, Comparison comp, int zone, String originalQuery) {
        super(zone, originalQuery);
        this.cityCount = cityCount;
        this.comp = comp;
        this.threshold = threshold;
    }
}
