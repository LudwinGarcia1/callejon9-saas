package com.callejon9.shared.time;

import java.time.Instant;

/** Rango [start, endExclusive) de instantes, ya resuelto a partir de dias de calendario. */
public record InstantRange(Instant start, Instant endExclusive) {
}
