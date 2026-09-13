package com.finsight.app;

import javafx.application.Application;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sanity test proving the JUnit 5 test setup works. Deliberately trivial and independent of
 * actually starting the JavaFX toolkit (that requires a display and is verified manually /
 * via {@code mvn javafx:run} for this milestone) — it only needs to prove that {@code mvn test}
 * discovers and runs JUnit 5 tests against the main source set correctly.
 */
class FinSightApplicationTest {

    @Test
    void applicationExtendsJavaFxApplication() {
        assertTrue(Application.class.isAssignableFrom(FinSightApplication.class));
    }
}
