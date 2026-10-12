package com.automation.studio.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/** Forwards Logback events to the studio console ("10:10:01 INFO Starting test"). */
public class LogManager implements AutoCloseable {
    private final AppenderBase<ILoggingEvent> appender;
    private final Logger root;

    public LogManager(Consumer<String> sink) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("HH:mm:ss");
        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent e) {
                sink.accept(LocalTime.now().format(f) + " " + e.getLevel() + " " + e.getFormattedMessage());
            }
        };
        appender.setContext(root.getLoggerContext());
        appender.start();
        root.addAppender(appender);
    }

    @Override
    public void close() {
        shutdown();
    }

    public void shutdown() {
        root.detachAppender(appender);
        appender.stop();
    }
}
