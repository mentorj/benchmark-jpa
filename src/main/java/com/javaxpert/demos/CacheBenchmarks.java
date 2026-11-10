package com.javaxpert.demos;

import jakarta.persistence.EntityManager;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * First-level cache (persistence context) and second-level cache (Ehcache) benchmarks.
 * The three "same book" benchmarks each do REPEATS lookups of one id per invocation,
 * and the score is reported per lookup.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
public class CacheBenchmarks {

    private static final int REPEATS = 20;

    /**
     * One session, same id looked up REPEATS times: the first lookup goes to the database,
     * the other 19 are served by the first-level cache.
     */
    @Benchmark
    @OperationsPerInvocation(REPEATS)
    public void sameBookSameSession(JpaState s, Blackhole bh) {
        long id = s.randomBookId();
        try (EntityManager em = s.emNoL2()) {
            for (int i = 0; i < REPEATS; i++) {
                bh.consume(em.find(Book.class, id));
            }
        }
    }

    /** A new session per lookup, second-level cache bypassed: every lookup hits the database. */
    @Benchmark
    @OperationsPerInvocation(REPEATS)
    public void sameBookNewSessionNoL2(JpaState s, Blackhole bh) {
        long id = s.randomBookId();
        for (int i = 0; i < REPEATS; i++) {
            try (EntityManager em = s.emNoL2()) {
                bh.consume(em.find(Book.class, id));
            }
        }
    }

    /** A new session per lookup with the second-level cache: no first-level help, no database. */
    @Benchmark
    @OperationsPerInvocation(REPEATS)
    public void sameBookNewSessionWithL2(JpaState s, Blackhole bh) {
        long id = s.randomBookId();
        for (int i = 0; i < REPEATS; i++) {
            try (EntityManager em = s.em()) {
                bh.consume(em.find(Book.class, id));
            }
        }
    }

    /**
     * Random id with the second-level cache enabled. Compare with
     * BookBenchmarks.findBookById, which bypasses the cache and always queries PostgreSQL.
     */
    @Benchmark
    public Book findBookByIdWithL2(JpaState s) {
        try (EntityManager em = s.em()) {
            return em.find(Book.class, s.randomBookId());
        }
    }
}
