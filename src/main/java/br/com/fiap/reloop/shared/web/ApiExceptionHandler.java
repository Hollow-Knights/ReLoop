package br.com.fiap.reloop.shared.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(ApiError.of(exception.status(), exception.code(), exception.getMessage()));
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String code = status != null ? status.name() : "HTTP_" + statusCode.value();
        String message = "A requisição não pôde ser concluída.";
        if (status == HttpStatus.NOT_FOUND) {
            code = "RESOURCE_NOT_FOUND";
            message = "Recurso não encontrado.";
        } else if (status == HttpStatus.PAYLOAD_TOO_LARGE) {
            code = "FILE_TOO_LARGE";
            message = "A fotografia deve ter até 10 MiB.";
        } else if (statusCode.is5xxServerError()) {
            message = "Erro interno da aplicação.";
        } else if (status != null) {
            message = status.getReasonPhrase();
        }
        return new ResponseEntity<>(ApiError.of(statusCode, code, message), headers, statusCode);
    }
}
