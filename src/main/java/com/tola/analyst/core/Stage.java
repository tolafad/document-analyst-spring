package com.tola.analyst.core;

import java.util.Locale;

/**
 * Where in the pipeline a document failed. The codes match n8n's Record failure
 * stages, so Errors-tab rows from both builds read the same.
 */
public enum Stage {
    FILE_CHECK,
    EXTRACT,
    CATEGORIES,
    GEMINI,
    GEMINI_OUTPUT,
    ENRICH,
    SHEET,
    OUTPUT_FILES,
    NOTIFY;

    /** The value written to the Errors tab, e.g. "file_check". */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}