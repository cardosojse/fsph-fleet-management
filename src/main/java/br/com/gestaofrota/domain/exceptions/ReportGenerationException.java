package br.com.gestaofrota.domain.exceptions;

public class ReportGenerationException extends ExceptionMessageBuilder {
    public ReportGenerationException(Throwable cause) {
        super("Erro ao gerar relatório", "Não foi possível gerar o arquivo do relatório",
                500, "REPORT_GENERATION_ERROR");
        initCause(cause);
    }
}
