package com.tola.analyst.core;

import java.util.Optional;

/**
 * Decides whether a file is worth processing, from facts gathered by a
 * FileInspector (D8). No I/O here: every rule is testable without a file.
 */
public final class FileCheck {

    private final long maxBytes;

    public FileCheck(long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        this.maxBytes = maxBytes;
    }

    public FileCheckResult check(FileFacts facts) {
        // 1. Empty first: Tika labels an empty file application/octet-stream,
        //    which would otherwise be reported as unsupported_type.
        if (facts.sizeBytes() == 0) {
            return new FileCheckResult.Rejected("empty_file", "0 bytes");
        }

        // 2. Size, before any type decision.
        if (facts.sizeBytes() > maxBytes) {
            return new FileCheckResult.Rejected("file_too_large",
                    facts.sizeBytes() + " bytes, limit " + maxBytes + " bytes");
        }

        // 3. Type, from what Tika detected in the bytes, not the extension.
        Optional<DocumentType> type = DocumentType.fromMediaType(facts.mediaType());
        if (type.isEmpty()) {
            return new FileCheckResult.Rejected("unsupported_type",
                    facts.mediaType() + " (" + facts.fileName() + ")");
        }

        return new FileCheckResult.Accepted(type.get(), facts.sizeBytes(), facts.sha256());
    }
}