package br.com.gestaofrota.application.reports;

import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.reports.ReportData;
import br.com.gestaofrota.domain.reports.ReportFormat;
import org.springframework.stereotype.Component;

@Component
public class ReportValidator {
    // Five worksheet rows are reserved for title, source, spacing and headers.
    public static final int EXCEL_MAX_DATA_ROWS = 1_048_576 - 5;
    public static final int EXCEL_MAX_COLUMNS = 16_384;
    public static final int EXCEL_MAX_CELL_LENGTH = 32_767;
    private final ReportLimits limits;

    public ReportValidator(ReportLimits limits) { this.limits = limits; }

    public void validate(ReportData data, ReportFormat format) {
        if (data == null || format == null) {
            throw new InvalidRequestException("Dados e formato do relatório são obrigatórios");
        }
        if (data.rows().size() > limits.maxRows() || data.columns().size() > limits.maxColumns()) {
            throw new InvalidRequestException("O relatório excede o limite configurado de linhas ou colunas");
        }
        if (format == ReportFormat.EXCEL && (data.rows().size() > EXCEL_MAX_DATA_ROWS
                || data.columns().size() > EXCEL_MAX_COLUMNS)) {
            throw new InvalidRequestException("O relatório excede o limite de linhas ou colunas do Excel");
        }
        long total = length(data.title(), format) + (long) length(data.source(), format);
        for (String column : data.columns()) total += length(column, format);
        for (var row : data.rows()) {
            for (String cell : row) total += length(cell, format);
        }
        if (total > limits.maxTotalTextLength()) {
            throw new InvalidRequestException("O relatório excede o limite total de texto configurado");
        }
    }

    private int length(String text, ReportFormat format) {
        // UTF-16 units match String.length() and POI's cell-length validation.
        if (text.length() > limits.maxTextLength()) {
            throw new InvalidRequestException("Um texto do relatório excede o limite configurado");
        }
        if (format == ReportFormat.EXCEL && text.length() > EXCEL_MAX_CELL_LENGTH) {
            throw new InvalidRequestException("Uma célula excede o limite de 32767 caracteres do Excel");
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isHighSurrogate(ch)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) invalidText();
            } else if (Character.isLowSurrogate(ch)
                    || (Character.isISOControl(ch) && ch != '\n' && ch != '\r' && ch != '\t')
                    || ch == '\uFFFE' || ch == '\uFFFF') {
                invalidText();
            }
        }
        return text.length();
    }

    private void invalidText() {
        throw new InvalidRequestException("O relatório contém texto Unicode inválido ou caracteres de controle");
    }
}
