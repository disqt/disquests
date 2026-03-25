package com.disqt.disquests.common;

import java.util.regex.Pattern;

public final class TagConstraints {
    public static final int MAX_TAGS = 8;
    public static final int MAX_TAG_LENGTH = 32;
    public static final Pattern TAG_PATTERN = Pattern.compile("[a-z0-9_-]+");

    private TagConstraints() {}
}
