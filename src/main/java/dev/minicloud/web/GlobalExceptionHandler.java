package dev.minicloud.web;

import java.io.IOException;

import dev.minicloud.files.StoredFileNotFoundException;
import dev.minicloud.files.UploadRejectedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Maps failures to generic user-facing messages. Internal paths and exception messages are never shown.
 */
@ControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UploadRejectedException.class)
    String uploadRejected(UploadRejectedException e, HttpServletRequest request) {
        return redirectWithError(request, messageFor(e.reason()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    String tooLarge(HttpServletRequest request) {
        return redirectWithError(request, messageFor(UploadRejectedException.Reason.TOO_LARGE));
    }

    @ExceptionHandler(StoredFileNotFoundException.class)
    String notFound(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return "error";
    }

    @ExceptionHandler(IOException.class)
    String ioError(IOException e, HttpServletResponse response) {
        log.error("I/O error while handling request: {}", e.getClass().getSimpleName());
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return "error";
    }

    static String messageFor(UploadRejectedException.Reason reason) {
        return switch (reason) {
            case INVALID_NAME -> "Nombre de archivo no válido.";
            case EMPTY -> "El archivo está vacío.";
            case TOO_LARGE -> "El archivo supera el tamaño máximo permitido.";
            case TYPE_MISMATCH -> "El contenido del archivo no coincide con su extensión.";
            case EXECUTABLE -> "No se permiten archivos ejecutables.";
            case INFECTED -> "El archivo fue rechazado por el antivirus.";
            case SCAN_ERROR -> "No se pudo analizar el archivo. Inténtalo de nuevo.";
            case INSUFFICIENT_SPACE -> "No hay espacio suficiente en el servidor.";
        };
    }

    private static String redirectWithError(HttpServletRequest request, String message) {
        RequestContextUtils.getOutputFlashMap(request).put("error", message);
        return "redirect:/files";
    }
}
