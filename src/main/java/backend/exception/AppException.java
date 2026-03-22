package backend.exception;

import backend.dto.common.CustomCode;

/**
 * Generic domain exception used for business-logic errors across all features.
 * Carries a {@link CustomCode} that maps to an HTTP status and a default message.
 */
public class AppException extends RuntimeException {

    private final CustomCode customCode;

    public AppException(CustomCode customCode) {
        super(customCode.getDefaultMessage());
        this.customCode = customCode;
    }

    public CustomCode getCustomCode() {
        return customCode;
    }
}
