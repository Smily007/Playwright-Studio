package com.automation.studio.execution;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Runs Maven goals (e.g. "test", "-Dtest=LoginTest") in a directory and streams the output. */
public class MavenExecutor {
    public int run(Path dir, List<String> args, Consumer<String> out) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("os.name").toLowerCase().contains("win") ? "mvn.cmd" : "mvn");
        cmd.addAll(args);
        out.accept("> " + String.join(" ", cmd));
        return ProcessRunner.run(cmd, dir, out);
    }
}
