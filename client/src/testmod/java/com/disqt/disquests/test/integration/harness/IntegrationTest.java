package com.disqt.disquests.test.integration.harness;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(IntegrationTestExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public @interface IntegrationTest {}
