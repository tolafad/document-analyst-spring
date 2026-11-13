package com.tola.analyst.core;

import java.util.Arrays;
import java.util.Optional;

public enum DocumentType {
	PDF("application/pdf"), DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
	TXT("text/plain");

	private final String mediaType;

	DocumentType(String mediaType) {
		this.mediaType = mediaType;
	}

	public String mediaType() {
		return mediaType;
	}

	/** The supported type for a detected media type, or empty if unsupported. */
	public static Optional<DocumentType> fromMediaType(String detected) {
		return Arrays.stream(values()).filter(t -> t.mediaType.equals(detected)).findFirst();
	}
}