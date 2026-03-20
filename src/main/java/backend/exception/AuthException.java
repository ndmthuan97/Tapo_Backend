package backend.exception;

import backend.dto.common.CustomCode;

public class AuthException extends RuntimeException {
    private final CustomCode customCode;

    public AuthException(CustomCode customCode) {
        super(customCode.getDefaultMessage());
        this.customCode = customCode;
    }

    public CustomCode getCustomCode() {
        return customCode;
    }
}
