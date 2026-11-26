package br.com.gestaofrota.application.reports;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.reports.limits")
public record ReportLimits(
        @DefaultValue("1000") int maxRows,
        @DefaultValue("20") int maxColumns,
        @DefaultValue("2000") int maxTextLength,
        @DefaultValue("1000000") int maxTotalTextLength
) {
    public ReportLimits {
        if (maxRows < 1 || maxColumns < 1 || maxTextLength < 1 || maxTotalTextLength < 1) {
            throw new IllegalArgumentException("Os limites de relatórios devem ser positivos");
        }
    }
}
