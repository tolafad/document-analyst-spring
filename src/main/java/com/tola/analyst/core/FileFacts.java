package com.tola.analyst.core;

public record FileFacts(String fileName, long sizeBytes, String sha256, String mediaType) {
	
	public FileFacts {

        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
 
        if (sha256 == null || sha256.isBlank()) {
            throw new IllegalArgumentException("sha256 must not be blank");
        }
        
        if (mediaType == null || mediaType.isBlank()) {
            throw new IllegalArgumentException("mediaType must not be blank");
        }
    }
}
