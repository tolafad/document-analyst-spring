package com.tola.analyst.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FileCheckTest {

    private static final long LIMIT = 15_728_640;          // 15 MB, as in n8n
    private final FileCheck check = new FileCheck(LIMIT);

    private static FileFacts facts(long size, String mediaType) {
        return new FileFacts("doc", size, "a".repeat(64), mediaType);
    }

    private static FileCheckResult.Rejected rejected(FileCheckResult result) {
        assertThat(result).isInstanceOf(FileCheckResult.Rejected.class);
        return (FileCheckResult.Rejected) result;
    }

    @Test
    void emptyFileIsRejected() {
        var result = check.check(facts(0, "text/plain"));

        assertThat(rejected(result).reason()).isEqualTo("empty_file");
    }
    
    @Test
    void emptyFileWinsOverUnsupportedType() {
        var result = check.check(facts(0, "application/octet-stream"));

        assertThat(rejected(result).reason()).isEqualTo("empty_file");
    }

    @Test
    void fileOneByteOverTheLimitIsRejected() {
        var result = check.check(facts(LIMIT + 1, DocumentType.PDF.mediaType()));

        var rejected = rejected(result);
        assertThat(rejected.reason()).isEqualTo("file_too_large");
        assertThat(rejected.detail()).contains("15728641");
    }
    
    @Test
    void fileAtTheLimitIsAccepted() {
        var result = check.check(facts(LIMIT, DocumentType.PDF.mediaType()));

        assertThat(result).isEqualTo(
                new FileCheckResult.Accepted(DocumentType.PDF, LIMIT, "a".repeat(64)));
    }
    
    @ParameterizedTest
    @EnumSource(DocumentType.class)
    void everySupportedTypeIsAccepted(DocumentType type) {
        var result = check.check(facts(1000, type.mediaType()));

        assertThat(result).isEqualTo(
                new FileCheckResult.Accepted(type, 1000, "a".repeat(64)));
    }

    @Test
    void xlsxIsRejected() {
        String xlsx = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

        var rejected = rejected(check.check(facts(1000, xlsx)));

        assertThat(rejected.reason()).isEqualTo("unsupported_type");
        assertThat(rejected.detail()).contains("spreadsheetml");
    }

    @Test
    void rejectionBecomesFileCheckFailure() {
        var failure = rejected(check.check(facts(0, "text/plain"))).toFailure();

        assertThat(failure.stage()).isEqualTo(Stage.FILE_CHECK);
        assertThat(failure.reason()).isEqualTo("empty_file");
        assertThat(failure.detail()).isEqualTo("0 bytes");
    }
}