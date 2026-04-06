package backend.exception;

import backend.dto.common.ApiResponse;
import backend.dto.common.CustomCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthException(AuthException ex) {
        return ResponseEntity.status(ex.getCustomCode().getHttpStatus())
                .body(ApiResponse.error(ex.getCustomCode().getCode(), ex.getMessage()));
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Object>> handleAppException(AppException ex) {
        return ResponseEntity.status(ex.getCustomCode().getHttpStatus())
                .body(ApiResponse.error(ex.getCustomCode().getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        List<String> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .toList();

        return ResponseEntity.status(CustomCode.VALIDATION_FAILED.getHttpStatus())
                .body(ApiResponse.error(CustomCode.VALIDATION_FAILED.getCode(),
                        CustomCode.VALIDATION_FAILED.getDefaultMessage(), errors));
    }

    /**
     * Xử lý RuntimeException KHÔNG xác định (vd: NullPointerException, Redis lỗi, DB timeout).
     * Trả 500 và log đầy đủ stack trace để debug — KHÔNG được nuốt lỗi thành 422 ẩn danh.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException ex) {
        log.error("[RUNTIME EXCEPTION] {}: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return ResponseEntity.status(CustomCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.error(CustomCode.INTERNAL_SERVER_ERROR.getCode(),
                        "An unexpected error occurred: " + ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGlobalException(Exception ex) {
        log.error("[UNHANDLED EXCEPTION] {} : {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return ResponseEntity.status(CustomCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.error(CustomCode.INTERNAL_SERVER_ERROR.getCode(),
                        CustomCode.INTERNAL_SERVER_ERROR.getDefaultMessage()));
    }
}
