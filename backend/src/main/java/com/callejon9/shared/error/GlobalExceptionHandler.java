package com.callejon9.shared.error;

import com.callejon9.tenancy.NoTenantContextException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones de la aplicacion a ProblemDetail (RFC 7807).
 *
 * Reemplaza el patron del sistema Flask, donde un try/except con print()
 * devolvia None o lista vacia y hacia indistinguible un fallo de base de datos
 * de un resultado vacio.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onValidationError(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "La solicitud contiene campos invalidos.");
        problem.setTitle("Validacion fallida");

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));
        problem.setProperty("errors", fieldErrors);

        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail onNotFound(ResourceNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Recurso no encontrado");
        return problem;
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail onBusinessRule(BusinessRuleException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Regla de negocio");
        return problem;
    }

    @ExceptionHandler(NoTenantContextException.class)
    ProblemDetail onMissingTenant(NoTenantContextException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, exception.getMessage());
        problem.setTitle("Sin restaurante activo");
        return problem;
    }
}
