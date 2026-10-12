package com.automation.studio.generator;

import java.util.List;

/** Builds TestNG suite XML on the fly. */
public class TestNGGenerator {
    public String suiteXml(String suiteName, List<String> classNames, int threads) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE suite SYSTEM \"https://testng.org/testng-1.0.dtd\">\n");
        sb.append("<suite name=\"").append(esc(suiteName)).append("\"");
        if (threads > 1) sb.append(" parallel=\"classes\" thread-count=\"").append(threads).append("\"");
        sb.append(">\n");
        for (String c : classNames) {
            sb.append("  <test name=\"").append(esc(c)).append("\">\n    <classes>\n      <class name=\"tests.")
                    .append(c).append("\"/>\n    </classes>\n  </test>\n");
        }
        sb.append("</suite>\n");
        return sb.toString();
    }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;"); }
}
