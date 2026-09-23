package com.ass1.client;

import com.ass1.common.QueryResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueryStatsTest {

    private static ClientResult result(long turnaround, long execution, long waiting) {
        return new ClientResult(
                new PopulationOfCountry("Norway", 1, "getPopulationofCountry Norway Zone:1"),
                new QueryResult(1L, execution, waiting, 1),
                turnaround);
    }

    @Test
    void singleEntryAveragesToItself() {
        QueryStats stats = new QueryStats();
        stats.add(result(100, 10, 90));

        assertEquals(1, stats.count);
        assertEquals(100.0, stats.averageTurnaround());
        assertEquals(10.0, stats.averageExecution());
        assertEquals(90.0, stats.averageWaiting());
        assertEquals(100, stats.minTurnaround);
        assertEquals(100, stats.maxTurnaround);
    }

    @Test
    void averagesAcrossSeveralEntries() {
        QueryStats stats = new QueryStats();
        stats.add(result(100, 10, 90));
        stats.add(result(200, 30, 170));

        assertEquals(2, stats.count);
        assertEquals(150.0, stats.averageTurnaround());
        assertEquals(20.0, stats.averageExecution());
        assertEquals(130.0, stats.averageWaiting());
    }

    @Test
    void averageKeepsTheFractionalPart() {
        QueryStats stats = new QueryStats();
        stats.add(result(10, 0, 0));
        stats.add(result(11, 0, 0));

        assertEquals(10.5, stats.averageTurnaround(),
                "integer division would truncate this to 10");
    }

    @Test
    void tracksMinAndMaxRegardlessOfInsertionOrder() {
        QueryStats stats = new QueryStats();
        stats.add(result(500, 1, 1));
        stats.add(result(50, 1, 1));
        stats.add(result(250, 1, 1));

        assertEquals(50, stats.minTurnaround);
        assertEquals(500, stats.maxTurnaround);
    }

    @Test
    void handlesZeroTurnaround() {
        QueryStats stats = new QueryStats();
        stats.add(result(0, 0, 0));

        assertEquals(0, stats.minTurnaround);
        assertEquals(0, stats.maxTurnaround);
        assertEquals(0.0, stats.averageTurnaround());
    }

    @Test
    void totalsAccumulate() {
        QueryStats stats = new QueryStats();
        stats.add(result(100, 10, 90));
        stats.add(result(200, 30, 170));

        assertEquals(300, stats.totalTurnaround);
        assertEquals(40, stats.totalExecution);
        assertEquals(260, stats.totalWaiting);
    }
}
