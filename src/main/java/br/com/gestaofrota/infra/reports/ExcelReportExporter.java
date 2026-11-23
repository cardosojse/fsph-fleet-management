package br.com.gestaofrota.infra.reports;

import br.com.gestaofrota.application.reports.ReportExporter;
import br.com.gestaofrota.application.reports.ReportValidator;
import br.com.gestaofrota.domain.exceptions.ReportGenerationException;
import br.com.gestaofrota.domain.reports.ReportData;
import br.com.gestaofrota.domain.reports.ReportFormat;
import br.com.gestaofrota.domain.reports.ReportHeader;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

@Component
public class ExcelReportExporter implements ReportExporter {
    private static final int COLUMN_HEADER = 4;
    private static final Color BLUE = new Color(0x243e90);
    private final ReportValidator validator;

    public ExcelReportExporter(ReportValidator validator) { this.validator = validator; }

    @Override
    public ReportFormat format() { return ReportFormat.EXCEL; }

    @Override
    public byte[] export(ReportData data) throws IOException {
        validator.validate(data, format());
        return exportWorkbook(null, List.of(data));
    }

    @Override
    public byte[] exportSections(String title, List<ReportData> sections) throws IOException {
        for (var section : sections) validator.validate(section, format());
        return exportWorkbook(title, sections);
    }

    private byte[] exportWorkbook(String title, List<ReportData> sections) throws IOException {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            if (title == null) {
                writeSheet(workbook, workbook.createSheet("Relatório"), sections.getFirst());
            } else {
                workbook.getProperties().getCoreProperties().setTitle(title);
                writeConsolidatedSheet(workbook, workbook.createSheet("Geral SVO"), title, sections);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException | RuntimeException exception) {
            throw new ReportGenerationException(exception);
        }
    }

    private void writeSheet(XSSFWorkbook workbook, XSSFSheet sheet, ReportData data) {
        int columnCount = data.columns().size();
        var brand = headingStyle(workbook, 12, true);
        var title = headingStyle(workbook, 16, false);
        var details = workbook.createCellStyle();
        details.setWrapText(true);
        writeHeading(sheet, 0, columnCount, ReportHeader.INSTITUTION + " | " + ReportHeader.SYSTEM, brand, 24);
        writeHeading(sheet, 1, columnCount, data.title(), title, 25);
        String context = data.header().context() + " | " + data.header().reference();
        writeHeading(sheet, 2, columnCount, context, details, 18);
        writeHeading(sheet, 3, columnCount, data.source(), details, 18);
        int index = writeTable(sheet, COLUMN_HEADER, data, brand, numberStyles(workbook), null);
        fitColumns(sheet, columnCount);
        fitDetails(sheet, 2, columnCount, context);
        fitDetails(sheet, 3, columnCount, data.source());
        sheet.createFreezePane(0, COLUMN_HEADER + 1);
        sheet.setRepeatingRows(new CellRangeAddress(0, COLUMN_HEADER, -1, -1));
        setupPrint(workbook, sheet, columnCount, index);
    }

