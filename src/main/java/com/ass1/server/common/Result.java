package com.ass1.server.common;

import java.io.Serializable;

public record Result(long value,
                     long executionTimeInMs,
                     long waitingTimeInMs,
                     int zone) implements Serializable {
}
