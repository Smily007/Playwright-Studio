package com.automation.studio.execution;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Controls a generated test process while it is paused at a debugger step boundary. */
public final class DebuggerController {
    public static final String PAUSE_PREFIX = "STUDIO_DEBUG_PAUSED:";

    private volatile BufferedWriter input;
    private volatile Runnable stopAction;
    private volatile boolean stopped;
    private volatile Consumer<Pause> pauseListener = pause -> {};

    public void setPauseListener(Consumer<Pause> listener) {
        pauseListener = listener == null ? pause -> {} : listener;
    }

    public void attach(OutputStream processInput, Runnable stopAction) {
        input = new BufferedWriter(new OutputStreamWriter(processInput, StandardCharsets.UTF_8));
        this.stopAction = stopAction;
        stopped = false;
    }

    public void stepForward() throws IOException { send("step"); }

    public void continueExecution() throws IOException { send("continue"); }

    public void stop() {
        Runnable action = stopAction;
        if (action != null) {
            stopped = true;
            action.run();
        }
    }

    public boolean isStopped() { return stopped; }

    public void detach() {
        input = null;
        stopAction = null;
    }

    void paused(int stepNumber, String description) {
        pauseListener.accept(new Pause(stepNumber, description));
    }

    private void send(String command) throws IOException {
        BufferedWriter writer = input;
        if (writer == null) throw new IllegalStateException("The debugger process is not connected.");
        synchronized (writer) {
            writer.write(command);
            writer.newLine();
            writer.flush();
        }
    }

    public record Pause(int stepNumber, String description) {}
}
