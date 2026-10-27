package com.onmyway.ci.diagnostics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CiDiagnosticsProbeTest {

    @Test
    void reportsFirstIntentionalFailure() {
        assertEquals("expected-one", "actual-one", "CI_DIAGNOSTIC_PROBE_FAILURE_ONE");
    }

    @Test
    void reportsSecondIntentionalFailure() {
        assertEquals("expected-two", "actual-two", "CI_DIAGNOSTIC_PROBE_FAILURE_TWO");
    }
}
