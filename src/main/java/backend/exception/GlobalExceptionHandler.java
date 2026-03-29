package backend.exception;

import backend.dto.common.ApiResponse;
import backend.dto.common.CustomCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

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
                .collect(Collectors.toList());
                
        return ResponseEntity.status(CustomCode.VALIDATION_FAILED.getHttpStatus())
                .body(ApiResponse.error(CustomCode.VALIDATION_FAILED.getCode(), CustomCode.VALIDATION_FAILED.getDefaultMessage(), errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGlobalException(Exception ex) {
        return ResponseEntity.status(CustomCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.error(CustomCode.INTERNAL_SERVER_ERROR.getCode(), CustomCode.INTERNAL_SERVER_ERROR.getDefaultMessage()));
    }
}
