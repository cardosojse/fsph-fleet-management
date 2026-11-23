package br.com.gestaofrota.application.reports;

import br.com.gestaofrota.api.v1.dtos.requests.FuelingReportFilterRequest;
import br.com.gestaofrota.api.v1.dtos.responses.FuelingReportItemResponse;
import br.com.gestaofrota.api.v1.dtos.responses.FuelingReportResponse;
import br.com.gestaofrota.api.v1.dtos.responses.FuelingReportTotalsResponse;
import br.com.gestaofrota.domain.enums.FuelType;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.reports.ReportFormat;
import br.com.gestaofrota.infra.reports.ExcelReportExporter;
import br.com.gestaofrota.infra.reports.PdfReportExporter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic inputs are unit-test fixtures only, never a production report source. */
class FuelingReportTests {
    private final UUID vehicle = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final ReportLimits limits = new ReportLimits(1000, 20, 2000, 1000000);
    private final FuelingReportService service = new FuelingReportService(limits);
    private final ReportValidator validator = new ReportValidator(limits);
    private final FuelingReportMapper mapper = new FuelingReportMapper(limits, validator);
    private final FuelingReportFilterRequest filters = new FuelingReportFilterRequest(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null);

    private FuelingReportItemResponse row(int day, UUID vehicleId, String liters, String value) {
        return new FuelingReportItemResponse(UUID.randomUUID(), LocalDate.of(2026, 1, day),
                vehicleId, "TESTE", "Veículo de teste", FuelType.DIESEL, new BigDecimal(liters), new BigDecimal(value));
    }

    @Test
    void sumsExactDecimalsAndNeverReconstructsTotalFromRoundedUnitPrices() {
        var first = row(1, vehicle, "3", "10");
        var second = row(2, vehicle, "0.1", "0.10");
        var report = service.build(filters, List.of(first, second));
        assertEquals(new BigDecimal("3.100"), report.totals().totalLiters());
        assertEquals(new BigDecimal("10.10"), report.totals().totalValue());
        assertEquals(2, report.totals().totalFuelings());
        assertEquals(new BigDecimal("3.33"), first.getPricePerLiter());
        assertNotEquals(0, report.totals().totalValue().compareTo(
                first.getPricePerLiter().multiply(first.liters()).add(second.getPricePerLiter().multiply(second.liters()))));
    }

    @Test
    void unitPriceMatchesExistingHalfUpRule() {
        assertEquals(new BigDecimal("0.13"), row(1, vehicle, "8", "1").getPricePerLiter());
        assertEquals(new BigDecimal("0.12"), row(1, vehicle, "25", "3").getPricePerLiter());
    }

