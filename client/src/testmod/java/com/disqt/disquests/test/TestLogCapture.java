package com.disqt.disquests.test;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.Level;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Captures Log4j2 log events for assertion in tests.
 * Usage:
 *   TestLogCapture capture = TestLogCapture.attach("Disquests/QuestEntry");
 *   // ... do stuff that logs ...
 *   List<String> messages = capture.getMessages();
 *   capture.detach();
 */
public class TestLogCapture extends AbstractAppender {

    private final List<LogEvent> events = new CopyOnWriteArrayList<>();
    private final Logger logger;

    private TestLogCapture(Logger logger) {
        super("TestLogCapture-" + logger.getName(), null, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY);
        this.logger = logger;
    }

    /**
     * Attach a capture to the named logger at DEBUG level.
     */
    public static TestLogCapture attach(String loggerName) {
        Logger logger = (Logger) LogManager.getLogger(loggerName);
        TestLogCapture capture = new TestLogCapture(logger);
        capture.start();
        logger.addAppender(capture);
        logger.setLevel(Level.DEBUG);
        return capture;
    }

    /**
     * Detach this capture from the logger.
     */
    public void detach() {
        logger.removeAppender(this);
        this.stop();
    }

    @Override
    public void append(LogEvent event) {
        events.add(event.toImmutable());
    }

    /**
     * Get all captured formatted messages.
     */
    public List<String> getMessages() {
        return events.stream()
                .map(e -> e.getMessage().getFormattedMessage())
                .collect(Collectors.toList());
    }

    /**
     * Check if any captured message contains the given substring.
     */
    public boolean hasMessageContaining(String substring) {
        return events.stream()
                .anyMatch(e -> e.getMessage().getFormattedMessage().contains(substring));
    }

    /**
     * Clear all captured events.
     */
    public void clear() {
        events.clear();
    }
}
