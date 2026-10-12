package com.automation.studio;

import com.automation.studio.execution.DebuggerController;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class DebuggerControllerTest {
    @Test
    public void forwardsCommandsToPausedTestProcess() throws Exception {
        ByteArrayOutputStream input = new ByteArrayOutputStream();
        AtomicBoolean stopped = new AtomicBoolean();
        DebuggerController controller = new DebuggerController();
        controller.attach(input, () -> stopped.set(true));

        controller.stepForward();
        controller.continueExecution();
        controller.stop();

        Assert.assertEquals(input.toString(StandardCharsets.UTF_8), "step" + System.lineSeparator()
                + "continue" + System.lineSeparator());
        Assert.assertTrue(stopped.get());
    }
}
