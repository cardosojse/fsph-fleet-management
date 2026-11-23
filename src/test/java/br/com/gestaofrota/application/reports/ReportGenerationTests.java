package br.com.gestaofrota.application.reports;

import br.com.gestaofrota.domain.reports.ReportData;
import br.com.gestaofrota.domain.reports.ReportFormat;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.exceptions.ReportGenerationException;
import br.com.gestaofrota.infra.reports.ExcelReportExporter;
import br.com.gestaofrota.infra.reports.PdfReportExporter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class ReportGenerationTests {
    private final ReportValidator validator = new ReportValidator(new ReportLimits(1000, 20, 2000, 1000000));
    private final ReportGenerationService service = new ReportGenerationService(
            List.of(new PdfReportExporter(validator), new ExcelReportExporter(validator)));

    // Example exists only in test scope. No fake provider is registered in production.
    private ReportData example() {
        return new ReportData("Relatório demonstrativo de custos",
                "DADOS FICTÍCIOS - SOMENTE TESTE",
                List.of("Veículo", "Descrição", "Valor (R$)"),
                List.of(List.of("TESTE-001", "Manutenção preventiva", "150,50"),
                        List.of("TESTE-002", "Abastecimento fictício", "230,00")));
    }

    @Test
    void generatesReadablePdfWithPortugueseContentAndExplicitFakeSource() throws Exception {
        byte[] bytes = service.generate(example(), ReportFormat.PDF);
        try (var pdf = Loader.loadPDF(bytes)) {
            assertEquals(1, pdf.getNumberOfPages());
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("DADOS FICTÍCIOS - SOMENTE TESTE"));
            assertTrue(text.contains("Manutenção preventiva"));
            assertTrue(text.contains("TESTE-002"));
            assertTrue(text.contains("230,00"));
        }
    }

    @Test
    void generatesReadableExcelWithHeadersAndAllRows() throws Exception {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(
                service.generate(example(), ReportFormat.EXCEL)))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals("Relatório demonstrativo de custos", sheet.getRow(1).getCell(0).getStringCellValue());
            assertTrue(sheet.getRow(3).getCell(0).getStringCellValue().contains("FICTÍCIOS"));
            assertEquals("Veículo", sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals("Manutenção preventiva", sheet.getRow(5).getCell(1).getStringCellValue());
            assertEquals("230,00", sheet.getRow(6).getCell(2).getStringCellValue());
            assertEquals(6, sheet.getLastRowNum());
        }
    }

    @Test
    void paginatesPdfAndRepeatsSourceWithoutLosingLastRow() throws Exception {
        var rows = IntStream.range(0, 120).mapToObj(i -> List.of("Registro " + i)).toList();
        var data = new ReportData("Exemplo", "DADOS FICTÍCIOS", List.of("Registro"), rows);
        try (var pdf = Loader.loadPDF(service.generate(data, ReportFormat.PDF))) {
            assertTrue(pdf.getNumberOfPages() > 1);
            var stripper = new PDFTextStripper();
            stripper.setStartPage(pdf.getNumberOfPages());
            String lastPage = stripper.getText(pdf);
            assertTrue(lastPage.contains("DADOS FICTÍCIOS"));
            assertTrue(lastPage.contains("Registro 119"));
        }
    }

    @Test
    void wrapsLongPdfValuesWithoutLosingText() throws Exception {
        var data = new ReportData("Teste", "DADOS FICTÍCIOS", List.of("Descrição"),
                List.of(List.of("A".repeat(300) + "FIM")));
        try (var pdf = Loader.loadPDF(service.generate(data, ReportFormat.PDF))) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("FIM"));
            assertEquals(300, text.chars().filter(c -> c == 'A').count() - 1); // A in DADOS
        }
    }

    @Test
    void exportsEmptyReportsInBothFormats() throws Exception {
        var data = new ReportData("Vazio", "DADOS FICTÍCIOS", List.of("Coluna"), List.of());
        try (var pdf = Loader.loadPDF(service.generate(data, ReportFormat.PDF));
             var workbook = new XSSFWorkbook(new ByteArrayInputStream(service.generate(data, ReportFormat.EXCEL)))) {
            assertTrue(new PDFTextStripper().getText(pdf).contains("Sem registros."));
            assertEquals(4, workbook.getSheetAt(0).getLastRowNum());
        }
    }

    @Test
    void writesFormulaLikeInputAsText() throws Exception {
        var data = new ReportData("Teste", "DADOS FICTÍCIOS", List.of("Entrada"), List.of(List.of("=1+1")));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(service.generate(data, ReportFormat.EXCEL)))) {
            var cell = workbook.getSheetAt(0).getRow(5).getCell(0);
            assertEquals(CellType.STRING, cell.getCellType());
            assertEquals("=1+1", cell.getStringCellValue());
        }
    }

    @Test
    void copiesDataAndRejectsMalformedRows() {
        var row = new ArrayList<>(List.of("Original"));
        var data = new ReportData("Teste", "Fictício", List.of("Coluna"), List.of(row));
        row.set(0, "Alterado");
        assertEquals("Original", data.rows().getFirst().getFirst());
        assertThrows(UnsupportedOperationException.class, () -> data.rows().clear());
        assertThrows(InvalidRequestException.class,
                () -> new ReportData("Teste", "Fictício", List.of("Coluna"), List.of(List.of())));
        assertThrows(InvalidRequestException.class,
                () -> new ReportData("Teste", "", List.of("Coluna"), List.of()));
    }

    @Test
    void rejectsMissingOrDuplicateExporter() {
        var empty = new ReportGenerationService(List.of());
        assertThrows(ReportGenerationException.class, () -> empty.generate(example(), ReportFormat.PDF));
        assertThrows(IllegalArgumentException.class, () -> new ReportGenerationService(
                List.of(new PdfReportExporter(validator), new PdfReportExporter(validator))));
    }
}
