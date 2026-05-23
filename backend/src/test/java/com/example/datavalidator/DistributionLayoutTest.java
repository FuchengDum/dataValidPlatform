package com.example.datavalidator;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DistributionLayoutTest {
    private final Path repoRoot = Path.of("..").toAbsolutePath().normalize();

    @Test
    void rootCliScriptExistsAndDoesNotBakeLocalPaths() throws Exception {
        Path script = repoRoot.resolve("bin/data-validator");

        assertThat(script).exists().isRegularFile();
        String content = Files.readString(script);
        assertThat(content).startsWith("#!/usr/bin/env sh");
        assertThat(content).contains("DATA_VALIDATOR_JAR");
        assertThat(content).doesNotContain("/Users/");
    }

    @Test
    void minimalDistributionExampleContainsOnlyPortableAssets() throws Exception {
        Path example = repoRoot.resolve("examples/distribution-minimal");

        assertThat(example.resolve("validator.yml")).exists();
        assertThat(example.resolve("source.yml")).exists();
        assertThat(example.resolve("rules.yml")).exists();
        assertThat(example.resolve("README.md")).exists();
        assertThat(Files.exists(example.resolve("reports"))).isFalse();
        assertThat(Files.readString(example.resolve("README.md"))).doesNotContain("/Users/");
        assertThat(Files.readString(example.resolve("validator.yml"))).doesNotContain("password");
    }
}
