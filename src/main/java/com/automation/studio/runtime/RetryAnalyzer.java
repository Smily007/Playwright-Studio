package com.automation.studio.runtime;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/** Retries a failed test up to -Dstudio.retry times. */
public class RetryAnalyzer implements IRetryAnalyzer {
    private int attempts;

    @Override
    public boolean retry(ITestResult result) {
        return attempts++ < Integer.getInteger("studio.retry", 0);
    }
}
