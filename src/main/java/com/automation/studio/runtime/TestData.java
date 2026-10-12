package com.automation.studio.runtime;

import com.automation.studio.utilities.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * External test data and secrets. References inside step values:
 * {@code ${env:NAME}}, {@code ${secret:key}}, {@code ${var:name}}.
 * Secrets come from env var STUDIO_SECRET_KEY or {projectDir}/testdata/secrets.json.
 */
public final class TestData {
    private static final Pattern REF = Pattern.compile("\\$\\{(env|secret|var):([^}]+)}");

    private TestData() {}

    public static String resolve(String s, Map<String, String> vars) {
        if (s == null) return "";
        Matcher m = REF.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(2);
            String v = switch (m.group(1)) {
                case "env" -> Optional.ofNullable(System.getenv(key)).orElse("");
                case "secret" -> secret(key);
                default -> vars == null ? "" : vars.getOrDefault(key, "");
            };
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String resolveEnvironmentUrl(String testUrl, String sourceBaseUrl, String targetBaseUrl) {
        if (targetBaseUrl == null || targetBaseUrl.isBlank()) return testUrl;
        try {
            URI target = baseUri(targetBaseUrl);
            URI requested = URI.create(testUrl);
            if (!requested.isAbsolute()) return withTrailingSlash(target).resolve(requested).toString();

            if (sourceBaseUrl == null || sourceBaseUrl.isBlank()) return testUrl;
            URI source = baseUri(sourceBaseUrl);
            if (!sameOrigin(source, requested)) return testUrl;

            String sourcePath = normalizedPath(source.getPath());
            String requestedPath = requested.getPath() == null ? "/" : requested.getPath();
            String relativePath;
            if (requestedPath.equals(sourcePath.substring(0, sourcePath.length() - 1))) relativePath = "";
            else if (requestedPath.startsWith(sourcePath)) relativePath = requestedPath.substring(sourcePath.length());
            else return testUrl;

            URI targetRoot = withTrailingSlash(target);
            URI resolved = targetRoot.resolve(relativePath);
            return new URI(resolved.getScheme(), resolved.getUserInfo(), resolved.getHost(), resolved.getPort(),
                    resolved.getPath(), requested.getQuery(), requested.getFragment()).toString();
        } catch (IllegalArgumentException | URISyntaxException e) {
            throw new IllegalArgumentException("Could not map navigation URL to the selected environment: " + testUrl, e);
        }
    }

    public static void validateEnvironmentUrl(String value) {
        try {
            baseUri(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Enter a valid http(s) environment base URL.", e);
        }
    }

    private static URI baseUri(String value) throws URISyntaxException {
        URI uri = new URI(value);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null)
            throw new URISyntaxException(value, "Expected an http(s) base URL without query or fragment");
        return uri;
    }

    private static URI withTrailingSlash(URI uri) throws URISyntaxException {
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (!path.endsWith("/")) path += "/";
        return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), path, null, null);
    }

    private static String normalizedPath(String path) {
        String value = path == null || path.isEmpty() ? "/" : path;
        return value.endsWith("/") ? value : value + "/";
    }

    private static boolean sameOrigin(URI first, URI second) {
        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && first.getPort() == second.getPort();
    }

    public static String secret(String key) {
        String env = System.getenv("STUDIO_SECRET_" + key.toUpperCase().replaceAll("[^A-Z0-9]", "_"));
        if (env != null) return env;
        String dir = System.getProperty("studio.projectDir");
        if (dir == null) return "";
        try {
            Path f = Paths.get(dir, "testdata", "secrets.json");
            if (!Files.exists(f)) return "";
            Map<String, String> m = JsonUtils.mapper().readValue(Files.readString(f), new TypeReference<Map<String, String>>() {});
            return m.getOrDefault(key, "");
        } catch (IOException e) {
            return "";
        }
    }

    public static void putSecret(Path projectDir, String key, String value) throws IOException {
        Path f = projectDir.resolve("testdata").resolve("secrets.json");
        Map<String, String> m = new LinkedHashMap<>();
        if (Files.exists(f)) m.putAll(JsonUtils.mapper().readValue(Files.readString(f), new TypeReference<Map<String, String>>() {}));
        m.put(key, value);
        JsonUtils.write(f, m);
    }

    /** Simple CSV reader (header row, comma separated, optional double quotes). */
    public static List<Map<String, String>> loadCsv(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        List<Map<String, String>> rows = new ArrayList<>();
        if (lines.isEmpty()) return rows;
        String[] header = split(lines.get(0));
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            String[] cells = split(lines.get(i));
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < header.length; c++) row.put(header[c].trim(), c < cells.length ? cells[c].trim() : "");
            rows.add(row);
        }
        return rows;
    }

    public static List<Map<String, String>> loadJson(Path file) throws IOException {
        return JsonUtils.mapper().readValue(Files.readString(file), new TypeReference<List<Map<String, String>>>() {});
    }

    public static List<String> excelSheets(Path file) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) names.add(workbook.getSheetName(i));
            return names;
        }
    }

    /** Reads the first row as header names and returns each remaining non-empty row keyed by those headers. */
    public static List<Map<String, String>> loadExcel(Path file, String sheetName) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) throw new IOException("Excel sheet not found: " + sheetName);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null || headerRow.getLastCellNum() < 1)
                throw new IOException("Excel sheet has no header row: " + sheetName);

            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<String> headers = new ArrayList<>();
            Set<String> uniqueHeaders = new HashSet<>();
            for (int col = 0; col < headerRow.getLastCellNum(); col++) {
                String header = cellValue(headerRow.getCell(col), formatter, evaluator).trim();
                if (header.isEmpty()) throw new IOException("Excel header in column " + (col + 1) + " is blank.");
                if (header.equals("__studio_excel_row")) throw new IOException("Excel header is reserved: " + header);
                if (!uniqueHeaders.add(header)) throw new IOException("Duplicate Excel header: " + header);
                headers.add(header);
            }

            List<Map<String, String>> rows = new ArrayList<>();
            for (int rowNumber = headerRow.getRowNum() + 1; rowNumber <= sheet.getLastRowNum(); rowNumber++) {
                Row source = sheet.getRow(rowNumber);
                Map<String, String> row = new LinkedHashMap<>();
                boolean hasValue = false;
                for (int col = 0; col < headers.size(); col++) {
                    String value = cellValue(source == null ? null : source.getCell(col), formatter, evaluator);
                    row.put(headers.get(col), value);
                    hasValue |= !value.isBlank();
                }
                if (hasValue) rows.add(row);
            }
            if (rows.isEmpty()) throw new IOException("Excel sheet has a header row but no data rows: " + sheetName);
            return rows;
        } catch (org.apache.poi.EncryptedDocumentException e) {
            throw new IOException("Excel workbook is encrypted and cannot be read.", e);
        }
    }

    private static String cellValue(org.apache.poi.ss.usermodel.Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        return cell == null ? "" : formatter.formatCellValue(cell, evaluator);
    }

    private static String[] split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (char c : line.toCharArray()) {
            if (c == '"') q = !q;
            else if (c == ',' && !q) { out.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }
}
