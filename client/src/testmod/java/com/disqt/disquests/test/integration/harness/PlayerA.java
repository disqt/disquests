package com.disqt.disquests.test.integration.harness;

import org.junit.jupiter.api.Tag;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Tag("PlayerA")
public @interface PlayerA {}
