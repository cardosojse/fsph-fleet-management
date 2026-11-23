package br.com.gestaofrota.domain.reports;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;

/** Immutable, presentation-ready table. Contains no persistence or export-library types. */
public record ReportData(String title, String source, List<String> columns, List<List<String>> rows,
                         ReportHeader header, Map<Integer, NumberFormat> numericColumns) {
    public enum NumberFormat {
        INTEGER, DECIMAL_2, DECIMAL_3, PERCENT, BRL
    }

    public ReportData(String title, String source, List<String> columns, List<List<String>> rows) {
        this(title, source, columns, rows, Map.of());
    }

    public ReportData(String title, String source, List<String> columns, List<List<String>> rows,
                      Map<Integer, NumberFormat> numericColumns) {
        this(title, source, columns, rows,
                new ReportHeader(ReportHeader.Module.FLEET, ReportHeader.Kind.INDIVIDUAL, Instant.now()), numericColumns);
    }

    public ReportData {
        if (title == null || source == null || title.isBlank() || source.isBlank()) {
            throw new InvalidRequestException("Título e fonte do relatório são obrigatórios");
        }
        if (columns == null || columns.isEmpty() || columns.stream().anyMatch(c -> c == null || c.isBlank())) {
            throw new InvalidRequestException("Informe ao menos uma coluna com nome válido");
        }
        if (rows == null) throw new InvalidRequestException("As linhas do relatório são obrigatórias");
        for (List<String> row : rows) {
            if (row == null || row.size() != columns.size() || row.stream().anyMatch(c -> c == null)) {
                throw new InvalidRequestException("As linhas devem conter uma célula não nula por coluna");
            }
        }
        Objects.requireNonNull(header);
        numericColumns = Map.copyOf(numericColumns);
        columns = List.copyOf(columns);
        rows = rows.stream().map(List::copyOf).toList();
    }
}
