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

import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
public class BookBenchmarks {

    private static final int AUTHORS_PER_QUERY = 20;
    private static final int BOOKS_PER_INSERT = 50;

    /** Primary-key lookup with a fresh EntityManager (cold first-level cache). */
    @Benchmark
    public Book findBookById(JpaState s) {
        try (EntityManager em = s.emNoL2()) {
            return em.find(Book.class, s.randomBookId());
        }
    }

    /** 1 query for authors + 1 lazy query per author for its books (the classic N+1). */
    @Benchmark
    public int authorsWithBooksNPlusOne(JpaState s) {
        try (EntityManager em = s.emNoL2()) {
            List<Author> authors = em
                    .createQuery("select a from Author a where a.id in :ids", Author.class)
                    .setParameter("ids", s.randomAuthorIds(AUTHORS_PER_QUERY))
                    .getResultList();
            int books = 0;
            for (Author a : authors) {
                books += a.getBooks().size();
            }
            return books;
        }
    }

    @Benchmark
    public AuthorBookCount maxBookPerAuthorAnonymousQueryNoL2(JpaState s){
        try (EntityManager em = s.emNoL2()) {
            AuthorBookCount top = em.createQuery(
                            "SELECT new com.javaxpert.demos.AuthorBookCount(a, COUNT(b)) " +
                                    "FROM Author a JOIN a.books b GROUP BY a " +
                                    "ORDER BY COUNT(b) DESC, a.id ASC", AuthorBookCount.class)
                    .setMaxResults(1)
                    .getSingleResult();
            return top;
        }
    }

    @Benchmark
    public AuthorBookCount maxBookPerAuthorNamedQueryNoL2(JpaState s){
        try (EntityManager em = s.emNoL2()) {
            AuthorBookCount top = em.createNamedQuery("Author.findWithMostBooks"
                            , AuthorBookCount.class)
                    .setMaxResults(1)
                    .getSingleResult();
            return top;
        }
    }

    /**
     * Lazy loading exactly like the N+1 case, but the collection is mapped with
     * @BatchSize(size = 20): the 20 books collections are loaded with one query.
     */
    @Benchmark
    public int authorsWithBooksBatchSize(JpaState s) {
        try (EntityManager em = s.emNoL2()) {
            List<Author> authors = em
                    .createQuery("select a from Author a where a.id in :ids", Author.class)
                    .setParameter("ids", s.randomAuthorIds(AUTHORS_PER_QUERY))
                    .getResultList();
            int books = 0;
            for (Author a : authors) {
                books += a.getBooksBatched().size();
            }
            return books;
        }
    }

    /** Same result as above in a single query. */
    @Benchmark
    public int authorsWithBooksJoinFetch(JpaState s) {
        try (EntityManager em = s.emNoL2()) {
            List<Author> authors = em
                    .createQuery("select distinct a from Author a join fetch a.books where a.id in :ids",
                            Author.class)
                    .setParameter("ids", s.randomAuthorIds(AUTHORS_PER_QUERY))
                    .getResultList();
            int books = 0;
            for (Author a : authors) {
                books += a.getBooks().size();
            }
            return books;
        }
    }

    /**
     * Inserts 50 books in one transaction using JDBC batching.
     * Reported score is per book. Rows are deleted after each iteration in JpaState.
     */
    @Benchmark
    @OperationsPerInvocation(BOOKS_PER_INSERT)
    public void insertBooksBatch(JpaState s) {
        try (EntityManager em = s.emNoL2()) {
            em.getTransaction().begin();
            Author author = em.getReference(Author.class, s.randomAuthorId());
            for (int i = 0; i < BOOKS_PER_INSERT; i++) {
                em.persist(new Book(
                        JpaState.INSERT_TITLE_PREFIX + i,
                        String.format("999-%010d", i),
                        2024,
                        author));
            }
            em.getTransaction().commit();
            s.markBooksInserted();
        }
    }
}
