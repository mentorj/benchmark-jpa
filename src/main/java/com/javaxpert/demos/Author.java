package com.javaxpert.demos;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "author")
@NamedQueries({
        @NamedQuery( name = "Author.findWithMostBooks",
                query = "SELECT new com.javaxpert.demos.AuthorBookCount(a, COUNT(b))" +
                        "FROM Author a JOIN a.books b GROUP BY a " +
                        "ORDER BY COUNT(b) DESC, a.id ASC")
})
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "author_seq")
    @SequenceGenerator(name = "author_seq", sequenceName = "author_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL)
    private List<Book> books = new ArrayList<>();

    /**
     * Read-only second mapping of the same foreign key, used only to compare
     * @BatchSize fetching against the plain lazy collection above.
     */
    @OneToMany(mappedBy = "author")
    @BatchSize(size = 20)
    private List<Book> booksBatched = new ArrayList<>();

    protected Author() {
    }

    public Author(String name) {
        this.name = name;
    }

    public void addBook(Book book) {
        books.add(book);
        book.setAuthor(this);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<Book> getBooks() {
        return books;
    }

    public List<Book> getBooksBatched() {
        return booksBatched;
    }
}
