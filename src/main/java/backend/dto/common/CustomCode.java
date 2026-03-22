package backend.dto.common;

public enum CustomCode {
    SUCCESS(2000, 200, "Success"),
    CREATED(2001, 201, "Created"),
    UPDATED(2002, 200, "Updated"),
    DELETED(2003, 200, "Deleted"),
    NO_CONTENT(2004, 204, "No Content"),
    
    BAD_REQUEST(4000, 400, "Bad Request"),
    UNAUTHORIZED(4001, 401, "Unauthorized"),
    FORBIDDEN(4003, 403, "Forbidden"),
    NOT_FOUND(4004, 404, "Not Found"),
    INTERNAL_SERVER_ERROR(5000, 500, "Internal Server Error"),
    
    VALIDATION_FAILED(4005, 400, "Validation Failed"),
    CREDENTIALS_INVALID(4006, 401, "Sai email hoặc mật khẩu"),
    EMAIL_ALREADY_EXISTS(4007, 409, "Email đã được sử dụng"),
    USER_NOT_FOUND(4008, 404, "Không tìm thấy người dùng"),
    ACCOUNT_LOCKED(4009, 401, "Tài khoản của bạn đã bị khóa"),
    INVALID_REFRESH_TOKEN(4010, 401, "Refresh token không hợp lệ"),
    EXPIRED_REFRESH_TOKEN(4011, 401, "Refresh token đã hết hạn hoặc không hợp lệ"),
    ADDRESS_NOT_FOUND(4012, 404, "Không tìm thấy địa chỉ"),
    PASSWORD_INCORRECT(4013, 400, "Mật khẩu hiện tại không đúng"),
    PASSWORD_MISMATCH(4014, 400, "Mật khẩu xác nhận không khớp"),
    CANNOT_CHANGE_ADMIN_STATUS(4015, 403, "Không thể thay đổi trạng thái tài khoản Admin");

    private final int code;
    private final int httpStatus;
    private final String defaultMessage;

    CustomCode(int code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
    public String getDefaultMessage() { return defaultMessage; }
}
