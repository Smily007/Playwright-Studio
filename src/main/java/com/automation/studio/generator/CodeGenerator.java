package com.automation.studio.generator;

import com.automation.studio.model.Project;
import com.automation.studio.model.TestCase;

public interface CodeGenerator {
    /** Complete Java source of a TestNG + Playwright test class for the given test case. */
    String generate(TestCase test, Project project);

    /** Class name used for the generated test. */
    String className(TestCase test);
}
