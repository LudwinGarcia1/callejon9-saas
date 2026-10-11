package com.callejon9.shared.error;

import com.callejon9.shared.throttle.RateLimitExceededException;
import com.callejon9.tenancy.NoTenantContextException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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

    @ExceptionHandler(com.callejon9.sale.service.InvalidPaymentException.class)
    ProblemDetail onInvalidPayment(com.callejon9.sale.service.InvalidPaymentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
    }

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String DATA_INTEGRITY_DETAIL =
            "Ya existe un registro con estos datos o se produjo un conflicto de "
                    + "concurrencia. Intenta de nuevo.";

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

    /**
     * El cuerpo no es JSON valido o un campo trae un tipo imposible de
     * convertir. El mensaje de Jackson incluye la clase destino y fragmentos
     * del cuerpo recibido -- que en el login puede ser la contrasena --, asi
     * que no se expone al cliente ni se escribe en el log: solo se registra
     * el tipo de la causa.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail onUnreadableBody(HttpMessageNotReadableException exception) {
        log.debug("Cuerpo de solicitud ilegible: {}",
                exception.getMostSpecificCause().getClass().getSimpleName());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no tiene un formato valido.");
        problem.setTitle("Solicitud malformada");
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

    @ExceptionHandler(InvalidRoleException.class)
    ProblemDetail onInvalidRole(InvalidRoleException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Rol invalido");
        return problem;
    }

    @ExceptionHandler(NoTenantContextException.class)
    ProblemDetail onMissingTenant(NoTenantContextException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, exception.getMessage());
        problem.setTitle("Sin restaurante activo");
        return problem;
    }

    /**
     * Una insercion o actualizacion concurrente choco con una restriccion de
     * la base de datos (un UNIQUE, tipicamente): un folio duplicado, un
     * numero de mesa repetido, etc. El mensaje de Postgres nombra la
     * restriccion, la columna y la tabla -- estructura interna que nunca debe
     * llegarle al cliente -- asi que el detalle expuesto es siempre el mismo
     * texto generico. La causa real se registra en warn para que siga siendo
     * diagnosticable desde el servidor.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail onDataIntegrityViolation(DataIntegrityViolationException exception) {
        log.warn("Conflicto de integridad de datos: {}", exception.getMessage(), exception);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, DATA_INTEGRITY_DETAIL);
        problem.setTitle("Conflicto de datos");
        return problem;
    }

    /**
     * 429 con {@code Retry-After} en segundos, redondeado hacia arriba para
     * que un cliente que espere exactamente ese tiempo ya encuentre cupo. El
     * mensaje lo fija quien lanza la excepcion y nunca repite la peticion.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> onRateLimitExceeded(RateLimitExceededException exception) {
        long seconds = Math.max(1, (exception.getRetryAfter().toMillis() + 999) / 1000);
        long minutes = (seconds + 59) / 60;

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                exception.getMessage() + " Intenta de nuevo en "
                        + (minutes == 1 ? "1 minuto." : minutes + " minutos."));
        problem.setTitle(exception.getTitle());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
                .body(problem);
    }
}
