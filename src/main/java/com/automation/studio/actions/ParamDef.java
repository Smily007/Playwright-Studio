package com.automation.studio.actions;

import com.automation.studio.runtime.TestData;
import com.automation.studio.utilities.CodeUtil;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** One parameter of an action: positional argument, option setter, or meta (negate / storeAs). */
public class ParamDef {
    public enum Kind { STRING, INT, LONG, DOUBLE, BOOLEAN, ENUM }

    public final String name;
    public final Kind kind;
    public final Class<?> javaType;
    public final boolean option;
    public final boolean meta;
    public final Method setter;
    public final List<String> enumValues = new ArrayList<>();

    public ParamDef(String name, Class<?> type, boolean option, boolean meta, Method setter) {
        this.name = name;
        this.javaType = type;
        this.kind = kindOf(type);
        this.option = option;
        this.meta = meta;
        this.setter = setter;
        if (type.isEnum()) for (Object c : type.getEnumConstants()) enumValues.add(((Enum<?>) c).name());
    }

    public static ParamDef of(String name, Kind kind) {
        return new ParamDef(name, classOf(kind), false, false, null);
    }

    public static ParamDef str(String name) { return of(name, Kind.STRING); }
    public static ParamDef integer(String name) { return of(name, Kind.INT); }
    public static ParamDef number(String name) { return of(name, Kind.DOUBLE); }
    public static ParamDef meta(String name, Kind kind) { return new ParamDef(name, classOf(kind), false, true, null); }

    private static Class<?> classOf(Kind k) {
        return switch (k) {
            case INT -> int.class;
            case LONG -> long.class;
            case DOUBLE -> double.class;
            case BOOLEAN -> boolean.class;
            default -> String.class;
        };
    }

    public static boolean supported(Class<?> c) {
        return c == String.class || c == int.class || c == Integer.class || c == long.class || c == Long.class
                || c == double.class || c == Double.class || c == boolean.class || c == Boolean.class || c.isEnum();
    }

    static Kind kindOf(Class<?> c) {
        if (c.isEnum()) return Kind.ENUM;
        if (c == int.class || c == Integer.class) return Kind.INT;
        if (c == long.class || c == Long.class) return Kind.LONG;
        if (c == double.class || c == Double.class) return Kind.DOUBLE;
        if (c == boolean.class || c == Boolean.class) return Kind.BOOLEAN;
        return Kind.STRING;
    }

    private static String num(String raw) {
        return raw == null || raw.isBlank() ? "0" : raw.trim();
    }

    /** Java source literal for a raw string value. */
    public String javaLiteral(String raw) {
        switch (kind) {
            case INT:
                return hasReference(raw) ? "Integer.parseInt(" + CodeUtil.expr(raw) + ")"
                        : String.valueOf((int) Double.parseDouble(num(raw)));
            case LONG:
                return hasReference(raw) ? "Long.parseLong(" + CodeUtil.expr(raw) + ")"
                        : (long) Double.parseDouble(num(raw)) + "L";
            case DOUBLE:
                return hasReference(raw) ? "Double.parseDouble(" + CodeUtil.expr(raw) + ")"
                        : String.valueOf(Double.parseDouble(num(raw)));
            case BOOLEAN:
                return hasReference(raw) ? "Boolean.parseBoolean(" + CodeUtil.expr(raw) + ")"
                        : String.valueOf(Boolean.parseBoolean(raw == null ? "" : raw.trim()));
            case ENUM: {
                if (hasReference(raw))
                    return javaType.getSimpleName() + ".valueOf(" + CodeUtil.expr(raw) + ".toUpperCase(Locale.ROOT))";
                String v = raw == null || raw.isBlank() ? enumValues.get(0) : raw.trim().toUpperCase();
                return javaType.getSimpleName() + "." + v;
            }
            default: return CodeUtil.expr(raw);
        }
    }

    private static boolean hasReference(String raw) { return raw != null && raw.contains("${"); }

    /** Runtime value for reflection calls. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object toRuntime(String raw, Map<String, String> vars) {
        switch (kind) {
            case INT: return (int) Double.parseDouble(num(raw));
            case LONG: return (long) Double.parseDouble(num(raw));
            case DOUBLE: return Double.parseDouble(num(raw));
            case BOOLEAN: return Boolean.parseBoolean(raw == null ? "" : raw.trim());
            case ENUM: {
                String v = raw == null || raw.isBlank() ? enumValues.get(0) : raw.trim().toUpperCase();
                return Enum.valueOf((Class<Enum>) javaType, v);
            }
            default: return TestData.resolve(raw, vars);
        }
    }
}
