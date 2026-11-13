package com.tola.analyst.adapter;

import com.tola.analyst.core.FileFacts;
import com.tola.analyst.core.port.FileInspector;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.springframework.stereotype.Component;

/**
 * Gathers the facts about a file: exact size, SHA-256, and the media type
 * Tika detects from the content (D8). The original file name is only a hint
 * to Tika; the bytes decide.
 */
@Component
public class TikaFileInspector implements FileInspector {

    private final Tika tika = new Tika();          // thread-safe, costly to create

    @Override
    public FileFacts inspect(Path file, String originalName) throws IOException {
        long size = Files.size(file);
        String sha256 = sha256(file);
        String mediaType = detect(file, originalName);
        return new FileFacts(originalName, size, sha256, mediaType);
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JDK", e);
        }
        try (InputStream in = Files.newInputStream(file);
             OutputStream sink = new DigestOutputStream(OutputStream.nullOutputStream(), digest)) {
            in.transferTo(sink);                   // streams; never loads the whole file
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String detect(Path file, String originalName) throws IOException {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, originalName);
        try (TikaInputStream in = TikaInputStream.get(file)) {
            String detected = tika.detect(in, metadata);
            // "text/plain; charset=..." -> "text/plain", so it matches DocumentType
            return MediaType.parse(detected).getBaseType().toString();
        }
    }
}