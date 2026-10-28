package br.com.gestaofrota.domain.exceptions;

public class AssignmentProviderUnavailableException extends ExceptionMessageBuilder {
    public AssignmentProviderUnavailableException() {
        super("Serviço indisponível",
                "A integração oficial de vínculos entre motoristas e veículos não está configurada.",
                503, "ASSIGNMENT_PROVIDER_UNAVAILABLE");
    }
}
