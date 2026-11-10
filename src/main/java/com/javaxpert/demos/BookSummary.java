package com.javaxpert.demos;

/** DTO projection of Book, loaded with a JPQL constructor expression. */
public record BookSummary(Long id, String title, String isbn, int publishedYear) {
}
