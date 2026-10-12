package com.automation.studio.utilities;

/** Helpers for emitting Java source. */
public final class CodeUtil {
    private CodeUtil() {}

    /** Quoted, escaped Java string literal. */
    public static String literal(String s) {
        if (s == null) s = "";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /**
     * Java expression for a string value. Values containing {@code ${env:X}}, {@code ${secret:K}} or
     * {@code ${var:name}} are resolved at runtime through TestData, so secrets never appear in source.
     */
    public static String expr(String s) {
        if (s != null && s.contains("${")) return "TestData.resolve(" + literal(s) + ", vars)";
        return literal(s);
    }

    public static String snake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase();
    }

    public static String decap(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /** "My login test" -> "MyLoginTest" (valid Java class name). */
    public static String className(String name) {
        StringBuilder sb = new StringBuilder();
        boolean up = true;
        for (char c : (name == null ? "" : name).toCharArray()) {
            if (Character.isLetterOrDigit(c)) { sb.append(up ? Character.toUpperCase(c) : c); up = false; }
            else up = true;
        }
        if (sb.length() == 0 || !Character.isLetter(sb.charAt(0))) sb.insert(0, 'T');
        return sb.toString();
    }

    public static String methodName(String name) { return decap(className(name)); }
}