    @Test
    void filtersDatesInclusivelyAndVehicleBeforeCalculatingTotals() {
        var specific = new FuelingReportFilterRequest(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3), vehicle);
        var report = service.build(specific, List.of(row(1, vehicle, "1", "1"), row(2, vehicle, "2", "2"),
                row(3, vehicle, "3", "3"), row(4, vehicle, "4", "4"), row(2, UUID.randomUUID(), "50", "50")));
        assertEquals(2, report.items().size());
        assertEquals(new BigDecimal("5.000"), report.totals().totalLiters());
        assertEquals(specific, report.filters());
    }

    @Test
    void acceptsSingleDayAndRejectsMissingOrInvertedDates() {
        assertDoesNotThrow(() -> new FuelingReportFilterRequest(filters.startDate(), filters.startDate(), null));
        assertThrows(InvalidRequestException.class, () -> new FuelingReportFilterRequest(null, filters.endDate(), null));
        assertThrows(InvalidRequestException.class, () -> new FuelingReportFilterRequest(filters.startDate(), null, null));
        assertThrows(InvalidRequestException.class, () -> new FuelingReportFilterRequest(filters.endDate(), filters.startDate(), null));
    }

    @Test
    void returnsImmutableEmptyReportAndExplicitZeroTotals() {
        var report = service.build(filters, List.of());
        assertTrue(report.items().isEmpty());
        assertEquals(new FuelingReportTotalsResponse(0, new BigDecimal("0.000"), new BigDecimal("0.00")), report.totals());
        var data = mapper.toReportData(report, ReportFormat.PDF);
        assertTrue(data.source().contains("sem registros"));
        assertEquals(1, data.rows().size());
        assertEquals("0,000", data.rows().getFirst().get(4));
        assertEquals("0,00", data.rows().getFirst().get(6));
        assertThrows(UnsupportedOperationException.class, () -> report.items().clear());
    }

    @Test
    void rejectsNullAndInconsistentRecordsWithoutSilentlyIgnoringThem() {
        assertThrows(InvalidRequestException.class, () -> service.build(null, List.of()));
        assertThrows(InvalidRequestException.class, () -> service.build(filters, null));
        assertThrows(InvalidRequestException.class, () -> service.build(filters, Arrays.asList((FuelingReportItemResponse) null)));
        for (String invalid : List.of("0", "-1", "0.0001", "10000000")) {
            assertThrows(InvalidRequestException.class, () -> row(1, vehicle, invalid, "1"));
        }
        for (String invalid : List.of("0", "-1", "0.001", "10000000000")) {
            assertThrows(InvalidRequestException.class, () -> row(1, vehicle, "1", invalid));
        }
        assertThrows(InvalidRequestException.class, () -> new FuelingReportItemResponse(null, filters.startDate(), vehicle,
                "TESTE", "Modelo", FuelType.DIESEL, BigDecimal.ONE, BigDecimal.ONE));
        assertThrows(InvalidRequestException.class, () -> new FuelingReportItemResponse(UUID.randomUUID(), filters.startDate(), vehicle,
                " ", "Modelo", FuelType.DIESEL, BigDecimal.ONE, BigDecimal.ONE));
    }

    @Test
    void rejectsDuplicateIdsForgedTotalsAndRowsOutsideResponseFilters() {
        var item = row(1, vehicle, "1", "1");
        assertThrows(InvalidRequestException.class, () -> service.build(filters, List.of(item, item)));
        assertThrows(InvalidRequestException.class, () -> new FuelingReportResponse(filters, List.of(item),
                new FuelingReportTotalsResponse(1, BigDecimal.TEN, BigDecimal.ONE)));
        var otherFilter = new FuelingReportFilterRequest(filters.endDate(), filters.endDate(), null);
        assertThrows(InvalidRequestException.class, () -> new FuelingReportResponse(otherFilter, List.of(item),
                FuelingReportTotalsResponse.from(List.of(item))));
    }

    @Test
    void sortsAndDefensivelyCopiesRows() {
        var input = new ArrayList<>(List.of(row(3, vehicle, "1", "1"), row(1, vehicle, "1", "1")));
        var report = service.build(filters, input);
        input.clear();
        assertEquals(2, report.items().size());
        assertEquals(filters.startDate(), report.items().getFirst().fueledOn());
    }

    @Test
    void reservesTotalRowAndRejectsExcessRatherThanTruncating() {
        var small = new ReportLimits(2, 20, 2000, 100000);
        var smallService = new FuelingReportService(small);
        assertEquals(1, smallService.build(filters, List.of(row(1, vehicle, "1", "1"))).items().size());
        assertThrows(InvalidRequestException.class, () -> smallService.build(filters,
                List.of(row(1, vehicle, "1", "1"), row(2, vehicle, "1", "1"))));
        var smallMapper = new FuelingReportMapper(small, new ReportValidator(small));
        assertThrows(InvalidRequestException.class, () -> smallMapper.toReportData(service.build(filters,
                List.of(row(1, vehicle, "1", "1"), row(2, vehicle, "1", "1"))), ReportFormat.PDF));
    }

    @Test
    void validatesColumnTextAndTotalTextLimitsInMapper() {
        var report = service.build(filters, List.of(row(1, vehicle, "1", "1")));
        for (ReportLimits restricted : List.of(new ReportLimits(10, 6, 2000, 100000),
                new ReportLimits(10, 20, 10, 100000), new ReportLimits(10, 20, 2000, 10))) {
            var restrictedMapper = new FuelingReportMapper(restricted, new ReportValidator(restricted));
            for (var format : ReportFormat.values()) {
                assertThrows(InvalidRequestException.class, () -> restrictedMapper.toReportData(report, format));
            }
        }
        assertThrows(InvalidRequestException.class, () -> mapper.toReportData(null, ReportFormat.PDF));
        assertThrows(InvalidRequestException.class, () -> mapper.toReportData(report, null));
    }

    @Test
    void convertsTypedReportAndReusesPdfAndExcelExporters() throws Exception {
        var report = service.build(filters, List.of(row(1, vehicle, "3", "10")));
        var generator = new ReportGenerationService(List.of(new PdfReportExporter(validator), new ExcelReportExporter(validator)));
        var data = mapper.toReportData(report, ReportFormat.PDF);
        assertEquals(7, data.columns().size());
        assertEquals(List.of("01/01/2026", "TESTE", "Veículo de teste", "DIESEL", "3,000", "3,33", "10,00"), data.rows().getFirst());
        try (var pdf = Loader.loadPDF(generator.generate(data, ReportFormat.PDF))) {
            var text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Abastecimentos"));
            assertTrue(text.contains("TOTAL"));
            assertTrue(text.contains("R$ 10,00"));
        }
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(generator.generate(
                mapper.toReportData(report, ReportFormat.EXCEL), ReportFormat.EXCEL)))) {
            var price = workbook.getSheetAt(0).getRow(5).getCell(5);
            assertEquals(3.33, price.getNumericCellValue());
            assertEquals("\"R$\" #,##0.00", price.getCellStyle().getDataFormatString());
            assertEquals("TOTAL", workbook.getSheetAt(0).getRow(6).getCell(0).getStringCellValue());
        }
    }
}
