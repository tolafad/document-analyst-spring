package com.tola.analyst.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OutcomeSwitchTest {

    @Test
    void everyOutcomeIsHandled() {
        Outcome outcome = new Outcome.Success("abc");

        String text = switch (outcome) {
            case Outcome.Success s -> "ok " + s.documentId();
            case Outcome.Failure f -> "failed at " + f.stage().code();
        };

        assertThat(text).isEqualTo("ok abc");
    }
}