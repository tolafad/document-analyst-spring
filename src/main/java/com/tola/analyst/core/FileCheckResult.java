package com.tola.analyst.core;

public sealed interface FileCheckResult permits FileCheckResult.Accepted, FileCheckResult.Rejected {

    record Accepted(DocumentType type, long sizeBytes, String sha256) implements FileCheckResult { }

    record Rejected(String reason, String detail) implements FileCheckResult {
        public Outcome.Failure toFailure() {
            return new Outcome.Failure(Stage.FILE_CHECK, reason, detail);
        }
    }
}