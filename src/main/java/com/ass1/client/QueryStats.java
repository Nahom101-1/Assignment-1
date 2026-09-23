package com.ass1.client;

/**
 * Totals for one query type. Used for the summary lines at the end of the
 * output file.
 */
class QueryStats {
    long totalTurnaround = 0;
    long totalExecution = 0;
    long totalWaiting = 0;

    long minTurnaround = Long.MAX_VALUE;
    long maxTurnaround = Long.MIN_VALUE;

    int count = 0;

    /**
     * Adds one result to the totals.
     *
     * @param clientResult the result to count
     */
    void add(ClientResult clientResult) {
        long turnaround = clientResult.turnaroundTime;
        long execution = clientResult.result.executionTimeMs();
        long waiting = clientResult.result.waitingTimeMs();

        totalTurnaround += turnaround;
        totalExecution += execution;
        totalWaiting += waiting;

        minTurnaround = Math.min(minTurnaround, turnaround);
        maxTurnaround = Math.max(maxTurnaround, turnaround);

        count++;
    }

    /** @return mean turnaround time in milliseconds */
    double averageTurnaround() {
        return (double) totalTurnaround / count;
    }

    /** @return mean execution time in milliseconds */
    double averageExecution() {
        return (double) totalExecution / count;
    }

    /** @return mean waiting time in milliseconds */
    double averageWaiting() {
        return (double) totalWaiting / count;
    }
}
