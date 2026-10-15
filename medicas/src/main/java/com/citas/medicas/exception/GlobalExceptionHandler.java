package com.citas.medicas.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail manejarNoEncontrado(RecursoNoEncontradoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ProblemDetail manejarCredencialesInvalidas(CredencialesInvalidasException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(EmailNoVerificadoException.class)
    public ProblemDetail manejarEmailNoVerificado(EmailNoVerificadoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(TokenInvalidoException.class)
    public ProblemDetail manejarTokenInvalido(TokenInvalidoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ProblemDetail manejarAccesoDenegado(AccesoDenegadoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail manejarRolNoPermitido(AccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Tu rol no tiene permiso para realizar esta acción");
    }

    @ExceptionHandler(FechaInvalidaException.class)
    public ProblemDetail manejarFechaInvalida(FechaInvalidaException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(OrdenamientoInvalidoException.class)
    public ProblemDetail manejarOrdenamientoInvalido(OrdenamientoInvalidoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ContrasenaIncorrectaException.class)
    public ProblemDetail manejarContrasenaIncorrecta(ContrasenaIncorrectaException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ArchivoInvalidoException.class)
    public ProblemDetail manejarArchivoInvalido(ArchivoInvalidoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(DemasiadosIntentosException.class)
    public ResponseEntity<ProblemDetail> manejarDemasiadosIntentos(DemasiadosIntentosException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getSegundosDeEspera()))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail manejarArchivoDemasiadoGrande(MaxUploadSizeExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE,
                "El archivo supera el tamaño máximo permitido de 5 MB");
    }

    @ExceptionHandler({
            HorarioNoDisponibleException.class,
            CitaConflictException.class,
            TransicionInvalidaException.class,
            CancelacionNoPermitidaException.class,
            RecursoDuplicadoException.class,
            RecursoEnUsoException.class,
            HorarioSolapadoException.class,
            LimiteDeReservasException.class
    })
    public ProblemDetail manejarConflicto(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail manejarModificacionConcurrente(ObjectOptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "La cita fue modificada por otra operación. Consúltala de nuevo e intenta otra vez.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail manejarCuerpoIlegible(HttpMessageNotReadableException ex) {
        if (!(ex.getCause() instanceof MismatchedInputException error) || error.getPath().isEmpty()) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                    "El cuerpo de la solicitud no es un JSON válido. Revisa comillas, comas y llaves.");
        }

        String campo = error.getPath().stream()
                .map(referencia -> referencia.getPropertyName() != null
                        ? referencia.getPropertyName()
                        : "[" + referencia.getIndex() + "]")
                .collect(Collectors.joining("."));
        String mensaje = "Valor inválido" + formatoEsperado(error.getTargetType());

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "La solicitud tiene campos inválidos");
        problema.setProperty("errores", Map.of(campo, mensaje));
        return problema;
    }

    private String formatoEsperado(Class<?> tipo) {
        if (tipo == null) {
            return "";
        }
        if (tipo.isEnum()) {
            return ". Valores permitidos: " + Arrays.stream(tipo.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
        }
        if (tipo == LocalDate.class) {
            return ". Formato esperado: AAAA-MM-DD, por ejemplo 2005-04-03";
        }
        if (tipo == LocalDateTime.class) {
            return ". Formato esperado: AAAA-MM-DDTHH:MM:SS, por ejemplo 2026-10-05T09:00:00";
        }
        if (tipo == LocalTime.class) {
            return ". Formato esperado: HH:MM, por ejemplo 08:00";
        }
        if (tipo == UUID.class) {
            return ". Debe ser un identificador UUID";
        }
        if (Number.class.isAssignableFrom(tipo) || tipo.isPrimitive()) {
            return ". Debe ser un número";
        }
        return "";
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail manejarValidacion(MethodArgumentNotValidException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "La solicitud tiene campos inválidos");

        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errores.put(error.getField(), error.getDefaultMessage()));
        problema.setProperty("errores", errores);

        return problema;
    }
}
