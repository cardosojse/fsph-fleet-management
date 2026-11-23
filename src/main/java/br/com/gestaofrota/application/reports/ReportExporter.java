package br.com.gestaofrota.application.reports;

import br.com.gestaofrota.domain.reports.ReportData;
import br.com.gestaofrota.domain.reports.ReportFormat;
import java.io.IOException;
import java.util.List;

public interface ReportExporter {
    ReportFormat format();
    byte[] export(ReportData data) throws IOException;
    default byte[] exportSections(String title, List<ReportData> sections) throws IOException {
        throw new UnsupportedOperationException("Exportador sem suporte a relatórios com seções");
    }
}
