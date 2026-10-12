package com.automation.studio.execution;

import com.automation.studio.runtime.RetryTransformer;
import org.testng.TestNG;
import org.testng.xml.XmlSuite;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Entry point of the test JVM: runs generated classes or a suite XML with TestNG. */
public final class RunnerMain {
    private RunnerMain() {}

    public static void main(String[] args) throws Exception {
        String suite = null;
        String classes = null;
        for (String a : args) {
            if (a.startsWith("--suite=")) suite = a.substring(8);
            if (a.startsWith("--classes=")) classes = a.substring(10);
        }
        TestNG testng = new TestNG();
        testng.setOutputDirectory(Paths.get(System.getProperty("studio.reportDir", "target/studio-report"), "testng-output").toString());
        testng.addListener(new RetryTransformer());
        if (suite != null) {
            testng.setTestSuites(List.of(suite));
        } else {
            List<Class<?>> list = new ArrayList<>();
            for (String c : classes.split(",")) list.add(Class.forName(c.trim()));
            testng.setTestClasses(list.toArray(new Class<?>[0]));
            int threads = Integer.getInteger("studio.threads", 1);
            if (threads > 1) { testng.setParallel(XmlSuite.ParallelMode.CLASSES); testng.setThreadCount(threads); }
        }
        testng.run();
        System.exit(testng.getStatus() == 0 ? 0 : 1);
    }
}
