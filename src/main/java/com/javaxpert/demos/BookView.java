package com.javaxpert.demos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

/**
 * Read-only view of the book table. @Immutable lets Hibernate skip dirty checking
 * and snapshot bookkeeping; @Subselect means no extra table is generated.
 */
@Entity
@Immutable
@Subselect("select id, title, isbn, published_year from book")
@Synchronize("book")
public class BookView {

    @Id
    private Long id;

    private String title;

    private String isbn;

    @Column(name = "published_year")
    private int publishedYear;

    protected BookView() {
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getIsbn() {
        return isbn;
    }

    public int getPublishedYear() {
        return publishedYear;
    }
}
