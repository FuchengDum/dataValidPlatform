package com.example.datavalidator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataValidatorApplicationTest {
    @Test
    void versionFlagUsesCliMode() {
        assertThat(DataValidatorApplication.isCli(new String[] {"--version"})).isTrue();
    }

    @Test
    void initCommandUsesCliMode() {
        assertThat(DataValidatorApplication.isCli(new String[] {"init", "jdbc"})).isTrue();
    }
}
