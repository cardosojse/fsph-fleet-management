package br.com.gestaofrota.application.reports;

import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.exceptions.ReportGenerationException;
import br.com.gestaofrota.domain.reports.ReportData;
import br.com.gestaofrota.domain.reports.ReportFormat;
import br.com.gestaofrota.infra.reports.ExcelReportExporter;
import br.com.gestaofrota.infra.reports.PdfReportExporter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ReportRobustnessTests {
    private final ReportValidator validator = new ReportValidator(new ReportLimits(1000, 20, 2000, 1000000));

    private ReportData data(String cell) {
        return new ReportData("Título", "Fonte", List.of("Coluna"), List.of(List.of(cell)));
    }

    @Test
    void rejectsNullOrMalformedStructureWithProjectValidationError() {
        List<Runnable> invalid = List.of(
                () -> new ReportData(null, "Fonte", List.of("C"), List.of()),
                () -> new ReportData(" ", "Fonte", List.of("C"), List.of()),
                () -> new ReportData("T", null, List.of("C"), List.of()),
                () -> new ReportData("T", "F", null, List.of()),
                () -> new ReportData("T", "F", List.of(), List.of()),
                () -> new ReportData("T", "F", Arrays.asList((String) null), List.of()),
                () -> new ReportData("T", "F", List.of(" "), List.of()),
                () -> new ReportData("T", "F", List.of("C"), null),
                () -> new ReportData("T", "F", List.of("C"), Arrays.asList((List<String>) null)),
                () -> new ReportData("T", "F", List.of("C"), List.of(Arrays.asList((String) null))));
        for (Runnable input : invalid) {
            var error = assertThrows(InvalidRequestException.class, input::run);
            assertEquals(400, error.getStatusCode());
            assertEquals("VALIDATION_ERROR", error.getCode());
        }
    }

    @Test
    void rejectsNullDataOrFormatAtServiceAndDirectExporters() {
        var service = new ReportGenerationService(List.of(new PdfReportExporter(validator)));
        assertThrows(InvalidRequestException.class, () -> service.generate(null, ReportFormat.PDF));
        assertThrows(InvalidRequestException.class, () -> service.generate(data("x"), null));
        assertThrows(InvalidRequestException.class, () -> new PdfReportExporter(validator).export(null));
        assertThrows(InvalidRequestException.class, () -> new ExcelReportExporter(validator).export(null));
    }

    @Test
    void servicePreservesValidationErrorsRatherThanReturningInternalError() {
        var service = new ReportGenerationService(List.of(new PdfReportExporter(validator), new ExcelReportExporter(validator)));
        assertThrows(InvalidRequestException.class, () -> service.generate(data("😀"), ReportFormat.PDF));
        assertThrows(InvalidRequestException.class, () -> service.generate(data("x".repeat(2001)), ReportFormat.EXCEL));
    }

    @Test
    void enforcesConfiguredBoundariesBeforeEitherExporterAllocatesDocument() throws Exception {
        var limited = new ReportValidator(new ReportLimits(1, 1, 6, 30));
        for (ReportExporter exporter : List.of(new PdfReportExporter(limited), new ExcelReportExporter(limited))) {
            assertTrue(exporter.export(data("123456")).length > 0);
            assertThrows(InvalidRequestException.class, () -> exporter.export(data("1234567")));
            assertThrows(InvalidRequestException.class, () -> exporter.export(new ReportData(
                    "T", "F", List.of("C"), List.of(List.of("1"), List.of("2")))));
            assertThrows(InvalidRequestException.class, () -> exporter.export(new ReportData(
                    "T", "F", List.of("C", "D"), List.of())));
            assertThrows(InvalidRequestException.class, () -> exporter.export(new ReportData(
                    "1234567", "F", List.of("C"), List.of())));
            assertThrows(InvalidRequestException.class, () -> exporter.export(new ReportData(
                    "T", "1234567", List.of("C"), List.of())));
            assertThrows(InvalidRequestException.class, () -> exporter.export(new ReportData(
                    "T", "F", List.of("1234567"), List.of())));
        }
    }

    @Test
    void enforcesTotalTextBudgetIncludingMetadataAndHeaders() {
        var limited = new ReportValidator(new ReportLimits(5, 5, 10, 4));
        limited.validate(new ReportData("T", "F", List.of("C"), List.of(List.of("x"))), ReportFormat.PDF);
        assertThrows(InvalidRequestException.class, () -> limited.validate(
                new ReportData("T", "F", List.of("C"), List.of(List.of("xx"))), ReportFormat.PDF));
    }

    @Test
    void checksExcelCellLengthEvenWhenConfiguredLimitIsHigher() throws Exception {
        var large = new ReportValidator(new ReportLimits(1, 1, 40000, 100000));
        var exporter = new ExcelReportExporter(large);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(exporter.export(data("x".repeat(32767)))))) {
            assertEquals(32767, book.getSheetAt(0).getRow(5).getCell(0).getStringCellValue().length());
        }
        assertThrows(InvalidRequestException.class, () -> exporter.export(data("x".repeat(32768))));
        assertThrows(InvalidRequestException.class, () -> large.validate(
                new ReportData("x".repeat(32768), "F", List.of("C"), List.of()), ReportFormat.EXCEL));
    }

    @Test
    void checksExcelWorksheetBoundariesIncludingFourReservedRows() {
        var large = new ReportValidator(new ReportLimits(1100000, 20000, 10, 2000000));
        // Repeated immutable rows keep the boundary fixture small.
        var lastRow = new ReportData("T", "F", List.of("C"),
                Collections.nCopies(ReportValidator.EXCEL_MAX_DATA_ROWS, List.of("")));
        large.validate(lastRow, ReportFormat.EXCEL);
        assertThrows(InvalidRequestException.class, () -> large.validate(new ReportData(
                "T", "F", List.of("C"), Collections.nCopies(ReportValidator.EXCEL_MAX_DATA_ROWS + 1,
                List.of(""))), ReportFormat.EXCEL));
        large.validate(new ReportData("T", "F", Collections.nCopies(16384, "C"), List.of()), ReportFormat.EXCEL);
        assertThrows(InvalidRequestException.class, () -> large.validate(new ReportData(
                "T", "F", Collections.nCopies(16385, "C"), List.of()), ReportFormat.EXCEL));
    }

    @Test
    void rejectsUnsupportedPdfUnicodePredictablyWithoutDroppingOrReplacingData() {
        var exporter = new PdfReportExporter(validator);
        for (String text : List.of("😀", "漢字", "\u200B")) {
            var error = assertThrows(InvalidRequestException.class, () -> exporter.export(data(text)));
            assertTrue(error.getMessage().contains("U+"));
            assertEquals(400, error.getStatusCode());
        }
    }

    @Test
    void normalizesDecomposedPortugueseAccentsForPdf() throws Exception {
        try (var document = Loader.loadPDF(new PdfReportExporter(validator).export(data("Manutenc\u0327a\u0303o")))) {
            assertTrue(new PDFTextStripper().getText(document).contains("Manutenção"));
        }
    }

    @Test
    void preservesValidUnicodeInExcel() throws Exception {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(
                new ExcelReportExporter(validator).export(data("😀 漢字"))))) {
            assertEquals("😀 漢字", book.getSheetAt(0).getRow(5).getCell(0).getStringCellValue());
        }
    }

    @Test
    void rejectsMalformedUnicodeAndInvalidControlsForBothFormats() {
        for (var format : ReportFormat.values()) {
            for (String text : List.of("\uD800", "\uDC00", "\u0000", "\uFFFE")) {
                assertThrows(InvalidRequestException.class, () -> validator.validate(data(text), format));
            }
        }
    }

    @Test
    void mapsIoAndUnexpectedLibraryErrorsToSafeProjectErrorPreservingCause() {
        for (Exception failure : List.of(new IOException("private path"), new IllegalStateException("secret"))) {
            ReportExporter broken = new ReportExporter() {
                public ReportFormat format() { return ReportFormat.PDF; }
                public byte[] export(ReportData input) throws IOException {
                    if (failure instanceof IOException io) throw io;
                    throw (RuntimeException) failure;
                }
            };
            var service = new ReportGenerationService(List.of(broken));
            var error = assertThrows(ReportGenerationException.class, () -> service.generate(data("x"), ReportFormat.PDF));
            assertEquals(500, error.getStatusCode());
            assertEquals("REPORT_GENERATION_ERROR", error.getCode());
            assertSame(failure, error.getCause());
            assertFalse(error.getMessage().contains(failure.getMessage()));
        }
    }

    @Test
    void reportsExcessivePdfHeadingAsValidationError() {
        var large = new ReportValidator(new ReportLimits(1, 1, 10000, 20000));
        assertThrows(InvalidRequestException.class, () -> new PdfReportExporter(large).export(
                new ReportData("W".repeat(2000), "F", List.of("C"), List.of())));
    }

    @Test
    void bindsCustomLimitsAndRejectsInvalidConfiguration() {
        var defaults = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("app.reports.limits", Bindable.of(ReportLimits.class));
        assertEquals(new ReportLimits(1000, 20, 2000, 1000000), defaults);
        var binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "app.reports.limits.max-rows", "2", "app.reports.limits.max-columns", "3",
                "app.reports.limits.max-text-length", "40", "app.reports.limits.max-total-text-length", "100")));
        var limits = binder.bind("app.reports.limits", Bindable.of(ReportLimits.class)).get();
        assertEquals(new ReportLimits(2, 3, 40, 100), limits);
        assertThrows(IllegalArgumentException.class, () -> new ReportLimits(0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ReportLimits(1, -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ReportLimits(1, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ReportLimits(1, 1, 1, 0));
    }
}
