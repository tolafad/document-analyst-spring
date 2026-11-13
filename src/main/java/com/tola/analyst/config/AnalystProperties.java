package com.tola.analyst.config;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@Validated
@ConfigurationProperties("analyst")
public record AnalystProperties(
        @Valid @NotNull Folders folders,
        @Valid @NotNull Files files,
        @Valid @NotNull MetadataApi metadataApi,
        @Valid @NotNull Gemini gemini,
        @Valid @NotNull Sheets sheets,
        @Valid @NotNull Mail mail) {

    // Finished example
    public record Folders(@NotNull Path root) {
        public Path incoming()  { return root.resolve("incoming"); }
        public Path work()      { return root.resolve("work"); }
        public Path processed() { return root.resolve("processed"); }
        public Path failed()    { return root.resolve("failed"); }
        public Path output()    { return root.resolve("output"); }
        public Path errors()    { return output().resolve("errors"); }
    }

    // Your turn: same pattern
    public record Files(@NotNull DataSize maxSize ) { }
    public record MetadataApi( @NotNull URI baseUrl, @NotNull Duration timeout ) { }
    public record Gemini(@NotBlank String model,
            @NotBlank String thinkingLevel,
            @Positive int maxAttempts,
            @NotNull Duration maxWait,
            @NotNull Duration timeout,
            @NotNull URI baseUrl) { }
    public record Sheets( @NotBlank String spreadsheetId, @NotBlank String documentsTab,
    		@NotBlank String errorsTab, @PositiveOrZero int documentsGid ) { }
    public record Mail(@NotBlank @Email String to) { }
}