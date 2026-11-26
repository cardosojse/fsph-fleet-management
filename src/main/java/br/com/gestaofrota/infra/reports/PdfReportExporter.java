package br.com.gestaofrota.infra.reports;

import br.com.gestaofrota.application.reports.ReportExporter;
import br.com.gestaofrota.application.reports.ReportValidator;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.exceptions.ReportGenerationException;
import br.com.gestaofrota.domain.reports.ReportData;
import br.com.gestaofrota.domain.reports.ReportFormat;
import br.com.gestaofrota.domain.reports.ReportHeader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.text.Normalizer;

@Component
public class PdfReportExporter implements ReportExporter {
    private final ReportValidator validator;

    public PdfReportExporter(ReportValidator validator) { this.validator = validator; }
    private static final float MARGIN = 40;
    private static final float FONT_SIZE = 10;
    private static final float LINE = 14;
    private static final float PAD = 5;
    private static final Color BLUE = new Color(0x243e90);
    private static final Color INK = new Color(0x17243a);
    private static final Color GRID = new Color(0xd8e0eb);
    private static final Color STRIPE = new Color(0xf7f8fa);

    @Override
    public ReportFormat format() { return ReportFormat.PDF; }

    @Override
    public byte[] export(ReportData data) throws IOException {
        validator.validate(data, format());
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            renderSection(document, data);
            document.save(output);
            return output.toByteArray();
        } catch (InvalidRequestException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ReportGenerationException(exception);
        }
    }

    @Override
    public byte[] exportSections(String title, List<ReportData> sections) throws IOException {
        for (var section : sections) validator.validate(section, format());
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle(title);
            try (var page = new ConsolidatedPage(document)) {
                page.next();
                page.heading(title, sections.getFirst().header());
                for (var section : sections) {
                    try {
                        renderConsolidatedSection(page, section);
                    } catch (InvalidRequestException exception) {
                        throw new InvalidRequestException(section.title() + " — " + exception.getMessage());
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (InvalidRequestException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ReportGenerationException(exception);
        }
    }

    private void renderConsolidatedSection(ConsolidatedPage page, ReportData section) throws IOException {
        var titles = wrap(section.title(), page.bold, page.width(), 14);
        var details = wrap(section.source(), page.font, page.width(), FONT_SIZE);
        var widths = columnWidths(section, page.width());
        var headings = cells(section.columns(), page.bold, widths);
        var rows = new ArrayList<List<List<String>>>();
        for (var row : section.rows()) rows.add(cells(displayRow(section, row), page.font, widths));

        float headingHeight = rowSpace(height(headings));
        float sectionStart = titles.size() * 19 + details.size() * LINE + PAD + headingHeight;
        int firstLines = rows.isEmpty() ? 1 : height(rows.getFirst());
        float usable = page.size.getHeight() - 2 * MARGIN;
        if (sectionStart + rowSpace(1) > usable) {
            throw new InvalidRequestException("O cabeçalho da seção excede o espaço disponível no PDF");
        }
        // Reserve the complete first record when it fits on a fresh page with its title.
        float keepTogether = sectionStart + Math.min(rowSpace(firstLines), usable - sectionStart);
        if (page.y - MARGIN < keepTogether) page.next();

        for (var title : titles) {
            text(page.content, page.bold, 14, INK, MARGIN, page.y - 14, title);
            page.y -= 19;
        }
        for (var line : details) {
            text(page.content, page.font, FONT_SIZE, INK, MARGIN, page.y - FONT_SIZE, line);
            page.y -= LINE;
        }
        page.y -= PAD;
        page.tableHeader(widths, headings);
        if (rows.isEmpty()) {
            text(page.content, page.font, FONT_SIZE, INK, MARGIN + PAD, page.y - LINE, "Sem registros.");
            page.y -= rowSpace(1);
        }

        for (int row = 0; row < rows.size(); row++) {
            var contents = rows.get(row);
            int remaining = height(contents);
            int offset = 0;
            while (remaining > 0) {
                // Move whole records to the next page where possible. Split only overlong records.
                if (page.capacity() < 1 || (row > 0 && offset == 0 && remaining > page.capacity()
                        && headingHeight + rowSpace(remaining) <= usable)) {
                    page.next();
                    page.tableHeader(widths, headings);
                }
                int count = Math.min(remaining, page.capacity());
                page.y = drawRow(page.content, page.font, widths, contents, page.y, offset, count,
                        row % 2 == 0 ? Color.WHITE : STRIPE, INK);
                offset += count;
                remaining -= count;
            }
        }
        page.y -= 14;
    }

    private static float rowSpace(int lines) { return lines * LINE + 2 * PAD; }

    private final class ConsolidatedPage implements AutoCloseable {
        private final PDDocument document;
        private final PDRectangle size = PDRectangle.A4;
        private final PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream content;
        private float y;

        private ConsolidatedPage(PDDocument document) { this.document = document; }

        private void next() throws IOException {
            close();
            var page = new PDPage(size);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = size.getHeight() - MARGIN;
        }

        private float width() { return size.getWidth() - 2 * MARGIN; }

        private int capacity() { return (int) ((y - MARGIN - 2 * PAD) / LINE); }

        private void heading(String title, ReportHeader header) throws IOException {
            text(content, bold, 12, BLUE, MARGIN, y - 12, ReportHeader.INSTITUTION + " | " + ReportHeader.SYSTEM);
            y -= 29;
            text(content, bold, 18, INK, MARGIN, y - 18, title);
            y -= 28;
            for (var line : List.of(header.context(), header.reference())) {
                text(content, font, FONT_SIZE, INK, MARGIN, y - FONT_SIZE, line);
                y -= LINE;
            }
            y -= 14;
        }

        private void tableHeader(float[] widths, List<List<String>> headings) throws IOException {
            y = drawRow(content, bold, widths, headings, y, 0, height(headings), BLUE, Color.WHITE);
        }

        @Override
        public void close() throws IOException {
            if (content != null) {
                var previous = content;
                content = null;
                try (previous) {
                    footer(previous, document, size, font);
                }
            }
        }
    }

    private void renderSection(PDDocument document, ReportData data) throws IOException {
        var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        var bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        var paper = data.columns().size() > 12 ? PDRectangle.A3 : PDRectangle.A4;
        var size = data.columns().size() > 5
                ? new PDRectangle(paper.getHeight(), paper.getWidth()) : paper;
        float width = size.getWidth() - 2 * MARGIN;
        var titles = wrap(data.title(), bold, width, 18);
        var metadata = new ArrayList<String>();
        metadata.add(data.header().context());
        metadata.add(data.header().reference());
        metadata.addAll(wrap(data.source(), font, width, FONT_SIZE));
        if (titles.size() + metadata.size() > 12) {
            throw new InvalidRequestException("O cabeçalho excede as 12 linhas disponíveis no PDF");
        }

        float[] widths = columnWidths(data, width);
        var headings = cells(data.columns(), bold, widths);
        var rows = new ArrayList<List<List<String>>>();
        for (var row : data.rows()) rows.add(cells(displayRow(data, row), font, widths));

        int row = 0;
        int line = 0;
        do {
            var page = new PDPage(size);
            document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                float y = size.getHeight() - MARGIN;
                text(content, bold, 12, BLUE, MARGIN, y, ReportHeader.INSTITUTION + " | " + ReportHeader.SYSTEM);
                y -= 29;
                for (var title : titles) {
                    text(content, bold, 18, INK, MARGIN, y, title);
                    y -= 23;
                }
                for (var detail : metadata) {
                    text(content, font, FONT_SIZE, INK, MARGIN, y, detail);
                    y -= LINE;
                }
                y -= 8;
                y = drawRow(content, bold, widths, headings, y, 0, height(headings), BLUE, Color.WHITE);
                if (y < MARGIN + 2 * PAD + LINE) {
                    throw new InvalidRequestException("O cabeçalho da tabela não deixa espaço para dados no PDF");
                }
                if (rows.isEmpty()) text(content, font, FONT_SIZE, INK, MARGIN, y - LINE, "Sem registros.");
                while (row < rows.size()) {
                    var cells = rows.get(row);
                    int remaining = height(cells) - line;
                    int capacity = (int) ((y - MARGIN - 2 * PAD) / LINE);
                    if (capacity < 1) break;
                    int count = Math.min(remaining, capacity);
                    y = drawRow(content, font, widths, cells, y, line, count,
                            row % 2 == 0 ? Color.WHITE : STRIPE, INK);
                    line += count;
                    if (line == height(cells)) {
                        row++;
                        line = 0;
                    }
                }
                footer(content, document, size, font);
            }
        } while (row < rows.size());
    }

    private void footer(PDPageContentStream content, PDDocument document, PDRectangle size,
                        PDType1Font font) throws IOException {
        content.setStrokingColor(GRID);
        content.moveTo(MARGIN, 32);
        content.lineTo(size.getWidth() - MARGIN, 32);
        content.stroke();
        text(content, font, FONT_SIZE, INK, MARGIN, 19,
                ReportHeader.INSTITUTION + " | " + ReportHeader.SYSTEM);
        var number = "Página " + document.getNumberOfPages();
        text(content, font, FONT_SIZE, INK,
                size.getWidth() - MARGIN - font.getStringWidth(number) * FONT_SIZE / 1000, 19, number);
    }

    private List<String> displayRow(ReportData data, List<String> row) {
        var displayed = new ArrayList<>(row);
        data.numericColumns().forEach((column, format) -> {
            if (format == ReportData.NumberFormat.BRL && !row.get(column).isBlank()) {
                displayed.set(column, "R$ " + row.get(column));
            }
        });
        return displayed;
    }

    private float[] columnWidths(ReportData data, float width) {
        var weights = new float[data.columns().size()];
        float sum = 0;
        for (int i = 0; i < weights.length; i++) {
            int chars = data.columns().get(i).length();
            int prefixLength = data.numericColumns().get(i) == ReportData.NumberFormat.BRL ? 3 : 0;
            for (var row : data.rows()) chars = Math.max(chars, row.get(i).length() + prefixLength);
            weights[i] = Math.clamp(chars, 18, 48);
            sum += weights[i];
        }
        float reserved = Math.min(36, width / weights.length);
        for (int i = 0; i < weights.length; i++) {
            weights[i] = reserved + weights[i] * (width - reserved * weights.length) / sum;
        }
        return weights;
    }

    private List<List<String>> cells(List<String> values, PDType1Font font, float[] widths) throws IOException {
        var cells = new ArrayList<List<String>>();
        for (int i = 0; i < widths.length; i++) cells.add(wrap(values.get(i), font, widths[i] - 2 * PAD, FONT_SIZE));
        return cells;
    }

    private int height(List<List<String>> cells) {
        return cells.stream().mapToInt(List::size).max().orElse(1);
    }

    private float drawRow(PDPageContentStream content, PDType1Font font, float[] widths, List<List<String>> cells,
                          float y, int offset, int length, Color background, Color foreground) throws IOException {
        float x = MARGIN;
        float height = length * LINE + 2 * PAD;
        for (int i = 0; i < widths.length; i++) {
            content.setNonStrokingColor(background);
            content.setStrokingColor(GRID);
            content.addRect(x, y - height, widths[i], height);
            content.fillAndStroke();
            var cell = cells.get(i);
            for (int j = 0; j < length && offset + j < cell.size(); j++) {
                text(content, font, FONT_SIZE, foreground, x + PAD, y - PAD - FONT_SIZE - j * LINE, cell.get(offset + j));
            }
            x += widths[i];
        }
        return y - height;
    }

    private void text(PDPageContentStream content, PDType1Font font, float size, Color color,
                      float x, float y, String text) throws IOException {
        content.beginText();
        content.setNonStrokingColor(color);
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private List<String> wrap(String text, PDType1Font font, float width, float size) throws IOException {
        List<String> lines = new ArrayList<>();
        String normalized = supportedText(text, font);
        int start = 0;
        while (start < normalized.length()) {
            int end = start;
            int space = -1;
            float used = 0;
            while (end < normalized.length()) {
                char character = normalized.charAt(end);
                used += font.getStringWidth(String.valueOf(character)) * size / 1000;
                if (used > width && end > start) break;
                if (character == ' ') space = end;
                end++;
            }
            if (end < normalized.length() && space > start && normalized.charAt(end) != ' ') end = space;
            lines.add(normalized.substring(start, end));
            start = end;
            while (start < normalized.length() && normalized.charAt(start) == ' ') start++;
        }
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    private String supportedText(String text, PDType1Font font) throws IOException {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC).replaceAll("\\s", " ");
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            String glyph = new String(Character.toChars(codePoint));
            try {
                font.encode(glyph);
                result.append(glyph);
            } catch (IllegalArgumentException exception) {
                throw new InvalidRequestException("O PDF não suporta o caractere U+"
                        + Integer.toHexString(codePoint).toUpperCase(java.util.Locale.ROOT)
                        + ". Utilize texto compatível com a fonte do relatório");
            }
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }
}
