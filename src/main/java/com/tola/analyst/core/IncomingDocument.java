package com.tola.analyst.core;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

public record IncomingDocument(Path path, String originalName, Instant receivedAt) {

    public IncomingDocument {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(receivedAt, "receivedAt");
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("originalName must not be blank");
        }
    }
}