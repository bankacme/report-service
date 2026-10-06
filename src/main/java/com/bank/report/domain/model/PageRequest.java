package com.bank.report.domain.model;

/** Página pedida del reporte: {@code page ≥ 0}, {@code size} entre 1 y {@code maxSize} (100). */
public record PageRequest(int page, int size) {

    public static final int DEFAULT_SIZE = 20;

    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1");
        }
    }

    public static PageRequest of(Integer page, Integer size, int maxSize) {
        int resolvedSize = size == null ? DEFAULT_SIZE : size;
        if (resolvedSize > maxSize) {
            throw new IllegalArgumentException("size must be <= " + maxSize);
        }
        return new PageRequest(page == null ? 0 : page, resolvedSize);
    }

    public long offset() {
        return (long) page * size;
    }
}
