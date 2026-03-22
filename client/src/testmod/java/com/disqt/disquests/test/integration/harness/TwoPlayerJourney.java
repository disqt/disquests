package com.disqt.disquests.test.integration.harness;

import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for two-player journey test classes.
 * Like @IntegrationTest but adds TwoPlayerSyncExtension which synchronizes
 * both clients at @BeforeAll time to prevent PhaseSync signal races.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(IntegrationTestExtension.class)
@ExtendWith(AbortOnFailureExtension.class)
@ExtendWith(TwoPlayerSyncExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public @interface TwoPlayerJourney {}
