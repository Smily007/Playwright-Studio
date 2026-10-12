package com.automation.studio.runtime;

@FunctionalInterface
public interface ThrowingRunnable {
    void run() throws Exception;
}
