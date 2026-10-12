package com.automation.studio;

import com.automation.studio.runtime.TestData;
import com.automation.studio.runtime.StudioBaseTest;
import org.testng.ITestResult;
import org.testng.TestNG;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class TestDataExcelTest {
    @Test
    public void readsHeadersAndEachNonEmptyDataRow() throws Exception {
        Path file = createWorkbook(workbook -> {
            var sheet = workbook.createSheet("Login");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("LoginName");
            header.createCell(1).setCellValue("Password");
            header.createCell(2).setCellValue("Account");
            var first = sheet.createRow(1);
            first.createCell(0).setCellValue("alice");
            first.createCell(1).setCellValue("secret");
            first.createCell(2).setCellValue(1001);
            sheet.createRow(2);
            var second = sheet.createRow(3);
            second.createCell(0).setCellValue("bob");
            second.createCell(1).setCellValue("another");
            second.createCell(2).setCellValue(1002);
        });
        try {
            Assert.assertEquals(TestData.excelSheets(file), List.of("Login"));
            Assert.assertEquals(TestData.loadExcel(file, "Login"), List.of(
                    Map.of("LoginName", "alice", "Password", "secret", "Account", "1001"),
                    Map.of("LoginName", "bob", "Password", "another", "Account", "1002")));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsDuplicateHeaders() throws Exception {
        Path file = createWorkbook(workbook -> {
            var header = workbook.createSheet("Data").createRow(0);
            header.createCell(0).setCellValue("Email");
            header.createCell(1).setCellValue("Email");
            workbook.getSheetAt(0).createRow(1).createCell(0).setCellValue("alice@example.com");
        });
        try {
            Assert.expectThrows(IOException.class, () -> TestData.loadExcel(file, "Data"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsReservedRowNumberHeader() throws Exception {
        Path file = createWorkbook(workbook -> {
            var sheet = workbook.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("__studio_excel_row");
            sheet.createRow(1).createCell(0).setCellValue("user-value");
        });
        try {
            Assert.expectThrows(IOException.class, () -> TestData.loadExcel(file, "Data"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsUnknownWorksheet() throws Exception {
        Path file = createWorkbook(workbook -> workbook.createSheet("Present"));
        try {
            Assert.expectThrows(IOException.class, () -> TestData.loadExcel(file, "Missing"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void mapsSourceEnvironmentNavigationToSelectedBaseUrl() {
        Assert.assertEquals(
                TestData.resolveEnvironmentUrl("https://dev.example.com/app/login?next=1", "https://dev.example.com/app", "https://stage.example.com/portal"),
                "https://stage.example.com/portal/login?next=1");
    }

    @Test
    public void resolvesRelativeNavigationAgainstSelectedEnvironment() {
        Assert.assertEquals(
                TestData.resolveEnvironmentUrl("/login", "https://dev.example.com", "https://stage.example.com/app"),
                "https://stage.example.com/login");
    }

    @Test
    public void leavesExternalNavigationUntouched() {
        Assert.assertEquals(
                TestData.resolveEnvironmentUrl("https://idp.example.org/login", "https://dev.example.com", "https://stage.example.com"),
                "https://idp.example.org/login");
    }

    @Test
    public void dataProviderPassesEveryExcelRowToTestMethods() throws Exception {
        Path file = createWorkbook(workbook -> {
            var sheet = workbook.createSheet("Rows");
            sheet.createRow(0).createCell(0).setCellValue("User");
            sheet.getRow(0).createCell(1).setCellValue("Password");
            sheet.createRow(1).createCell(0).setCellValue("alice");
            sheet.getRow(1).createCell(1).setCellValue("a-secret");
            sheet.createRow(2).createCell(0).setCellValue("bob");
            sheet.getRow(2).createCell(1).setCellValue("b-secret");
        });
        String previousFile = System.getProperty("studio.excelDataFile");
        String previousSheet = System.getProperty("studio.excelSheetName");
        try {
            System.setProperty("studio.excelDataFile", file.toString());
            System.setProperty("studio.excelSheetName", "Rows");
            ExcelParameterProbe.RECEIVED.clear();
            TestNG testng = new TestNG();
            testng.setUseDefaultListeners(false);
            testng.setTestClasses(new Class<?>[]{ExcelParameterProbe.class});
            testng.run();

            Assert.assertEquals(testng.getStatus(), 0);
            Assert.assertEquals(ExcelParameterProbe.RECEIVED.size(), 2);
            Assert.assertEquals(ExcelParameterProbe.RECEIVED.get(0).get("User"), "alice");
            Assert.assertEquals(ExcelParameterProbe.RECEIVED.get(1).get("Password"), "b-secret");
        } finally {
            restoreProperty("studio.excelDataFile", previousFile);
            restoreProperty("studio.excelSheetName", previousSheet);
            Files.deleteIfExists(file);
        }
    }

    private static Path createWorkbook(WorkbookWriter writer) throws Exception {
        Path file = Files.createTempFile("studio-excel", ".xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            writer.write(workbook);
            try (var out = Files.newOutputStream(file)) {
                workbook.write(out);
            }
        }
        return file;
    }

    @FunctionalInterface
    private interface WorkbookWriter {
        void write(XSSFWorkbook workbook) throws Exception;
    }

    public static final class ExcelParameterProbe extends StudioBaseTest {
        private static final List<Map<String, String>> RECEIVED = new ArrayList<>();

        @BeforeMethod(alwaysRun = true)
        @Override
        public void studioSetUp(ITestResult result) {}

        @AfterMethod(alwaysRun = true)
        @Override
        public void studioTearDown(ITestResult result) {}

        @Test(dataProvider = "studioExcelRows")
        public void receivesSpreadsheetRow(Map<String, String> row) {
            RECEIVED.add(row);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }
}
