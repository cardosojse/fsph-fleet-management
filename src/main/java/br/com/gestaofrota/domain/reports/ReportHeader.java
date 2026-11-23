package br.com.gestaofrota.domain.reports;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record ReportHeader(Module module, Kind kind, Instant referenceAt) {
    public static final String INSTITUTION = "FSPH";
    public static final String SYSTEM = "Sistema de Gestão de Frotas";
    private static final DateTimeFormatter REFERENCE = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    public enum Module {
        SVO("SVO"), FLEET("Gestão de Frotas");
        private final String label;
        Module(String label) { this.label = label; }
    }

    public enum Kind {
        GENERAL("Geral"), INDIVIDUAL("Individual");
        private final String label;
        Kind(String label) { this.label = label; }
    }

    public String context() {
        return "Módulo: " + module.label + " | Tipo: " + kind.label;
    }

    public String referenceLabel() {
        return REFERENCE.format(referenceAt) + " (São Paulo)";
    }

    public String reference() {
        return "Referência: " + referenceLabel();
    }
}