    private void writeConsolidatedSheet(XSSFWorkbook workbook, XSSFSheet sheet, String title,
                                        List<ReportData> sections) {
        int columns = sections.stream().mapToInt(section -> section.columns().size()).max().orElseThrow();
        var brand = headingStyle(workbook, 12, true);
        var titleStyle = headingStyle(workbook, 16, false);
        var sectionStyle = headingStyle(workbook, 12, false);
        var wrapped = workbook.createCellStyle();
        wrapped.setWrapText(true);
        var formats = numberStyles(workbook);

        writeHeading(sheet, 0, columns, ReportHeader.INSTITUTION + " | " + ReportHeader.SYSTEM, brand, 24);
        writeHeading(sheet, 1, columns, title, titleStyle, 25);
        var header = sections.getFirst().header();
        String context = header.context() + " | " + header.reference();
        writeHeading(sheet, 2, columns, context, wrapped, 18);

        var detailRows = new ArrayList<Integer>();
        var dataRows = new ArrayList<Integer>();
        int index = 4;
        for (var section : sections) {
            writeHeading(sheet, index++, columns, section.title(), sectionStyle, 23);
            detailRows.add(index);
            writeHeading(sheet, index++, columns, section.source(), wrapped, 18);
            int start = index + 1;
            index = writeTable(sheet, index, section, brand, formats, wrapped);
            for (int row = start; row < index; row++) dataRows.add(row);
            if (section.rows().isEmpty()) writeHeading(sheet, index++, columns, "Sem registros.", wrapped, 18);
            sheet.createRow(index++).setHeightInPoints(7);
        }

        fitColumns(sheet, columns);
        fitDetails(sheet, 2, columns, context);
        for (int row : detailRows) fitDetails(sheet, row, columns, sheet.getRow(row).getCell(0).getStringCellValue());
        for (int row : dataRows) {
            int lines = 1;
            for (var cell : sheet.getRow(row)) {
                if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                    var cellLines = cell.getStringCellValue().split("\\R", -1);
                    int cellHeight = 0;
                    for (var line : cellLines) cellHeight += wrappedLines(line, sheet.getColumnWidth(cell.getColumnIndex()));
                    lines = Math.max(lines, cellHeight);
                }
            }
            if (lines > 1) sheet.getRow(row).setHeightInPoints(Math.min(409, 4 + 14 * lines));
        }
        sheet.createFreezePane(0, 3);
        setupPrint(workbook, sheet, columns, index - 1);
    }

    private int writeTable(XSSFSheet sheet, int headerIndex, ReportData data, XSSFCellStyle brand,
                           EnumMap<ReportData.NumberFormat, XSSFCellStyle> numberStyles, XSSFCellStyle textStyle) {
        int columnCount = data.columns().size();
        var header = sheet.createRow(headerIndex);
        for (int column = 0; column < columnCount; column++) {
            var cell = header.createCell(column);
            cell.setCellValue(data.columns().get(column));
            cell.setCellStyle(brand);
        }
        int index = headerIndex + 1;
        for (var values : data.rows()) {
            XSSFRow row = sheet.createRow(index++);
            for (int column = 0; column < columnCount; column++) {
                var cell = row.createCell(column);
                var text = values.get(column);
                var format = data.numericColumns().get(column);
                // Only explicitly numeric columns may emit numeric cells.
                if (format != null && text.matches("-?[0-9]+(?:,[0-9]+)?%?")) {
                    var value = new BigDecimal(text.replace(",", ".").replace("%", ""));
                    if (value.stripTrailingZeros().precision() > 15) {
                        cell.setCellValue(text);
                    } else {
                        if (format == ReportData.NumberFormat.PERCENT) value = value.movePointLeft(2);
                        cell.setCellValue(value.doubleValue());
                        cell.setCellStyle(numberStyles.get(format));
                    }
                } else {
                    cell.setCellValue(text);
                    if (textStyle != null) cell.setCellStyle(textStyle);
                }
            }
        }
        return index;
    }

    private void fitColumns(XSSFSheet sheet, int columnCount) {
        for (int column = 0; column < columnCount; column++) {
            sheet.autoSizeColumn(column);
            sheet.setColumnWidth(column, Math.clamp(sheet.getColumnWidth(column) + 512, 12 * 256, 52 * 256));
        }
    }

    private void setupPrint(XSSFWorkbook workbook, XSSFSheet sheet, int columnCount, int index) {
        sheet.setFitToPage(true);
        sheet.setHorizontallyCenter(true);
        var print = sheet.getPrintSetup();
        print.setPaperSize(org.apache.poi.ss.usermodel.PrintSetup.A4_PAPERSIZE);
        print.setLandscape(columnCount > 5);
        print.setFitWidth((short) 1);
        print.setFitHeight((short) 0);
        sheet.getFooter().setLeft(ReportHeader.INSTITUTION + " | " + ReportHeader.SYSTEM);
        sheet.getFooter().setRight("Página &P de &N");
        workbook.setPrintArea(workbook.getSheetIndex(sheet), 0, columnCount - 1, 0, index - 1);
    }

    private void fitDetails(XSSFSheet sheet, int row, int columns, String text) {
        int width = 0;
        for (int i = 0; i < columns; i++) width += sheet.getColumnWidth(i);
        sheet.getRow(row).setHeightInPoints(Math.min(409, 4 + 14 * wrappedLines(text, width)));
    }

    private int wrappedLines(String text, int width) {
        // Metadata mixes digits with proportionally narrower characters and spaces.
        int charactersPerLine = Math.max(1, width / 240);
        int lines = 1;
        int used = 0;
        for (String word : text.split("\\s+")) {
            int length = word.length();
            if (used > 0 && used + 1 + length > charactersPerLine) {
                lines++;
                used = 0;
            }
            if (used > 0) used++;
            int wrapped = (length - 1) / charactersPerLine;
            lines += wrapped;
            used += length - wrapped * charactersPerLine;
        }
        return lines;
    }

    private void writeHeading(XSSFSheet sheet, int index, int columns, String text, XSSFCellStyle style, float height) {
        var row = sheet.createRow(index);
        row.setHeightInPoints(height);
        for (int i = 0; i < columns; i++) row.createCell(i).setCellStyle(style);
        row.getCell(0).setCellValue(text);
        if (columns > 1) sheet.addMergedRegion(new CellRangeAddress(index, index, 0, columns - 1));
    }

    private XSSFCellStyle headingStyle(XSSFWorkbook workbook, int size, boolean filled) {
        var style = workbook.createCellStyle();
        var font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) size);
        font.setColor(new XSSFColor(filled ? Color.WHITE : BLUE, null));
        style.setFont(font);
        if (filled) {
            style.setFillForegroundColor(new XSSFColor(BLUE, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return style;
    }

    private EnumMap<ReportData.NumberFormat, XSSFCellStyle> numberStyles(XSSFWorkbook workbook) {
        var styles = new EnumMap<ReportData.NumberFormat, XSSFCellStyle>(ReportData.NumberFormat.class);
        var formats = workbook.createDataFormat();
        for (var type : ReportData.NumberFormat.values()) {
            var style = workbook.createCellStyle();
            style.setDataFormat(formats.getFormat(switch (type) {
                case INTEGER -> "#,##0";
                case DECIMAL_2 -> "#,##0.00";
                case DECIMAL_3 -> "#,##0.000";
                case PERCENT -> "0.00%";
                case BRL -> "\"R$\" #,##0.00";
            }));
            styles.put(type, style);
        }
        return styles;
    }
}
