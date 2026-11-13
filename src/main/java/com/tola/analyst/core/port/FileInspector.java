package com.tola.analyst.core.port;

import com.tola.analyst.core.FileFacts;
import java.io.IOException;
import java.nio.file.Path;

/** Reads a file and reports the facts FileCheck decides on (D8). */
public interface FileInspector {

    FileFacts inspect(Path file, String originalName) throws IOException;
}