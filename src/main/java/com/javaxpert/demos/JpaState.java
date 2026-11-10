package com.javaxpert.demos;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.hibernate.CacheMode;
import org.hibernate.Session;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared state: one EntityManagerFactory and a seeded dataset for the whole trial.
 * Override the connection with -Dbench.db.url / -Dbench.db.user / -Dbench.db.password.
 */
@State(Scope.Benchmark)
public class JpaState {

    public static final String INSERT_TITLE_PREFIX = "bench-insert-";

    private static final int AUTHORS = 200;
    private static final int BOOKS_PER_AUTHOR = 10;
    private static final int SEED_FLUSH_EVERY = 50;

    public EntityManagerFactory emf;

    private volatile boolean booksInserted;

    private long[] bookIds;
    private long[] authorIds;

    @Setup(Level.Trial)
    public void setUp() {
        Map<String, Object> overrides = new HashMap<>();
        copyProperty(overrides, "bench.db.url", "jakarta.persistence.jdbc.url");
        copyProperty(overrides, "bench.db.user", "jakarta.persistence.jdbc.user");
        copyProperty(overrides, "bench.db.password", "jakarta.persistence.jdbc.password");

        emf = Persistence.createEntityManagerFactory("bench", overrides);
        seed();
        loadIds();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (emf != null) {
            emf.close(); // create-drop removes the schema
        }
    }

    /** Remove rows created by the insert benchmark so the table doesn't grow between iterations. */
    @TearDown(Level.Iteration)
    public void cleanInsertedBooks() {
        if (!booksInserted) {
            return; // a bulk delete would also invalidate the Book second-level cache region
        }
        booksInserted = false;
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            em.createQuery("delete from Book b where b.title like :prefix")
                    .setParameter("prefix", INSERT_TITLE_PREFIX + "%")
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    /** EntityManager with default cache behaviour: the second-level cache is used. */
    public EntityManager em() {
        return emf.createEntityManager();
    }

    /** EntityManager that neither reads from nor writes to the second-level cache. */
    public EntityManager emNoL2() {
        EntityManager em = emf.createEntityManager();
        em.unwrap(Session.class).setCacheMode(CacheMode.IGNORE);
        return em;
    }

    public void markBooksInserted() {
        booksInserted = true;
    }

    /** Random offset so that a page of the given size stays inside the seeded books. */
    public int randomFirstResult(int pageSize) {
        return ThreadLocalRandom.current().nextInt(bookIds.length - pageSize + 1);
    }

    public long randomBookId() {
        return bookIds[ThreadLocalRandom.current().nextInt(bookIds.length)];
    }

    public long randomAuthorId() {
        return authorIds[ThreadLocalRandom.current().nextInt(authorIds.length)];
    }

    /** A window of consecutive author ids starting at a random position. */
    public List<Long> randomAuthorIds(int count) {
        int start = ThreadLocalRandom.current().nextInt(authorIds.length - count + 1);
        List<Long> ids = new ArrayList<>(count);
        for (int i = start; i < start + count; i++) {
            ids.add(authorIds[i]);
        }
        return ids;
    }

    private void seed() {
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            int isbn = 0;
            for (int a = 0; a < AUTHORS; a++) {
                Author author = new Author("Author " + a);
                for (int b = 0; b < BOOKS_PER_AUTHOR; b++) {
                    author.addBook(new Book(
                            "Book " + a + "-" + b,
                            String.format("978-%010d", isbn++),
                            1950 + ((a + b) % 75),
                            author));
                }
                em.persist(author);
                if (a % SEED_FLUSH_EVERY == SEED_FLUSH_EVERY - 1) {
                    em.flush();
                    em.clear();
                }
            }
            em.getTransaction().commit();
        }
    }

    private void loadIds() {
        try (EntityManager em = emf.createEntityManager()) {
            bookIds = em.createQuery("select b.id from Book b order by b.id", Long.class)
                    .getResultList().stream().mapToLong(Long::longValue).toArray();
            authorIds = em.createQuery("select a.id from Author a order by a.id", Long.class)
                    .getResultList().stream().mapToLong(Long::longValue).toArray();
        }
    }

    private static void copyProperty(Map<String, Object> target, String sysProp, String jpaProp) {
        String value = System.getProperty(sysProp);
        if (value != null) {
            target.put(jpaProp, value);
        }
    }
}
