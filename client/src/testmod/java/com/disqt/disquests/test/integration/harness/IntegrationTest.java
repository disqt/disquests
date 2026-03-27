package com.disqt.disquests.test.integration.harness;

import com.disqt.disquests.test.integration.bdd.AbortOnFailureExtension;
import java.lang.annotation.*;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(IntegrationTestExtension.class)
@ExtendWith(AbortOnFailureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public @interface IntegrationTest {}
