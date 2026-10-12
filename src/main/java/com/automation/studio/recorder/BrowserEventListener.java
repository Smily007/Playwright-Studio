package com.automation.studio.recorder;

import com.automation.studio.model.TestStep;

/** Receives recorded steps. Called on the Playwright worker thread: marshal to the UI thread yourself. */
public interface BrowserEventListener {
    void onStep(TestStep step);

    /** Replace the last {@code count} recorded steps with {@code step} (e.g. two clicks + dblclick, repeated fill). */
    void onReplaceLast(int count, TestStep step);
}
