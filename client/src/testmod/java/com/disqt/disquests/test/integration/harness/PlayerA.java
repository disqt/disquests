package com.disqt.disquests.test.integration.harness;

import java.lang.annotation.*;
import org.junit.jupiter.api.Tag;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Tag("PlayerA")
public @interface PlayerA {}
