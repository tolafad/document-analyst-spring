package com.tola.analyst.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.tola.analyst.core.DocumentType;
import com.tola.analyst.core.FileCheck;
import com.tola.analyst.core.FileCheckResult;
import com.tola.analyst.core.FileFacts;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TikaFileInspectorTest {

    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private final TikaFileInspector inspector = new TikaFileInspector();

    private static Path sample(String name) throws Exception {
        return Path.of(TikaFileInspectorTest.class.getResource("/sample_docs/" + name).toURI());
    }

    private FileFacts inspect(String name) throws Exception {
        return inspector.inspect(sample(name), name);
    }

    @Test
    void pdfIsDetected() throws Exception {
        FileFacts facts = inspect("T01_invoice_standard.pdf");

        assertThat(facts.mediaType()).isEqualTo(DocumentType.PDF.mediaType());
        assertThat(facts.sizeBytes()).isEqualTo(Files.size(sample("T01_invoice_standard.pdf")));
        assertThat(facts.sha256()).matches("[0-9a-f]{64}");
        System.out.println("T01 sha256: " + facts.sha256());
    }

    @Test
    void docxIsDetected() throws Exception {
        assertThat(inspect("T03_purchase_order.docx").mediaType())
                .isEqualTo(DocumentType.DOCX.mediaType());
    }

    @Test
    void textIsDetected() throws Exception {
        assertThat(inspect("T05_mutual_nda.txt").mediaType())
                .isEqualTo(DocumentType.TXT.mediaType());
    }

    @Test
    void emptyFileHasTheEmptyHash() throws Exception {
        FileFacts facts = inspect("B1_empty.txt");

        assertThat(facts.sizeBytes()).isZero();
        assertThat(facts.sha256()).isEqualTo(EMPTY_SHA256);
    }

    @Test
    void xlsxIsDetected() throws Exception {
        assertThat(inspect("B3_supplier_list.xlsx").mediaType()).contains("spreadsheetml");
    }

    @Test
    void contentWinsOverExtension() throws Exception {
        assertThat(inspect("B3_renamed.pdf").mediaType())
                .contains("spreadsheetml")
                .isNotEqualTo(DocumentType.PDF.mediaType());
    }

    @Test
    void renamedXlsxIsRejectedByFileCheck() throws Exception {
        FileCheckResult result = new FileCheck(15_728_640).check(inspect("B3_renamed.pdf"));

        assertThat(result).isInstanceOf(FileCheckResult.Rejected.class);
        assertThat(((FileCheckResult.Rejected) result).reason()).isEqualTo("unsupported_type");
    }
}