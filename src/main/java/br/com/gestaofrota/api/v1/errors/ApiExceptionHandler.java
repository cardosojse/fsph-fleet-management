package br.com.gestaofrota.api.v1.errors;

import br.com.gestaofrota.domain.exceptions.ExceptionMessageBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = validationProblem();
        problem.setProperty("violations", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> java.util.Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "Valor inválido" : error.getDefaultMessage()))
                .toList());
        return problem;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail handleMalformedRequest() {
        return validationProblem();
    }

    private ProblemDetail validationProblem() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Dados da requisição inválidos");
        problem.setTitle("Erro de validação");
        problem.setProperty("code", "VALIDATION_ERROR");
        return problem;
    }

    @ExceptionHandler(ExceptionMessageBuilder.class)
    ProblemDetail handleDomainException(ExceptionMessageBuilder exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(exception.getStatusCode()),
                exception.getMessage()
        );
        problem.setTitle(exception.getTitle());
        problem.setProperty("code", exception.getCode());
        return problem;
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    ProblemDetail handleConcurrencyFailure() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Os dados foram alterados por outra operação. Atualize e tente novamente.");
        problem.setTitle("Conflito de concorrência");
        problem.setProperty("code", "CONCURRENT_UPDATE");
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "A operação viola uma restrição de integridade dos dados"
        );
        problem.setTitle("Conflito de dados");
        problem.setProperty("code", "DATA_CONFLICT");
        return problem;
    }
}
