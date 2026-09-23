package com.ass1.client;

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
