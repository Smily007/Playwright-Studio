package com.automation.studio.execution;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Runs an external process and streams stdout+stderr line by line. Also builds the JVM classpath for child JVMs. */
public final class ProcessRunner {
    private ProcessRunner() {}

    public static int run(List<String> cmd, Path workDir, Consumer<String> out) throws IOException, InterruptedException {
        return run(cmd, workDir, out, null);
    }

    public static int run(List<String> cmd, Path workDir, Consumer<String> out, DebuggerController debugger)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        if (workDir != null) pb.directory(workDir.toFile());
        Process p = pb.start();
        if (debugger != null) debugger.attach(p.getOutputStream(), p::destroy);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (debugger != null && line.startsWith(DebuggerController.PAUSE_PREFIX)) {
                    String[] fields = line.substring(DebuggerController.PAUSE_PREFIX.length()).split(":", 2);
                    if (fields.length == 2) {
                        try {
                            debugger.paused(Integer.parseInt(fields[0]), fields[1]);
                            continue;
                        } catch (NumberFormatException ignored) {
                            // Preserve malformed protocol output in the execution log.
                        }
                    }
                }
                out.accept(line);
            }
        } finally {
            if (debugger != null) debugger.detach();
        }
        return p.waitFor();
    }

    public static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString();
    }

    /** Classpath of the running studio: java.class.path plus any jars on the module path. */
    public static String currentClasspath() {
        List<String> parts = new ArrayList<>();
        String cp = System.getProperty("java.class.path", "");
        if (!cp.isBlank()) parts.add(cp);
        String mp = System.getProperty("jdk.module.path", "");
        for (String e : mp.split(File.pathSeparator)) {
            if (e.isBlank()) continue;
            Path p = Path.of(e);
            if (Files.isDirectory(p)) {
                try (var s = Files.list(p)) { s.filter(f -> f.toString().endsWith(".jar")).forEach(f -> parts.add(f.toString())); } catch (IOException ignored) { }
            } else parts.add(e);
        }
        return String.join(File.pathSeparator, parts);
    }
}
