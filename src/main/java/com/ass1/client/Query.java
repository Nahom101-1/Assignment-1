package com.ass1.client;

/**
 * Base class for all query types.
 */
abstract class Query {
    final int zone;
    final String originalQuery;

    /**
     * Creates a query for a specific client zone.
     *
     * @param zone          the zone the query originates from
     * @param originalQuery the unparsed input line, echoed back in the output file
     */
    Query(int zone, String originalQuery) {
        this.zone = zone;
        this.originalQuery = originalQuery;
    }
}
