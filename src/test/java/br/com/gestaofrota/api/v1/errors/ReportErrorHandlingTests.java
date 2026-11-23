package br.com.gestaofrota.api.v1.errors;

import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.exceptions.ReportGenerationException;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class ReportErrorHandlingTests {
    @Test
    void existingHandlerProducesValidationProblemForReportInput() {
        var problem = new ApiExceptionHandler().handleDomainException(new InvalidRequestException("Limite excedido"));
        assertEquals(400, problem.getStatus());
        assertEquals("VALIDATION_ERROR", problem.getProperties().get("code"));
        assertEquals("Limite excedido", problem.getDetail());
    }

    @Test
    void existingHandlerDoesNotExposeGenerationCause() {
        var problem = new ApiExceptionHandler().handleDomainException(
                new ReportGenerationException(new IOException("private filesystem path")));
        assertEquals(500, problem.getStatus());
        assertEquals("REPORT_GENERATION_ERROR", problem.getProperties().get("code"));
        assertFalse(problem.toString().contains("private filesystem path"));
    }
}
