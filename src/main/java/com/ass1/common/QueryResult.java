// Only need value, execution time and waitingtime for the measurements. 
public record QueryResult(long value, long executionTimeMs, long waitingTimeMs, int serverZone) implements Serializable {}
