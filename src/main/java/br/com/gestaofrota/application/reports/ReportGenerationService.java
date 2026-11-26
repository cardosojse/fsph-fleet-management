package br.com.gestaofrota.application.reports;

import br.com.gestaofrota.domain.reports.ReportData;
import br.com.gestaofrota.domain.reports.ReportFormat;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.exceptions.ReportGenerationException;

@Service
public class ReportGenerationService {
    private final Map<ReportFormat, ReportExporter> exporters = new EnumMap<>(ReportFormat.class);

    public ReportGenerationService(List<ReportExporter> exporters) {
        for (ReportExporter exporter : exporters) {
            if (this.exporters.putIfAbsent(exporter.format(), exporter) != null) {
                throw new IllegalArgumentException("Duplicate report exporter: " + exporter.format());
            }
        }
    }

    public byte[] generate(ReportData data, ReportFormat format) {
        if (data == null || format == null) {
            throw new InvalidRequestException("Dados e formato do relatório são obrigatórios");
        }
        return generate(format, exporter -> exporter.export(data));
    }

    public byte[] generateSections(String title, List<ReportData> sections, ReportFormat format) {
        if (title == null || title.isBlank() || sections == null || sections.isEmpty() || format == null) {
            throw new InvalidRequestException("Título, seções e formato são obrigatórios");
        }
        return generate(format, exporter -> exporter.exportSections(title, sections));
    }

    private byte[] generate(ReportFormat format, Export export) {
        ReportExporter exporter = exporters.get(format);
        if (exporter == null) {
            throw new ReportGenerationException(new IllegalStateException("Exportador não configurado: " + format));
        }
        try {
            return export.run(exporter);
        } catch (InvalidRequestException | ReportGenerationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ReportGenerationException(exception);
        }
    }
    @FunctionalInterface
    private interface Export {
        byte[] run(ReportExporter exporter) throws IOException;
    }
}
