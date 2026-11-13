package com.tola.analyst.core;

import java.util.List;

/**
 * The result of processing one document. Sealed, so a switch over an Outcome
 * must handle every case; adding a new kind of result breaks the build until
 * every caller deals with it (D3).
 */
public sealed interface Outcome permits Outcome.Success, Outcome.Failure {

    /** Grows in later steps (sheet row, output files, alert status ...). */
    record Success(String documentId) implements Outcome {

        public Success {
            if (documentId == null || documentId.isBlank()) {
                throw new IllegalArgumentException("documentId must not be blank");
            }
        }
    }

    /**
     * One failure, in the shape of a row in the Errors tab. The rules are the
     * ones n8n's Record failure applied (n8n D17).
     */
    record Failure(Stage stage, String reason, String detail,
                   List<String> completedSteps) implements Outcome {

        public static final int MAX_DETAIL = 500;

        public Failure {
            if (stage == null) {
                throw new IllegalArgumentException("stage must not be null");
            }
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
            detail = oneLine(detail);
            completedSteps = completedSteps == null ? List.of() : List.copyOf(completedSteps);
        }

        /** Convenience for the common case: nothing done yet. */
        public Failure(Stage stage, String reason, String detail) {
            this(stage, reason, detail, List.of());
        }

        private static String oneLine(String text) {
            if (text == null) {
                return "none";
            }
            String flat = text.replaceAll("\\s+", " ").trim();
            if (flat.isEmpty()) {
                return "none";
            }
            return flat.length() > MAX_DETAIL ? flat.substring(0, MAX_DETAIL) : flat;
        }
    }
}