package com.disqt.disquests.test.integration.bdd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BDD {
    private static final Logger LOG = LoggerFactory.getLogger("Disquests/E2E");

    private BDD() {}

    public static void given(String description) {
        LOG.info("  GIVEN {}", description);
    }

    public static void when(String description) {
        LOG.info("  WHEN {}", description);
    }

    public static void then(String description) {
        LOG.info("  THEN {}", description);
    }

    public static void and(String description) {
        LOG.info("    AND {}", description);
    }
}
