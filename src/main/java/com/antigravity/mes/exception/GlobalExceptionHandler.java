package com.antigravity.mes.exception;

import com.antigravity.mes.web.ErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Turns exceptions into a consistent error body.
 *
 * Every response carries a {@code code} as well as an English {@code error} string. The
 * app translates the code into the user's language; the English text is only a fallback
 * for codes the app doesn't recognise. The backend itself stays language-agnostic, so
 * adding a language never requires a backend change.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(ConflictException ex) {
        return body(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Catch-all so unexpected failures always leave a real stack trace in the logs
    // instead of a bare 500 with no trace.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, ex.getClass().getSimpleName() + ": " + ex.getMessage());
    }

    /**
     * Services throw with either an error code or a plain sentence. A value starting with
     * "ERR_" is treated as a code and gets its English fallback resolved; anything else is
     * passed through as-is with no code, so older messages still work unchanged.
     */
    private ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        String text = message == null ? "" : message;
        if (text.startsWith("ERR_")) {
            return ResponseEntity.status(status)
                    .body(Map.of("code", text, "error", ErrorCodes.fallback(text)));
        }
        return ResponseEntity.status(status).body(Map.of("error", text));
    }
}
