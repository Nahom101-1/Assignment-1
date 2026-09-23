package com.ass1.common;
import java.io.Serializable;

// Only need value, execution time and waitingtime for the measurements.
public record
QueryResult(long value, long executionTimeMs, long waitingTimeMs, int serverZone) implements Serializable {}
