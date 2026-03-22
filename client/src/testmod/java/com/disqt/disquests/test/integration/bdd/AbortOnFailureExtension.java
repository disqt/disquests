package com.disqt.disquests.test.integration.bdd;

import org.junit.jupiter.api.extension.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skips remaining @Order steps in a journey if a prior step failed.
 * Prevents cascading failures from producing noise.
 */
public class AbortOnFailureExtension implements ExecutionCondition, AfterTestExecutionCallback {

    private static final Map<Class<?>, Throwable> FAILURES = new ConcurrentHashMap<>();

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return context.getTestClass()
                .filter(FAILURES::containsKey)
                .map(cls -> ConditionEvaluationResult.disabled(
                    "Prior step failed: " + FAILURES.get(cls).getMessage()))
                .orElse(ConditionEvaluationResult.enabled("No prior failures"));
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        context.getExecutionException().ifPresent(ex ->
            context.getTestClass().ifPresent(cls -> FAILURES.putIfAbsent(cls, ex))
        );
    }

    /**
     * Clear failure state. Call between journey classes (e.g. in RCON reset).
     */
    public static void clearFailures() {
        FAILURES.clear();
    }
}
