package com.javaxpert.demos;

import jakarta.persistence.EntityManager;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The same page of 500 books read four ways, each inside a transaction that commits
 * (so the flush and dirty-checking cost of managed entities is part of the measurement).
 * The score is per page, not per row. The second-level cache is bypassed everywhere.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
public class ReadModelBenchmarks {

    private static final int PAGE_SIZE = 500;

    /** Regular managed entities: Hibernate keeps a snapshot per row and dirty-checks at commit. */
    @Benchmark
    public List<Book> entities(JpaState s) {
        try (EntityManager em = s.emNoL2()) {
            em.getTransaction().begin();
            List<Book> result = em
                    .createQuery("select b from Book b order by b.id", Book.class)
                    .setFirstResult(s.randomFirstResult(PAGE_SIZE))
                    .setMaxResults(PAGE_SIZE)
                    .getResultList();
            em.getTransaction().commit();
            return result;
        }
    }

    /** Same entities, loaded read-only via a query hint: no snapshots, no dirty checking. */
    @Benchmark
    public List<Book> entitiesReadOnlyHint(JpaState s) {
        try (EntityManager em = s.emNoL2()) {
            em.getTransaction().begin();
            List<Book> result = em
                    .createQuery("select b from Book b order by b.id", Book.class)
                    .setHint("org.hibernate.readOnly", true)
                    .setFirstResult(s.randomFirstResult(PAGE_SIZE))
                    .setMaxResults(PAGE_SIZE)
                    .getResultList();
            em.getTransaction().commit();
            return result;
        }
    }

    /** DTO projection through a JPQL constructor expression: nothing is managed. */
    @Benchmark
    public List<BookSummary> dtoProjection(JpaState s) {
        try (EntityManager em = s.emNoL2()) {
            em.getTransaction().begin();
            List<BookSummary> result = em
                    .createQuery("select new com.example.bench.BookSummary(b.id, b.title, b.isbn, b.publishedYear) "
                            + "from Book b order by b.id", BookSummary.class)
                    .setFirstResult(s.randomFirstResult(PAGE_SIZE))
                    .setMaxResults(PAGE_SIZE)
                    .getResultList();
            em.getTransaction().commit();
            return result;
        }
    }

    /** @Immutable entity on a subselect: managed, but excluded from dirty checking by mapping. */
    @Benchmark
    public List<BookView> immutableEntities(JpaState s) {
        try (EntityManager em = s.emNoL2()) {
            em.getTransaction().begin();
            List<BookView> result = em
                    .createQuery("select v from BookView v order by v.id", BookView.class)
                    .setFirstResult(s.randomFirstResult(PAGE_SIZE))
                    .setMaxResults(PAGE_SIZE)
                    .getResultList();
            em.getTransaction().commit();
            return result;
        }
    }
}
