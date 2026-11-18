package br.com.fiap.reloop.persistence.postgres;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

final class DatabaseTime {
    private DatabaseTime() { }
    // PostgreSQL timestamptz stores microseconds. First responses and later replays must agree.
    static Instant now() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }
}
